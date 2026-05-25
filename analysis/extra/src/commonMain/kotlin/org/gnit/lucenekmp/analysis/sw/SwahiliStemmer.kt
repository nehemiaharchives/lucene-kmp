package org.gnit.lucenekmp.analysis.sw

/**
 * Light Stemmer for Swahili.
 *
 * Applies conservative prefix and suffix stripping for common Swahili noun-class and verb
 * inflections. This is intentionally lightweight and dictionary-free for Kotlin/Native.
 */
internal class SwahiliStemmer {
    /**
     * Stem an input buffer of Swahili text.
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

        word = stripVerbPrefix(word)
        word = stripNounPrefix(word)
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

    private fun stripVerbPrefix(word: String): String {
        for (prefix in VERB_PREFIXES) {
            if (word.length > prefix.length + 3 && word.startsWith(prefix)) {
                return word.substring(prefix.length)
            }
        }
        return word
    }

    private fun stripNounPrefix(word: String): String {
        for (prefix in NOUN_PREFIXES) {
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
        private val VERB_PREFIXES: Array<String> = arrayOf(
            "hawata",
            "hatuta",
            "hamta",
            "hata",
            "nina",
            "una",
            "ana",
            "tuna",
            "mna",
            "wana",
            "nili",
            "uli",
            "ali",
            "tuli",
            "mli",
            "wali",
            "nita",
            "uta",
            "ata",
            "tuta",
            "mta",
            "wata",
            "nime",
            "ume",
            "ame",
            "tume",
            "mme",
            "wame",
            "haku",
            "ku"
        )

        private val NOUN_PREFIXES: Array<String> = arrayOf(
            "wa",
            "vi",
            "ki",
            "mi",
            "ma",
            "m"
        )

        private val SUFFIXES: Array<String> = arrayOf(
            "ishwa",
            "esha",
            "isha",
            "ika",
            "ana",
            "eni",
            "eni",
            "ni",
            "wa",
            "a"
        )
    }
}
