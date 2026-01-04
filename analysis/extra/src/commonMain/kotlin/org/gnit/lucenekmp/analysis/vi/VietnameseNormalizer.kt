package org.gnit.lucenekmp.analysis.vi

/**
 * Normalizer for Vietnamese.
 *
 * Applies light diacritic folding for Vietnamese letters.
 */
internal class VietnameseNormalizer {
    /**
     * Normalize an input buffer of Vietnamese text.
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
            val normalized = foldChar(ch)
            s[outLen] = normalized
            outLen += 1
            i += 1
        }
        return outLen
    }

    private fun foldChar(ch: Char): Char {
        return when (ch) {
            'À', 'Á', 'Ả', 'Ã', 'Ạ',
            'Ă', 'Ắ', 'Ằ', 'Ẳ', 'Ẵ', 'Ặ',
            'Â', 'Ấ', 'Ầ', 'Ẩ', 'Ẫ', 'Ậ' -> 'A'
            'à', 'á', 'ả', 'ã', 'ạ',
            'ă', 'ắ', 'ằ', 'ẳ', 'ẵ', 'ặ',
            'â', 'ấ', 'ầ', 'ẩ', 'ẫ', 'ậ' -> 'a'
            'È', 'É', 'Ẻ', 'Ẽ', 'Ẹ',
            'Ê', 'Ế', 'Ề', 'Ể', 'Ễ', 'Ệ' -> 'E'
            'è', 'é', 'ẻ', 'ẽ', 'ẹ',
            'ê', 'ế', 'ề', 'ể', 'ễ', 'ệ' -> 'e'
            'Ì', 'Í', 'Ỉ', 'Ĩ', 'Ị' -> 'I'
            'ì', 'í', 'ỉ', 'ĩ', 'ị' -> 'i'
            'Ò', 'Ó', 'Ỏ', 'Õ', 'Ọ',
            'Ô', 'Ố', 'Ồ', 'Ổ', 'Ỗ', 'Ộ',
            'Ơ', 'Ớ', 'Ờ', 'Ở', 'Ỡ', 'Ợ' -> 'O'
            'ò', 'ó', 'ỏ', 'õ', 'ọ',
            'ô', 'ố', 'ồ', 'ổ', 'ỗ', 'ộ',
            'ơ', 'ớ', 'ờ', 'ở', 'ỡ', 'ợ' -> 'o'
            'Ù', 'Ú', 'Ủ', 'Ũ', 'Ụ',
            'Ư', 'Ứ', 'Ừ', 'Ử', 'Ữ', 'Ự' -> 'U'
            'ù', 'ú', 'ủ', 'ũ', 'ụ',
            'ư', 'ứ', 'ừ', 'ử', 'ữ', 'ự' -> 'u'
            'Ỳ', 'Ý', 'Ỷ', 'Ỹ', 'Ỵ' -> 'Y'
            'ỳ', 'ý', 'ỷ', 'ỹ', 'ỵ' -> 'y'
            'Đ' -> 'D'
            'đ' -> 'd'
            else -> ch
        }
    }
}
