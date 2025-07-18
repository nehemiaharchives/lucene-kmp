package org.gnit.lucenekmp.codecs.lucene95


import org.gnit.lucenekmp.codecs.hnsw.FlatVectorsScorer
import org.gnit.lucenekmp.codecs.lucene90.IndexedDISI
import org.gnit.lucenekmp.index.FloatVectorValues
import org.gnit.lucenekmp.index.VectorEncoding
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.VectorScorer
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.RandomAccessInput
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.hnsw.RandomVectorScorer
import org.gnit.lucenekmp.util.packed.DirectMonotonicReader
import okio.IOException

/** Read the vector values from the index input. This supports both iterated and random access.  */
abstract class OffHeapFloatVectorValues internal constructor(
    protected val dimension: Int,
    protected val size: Int,
    override val slice: IndexInput?,
    protected val byteSize: Int,
    protected val flatVectorsScorer: FlatVectorsScorer,
    protected val similarityFunction: VectorSimilarityFunction
) : FloatVectorValues(), HasIndexSlice {
    protected var lastOrd: Int = -1
    protected val value: FloatArray = FloatArray(dimension)

    override fun dimension(): Int {
        return dimension
    }

    override fun size(): Int {
        return size
    }

    @Throws(IOException::class)
    override fun vectorValue(targetOrd: Int): FloatArray {
        if (lastOrd == targetOrd) {
            return value
        }
        slice!!.seek(targetOrd.toLong() * byteSize)
        slice!!.readFloats(value, 0, value.size)
        lastOrd = targetOrd
        return value
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
        similarityFunction: VectorSimilarityFunction
    ) : OffHeapFloatVectorValues(dimension, size, slice, byteSize, flatVectorsScorer, similarityFunction) {
        @Throws(IOException::class)
        override fun copy(): DenseOffHeapVectorValues {
            return DenseOffHeapVectorValues(
                dimension, size, slice!!.clone(), byteSize, flatVectorsScorer, similarityFunction
            )
        }

        override fun ordToDoc(ord: Int): Int {
            return ord
        }

        override fun getAcceptOrds(acceptDocs: Bits?): Bits? {
            return acceptDocs
        }

        override fun iterator(): DocIndexIterator {
            return createDenseIterator()
        }

        @Throws(IOException::class)
        override fun scorer(query: FloatArray): VectorScorer {
            val copy = copy()
            val iterator: DocIndexIterator = copy.iterator()
            val randomVectorScorer: RandomVectorScorer =
                flatVectorsScorer.getRandomVectorScorer(similarityFunction, copy, query)
            return object : VectorScorer {
                @Throws(IOException::class)
                override fun score(): Float {
                    return randomVectorScorer.score(iterator.docID())
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
        similarityFunction: VectorSimilarityFunction
    ) : OffHeapFloatVectorValues(
        dimension,
        configuration.size,
        slice,
        byteSize,
        flatVectorsScorer,
        similarityFunction
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
        override fun scorer(query: FloatArray): VectorScorer {
            val copy = copy()
            val iterator: DocIndexIterator = copy.iterator()
            val randomVectorScorer: RandomVectorScorer =
                flatVectorsScorer.getRandomVectorScorer(similarityFunction, copy, query)
            return object : VectorScorer {
                @Throws(IOException::class)
                override fun score(): Float {
                    return randomVectorScorer.score(iterator.index())
                }

                override fun iterator(): DocIdSetIterator {
                    return iterator
                }
            }
        }
    }

    private class EmptyOffHeapVectorValues(
        dimension: Int,
        flatVectorsScorer: FlatVectorsScorer,
        similarityFunction: VectorSimilarityFunction
    ) : OffHeapFloatVectorValues(dimension, 0, null, 0, flatVectorsScorer, similarityFunction) {
        override fun dimension(): Int {
            return super.dimension()
        }

        override fun size(): Int {
            return 0
        }

        override fun copy(): EmptyOffHeapVectorValues {
            throw UnsupportedOperationException()
        }

        override fun vectorValue(targetOrd: Int): FloatArray {
            throw UnsupportedOperationException()
        }

        override fun iterator(): DocIndexIterator {
            return createDenseIterator()
        }

        override fun getAcceptOrds(acceptDocs: Bits?): Bits? {
            return null
        }

        override fun scorer(query: FloatArray): VectorScorer? {
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
        ): OffHeapFloatVectorValues {
            if (configuration.docsWithFieldOffset == -2L || vectorEncoding !== VectorEncoding.FLOAT32) {
                return EmptyOffHeapVectorValues(dimension, flatVectorsScorer, vectorSimilarityFunction)
            }
            val bytesSlice: IndexInput = vectorData.slice("vector-data", vectorDataOffset, vectorDataLength)
            val byteSize: Int = dimension * Float.SIZE_BYTES
            if (configuration.docsWithFieldOffset == -1L) {
                return DenseOffHeapVectorValues(
                    dimension,
                    configuration.size,
                    bytesSlice,
                    byteSize,
                    flatVectorsScorer,
                    vectorSimilarityFunction
                )
            } else {
                return SparseOffHeapVectorValues(
                    configuration,
                    vectorData,
                    bytesSlice,
                    dimension,
                    byteSize,
                    flatVectorsScorer,
                    vectorSimilarityFunction
                )
            }
        }
    }
}