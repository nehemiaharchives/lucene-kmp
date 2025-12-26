package org.gnit.lucenekmp.analysis.de

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.util.StemmerUtil
import org.gnit.lucenekmp.jdkport.System

/**
 * Normalizes German characters according to the heuristics of the German snowball algorithm.
 */
class GermanNormalizationFilter(input: TokenStream) : TokenFilter(input) {
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (!input.incrementToken()) {
            return false
        }

        var state = N
        var buffer = termAtt.buffer()
        var length = termAtt.length
        var i = 0
        while (i < length) {
            val c = buffer[i]
            when (c) {
                'a', 'o' -> state = U
                'u' -> state = if (state == N) U else V
                'e' -> {
                    if (state == U) {
                        length = StemmerUtil.delete(buffer, i--, length)
                    }
                    state = V
                }
                'i', 'q', 'y' -> state = V
                'ä' -> {
                    buffer[i] = 'a'
                    state = V
                }
                'ö' -> {
                    buffer[i] = 'o'
                    state = V
                }
                'ü' -> {
                    buffer[i] = 'u'
                    state = V
                }
                'ß' -> {
                    buffer[i] = 's'
                    i++
                    buffer = termAtt.resizeBuffer(1 + length)
                    if (i < length) {
                        System.arraycopy(buffer, i, buffer, i + 1, length - i)
                    }
                    buffer[i] = 's'
                    length++
                    state = N
                }
                else -> state = N
            }
            i++
        }

        termAtt.setLength(length)
        return true
    }

    companion object {
        private const val N = 0
        private const val V = 1
        private const val U = 2
    }
}
