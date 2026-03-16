package org.gnit.lucenekmp.queries.spans

import okio.IOException
import org.gnit.lucenekmp.search.DocIdSetIterator

/**
 * A [DocIdSetIterator] which is a disjunction of the approximations of the provided iterators.
 *
 * @lucene.internal
 */
internal class SpanDisjunctionDISIApproximation(
    val subIterators: SpanDisiPriorityQueue,
) : DocIdSetIterator() {
    val cost: Long

    init {
        var cost = 0L
        for (w in subIterators) {
            cost += w.cost
        }
        this.cost = cost
    }

    override fun cost(): Long {
        return cost
    }

    override fun docID(): Int {
        return subIterators.top().doc
    }

    @Throws(IOException::class)
    override fun nextDoc(): Int {
        var top = subIterators.top()
        val doc = top.doc
        do {
            top.doc = top.approximation.nextDoc()
            top = subIterators.updateTop()
        } while (top.doc == doc)

        return top.doc
    }

    @Throws(IOException::class)
    override fun advance(target: Int): Int {
        var top = subIterators.top()
        do {
            top.doc = top.approximation.advance(target)
            top = subIterators.updateTop()
        } while (top.doc < target)

        return top.doc
    }
}
