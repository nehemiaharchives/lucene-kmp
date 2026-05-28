package org.gnit.lucenekmp.analysis.ckb

import org.gnit.lucenekmp.analysis.util.StemmerUtil.delete
import org.gnit.lucenekmp.jdkport.Character

/**
 * Normalizes the Unicode representation of Sorani text.
 *
 * Normalization consists of:
 *
 * - Alternate forms of 'y' (0064, 0649) are converted to 06CC (FARSI YEH)
 * - Alternate form of 'k' (0643) is converted to 06A9 (KEHEH)
 * - Alternate forms of vowel 'e' (0647+200C, word-final 0647, 0629) are converted to 06D5 (AE)
 * - Alternate (joining) form of 'h' (06BE) is converted to 0647
 * - Alternate forms of 'rr' (0692, word-initial 0631) are converted to 0695 (REH WITH SMALL V
 *   BELOW)
 * - Harakat, tatweel, and formatting characters such as directional controls are removed.
 */
internal class SoraniNormalizer {

    /**
     * Normalize an input buffer of Sorani text
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
                YEH, DOTLESS_YEH -> s[i] = FARSI_YEH
                KAF -> s[i] = KEHEH
                ZWNJ -> {
                    if (i > 0 && s[i - 1] == HEH) {
                        s[i - 1] = AE
                    }
                    length = delete(s, i, length)
                    i--
                }
                HEH -> {
                    if (i == length - 1) {
                        s[i] = AE
                    }
                }
                TEH_MARBUTA -> s[i] = AE
                HEH_DOACHASHMEE -> s[i] = HEH
                REH -> {
                    if (i == 0) {
                        s[i] = RREH
                    }
                }
                RREH_ABOVE -> s[i] = RREH
                TATWEEL, KASRATAN, DAMMATAN, FATHATAN, FATHA, DAMMA, KASRA, SHADDA, SUKUN -> {
                    length = delete(s, i, length)
                    i--
                }
                else -> {
                    if (Character.getType(s[i].code) == Character.FORMAT.toInt()) {
                        length = delete(s, i, length)
                        i--
                    }
                }
            }
            i++
        }
        return length
    }

    companion object {
        const val YEH: Char = '\u064A'
        const val DOTLESS_YEH: Char = '\u0649'
        const val FARSI_YEH: Char = '\u06CC'

        const val KAF: Char = '\u0643'
        const val KEHEH: Char = '\u06A9'

        const val HEH: Char = '\u0647'
        const val AE: Char = '\u06D5'
        const val ZWNJ: Char = '\u200C'
        const val HEH_DOACHASHMEE: Char = '\u06BE'
        const val TEH_MARBUTA: Char = '\u0629'

        const val REH: Char = '\u0631'
        const val RREH: Char = '\u0695'
        const val RREH_ABOVE: Char = '\u0692'

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

