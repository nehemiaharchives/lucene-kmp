package org.gnit.lucenekmp.analysis.pa

import org.gnit.lucenekmp.analysis.util.StemmerUtil.delete

/**
 * Normalizer for Punjabi written in Gurmukhi.
 *
 * Applies light Punjabi-specific normalization after the generic Indic normalizer.
 */
internal class PunjabiNormalizer {
    /**
     * Normalize an input buffer of Punjabi text.
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
                // Gurmukhi danda characters -> generic Indic danda.
                '\u0A64' -> s[i] = '\u0964'
                '\u0A65' -> s[i] = '\u0965'
                // Candrabindu -> bindi.
                '\u0A01' -> s[i] = '\u0A02'
                // Remove zero-width joiner/non-joiner and virama.
                '\u200D', '\u200C', '\u0A4D' -> {
                    length = delete(s, i, length)
                    i--
                }
            }
            i++
        }
        return length
    }
}
