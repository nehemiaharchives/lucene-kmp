package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.MathUtil
import kotlinx.io.IOException
import kotlin.math.max
import kotlin.math.min

/**
 * BulkScorer implementation of [BlockMaxConjunctionScorer] that focuses on top-level
 * conjunctions over clauses that do not have two-phase iterators. Use a [DefaultBulkScorer]
 * around a [BlockMaxConjunctionScorer] if you need two-phase support. Another difference with
 * [BlockMaxConjunctionScorer] is that this scorer computes scores on the fly in order to be
 * able to skip evaluating more clauses if the total score would be under the minimum competitive
 * score anyway. This generally works well because computing a score is cheaper than decoding a
 * block of postings.
 */
internal class BlockMaxConjunctionBulkScorer(maxDoc: Int, scorers: MutableList<Scorer>) : BulkScorer() {
    private val scorers: Array<Scorer>
    private val iterators: Array<DocIdSetIterator>
    private val lead1: DocIdSetIterator
    private val lead2: DocIdSetIterator
    private val scorer1: Scorable
    private val scorer2: Scorable
    private val scorable = DocAndScore()
    private val sumOfOtherClauses: DoubleArray
    private val maxDoc: Int

    init {
        require(scorers.size > 1) { "Expected 2 or more scorers, got " + scorers.size }
        this.scorers = scorers.toTypedArray<Scorer>()
        this.scorers.sortBy { it.iterator().cost() }
        this.iterators =
            this.scorers.map(Scorer::iterator).toTypedArray<DocIdSetIterator>()
        lead1 = ScorerUtil.likelyImpactsEnum(iterators[0])
        lead2 = ScorerUtil.likelyImpactsEnum(iterators[1])
        scorer1 = ScorerUtil.likelyTermScorer(this.scorers[0])
        scorer2 = ScorerUtil.likelyTermScorer(this.scorers[1])
        this.sumOfOtherClauses = DoubleArray(this.scorers.size)
        for (i in sumOfOtherClauses.indices) {
            sumOfOtherClauses[i] = Double.Companion.POSITIVE_INFINITY
        }
        this.maxDoc = maxDoc
    }

    @Throws(IOException::class)
    private fun computeMaxScore(windowMin: Int, windowMax: Int): Float {
        for (i in scorers.indices) {
            scorers[i].advanceShallow(windowMin)
        }

        var maxWindowScore = 0.0
        for (i in scorers.indices) {
            val maxClauseScore = scorers[i].getMaxScore(windowMax)
            sumOfOtherClauses[i] = maxClauseScore.toDouble()
            maxWindowScore += maxClauseScore.toDouble()
        }
        for (i in sumOfOtherClauses.size - 2 downTo 0) {
            sumOfOtherClauses[i] += sumOfOtherClauses[i + 1]
        }
        return maxWindowScore.toFloat()
    }

    @Throws(IOException::class)
    override fun score(collector: LeafCollector, acceptDocs: Bits?, min: Int, max: Int): Int {
        collector.setScorer(scorable)

        var windowMin: Int = max(lead1.docID(), min)
        while (windowMin < max) {
            // Use impacts of the least costly scorer to compute windows
            // NOTE: windowMax is inclusive
            val windowMax: Int = min(scorers[0].advanceShallow(windowMin), max - 1)

            var maxWindowScore = Float.Companion.POSITIVE_INFINITY
            if (0 < scorable.minCompetitiveScore) {
                maxWindowScore = computeMaxScore(windowMin, windowMax)
            }
            scoreWindow(collector, acceptDocs, windowMin, windowMax + 1, maxWindowScore)
            windowMin = max(lead1.docID(), windowMax + 1)
        }

        return if (windowMin >= maxDoc) DocIdSetIterator.NO_MORE_DOCS else windowMin
    }

    @Throws(IOException::class)
    private fun scoreWindow(
        collector: LeafCollector, acceptDocs: Bits?, min: Int, max: Int, maxWindowScore: Float
    ) {
        if (maxWindowScore < scorable.minCompetitiveScore) {
            // no hits are competitive
            return
        }

        if (lead1.docID() < min) {
            lead1.advance(min)
        }

        val sumOfOtherMaxScoresAt1 = sumOfOtherClauses[1]

        var doc = lead1.docID()
        advanceHead@ while (doc < max) {
            if (acceptDocs != null && !acceptDocs.get(doc)) {
                doc = lead1.nextDoc()
                continue
            }

            // Compute the score as we find more matching clauses, in order to skip advancing other
            // clauses if the total score has no chance of being competitive. This works well because
            // computing a score is usually cheaper than decoding a full block of postings and
            // frequencies.
            val hasMinCompetitiveScore = scorable.minCompetitiveScore > 0
            var currentScore: Double = if (hasMinCompetitiveScore) {
                scorer1.score().toDouble()
            } else {
                0.0
            }

            // This is the same logic as in the below for loop, specialized for the 2nd least costly
            // clause. This seems to help the JVM.

            // First check if we have a chance of having a match based on max scores
            if (hasMinCompetitiveScore
                && ((MathUtil.sumUpperBound(currentScore + sumOfOtherMaxScoresAt1, scorers.size).toFloat())
                        < scorable.minCompetitiveScore)
            ) {
                doc = lead1.nextDoc()
                continue@advanceHead
            }

            // NOTE: lead2 may be on `doc` already if we `continue`d on the previous loop iteration.
            if (lead2.docID() < doc) {
                val next = lead2.advance(doc)
                if (next != doc) {
                    doc = lead1.advance(next)
                    continue@advanceHead
                }
            }
            require(lead2.docID() == doc)
            if (hasMinCompetitiveScore) {
                currentScore += scorer2.score()
            }

            for (i in 2..<iterators.size) {
                // First check if we have a chance of having a match based on max scores
                if (hasMinCompetitiveScore
                    && ((MathUtil.sumUpperBound(currentScore + sumOfOtherClauses[i], scorers.size).toFloat())
                            < scorable.minCompetitiveScore)
                ) {
                    doc = lead1.nextDoc()
                    continue@advanceHead
                }

                // NOTE: these iterators may be on `doc` already if we called `continue advanceHead` on the
                // previous loop iteration.
                if (iterators[i].docID() < doc) {
                    val next = iterators[i].advance(doc)
                    if (next != doc) {
                        doc = lead1.advance(next)
                        continue@advanceHead
                    }
                }
                require(iterators[i].docID() == doc)
                if (hasMinCompetitiveScore) {
                    currentScore += scorers[i].score()
                }
            }

            if (!hasMinCompetitiveScore) {
                for (scorer in scorers) {
                    currentScore += scorer.score()
                }
            }
            scorable.score = currentScore.toFloat()
            collector.collect(doc)
            // The collect() call may have updated the minimum competitive score.
            if (maxWindowScore < scorable.minCompetitiveScore) {
                // no more hits are competitive
                return
            }

            doc = lead1.nextDoc()
        }
    }

    override fun cost(): Long {
        return lead1.cost()
    }

    private class DocAndScore : Scorable() {
        var score: Float = 0f

        override var minCompetitiveScore: Float = 0f
            set(minScore) {
                this.minCompetitiveScore = minScore
            }

        @Throws(IOException::class)
        override fun score(): Float {
            return score
        }
    }
}
