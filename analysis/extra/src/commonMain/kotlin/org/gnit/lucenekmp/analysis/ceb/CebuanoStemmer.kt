package org.gnit.lucenekmp.analysis.ceb

/**
 * Light Stemmer for Cebuano.
 *
 * Applies conservative stripping for common Cebuano affixes. This is intentionally
 * lightweight and dictionary-free for Kotlin/Native.
 */
internal class CebuanoStemmer {
    /**
     * Stem an input buffer of Cebuano text.
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

        word = stripContractions(word)
        word = stripPrefix(word)
        word = stripInfix(word)
        word = stripReduplication(word)
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

    private fun stripContractions(word: String): String {
        if (word.length > 3 && (word.endsWith("'g") || word.endsWith("'y"))) {
            return word.substring(0, word.length - 2)
        }
        if (word.length > 4 && word.endsWith("'ng")) {
            return word.substring(0, word.length - 3)
        }
        return word
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
            word.length >= 6 && word.startsWith("mang") -> word.substring(4)
            word.length >= 6 && word.startsWith("pang") -> word.substring(4)
            word.length >= 5 && word.startsWith("man") -> "t" + word.substring(3)
            word.length >= 5 && word.startsWith("pan") -> "t" + word.substring(3)
            word.length >= 5 && word.startsWith("mam") -> "p" + word.substring(3)
            word.length >= 5 && word.startsWith("pam") -> "p" + word.substring(3)
            else -> word
        }
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
            "ginapang",
            "gipang",
            "ginapa",
            "gina",
            "gim",
            "gin",
            "ging",
            "nagpa",
            "magpa",
            "maka",
            "naka",
            "nipa",
            "nag",
            "mag",
            "pag",
            "gim",
            "gin",
            "gi",
            "mi",
            "ni",
            "mo",
            "na"
        )

        private val SUFFIXES: Array<String> = arrayOf(
            "hanan",
            "anan",
            "han",
            "hon",
            "non",
            "onon",
            "on",
            "an",
            "ha",
            "hi",
            "a",
            "i"
        )
    }
}
