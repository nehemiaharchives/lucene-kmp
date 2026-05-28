package org.gnit.lucenekmp.analysis.kn

import org.gnit.lucenekmp.analysis.util.StemmerUtil.delete

/**
 * Normalizer for Kannada.
 *
 * Applies light Kannada-specific normalization after the generic Indic normalizer.
 */
internal class KannadaNormalizer {
    /**
     * Normalize an input buffer of Kannada text.
     *
     * @param s input buffer
     * @param len length of input buffer
     * @return length of input buffer after normalization
     */
    fun normalize(s: CharArray, len: Int): Int {
        var length = len
        var i = 0
        var prevWasKannada = false
        while (i < length) {
            when {
                s[i] == '\u0CE4' -> s[i] = '\u0964'
                s[i] == '\u0CE5' -> s[i] = '\u0965'
                s[i] == ':' && prevWasKannada -> s[i] = '\u0C83'
                s[i] == '\u200D' || s[i] == '\u200C' -> {
                    length = delete(s, i, length)
                    i--
                }
            }
            prevWasKannada = i >= 0 && i < length && isKannadaChar(s[i])
            i++
        }
        return length
    }

    private fun isKannadaChar(ch: Char): Boolean {
        return ch in '\u0C80'..'\u0CFF'
    }
}
