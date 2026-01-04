package org.gnit.lucenekmp.analysis.gu

/**
 * Light Stemmer for Gujarati.
 *
 * Applies a small set of light-stemming rules for Gujarati suffixes and postpositions.
 */
internal class GujaratiStemmer {
    fun stem(buffer: CharArray, len: Int): Int {
        if (len <= 2) return len
        var word = buffer.concatToString(0, len)

        val stripped = stripSuffix(word)
        if (stripped == word) return len

        val outLen = stripped.length
        val out = stripped.toCharArray()
        var i = 0
        while (i < outLen) {
            buffer[i] = out[i]
            i += 1
        }
        return outLen
    }

    private fun stripSuffix(word: String): String {
        for (suffix in SUFFIXES) {
            if (word.length >= suffix.length + 2 && word.endsWith(suffix)) {
                return word.substring(0, word.length - suffix.length)
            }
        }
        return word
    }

    companion object {
        // Ordered longest to shortest to prefer more specific matches.
        private val SUFFIXES: Array<String> = arrayOf(
            "માંથી",
            "પરથી",
            "માં",
            "થી",
            "પર",
            "નો",
            "ની",
            "નું",
            "ના",
            "ને",
            "નાં",
            "ઓ",
            "એ",
            "આં",
            "આ",
            "ઈ",
            "ું"
        )
    }
}
