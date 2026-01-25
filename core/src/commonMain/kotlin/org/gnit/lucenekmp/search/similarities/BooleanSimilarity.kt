package org.gnit.lucenekmp.search.similarities

import org.gnit.lucenekmp.search.CollectionStatistics
import org.gnit.lucenekmp.search.Explanation
import org.gnit.lucenekmp.search.TermStatistics


/**
 * Simple similarity that gives terms a score that is equal to their query boost. This similarity is
 * typically used with disabled norms since neither document statistics nor index statistics are
 * used for scoring. That said, if norms are enabled, they will be computed the same way as [ ] and [BM25Similarity] with [ discounted overlaps][SimilarityBase.getDiscountOverlaps] so that the [Similarity] can be changed after the index has been
 * created.
 */
class BooleanSimilarity
/** Sole constructor  */
    : Similarity() {
    override fun scorer(
        boost: Float,
        collectionStats: CollectionStatistics,
        vararg termStats: TermStatistics
    ): SimScorer {
        return BooleanWeight(boost)
    }

    private class BooleanWeight(val boost: Float) :
        SimScorer() {
        override fun score(freq: Float, norm: Long): Float {
            return boost
        }

        override fun explain(
            freq: Explanation,
            norm: Long
        ): Explanation {
            val queryBoostExpl: Explanation =
                Explanation.match(boost, "boost, query boost")
            return Explanation.match(
                queryBoostExpl.value,
                "score(" + this::class.simpleName + "), computed from:",
                queryBoostExpl
            )
        }
    }
}
