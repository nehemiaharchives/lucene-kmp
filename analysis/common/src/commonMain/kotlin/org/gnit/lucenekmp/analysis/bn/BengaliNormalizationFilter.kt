package org.gnit.lucenekmp.analysis.bn

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.KeywordAttribute

/**
 * A [TokenFilter] that applies [BengaliNormalizer] to normalize the orthography.
 */
class BengaliNormalizationFilter(input: TokenStream) : TokenFilter(input) {
    private val normalizer = BengaliNormalizer()
    private val termAtt = addAttribute(CharTermAttribute::class)
    private val keywordAtt = addAttribute(KeywordAttribute::class)

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
