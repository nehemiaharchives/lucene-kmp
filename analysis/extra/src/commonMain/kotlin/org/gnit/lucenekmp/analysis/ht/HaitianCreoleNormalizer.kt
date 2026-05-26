package org.gnit.lucenekmp.analysis.ht

/**
 * Normalizer for Haitian Creole.
 *
 * Applies light normalization for Latin punctuation, accent variants and common contracted pronoun
 * clitics.
 */
internal class HaitianCreoleNormalizer {
    /**
     * Normalize an input buffer of Haitian Creole text.
     *
     * @param s input buffer
     * @param len length of input buffer
     * @return length of input buffer after normalization
     */
    fun normalize(s: CharArray, len: Int): Int {
        if (len == 0) return 0
        val normalized = normalizeChars(s, len)
        val word = stripPronounClitic(normalized)
        val outLen = word.length
        var i = 0
        while (i < outLen) {
            s[i] = word[i]
            i += 1
        }
        return outLen
    }

    private fun normalizeChars(s: CharArray, len: Int): String {
        val builder = StringBuilder(len)
        var i = 0
        while (i < len) {
            val ch = s[i]
            val normalized = when (ch) {
                '’', '‘', '‛', 'ʹ', 'ʼ', '`', '´' -> '\''
                '‐', '‑', '‒', '–', '—', '―' -> '-'
                'à', 'á', 'â', 'ã', 'ä', 'å', 'ā', 'ă', 'ą' -> 'a'
                'è', 'é', 'ê', 'ë', 'ē', 'ĕ', 'ė', 'ę', 'ě' -> 'e'
                'ì', 'í', 'î', 'ï', 'ĩ', 'ī', 'ĭ', 'į' -> 'i'
                'ò', 'ó', 'ô', 'õ', 'ö', 'ō', 'ŏ', 'ő' -> 'o'
                'ù', 'ú', 'û', 'ü', 'ũ', 'ū', 'ŭ', 'ů', 'ű', 'ų' -> 'u'
                'ç', 'ć', 'ĉ', 'ċ', 'č' -> 'c'
                'ñ', 'ń', 'ņ', 'ň' -> 'n'
                else -> ch
            }
            builder.append(normalized)
            i += 1
        }
        return builder.toString()
    }

    private fun stripPronounClitic(word: String): String {
        for (prefix in PREFIX_CLITICS) {
            if (word.length > prefix.length + 1 && word.startsWith(prefix)) {
                return word.substring(prefix.length)
            }
        }
        for (suffix in SUFFIX_CLITICS) {
            if (word.length > suffix.length + 1 && word.endsWith(suffix)) {
                return word.substring(0, word.length - suffix.length)
            }
        }
        return word
    }

    companion object {
        private val PREFIX_CLITICS: Array<String> = arrayOf(
            "m'",
            "w'",
            "l'",
            "n'",
            "y'",
            "k'"
        )

        private val SUFFIX_CLITICS: Array<String> = arrayOf(
            "'m",
            "'w",
            "'l",
            "'n",
            "'y"
        )
    }
}
