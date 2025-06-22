package org.gnit.lucenekmp.util.hnsw

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.hnsw.HnswGraph
import org.gnit.lucenekmp.codecs.hnsw.DefaultFlatVectorScorer
import org.gnit.lucenekmp.index.VectorEncoding
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.KnnCollector
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.index.KnnVectorValues
import org.gnit.lucenekmp.search.TopKnnCollector

/**
 * Partial port of Lucene's HnswGraphTestCase.
 *
 * The original Java class contains extensive test logic which has not yet been
 * fully translated. This Kotlin version only defines the basic structure so
 * that tests depending on it can be implemented incrementally.
 */
abstract class HnswGraphTestCase<T> : LuceneTestCase() {
    protected lateinit var similarityFunction: VectorSimilarityFunction
    protected val flatVectorScorer = DefaultFlatVectorScorer()

    abstract fun getVectorEncoding(): VectorEncoding
    abstract fun knnQuery(field: String, vector: T, k: Int): Query
    abstract fun randomVector(dim: Int): T
    abstract fun vectorValues(size: Int, dimension: Int): KnnVectorValues
    abstract fun vectorValues(values: Array<FloatArray>): KnnVectorValues
    abstract fun vectorValues(reader: KnnVectorValues, fieldName: String): KnnVectorValues
    abstract fun vectorValues(
        size: Int,
        dimension: Int,
        pregeneratedVectorValues: KnnVectorValues,
        pregeneratedOffset: Int
    ): KnnVectorValues

    abstract fun knnVectorField(name: String, vector: T, similarityFunction: VectorSimilarityFunction): Any
    abstract fun circularVectorValues(nDoc: Int): KnnVectorValues
    abstract fun getTargetVector(): T

    // Placeholder for the heavy-weight tests in the original Java version
    open fun testReadWrite() = Unit
    open fun testRandomReadWriteAndMerge() = Unit
    open fun testSearchWithSkewedAcceptOrds() = Unit
    open fun testRamBytesUsed() = Unit
    open fun testNeighborsRefinement() = Unit

    /**
     * Utility to execute a search using the provided collector and graph.
     * The detailed implementation from the Java version is pending.
     */
    protected fun search(
        scorer: Any,
        topK: Int,
        graph: HnswGraph,
        accept: Bits?
    ): TopDocs {
        val collector = TopKnnCollector(topK, Int.MAX_VALUE)
        // TODO: implement real search logic
        return collector.topDocs()
    }
}
