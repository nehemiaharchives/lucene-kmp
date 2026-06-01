package org.gnit.lucenekmp.analysis.miscellaneous

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute

/**
 * A filter to correct offsets that illegally go backwards.
 *
 * @deprecated Fix the token filters that create broken offsets in the first place.
 */
@Deprecated("")
class FixBrokenOffsetsFilter(`in`: TokenStream) : TokenFilter(`in`) {
    private var lastStartOffset = 0

    private val offsetAtt = addAttribute(OffsetAttribute::class)

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (!input.incrementToken()) {
            return false
        }
        fixOffsets()
        return true
    }

    @Throws(IOException::class)
    override fun end() {
        super.end()
        fixOffsets()
    }

    @Throws(IOException::class)
    override fun reset() {
        super.reset()
        lastStartOffset = 0
    }

    private fun fixOffsets() {
        var startOffset = offsetAtt.startOffset()
        var endOffset = offsetAtt.endOffset()
        if (startOffset < lastStartOffset) {
            startOffset = lastStartOffset
        }
        if (endOffset < startOffset) {
            endOffset = startOffset
        }
        offsetAtt.setOffset(startOffset, endOffset)
        lastStartOffset = startOffset
    }
}
