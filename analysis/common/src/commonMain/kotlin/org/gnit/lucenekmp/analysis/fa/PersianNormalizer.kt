package org.gnit.lucenekmp.analysis.fa

import org.gnit.lucenekmp.analysis.util.StemmerUtil.delete

/**
 * Normalizer for Persian.
 *
 * Normalization is done in-place for efficiency, operating on a termbuffer.
 *
 * Normalization is defined as:
 * - Normalization of various heh + hamza forms and heh goal to heh.
 * - Normalization of farsi yeh and yeh barree to arabic yeh
 * - Normalization of persian keheh to arabic kaf
 */
internal class PersianNormalizer {
    /**
     * Normalize an input buffer of Persian text.
     *
     * @param s input buffer
     * @param len length of input buffer
     * @return length of input buffer after normalization
     */
    fun normalize(s: CharArray, len: Int): Int {
        var len = len
        var i = 0
        while (i < len) {
            when (s[i]) {
                FARSI_YEH,
                YEH_BARREE,
                -> s[i] = YEH

                KEHEH -> s[i] = KAF
                HEH_YEH,
                HEH_GOAL,
                -> s[i] = HEH

                HAMZA_ABOVE -> {
                    len = delete(s, i, len)
                    continue
                }

                else -> {}
            }
            i++
        }
        return len
    }

    private companion object {
        const val YEH: Char = '\u064A'
        const val FARSI_YEH: Char = '\u06CC'
        const val YEH_BARREE: Char = '\u06D2'
        const val KEHEH: Char = '\u06A9'
        const val KAF: Char = '\u0643'
        const val HAMZA_ABOVE: Char = '\u0654'
        const val HEH_YEH: Char = '\u06C0'
        const val HEH_GOAL: Char = '\u06C1'
        const val HEH: Char = '\u0647'
    }
}

