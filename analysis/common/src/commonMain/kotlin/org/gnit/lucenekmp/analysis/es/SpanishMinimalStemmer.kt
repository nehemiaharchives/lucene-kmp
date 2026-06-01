package org.gnit.lucenekmp.analysis.es

/**
 * Minimal plural stemmer for Spanish.
 *
 * This stemmer implements the "plurals" stemmer for spanish lanugage.
 *
 * @deprecated Use [SpanishPluralStemmer] instead.
 */
@Deprecated("Use SpanishPluralStemmer instead")
internal class SpanishMinimalStemmer {
    fun stem(s: CharArray, len: Int): Int {
        if (len < 4 || s[len - 1] != 's') return len

        for (i in 0..<len) {
            when (s[i]) {
                'à', 'á', 'â', 'ä' -> s[i] = 'a'
                'ò', 'ó', 'ô', 'ö' -> s[i] = 'o'
                'è', 'é', 'ê', 'ë' -> s[i] = 'e'
                'ù', 'ú', 'û', 'ü' -> s[i] = 'u'
                'ì', 'í', 'î', 'ï' -> s[i] = 'i'
                'ñ' -> s[i] = 'n'
            }
        }

        when (s[len - 1]) {
            's' -> {
                if (s[len - 2] == 'a' || s[len - 2] == 'o') {
                    return len - 1
                }
                if (s[len - 2] == 'e') {
                    if (s[len - 3] == 's' && s[len - 4] == 'e') {
                        return len - 2
                    }
                    if (s[len - 3] == 'c') {
                        s[len - 3] = 'z'
                        return len - 2
                    } else {
                        return len - 2
                    }
                } else {
                    return len - 1
                }
            }
        }

        return len
    }
}
