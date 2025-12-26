package org.gnit.lucenekmp.analysis.en

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.KeywordAttribute

/**
 * Transforms the token stream as per the Porter stemming algorithm.
 */
class PorterStemFilter(input: TokenStream) : TokenFilter(input) {
    private val stemmer = PorterStemmer()
    private val termAtt = addAttribute(CharTermAttribute::class)
    private val keywordAttr = addAttribute(KeywordAttribute::class)

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (!input.incrementToken()) return false

        if (!keywordAttr.isKeyword && stemmer.stem(termAtt.buffer(), 0, termAtt.length)) {
            termAtt.copyBuffer(stemmer.getResultBuffer(), 0, stemmer.getResultLength())
        }
        return true
    }
}
