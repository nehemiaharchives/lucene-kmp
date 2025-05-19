package org.gnit.lucenekmp.jdkport

/**
 * Utility class for string encoding and decoding.
 */
internal object StringCoding {

    // implement if needed
    /**
     * Count the number of leading non-zero ascii chars in the range.
     */
    /*fun countNonZeroAscii(s: String): Int {
        val value: ByteArray = s.value()
        if (s.isLatin1()) {
            return countNonZeroAsciiLatin1(value, 0, value.size)
        } else {
            return countNonZeroAsciiUTF16(value, 0, s.length)
        }
    }*/

    /**
     * Count the number of non-zero ascii chars in the range.
     */
    fun countNonZeroAsciiLatin1(ba: ByteArray, off: Int, len: Int): Int {
        val limit = off + len
        for (i in off..<limit) {
            if (ba[i] <= 0) {
                return i - off
            }
        }
        return len
    }

    /**
     * Count the number of leading non-zero ascii chars in the range.
     */
    fun countNonZeroAsciiUTF16(ba: ByteArray, off: Int, strlen: Int): Int {
        val limit = off + strlen
        for (i in off..<limit) {
            val c: Char = StringUTF16.charAt(ba, i)
            if (c.code == 0 || c.code > 0x7F) {
                return i - off
            }
        }
        return strlen
    }

    fun hasNegatives(ba: ByteArray, off: Int, len: Int): Boolean {
        return countPositives(ba, off, len) != len
    }

    /**
     * Count the number of leading positive bytes in the range.
     *
     * @implSpec the implementation must return len if there are no negative
     * bytes in the range. If there are negative bytes, the implementation must return
     * a value that is less than or equal to the index of the first negative byte
     * in the range.
     */
    fun countPositives(ba: ByteArray, off: Int, len: Int): Int {
        val limit = off + len
        for (i in off..<limit) {
            if (ba[i] < 0) {
                return i - off
            }
        }
        return len
    }

    fun implEncodeISOArray(
        sa: ByteArray, sp: Int,
        da: ByteArray, dp: Int, len: Int
    ): Int {
        var sp = sp
        var dp = dp
        var i = 0
        while (i < len) {
            val c: Char = StringUTF16.getChar(sa, sp++)
            if (c > '\u00FF') break
            da[dp++] = c.code.toByte()
            i++
        }
        return i
    }

    fun implEncodeAsciiArray(
        sa: CharArray, sp: Int,
        da: ByteArray, dp: Int, len: Int
    ): Int {
        var sp = sp
        var dp = dp
        var i = 0
        while (i < len) {
            val c = sa[sp++]
            if (c >= '\u0080') break
            da[dp++] = c.code.toByte()
            i++
        }
        return i
    }
}
