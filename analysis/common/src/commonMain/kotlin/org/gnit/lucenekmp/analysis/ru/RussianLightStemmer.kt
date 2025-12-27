package org.gnit.lucenekmp.analysis.ru

import org.gnit.lucenekmp.analysis.util.StemmerUtil.endsWith

/**
 * Light Stemmer for Russian.
 *
 * This stemmer implements the algorithm from "Indexing and Searching Strategies for the Russian Language"
 * by Ljiljana Dolamic and Jacques Savoy.
 */
class RussianLightStemmer {
    fun stem(s: CharArray, len: Int): Int {
        var newLen = removeCase(s, len)
        return normalize(s, newLen)
    }

    private fun normalize(s: CharArray, len: Int): Int {
        if (len > 3) {
            when (s[len - 1]) {
                'ь', 'и' -> return len - 1
                'н' -> if (s[len - 2] == 'н') return len - 1
            }
        }
        return len
    }

    private fun removeCase(s: CharArray, len: Int): Int {
        if (len > 6 && (endsWith(s, len, "иями") || endsWith(s, len, "оями"))) return len - 4

        if (len > 5 &&
            (endsWith(s, len, "иям") ||
                endsWith(s, len, "иях") ||
                endsWith(s, len, "оях") ||
                endsWith(s, len, "ями") ||
                endsWith(s, len, "оям") ||
                endsWith(s, len, "оьв") ||
                endsWith(s, len, "ами") ||
                endsWith(s, len, "его") ||
                endsWith(s, len, "ему") ||
                endsWith(s, len, "ери") ||
                endsWith(s, len, "ими") ||
                endsWith(s, len, "ого") ||
                endsWith(s, len, "ому") ||
                endsWith(s, len, "ыми") ||
                endsWith(s, len, "оев"))
        ) {
            return len - 3
        }

        if (len > 4 &&
            (endsWith(s, len, "ая") ||
                endsWith(s, len, "яя") ||
                endsWith(s, len, "ях") ||
                endsWith(s, len, "юю") ||
                endsWith(s, len, "ах") ||
                endsWith(s, len, "ею") ||
                endsWith(s, len, "их") ||
                endsWith(s, len, "ия") ||
                endsWith(s, len, "ию") ||
                endsWith(s, len, "ьв") ||
                endsWith(s, len, "ою") ||
                endsWith(s, len, "ую") ||
                endsWith(s, len, "ям") ||
                endsWith(s, len, "ых") ||
                endsWith(s, len, "ея") ||
                endsWith(s, len, "ам") ||
                endsWith(s, len, "ем") ||
                endsWith(s, len, "ей") ||
                endsWith(s, len, "ём") ||
                endsWith(s, len, "ев") ||
                endsWith(s, len, "ий") ||
                endsWith(s, len, "им") ||
                endsWith(s, len, "ое") ||
                endsWith(s, len, "ой") ||
                endsWith(s, len, "ом") ||
                endsWith(s, len, "ов") ||
                endsWith(s, len, "ые") ||
                endsWith(s, len, "ый") ||
                endsWith(s, len, "ым") ||
                endsWith(s, len, "ми"))
        ) {
            return len - 2
        }

        if (len > 3) {
            when (s[len - 1]) {
                'а', 'е', 'и', 'о', 'у', 'й', 'ы', 'я', 'ь' -> return len - 1
            }
        }

        return len
    }
}
