package org.gnit.lucenekmp.analysis.ml

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute

/** A [TokenFilter] that applies [MalayalamNormalizer]. */
class MalayalamNormalizationFilter(input: TokenStream) : TokenFilter(input) {
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val normalizer = MalayalamNormalizer()

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (input.incrementToken()) {
            termAtt.setLength(normalizer.normalize(termAtt.buffer(), termAtt.length))
            return true
        }
        return false
    }
}
