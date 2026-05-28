package org.gnit.lucenekmp.analysis.miscellaneous

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute

/**
 * This filter normalize use of the interchangeable Scandinavian characters aeAEaeAEoeOEoeOE and folded
 * variants (aa, ao, ae, oe and oo) by transforming them to aaAAaeAEoeOE.
 */
class ScandinavianNormalizationFilter(input: TokenStream) : TokenFilter(input) {
    private val normalizer = ScandinavianNormalizer(ScandinavianNormalizer.ALL_FOLDINGS)
    private val charTermAttribute: CharTermAttribute = addAttribute(CharTermAttribute::class)

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (!input.incrementToken()) {
            return false
        }
        charTermAttribute.setLength(
            normalizer.processToken(charTermAttribute.buffer(), charTermAttribute.length)
        )
        return true
    }
}
