package org.gnit.lucenekmp.analysis.core

import okio.IOException
import org.gnit.lucenekmp.analysis.CharacterUtils
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute

/**
 * Normalizes token text to UPPER CASE.
 *
 * <p><b>NOTE:</b> In Unicode, this transformation may lose information when the upper case
 * character represents more than one lower case character. Use this filter when you require
 * uppercase tokens. Use the [org.gnit.lucenekmp.analysis.LowerCaseFilter] for general search matching
 */
class UpperCaseFilter(
    `in`: TokenStream
) : TokenFilter(`in`) {
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)

    /**
     * Create a new UpperCaseFilter, that normalizes token text to upper case.
     *
     * @param in TokenStream to filter
     */

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (input.incrementToken()) {
            CharacterUtils.toUpperCase(termAtt.buffer(), 0, termAtt.length)
            return true
        }
        return false
    }
}

