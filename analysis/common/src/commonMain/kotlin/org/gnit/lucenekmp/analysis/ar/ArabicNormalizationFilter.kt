package org.gnit.lucenekmp.analysis.ar

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute

/** A [TokenFilter] that applies [ArabicNormalizer] to normalize the orthography. */
class ArabicNormalizationFilter(input: TokenStream) : TokenFilter(input) {
    private val normalizer = ArabicNormalizer()
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (input.incrementToken()) {
            val newlen = normalizer.normalize(termAtt.buffer(), termAtt.length)
            termAtt.setLength(newlen)
            return true
        }
        return false
    }
}

