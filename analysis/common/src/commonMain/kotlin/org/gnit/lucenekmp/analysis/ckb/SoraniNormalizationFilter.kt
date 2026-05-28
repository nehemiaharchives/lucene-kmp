package org.gnit.lucenekmp.analysis.ckb

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute

/** A [TokenFilter] that applies [SoraniNormalizer] to normalize the orthography. */
class SoraniNormalizationFilter(input: TokenStream) : TokenFilter(input) {
    private val normalizer = SoraniNormalizer()
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

