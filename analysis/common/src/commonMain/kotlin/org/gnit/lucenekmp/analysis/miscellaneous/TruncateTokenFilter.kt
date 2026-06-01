package org.gnit.lucenekmp.analysis.miscellaneous

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.KeywordAttribute

/**
 * A token filter for truncating the terms into a specific length. Fixed prefix truncation, as a
 * stemming method, produces good results on Turkish language. It is reported that F5, using first 5
 * characters, produced best results in <a
 * href="http://www.users.muohio.edu/canf/papers/JASIST2008offPrint.pdf">Information Retrieval on
 * Turkish Texts</a>
 */
class TruncateTokenFilter(input: TokenStream, private val length: Int) : TokenFilter(input) {
    private val termAttribute = addAttribute(CharTermAttribute::class)
    private val keywordAttr = addAttribute(KeywordAttribute::class)

    init {
        if (length < 1) {
            throw IllegalArgumentException("length parameter must be a positive number: $length")
        }
    }

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (input.incrementToken()) {
            if (!keywordAttr.isKeyword && termAttribute.length > length) {
                termAttribute.setLength(length)
            }
            return true
        } else {
            return false
        }
    }
}
