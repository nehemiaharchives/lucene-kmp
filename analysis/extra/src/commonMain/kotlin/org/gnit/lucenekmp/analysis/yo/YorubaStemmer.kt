package org.gnit.lucenekmp.analysis.yo

/**
 * Light stemmer for Yoruba.
 *
 * Yoruba has little inflectional morphology, so this intentionally avoids aggressive stemming.
 */
internal class YorubaStemmer {
    /**
     * Stem an input buffer of Yoruba text.
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
            return len
        }

        word = stripContractions(word)
        word = stripReduplication(word)
        word = stripPrefix(word)

        val outLen = word.length
        val out = word.toCharArray()
        var i = 0
        while (i < outLen) {
            s[i] = out[i]
            i += 1
        }
        return outLen
    }

    private fun stripContractions(word: String): String {
        if (word.length > 2 && word.endsWith("'n")) {
            return word.substring(0, word.length - 2)
        }
        return word
    }

    private fun stripReduplication(word: String): String {
        val hyphenIndex = word.indexOf('-')
        if (hyphenIndex > 0 && hyphenIndex < word.length - 1) {
            val first = word.substring(0, hyphenIndex)
            val second = word.substring(hyphenIndex + 1)
            if (first == second && first.length >= MIN_STEM_LENGTH) {
                return first
            }
        }
        return word
    }

    private fun stripPrefix(word: String): String {
        for (prefix in PREFIXES) {
            if (word.length > prefix.length + MIN_STEM_LENGTH && word.startsWith(prefix)) {
                val nextIndex = prefix.length
                if (nextIndex < word.length && isConsonant(word[nextIndex])) {
                    return word.substring(prefix.length)
                }
            }
        }
        return word
    }

    private fun isConsonant(ch: Char): Boolean {
        return ch in 'a'..'z' && ch !in VOWELS
    }

    companion object {
        private const val MIN_STEM_LENGTH = 3

        private val PREFIXES: Array<String> = arrayOf(
            "i",
            "a"
        )

        private val VOWELS: Set<Char> = setOf('a', 'e', 'i', 'o', 'u')
    }
}
