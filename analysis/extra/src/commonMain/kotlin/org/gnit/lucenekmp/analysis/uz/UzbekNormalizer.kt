package org.gnit.lucenekmp.analysis.uz

/**
 * Normalizer for Uzbek Latin text.
 *
 * Unifies apostrophe variants used in o'/g' and folds common Latin diacritics found in imported
 * text while leaving Uzbek vowel distinctions intact.
 */
internal class UzbekNormalizer {
    /**
     * Normalize an input buffer of Uzbek text.
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
            val normalized = when {
                isApostropheVariant(ch) -> '\''
                ch == '‐' || ch == '‑' || ch == '‒' || ch == '–' || ch == '—' || ch == '―' -> '-'
                ch == 'á' || ch == 'à' || ch == 'â' || ch == 'ã' || ch == 'ā' || ch == 'ă' || ch == 'ą' -> 'a'
                ch == 'é' || ch == 'è' || ch == 'ê' || ch == 'ë' || ch == 'ē' || ch == 'ĕ' || ch == 'ė' || ch == 'ę' || ch == 'ě' -> 'e'
                ch == 'í' || ch == 'ì' || ch == 'î' || ch == 'ï' || ch == 'ĩ' || ch == 'ī' || ch == 'ĭ' || ch == 'į' -> 'i'
                ch == 'ó' || ch == 'ò' || ch == 'ô' || ch == 'õ' || ch == 'ö' || ch == 'ō' || ch == 'ŏ' || ch == 'ő' -> 'o'
                ch == 'ú' || ch == 'ù' || ch == 'û' || ch == 'ü' || ch == 'ũ' || ch == 'ū' || ch == 'ŭ' || ch == 'ů' || ch == 'ű' || ch == 'ų' -> 'u'
                ch == 'ç' || ch == 'ć' || ch == 'ĉ' || ch == 'ċ' || ch == 'č' -> 'c'
                ch == 'ñ' || ch == 'ń' || ch == 'ņ' || ch == 'ň' -> 'n'
                ch == 'ś' || ch == 'ŝ' || ch == 'ş' || ch == 'š' -> 's'
                else -> ch
            }
            s[outLen] = normalized
            outLen += 1
            i += 1
        }
        return outLen
    }

    companion object {
        fun isApostropheVariant(ch: Char): Boolean {
            return ch == '\'' ||
                ch == 'ʻ' ||
                ch == 'ʼ' ||
                ch == '’' ||
                ch == '‘' ||
                ch == '‛' ||
                ch == 'ʹ' ||
                ch == '`' ||
                ch == '´' ||
                ch == 'ʽ'
        }
    }
}
