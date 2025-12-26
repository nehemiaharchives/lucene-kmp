package org.gnit.lucenekmp.analysis.pt

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.KeywordAttribute

/**
 * A TokenFilter that applies PortugueseStemmer to stem Portuguese words.
 */
class PortugueseStemFilter(input: TokenStream) : TokenFilter(input) {
    private val stemmer = PortugueseStemmer()
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val keywordAttr: KeywordAttribute = addAttribute(KeywordAttribute::class)

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (input.incrementToken()) {
            if (!keywordAttr.isKeyword) {
                val len = termAtt.length
                val newLen = stemmer.stem(termAtt.resizeBuffer(len + 1), len)
                termAtt.setLength(newLen)
            }
            return true
        }
        return false
    }
}
