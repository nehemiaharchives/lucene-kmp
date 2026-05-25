package org.gnit.lucenekmp.analysis.om

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute

/** Normalizes apostrophe variants used in Oromo text. */
class OromoNormalizationFilter(input: TokenStream) : TokenFilter(input) {
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val normalizer = OromoNormalizer()

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (!input.incrementToken()) return false
        termAtt.setLength(normalizer.normalize(termAtt.buffer(), termAtt.length))
        return true
    }
}
