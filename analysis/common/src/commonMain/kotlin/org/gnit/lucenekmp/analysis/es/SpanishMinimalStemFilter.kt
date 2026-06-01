package org.gnit.lucenekmp.analysis.es

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.KeywordAttribute

/**
 * A [TokenFilter] that applies [SpanishMinimalStemmer] to stem Spanish words.
 *
 * To prevent terms from being stemmed use an instance of SetKeywordMarkerFilter or a custom
 * [TokenFilter] that sets the [KeywordAttribute] before this [TokenStream].
 *
 * @deprecated Use [SpanishPluralStemFilter] instead.
 */
@Deprecated("Use SpanishPluralStemFilter instead")
class SpanishMinimalStemFilter(input: TokenStream) : TokenFilter(input) {
    private val stemmer = SpanishMinimalStemmer()
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
