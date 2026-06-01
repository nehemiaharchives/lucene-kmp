package org.gnit.lucenekmp.analysis.fi

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.KeywordAttribute

/**
 * A [TokenFilter] that applies [FinnishLightStemmer] to stem Finnish words.
 *
 * To prevent terms from being stemmed use an instance of SetKeywordMarkerFilter or a custom
 * [TokenFilter] that sets the [KeywordAttribute] before this [TokenStream].
 */
class FinnishLightStemFilter(input: TokenStream) : TokenFilter(input) {
    private val stemmer = FinnishLightStemmer()
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val keywordAttr: KeywordAttribute = addAttribute(KeywordAttribute::class)

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (input.incrementToken()) {
            if (!keywordAttr.isKeyword) {
                val newlen = stemmer.stem(termAtt.buffer(), termAtt.length)
                termAtt.setLength(newlen)
            }
            return true
        } else {
            return false
        }
    }
}
