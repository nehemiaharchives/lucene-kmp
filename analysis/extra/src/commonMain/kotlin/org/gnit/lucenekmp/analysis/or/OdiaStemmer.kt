package org.gnit.lucenekmp.analysis.or

/**
 * Light Stemmer for Odia.
 *
 * Applies conservative suffix and postposition stripping for common Odia noun and verb forms.
 * This is intentionally lightweight and dictionary-free for Kotlin/Native.
 */
internal class OdiaStemmer {
    fun stem(buffer: CharArray, len: Int): Int {
        if (len <= 2) return len
        val word = buffer.concatToString(0, len)
        val stripped = stripSuffix(word)
        if (stripped == word || stripped.length < 2) return len

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
            if (word.length >= suffix.length + 2 && word.endsWith(suffix)) {
                return word.substring(0, word.length - suffix.length)
            }
        }
        return word
    }

    companion object {
        // Ordered longest to shortest to prefer more specific postposition and inflection matches.
        private val SUFFIXES: Array<String> = arrayOf(
            "ମାନଙ୍କର",
            "ମାନଙ୍କଠାରୁ",
            "ମାନଙ୍କୁ",
            "ମାନଙ୍କ",
            "ଗୁଡ଼ିକରେ",
            "ଗୁଡ଼ିକରେ",
            "ଗୁଡ଼ିକର",
            "ଗୁଡ଼ିକର",
            "ଗୁଡିକରେ",
            "ଗୁଡିକର",
            "ଗୁଡ଼ିକୁ",
            "ଗୁଡ଼ିକୁ",
            "ଗୁଡିକୁ",
            "ଗୁଡ଼ିକ",
            "ଗୁଡ଼ିକ",
            "ଗୁଡିକ",
            "ଙ୍କଠାରୁ",
            "ଠାରୁ",
            "ିବାକୁ",
            "ବାକୁ",
            "ିବାର",
            "ିବା",
            "ଛନ୍ତି",
            "ଥିଲା",
            "ମାନେ",
            "ଙ୍କର",
            "ଙ୍କୁ",
            "ଙ୍କ",
            "ଟିକୁ",
            "ଟିରେ",
            "ଟିର",
            "ରେ",
            "ରୁ",
            "କୁ",
            "ର",
            "ଟି",
            "ଟା"
        )
    }
}
