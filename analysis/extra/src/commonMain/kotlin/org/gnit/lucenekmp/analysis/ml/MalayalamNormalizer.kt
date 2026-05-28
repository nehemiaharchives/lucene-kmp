package org.gnit.lucenekmp.analysis.ml

import org.gnit.lucenekmp.analysis.util.StemmerUtil.delete

/**
 * Normalizer for Malayalam.
 *
 * Applies Malayalam-specific cleanup after the generic Indic normalizer.
 */
internal class MalayalamNormalizer {
    /**
     * Normalize an input buffer of Malayalam text.
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
                '\u200D', '\u200C' -> {
                    length = delete(s, i, length)
                    i--
                }
                '\u0D64' -> s[i] = '\u0964'
                '\u0D65' -> s[i] = '\u0965'
            }
            i++
        }
        return length
    }
}
