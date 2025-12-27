package org.gnit.lucenekmp.analysis.sv

import org.gnit.lucenekmp.analysis.util.StemmerUtil.endsWith

/**
 * Minimal Stemmer for Swedish. The algorithm is an adapted version of the SwedishLightStemmer,
 * but only stripping the most common plural suffixes for nouns: -ar/arne/arna/aren, -at, -er/erna,
 * -et, -or/orna, -en. We do not strip -an or -ans suffixes, since that would require a large
 * dictionary of exceptions.
 */
internal class SwedishMinimalStemmer {
    fun stem(s: CharArray, len: Int): Int {
        var length = len
        if (length > 4 && s[length - 1] == 's') {
            length--
        }

        if (length > 6 && (
                endsWith(s, length, "arne") ||
                    endsWith(s, length, "erna") ||
                    endsWith(s, length, "arna") ||
                    endsWith(s, length, "orna") ||
                    endsWith(s, length, "aren")
                )
        ) {
            return length - 4
        }

        if (length > 5 && endsWith(s, length, "are")) {
            return length - 3
        }

        if (length > 4 && (
                endsWith(s, length, "ar") ||
                    endsWith(s, length, "at") ||
                    endsWith(s, length, "er") ||
                    endsWith(s, length, "et") ||
                    endsWith(s, length, "or") ||
                    endsWith(s, length, "en")
                )
        ) {
            return length - 2
        }

        if (length > 3) {
            when (s[length - 1]) {
                'a', 'e', 'n' -> return length - 1
            }
        }

        return length
    }
}
