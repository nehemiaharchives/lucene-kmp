package org.gnit.lucenekmp.analysis.uz

/**
 * Light stemmer for Uzbek.
 *
 * Uzbek is agglutinative, so this strips common plural, possessive, and case suffix stacks.
 */
internal class UzbekStemmer {
    /**
     * Stem an input buffer of Uzbek text.
     *
     * @param s input buffer
     * @param len length of input buffer
     * @return length of input buffer after stemming
     */
    fun stem(s: CharArray, len: Int): Int {
        if (len <= MIN_STEM_LENGTH) return len
        val word = s.concatToString(0, len)
        val lower = word.lowercase()
        if (word != lower) {
            return len
        }

        val stemmed = stripSuffixes(word)
        if (stemmed == word || stemmed.length < MIN_STEM_LENGTH) return len

        val out = stemmed.toCharArray()
        var i = 0
        while (i < out.size) {
            s[i] = out[i]
            i += 1
        }
        return out.size
    }

    private fun stripSuffixes(word: String): String {
        var result = word
        var stripped = 0
        while (stripped < MAX_STRIPS) {
            val next = stripOneSuffix(result)
            if (next == result) break
            result = next
            stripped += 1
        }
        return result
    }

    private fun stripOneSuffix(word: String): String {
        for (suffix in SUFFIXES) {
            if (word.length >= suffix.length + MIN_STEM_LENGTH && word.endsWith(suffix)) {
                return word.substring(0, word.length - suffix.length)
            }
        }
        return word
    }

    companion object {
        private const val MIN_STEM_LENGTH = 2
        private const val MAX_STRIPS = 3

        private val SUFFIXES: Array<String> = arrayOf(
            "larimizdan",
            "laringizdan",
            "larimizga",
            "laringizga",
            "larining",
            "laridan",
            "larida",
            "larni",
            "larga",
            "larka",
            "larqa",
            "lar",
            "imizdan",
            "ingizdan",
            "imizga",
            "ingizga",
            "imizni",
            "ingizni",
            "imizda",
            "ingizda",
            "imiz",
            "ingiz",
            "ining",
            "sining",
            "ning",
            "idan",
            "sidan",
            "dan",
            "tan",
            "ida",
            "sida",
            "da",
            "ta",
            "ini",
            "sini",
            "ni",
            "iga",
            "siga",
            "ga",
            "ka",
            "qa",
            "im",
            "ing",
            "si",
            "i",
            "lik",
            "chi"
        ).sortedByDescending { it.length }.toTypedArray()
    }
}
