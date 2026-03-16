package org.gnit.lucenekmp.queries.spans

import org.gnit.lucenekmp.jdkport.assert

/**
 * Matches spans near the beginning of a field.
 *
 * <p>This class is a simple extension of [SpanPositionRangeQuery] in that it assumes the
 * start to be zero and only checks the end boundary.
 */
class SpanFirstQuery(
    match: SpanQuery,
    end: Int,
) : SpanPositionRangeQuery(match, 0, end) {
    /**
     * Construct a SpanFirstQuery matching spans in `match` whose end position is less than
     * or equal to `end`.
     */
    override fun acceptPosition(spans: Spans): FilterSpans.AcceptStatus {
        assert(spans.startPosition() != spans.endPosition()) {
            "start equals end: ${spans.startPosition()}"
        }
        return if (spans.startPosition() >= endInternal) {
            FilterSpans.AcceptStatus.NO_MORE_IN_CURRENT_DOC
        } else if (spans.endPosition() <= endInternal) {
            FilterSpans.AcceptStatus.YES
        } else {
            FilterSpans.AcceptStatus.NO
        }
    }

    override fun toString(field: String?): String {
        val buffer = StringBuilder()
        buffer.append("spanFirst(")
        buffer.append(matchInternal.toString(field))
        buffer.append(", ")
        buffer.append(endInternal)
        buffer.append(")")
        return buffer.toString()
    }

    override fun clone(): SpanPositionCheckQuery {
        return SpanFirstQuery(matchInternal, endInternal)
    }
}
