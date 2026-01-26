package org.gnit.lucenekmp.util

import kotlin.math.ceil

/*
 * Some of this code came from the excellent Unicode
 * conversion examples from:
 *
 *   http://www.unicode.org/Public/PROGRAMS/CVTUTF
 *
 * Full Copyright for that code follows:
 */

/*
 * Copyright 2001-2004 Unicode, Inc.
 *
 * Disclaimer
 *
 * This source code is provided as is by Unicode, Inc. No claims are
 * made as to fitness for any particular purpose. No warranties of any
 * kind are expressed or implied. The recipient agrees to determine
 * applicability of information provided. If this file has been
 * purchased on magnetic or optical media from Unicode, Inc., the
 * sole remedy for any claim will be exchange of defective media
 * within 90 days of receipt.
 *
 * Limitations on Rights to Redistribute This Code
 *
 * Unicode, Inc. hereby grants the right to freely use the information
 * supplied in this file in the creation of products supporting the
 * Unicode Standard, and to make copies of this file in any form
 * for internal or external distribution as long as this notice
 * remains attached.
 */

/*
 * Additional code came from the IBM ICU library.
 *
 *  http://www.icu-project.org
 *
 * Full Copyright for that code follows.
 */

/*
 * Copyright (C) 1999-2010, International Business Machines
 * Corporation and others.  All Rights Reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * provided that the above copyright notice(s) and this permission notice appear
 * in all copies of the Software and that both the above copyright notice(s) and
 * this permission notice appear in supporting documentation.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT OF THIRD PARTY RIGHTS.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR HOLDERS INCLUDED IN THIS NOTICE BE
 * LIABLE FOR ANY CLAIM, OR ANY SPECIAL INDIRECT OR CONSEQUENTIAL DAMAGES, OR
 * ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER
 * IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT
 * OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 *
 * Except as contained in this notice, the name of a copyright holder shall not
 * be used in advertising or otherwise to promote the sale, use or other
 * dealings in this Software without prior written authorization of the
 * copyright holder.
 */


/**
 * Class to encode java's UTF16 char[] into UTF8 byte[] without always allocating a new byte[] as
 * String.getBytes(StandardCharsets.UTF_8) does.
 *
 * @lucene.internal
 */
object UnicodeUtil {
    /**
     * A binary term consisting of a number of 0xff bytes, likely to be bigger than other terms (e.g.
     * collation keys) one would normally encounter, and definitely bigger than any UTF-8 terms.
     *
     *
     * WARNING: This is not a valid UTF8 Term
     */
    val BIG_TERM: BytesRef = BytesRef(
        byteArrayOf(
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1
        )
    ) // TODO this is unrelated here find a better place for it

    const val UNI_SUR_HIGH_START: Int = 0xD800
    const val UNI_SUR_HIGH_END: Int = 0xDBFF
    const val UNI_SUR_LOW_START: Int = 0xDC00
    const val UNI_SUR_LOW_END: Int = 0xDFFF
    const val UNI_REPLACEMENT_CHAR: Int = 0xFFFD

    const val UNI_MAX_BMP: Long = 0x0000FFFF

    const val HALF_SHIFT: Long = 10 // TODO const val HALF_SHIFT: Int = 10  // Changed to Int
    const val HALF_MASK: Long = 0x3FFL

    const val MIN_SUPPLEMENTARY_CODE_POINT: Int = 0x10000

    val SURROGATE_OFFSET: Int = (MIN_SUPPLEMENTARY_CODE_POINT
            - (UNI_SUR_HIGH_START shl HALF_SHIFT.toInt()) // TODO (UNI_SUR_HIGH_START shl HALF_SHIFT)  // No need for toInt() conversion
            - UNI_SUR_LOW_START)

    /** Maximum number of UTF8 bytes per UTF16 character.  */
    const val MAX_UTF8_BYTES_PER_CHAR: Int = 3


    // line 389
    const val v: Int = Int.MIN_VALUE

    /* Borrowed from Python's 3.1.2 sources,
     Objects/unicodeobject.c, and modified (see commented
     out section, and the -1s) to disallow the reserved for
     future (RFC 3629) 5/6 byte sequence characters, and
     invalid 0xFE and 0xFF bytes.*/
    val utf8CodeLength: IntArray = arrayOf(
        intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1),
        intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1),
        intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1),
        intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1),
        intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1),
        intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1),
        intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1),
        intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1),
        intArrayOf(v, v, v, v, v, v, v, v, v, v, v, v, v, v, v, v),
        intArrayOf(v, v, v, v, v, v, v, v, v, v, v, v, v, v, v, v),
        intArrayOf(v, v, v, v, v, v, v, v, v, v, v, v, v, v, v, v),
        intArrayOf(v, v, v, v, v, v, v, v, v, v, v, v, v, v, v, v),
        intArrayOf(2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2),
        intArrayOf(2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2),
        intArrayOf(3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3),
        intArrayOf(4, 4, 4, 4, 4, 4, 4, 4 /* , 5, 5, 5, 5, 6, 6, 0, 0 */)
    ).flatMap { it.toList() }.toIntArray()
    // line 414


    // line 183
    /**
     * Encode characters from this String, starting at offset for length characters. It is the
     * responsibility of the caller to make sure that the destination array is large enough.
     */
    fun UTF16toUTF8(
        s: CharSequence, offset: Int, length: Int, out: ByteArray
    ): Int {
        return UTF16toUTF8(s, offset, length, out, 0)
    }

    /**
     * Encode characters from a char[] source, starting at offset for length chars. It is the
     * responsibility of the caller to make sure that the destination array is large enough.
     */
    fun UTF16toUTF8(
        source: CharArray, offset: Int, length: Int, out: ByteArray
    ): Int {
        var upto = 0
        var i = offset
        val end = offset + length

        while (i < end) {
            val code = source[i++].code

            if (code < 0x80) out[upto++] = code.toByte()
            else if (code < 0x800) {
                out[upto++] = (0xC0 or (code shr 6)).toByte()
                out[upto++] = (0x80 or (code and 0x3F)).toByte()
            } else if (code < 0xD800 || code > 0xDFFF) {
                out[upto++] = (0xE0 or (code shr 12)).toByte()
                out[upto++] = (0x80 or ((code shr 6) and 0x3F)).toByte()
                out[upto++] = (0x80 or (code and 0x3F)).toByte()
            } else {
                // surrogate pair
                // confirm valid high surrogate
                if (code < 0xDC00 && i < end) {
                    var utf32 = source[i].code
                    // confirm valid low surrogate and write pair
                    if (utf32 >= 0xDC00 && utf32 <= 0xDFFF) {
                        utf32 = (code shl 10) + utf32 + SURROGATE_OFFSET
                        i++
                        out[upto++] = (0xF0 or (utf32 shr 18)).toByte()
                        out[upto++] = (0x80 or ((utf32 shr 12) and 0x3F)).toByte()
                        out[upto++] = (0x80 or ((utf32 shr 6) and 0x3F)).toByte()
                        out[upto++] = (0x80 or (utf32 and 0x3F)).toByte()
                        continue
                    }
                }
                // replace unpaired surrogate or out-of-order low surrogate
                // with substitution character
                out[upto++] = 0xEF.toByte()
                out[upto++] = 0xBF.toByte()
                out[upto++] = 0xBD.toByte()
            }
        }
        // assert matches(source, offset, length, out, upto);
        return upto
    }


    /**
     * Encode characters from this String, starting at offset for length characters. Output to the
     * destination array will begin at `outOffset`. It is the responsibility of the caller to
     * make sure that the destination array is large enough.
     *
     *
     * note this method returns the final output offset (outOffset + number of bytes written)
     */
    fun UTF16toUTF8(
        s: CharSequence, offset: Int, length: Int, out: ByteArray, outOffset: Int
    ): Int {
        val end = offset + length

        var upto = outOffset
        var i = offset
        while (i < end) {
            val code = s.get(i).code

            if (code < 0x80) out[upto++] = code.toByte()
            else if (code < 0x800) {
                out[upto++] = (0xC0 or (code shr 6)).toByte()
                out[upto++] = (0x80 or (code and 0x3F)).toByte()
            } else if (code < 0xD800 || code > 0xDFFF) {
                out[upto++] = (0xE0 or (code shr 12)).toByte()
                out[upto++] = (0x80 or ((code shr 6) and 0x3F)).toByte()
                out[upto++] = (0x80 or (code and 0x3F)).toByte()
            } else {
                // surrogate pair
                // confirm valid high surrogate
                if (code < 0xDC00 && (i < end - 1)) {
                    var utf32 = s.get(i + 1).code
                    // confirm valid low surrogate and write pair
                    if (utf32 >= 0xDC00 && utf32 <= 0xDFFF) {
                        utf32 = (code shl 10) + utf32 + SURROGATE_OFFSET
                        out[upto++] = (0xF0 or (utf32 shr 18)).toByte()
                        out[upto++] = (0x80 or ((utf32 shr 12) and 0x3F)).toByte()
                        out[upto++] = (0x80 or ((utf32 shr 6) and 0x3F)).toByte()
                        out[upto++] = (0x80 or (utf32 and 0x3F)).toByte()
                        i += 2
                        continue
                    }
                }
                // replace unpaired surrogate or out-of-order low surrogate
                // with substitution character
                out[upto++] = 0xEF.toByte()
                out[upto++] = 0xBF.toByte()
                out[upto++] = 0xBD.toByte()
            }
            i++
        }
        // assert matches(s, offset, length, out, upto);
        return upto
    }

    /**
     * Calculates the number of UTF8 bytes necessary to write a UTF16 string.
     *
     * @return the number of bytes written
     */
    fun calcUTF16toUTF8Length(s: CharSequence, offset: Int, len: Int): Int {
        val end = offset + len

        var res = 0
        var i = offset
        while (i < end) {
            val code = s[i].code

            if (code < 0x80) res++
            else if (code < 0x800) {
                res += 2
            } else if (code < 0xD800 || code > 0xDFFF) {
                res += 3
            } else {
                // surrogate pair
                // confirm valid high surrogate
                if (code < 0xDC00 && (i < end - 1)) {
                    val utf32 = s[i + 1].code
                    // confirm valid low surrogate and write pair
                    if (utf32 >= 0xDC00 && utf32 <= 0xDFFF) {
                        i++
                        res += 4
                        i++
                        continue
                    }
                }
                res += 3
            }
            i++
        }

        return res
    }

    fun validUTF16String(s: CharSequence): Boolean {
        val size = s.length
        var i = 0
        while (i < size) {
            val ch = s[i]
            if (ch.code >= UNI_SUR_HIGH_START && ch.code <= UNI_SUR_HIGH_END) {
                if (i < size - 1) {
                    i++
                    val nextCH = s[i]
                    if (nextCH.code >= UNI_SUR_LOW_START && nextCH.code <= UNI_SUR_LOW_END) {
                        // Valid surrogate pair
                    } else  // Unmatched high surrogate
                        return false
                } else  // Unmatched high surrogate
                    return false
            } else if (ch.code >= UNI_SUR_LOW_START && ch.code <= UNI_SUR_LOW_END)  // Unmatched low surrogate
                return false
            i++
        }

        return true
    }

    fun validUTF16String(s: CharArray, size: Int): Boolean {
        var i = 0
        while (i < size) {
            val ch = s[i]
            if (ch.code >= UNI_SUR_HIGH_START && ch.code <= UNI_SUR_HIGH_END) {
                if (i < size - 1) {
                    i++
                    val nextCH = s[i]
                    if (nextCH.code >= UNI_SUR_LOW_START && nextCH.code <= UNI_SUR_LOW_END) {
                        // Valid surrogate pair
                    } else {
                        return false
                    }
                } else {
                    return false
                }
            } else if (ch.code >= UNI_SUR_LOW_START && ch.code <= UNI_SUR_LOW_END)  // Unmatched low surrogate
                return false
            i++
        }

        return true
    }

    /**
     * Returns the number of code points in this UTF8 sequence.
     *
     *
     * This method assumes valid UTF8 input. This method **does not perform** full
     * UTF8 validation, it will check only the first byte of each codepoint (for multi-byte sequences
     * any bytes after the head are skipped).
     *
     * @throws IllegalArgumentException If invalid codepoint header byte occurs or the content is
     * prematurely truncated.
     */
    fun codePointCount(utf8: BytesRef): Int {
        var pos = utf8.offset
        val limit = pos + utf8.length
        val bytes = utf8.bytes

        var codePointCount = 0
        while (pos < limit) {
            val v = bytes[pos].toInt() and 0xFF
            if (v <  /* 0xxx xxxx */0x80) {
                pos += 1
                codePointCount++
                continue
            }
            if (v >=  /* 110x xxxx */0xc0) {
                if (v <  /* 111x xxxx */0xe0) {
                    pos += 2
                    codePointCount++
                    continue
                }
                if (v <  /* 1111 xxxx */0xf0) {
                    pos += 3
                    codePointCount++
                    continue
                }
                if (v <  /* 1111 1xxx */0xf8) {
                    pos += 4
                    codePointCount++
                    continue
                }
                // fallthrough, consider 5 and 6 byte sequences invalid.
            }

            // Anything not covered above is invalid UTF8.
            throw IllegalArgumentException()
            codePointCount++
        }

        // Check if we didn't go over the limit on the last character.
        require(pos <= limit)

        return codePointCount
    }

    /** Holds a codepoint along with the number of bytes required to represent it in UTF8  */
    class UTF8CodePoint {
        var codePoint: Int = 0
        var numBytes: Int = 0
    }

    /** Shift value for lead surrogate to form a supplementary character.  */
    private const val LEAD_SURROGATE_SHIFT_: Int = 10

    /** Mask to retrieve the significant value from a trail surrogate.  */
    private const val TRAIL_SURROGATE_MASK_: Int = 0x3FF

    /** Trail surrogate minimum value  */
    private const val TRAIL_SURROGATE_MIN_VALUE: Int = 0xDC00

    /** Lead surrogate minimum value  */
    private const val LEAD_SURROGATE_MIN_VALUE: Int = 0xD800

    /** The minimum value for Supplementary code points  */
    private const val SUPPLEMENTARY_MIN_VALUE: Int = 0x10000

    /** Value that all lead surrogate starts with  */
    private const val LEAD_SURROGATE_OFFSET_: Int =
        LEAD_SURROGATE_MIN_VALUE - (SUPPLEMENTARY_MIN_VALUE shr LEAD_SURROGATE_SHIFT_)


    /**
     * This method assumes valid UTF8 input. This method **does not perform** full UTF8
     * validation, it will check only the first byte of each codepoint (for multi-byte sequences any
     * bytes after the head are skipped). It is the responsibility of the caller to make sure that the
     * destination array is large enough.
     *
     * @throws IllegalArgumentException If invalid codepoint header byte occurs or the content is
     * prematurely truncated.
     */
    fun UTF8toUTF32(utf8: BytesRef, ints: IntArray): Int {
        // TODO: ints must not be null, should be an assert
        var utf32Count = 0
        var utf8Upto = utf8.offset
        val bytes = utf8.bytes
        val utf8Limit = utf8.offset + utf8.length
        var reuse: UTF8CodePoint? = null
        while (utf8Upto < utf8Limit) {
            reuse = codePointAt(bytes, utf8Upto, reuse)
            ints[utf32Count++] = reuse.codePoint
            utf8Upto += reuse.numBytes
        }

        return utf32Count
    }

    /**
     * Computes the codepoint and codepoint length (in bytes) of the specified `offset` in the
     * provided `utf8` byte array, assuming UTF8 encoding. As with other related methods in this
     * class, this assumes valid UTF8 input and **does not perform** full UTF8
     * validation. Passing invalid UTF8 or a position that is not a valid header byte position may
     * result in undefined behavior. This makes no attempt to synchronize or validate.
     */
    @OptIn(ExperimentalStdlibApi::class)
    fun codePointAt(utf8: ByteArray, pos: Int, reuse: UTF8CodePoint?): UTF8CodePoint {
        var pos = pos
        var reuse = reuse
        if (reuse == null) {
            reuse = UTF8CodePoint()
        }

        val leadByte = utf8[pos].toInt() and 0xFF
        val numBytes: Int = utf8CodeLength[leadByte]!!
        reuse.numBytes = numBytes
        var v: Int
        when (numBytes) {
            1 -> {
                reuse.codePoint = leadByte
                return reuse
            }

            2 -> v = leadByte and 31
            3 -> v = leadByte and 15
            4 -> v = leadByte and 7
            else -> throw IllegalArgumentException(
                "Invalid UTF8 header byte: 0x ${leadByte.toHexString()}"
            )
        }

        // TODO: this may read past utf8's limit.
        val limit = pos + numBytes
        pos++
        while (pos < limit) {
            v = v shl 6 or (utf8[pos++].toInt() and 63)
        }
        reuse.codePoint = v

        return reuse
    }

    /**
     * Cover JDK 1.5 API. Create a String from an array of codePoints.
     *
     * @param codePoints The code array
     * @param offset The start of the text in the code point array
     * @param count The number of code points
     * @return a String representing the code points between offset and count
     * @throws IllegalArgumentException If an invalid code point is encountered
     * @throws IndexOutOfBoundsException If the offset or count are out of bounds.
     */
    fun newString(codePoints: IntArray, offset: Int, count: Int): String {
        require(count >= 0)
        var chars = CharArray(count)
        var w = 0
        var r = offset
        val e = offset + count
        while (r < e) {
            val cp = codePoints[r]
            require(!(cp < 0 || cp > 0x10ffff))
            while (true) {
                try {
                    if (cp < 0x010000) {
                        chars[w] = cp.toChar()
                        w++
                    } else {
                        chars[w] =
                            (LEAD_SURROGATE_OFFSET_ + (cp shr LEAD_SURROGATE_SHIFT_)).toChar()
                        chars[w + 1] =
                            (TRAIL_SURROGATE_MIN_VALUE + (cp and TRAIL_SURROGATE_MASK_)).toChar()
                        w += 2
                    }
                    break
                } catch (ex: IndexOutOfBoundsException) {
                    val newlen = (ceil(codePoints.size.toDouble() * (w + 2) / (r - offset + 1))).toInt()
                    val temp = CharArray(newlen)
                    /*java.lang.System.arraycopy(chars, 0, temp, 0, w)*/
                    chars.copyInto(
                        destination = temp,
                        destinationOffset = 0,
                        startIndex = 0,
                        endIndex = w
                    )

                    chars = temp
                }
            }
            ++r
        }
        return createString(chars, 0, w)
    }

    fun createString(value: CharArray, offset: Int, count: Int): String {
        // Validate the range
        if (offset < 0 || count < 0 || offset > value.size - count) {
            throw IndexOutOfBoundsException("Invalid range: offset=$offset, count=$count, array size=${value.size}")
        }
        // Create a new string from the subarray (copies the characters)
        return value.concatToString(offset, offset + count)
    }


    // for debugging
    @OptIn(ExperimentalStdlibApi::class)
    fun toHexString(s: String): String {
        val sb: StringBuilder = StringBuilder()
        for (i in 0..<s.length) {
            val ch = s.get(i)
            if (i > 0) {
                sb.append(' ')
            }
            if (ch.code < 128) {
                sb.append(ch)
            } else {
                if (ch.code >= UNI_SUR_HIGH_START && ch.code <= UNI_SUR_HIGH_END) {
                    sb.append("H:")
                } else if (ch.code >= UNI_SUR_LOW_START && ch.code <= UNI_SUR_LOW_END) {
                    sb.append("L:")
                } else if (ch.code > UNI_SUR_LOW_END) {
                    if (ch.code == 0xffff) {
                        sb.append("F:")
                    } else {
                        sb.append("E:")
                    }
                }

                sb.append("0x").append(ch.code.toHexString())
            }
        }
        return sb.toString()
    }


    /**
     * Interprets the given byte array as UTF-8 and converts to UTF-16. It is the responsibility of
     * the caller to make sure that the destination array is large enough.
     *
     *
     * NOTE: Full characters are read, even if this reads past the length passed (and can result in
     * an ArrayOutOfBoundsException if invalid UTF-8 is passed). Explicit checks for valid UTF-8 are
     * not performed.
     */
    // TODO: broken if chars.offset != 0
    @OptIn(ExperimentalStdlibApi::class)
    fun UTF8toUTF16(utf8: ByteArray, offset: Int, length: Int, out: CharArray): Int {
        var offset = offset
        var out_offset = 0
        val limit = offset + length
        while (offset < limit) {
            val b = utf8[offset++].toInt() and 0xff
            if (b < 0xc0) {
                if (b >= 0x80) throw RuntimeException()
                out[out_offset++] = b.toChar()
            } else if (b < 0xe0) {
                out[out_offset++] = (((b and 0x1f) shl 6) + (utf8[offset++].toInt() and 0x3f)).toChar()
            } else if (b < 0xf0) {
                out[out_offset++] =
                    (((b and 0xf) shl 12) + ((utf8[offset].toInt() and 0x3f) shl 6) + (utf8[offset + 1].toInt() and 0x3f)).toChar()
                offset += 2
            } else {
                if (b >= 0xf8) throw RuntimeException("b = 0x" + b.toHexString())
                val ch =
                    (((b and 0x7) shl 18)
                            + ((utf8[offset].toInt() and 0x3f) shl 12)
                            + ((utf8[offset + 1].toInt() and 0x3f) shl 6)
                            + (utf8[offset + 2].toInt() and 0x3f))
                offset += 3
                if (ch < UNI_MAX_BMP) {
                    out[out_offset++] = ch.toChar()
                } else {
                    val chHalf = ch - 0x0010000
                    out[out_offset++] = ((chHalf shr 10) + 0xD800).toChar()
                    out[out_offset++] =
                        Char(((chHalf.toLong() and HALF_MASK) + 0xDC00).toUShort())
                }
            }
        }
        return out_offset
    }

    /**
     * Returns the maximum number of utf8 bytes required to encode a utf16 (e.g., java char[], String)
     */
    fun maxUTF8Length(utf16Length: Int): Int {
        return utf16Length * MAX_UTF8_BYTES_PER_CHAR /*java.lang.Math.multiplyExact(utf16Length, MAX_UTF8_BYTES_PER_CHAR)*/ //TODO not sure how to implement this in kotlin common, need walk around
    }

    /**
     * Utility method for [.UTF8toUTF16]
     *
     * @see .UTF8toUTF16
     */
    fun UTF8toUTF16(bytesRef: BytesRef, chars: CharArray): Int {
        return UTF8toUTF16(
            bytesRef.bytes,
            bytesRef.offset,
            bytesRef.length,
            chars
        )
    }
}

fun String.codePointCount(start: Int = 0, end: Int = length): Int {
    var count = 0
    var i = start
    while (i < end) {
        val c = get(i)
        if (c.isHighSurrogate() && i + 1 < end) {
            val next = get(i + 1)
            if (next.isLowSurrogate()) {
                count++
                i += 2
                continue
            }
        }
        count++
        i++
    }
    return count
}

private fun Char.isHighSurrogate(): Boolean = this in '\uD800'..'\uDBFF'
private fun Char.isLowSurrogate(): Boolean = this in '\uDC00'..'\uDFFF'

/**
 * Returns a sequence of Unicode code point values from this string.
 *
 * Any surrogate pairs are combined into a single code point (using the formula:
 *   ((high - 0xD800) shl 10) + (low - 0xDC00) + 0x10000).
 * Other characters (including unpaired surrogates) are zero-extended.
 */
fun String.codePointsSeq(): Sequence<Int> = sequence {
    var i = 0
    while (i < this@codePointsSeq.length) {
        val c = this@codePointsSeq[i]
        if (c in '\uD800'..'\uDBFF' &&
            i + 1 < this@codePointsSeq.length &&
            this@codePointsSeq[i + 1] in '\uDC00'..'\uDFFF'
        ) {
            val high = c.code - 0xD800
            val low = this@codePointsSeq[i + 1].code - 0xDC00
            val codePoint = (high shl 10) + low + 0x10000
            yield(codePoint)
            i += 2
        } else {
            yield(c.code)
            i++
        }
    }
}
