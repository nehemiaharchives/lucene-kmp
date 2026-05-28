package org.gnit.lucenekmp.analysis.yo

/**
 * Normalizer for Yoruba written in Latin script.
 *
 * Folds tone marks and underdot letters to improve recall between fully marked and unmarked text.
 */
internal class YorubaNormalizer {
    /**
     * Normalize an input buffer of Yoruba text.
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
            if (ch == '\u0300' || ch == '\u0301' || ch == '\u0304' || ch == '\u0307' || ch == '\u0323') {
                i += 1
                continue
            }
            val normalized = when (ch) {
                '’', '‘', '‛', 'ʹ', 'ʼ', '`', '´' -> '\''
                '‐', '‑', '‒', '–', '—', '―' -> '-'
                'à', 'á', 'â', 'ã', 'ä', 'å', 'ā', 'ă', 'ą' -> 'a'
                'è', 'é', 'ê', 'ë', 'ē', 'ĕ', 'ė', 'ę', 'ě', 'ẹ' -> 'e'
                'ì', 'í', 'î', 'ï', 'ĩ', 'ī', 'ĭ', 'į', 'ị' -> 'i'
                'ò', 'ó', 'ô', 'õ', 'ö', 'ō', 'ŏ', 'ő', 'ọ' -> 'o'
                'ù', 'ú', 'û', 'ü', 'ũ', 'ū', 'ŭ', 'ů', 'ű', 'ų', 'ụ' -> 'u'
                'ç', 'ć', 'ĉ', 'ċ', 'č' -> 'c'
                'ñ', 'ń', 'ņ', 'ň', 'ṅ', 'ǹ' -> 'n'
                'ṣ', 'ş', 'ś', 'ŝ', 'š' -> 's'
                else -> ch
            }
            s[outLen] = normalized
            outLen += 1
            i += 1
        }
        return outLen
    }
}
