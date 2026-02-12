package org.gnit.lucenekmp.codecs.lucene102

import okio.IOException
import org.gnit.lucenekmp.codecs.hnsw.FlatVectorsScorer
import org.gnit.lucenekmp.codecs.lucene90.IndexedDISI
import org.gnit.lucenekmp.codecs.lucene95.OrdToDocDISIReaderConfiguration
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.jdkport.ByteBuffer
import org.gnit.lucenekmp.jdkport.toUnsignedInt
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.VectorScorer
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.hnsw.RandomVectorScorer
import org.gnit.lucenekmp.util.packed.DirectMonotonicReader
import org.gnit.lucenekmp.util.quantization.OptimizedScalarQuantizer
import kotlin.properties.Delegates

/**
 * Binarized vector values loaded from off-heap
 *
 * @lucene.internal
 */
abstract class OffHeapBinarizedVectorValues internal constructor(
    val dimension: Int,
    val size: Int,
    centroid: FloatArray?,
    centroidDp: Float,
    quantizer: OptimizedScalarQuantizer?,
    val similarityFunction: VectorSimilarityFunction,
    val vectorsScorer: FlatVectorsScorer?,
    val slice: IndexInput?
) : BinarizedByteVectorValues() {
    override var vectorByteLength by Delegates.notNull<Int>()

    val binaryValue: ByteArray
    val byteBuffer: ByteBuffer
    val byteSize: Int
    private var lastOrd = -1
    val correctiveValues: FloatArray
    var quantizedComponentSum: Int = 0
    val binaryQuantizer: OptimizedScalarQuantizer?
    override var centroid: FloatArray? = null
    override var centroidDP by Delegates.notNull<Float>()
    val discretizedDimensions: Int

    init {
        this.centroid = centroid
        this.centroidDP = centroidDp
        this.vectorByteLength = OptimizedScalarQuantizer.discretize(dimension, 64) / 8
        this.correctiveValues = FloatArray(3)
        this.byteSize = this.vectorByteLength + (Float.SIZE_BYTES * 3) + Short.SIZE_BYTES
        this.byteBuffer = ByteBuffer.allocate(this.vectorByteLength)
        this.binaryValue = byteBuffer.array()
        this.binaryQuantizer = quantizer
        this.discretizedDimensions = OptimizedScalarQuantizer.discretize(dimension, 64)
    }

    override fun dimension(): Int {
        return dimension
    }

    override fun size(): Int {
        return size
    }

    @Throws(IOException::class)
    override fun vectorValue(targetOrd: Int): ByteArray {
        if (lastOrd == targetOrd) {
            return binaryValue
        }
        slice!!.seek(targetOrd.toLong() * byteSize)
        slice.readBytes(byteBuffer.array(), byteBuffer.arrayOffset(), this.vectorByteLength)
        slice.readFloats(correctiveValues, 0, 3)
        quantizedComponentSum = Short.toUnsignedInt(slice.readShort())
        lastOrd = targetOrd
        return binaryValue
    }

    /*public override fun discretizedDimensions(): Int {
        return discretizedDimensions
    }*/

    @Throws(IOException::class)
    override fun getCorrectiveTerms(targetOrd: Int): OptimizedScalarQuantizer.QuantizationResult {
        if (lastOrd == targetOrd) {
            return OptimizedScalarQuantizer.QuantizationResult(
                correctiveValues[0], correctiveValues[1], correctiveValues[2], quantizedComponentSum
            )
        }
        slice!!.seek((targetOrd.toLong() * byteSize) + this.vectorByteLength)
        slice.readFloats(correctiveValues, 0, 3)
        quantizedComponentSum = Short.toUnsignedInt(slice.readShort())
        return OptimizedScalarQuantizer.QuantizationResult(
            correctiveValues[0], correctiveValues[1], correctiveValues[2], quantizedComponentSum
        )
    }

    override val quantizer: OptimizedScalarQuantizer?
        get() = binaryQuantizer

    /** Dense off-heap binarized vector values  */
    internal class DenseOffHeapVectorValues(
        dimension: Int,
        size: Int,
        centroid: FloatArray?,
        centroidDp: Float,
        binaryQuantizer: OptimizedScalarQuantizer?,
        similarityFunction: VectorSimilarityFunction,
        vectorsScorer: FlatVectorsScorer?,
        slice: IndexInput
    ) : OffHeapBinarizedVectorValues(
        dimension,
        size,
        centroid,
        centroidDp,
        binaryQuantizer,
        similarityFunction,
        vectorsScorer,
        slice
    ) {
        @Throws(IOException::class)
        override fun copy(): DenseOffHeapVectorValues {
            return DenseOffHeapVectorValues(
                dimension,
                size,
                centroid,
                this.centroidDP,
                binaryQuantizer,
                similarityFunction,
                vectorsScorer,
                slice!!.clone()
            )
        }

        override fun getAcceptOrds(acceptDocs: Bits?): Bits? {
            return acceptDocs
        }

        @Throws(IOException::class)
        override fun scorer(target: FloatArray): VectorScorer {
            val copy = copy()
            val iterator: DocIndexIterator = copy.iterator()
            val scorer: RandomVectorScorer =
                vectorsScorer!!.getRandomVectorScorer(similarityFunction, copy, target)
            return object : VectorScorer {
                @Throws(IOException::class)
                override fun score(): Float {
                    return scorer.score(iterator.index())
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

    /** Sparse off-heap binarized vector values  */
    private class SparseOffHeapVectorValues(
        private val configuration: OrdToDocDISIReaderConfiguration,
        dimension: Int,
        size: Int,
        centroid: FloatArray?,
        centroidDp: Float,
        binaryQuantizer: OptimizedScalarQuantizer?,
        // dataIn was used to init a new IndexedDIS for #randomAccess()
        private val dataIn: IndexInput,
        similarityFunction: VectorSimilarityFunction,
        vectorsScorer: FlatVectorsScorer?,
        slice: IndexInput
    ) : OffHeapBinarizedVectorValues(
        dimension,
        size,
        centroid,
        centroidDp,
        binaryQuantizer,
        similarityFunction,
        vectorsScorer,
        slice
    ) {
        private val ordToDoc: DirectMonotonicReader = configuration.getDirectMonotonicReader(dataIn)
        private val disi: IndexedDISI = configuration.getIndexedDISI(dataIn)

        @Throws(IOException::class)
        override fun copy(): SparseOffHeapVectorValues {
            return SparseOffHeapVectorValues(
                configuration,
                dimension,
                size,
                centroid,
                this.centroidDP,
                binaryQuantizer,
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

        override fun iterator(): DocIndexIterator {
            return IndexedDISI.asDocIndexIterator(disi)
        }

        @Throws(IOException::class)
        override fun scorer(target: FloatArray): VectorScorer {
            val copy = copy()
            val iterator: DocIndexIterator = copy.iterator()
            val scorer: RandomVectorScorer =
                vectorsScorer!!.getRandomVectorScorer(similarityFunction, copy, target)
            return object : VectorScorer {
                @Throws(IOException::class)
                override fun score(): Float {
                    return scorer.score(iterator.index())
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
        vectorsScorer: FlatVectorsScorer?
    ) : OffHeapBinarizedVectorValues(dimension, 0, null, Float.NaN, null, similarityFunction, vectorsScorer, null) {
        override fun iterator(): DocIndexIterator {
            return createDenseIterator()
        }

        override fun copy(): DenseOffHeapVectorValues {
            throw UnsupportedOperationException()
        }

        override fun getAcceptOrds(acceptDocs: Bits?): Bits? {
            return null
        }

        override fun scorer(target: FloatArray): VectorScorer? {
            return null
        }
    }

    companion object {
        @Throws(IOException::class)
        fun load(
            configuration: OrdToDocDISIReaderConfiguration,
            dimension: Int,
            size: Int,
            binaryQuantizer: OptimizedScalarQuantizer,
            similarityFunction: VectorSimilarityFunction,
            vectorsScorer: Lucene102BinaryFlatVectorsScorer?,
            centroid: FloatArray?,
            centroidDp: Float,
            quantizedVectorDataOffset: Long,
            quantizedVectorDataLength: Long,
            vectorData: IndexInput
        ): OffHeapBinarizedVectorValues {
            if (configuration.isEmpty) {
                return EmptyOffHeapVectorValues(dimension, similarityFunction, vectorsScorer)
            }
            //checkNotNull(centroid)
            val bytesSlice: IndexInput = vectorData.slice("quantized-vector-data", quantizedVectorDataOffset, quantizedVectorDataLength)
            if (configuration.isDense) {
                return DenseOffHeapVectorValues(
                    dimension,
                    size,
                    centroid,
                    centroidDp,
                    binaryQuantizer,
                    similarityFunction,
                    vectorsScorer,
                    bytesSlice
                )
            } else {
                return SparseOffHeapVectorValues(
                    configuration,
                    dimension,
                    size,
                    centroid,
                    centroidDp,
                    binaryQuantizer,
                    vectorData,
                    similarityFunction,
                    vectorsScorer,
                    bytesSlice
                )
            }
        }
    }
}
