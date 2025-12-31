package org.gnit.lucenekmp.analysis.ja.completion

object CharSequenceUtils {
    /** Checks if a char sequence is composed only of lowercase alphabets */
    fun isLowercaseAlphabets(s: CharSequence): Boolean {
        for (i in 0 until s.length) {
            val ch = s[i]
            if (!(isHalfWidthLowercaseAlphabet(ch) || isFullWidthLowercaseAlphabet(ch))) {
                return false
            }
        }
        return true
    }

    /** Checks if a char sequence is composed only of Katakana or hiragana */
    fun isKana(s: CharSequence): Boolean {
        for (i in 0 until s.length) {
            val ch = s[i]
            if (!(isHiragana(ch) || isKatakana(ch))) {
                return false
            }
        }
        return true
    }

    /** Checks if a char sequence is composed only of Katakana or lowercase alphabets */
    fun isKatakanaOrHWAlphabets(ref: CharSequence): Boolean {
        for (i in 0 until ref.length) {
            val ch = ref[i]
            if (!isKatakana(ch) && !isHalfWidthLowercaseAlphabet(ch)) {
                return false
            }
        }
        return true
    }

    /** Checks if a char is a Hiragana */
    private fun isHiragana(ch: Char): Boolean = ch in '\u3040'..'\u309f'

    /** Checks if a char is a Katakana */
    private fun isKatakana(ch: Char): Boolean = ch in '\u30a0'..'\u30ff'

    /** Checks if a char is a half-width lowercase alphabet */
    private fun isHalfWidthLowercaseAlphabet(ch: Char): Boolean = ch in '\u0061'..'\u007a'

    /** Checks if a char is a full-width lowercase alphabet */
    fun isFullWidthLowercaseAlphabet(ch: Char): Boolean = ch in '\uff41'..'\uff5a'

    /** Convert all hiragana in a string into Katakana */
    fun toKatakana(s: CharSequence): String {
        val chars = CharArray(s.length)
        for (i in 0 until s.length) {
            val ch = s[i]
            chars[i] = if (ch in '\u3041'..'\u3096' || ch == '\u309d' || ch == '\u309e') {
                (ch.code + 0x60).toChar()
            } else {
                ch
            }
        }
        return chars.concatToString()
    }
}
