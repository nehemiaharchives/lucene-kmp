package org.gnit.lucenekmp.analysis.ilo

/**
 * Light Stemmer for Ilocano.
 *
 * Applies conservative stripping for common Ilocano verbal affixes and reduplication. This is
 * intentionally lightweight and dictionary-free for Kotlin/Native.
 */
internal class IlocanoStemmer {
    /**
     * Stem an input buffer of Ilocano text.
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
        word = stripInfix(word)
        word = stripReduplication(word)
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
        for (prefix in PREFIXES) {
            if (word.length >= prefix.length + 3 && word.startsWith(prefix)) {
                return word.substring(prefix.length)
            }
        }
        return word
    }

    private fun stripInfix(word: String): String {
        if (word.length <= 4) return word
        if (word.startsWith("um") || word.startsWith("in")) {
            return word.substring(2)
        }
        if (isConsonant(word[0]) && word.length > 5) {
            val infix = word.substring(1, 3)
            if (infix == "um" || infix == "in") {
                return word[0] + word.substring(3)
            }
        }
        return word
    }

    private fun stripReduplication(word: String): String {
        val hyphenIndex = word.indexOf('-')
        if (hyphenIndex > 0 && hyphenIndex < word.length - 1) {
            val first = word.substring(0, hyphenIndex)
            val second = word.substring(hyphenIndex + 1)
            if (first.isNotEmpty() && first == second) {
                return first
            }
        }

        if (word.length >= 6 && word.substring(0, 3) == word.substring(3, 6)) {
            return word.substring(3)
        }
        if (word.length >= 4 &&
            isConsonant(word[0]) &&
            isVowel(word[1]) &&
            word[0] == word[2] &&
            word[1] == word[3]
        ) {
            return word.substring(2)
        }
        return word
    }

    private fun stripSuffix(word: String): String {
        for (suffix in SUFFIXES) {
            if (word.length > suffix.length + 3 && word.endsWith(suffix)) {
                return word.substring(0, word.length - suffix.length)
            }
        }
        return word
    }

    private fun isVowel(ch: Char): Boolean {
        return ch == 'a' || ch == 'e' || ch == 'i' || ch == 'o' || ch == 'u'
    }

    private fun isConsonant(ch: Char): Boolean {
        return ch in 'a'..'z' && !isVowel(ch)
    }

    companion object {
        private val PREFIXES: Array<String> = arrayOf(
            "makapag",
            "nakapag",
            "mang",
            "nang",
            "panag",
            "pag",
            "nag",
            "ag",
            "ma",
            "na",
            "pa"
        )

        private val SUFFIXES: Array<String> = arrayOf(
            "en",
            "an",
            "in"
        )
    }
}
