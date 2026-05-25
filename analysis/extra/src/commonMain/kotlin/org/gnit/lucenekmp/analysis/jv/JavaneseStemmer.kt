package org.gnit.lucenekmp.analysis.jv

/**
 * Light Stemmer for Javanese written in Latin script.
 *
 * Applies conservative stripping for common Javanese affixes. This is intentionally
 * lightweight and dictionary-free for Kotlin/Native.
 */
internal class JavaneseStemmer {
    /**
     * Stem an input buffer of Javanese text.
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
        val nasal = stripNasalPrefix(word)
        if (nasal != word) return nasal

        for (prefix in PREFIXES) {
            if (word.length > prefix.length + 3 && word.startsWith(prefix)) {
                return word.substring(prefix.length)
            }
        }
        return word
    }

    private fun stripNasalPrefix(word: String): String {
        return when {
            word.length >= 5 && word.startsWith("ny") -> "s" + word.substring(2)
            word.length >= 5 && word.startsWith("ng") -> word.substring(2)
            word.length > 4 && word.startsWith("n") -> "t" + word.substring(1)
            word.length > 4 && word.startsWith("m") -> "p" + word.substring(1)
            else -> word
        }
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
            "dipun",
            "dak",
            "tak",
            "kok",
            "di",
            "ke",
            "ka",
            "sa",
            "se",
            "pa",
            "pi"
        )

        private val SUFFIXES: Array<String> = arrayOf(
            "aken",
            "ake",
            "ane",
            "ipun",
            "ing",
            "an",
            "na",
            "ne",
            "i"
        )
    }
}
