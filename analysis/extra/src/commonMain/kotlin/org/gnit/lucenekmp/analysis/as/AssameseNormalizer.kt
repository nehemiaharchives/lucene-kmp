package org.gnit.lucenekmp.analysis.`as`

import org.gnit.lucenekmp.analysis.util.StemmerUtil.delete

/**
 * Normalizer for Assamese.
 *
 * Applies light Assamese-specific normalization after the generic Indic normalizer.
 */
internal class AssameseNormalizer {
    /**
     * Normalize an input buffer of Assamese text.
     *
     * @param s input buffer
     * @param len length of input buffer
     * @return length of input buffer after normalization
     */
    fun normalize(s: CharArray, len: Int): Int {
        var length = len
        var i = 0
        var prevWasBengaliBlock = false
        while (i < length) {
            when {
                s[i] == '\u09F7' -> s[i] = '\u0964'
                s[i] == ':' && prevWasBengaliBlock -> s[i] = '\u0983'
                s[i] == '\u09B0' -> s[i] = '\u09F0'
                s[i] == '\u200D' || s[i] == '\u200C' -> {
                    length = delete(s, i, length)
                    i--
                }
            }
            prevWasBengaliBlock = i >= 0 && i < length && isBengaliBlockChar(s[i])
            i++
        }
        return length
    }

    private fun isBengaliBlockChar(ch: Char): Boolean {
        return ch in '\u0980'..'\u09FF'
    }
}
