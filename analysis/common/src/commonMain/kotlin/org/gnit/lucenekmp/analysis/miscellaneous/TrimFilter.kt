package org.gnit.lucenekmp.analysis.miscellaneous

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.jdkport.Character

/** Trims leading and trailing whitespace from Tokens in the stream. */
class TrimFilter(`in`: TokenStream) : TokenFilter(`in`) {
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)

    /**
     * Create a new [TrimFilter].
     *
     * @param in the stream to consume
     */
    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (!input.incrementToken()) return false

        val termBuffer = termAtt.buffer()
        val len = termAtt.length
        // TODO: Is this the right behavior or should we return false?  Currently, "  ", returns true,
        // so I think this should
        // also return true
        if (len == 0) {
            return true
        }
        var start: Int
        var end: Int

        // eat the first characters
        start = 0
        while (start < len && Character.isWhitespace(termBuffer[start].code)) {
            start++
        }
        // eat the end characters
        end = len
        while (end >= start && Character.isWhitespace(termBuffer[end - 1].code)) {
            end--
        }
        if (start > 0 || end < len) {
            if (start < end) {
                termAtt.copyBuffer(termBuffer, start, end - start)
            } else {
                termAtt.setEmpty()
            }
        }

        return true
    }
}
