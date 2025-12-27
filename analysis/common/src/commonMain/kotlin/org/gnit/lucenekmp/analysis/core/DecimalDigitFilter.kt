package org.gnit.lucenekmp.analysis.core

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.util.StemmerUtil
import org.gnit.lucenekmp.jdkport.Character

/**
 * Folds all Unicode digits in [:General_Category=Decimal_Number:] to Basic Latin digits (0-9).
 */
class DecimalDigitFilter(input: TokenStream) : TokenFilter(input) {
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (!input.incrementToken()) {
            return false
        }

        val buffer = termAtt.buffer()
        var length = termAtt.length

        var i = 0
        while (i < length) {
            val ch = Character.codePointAt(buffer, i, length)
            if (ch > 0x7F && Character.isDigit(ch)) {
                buffer[i] = ('0'.code + Character.getNumericValue(ch)).toChar()
                if (ch > 0xFFFF) {
                    length = StemmerUtil.delete(buffer, i + 1, length)
                    termAtt.setLength(length)
                }
            }
            i++
        }

        return true
    }
}
