package org.gnit.lucenekmp.queries.spans

import okio.IOException
import org.gnit.lucenekmp.jdkport.assert

/**
 * A Spans that is formed from the ordered subspans of a SpanNearQuery where the subspans do not
 * overlap and have a maximum slop between them.
 *
 * <p>The formed spans only contains minimum slop matches.<br>
 * The matching slop is computed from the distance(s) between the non overlapping matching Spans.
 * <br>
 * Successive matches are always formed from the successive Spans of the SpanNearQuery.
 *
 * <p>The formed spans may contain overlaps when the slop is at least 1. For example, when querying
 * using
 *
 * <pre>t1 t2 t3</pre>
 *
 * with slop at least 1, the fragment:
 *
 * <pre>t1 t2 t1 t3 t2 t3</pre>
 *
 * matches twice:
 *
 * <pre>t1 t2 .. t3      </pre>
 *
 * <pre>      t1 .. t2 t3</pre>
 *
 * Expert: Only public for subclassing. Most implementations should not need this class
 */
class NearSpansOrdered(
    private val allowedSlop: Int,
    subSpans: List<Spans>,
) : ConjunctionSpans(subSpans) {
    protected var matchStart = -1
    protected var matchEnd = -1
    protected var matchWidth = -1

    init {
        this.atFirstInCurrentDoc = true // -1 startPosition/endPosition also at doc -1
    }

    @Throws(IOException::class)
    override fun twoPhaseCurrentDocMatches(): Boolean {
        assert(unpositioned())
        oneExhaustedInCurrentDoc = false
        while (subSpansInternal[0].nextStartPosition() != NO_MORE_POSITIONS && !oneExhaustedInCurrentDoc) {
            if (stretchToOrder() && matchWidth <= allowedSlop) {
                atFirstInCurrentDoc = true
                return true
            }
        }
        return false
    }

    private fun unpositioned(): Boolean {
        for (span in subSpansInternal) {
            if (span.startPosition() != -1) return false
        }
        return true
    }

    @Throws(IOException::class)
    override fun nextStartPosition(): Int {
        if (atFirstInCurrentDoc) {
            atFirstInCurrentDoc = false
            return matchStart
        }
        oneExhaustedInCurrentDoc = false
        while (subSpansInternal[0].nextStartPosition() != NO_MORE_POSITIONS && !oneExhaustedInCurrentDoc) {
            if (stretchToOrder() && matchWidth <= allowedSlop) {
                return matchStart
            }
        }
        return NO_MORE_POSITIONS.also {
            matchStart = it
            matchEnd = it
        }
    }

    /**
     * Order the subSpans within the same document by using nextStartPosition on all subSpans after
     * the first as little as necessary. Return true when the subSpans could be ordered in this way,
     * otherwise at least one is exhausted in the current doc.
     */
    @Throws(IOException::class)
    private fun stretchToOrder(): Boolean {
        var prevSpans = subSpansInternal[0]
        matchStart = prevSpans.startPosition()
        assert(prevSpans.startPosition() != NO_MORE_POSITIONS) { "prevSpans no start position $prevSpans" }
        assert(prevSpans.endPosition() != NO_MORE_POSITIONS)
        matchWidth = 0
        for (i in 1..<subSpansInternal.size) {
            val spans = subSpansInternal[i]
            assert(spans.startPosition() != NO_MORE_POSITIONS)
            assert(spans.endPosition() != NO_MORE_POSITIONS)
            if (advancePosition(spans, prevSpans.endPosition()) == NO_MORE_POSITIONS) {
                oneExhaustedInCurrentDoc = true
                return false
            }
            matchWidth += (spans.startPosition() - prevSpans.endPosition())
            prevSpans = spans
        }
        matchEnd = subSpansInternal[subSpansInternal.size - 1].endPosition()
        return true // all subSpans ordered and non overlapping
    }

    override fun startPosition(): Int {
        return if (atFirstInCurrentDoc) -1 else matchStart
    }

    override fun endPosition(): Int {
        return if (atFirstInCurrentDoc) -1 else matchEnd
    }

    override fun width(): Int {
        return matchWidth
    }

    @Throws(IOException::class)
    override fun collect(collector: SpanCollector) {
        for (span in subSpansInternal) {
            span.collect(collector)
        }
    }

    companion object {
        @Throws(IOException::class)
        private fun advancePosition(spans: Spans, position: Int): Int {
            if (spans is SpanNearQuery.GapSpans) {
                return spans.skipToPosition(position)
            }
            while (spans.startPosition() < position) {
                spans.nextStartPosition()
            }
            return spans.startPosition()
        }
    }
}
