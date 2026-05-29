package org.gnit.lucenekmp.analysis.br

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.KeywordAttribute

/**
 * A [TokenFilter] that applies [BrazilianStemmer].
 *
 * To prevent terms from being stemmed use an instance of
 * `SetKeywordMarkerFilter` or a custom [TokenFilter] that sets the [KeywordAttribute] before this
 * [TokenStream].
 */
class BrazilianStemFilter(input: TokenStream) : TokenFilter(input) {
    /** [BrazilianStemmer] in use by this filter. */
    private var stemmer: BrazilianStemmer = BrazilianStemmer()
    private var exclusions: Set<*>? = null
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val keywordAttr: KeywordAttribute = addAttribute(KeywordAttribute::class)

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (input.incrementToken()) {
            val term = termAtt.toString()
            if (!keywordAttr.isKeyword && (exclusions == null || !exclusions!!.contains(term))) {
                val s = stemmer.stem(term)
                if (s != null && s != term) {
                    termAtt.setEmpty()!!.append(s)
                }
            }
            return true
        }
        return false
    }
}
