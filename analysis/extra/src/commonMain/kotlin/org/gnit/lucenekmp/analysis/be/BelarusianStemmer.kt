package org.gnit.lucenekmp.analysis.be

/**
 * Light stemmer for Belarusian Cyrillic.
 *
 * This intentionally strips only common inflectional endings. It is not a dictionary lemmatizer.
 */
internal class BelarusianStemmer {
    /**
     * Stem an input buffer of Belarusian text.
     *
     * @param s input buffer
     * @param len length of input buffer
     * @return length of input buffer after stemming
     */
    fun stem(s: CharArray, len: Int): Int {
        if (len <= 4) return len
        val word = s.concatToString(0, len)
        if (word != word.lowercase()) {
            return len
        }

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
        private const val MIN_STEM_LENGTH = 4

        private val SUFFIXES: Array<String> = arrayOf(
            "ымі",
            "імі",
            "ага",
            "яга",
            "ога",
            "ему",
            "аму",
            "ому",
            "амі",
            "ямі",
            "аго",
            "яму",
            "ых",
            "іх",
            "ай",
            "ой",
            "ую",
            "юю",
            "ая",
            "яя",
            "ае",
            "ое",
            "ыя",
            "ія",
            "ах",
            "ях",
            "ам",
            "ям",
            "аў",
            "оў",
            "еў",
            "ей",
            "цца",
            "ць",
            "ці",
            "ла",
            "лі",
            "ло",
            "ся",
            "а",
            "у",
            "ю",
            "ы",
            "і",
            "е",
            "я"
        )
    }
}
