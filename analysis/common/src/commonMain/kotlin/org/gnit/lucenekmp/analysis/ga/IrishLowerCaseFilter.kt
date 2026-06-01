package org.gnit.lucenekmp.analysis.ga

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.jdkport.Character

/**
 * Normalises token text to lower case, handling t-prothesis and n-eclipsis (i.e., that 'nAthair'
 * should become 'n-athair')
 */
class IrishLowerCaseFilter(input: TokenStream) : TokenFilter(input) {
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)

    /** Create an IrishLowerCaseFilter that normalises Irish token text. */
    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (input.incrementToken()) {
            var chArray = termAtt.buffer()
            var chLen = termAtt.length
            var idx = 0

            if (chLen > 1 && (chArray[0] == 'n' || chArray[0] == 't') && isUpperVowel(chArray[1].code)) {
                chArray = termAtt.resizeBuffer(chLen + 1)
                for (i in chLen downTo 2) {
                    chArray[i] = chArray[i - 1]
                }
                chArray[1] = '-'
                termAtt.setLength(chLen + 1)
                idx = 2
                chLen += 1
            }

            var i = idx
            while (i < chLen) {
                i += Character.toChars(Character.toLowerCase(chArray[i].code), chArray, i)
            }
            return true
        } else {
            return false
        }
    }

    private fun isUpperVowel(v: Int): Boolean {
        when (v) {
            'A'.code,
            'E'.code,
            'I'.code,
            'O'.code,
            'U'.code,
            // vowels with acute accent (fada)
            '\u00c1'.code,
            '\u00c9'.code,
            '\u00cd'.code,
            '\u00d3'.code,
            '\u00da'.code -> return true
            else -> return false
        }
    }
}
