package org.gnit.lucenekmp.analysis.ha

/**
 * Light Stemmer for Hausa written in Latin Boko script.
 *
 * Applies conservative stripping for common Hausa verbal prefixes and suffixes.
 * This is intentionally lightweight and dictionary-free for Kotlin/Native.
 */
internal class HausaStemmer {
    /**
     * Stem an input buffer of Hausa text.
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
        for (prefix in PREFIXES) {
            if (word.length > prefix.length + 3 && word.startsWith(prefix)) {
                return word.substring(prefix.length)
            }
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

    companion object {
        private val PREFIXES: Array<String> = arrayOf(
            "na",
            "ta",
            "ya",
            "ba",
            "an"
        )

        private val SUFFIXES: Array<String> = arrayOf(
            "wa",
            "ce",
            "shi",
            "su",
            "n",
            "r"
        )
    }
}
