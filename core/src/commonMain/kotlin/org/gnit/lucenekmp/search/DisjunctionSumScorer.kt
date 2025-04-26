package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.util.MathUtil
import kotlinx.io.IOException
import kotlin.math.min

/** A Scorer for OR like queries, counterpart of `ConjunctionScorer`.  */
internal class DisjunctionSumScorer
/**
 * Construct a `DisjunctionScorer`.
 *
 * @param scorers Array of at least two subscorers.
 */(private val scorers: MutableList<Scorer>, scoreMode: ScoreMode?, leadCost: Long) : DisjunctionScorer(
    scorers, scoreMode, leadCost
) {
    @Throws(IOException::class)
    override fun score(topList: DisiWrapper?): Float {
        var score = 0.0

        var w = topList
        while (w != null) {
            score += w.scorable!!.score()
            w = w.next
        }
        return score.toFloat()
    }

    @Throws(IOException::class)
    override fun advanceShallow(target: Int): Int {
        var min = DocIdSetIterator.NO_MORE_DOCS
        for (scorer in scorers) {
            if (scorer.docID() <= target) {
                min = min(min, scorer.advanceShallow(target))
            }
        }
        return min
    }

    @Throws(IOException::class)
    override fun getMaxScore(upTo: Int): Float {
        var maxScore = 0.0
        for (scorer in scorers) {
            if (scorer.docID() <= upTo) {
                maxScore += scorer.getMaxScore(upTo)
            }
        }
        return MathUtil.sumUpperBound(maxScore, scorers.size).toFloat()
    }
}
