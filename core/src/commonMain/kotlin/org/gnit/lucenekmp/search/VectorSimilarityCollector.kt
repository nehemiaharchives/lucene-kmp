package org.gnit.lucenekmp.search

import kotlin.math.max
import kotlin.math.min

/**
 * Perform a similarity-based graph search.
 *
 * @lucene.experimental
 */
class VectorSimilarityCollector(
    private val traversalSimilarity: Float,
    private val resultSimilarity: Float,
    visitLimit: Long
) : AbstractKnnCollector(1, visitLimit, AbstractVectorSimilarityQuery.DEFAULT_STRATEGY) {
    private var maxSimilarity: Float = Float.NEGATIVE_INFINITY
    private val scoreDocList: MutableList<ScoreDoc> = ArrayList()

    init {
        require(traversalSimilarity <= resultSimilarity) {
            "traversalSimilarity should be <= resultSimilarity"
        }
    }

    override fun collect(docId: Int, similarity: Float): Boolean {
        maxSimilarity = max(maxSimilarity, similarity)
        if (similarity >= resultSimilarity) {
            scoreDocList.add(ScoreDoc(docId, similarity))
        }
        return true
    }

    override fun minCompetitiveSimilarity(): Float {
        return min(traversalSimilarity, maxSimilarity)
    }

    override fun topDocs(): TopDocs {
        val relation =
            if (earlyTerminated()) {
                TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO
            } else {
                TotalHits.Relation.EQUAL_TO
            }
        return TopDocs(TotalHits(visitedCount(), relation), scoreDocList.toTypedArray())
    }

    override fun numCollected(): Int {
        return scoreDocList.size
    }
}
