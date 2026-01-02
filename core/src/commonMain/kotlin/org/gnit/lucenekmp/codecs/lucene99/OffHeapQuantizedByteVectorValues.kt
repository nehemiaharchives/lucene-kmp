package org.gnit.lucenekmp.codecs.lucene99

import okio.IOException
import org.gnit.lucenekmp.codecs.hnsw.FlatVectorsScorer
import org.gnit.lucenekmp.codecs.lucene90.IndexedDISI
import org.gnit.lucenekmp.codecs.lucene95.OrdToDocDISIReaderConfiguration
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.VectorScorer
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.hnsw.RandomVectorScorer
import org.gnit.lucenekmp.util.packed.DirectMonotonicReader
import org.gnit.lucenekmp.util.quantization.QuantizedByteVectorValues
import org.gnit.lucenekmp.util.quantization.ScalarQuantizer

/**
 * Read the quantized vector values and their score correction values from the index input. This
 * supports both iterated and random access.
 */
abstract class OffHeapQuantizedByteVectorValues(
    protected val dimension: Int,
    protected val size: Int,
    protected val scalarQuantizerValue: ScalarQuantizer,
    protected val similarityFunction: VectorSimilarityFunction,
    protected val vectorsScorer: FlatVectorsScorer,
    protected val compress: Boolean,
    override val slice: IndexInput?
) : QuantizedByteVectorValues() {

    protected val numBytes: Int
    protected val binaryValue: ByteArray
    protected val byteSize: Int
    protected var lastOrd: Int = -1
    protected val scoreCorrectionConstant = FloatArray(1)

    init {
        numBytes = if (scalarQuantizerValue.bits <= 4 && compress) {
            (dimension + 1) shr 1
        } else {
            dimension
        }
        byteSize = numBytes + Float.SIZE_BYTES
        binaryValue = ByteArray(dimension)
    }

    override val scalarQuantizer: ScalarQuantizer
        get() = scalarQuantizerValue

    override fun dimension(): Int {
        return dimension
    }

    override fun size(): Int {
        return size
    }

    override val vectorByteLength: Int
        get() = numBytes

    @Throws(IOException::class)
    override fun vectorValue(targetOrd: Int): ByteArray {
        if (lastOrd == targetOrd) {
            return binaryValue
        }
        slice!!.seek(targetOrd.toLong() * byteSize)
        slice!!.readBytes(binaryValue, 0, numBytes)
        slice!!.readFloats(scoreCorrectionConstant, 0, 1)
        decompressBytes(binaryValue, numBytes)
        lastOrd = targetOrd
        return binaryValue
    }

    @Throws(IOException::class)
    override fun getScoreCorrectionConstant(ord: Int): Float {
        if (lastOrd == ord) {
            return scoreCorrectionConstant[0]
        }
        slice!!.seek(ord.toLong() * byteSize + numBytes)
        slice!!.readFloats(scoreCorrectionConstant, 0, 1)
        return scoreCorrectionConstant[0]
    }

    companion object {
        fun decompressBytes(compressed: ByteArray, numBytes: Int) {
            if (numBytes == compressed.size) {
                return
            }
            if (numBytes shl 1 != compressed.size) {
                throw IllegalArgumentException(
                    "numBytes: $numBytes does not match compressed length: ${compressed.size}"
                )
            }
            for (i in 0 until numBytes) {
                compressed[numBytes + i] = (compressed[i].toInt() and 0x0F).toByte()
                compressed[i] = ((compressed[i].toInt() and 0xFF) shr 4).toByte()
            }
        }

        fun compressedArray(dimension: Int, bits: Byte): ByteArray? {
            return if (bits <= 4) {
                ByteArray((dimension + 1) shr 1)
            } else {
                null
            }
        }

        fun compressBytes(raw: ByteArray, compressed: ByteArray) {
            if (compressed.size != ((raw.size + 1) shr 1)) {
                throw IllegalArgumentException(
                    "compressed length: ${compressed.size} does not match raw length: ${raw.size}"
                )
            }
            for (i in compressed.indices) {
                val v = (raw[i].toInt() shl 4) or raw[compressed.size + i].toInt()
                compressed[i] = v.toByte()
            }
        }

        @Throws(IOException::class)
        fun load(
            configuration: OrdToDocDISIReaderConfiguration,
            dimension: Int,
            size: Int,
            scalarQuantizer: ScalarQuantizer?,
            similarityFunction: VectorSimilarityFunction,
            vectorsScorer: FlatVectorsScorer,
            compress: Boolean,
            quantizedVectorDataOffset: Long,
            quantizedVectorDataLength: Long,
            vectorData: IndexInput
        ): OffHeapQuantizedByteVectorValues {
            if (configuration.isEmpty) {
                return EmptyOffHeapVectorValues(dimension, similarityFunction, vectorsScorer)
            }
            require(scalarQuantizer != null) { "scalarQuantizer is required for non-empty vectors" }
            val bytesSlice = vectorData.slice(
                "quantized-vector-data",
                quantizedVectorDataOffset,
                quantizedVectorDataLength
            )
            return if (configuration.isDense) {
                DenseOffHeapVectorValues(
                    dimension,
                    size,
                    scalarQuantizer,
                    compress,
                    similarityFunction,
                    vectorsScorer,
                    bytesSlice
                )
            } else {
                SparseOffHeapVectorValues(
                    configuration,
                    dimension,
                    size,
                    scalarQuantizer,
                    compress,
                    vectorData,
                    similarityFunction,
                    vectorsScorer,
                    bytesSlice
                )
            }
        }
    }

    /**
     * Dense vector values that are stored off-heap. This is the most common case when every doc has a
     * vector.
     */
    class DenseOffHeapVectorValues(
        dimension: Int,
        size: Int,
        scalarQuantizer: ScalarQuantizer,
        compress: Boolean,
        similarityFunction: VectorSimilarityFunction,
        vectorsScorer: FlatVectorsScorer,
        slice: IndexInput
    ) : OffHeapQuantizedByteVectorValues(
        dimension,
        size,
        scalarQuantizer,
        similarityFunction,
        vectorsScorer,
        compress,
        slice
    ) {
        @Throws(IOException::class)
        override fun copy(): DenseOffHeapVectorValues {
            return DenseOffHeapVectorValues(
                dimension,
                size,
                scalarQuantizerValue,
                compress,
                similarityFunction,
                vectorsScorer,
                slice!!.clone()
            )
        }

        override fun getAcceptOrds(acceptDocs: Bits?): Bits? {
            return acceptDocs
        }

        @Throws(IOException::class)
        override fun scorer(query: FloatArray): VectorScorer {
            val copy = copy()
            val iterator = copy.iterator()
            val vectorScorer: RandomVectorScorer =
                vectorsScorer.getRandomVectorScorer(similarityFunction, copy, query)
            return object : VectorScorer {
                @Throws(IOException::class)
                override fun score(): Float {
                    return vectorScorer.score(iterator.index())
                }

                override fun iterator(): DocIdSetIterator {
                    return iterator
                }
            }
        }

        override fun iterator(): DocIndexIterator {
            return createDenseIterator()
        }
    }

    private class SparseOffHeapVectorValues(
        private val configuration: OrdToDocDISIReaderConfiguration,
        dimension: Int,
        size: Int,
        scalarQuantizer: ScalarQuantizer,
        compress: Boolean,
        dataIn: IndexInput,
        similarityFunction: VectorSimilarityFunction,
        vectorsScorer: FlatVectorsScorer,
        slice: IndexInput
    ) : OffHeapQuantizedByteVectorValues(
        dimension,
        size,
        scalarQuantizer,
        similarityFunction,
        vectorsScorer,
        compress,
        slice
    ) {
        private val ordToDoc: DirectMonotonicReader = configuration.getDirectMonotonicReader(dataIn)
        private val disi: IndexedDISI = configuration.getIndexedDISI(dataIn)
        private val dataIn: IndexInput = dataIn

        override fun iterator(): DocIndexIterator {
            return IndexedDISI.asDocIndexIterator(disi)
        }

        @Throws(IOException::class)
        override fun copy(): SparseOffHeapVectorValues {
            return SparseOffHeapVectorValues(
                configuration,
                dimension,
                size,
                scalarQuantizerValue,
                compress,
                dataIn,
                similarityFunction,
                vectorsScorer,
                slice!!.clone()
            )
        }

        override fun ordToDoc(ord: Int): Int {
            return ordToDoc.get(ord.toLong()).toInt()
        }

        override fun getAcceptOrds(acceptDocs: Bits?): Bits? {
            if (acceptDocs == null) {
                return null
            }
            return object : Bits {
                override fun get(index: Int): Boolean {
                    return acceptDocs.get(ordToDoc(index))
                }

                override fun length(): Int {
                    return size
                }
            }
        }

        @Throws(IOException::class)
        override fun scorer(query: FloatArray): VectorScorer {
            val copy = copy()
            val iterator = copy.iterator()
            val vectorScorer: RandomVectorScorer =
                vectorsScorer.getRandomVectorScorer(similarityFunction, copy, query)
            return object : VectorScorer {
                @Throws(IOException::class)
                override fun score(): Float {
                    return vectorScorer.score(iterator.index())
                }

                override fun iterator(): DocIdSetIterator {
                    return iterator
                }
            }
        }
    }

    private class EmptyOffHeapVectorValues(
        dimension: Int,
        similarityFunction: VectorSimilarityFunction,
        vectorsScorer: FlatVectorsScorer
    ) : OffHeapQuantizedByteVectorValues(
        dimension,
        0,
        ScalarQuantizer(-1f, 1f, 7),
        similarityFunction,
        vectorsScorer,
        false,
        null
    ) {
        override fun size(): Int {
            return 0
        }

        override fun iterator(): DocIndexIterator {
            return createDenseIterator()
        }

        override fun copy(): EmptyOffHeapVectorValues {
            throw UnsupportedOperationException()
        }

        override fun vectorValue(targetOrd: Int): ByteArray {
            throw UnsupportedOperationException()
        }

        override fun ordToDoc(ord: Int): Int {
            throw UnsupportedOperationException()
        }

        override fun getAcceptOrds(acceptDocs: Bits?): Bits? {
            return null
        }

        override fun scorer(query: FloatArray): VectorScorer? {
            return null
        }
    }
}
