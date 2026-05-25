package org.gnit.lucenekmp.analysis.am

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute

/** Normalizes common Amharic orthographic variants before stemming. */
class AmharicNormalizationFilter(input: TokenStream) : TokenFilter(input) {
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val normalizer = AmharicNormalizer()

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (!input.incrementToken()) return false
        termAtt.setLength(normalizer.normalize(termAtt.buffer(), termAtt.length))
        return true
    }
}
