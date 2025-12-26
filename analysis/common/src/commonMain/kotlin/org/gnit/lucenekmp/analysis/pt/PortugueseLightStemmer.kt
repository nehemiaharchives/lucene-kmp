package org.gnit.lucenekmp.analysis.pt

import org.gnit.lucenekmp.analysis.util.StemmerUtil.endsWith

/**
 * Light Stemmer for Portuguese.
 */
class PortugueseLightStemmer {
    fun stem(s: CharArray, len: Int): Int {
        if (len < 4) return len

        var newLen = removeSuffix(s, len)

        if (newLen > 3 && s[newLen - 1] == 'a') {
            newLen = normFeminine(s, newLen)
        }

        if (newLen > 4) {
            when (s[newLen - 1]) {
                'e', 'a', 'o' -> newLen--
            }
        }

        for (i in 0 until newLen) {
            when (s[i]) {
                'à', 'á', 'â', 'ä', 'ã' -> s[i] = 'a'
                'ò', 'ó', 'ô', 'ö', 'õ' -> s[i] = 'o'
                'è', 'é', 'ê', 'ë' -> s[i] = 'e'
                'ù', 'ú', 'û', 'ü' -> s[i] = 'u'
                'ì', 'í', 'î', 'ï' -> s[i] = 'i'
                'ç' -> s[i] = 'c'
            }
        }

        return newLen
    }

    private fun removeSuffix(s: CharArray, len: Int): Int {
        if (len > 4 && endsWith(s, len, "es")) {
            when (s[len - 3]) {
                'r', 's', 'l', 'z' -> return len - 2
            }
        }

        if (len > 3 && endsWith(s, len, "ns")) {
            s[len - 2] = 'm'
            return len - 1
        }

        if (len > 4 && (endsWith(s, len, "eis") || endsWith(s, len, "éis"))) {
            s[len - 3] = 'e'
            s[len - 2] = 'l'
            return len - 1
        }

        if (len > 4 && endsWith(s, len, "ais")) {
            s[len - 2] = 'l'
            return len - 1
        }

        if (len > 4 && endsWith(s, len, "óis")) {
            s[len - 3] = 'o'
            s[len - 2] = 'l'
            return len - 1
        }

        if (len > 4 && endsWith(s, len, "is")) {
            s[len - 1] = 'l'
            return len
        }

        if (len > 3 && (endsWith(s, len, "ões") || endsWith(s, len, "ães"))) {
            var newLen = len - 1
            s[newLen - 2] = 'ã'
            s[newLen - 1] = 'o'
            return newLen
        }

        if (len > 6 && endsWith(s, len, "mente")) return len - 5

        if (len > 3 && s[len - 1] == 's') return len - 1
        return len
    }

    private fun normFeminine(s: CharArray, len: Int): Int {
        if (len > 7 && (endsWith(s, len, "inha") || endsWith(s, len, "iaca") || endsWith(s, len, "eira"))) {
            s[len - 1] = 'o'
            return len
        }

        if (len > 6) {
            if (endsWith(s, len, "osa") || endsWith(s, len, "ica") || endsWith(s, len, "ida")
                || endsWith(s, len, "ada") || endsWith(s, len, "iva") || endsWith(s, len, "ama")
            ) {
                s[len - 1] = 'o'
                return len
            }

            if (endsWith(s, len, "ona")) {
                s[len - 3] = 'ã'
                s[len - 2] = 'o'
                return len - 1
            }

            if (endsWith(s, len, "ora")) return len - 1

            if (endsWith(s, len, "esa")) {
                s[len - 3] = 'ê'
                return len - 1
            }

            if (endsWith(s, len, "na")) {
                s[len - 1] = 'o'
                return len
            }
        }
        return len
    }
}
