package org.gnit.lucenekmp.analysis.`as`

/**
 * Light stemmer for Assamese.
 *
 * Applies conservative suffix and postposition stripping for common Assamese noun forms.
 * This is intentionally lightweight and dictionary-free for Kotlin/Native.
 */
internal class AssameseStemmer {
    fun stem(buffer: CharArray, len: Int): Int {
        if (len <= MIN_STEM_LENGTH) return len
        val word = buffer.concatToString(0, len)
        val stripped = stripSuffix(word)
        if (stripped == word || stripped.length < MIN_STEM_LENGTH) return len

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
            if (word.length >= suffix.length + MIN_STEM_LENGTH && word.endsWith(suffix)) {
                return word.substring(0, word.length - suffix.length)
            }
        }
        return word
    }

    companion object {
        private const val MIN_STEM_LENGTH = 2

        // Ordered longest to shortest to prefer plural/classifier + case combinations.
        private val SUFFIXES: Array<String> = arrayOf(
            "বিলাকৰ পৰা",
            "বিলাকলৈ",
            "বিলাকৰ",
            "বিলাকক",
            "বিলাকত",
            "বিলাক",
            "বোৰৰ পৰা",
            "বোৰলৈ",
            "বোৰৰ",
            "বোৰক",
            "বোৰত",
            "বোৰ",
            "সমূহৰ পৰা",
            "সমূহলৈ",
            "সমূহৰ",
            "সমূহক",
            "সমূহত",
            "সমূহ",
            "সকলৰ পৰা",
            "সকললৈ",
            "সকলৰ",
            "সকলক",
            "সকলত",
            "সকল",
            "কেইজনৰ",
            "কেইজনক",
            "কেইজন",
            "জনলৈ",
            "জনৰ",
            "জনক",
            "জনত",
            "জন",
            "খনলৈ",
            "খনৰ",
            "খনক",
            "খনত",
            "খন",
            "টোৰ",
            "টোক",
            "টোত",
            "টো",
            "টিৰ",
            "টিক",
            "টিত",
            "টি",
            "টাৰ",
            "টাক",
            "টাত",
            "টা",
            "লৈ",
            "ৰে",
            "ৰপৰা",
            "পৰা",
            "ত",
            "ক",
            "ৰ",
            "ে"
        )
    }
}
