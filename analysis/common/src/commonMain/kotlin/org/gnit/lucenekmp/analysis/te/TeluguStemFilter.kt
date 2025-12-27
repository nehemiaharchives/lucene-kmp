package org.gnit.lucenekmp.analysis.te

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.KeywordAttribute

/** A [TokenFilter] that applies [TeluguStemmer] to stem Telugu words. */
class TeluguStemFilter(input: TokenStream) : TokenFilter(input) {
    private val termAttribute = addAttribute(CharTermAttribute::class)
    private val keywordAttribute = addAttribute(KeywordAttribute::class)
    private val teluguStemmer = TeluguStemmer()

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (input.incrementToken()) {
            if (!keywordAttribute.isKeyword) {
                termAttribute.setLength(teluguStemmer.stem(termAttribute.buffer(), termAttribute.length))
            }
            return true
        }
        return false
    }
}
