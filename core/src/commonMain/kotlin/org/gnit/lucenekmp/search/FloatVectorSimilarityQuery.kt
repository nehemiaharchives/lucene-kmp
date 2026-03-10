package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.FloatVectorValues
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.search.knn.KnnCollectorManager
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.VectorUtil

/**
 * Search for all (approximate) float vectors above a similarity threshold.
 *
 * @lucene.experimental
 */
open class FloatVectorSimilarityQuery(
    field: String,
    private val target: FloatArray,
    traversalSimilarity: Float,
    resultSimilarity: Float,
    filter: Query? = null
) : AbstractVectorSimilarityQuery(field, traversalSimilarity, resultSimilarity, filter) {
    init {
        VectorUtil.checkFinite(requireNotNull(target) { "target" })
    }

    /**
     * Search for all (approximate) float vectors above a similarity threshold using [
     * VectorSimilarityCollector].
     *
     * @param field a field that has been indexed as a `KnnFloatVectorField`.
     * @param target the target of the search.
     * @param traversalSimilarity (lower) similarity score for graph traversal.
     * @param resultSimilarity (higher) similarity score for result collection.
     */
    constructor(field: String, target: FloatArray, traversalSimilarity: Float, resultSimilarity: Float) :
        this(field, target, traversalSimilarity, resultSimilarity, null)

    /**
     * Search for all (approximate) float vectors above a similarity threshold using [
     * VectorSimilarityCollector]. If a filter is applied, it traverses as many nodes as the cost of
     * the filter, and then falls back to exact search if results are incomplete.
     *
     * @param field a field that has been indexed as a `KnnFloatVectorField`.
     * @param target the target of the search.
     * @param resultSimilarity similarity score for result collection.
     * @param filter a filter applied before the vector search.
     */
    constructor(field: String, target: FloatArray, resultSimilarity: Float, filter: Query?) :
        this(field, target, resultSimilarity, resultSimilarity, filter)

    /**
     * Search for all (approximate) float vectors above a similarity threshold using [
     * VectorSimilarityCollector].
     *
     * @param field a field that has been indexed as a `KnnFloatVectorField`.
     * @param target the target of the search.
     * @param resultSimilarity similarity score for result collection.
     */
    constructor(field: String, target: FloatArray, resultSimilarity: Float) :
        this(field, target, resultSimilarity, resultSimilarity, null)

    @Throws(IOException::class)
    override fun createVectorScorer(context: LeafReaderContext): VectorScorer? {
        val vectorValues: FloatVectorValues = context.reader().getFloatVectorValues(field) ?: return null
        return vectorValues.scorer(target)
    }

    @Throws(IOException::class)
    override fun approximateSearch(
        context: LeafReaderContext,
        acceptDocs: Bits?,
        visitLimit: Int,
        knnCollectorManager: KnnCollectorManager
    ): TopDocs {
        val collector = knnCollectorManager.newCollector(visitLimit, DEFAULT_STRATEGY, context)
        context.reader().searchNearestVectors(field, target, collector, acceptDocs)
        return collector.topDocs()
    }

    override fun toString(field: String?): String {
        return "${this::class.simpleName}[field=$field target=[${target[0]}...] traversalSimilarity=$traversalSimilarity resultSimilarity=$resultSimilarity filter=$filter]"
    }

    override fun equals(other: Any?): Boolean {
        return sameClassAs(other) && super.equals(other) && target.contentEquals((other as FloatVectorSimilarityQuery).target)
    }

    override fun hashCode(): Int {
        return 31 * super.hashCode() + target.contentHashCode()
    }
}
