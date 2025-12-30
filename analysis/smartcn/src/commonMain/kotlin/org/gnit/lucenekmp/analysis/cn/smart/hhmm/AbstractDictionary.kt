package org.gnit.lucenekmp.analysis.cn.smart.hhmm

/**
 * SmartChineseAnalyzer abstract dictionary implementation.
 *
 * @lucene.experimental
 */
abstract class AbstractDictionary {
    companion object {
        /**
         * First Chinese Character in GB2312 (15 * 94) Characters in GB2312 are arranged in a grid of 94 *
         * 94, 0-14 are unassigned or punctuation.
         */
        const val GB2312_FIRST_CHAR: Int = 1410

        /**
         * Last Chinese Character in GB2312 (87 * 94). Characters in GB2312 are arranged in a grid of 94 *
         * 94, 88-94 are unassigned.
         */
        const val GB2312_CHAR_NUM: Int = 87 * 94

        /** Dictionary data contains 6768 Chinese characters with frequency statistics. */
        const val CHAR_NUM_IN_FILE: Int = 6768
    }

    /**
     * Transcode from GB2312 ID to Unicode.
     *
     * GB2312 charset is not available in common code, so this returns an empty string.
     */
    fun getCCByGB2312Id(ccid: Int): String {
        if (ccid < 0 || ccid > GB2312_CHAR_NUM) return ""
        val cc1 = ccid / 94 + 161
        val cc2 = ccid % 94 + 161
        val c = org.gnit.lucenekmp.jdkport.GB2312.decodeDouble(cc1, cc2)
        return if (c == '\uFFFD') "" else charArrayOf(c).concatToString()
    }

    /**
     * Transcode from Unicode to GB2312.
     *
     * GB2312 charset is not available in common code, so this returns -1.
     */
    fun getGB2312Id(ch: Char): Short {
        val encoded = org.gnit.lucenekmp.jdkport.GB2312.encodeChar(ch)
        if (encoded < 0 || encoded <= 0xFF) return (-1).toShort()
        val b0 = (encoded shr 8) and 0xFF
        val b1 = encoded and 0xFF
        return ((b0 - 161) * 94 + (b1 - 161)).toShort()
    }

    /**
     * 32-bit FNV Hash Function
     */
    fun hash1(c: Char): Long {
        val p = 1099511628211L
        var hash = -3750763034362895579L
        hash = (hash xor (c.code and 0x00FF).toLong()) * p
        hash = (hash xor (c.code shr 8).toLong()) * p
        hash += hash shl 13
        hash = hash xor (hash shr 7)
        hash += hash shl 3
        hash = hash xor (hash shr 17)
        hash += hash shl 5
        return hash
    }

    /**
     * 32-bit FNV Hash Function
     */
    fun hash1(carray: CharArray): Long {
        val p = 1099511628211L
        var hash = -3750763034362895579L
        for (d in carray) {
            hash = (hash xor (d.code and 0x00FF).toLong()) * p
            hash = (hash xor (d.code shr 8).toLong()) * p
        }
        return hash
    }

    /**
     * djb2 hash algorithm.
     */
    fun hash2(c: Char): Int {
        var hash = 5381
        hash = ((hash shl 5) + hash + c.code) and 0x00FF
        hash = ((hash shl 5) + hash + c.code) shr 8
        return hash
    }

    /**
     * djb2 hash algorithm.
     */
    fun hash2(carray: CharArray): Int {
        var hash = 5381
        for (d in carray) {
            hash = ((hash shl 5) + hash + d.code) and 0x00FF
            hash = ((hash shl 5) + hash + d.code) shr 8
        }
        return hash
    }
}
