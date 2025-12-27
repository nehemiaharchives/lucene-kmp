package org.gnit.lucenekmp.analysis.it

/**
 * Light Stemmer for Italian.
 *
 * This stemmer implements the algorithm described in: Report on CLEF-2001 Experiments
 * Jacques Savoy.
 */
internal class ItalianLightStemmer {
    fun stem(s: CharArray, len: Int): Int {
        if (len < 6) return len

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
            'e' -> if (s[len - 2] == 'i' || s[len - 2] == 'h') len - 2 else len - 1
            'i' -> if (s[len - 2] == 'h' || s[len - 2] == 'i') len - 2 else len - 1
            'a' -> if (s[len - 2] == 'i') len - 2 else len - 1
            'o' -> if (s[len - 2] == 'i') len - 2 else len - 1
            else -> len
        }
    }
}
