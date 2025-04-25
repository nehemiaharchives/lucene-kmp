package org.gnit.lucenekmp.analysis

import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import kotlinx.io.IOException

/** Normalizes token text to lower case.  */
class LowerCaseFilter
/**
 * Create a new LowerCaseFilter, that normalizes token text to lower case.
 *
 * @param in TokenStream to filter
 */
    (`in`: TokenStream) : TokenFilter(`in`) {
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (input.incrementToken()) {

            val termBuffer: CharArray = termAtt.buffer()
            val offset = 0
            val length: Int = termAtt.length

            CharacterUtils.toLowerCase(termBuffer, offset, length)

            return true
        } else {
            return false
        }
    }
}

