package org.gnit.lucenekmp.analysis.ml

/**
 * Light stemmer for Malayalam.
 *
 * Malayalam is agglutinative, so this strips only common plural, case, and postposition suffixes.
 */
internal class MalayalamStemmer {
    /**
     * Stem an input buffer of Malayalam text.
     *
     * @param s input buffer
     * @param len length of input buffer
     * @return length of input buffer after stemming
     */
    fun stem(s: CharArray, len: Int): Int {
        if (len <= MIN_STEM_LENGTH) return len
        val word = s.concatToString(0, len)
        val stemmed = stripSuffix(word)
        if (stemmed == word || stemmed.length < MIN_STEM_LENGTH) return len

        val out = stemmed.toCharArray()
        var i = 0
        while (i < out.size) {
            s[i] = out[i]
            i += 1
        }
        return out.size
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
        private const val MIN_STEM_LENGTH = 2

        // Ordered longest to shortest to prefer full postpositions and stacked case/plural markers.
        private val SUFFIXES: Array<String> = arrayOf(
            "കളുടെ",
            "കളിൽനിന്ന്",
            "ങ്ങളിൽനിന്ന്",
            "ങ്ങളിൽ",
            "ങ്ങൾക്ക്",
            "ങ്ങൾക്ക്‌",
            "ങ്ങളോട്",
            "ങ്ങളാൽ",
            "ങ്ങളെ",
            "ങ്ങൾ",
            "മാരുടെ",
            "മാരിൽ",
            "മാർക്ക്",
            "മാരെ",
            "മാർ",
            "ത്തിനായി",
            "ത്തോട്",
            "ത്തിൽ",
            "ത്തിന്റെ",
            "ത്തിന്റേ",
            "ത്തിനു",
            "ത്തിന്",
            "ത്താൽ",
            "ക്കായി",
            "ക്കുള്ള",
            "ക്കുള്ളിൽ",
            "ക്കു",
            "ക്ക്",
            "യ്ക്ക്",
            "യോട്",
            "യോടെ",
            "യിൽ",
            "യുടെ",
            "യാൽ",
            "യെ",
            "കൊണ്ട്",
            "കുറിച്ച്",
            "വേണ്ടി",
            "പോലെ",
            "ശേഷം",
            "മുമ്പ്",
            "നിന്ന്",
            "ഇൽ",
            "ൽ",
            "ന്",
            "നെ",
            "ഓട്",
            "ആൽ",
            "കൾ",
            "കള്"
        )
    }
}
