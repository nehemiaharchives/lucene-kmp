package org.gnit.lucenekmp.analysis.cz

import org.gnit.lucenekmp.analysis.util.StemmerUtil

/**
 * Light stemmer for Czech.
 *
 * Implements the algorithm described in: Indexing and stemming approaches for the Czech language.
 */
class CzechStemmer {
    /**
     * Stem an input buffer of Czech text.
     *
     * NOTE: Input is expected to be in lowercase, but with diacritical marks.
     */
    fun stem(s: CharArray, len: Int): Int {
        var newLen = removeCase(s, len)
        newLen = removePossessives(s, newLen)
        if (newLen > 0) {
            newLen = normalize(s, newLen)
        }
        return newLen
    }

    private fun removeCase(s: CharArray, len: Int): Int {
        if (len > 7 && StemmerUtil.endsWith(s, len, "atech")) return len - 5

        if (len > 6 &&
            (StemmerUtil.endsWith(s, len, "ětem") ||
                StemmerUtil.endsWith(s, len, "etem") ||
                StemmerUtil.endsWith(s, len, "atům"))
        ) {
            return len - 4
        }

        if (len > 5 &&
            (StemmerUtil.endsWith(s, len, "ech") ||
                StemmerUtil.endsWith(s, len, "ich") ||
                StemmerUtil.endsWith(s, len, "ích") ||
                StemmerUtil.endsWith(s, len, "ého") ||
                StemmerUtil.endsWith(s, len, "ěmi") ||
                StemmerUtil.endsWith(s, len, "emi") ||
                StemmerUtil.endsWith(s, len, "ému") ||
                StemmerUtil.endsWith(s, len, "ěte") ||
                StemmerUtil.endsWith(s, len, "ete") ||
                StemmerUtil.endsWith(s, len, "ěti") ||
                StemmerUtil.endsWith(s, len, "eti") ||
                StemmerUtil.endsWith(s, len, "ího") ||
                StemmerUtil.endsWith(s, len, "iho") ||
                StemmerUtil.endsWith(s, len, "ími") ||
                StemmerUtil.endsWith(s, len, "ímu") ||
                StemmerUtil.endsWith(s, len, "imu") ||
                StemmerUtil.endsWith(s, len, "ách") ||
                StemmerUtil.endsWith(s, len, "ata") ||
                StemmerUtil.endsWith(s, len, "aty") ||
                StemmerUtil.endsWith(s, len, "ých") ||
                StemmerUtil.endsWith(s, len, "ama") ||
                StemmerUtil.endsWith(s, len, "ami") ||
                StemmerUtil.endsWith(s, len, "ové") ||
                StemmerUtil.endsWith(s, len, "ovi") ||
                StemmerUtil.endsWith(s, len, "ými"))
        ) {
            return len - 3
        }

        if (len > 4 &&
            (StemmerUtil.endsWith(s, len, "em") ||
                StemmerUtil.endsWith(s, len, "es") ||
                StemmerUtil.endsWith(s, len, "ém") ||
                StemmerUtil.endsWith(s, len, "ím") ||
                StemmerUtil.endsWith(s, len, "ům") ||
                StemmerUtil.endsWith(s, len, "at") ||
                StemmerUtil.endsWith(s, len, "ám") ||
                StemmerUtil.endsWith(s, len, "os") ||
                StemmerUtil.endsWith(s, len, "us") ||
                StemmerUtil.endsWith(s, len, "ým") ||
                StemmerUtil.endsWith(s, len, "mi") ||
                StemmerUtil.endsWith(s, len, "ou"))
        ) {
            return len - 2
        }

        if (len > 3) {
            when (s[len - 1]) {
                'a', 'e', 'i', 'o', 'u', 'ů', 'y', 'á', 'é', 'í', 'ý', 'ě' -> return len - 1
            }
        }

        return len
    }

    private fun removePossessives(s: CharArray, len: Int): Int {
        if (len > 5 &&
            (StemmerUtil.endsWith(s, len, "ov") ||
                StemmerUtil.endsWith(s, len, "in") ||
                StemmerUtil.endsWith(s, len, "ův"))
        ) {
            return len - 2
        }

        return len
    }

    private fun normalize(s: CharArray, len: Int): Int {
        if (StemmerUtil.endsWith(s, len, "čt")) {
            s[len - 2] = 'c'
            s[len - 1] = 'k'
            return len
        }

        if (StemmerUtil.endsWith(s, len, "št")) {
            s[len - 2] = 's'
            s[len - 1] = 'k'
            return len
        }

        when (s[len - 1]) {
            'c', 'č' -> {
                s[len - 1] = 'k'
                return len
            }

            'z', 'ž' -> {
                s[len - 1] = 'h'
                return len
            }
        }

        if (len > 1 && s[len - 2] == 'e') {
            s[len - 2] = s[len - 1]
            return len - 1
        }

        if (len > 2 && s[len - 2] == 'ů') {
            s[len - 2] = 'o'
            return len
        }

        return len
    }
}
