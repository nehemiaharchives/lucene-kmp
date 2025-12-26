package org.gnit.lucenekmp.analysis.fr

/**
 * Light Stemmer for French (minimal).
 *
 * Implements: "A Stemming procedure and stopword list for general French corpora." Jacques Savoy.
 */
internal class FrenchMinimalStemmer {
    fun stem(s: CharArray, len: Int): Int {
        var length = len
        if (length < 6) return length

        if (s[length - 1] == 'x') {
            if (s[length - 3] == 'a' && s[length - 2] == 'u') {
                s[length - 2] = 'l'
            }
            return length - 1
        }

        if (s[length - 1] == 's') length--
        if (s[length - 1] == 'r') length--
        if (s[length - 1] == 'e') length--
        if (s[length - 1] == 'é') length--
        if (s[length - 1] == s[length - 2] && s[length - 1].isLetter()) length--
        return length
    }
}
