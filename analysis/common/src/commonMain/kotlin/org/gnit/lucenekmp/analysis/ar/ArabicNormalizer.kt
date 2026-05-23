package org.gnit.lucenekmp.analysis.ar

import org.gnit.lucenekmp.analysis.util.StemmerUtil.delete

/**
 * Normalizer for Arabic.
 *
 * Normalization is done in-place for efficiency, operating on a termbuffer.
 *
 * Normalization is defined as:
 * - Normalization of hamza with alef seat to a bare alef.
 * - Normalization of teh marbuta to heh
 * - Normalization of dotless yeh (alef maksura) to yeh.
 * - Removal of Arabic diacritics (the harakat)
 * - Removal of tatweel (stretching character).
 */
internal class ArabicNormalizer {
    /**
     * Normalize an input buffer of Arabic text.
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
                ALEF_MADDA,
                ALEF_HAMZA_ABOVE,
                ALEF_HAMZA_BELOW,
                -> s[i] = ALEF

                DOTLESS_YEH -> s[i] = YEH
                TEH_MARBUTA -> s[i] = HEH
                TATWEEL,
                KASRATAN,
                DAMMATAN,
                FATHATAN,
                FATHA,
                DAMMA,
                KASRA,
                SHADDA,
                SUKUN,
                -> {
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
        const val ALEF: Char = '\u0627'
        const val ALEF_MADDA: Char = '\u0622'
        const val ALEF_HAMZA_ABOVE: Char = '\u0623'
        const val ALEF_HAMZA_BELOW: Char = '\u0625'

        const val YEH: Char = '\u064A'
        const val DOTLESS_YEH: Char = '\u0649'

        const val TEH_MARBUTA: Char = '\u0629'
        const val HEH: Char = '\u0647'

        const val TATWEEL: Char = '\u0640'

        const val FATHATAN: Char = '\u064B'
        const val DAMMATAN: Char = '\u064C'
        const val KASRATAN: Char = '\u064D'
        const val FATHA: Char = '\u064E'
        const val DAMMA: Char = '\u064F'
        const val KASRA: Char = '\u0650'
        const val SHADDA: Char = '\u0651'
        const val SUKUN: Char = '\u0652'
    }
}


