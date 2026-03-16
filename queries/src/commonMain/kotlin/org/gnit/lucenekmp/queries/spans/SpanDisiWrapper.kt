package org.gnit.lucenekmp.queries.spans

import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.TwoPhaseIterator

/**
 * Wrapper used in [SpanDisiPriorityQueue].
 *
 * @lucene.internal
 */
class SpanDisiWrapper(
    val spans: Spans,
) {
    val iterator: DocIdSetIterator = spans
    val cost: Long = iterator.cost()
    val matchCost: Float
    var doc = -1
    var next: SpanDisiWrapper? = null

    // An approximation of the iterator, or the iterator itself if it does not
    // support two-phase iteration
    val approximation: DocIdSetIterator

    // A two-phase view of the iterator, or null if the iterator does not support
    // two-phase iteration
    val twoPhaseView: TwoPhaseIterator?

    var lastApproxMatchDoc: Int
    var lastApproxNonMatchDoc: Int

    init {
        this.twoPhaseView = spans.asTwoPhaseIterator()
        if (twoPhaseView != null) {
            approximation = twoPhaseView.approximation()
            matchCost = twoPhaseView.matchCost()
        } else {
            approximation = iterator
            matchCost = 0f
        }
        this.lastApproxNonMatchDoc = -2
        this.lastApproxMatchDoc = -2
    }
}
