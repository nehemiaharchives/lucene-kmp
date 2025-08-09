package org.gnit.lucenekmp.search


/**
 * Wrapper used in [DisiPriorityQueue].
 *
 * @lucene.internal
 */
open class DisiWrapper(scorer: Scorer, impacts: Boolean) {
    var iterator: DocIdSetIterator? = null
    val scorer: Scorer
    val scorable: Scorable?
    val cost: Long
    var matchCost: Float = 0f // the match cost for two-phase iterators, 0 otherwise
    var doc: Int // the current doc, used for comparison
    var next: DisiWrapper? = null // reference to a next element, see #topList

    // An approximation of the iterator, or the iterator itself if it does not
    // support two-phase iteration
    var approximation: DocIdSetIterator? = null

    // A two-phase view of the iterator, or null if the iterator does not support
    // two-phase iteration
    val twoPhaseView: TwoPhaseIterator?

    // For WANDScorer
    var scaledMaxScore: Long = 0

    // for MaxScoreBulkScorer
    var maxWindowScore: Float = 0f

    init {
        this.scorer = scorer
        this.scorable = ScorerUtil.likelyTermScorer(scorer)
        if (impacts) {
            this.iterator = ScorerUtil.likelyImpactsEnum(scorer.iterator())
        } else {
            this.iterator = scorer.iterator()
        }
        this.cost = iterator!!.cost()
        this.doc = -1
        this.twoPhaseView = scorer.twoPhaseIterator()

        if (twoPhaseView != null) {
            approximation = twoPhaseView.approximation()
            matchCost = twoPhaseView.matchCost()
        } else {
            approximation = iterator
            matchCost = 0f
        }
    }
}
