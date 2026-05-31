package org.gnit.lucenekmp.analysis.classic

import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TypeAttribute
import okio.IOException

/** Normalizes tokens extracted with [ClassicTokenizer]. */
class ClassicFilter(input: TokenStream) : TokenFilter(input) {
    // this filters uses attribute type
    private val typeAtt: TypeAttribute = addAttribute(TypeAttribute::class)
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)

    /**
     * Returns the next token in the stream, or null at EOS.
     *
     * <p>Removes <code>'s</code> from the end of words.
     *
     * <p>Removes dots from acronyms.
     */
    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (!input.incrementToken()) {
            return false
        }

        val buffer = termAtt.buffer()
        val bufferLength = termAtt.length
        val type = typeAtt.type()

        if (type == APOSTROPHE_TYPE &&
            bufferLength >= 2 &&
            buffer[bufferLength - 2] == '\'' &&
            (buffer[bufferLength - 1] == 's' || buffer[bufferLength - 1] == 'S')
        ) {
            termAtt.setLength(bufferLength - 2)
        } else if (type == ACRONYM_TYPE) {
            var upto = 0
            for (i in 0..<bufferLength) {
                val c = buffer[i]
                if (c != '.') buffer[upto++] = c
            }
            termAtt.setLength(upto)
        }

        return true
    }

    companion object {
        private val APOSTROPHE_TYPE: String = ClassicTokenizer.TOKEN_TYPES[ClassicTokenizer.APOSTROPHE]
        private val ACRONYM_TYPE: String = ClassicTokenizer.TOKEN_TYPES[ClassicTokenizer.ACRONYM]
    }
}
