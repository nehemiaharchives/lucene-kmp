package org.gnit.lucenekmp.analysis.ig

/**
 * Normalizer for Igbo written in Latin script.
 *
 * Applies light normalization for punctuation, tone marks and dotted-vowel variants.
 */
internal class IgboNormalizer {
    /**
     * Normalize an input buffer of Igbo text.
     *
     * @param s input buffer
     * @param len length of input buffer
     * @return length of input buffer after normalization
     */
    fun normalize(s: CharArray, len: Int): Int {
        if (len == 0) return 0
        var outLen = 0
        var i = 0
        while (i < len) {
            val ch = s[i]
            val normalized = when (ch) {
                '’', '‘', '‛', 'ʹ', 'ʼ', '`', '´' -> '\''
                '‐', '‑', '‒', '–', '—', '―' -> '-'
                'à', 'á', 'â', 'ã', 'ä', 'å', 'ā', 'ă', 'ą' -> 'a'
                'è', 'é', 'ê', 'ë', 'ē', 'ĕ', 'ė', 'ę', 'ě' -> 'e'
                'ì', 'í', 'î', 'ï', 'ĩ', 'ī', 'ĭ', 'į', 'ị' -> 'i'
                'ò', 'ó', 'ô', 'õ', 'ö', 'ō', 'ŏ', 'ő', 'ọ' -> 'o'
                'ù', 'ú', 'û', 'ü', 'ũ', 'ū', 'ŭ', 'ů', 'ű', 'ų', 'ụ' -> 'u'
                'ç', 'ć', 'ĉ', 'ċ', 'č' -> 'c'
                'ñ', 'ń', 'ņ', 'ň', 'ṅ' -> 'n'
                else -> ch
            }
            s[outLen] = normalized
            outLen += 1
            i += 1
        }
        return outLen
    }
}
