package org.gnit.lucenekmp.analysis.si

import org.gnit.lucenekmp.analysis.util.StemmerUtil.delete

/**
 * Normalizer for Sinhala.
 *
 * Applies conservative Sinhala-specific normalization that is useful for search without changing
 * lexical letters.
 */
internal class SinhalaNormalizer {
    /**
     * Normalize an input buffer of Sinhala text.
     *
     * @param s input buffer
     * @param len length of input buffer
     * @return length of input buffer after normalization
     */
    fun normalize(s: CharArray, len: Int): Int {
        var length = len
        var i = 0
        while (i < length) {
            when (s[i]) {
                '\u0DF4' -> s[i] = '\u0964'
                '\u200D', '\u200C' -> {
                    length = delete(s, i, length)
                    i--
                }
            }
            i++
        }
        return length
    }
}
