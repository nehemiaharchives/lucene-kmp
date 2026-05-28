package org.gnit.lucenekmp.analysis.my

/**
 * Light stemmer for Burmese.
 *
 * Burmese is heavily particle-based, so stemming is limited to common plural, case, and clause
 * suffixes that are useful for search recall.
 */
internal class BurmeseStemmer {
    /**
     * Stem an input buffer of Burmese text.
     *
     * @param s input buffer
     * @param len length of input buffer
     * @return length of input buffer after stemming
     */
    fun stem(s: CharArray, len: Int): Int {
        if (len <= 1) return len
        val word = s.concatToString(0, len)
        val stem = stripSuffix(word)
        if (stem.length == len) return len
        val out = stem.toCharArray()
        var i = 0
        while (i < out.size) {
            s[i] = out[i]
            i += 1
        }
        return out.size
    }

    private fun stripSuffix(word: String): String {
        for (suffix in SUFFIXES) {
            if (word.length > suffix.length + MIN_STEM_LENGTH && word.endsWith(suffix)) {
                return word.substring(0, word.length - suffix.length)
            }
        }
        return word
    }

    companion object {
        private const val MIN_STEM_LENGTH = 1

        private val SUFFIXES: Array<String> = arrayOf(
            "ကတည်းက",
            "အတွက်",
            "ကြောင့်",
            "များ",
            "တွေ",
            "တို့",
            "တွင်",
            "သည်",
            "သော",
            "တဲ့",
            "နှင့်",
            "နဲ့",
            "မှာ",
            "မှ",
            "ကို",
            "က",
            "၏",
            "ပါ"
        ).sortedByDescending { it.length }.toTypedArray()
    }
}
