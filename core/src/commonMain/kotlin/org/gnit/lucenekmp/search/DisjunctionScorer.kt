package org.gnit.lucenekmp.search

import kotlinx.io.IOException
import org.gnit.lucenekmp.util.PriorityQueue


/** Base class for Scorers that score disjunctions.  */
internal abstract class DisjunctionScorer protected constructor(
    subScorers: MutableList<Scorer>,
    scoreMode: ScoreMode?,
    leadCost: Long
) : Scorer() {
    private val numClauses: Int
    private val needsScores: Boolean

    private val approximation: DisjunctionDISIApproximation
    private var twoPhase: TwoPhase? = null

    init {
        require(subScorers.size > 1) { "There must be at least 2 subScorers" }
        this.numClauses = subScorers.size
        this.needsScores = scoreMode !== ScoreMode.COMPLETE_NO_SCORES
        var hasApproximation = false
        var sumMatchCost = 0f
        var sumApproxCost: Long = 0
        val wrappers: MutableList<DisiWrapper> = ArrayList<DisiWrapper>()
        for (scorer in subScorers) {
            val w = DisiWrapper(scorer, false)
            val costWeight: Long = if (w.cost <= 1) 1 else w.cost
            sumApproxCost += costWeight
            if (w.twoPhaseView != null) {
                hasApproximation = true
                sumMatchCost += w.matchCost * costWeight
            }
            wrappers.add(w)
        }
        this.approximation = DisjunctionDISIApproximation(wrappers, leadCost)

        if (hasApproximation == false) { // no sub scorer supports approximations
            twoPhase = null
        } else {
            val matchCost = sumMatchCost / sumApproxCost
            twoPhase = this.TwoPhase(approximation, matchCost)
        }
    }

    override fun iterator(): DocIdSetIterator {
        if (twoPhase != null) {
            return TwoPhaseIterator.asDocIdSetIterator(twoPhase!!)
        } else {
            return approximation
        }
    }

    override fun twoPhaseIterator(): TwoPhaseIterator? {
        return twoPhase
    }

    private inner class TwoPhase(approximation: DocIdSetIterator?, private val matchCost: Float) : TwoPhaseIterator(
        approximation!!
    ) {
        // list of verified matches on the current doc
        var verifiedMatches: DisiWrapper? = null

        // priority queue of approximations on the current doc that have not been verified yet
        val unverifiedMatches: PriorityQueue<DisiWrapper> = object : PriorityQueue<DisiWrapper>(numClauses) {
            override fun lessThan(a: DisiWrapper, b: DisiWrapper): Boolean {
                return a.matchCost < b.matchCost
            }
        }

        val subMatches: DisiWrapper?
            get() {
                // iteration order does not matter
                for (w in unverifiedMatches) {
                    if (w.twoPhaseView!!.matches()) {
                        w.next = verifiedMatches
                        verifiedMatches = w
                    }
                }
                unverifiedMatches.clear()
                return verifiedMatches
            }

        @Throws(IOException::class)
        override fun matches(): Boolean {
            verifiedMatches = null
            unverifiedMatches.clear()

            var w: DisiWrapper? = this@DisjunctionScorer.approximation.topList()
            while (w != null) {
                val next: DisiWrapper? = w.next

                if (w.twoPhaseView == null) {
                    // implicitly verified, move it to verifiedMatches
                    w.next = verifiedMatches
                    verifiedMatches = w

                    if (needsScores == false) {
                        // we can stop here
                        return true
                    }
                } else {
                    unverifiedMatches.add(w)
                }
                w = next
            }

            if (verifiedMatches != null) {
                return true
            }

            // verify subs that have an two-phase iterator
            // least-costly ones first
            while (unverifiedMatches.size() > 0) {
                val w: DisiWrapper = unverifiedMatches.pop()!!
                if (w.twoPhaseView!!.matches()) {
                    w.next = null
                    verifiedMatches = w
                    return true
                }
            }

            return false
        }

        override fun matchCost(): Float {
            return matchCost
        }
    }

    override fun docID(): Int {
        return approximation.docID()
    }

    val subMatches: DisiWrapper?
        get() {
            if (twoPhase == null) {
                return approximation.topList()
            } else {
                return twoPhase!!.subMatches
            }
        }

    @Throws(IOException::class)
    override fun score(): Float {
        return score(this.subMatches)
    }

    /** Compute the score for the given linked list of scorers.  */
    @Throws(IOException::class)
    protected abstract fun score(topList: DisiWrapper?): Float

    override val children: MutableCollection<ChildScorable>
        get() {
            val children: ArrayList<ChildScorable> = ArrayList<ChildScorable>()
            var scorer: DisiWrapper? = this.subMatches
            while (scorer != null) {
                children.add(ChildScorable(scorer.scorer, "SHOULD"))
                scorer = scorer.next
            }
            return children
        }
}
