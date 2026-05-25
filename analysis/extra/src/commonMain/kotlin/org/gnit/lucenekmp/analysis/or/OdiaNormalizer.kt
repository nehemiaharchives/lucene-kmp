package org.gnit.lucenekmp.analysis.or

import org.gnit.lucenekmp.analysis.util.StemmerUtil.delete

/**
 * Normalizer for Odia.
 *
 * Applies light Odia-specific normalization after the generic Indic normalizer.
 */
internal class OdiaNormalizer {
    /**
     * Normalize an input buffer of Odia text.
     *
     * @param s input buffer
     * @param len length of input buffer
     * @return length of input buffer after normalization
     */
    fun normalize(s: CharArray, len: Int): Int {
        var length = len
        var i = 0
        var prevWasOdia = false
        while (i < length) {
            when {
                s[i] == '\u0B64' -> s[i] = '\u0964'
                s[i] == '\u0B65' -> s[i] = '\u0965'
                s[i] == ':' && prevWasOdia -> s[i] = '\u0B03'
                s[i] == '\u200D' || s[i] == '\u200C' -> {
                    length = delete(s, i, length)
                    i--
                }
            }
            prevWasOdia = i >= 0 && i < length && isOdiaChar(s[i])
            i++
        }
        return length
    }

    private fun isOdiaChar(ch: Char): Boolean {
        return ch in '\u0B00'..'\u0B7F'
    }
}
