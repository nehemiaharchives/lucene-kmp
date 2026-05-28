package org.gnit.lucenekmp.analysis.no

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.miscellaneous.ScandinavianNormalizationFilter
import org.gnit.lucenekmp.analysis.miscellaneous.ScandinavianNormalizer
import org.gnit.lucenekmp.analysis.miscellaneous.ScandinavianNormalizer.Foldings
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute

/**
 * This filter normalize use of the interchangeable Scandinavian characters aeAEaeAEoeOEoeOE and folded
 * variants (ae, oe, aa) by transforming them to aaAAaeAEoeOE. This is similar to
 * ScandinavianNormalizationFilter, except for the folding rules customized for Norwegian.
 *
 * blåbærsyltetøj == blåbärsyltetöj == blaabaersyltetoej
 *
 * @see ScandinavianNormalizationFilter
 */
class NorwegianNormalizationFilter(input: TokenStream) : TokenFilter(input) {
    private val normalizer = ScandinavianNormalizer(setOf(Foldings.AE, Foldings.OE, Foldings.AA))
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

