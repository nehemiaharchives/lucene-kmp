package org.gnit.lucenekmp.analysis.tl

/**
 * Stemmer for Tagalog.
 *
 * Applies a small set of light-stemming rules for Tagalog.
 */
internal class TagalogStemmer {
    /**
     * Stem an input buffer of Tagalog text.
     *
     * @param s input buffer
     * @param len length of input buffer
     * @return length of input buffer after stemming
     */
    fun stem(s: CharArray, len: Int): Int {
        if (len <= 2) return len
        var word = s.concatToString(0, len)
        val lower = word.lowercase()
        if (word != lower) {
            // Avoid lowercasing or modifying mixed-case tokens in this filter.
            return len
        }

        word = stripContractions(word)
        word = reduceInitialVowelDuplication(word)
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

    private fun stripContractions(word: String): String {
        if (word.length > 2 && (word.endsWith("'t") || word.endsWith("'y"))) {
            return word.substring(0, word.length - 2)
        }
        return word
    }

    private fun reduceInitialVowelDuplication(word: String): String {
        if (word.length > 2 && word[0] == word[1] && isVowel(word[0])) {
            return word.substring(1)
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

    private fun stripPrefix(word: String): String {
        for (prefix in PREFIXES) {
            if (word.length > prefix.length + 2 && word.startsWith(prefix)) {
                if ((prefix == "ma" || prefix == "pa" || prefix == "ka")) {
                    val nextIndex = prefix.length
                    if (nextIndex < word.length && isConsonant(word[nextIndex]) && word.length > prefix.length + 3) {
                        return word.substring(prefix.length)
                    }
                    continue
                }
                return word.substring(prefix.length)
            }
        }
        return word
    }

    private fun stripInfix(word: String): String {
        if (word.length <= 3) return word
        if (word.startsWith("um") && word.length > 4) {
            return word.substring(2)
        }
        if (word.startsWith("in") && word.length > 4) {
            return word.substring(2)
        }
        if (isConsonant(word[0]) && word.length > 4) {
            val infix = word.substring(1, 3)
            if (infix == "um" || infix == "in") {
                return word[0] + word.substring(3)
            }
        }
        return word
    }

    private fun stripSuffix(word: String): String {
        for (suffix in SUFFIXES) {
            if (word.length > suffix.length + 2 && word.endsWith(suffix)) {
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
            "pinag",
            "pagka",
            "naka",
            "maka",
            "pag",
            "mag",
            "nag",
            "ma",
            "pa",
            "ka"
        )

        private val SUFFIXES: Array<String> = arrayOf(
            "han",
            "hin",
            "an",
            "in"
        )
    }
}
