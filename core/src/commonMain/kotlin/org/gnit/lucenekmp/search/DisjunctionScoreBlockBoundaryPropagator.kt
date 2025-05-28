package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.jdkport.Arrays
import kotlin.math.max
import kotlin.math.min

/**
 * A helper to propagate block boundaries for disjunctions. Because a disjunction matches if any of
 * its sub clauses matches, it is tempting to return the minimum block boundary across all clauses.
 * The problem is that it might then make the query slow when the minimum competitive score is high
 * and low-scoring clauses don't drive iteration anymore. So this class computes block boundaries
 * only across clauses whose maximum score is greater than or equal to the minimum competitive
 * score, or the maximum scoring clause if there is no such clause.
 */
internal class DisjunctionScoreBlockBoundaryPropagator(scorers: MutableCollection<Scorer>) {
    private val scorers: Array<Scorer> = scorers.toTypedArray()
    private val maxScores: FloatArray
    private var leadIndex = 0

    init {
        for (scorer in this.scorers) {
            scorer.advanceShallow(0)
        }
        Arrays.sort<Scorer>(this.scorers, MAX_SCORE_COMPARATOR)

        maxScores = FloatArray(this.scorers.size)
        for (i in this.scorers.indices) {
            maxScores[i] = this.scorers[i].getMaxScore(DocIdSetIterator.NO_MORE_DOCS)
        }
    }

    /** See [Scorer.advanceShallow].  */
    @Throws(IOException::class)
    fun advanceShallow(target: Int): Int {
        // For scorers that are below the lead index, just propagate.
        for (i in 0..<leadIndex) {
            val s = scorers[i]
            if (s.docID() < target) {
                s.advanceShallow(target)
            }
        }

        // For scorers above the lead index, we take the minimum
        // boundary.
        val leadScorer = scorers[leadIndex]
        var upTo = leadScorer.advanceShallow(max(leadScorer.docID(), target))

        for (i in leadIndex + 1..<scorers.size) {
            val scorer = scorers[i]
            if (scorer.docID() <= target) {
                upTo = min(scorer.advanceShallow(target), upTo)
            }
        }

        // If the maximum scoring clauses are beyond `target`, then we use their
        // docID as a boundary. It helps not consider them when computing the
        // maximum score and get a lower score upper bound.
        for (i in scorers.size - 1 downTo leadIndex + 1) {
            val scorer = scorers[i]
            if (scorer.docID() > target) {
                upTo = min(upTo, scorer.docID() - 1)
            } else {
                break
            }
        }

        return upTo
    }

    /**
     * Set the minimum competitive score to filter out clauses that score less than this threshold.
     *
     * @see Scorer.setMinCompetitiveScore
     */
    @Throws(IOException::class)
    fun setMinCompetitiveScore(minScore: Float) {
        // Update the lead index if necessary
        while (leadIndex < maxScores.size - 1 && minScore > maxScores[leadIndex]) {
            leadIndex++
        }
    }

    companion object {
        private val MAX_SCORE_COMPARATOR = compareBy<Scorer> { scorer ->
            try {
                scorer.getMaxScore(DocIdSetIterator.NO_MORE_DOCS)
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }.thenBy { scorer ->
            scorer.iterator().cost()
        }
    }
}
