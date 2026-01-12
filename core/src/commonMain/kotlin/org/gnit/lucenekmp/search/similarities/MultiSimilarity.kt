package org.gnit.lucenekmp.search.similarities

import org.gnit.lucenekmp.index.FieldInvertState
import org.gnit.lucenekmp.search.CollectionStatistics
import org.gnit.lucenekmp.search.Explanation
import org.gnit.lucenekmp.search.TermStatistics


/**
 * Implements the CombSUM method for combining evidence from multiple similarity values described
 * in: Joseph A. Shaw, Edward A. Fox. In Text REtrieval Conference (1993), pp. 243-252
 *
 * @lucene.experimental
 */
class MultiSimilarity(
    /** the sub-similarities used to create the combined score  */
    protected val sims: Array<Similarity>
) :
    Similarity() {

    override fun computeNorm(state: FieldInvertState): Long {
        return sims[0].computeNorm(state)
    }

    override fun scorer(
        boost: Float,
        collectionStats: CollectionStatistics,
        vararg termStats: TermStatistics
    ): SimScorer {
        val subScorers: Array<SimScorer> = Array(sims.size) { i ->
            sims[i].scorer(boost, collectionStats, *termStats)
        }
        return MultiSimScorer(subScorers)
    }

    internal class MultiSimScorer(private val subScorers: Array<SimScorer>) :
        SimScorer() {

        override fun score(freq: Float, norm: Long): Float {
            var sum = 0.0
            for (subScorer in subScorers) {
                sum += subScorer.score(freq, norm).toDouble()
            }
            return sum.toFloat()
        }

        override fun explain(
            freq: Explanation,
            norm: Long
        ): Explanation {
            val subs: MutableList<Explanation> = mutableListOf()
            for (subScorer in subScorers) {
                subs.add(subScorer.explain(freq, norm))
            }
            return Explanation.match(
                score(
                    freq.value.toFloat(),
                    norm
                ), "sum of:", subs
            )
        }
    }
}
