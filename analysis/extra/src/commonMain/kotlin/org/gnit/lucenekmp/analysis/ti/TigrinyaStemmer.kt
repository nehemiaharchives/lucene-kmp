package org.gnit.lucenekmp.analysis.ti

/**
 * Light stemmer for Tigrinya Ethiopic text.
 *
 * This intentionally strips only common search-oriented inflectional endings. It is not a
 * transliteration-based stemmer and does not attempt full Semitic root extraction.
 */
internal class TigrinyaStemmer {
    /**
     * Stem an input buffer of Tigrinya text.
     *
     * @param s input buffer
     * @param len length of input buffer
     * @return length of input buffer after stemming
     */
    fun stem(s: CharArray, len: Int): Int {
        if (len <= MIN_STEM_LENGTH) return len
        val word = s.concatToString(0, len)
        val stemmed = stripSuffix(word)
        val outLen = stemmed.length
        val out = stemmed.toCharArray()
        var i = 0
        while (i < outLen) {
            s[i] = out[i]
            i += 1
        }
        return outLen
    }

    private fun stripSuffix(word: String): String {
        for (suffix in SUFFIXES) {
            if (word.length >= suffix.length + MIN_STEM_LENGTH && word.endsWith(suffix)) {
                return word.substring(0, word.length - suffix.length)
            }
        }
        return word
    }

    companion object {
        private const val MIN_STEM_LENGTH = 3

        private val SUFFIXES: Array<String> = arrayOf(
            "ታት",
            "ኹም",
            "ኽን",
            "ኩም",
            "ክን",
            "ልኩም",
            "ልክን",
            "ለይ",
            "ልካ",
            "ልኪ",
            "ኣት",
            "ኦም",
            "ኤን",
            "ዎም",
            "ወን",
            "ና",
            "ኻ",
            "ኺ",
            "ካ",
            "ኪ",
            "ላ"
        )
    }
}
