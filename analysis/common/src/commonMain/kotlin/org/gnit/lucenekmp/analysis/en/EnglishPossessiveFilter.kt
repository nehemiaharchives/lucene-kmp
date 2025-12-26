package org.gnit.lucenekmp.analysis.en

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute

/** TokenFilter that removes possessives (trailing 's) from words. */
class EnglishPossessiveFilter(input: TokenStream) : TokenFilter(input) {
    private val termAtt = addAttribute(CharTermAttribute::class)

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (!input.incrementToken()) {
            return false
        }

        val buffer = termAtt.buffer()
        val bufferLength = termAtt.length

        if (bufferLength >= 2
            && (buffer[bufferLength - 2] == '\''
                || buffer[bufferLength - 2] == '\u2019'
                || buffer[bufferLength - 2] == '\uFF07')
            && (buffer[bufferLength - 1] == 's' || buffer[bufferLength - 1] == 'S')
        ) {
            termAtt.setLength(bufferLength - 2)
        }

        return true
    }
}
