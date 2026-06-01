package org.gnit.lucenekmp.analysis.lv

import org.gnit.lucenekmp.analysis.util.StemmerUtil.endsWith

/**
 * Light stemmer for Latvian.
 *
 * This is a light version of the algorithm in Karlis Kreslin's PhD thesis _A stemming
 * algorithm for Latvian_ with the following modifications:
 *
 * - Only explicitly stems noun and adjective morphology
 * - Stricter length/vowel checks for the resulting stems (verb etc suffix stripping is removed)
 * - Removes only the primary inflectional suffixes: case and number for nouns ; case, number,
 *   gender, and definitiveness for adjectives.
 * - Palatalization is only handled when a declension II,V,VI noun suffix is removed.
 */
internal class LatvianStemmer {
    /** Stem a latvian word. returns the new adjusted length. */
    fun stem(s: CharArray, len: Int): Int {
        var len = len
        val numVowels = numVowels(s, len)

        for (i in affixes.indices) {
            val affix = affixes[i]
            if (numVowels > affix.vc && len >= affix.affix.size + 3 && endsWith(s, len, affix.affix)) {
                len -= affix.affix.size
                return if (affix.palatalizes) unpalatalize(s, len) else len
            }
        }

        return len
    }

    internal class Affix(affix: String, val vc: Int, val palatalizes: Boolean) {
        val affix: CharArray = affix.toCharArray() // suffix
    }

    /**
     * Most cases are handled except for the ambiguous ones:
     *
     * - s -> š
     * - t -> š
     * - d -> ž
     * - z -> ž
     */
    private fun unpalatalize(s: CharArray, len: Int): Int {
        var len = len
        // we check the character removed: if it's -u then
        // it's 2,5, or 6 gen pl., and these two can only apply then.
        if (s[len] == 'u') {
            // kš -> kst
            if (endsWith(s, len, "kš")) {
                len++
                s[len - 2] = 's'
                s[len - 1] = 't'
                return len
            }
            // ņņ -> nn
            if (endsWith(s, len, "ņņ")) {
                s[len - 2] = 'n'
                s[len - 1] = 'n'
                return len
            }
        }

        // otherwise all other rules
        if (endsWith(s, len, "pj")
            || endsWith(s, len, "bj")
            || endsWith(s, len, "mj")
            || endsWith(s, len, "vj")
        ) {
            // labial consonant
            return len - 1
        } else if (endsWith(s, len, "šņ")) {
            s[len - 2] = 's'
            s[len - 1] = 'n'
            return len
        } else if (endsWith(s, len, "žņ")) {
            s[len - 2] = 'z'
            s[len - 1] = 'n'
            return len
        } else if (endsWith(s, len, "šļ")) {
            s[len - 2] = 's'
            s[len - 1] = 'l'
            return len
        } else if (endsWith(s, len, "žļ")) {
            s[len - 2] = 'z'
            s[len - 1] = 'l'
            return len
        } else if (endsWith(s, len, "ļņ")) {
            s[len - 2] = 'l'
            s[len - 1] = 'n'
            return len
        } else if (endsWith(s, len, "ļļ")) {
            s[len - 2] = 'l'
            s[len - 1] = 'l'
            return len
        } else if (s[len - 1] == 'č') {
            s[len - 1] = 'c'
            return len
        } else if (s[len - 1] == 'ļ') {
            s[len - 1] = 'l'
            return len
        } else if (s[len - 1] == 'ņ') {
            s[len - 1] = 'n'
            return len
        }

        return len
    }

    /**
     * Count the vowels in the string, we always require at least one in the remaining stem to accept
     * it.
     */
    private fun numVowels(s: CharArray, len: Int): Int {
        var n = 0
        for (i in 0..<len) {
            when (s[i]) {
                'a',
                'e',
                'i',
                'o',
                'u',
                'ā',
                'ī',
                'ē',
                'ū' -> n++
            }
        }
        return n
    }

    companion object {
        internal val affixes: Array<Affix> = arrayOf(
            Affix("ajiem", 3, false), Affix("ajai", 3, false),
            Affix("ajam", 2, false), Affix("ajām", 2, false),
            Affix("ajos", 2, false), Affix("ajās", 2, false),
            Affix("iem", 2, true), Affix("ajā", 2, false),
            Affix("ais", 2, false), Affix("ai", 2, false),
            Affix("ei", 2, false), Affix("ām", 1, false),
            Affix("am", 1, false), Affix("ēm", 1, false),
            Affix("īm", 1, false), Affix("im", 1, false),
            Affix("um", 1, false), Affix("us", 1, true),
            Affix("as", 1, false), Affix("ās", 1, false),
            Affix("es", 1, false), Affix("os", 1, true),
            Affix("ij", 1, false), Affix("īs", 1, false),
            Affix("ēs", 1, false), Affix("is", 1, false),
            Affix("ie", 1, false), Affix("u", 1, true),
            Affix("a", 1, true), Affix("i", 1, true),
            Affix("e", 1, false), Affix("ā", 1, false),
            Affix("ē", 1, false), Affix("ī", 1, false),
            Affix("ū", 1, false), Affix("o", 1, false),
            Affix("s", 0, false), Affix("š", 0, false),
        )
    }
}
