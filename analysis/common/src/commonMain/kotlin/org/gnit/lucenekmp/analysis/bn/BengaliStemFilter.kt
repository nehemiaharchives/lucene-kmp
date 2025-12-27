package org.gnit.lucenekmp.analysis.bn

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.KeywordAttribute

/** A [TokenFilter] that applies [BengaliStemmer] to stem Bengali words. */
class BengaliStemFilter(input: TokenStream) : TokenFilter(input) {
    private val termAttribute = addAttribute(CharTermAttribute::class)
    private val keywordAttribute = addAttribute(KeywordAttribute::class)
    private val bengaliStemmer = BengaliStemmer()

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (input.incrementToken()) {
            if (!keywordAttribute.isKeyword) {
                termAttribute.setLength(bengaliStemmer.stem(termAttribute.buffer(), termAttribute.length))
            }
            return true
        }
        return false
    }
}
