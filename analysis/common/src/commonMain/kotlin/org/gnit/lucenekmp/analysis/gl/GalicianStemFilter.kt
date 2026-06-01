package org.gnit.lucenekmp.analysis.gl

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.KeywordAttribute

/**
 * A [TokenFilter] that applies [GalicianStemmer] to stem Galician words.
 *
 * To prevent terms from being stemmed use an instance of SetKeywordMarkerFilter or a custom
 * [TokenFilter] that sets the [KeywordAttribute] before this [TokenStream].
 */
class GalicianStemFilter(input: TokenStream) : TokenFilter(input) {
    private val stemmer = GalicianStemmer()
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val keywordAttr: KeywordAttribute = addAttribute(KeywordAttribute::class)

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (input.incrementToken()) {
            if (!keywordAttr.isKeyword) {
                // this stemmer increases word length by 1: worst case '*çom' -> '*ción'
                val len = termAtt.length
                val newlen = stemmer.stem(termAtt.resizeBuffer(len + 1), len)
                termAtt.setLength(newlen)
            }
            return true
        } else {
            return false
        }
    }
}
