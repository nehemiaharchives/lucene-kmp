package org.gnit.lucenekmp.analysis.ig

/**
 * Light Stemmer for Igbo written in Latin script.
 *
 * Applies conservative stripping for common Igbo verbal prefixes and suffixes.
 * This is intentionally lightweight and dictionary-free for Kotlin/Native.
 */
internal class IgboStemmer {
    /**
     * Stem an input buffer of Igbo text.
     *
     * @param s input buffer
     * @param len length of input buffer
     * @return length of input buffer after stemming
     */
    fun stem(s: CharArray, len: Int): Int {
        if (len <= 3) return len
        var word = s.concatToString(0, len)
        val lower = word.lowercase()
        if (word != lower) {
            // Avoid lowercasing or modifying mixed-case tokens in this filter.
            return len
        }

        word = stripPrefix(word)
        word = stripSuffix(word)

        val outLen = word.length
        val out = word.toCharArray()
        var i = 0
        while (i < outLen) {
            s[i] = out[i]
            i += 1
        }
        return outLen
    }

    private fun stripPrefix(word: String): String {
        if (word.length > 5 && word.startsWith("na")) {
            return word.substring(2)
        }
        if (word.length >= 4 && word.startsWith("ị") && isConsonant(word[1])) {
            return word.substring(1)
        }
        if (word.length >= 4 && word.startsWith("i") && isConsonant(word[1])) {
            return word.substring(1)
        }
        if (word.length >= 5 && word.startsWith("n") && isConsonant(word[1])) {
            return word.substring(1)
        }
        if (word.length >= 5 && word.startsWith("m") && isConsonant(word[1])) {
            return word.substring(1)
        }
        return word
    }

    private fun stripSuffix(word: String): String {
        for (suffix in SUFFIXES) {
            if (word.length >= suffix.length + 3 && word.endsWith(suffix)) {
                return word.substring(0, word.length - suffix.length)
            }
        }
        return word
    }

    private fun isConsonant(ch: Char): Boolean {
        return ch !in VOWELS
    }

    companion object {
        private val SUFFIXES: Array<String> = arrayOf(
            "ghị",
            "ghi",
            "kwa",
            "kwọ",
            "kwu",
            "rị",
            "ri",
            "ra",
            "la"
        )

        private val VOWELS: Set<Char> = setOf(
            'a', 'e', 'i', 'o', 'u', 'ị', 'ọ', 'ụ'
        )
    }
}
