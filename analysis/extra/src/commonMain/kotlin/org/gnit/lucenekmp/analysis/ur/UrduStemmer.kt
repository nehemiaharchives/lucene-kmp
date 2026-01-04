package org.gnit.lucenekmp.analysis.ur

/**
 * Stemmer for Urdu.
 *
 * Applies a small set of light-stemming rules for Urdu based on published rule samples.
 */
internal class UrduStemmer {
    /**
     * Stem an input buffer of Urdu text.
     *
     * @param s input buffer
     * @param len length of input buffer
     * @return length of input buffer after stemming
     */
    fun stem(s: CharArray, len: Int): Int {
        if (len <= 3) return len
        var word = s.concatToString(0, len)

        word = stripPrefix(word)
        word = applyInfixRules(word)
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
            if (word.length > prefix.length + 2 && word.startsWith(prefix)) {
                return word.substring(prefix.length)
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

    private fun applyInfixRules(word: String): String {
        if (word.isEmpty()) return word
        if (word[0] == 'ا') {
            if (word.length == 5 && word.length > 1 && word[1] == 'ت') {
                return removeAny(word, RULE3_REMOVE)
            }
            if (word.length == 5) {
                return word.replace("ا", "")
            }
            if (word.length > 5) {
                return removeAny(word, RULE2_REMOVE)
            }
        }
        // Isam Mafool: if word starts with 'م' and second-last is 'و', remove 'م' and 'و'
        if (word.length == 5 && word[0] == 'م' && word[word.length - 2] == 'و') {
            return word.replace("م", "").replace("و", "")
        }
        return word
    }

    private fun removeAny(word: String, chars: Set<Char>): String {
        val sb = StringBuilder(word.length)
        for (c in word) {
            if (!chars.contains(c)) sb.append(c)
        }
        return sb.toString()
    }

    companion object {
        private val PREFIXES: Array<String> = arrayOf(
            "ال",
            "تش",
            "دس",
            "تذ",
            "نا",
            "اص",
            "تا",
            "عش"
        )

        private val SUFFIXES: Array<String> = arrayOf(
            "ویں",
            "ئیں",
            "یاں",
            "اتے",
            "اتی",
            "ہىے",
            "وے",
            "وں"
        )

        private val RULE2_REMOVE: Set<Char> = hashSetOf('ا', 'ت', 'ط', 'ی', 'ں', 'ئ', 'ؤ', 'ء')
        private val RULE3_REMOVE: Set<Char> = hashSetOf('ا', 'ی', 'ں', 'ئ', 'ؤ', 'ء', 'و')
    }
}
