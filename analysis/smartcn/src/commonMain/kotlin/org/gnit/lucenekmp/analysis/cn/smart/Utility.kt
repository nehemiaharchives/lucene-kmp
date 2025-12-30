package org.gnit.lucenekmp.analysis.cn.smart

/**
 * SmartChineseAnalyzer utility constants and methods
 *
 * @lucene.experimental
 */
object Utility {
    val STRING_CHAR_ARRAY: CharArray = "未##串".toCharArray()

    val NUMBER_CHAR_ARRAY: CharArray = "未##数".toCharArray()

    val START_CHAR_ARRAY: CharArray = "始##始".toCharArray()

    val END_CHAR_ARRAY: CharArray = "末##末".toCharArray()

    /** Delimiters will be filtered to this character by SegTokenFilter */
    val COMMON_DELIMITER: CharArray = charArrayOf(',')

    /**
     * Space-like characters that need to be skipped: such as space, tab, newline, carriage return.
     */
    const val SPACES: String = " 　\t\r\n"

    /** Maximum bigram frequency (used in the smoothing function). */
    const val MAX_FREQUENCE: Int = 2079997 + 80000

    /**
     * compare two arrays starting at the specified offsets.
     *
     * @param larray left array
     * @param lstartIndex start offset into larray
     * @param rarray right array
     * @param rstartIndex start offset into rarray
     * @return 0 if the arrays are equal, 1 if larray > rarray, -1 if larray < rarray
     */
    fun compareArray(larray: CharArray?, lstartIndex: Int, rarray: CharArray?, rstartIndex: Int): Int {
        if (larray == null) {
            return if (rarray == null || rstartIndex >= rarray.size) 0 else -1
        } else if (rarray == null) {
            return if (lstartIndex >= larray.size) 0 else 1
        }

        var li = lstartIndex
        var ri = rstartIndex
        while (li < larray.size && ri < rarray.size && larray[li] == rarray[ri]) {
            li++
            ri++
        }
        return if (li == larray.size) {
            if (ri == rarray.size) 0 else -1
        } else {
            if (ri == rarray.size) 1 else if (larray[li] > rarray[ri]) 1 else -1
        }
    }

    /**
     * Compare two arrays, starting at the specified offsets, but treating shortArray as a prefix to
     * longArray. As long as shortArray is a prefix of longArray, return 0. Otherwise, behave as
     * compareArray.
     *
     * @param shortArray prefix array
     * @param shortIndex offset into shortArray
     * @param longArray long array (word)
     * @param longIndex offset into longArray
     * @return 0 if shortArray is a prefix of longArray, otherwise act as compareArray
     */
    fun compareArrayByPrefix(
        shortArray: CharArray?,
        shortIndex: Int,
        longArray: CharArray?,
        longIndex: Int
    ): Int {
        if (shortArray == null) return 0
        if (longArray == null) return if (shortIndex < shortArray.size) 1 else 0

        var si = shortIndex
        var li = longIndex
        while (si < shortArray.size && li < longArray.size && shortArray[si] == longArray[li]) {
            si++
            li++
        }
        return if (si == shortArray.size) {
            0
        } else {
            if (li == longArray.size) 1 else if (shortArray[si] > longArray[li]) 1 else -1
        }
    }

    /**
     * Return the internal CharType constant of a given character.
     *
     * @param ch input character
     * @return constant from CharType describing the character type.
     * @see CharType
     */
    fun getCharType(ch: Char): Int {
        if (ch.isSurrogate()) return CharType.SURROGATE
        if (ch >= 0x4E00.toChar() && ch <= 0x9FA5.toChar()) return CharType.HANZI
        if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z')) return CharType.LETTER
        if (ch >= '0' && ch <= '9') return CharType.DIGIT
        if (ch == ' ' || ch == '\t' || ch == '\r' || ch == '\n' || ch == '　') return CharType.SPACE_LIKE
        if ((ch >= 0x0021.toChar() && ch <= 0x00BB.toChar())
            || (ch >= 0x2010.toChar() && ch <= 0x2642.toChar())
            || (ch >= 0x3001.toChar() && ch <= 0x301E.toChar())
        ) return CharType.DELIMITER

        if ((ch >= 0xFF21.toChar() && ch <= 0xFF3A.toChar())
            || (ch >= 0xFF41.toChar() && ch <= 0xFF5A.toChar())
        ) return CharType.FULLWIDTH_LETTER
        if (ch >= 0xFF10.toChar() && ch <= 0xFF19.toChar()) return CharType.FULLWIDTH_DIGIT
        if (ch >= 0xFE30.toChar() && ch <= 0xFF63.toChar()) return CharType.DELIMITER
        return CharType.OTHER
    }
}
