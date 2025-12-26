package org.gnit.lucenekmp.analysis.fr

import org.gnit.lucenekmp.analysis.util.StemmerUtil

/**
 * Light Stemmer for French.
 *
 * This stemmer implements the "UniNE" algorithm.
 */
internal class FrenchLightStemmer {
    fun stem(s: CharArray, len: Int): Int {
        var length = len
        if (length > 5 && s[length - 1] == 'x') {
            if (s[length - 3] == 'a' && s[length - 2] == 'u' && s[length - 4] != 'e') {
                s[length - 2] = 'l'
            }
            length--
        }

        if (length > 3 && s[length - 1] == 'x') length--
        if (length > 3 && s[length - 1] == 's') length--

        if (length > 9 && StemmerUtil.endsWith(s, length, "issement")) {
            length -= 6
            s[length - 1] = 'r'
            return norm(s, length)
        }

        if (length > 8 && StemmerUtil.endsWith(s, length, "issant")) {
            length -= 4
            s[length - 1] = 'r'
            return norm(s, length)
        }

        if (length > 6 && StemmerUtil.endsWith(s, length, "ement")) {
            length -= 4
            if (length > 3 && StemmerUtil.endsWith(s, length, "ive")) {
                length--
                s[length - 1] = 'f'
            }
            return norm(s, length)
        }

        if (length > 11 && StemmerUtil.endsWith(s, length, "ficatrice")) {
            length -= 5
            s[length - 2] = 'e'
            s[length - 1] = 'r'
            return norm(s, length)
        }

        if (length > 10 && StemmerUtil.endsWith(s, length, "ficateur")) {
            length -= 4
            s[length - 2] = 'e'
            s[length - 1] = 'r'
            return norm(s, length)
        }

        if (length > 9 && StemmerUtil.endsWith(s, length, "catrice")) {
            length -= 3
            s[length - 4] = 'q'
            s[length - 3] = 'u'
            s[length - 2] = 'e'
            return norm(s, length)
        }

        if (length > 8 && StemmerUtil.endsWith(s, length, "cateur")) {
            length -= 2
            s[length - 4] = 'q'
            s[length - 3] = 'u'
            s[length - 2] = 'e'
            s[length - 1] = 'r'
            return norm(s, length)
        }

        if (length > 8 && StemmerUtil.endsWith(s, length, "atrice")) {
            length -= 4
            s[length - 2] = 'e'
            s[length - 1] = 'r'
            return norm(s, length)
        }

        if (length > 7 && StemmerUtil.endsWith(s, length, "ateur")) {
            length -= 3
            s[length - 2] = 'e'
            s[length - 1] = 'r'
            return norm(s, length)
        }

        if (length > 6 && StemmerUtil.endsWith(s, length, "trice")) {
            length--
            s[length - 3] = 'e'
            s[length - 2] = 'u'
            s[length - 1] = 'r'
        }

        if (length > 5 && StemmerUtil.endsWith(s, length, "ième")) return norm(s, length - 4)

        if (length > 7 && StemmerUtil.endsWith(s, length, "teuse")) {
            length -= 2
            s[length - 1] = 'r'
            return norm(s, length)
        }

        if (length > 6 && StemmerUtil.endsWith(s, length, "teur")) {
            length--
            s[length - 1] = 'r'
            return norm(s, length)
        }

        if (length > 5 && StemmerUtil.endsWith(s, length, "euse")) return norm(s, length - 2)

        if (length > 8 && StemmerUtil.endsWith(s, length, "ère")) {
            length--
            s[length - 2] = 'e'
            return norm(s, length)
        }

        if (length > 7 && StemmerUtil.endsWith(s, length, "ive")) {
            length--
            s[length - 1] = 'f'
            return norm(s, length)
        }

        if (length > 4 && (StemmerUtil.endsWith(s, length, "folle") || StemmerUtil.endsWith(s, length, "molle"))) {
            length -= 2
            s[length - 1] = 'u'
            return norm(s, length)
        }

        if (length > 9 && StemmerUtil.endsWith(s, length, "nnelle")) return norm(s, length - 5)

        if (length > 9 && StemmerUtil.endsWith(s, length, "nnel")) return norm(s, length - 3)

        if (length > 4 && StemmerUtil.endsWith(s, length, "ète")) {
            length--
            s[length - 2] = 'e'
        }

        if (length > 8 && StemmerUtil.endsWith(s, length, "ique")) length -= 4

        if (length > 8 && StemmerUtil.endsWith(s, length, "esse")) return norm(s, length - 3)

        if (length > 7 && StemmerUtil.endsWith(s, length, "inage")) return norm(s, length - 3)

        if (length > 9 && StemmerUtil.endsWith(s, length, "isation")) {
            length -= 7
            if (length > 5 && StemmerUtil.endsWith(s, length, "ual")) s[length - 2] = 'e'
            return norm(s, length)
        }

        if (length > 9 && StemmerUtil.endsWith(s, length, "isateur")) return norm(s, length - 7)

        if (length > 8 && StemmerUtil.endsWith(s, length, "ation")) return norm(s, length - 5)

        if (length > 8 && StemmerUtil.endsWith(s, length, "ition")) return norm(s, length - 5)

        return norm(s, length)
    }

    private fun norm(s: CharArray, len: Int): Int {
        var length = len
        if (length > 4) {
            for (i in 0 until length) {
                when (s[i]) {
                    'à', 'á', 'â' -> s[i] = 'a'
                    'ô' -> s[i] = 'o'
                    'è', 'é', 'ê' -> s[i] = 'e'
                    'ù', 'û' -> s[i] = 'u'
                    'î' -> s[i] = 'i'
                    'ç' -> s[i] = 'c'
                }
            }

            var ch = s[0]
            var i = 1
            while (i < length) {
                if (s[i] == ch && ch.isLetter()) {
                    length = StemmerUtil.delete(s, i--, length)
                } else {
                    ch = s[i]
                }
                i++
            }
        }

        if (length > 4 && StemmerUtil.endsWith(s, length, "ie")) length -= 2

        if (length > 4) {
            if (s[length - 1] == 'r') length--
            if (s[length - 1] == 'e') length--
            if (s[length - 1] == 'e') length--
            if (s[length - 1] == s[length - 2] && s[length - 1].isLetter()) length--
        }
        return length
    }
}
