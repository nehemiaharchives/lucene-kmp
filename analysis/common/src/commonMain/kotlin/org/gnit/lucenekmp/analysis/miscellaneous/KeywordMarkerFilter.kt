package org.gnit.lucenekmp.analysis.miscellaneous

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.KeywordAttribute

/**
 * Marks terms as keywords via the KeywordAttribute.
 */
abstract class KeywordMarkerFilter(input: TokenStream) : TokenFilter(input) {
    private val keywordAttr: KeywordAttribute = addAttribute(KeywordAttribute::class)

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (input.incrementToken()) {
            if (isKeyword()) {
                keywordAttr.isKeyword = true
            }
            return true
        }
        return false
    }

    protected abstract fun isKeyword(): Boolean
}
