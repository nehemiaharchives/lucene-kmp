package org.gnit.lucenekmp.analysis.my

/** Conservative normalizer for Burmese/Myanmar script text. */
internal class BurmeseNormalizer {
    /**
     * Normalize an input buffer of Burmese text.
     *
     * @param s input buffer
     * @param len length of input buffer
     * @return length of input buffer after normalization
     */
    fun normalize(s: CharArray, len: Int): Int {
        var outLen = 0
        var i = 0
        while (i < len) {
            val ch = s[i]
            val normalized = when (ch) {
                '\u200B', '\u200C', '\u200D', '\uFEFF' -> {
                    i += 1
                    continue
                }
                '၀' -> '0'
                '၁' -> '1'
                '၂' -> '2'
                '၃' -> '3'
                '၄' -> '4'
                '၅' -> '5'
                '၆' -> '6'
                '၇' -> '7'
                '၈' -> '8'
                '၉' -> '9'
                '၊', '။' -> ' '
                '’', '‘', '‛', 'ʹ', 'ʼ', '`', '´' -> '\''
                '‐', '‑', '‒', '–', '—', '―' -> '-'
                else -> ch
            }
            if (normalized != ' ') {
                s[outLen] = normalized
                outLen += 1
            }
            i += 1
        }
        return outLen
    }
}
