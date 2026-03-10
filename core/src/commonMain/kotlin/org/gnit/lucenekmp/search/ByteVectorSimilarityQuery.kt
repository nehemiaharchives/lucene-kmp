package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.search.knn.KnnCollectorManager
import org.gnit.lucenekmp.util.Bits

/**
 * Search for all (approximate) byte vectors above a similarity threshold.
 *
 * @lucene.experimental
 */
open class ByteVectorSimilarityQuery(
    field: String,
    private val target: ByteArray,
    traversalSimilarity: Float,
    resultSimilarity: Float,
    filter: Query? = null
) : AbstractVectorSimilarityQuery(field, traversalSimilarity, resultSimilarity, filter) {
    init {
        requireNotNull(target) { "target" }
    }

    constructor(field: String, target: ByteArray, resultSimilarity: Float, filter: Query?) :
        this(field, target, resultSimilarity, resultSimilarity, filter)

    constructor(field: String, target: ByteArray, resultSimilarity: Float) :
        this(field, target, resultSimilarity, resultSimilarity, null)

    @Throws(IOException::class)
    override fun createVectorScorer(context: LeafReaderContext): VectorScorer? {
        val vectorValues = context.reader().getByteVectorValues(field)
        if (vectorValues == null) {
            return null
        }
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
        return "${this::class.simpleName}[field=${this.field} target=[${target[0]}...] traversalSimilarity=$traversalSimilarity resultSimilarity=$resultSimilarity filter=$filter]"
    }

    override fun equals(other: Any?): Boolean {
        return sameClassAs(other) && super.equals(other) && target.contentEquals((other as ByteVectorSimilarityQuery).target)
    }

    override fun hashCode(): Int {
        return 31 * super.hashCode() + target.contentHashCode()
    }
}
