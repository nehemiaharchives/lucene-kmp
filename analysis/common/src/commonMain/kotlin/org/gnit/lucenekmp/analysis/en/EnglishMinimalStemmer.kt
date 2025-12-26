package org.gnit.lucenekmp.analysis.en

/**
 * Minimal plural stemmer for English.
 *
 * This stemmer implements the "S-Stemmer" from How Effective Is Suffixing? Donna Harman.
 */
internal class EnglishMinimalStemmer {
    fun stem(s: CharArray, len: Int): Int {
        var length = len
        if (length < 3 || s[length - 1] != 's') return length

        when (s[length - 2]) {
            'u', 's' -> return length
            'e' -> {
                if (length > 3 && s[length - 3] == 'i' && s[length - 4] != 'a' && s[length - 4] != 'e') {
                    s[length - 3] = 'y'
                    return length - 2
                }
                if (s[length - 3] == 'i' || s[length - 3] == 'a' || s[length - 3] == 'o' || s[length - 3] == 'e') {
                    return length
                }
            }
        }
        return length - 1
    }
}
