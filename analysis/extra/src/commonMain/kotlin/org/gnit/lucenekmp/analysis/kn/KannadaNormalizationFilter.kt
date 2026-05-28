package org.gnit.lucenekmp.analysis.kn

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.miscellaneous.SetKeywordMarkerFilter
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.KeywordAttribute

/**
 * A [TokenFilter] that applies [KannadaNormalizer] to normalize Kannada orthography.
 *
 * To prevent terms from being normalized, use [SetKeywordMarkerFilter] or any filter that sets
 * [KeywordAttribute] before this filter.
 */
class KannadaNormalizationFilter(input: TokenStream) : TokenFilter(input) {
    private val normalizer = KannadaNormalizer()
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
