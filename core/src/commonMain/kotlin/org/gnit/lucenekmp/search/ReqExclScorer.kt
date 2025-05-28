package org.gnit.lucenekmp.search

import okio.IOException
import kotlin.jvm.JvmName
import kotlin.math.min


/**
 * A Scorer for queries with a required subscorer and an excluding (prohibited) sub [Scorer].
 */
internal class ReqExclScorer(private val reqScorer: Scorer, exclScorer: Scorer) : Scorer() {
    // approximations of the scorers, or the scorers themselves if they don't support approximations
    private val reqApproximation: DocIdSetIterator
    private val exclApproximation: DocIdSetIterator

    // two-phase views of the scorers, or null if they do not support approximations
    private val reqTwoPhaseIterator = reqScorer.twoPhaseIterator()
    private val exclTwoPhaseIterator: TwoPhaseIterator?

    override fun iterator(): DocIdSetIterator {
        return TwoPhaseIterator.asDocIdSetIterator(twoPhaseIterator()!!)
    }

    override fun docID(): Int {
        return reqApproximation.docID()
    }

    @Throws(IOException::class)
    override fun score(): Float {
        return reqScorer.score() // reqScorer may be null when next() or skipTo() already return false
    }

    @Throws(IOException::class)
    override fun advanceShallow(target: Int): Int {
        return reqScorer.advanceShallow(target)
    }

    @Throws(IOException::class)
    override fun getMaxScore(upTo: Int): Float {
        return reqScorer.getMaxScore(upTo)
    }

    @JvmName("setMinCompetitiveScoreKt")
    @Throws(IOException::class)
    fun setMinCompetitiveScore(score: Float) {
        // The score of this scorer is the same as the score of 'reqScorer'.
        reqScorer.minCompetitiveScore = score
    }

    override val children: MutableCollection<ChildScorable>
        get() = mutableSetOf(ChildScorable(reqScorer, "MUST"))

    /**
     * Construct a `ReqExclScorer`.
     *
     * @param reqScorer The scorer that must match, except where
     * @param exclScorer indicates exclusion.
     */
    init {
        reqApproximation = reqTwoPhaseIterator?.approximation() ?: reqScorer.iterator()
        exclTwoPhaseIterator = exclScorer.twoPhaseIterator()
        exclApproximation = exclTwoPhaseIterator?.approximation() ?: exclScorer.iterator()
    }

    override fun twoPhaseIterator(): TwoPhaseIterator? {
        val matchCost =
            matchCost(reqApproximation, reqTwoPhaseIterator, exclApproximation, exclTwoPhaseIterator)

        if (reqTwoPhaseIterator == null
            || (exclTwoPhaseIterator != null
                    && reqTwoPhaseIterator.matchCost() <= exclTwoPhaseIterator.matchCost())
        ) {
            // reqTwoPhaseIterator is LESS costly than exclTwoPhaseIterator, check it first
            return object : TwoPhaseIterator(reqApproximation) {
                @Throws(IOException::class)
                override fun matches(): Boolean {
                    val doc = reqApproximation.docID()
                    // check if the doc is not excluded
                    var exclDoc = exclApproximation.docID()
                    if (exclDoc < doc) {
                        exclDoc = exclApproximation.advance(doc)
                    }
                    if (exclDoc != doc) {
                        return matchesOrNull(reqTwoPhaseIterator)
                    }
                    return matchesOrNull(reqTwoPhaseIterator) && !matchesOrNull(exclTwoPhaseIterator)
                }

                override fun matchCost(): Float {
                    return matchCost
                }
            }
        } else {
            // reqTwoPhaseIterator is MORE costly than exclTwoPhaseIterator, check it last
            return object : TwoPhaseIterator(reqApproximation) {
                @Throws(IOException::class)
                override fun matches(): Boolean {
                    val doc = reqApproximation.docID()
                    // check if the doc is not excluded
                    var exclDoc = exclApproximation.docID()
                    if (exclDoc < doc) {
                        exclDoc = exclApproximation.advance(doc)
                    }
                    if (exclDoc != doc) {
                        return matchesOrNull(reqTwoPhaseIterator)
                    }
                    return !matchesOrNull(exclTwoPhaseIterator) && matchesOrNull(reqTwoPhaseIterator)
                }

                override fun matchCost(): Float {
                    return matchCost
                }
            }
        }
    }

    companion object {
        /** Confirms whether or not the given [TwoPhaseIterator] matches on the current document.  */
        @Throws(IOException::class)
        private fun matchesOrNull(it: TwoPhaseIterator?): Boolean {
            return it == null || it.matches()
        }

        /**
         * Estimation of the number of operations required to call DISI.advance. This is likely completely
         * wrong, especially given that the cost of this method usually depends on how far you want to
         * advance, but it's probably better than nothing.
         */
        private const val ADVANCE_COST = 10

        private fun matchCost(
            reqApproximation: DocIdSetIterator,
            reqTwoPhaseIterator: TwoPhaseIterator?,
            exclApproximation: DocIdSetIterator,
            exclTwoPhaseIterator: TwoPhaseIterator?
        ): Float {
            var matchCost = 2f // we perform 2 comparisons to advance exclApproximation
            if (reqTwoPhaseIterator != null) {
                // this two-phase iterator must always be matched
                matchCost += reqTwoPhaseIterator.matchCost()
            }

            // match cost of the prohibited clause: we need to advance the approximation
            // and match the two-phased iterator
            val exclMatchCost: Float =
                (ADVANCE_COST.toFloat() + (exclTwoPhaseIterator?.matchCost() ?: 0f))

            // upper value for the ratio of documents that reqApproximation matches that
            // exclApproximation also matches
            val ratio: Float = if (reqApproximation.cost() <= 0) {
                1f
            } else if (exclApproximation.cost() <= 0) {
                0f
            } else {
                (min(
                    reqApproximation.cost(),
                    exclApproximation.cost()
                ).toFloat() / reqApproximation.cost())
            }
            matchCost += ratio * exclMatchCost

            return matchCost
        }
    }
}
