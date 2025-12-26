package org.gnit.lucenekmp.analysis.es

/**
 * Light Stemmer for Spanish.
 *
 * This stemmer implements the algorithm described in: Report on CLEF-2001 Experiments
 * Jacques Savoy.
 */
internal class SpanishLightStemmer {
    fun stem(s: CharArray, len: Int): Int {
        if (len < 5) return len

        for (i in 0 until len) {
            when (s[i]) {
                'à', 'á', 'â', 'ä' -> s[i] = 'a'
                'ò', 'ó', 'ô', 'ö' -> s[i] = 'o'
                'è', 'é', 'ê', 'ë' -> s[i] = 'e'
                'ù', 'ú', 'û', 'ü' -> s[i] = 'u'
                'ì', 'í', 'î', 'ï' -> s[i] = 'i'
            }
        }

        return when (s[len - 1]) {
            'o', 'a', 'e' -> len - 1
            's' -> {
                if (s[len - 2] == 'e' && s[len - 3] == 's' && s[len - 4] == 'e') return len - 2
                if (s[len - 2] == 'e' && s[len - 3] == 'c') {
                    s[len - 3] = 'z'
                    return len - 2
                }
                if (s[len - 2] == 'o' || s[len - 2] == 'a' || s[len - 2] == 'e') return len - 2
                len
            }
            else -> len
        }
    }
}
