package org.gnit.lucenekmp.analysis.sv

import org.gnit.lucenekmp.analysis.util.StemmerUtil.endsWith

/**
 * Light Stemmer for Swedish.
 *
 * This stemmer implements the algorithm described in: Report on CLEF-2003 Monolingual Tracks
 * Jacques Savoy.
 */
internal class SwedishLightStemmer {
    fun stem(s: CharArray, len: Int): Int {
        var length = len
        if (length > 4 && s[length - 1] == 's') {
            length--
        }

        if (length > 7 && (endsWith(s, length, "elser") || endsWith(s, length, "heten"))) {
            return length - 5
        }

        if (length > 6 && (
                endsWith(s, length, "arne") ||
                    endsWith(s, length, "erna") ||
                    endsWith(s, length, "ande") ||
                    endsWith(s, length, "else") ||
                    endsWith(s, length, "aste") ||
                    endsWith(s, length, "orna") ||
                    endsWith(s, length, "aren")
                )
        ) {
            return length - 4
        }

        if (length > 5 && (
                endsWith(s, length, "are") ||
                    endsWith(s, length, "ast") ||
                    endsWith(s, length, "het")
                )
        ) {
            return length - 3
        }

        if (length > 4 && (
                endsWith(s, length, "ar") ||
                    endsWith(s, length, "er") ||
                    endsWith(s, length, "or") ||
                    endsWith(s, length, "en") ||
                    endsWith(s, length, "at") ||
                    endsWith(s, length, "te") ||
                    endsWith(s, length, "et")
                )
        ) {
            return length - 2
        }

        if (length > 3) {
            when (s[length - 1]) {
                't', 'a', 'e', 'n' -> return length - 1
            }
        }

        return length
    }
}
