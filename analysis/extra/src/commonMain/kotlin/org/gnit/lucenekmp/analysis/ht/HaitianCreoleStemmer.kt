package org.gnit.lucenekmp.analysis.ht

/**
 * Light Stemmer for Haitian Creole.
 *
 * Haitian Creole has little inflectional morphology, so this stemmer only strips a small set of
 * productive, search-oriented derivational endings.
 */
internal class HaitianCreoleStemmer {
    /**
     * Stem an input buffer of Haitian Creole text.
     *
     * @param s input buffer
     * @param len length of input buffer
     * @return length of input buffer after stemming
     */
    fun stem(s: CharArray, len: Int): Int {
        if (len <= 4) return len
        var word = s.concatToString(0, len)
        val lower = word.lowercase()
        if (word != lower) {
            // Avoid lowercasing or modifying mixed-case tokens in this filter.
            return len
        }

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

    private fun stripSuffix(word: String): String {
        for (suffix in SUFFIXES) {
            if (word.length > suffix.length + 3 && word.endsWith(suffix)) {
                return word.substring(0, word.length - suffix.length)
            }
        }
        return word
    }

    companion object {
        private val SUFFIXES: Array<String> = arrayOf(
            "man"
        )
    }
}
