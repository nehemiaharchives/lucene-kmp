package org.gnit.lucenekmp.codecs.lucene95

import org.gnit.lucenekmp.codecs.hnsw.FlatVectorsScorer
import org.gnit.lucenekmp.codecs.lucene90.IndexedDISI
import org.gnit.lucenekmp.index.ByteVectorValues
import org.gnit.lucenekmp.index.VectorEncoding
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.VectorScorer
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.RandomAccessInput
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.hnsw.RandomVectorScorer
import org.gnit.lucenekmp.util.packed.DirectMonotonicReader
import kotlinx.io.IOException
import org.gnit.lucenekmp.jdkport.ByteBuffer

/** Read the vector values from the index input. This supports both iterated and random access.  */
abstract class OffHeapByteVectorValues internal constructor(
    protected val dimension: Int,
    protected val size: Int,
    override val slice: IndexInput?,
    protected val byteSize: Int,
    flatVectorsScorer: FlatVectorsScorer,
    similarityFunction: VectorSimilarityFunction
) : ByteVectorValues(), HasIndexSlice {
    protected var lastOrd: Int = -1
    protected val binaryValue: ByteArray
    protected val byteBuffer: ByteBuffer = ByteBuffer.allocate(byteSize)
    protected val similarityFunction: VectorSimilarityFunction
    protected val flatVectorsScorer: FlatVectorsScorer

    init {
        binaryValue = byteBuffer.array()
        this.similarityFunction = similarityFunction
        this.flatVectorsScorer = flatVectorsScorer
    }

    override fun dimension(): Int {
        return dimension
    }

    override fun size(): Int {
        return size
    }

    @Throws(IOException::class)
    override fun vectorValue(targetOrd: Int): ByteArray {
        if (lastOrd != targetOrd) {
            readValue(targetOrd)
            lastOrd = targetOrd
        }
        return binaryValue
    }

    override fun getSlice(): IndexInput? {
        return slice
    }

    @Throws(IOException::class)
    private fun readValue(targetOrd: Int) {
        slice!!.seek(targetOrd.toLong() * byteSize)
        slice!!.readBytes(byteBuffer.array(), byteBuffer.arrayOffset(), byteSize) // TODO needs test because byteBuffer.arrayoffset() is always 0 because of implementation difference caused by porting
    }

    /**
     * Dense vector values that are stored off-heap. This is the most common case when every doc has a
     * vector.
     */
    class DenseOffHeapVectorValues(
        dimension: Int,
        size: Int,
        slice: IndexInput,
        byteSize: Int,
        flatVectorsScorer: FlatVectorsScorer,
        vectorSimilarityFunction: VectorSimilarityFunction
    ) : OffHeapByteVectorValues(dimension, size, slice, byteSize, flatVectorsScorer, vectorSimilarityFunction) {
        @Throws(IOException::class)
        override fun copy(): DenseOffHeapVectorValues {
            return DenseOffHeapVectorValues(
                dimension, size, slice!!.clone(), byteSize, flatVectorsScorer, similarityFunction
            )
        }

        override fun iterator(): DocIndexIterator {
            return createDenseIterator()
        }

        override fun getAcceptOrds(acceptDocs: Bits?): Bits? {
            return acceptDocs
        }

        @Throws(IOException::class)
        override fun scorer(query: ByteArray): VectorScorer {
            val copy = copy()
            val iterator: DocIndexIterator = copy.iterator()
            val scorer: RandomVectorScorer =
                flatVectorsScorer.getRandomVectorScorer(similarityFunction, copy, query)
            return object : VectorScorer {
                @Throws(IOException::class)
                override fun score(): Float {
                    return scorer.score(iterator.docID())
                }

                override fun iterator(): DocIdSetIterator {
                    return iterator
                }
            }
        }
    }

    private class SparseOffHeapVectorValues(
        private val configuration: OrdToDocDISIReaderConfiguration,
        dataIn: IndexInput,
        slice: IndexInput,
        dimension: Int,
        byteSize: Int,
        flatVectorsScorer: FlatVectorsScorer,
        vectorSimilarityFunction: VectorSimilarityFunction
    ) : OffHeapByteVectorValues(
        dimension,
        configuration.size,
        slice,
        byteSize,
        flatVectorsScorer,
        vectorSimilarityFunction
    ) {
        private val ordToDoc: DirectMonotonicReader
        private val disi: IndexedDISI

        // dataIn was used to init a new IndexedDIS for #randomAccess()
        private val dataIn: IndexInput

        init {
            val addressesData: RandomAccessInput =
                dataIn.randomAccessSlice(configuration.addressesOffset, configuration.addressesLength)
            this.dataIn = dataIn
            this.ordToDoc = DirectMonotonicReader.getInstance(configuration.meta!!, addressesData)
            this.disi =
                IndexedDISI(
                    dataIn,
                    configuration.docsWithFieldOffset,
                    configuration.docsWithFieldLength,
                    configuration.jumpTableEntryCount.toInt(),
                    configuration.denseRankPower,
                    configuration.size.toLong()
                )
        }

        @Throws(IOException::class)
        override fun copy(): SparseOffHeapVectorValues {
            return SparseOffHeapVectorValues(
                configuration,
                dataIn,
                slice!!.clone(),
                dimension,
                byteSize,
                flatVectorsScorer,
                similarityFunction
            )
        }

        override fun ordToDoc(ord: Int): Int {
            return ordToDoc.get(ord.toLong()).toInt()
        }

        override fun iterator(): DocIndexIterator {
            return IndexedDISI.asDocIndexIterator(disi)
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
        override fun scorer(query: ByteArray): VectorScorer {
            val copy = copy()
            val scorer: RandomVectorScorer =
                flatVectorsScorer.getRandomVectorScorer(similarityFunction, copy, query)
            return object : VectorScorer {
                @Throws(IOException::class)
                override fun score(): Float {
                    return scorer.score(copy.disi.index())
                }

                override fun iterator(): DocIdSetIterator {
                    return copy.disi
                }
            }
        }
    }

    private class EmptyOffHeapVectorValues(
        dimension: Int,
        flatVectorsScorer: FlatVectorsScorer,
        vectorSimilarityFunction: VectorSimilarityFunction
    ) : OffHeapByteVectorValues(dimension, 0, null, 0, flatVectorsScorer, vectorSimilarityFunction) {
        override fun dimension(): Int {
            return super.dimension()
        }

        override fun size(): Int {
            return 0
        }

        @Throws(IOException::class)
        override fun vectorValue(ord: Int): ByteArray {
            throw UnsupportedOperationException()
        }

        override fun iterator(): DocIndexIterator {
            return createDenseIterator()
        }

        @Throws(IOException::class)
        override fun copy(): EmptyOffHeapVectorValues {
            throw UnsupportedOperationException()
        }

        override fun ordToDoc(ord: Int): Int {
            throw UnsupportedOperationException()
        }

        override fun getAcceptOrds(acceptDocs: Bits?): Bits? {
            return null
        }

        override fun scorer(query: ByteArray): VectorScorer? {
            return null
        }
    }

    companion object {
        @Throws(IOException::class)
        fun load(
            vectorSimilarityFunction: VectorSimilarityFunction,
            flatVectorsScorer: FlatVectorsScorer,
            configuration: OrdToDocDISIReaderConfiguration,
            vectorEncoding: VectorEncoding,
            dimension: Int,
            vectorDataOffset: Long,
            vectorDataLength: Long,
            vectorData: IndexInput
        ): OffHeapByteVectorValues {
            if (configuration.isEmpty || vectorEncoding !== VectorEncoding.BYTE) {
                return EmptyOffHeapVectorValues(dimension, flatVectorsScorer, vectorSimilarityFunction)
            }
            val bytesSlice: IndexInput = vectorData.slice("vector-data", vectorDataOffset, vectorDataLength)
            if (configuration.isDense) {
                return DenseOffHeapVectorValues(
                    dimension,
                    configuration.size,
                    bytesSlice,
                    dimension,
                    flatVectorsScorer,
                    vectorSimilarityFunction
                )
            } else {
                return SparseOffHeapVectorValues(
                    configuration,
                    vectorData,
                    bytesSlice,
                    dimension,
                    dimension,
                    flatVectorsScorer,
                    vectorSimilarityFunction
                )
            }
        }
    }
}
