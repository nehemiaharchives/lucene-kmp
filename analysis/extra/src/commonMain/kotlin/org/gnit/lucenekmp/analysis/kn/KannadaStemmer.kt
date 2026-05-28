package org.gnit.lucenekmp.analysis.kn

/**
 * Light stemmer for Kannada.
 *
 * Kannada is agglutinative, so this strips only common plural, case, and postposition suffixes.
 * This is intentionally lightweight and dictionary-free for Kotlin/Native.
 */
internal class KannadaStemmer {
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

        // Ordered longest to shortest to prefer plural/case combinations over simple endings.
        private val SUFFIXES: Array<String> = arrayOf(
            "ಗಳಿಂದಾಗಿ",
            "ಗಳಿಗಾಗಿ",
            "ಗಳೊಂದಿಗೆ",
            "ಗಳಲ್ಲಿನ",
            "ಗಳಲ್ಲಿ",
            "ಗಳಿಂದ",
            "ಗಳಿಗೆ",
            "ಗಳನ್ನು",
            "ಗಳನ್ನ",
            "ಗಳಾದ",
            "ಗಳ",
            "ಗಳು",
            "ರಿಗಾಗಿ",
            "ರಿಂದಾಗಿ",
            "ರೊಂದಿಗೆ",
            "ರಲ್ಲಿನ",
            "ರಲ್ಲಿ",
            "ರಿಂದ",
            "ರಿಗೆ",
            "ರನ್ನು",
            "ರಾದ",
            "ಯೊಂದಿಗೆ",
            "ಯಲ್ಲಿನ",
            "ಯಲ್ಲಿ",
            "ಯಿಂದ",
            "ಯಿಗೆ",
            "ಯನ್ನು",
            "ಯಾದ",
            "ದಲ್ಲಿನ",
            "ದಲ್ಲಿ",
            "ದಿಂದ",
            "ದಾಗಿ",
            "ದನ್ನು",
            "ದಾದ",
            "ನಿಗೆ",
            "ನಿಂದ",
            "ನಲ್ಲಿ",
            "ನನ್ನು",
            "ನಾದ",
            "ಕ್ಕಾಗಿ",
            "ಕ್ಕೆ",
            "ಗೆ",
            "ನ್ನು",
            "ನ್ನ",
            "ಲ್ಲಿ",
            "ದಿಂದ",
            "ಇಂದ",
            "ಯ",
            "ದ",
            "ರ"
        )
    }
}
