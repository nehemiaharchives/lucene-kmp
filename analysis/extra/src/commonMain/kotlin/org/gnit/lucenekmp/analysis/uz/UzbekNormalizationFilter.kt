package org.gnit.lucenekmp.analysis.uz

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute

/** A [TokenFilter] that applies [UzbekNormalizer]. */
class UzbekNormalizationFilter(input: TokenStream) : TokenFilter(input) {
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val normalizer = UzbekNormalizer()

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (input.incrementToken()) {
            termAtt.setLength(normalizer.normalize(termAtt.buffer(), termAtt.length))
            return true
        }
        return false
    }
}
