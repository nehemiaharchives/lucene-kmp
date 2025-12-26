package org.gnit.lucenekmp.analysis.en

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.miscellaneous.SetKeywordMarkerFilter
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.KeywordAttribute

/**
 * A [TokenFilter] that applies [EnglishMinimalStemmer] to stem English words.
 *
 * To prevent terms from being stemmed use an instance of [SetKeywordMarkerFilter] or a
 * custom [TokenFilter] that sets the [KeywordAttribute] before this [TokenStream].
 */
class EnglishMinimalStemFilter(input: TokenStream) : TokenFilter(input) {
    private val stemmer = EnglishMinimalStemmer()
    private val termAtt = addAttribute(CharTermAttribute::class)
    private val keywordAttr = addAttribute(KeywordAttribute::class)

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (input.incrementToken()) {
            if (!keywordAttr.isKeyword) {
                val newlen = stemmer.stem(termAtt.buffer(), termAtt.length)
                termAtt.setLength(newlen)
            }
            return true
        }
        return false
    }
}
