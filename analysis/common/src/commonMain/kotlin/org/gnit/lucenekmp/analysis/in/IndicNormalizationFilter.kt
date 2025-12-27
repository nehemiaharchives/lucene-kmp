package org.gnit.lucenekmp.analysis.`in`

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute

/**
 * A [TokenFilter] that applies [IndicNormalizer] to normalize text in Indian Languages.
 */
class IndicNormalizationFilter(input: TokenStream) : TokenFilter(input) {
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val normalizer = IndicNormalizer()

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (input.incrementToken()) {
            termAtt.setLength(normalizer.normalize(termAtt.buffer(), termAtt.length))
            return true
        }
        return false
    }
}
