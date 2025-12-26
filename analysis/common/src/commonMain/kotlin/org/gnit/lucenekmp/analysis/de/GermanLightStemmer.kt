package org.gnit.lucenekmp.analysis.de

/**
 * Light Stemmer for German.
 *
 * This stemmer implements the "UniNE" algorithm in:
 * "Light Stemming Approaches for the French, Portuguese, German and Hungarian Languages"
 * Jacques Savoy.
 */
internal class GermanLightStemmer {
    fun stem(s: CharArray, len: Int): Int {
        for (i in 0 until len) {
            when (s[i]) {
                'ä', 'à', 'á', 'â' -> s[i] = 'a'
                'ö', 'ò', 'ó', 'ô' -> s[i] = 'o'
                'ï', 'ì', 'í', 'î' -> s[i] = 'i'
                'ü', 'ù', 'ú', 'û' -> s[i] = 'u'
            }
        }

        var length = step1(s, len)
        length = step2(s, length)
        return length
    }

    private fun stEnding(ch: Char): Boolean {
        return when (ch) {
            'b', 'd', 'f', 'g', 'h', 'k', 'l', 'm', 'n', 't' -> true
            else -> false
        }
    }

    private fun step1(s: CharArray, len: Int): Int {
        if (len > 5 && s[len - 3] == 'e' && s[len - 2] == 'r' && s[len - 1] == 'n') return len - 3

        if (len > 4 && s[len - 2] == 'e') {
            when (s[len - 1]) {
                'm', 'n', 'r', 's' -> return len - 2
            }
        }

        if (len > 3 && s[len - 1] == 'e') return len - 1

        if (len > 3 && s[len - 1] == 's' && stEnding(s[len - 2])) return len - 1

        return len
    }

    private fun step2(s: CharArray, len: Int): Int {
        if (len > 5 && s[len - 3] == 'e' && s[len - 2] == 's' && s[len - 1] == 't') return len - 3

        if (len > 4 && s[len - 2] == 'e' && (s[len - 1] == 'r' || s[len - 1] == 'n')) return len - 2

        if (len > 4 && s[len - 2] == 's' && s[len - 1] == 't' && stEnding(s[len - 3])) return len - 2

        return len
    }
}
