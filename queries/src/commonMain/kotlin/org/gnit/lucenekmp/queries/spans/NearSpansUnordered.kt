package org.gnit.lucenekmp.queries.spans

import okio.IOException
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.util.PriorityQueue

/**
 * Similar to [NearSpansOrdered], but for the unordered case.
 */
class NearSpansUnordered(
    private val allowedSlop: Int,
    subSpans: List<Spans>,
) : ConjunctionSpans(subSpans) {
    private val spanWindow = SpanTotalLengthEndPositionWindow()

    /** Maintain totalSpanLength and maxEndPosition */
    private inner class SpanTotalLengthEndPositionWindow : PriorityQueue<Spans>(this@NearSpansUnordered.subSpansInternal.size) {
        var totalSpanLength = 0
        var maxEndPosition = -1

        override fun lessThan(a: Spans, b: Spans): Boolean {
            return positionsOrdered(a, b)
        }

        @Throws(IOException::class)
        fun startDocument() {
            clear()
            totalSpanLength = 0
            maxEndPosition = -1
            for (spans in subSpansInternal) {
                assert(spans.startPosition() == -1)
                spans.nextStartPosition()
                assert(spans.startPosition() != NO_MORE_POSITIONS)
                add(spans)
                if (spans.endPosition() > maxEndPosition) {
                    maxEndPosition = spans.endPosition()
                }
                val spanLength = spans.endPosition() - spans.startPosition()
                assert(spanLength >= 0)
                totalSpanLength += spanLength
            }
        }

        @Throws(IOException::class)
        fun nextPosition(): Boolean {
            val topSpans = top()
            assert(topSpans.startPosition() != NO_MORE_POSITIONS)
            var spanLength = topSpans.endPosition() - topSpans.startPosition()
            val nextStartPos = topSpans.nextStartPosition()
            if (nextStartPos == NO_MORE_POSITIONS) {
                return false
            }
            totalSpanLength -= spanLength
            spanLength = topSpans.endPosition() - topSpans.startPosition()
            totalSpanLength += spanLength
            if (topSpans.endPosition() > maxEndPosition) {
                maxEndPosition = topSpans.endPosition()
            }
            updateTop()
            return true
        }

        fun atMatch(): Boolean {
            return (maxEndPosition - top().startPosition() - totalSpanLength) <= allowedSlop
        }
    }

    @Throws(IOException::class)
    override fun twoPhaseCurrentDocMatches(): Boolean {
        spanWindow.startDocument()
        while (true) {
            if (spanWindow.atMatch()) {
                atFirstInCurrentDoc = true
                oneExhaustedInCurrentDoc = false
                return true
            }
            if (!spanWindow.nextPosition()) {
                return false
            }
        }
    }

    @Throws(IOException::class)
    override fun nextStartPosition(): Int {
        if (atFirstInCurrentDoc) {
            atFirstInCurrentDoc = false
            return spanWindow.top().startPosition()
        }
        assert(spanWindow.top().startPosition() != -1)
        assert(spanWindow.top().startPosition() != NO_MORE_POSITIONS)
        while (true) {
            if (!spanWindow.nextPosition()) {
                oneExhaustedInCurrentDoc = true
                return NO_MORE_POSITIONS
            }
            if (spanWindow.atMatch()) {
                return spanWindow.top().startPosition()
            }
        }
    }

    override fun startPosition(): Int {
        return if (atFirstInCurrentDoc) {
            -1
        } else if (oneExhaustedInCurrentDoc) {
            NO_MORE_POSITIONS
        } else {
            spanWindow.top().startPosition()
        }
    }

    override fun endPosition(): Int {
        return if (atFirstInCurrentDoc) {
            -1
        } else if (oneExhaustedInCurrentDoc) {
            NO_MORE_POSITIONS
        } else {
            spanWindow.maxEndPosition
        }
    }

    override fun width(): Int {
        return spanWindow.maxEndPosition - spanWindow.top().startPosition()
    }

    @Throws(IOException::class)
    override fun collect(collector: SpanCollector) {
        for (spans in subSpansInternal) {
            spans.collect(collector)
        }
    }

    companion object {
        /**
         * Check whether two Spans in the same document are ordered with possible overlap.
         */
        fun positionsOrdered(spans1: Spans, spans2: Spans): Boolean {
            assert(spans1.docID() == spans2.docID()) { "doc1 ${spans1.docID()} != doc2 ${spans2.docID()}" }
            val start1 = spans1.startPosition()
            val start2 = spans2.startPosition()
            return if (start1 == start2) spans1.endPosition() < spans2.endPosition() else start1 < start2
        }
    }
}
