package org.gnit.lucenekmp.analysis.si

/**
 * Light stemmer for Sinhala.
 *
 * Sinhala marks common search-relevant noun forms with suffixes and postpositions. This strips only
 * common case, plural, and indefinite endings and intentionally stays dictionary-free for
 * Kotlin/Native.
 */
internal class SinhalaStemmer {
    fun stem(buffer: CharArray, len: Int): Int {
        if (len <= MIN_STEM_LENGTH) return len
        val word = buffer.concatToString(0, len)
        val stripped = stripSuffix(word)
        if (stripped == word || stripped.length < MIN_STEM_LENGTH) return len

        val out = stripped.toCharArray()
        var i = 0
        while (i < out.size) {
            buffer[i] = out[i]
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

        // Ordered longest to shortest to prefer combined plural/case endings over simple endings.
        private val SUFFIXES: Array<String> = arrayOf(
            "වලින්",
            "වලට",
            "වල",
            "යන්ගෙන්",
            "යන්ගේ",
            "යන්ට",
            "යන්",
            "වන්ගෙන්",
            "වන්ගේ",
            "වන්ට",
            "වන්",
            "ගෙන්",
            "යෙන්",
            "වෙන්",
            "යේ",
            "ගේ",
            "වක්",
            "යක්",
            "කින්",
            "ට",
            "ින්",
            "ක්",
            "ව",
            "ය"
        )
    }
}
