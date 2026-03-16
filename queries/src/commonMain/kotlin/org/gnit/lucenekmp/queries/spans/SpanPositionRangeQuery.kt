package org.gnit.lucenekmp.queries.spans

import okio.IOException
import org.gnit.lucenekmp.jdkport.assert

/**
 * Checks to see if the [getMatch] lies between a start and end position
 *
 * <p>See [SpanFirstQuery] for a derivation that is optimized for the case where start
 * position is 0.
 */
open class SpanPositionRangeQuery(
    match: SpanQuery,
    protected var startInternal: Int,
    protected var endInternal: Int,
) : SpanPositionCheckQuery(match) {
    @Throws(IOException::class)
    override fun acceptPosition(spans: Spans): FilterSpans.AcceptStatus {
        assert(spans.startPosition() != spans.endPosition())
        val res =
            if (spans.startPosition() >= endInternal) {
                FilterSpans.AcceptStatus.NO_MORE_IN_CURRENT_DOC
            } else if (spans.startPosition() >= startInternal && spans.endPosition() <= endInternal) {
                FilterSpans.AcceptStatus.YES
            } else {
                FilterSpans.AcceptStatus.NO
            }
        return res
    }

    /**
     * @return The minimum position permitted in a match
     */
    fun getStart(): Int {
        return startInternal
    }

    /**
     * @return the maximum end position permitted in a match.
     */
    fun getEnd(): Int {
        return endInternal
    }

    override fun toString(field: String?): String {
        val buffer = StringBuilder()
        buffer.append("spanPosRange(")
        buffer.append(matchInternal.toString(field))
        buffer.append(", ").append(startInternal).append(", ")
        buffer.append(endInternal)
        buffer.append(")")
        return buffer.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (!super.equals(other)) {
            return false
        }
        other as SpanPositionRangeQuery
        return this.endInternal == other.endInternal && this.startInternal == other.startInternal
    }

    override fun hashCode(): Int {
        var h = super.hashCode() xor endInternal
        h = (h * 127) xor startInternal
        return h
    }

    override fun clone(): SpanPositionCheckQuery {
        return SpanPositionRangeQuery(matchInternal, startInternal, endInternal)
    }
}
