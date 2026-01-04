package org.gnit.lucenekmp.analysis.tl

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.miscellaneous.SetKeywordMarkerFilter
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.KeywordAttribute

/**
 * A [TokenFilter] that applies [TagalogNormalizer] to normalize the orthography.
 *
 * In some cases the normalization may cause unrelated terms to conflate, so to prevent terms
 * from being normalized use an instance of [SetKeywordMarkerFilter] or a custom [TokenFilter]
 * that sets the [KeywordAttribute] before this [TokenStream].
 */
class TagalogNormalizationFilter(input: TokenStream) : TokenFilter(input) {
    private val normalizer = TagalogNormalizer()
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val keywordAtt: KeywordAttribute = addAttribute(KeywordAttribute::class)

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (input.incrementToken()) {
            if (!keywordAtt.isKeyword) {
                termAtt.setLength(normalizer.normalize(termAtt.buffer(), termAtt.length))
            }
            return true
        }
        return false
    }
}
