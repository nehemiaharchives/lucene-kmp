package org.gnit.lucenekmp.analysis.de

/**
 * Minimal Stemmer for German.
 *
 * This stemmer implements the algorithm from "Morphologie et recherche d'information".
 */
internal class GermanMinimalStemmer {
    fun stem(s: CharArray, len: Int): Int {
        if (len < 5) return len

        for (i in 0 until len) {
            when (s[i]) {
                'ä' -> s[i] = 'a'
                'ö' -> s[i] = 'o'
                'ü' -> s[i] = 'u'
            }
        }

        if (len > 6 && s[len - 3] == 'n' && s[len - 2] == 'e' && s[len - 1] == 'n') return len - 3

        if (len > 5) {
            when (s[len - 1]) {
                'n' -> if (s[len - 2] == 'e') return len - 2
                'e' -> if (s[len - 2] == 's') return len - 2
                's' -> if (s[len - 2] == 'e') return len - 2
                'r' -> if (s[len - 2] == 'e') return len - 2
            }
        }

        when (s[len - 1]) {
            'n', 'e', 's', 'r' -> return len - 1
        }

        return len
    }
}
