package org.gnit.lucenekmp.queries.spans

import org.gnit.lucenekmp.util.PriorityQueue

internal class SpanPositionQueue(maxSize: Int) : PriorityQueue<Spans>(maxSize) {
    override fun lessThan(s1: Spans, s2: Spans): Boolean {
        val start1 = s1.startPosition()
        val start2 = s2.startPosition()
        return if (start1 < start2) {
            true
        } else if (start1 == start2) {
            s1.endPosition() < s2.endPosition()
        } else {
            false
        }
    }
}
