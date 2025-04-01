package org.gnit.lucenekmp.jdkport

import kotlin.math.max

/**
 * Returns the number of zero bits preceding the highest-order
 * ("leftmost") one-bit in the two's complement binary representation
 * of the specified `long` value.  Returns 64 if the
 * specified value has no one-bits in its two's complement representation,
 * in other words if it is equal to zero.
 *
 *
 * Note that this method is closely related to the logarithm base 2.
 * For all positive `long` values x:
 *
 *  * floor(log<sub>2</sub>(x)) = `63 - numberOfLeadingZeros(x)`
 *  * ceil(log<sub>2</sub>(x)) = `64 - numberOfLeadingZeros(x - 1)`
 *
 *
 * @param i the value whose number of leading zeros is to be computed
 * @return the number of zero bits preceding the highest-order
 * ("leftmost") one-bit in the two's complement binary representation
 * of the specified `long` value, or 64 if the value
 * is equal to zero.
 * @since 1.5
 */
fun Long.Companion.numberOfLeadingZeros(i: Long): Int {
    val x = (i ushr 32).toInt()
    return if (x == 0)
        32 + Int.numberOfLeadingZeros(i.toInt())
    else
        Int.numberOfLeadingZeros(x)
}

/**
 * Returns the number of zero bits following the lowest-order ("rightmost")
 * one-bit in the two's complement binary representation of the specified
 * `long` value.  Returns 64 if the specified value has no
 * one-bits in its two's complement representation, in other words if it is
 * equal to zero.
 *
 * @param i the value whose number of trailing zeros is to be computed
 * @return the number of zero bits following the lowest-order ("rightmost")
 * one-bit in the two's complement binary representation of the
 * specified `long` value, or 64 if the value is equal
 * to zero.
 * @since 1.5
 */

fun Long.Companion.numberOfTrailingZeros(i: Long): Int {
    val x = i.toInt()
    return if (x == 0)
        32 + Int.numberOfTrailingZeros((i ushr 32).toInt())
    else
        Int.numberOfTrailingZeros(x)
}

/**
 * Compares two `long` values numerically.
 * The value returned is identical to what would be returned by:
 * <pre>
 * Long.valueOf(x).compareTo(Long.valueOf(y))
</pre> *
 *
 * @param  x the first `long` to compare
 * @param  y the second `long` to compare
 * @return the value `0` if `x == y`;
 * a value less than `0` if `x < y`; and
 * a value greater than `0` if `x > y`
 * @since 1.7
 */
fun Long.Companion.compare(x: Long, y: Long): Int {
    return if (x < y) -1 else (if (x == y) 0 else 1)
}

/**
 * Returns the number of one-bits in the two's complement binary
 * representation of the specified [Long] value. This function is sometimes
 * referred to as the population count.
 *
 * @param i the value whose bits are to be counted.
 * @return the number of one-bits in the two's complement binary representation of [i].
 */
fun Long.Companion.bitCount(i: Long): Int {
    var x = i
    x = x - ((x ushr 1) and 0x5555555555555555L)
    x = (x and 0x3333333333333333L) + ((x ushr 2) and 0x3333333333333333L)
    x = (x + (x ushr 4)) and 0x0f0f0f0f0f0f0f0fL
    x += x ushr 8
    x += x ushr 16
    x += x ushr 32
    return (x and 0x7f).toInt()
}

/**
 * Returns the value obtained by rotating the two's complement binary
 * representation of the specified `long` value left by the
 * specified number of bits.  (Bits shifted out of the left hand, or
 * high-order, side reenter on the right, or low-order.)
 *
 *
 * Note that left rotation with a negative distance is equivalent to
 * right rotation: `rotateLeft(val, -distance) == rotateRight(val,
 * distance)`.  Note also that rotation by any multiple of 64 is a
 * no-op, so all but the last six bits of the rotation distance can be
 * ignored, even if the distance is negative: `rotateLeft(val,
 * distance) == rotateLeft(val, distance & 0x3F)`.
 *
 * @param i the value whose bits are to be rotated left
 * @param distance the number of bit positions to rotate left
 * @return the value obtained by rotating the two's complement binary
 * representation of the specified `long` value left by the
 * specified number of bits.
 * @since 1.5
 */
fun Long.Companion.rotateLeft(i: Long, distance: Int): Long {
    return (i shl distance) or (i ushr -distance)
}

/**
 * Compares two `long` values numerically treating the values
 * as unsigned.
 *
 * @param  x the first `long` to compare
 * @param  y the second `long` to compare
 * @return the value `0` if `x == y`; a value less
 * than `0` if `x < y` as unsigned values; and
 * a value greater than `0` if `x > y` as
 * unsigned values
 * @since 1.8
 */
fun Long.Companion.compareUnsigned(x: Long, y: Long): Int {
    return Long.compare(x + MIN_VALUE, y + MIN_VALUE)
}


/**
 * Returns a string representation of the `long`
 * argument as an unsigned integer in base&nbsp;16.
 *
 *
 * The unsigned `long` value is the argument plus
 * 2<sup>64</sup> if the argument is negative; otherwise, it is
 * equal to the argument.  This value is converted to a string of
 * ASCII digits in hexadecimal (base&nbsp;16) with no extra
 * leading `0`s.
 *
 *
 * The value of the argument can be recovered from the returned
 * string `s` by calling [ ][Long.parseUnsignedLong].
 *
 *
 * If the unsigned magnitude is zero, it is represented by a
 * single zero character `'0'` (`'\u005Cu0030'`);
 * otherwise, the first character of the representation of the
 * unsigned magnitude will not be the zero character. The
 * following characters are used as hexadecimal digits:
 *
 * <blockquote>
 * `0123456789abcdef`
</blockquote> *
 *
 * These are the characters `'\u005Cu0030'` through
 * `'\u005Cu0039'` and  `'\u005Cu0061'` through
 * `'\u005Cu0066'`.  If uppercase letters are desired,
 * the [java.lang.String.toUpperCase] method may be called
 * on the result:
 *
 * <blockquote>
 * `Long.toHexString(n).toUpperCase()`
</blockquote> *
 *
 * @apiNote
 * The [java.util.HexFormat] class provides formatting and parsing
 * of byte arrays and primitives to return a string or adding to an [Appendable].
 * `HexFormat` formats and parses uppercase or lowercase hexadecimal characters,
 * with leading zeros and for byte arrays includes for each byte
 * a delimiter, prefix, and suffix.
 *
 * @param   i   a `long` to be converted to a string.
 * @return  the string representation of the unsigned `long`
 * value represented by the argument in hexadecimal
 * (base&nbsp;16).
 * @see java.util.HexFormat
 *
 * @see .parseUnsignedLong
 * @see .toUnsignedString
 * @since   1.0.2
 */
fun Long.Companion.toHexString(i: Long): String {
    return Long.toUnsignedString0(i, 4)
}

/**
 * Format a long (treated as unsigned) into a String.
 * @param val the value to format
 * @param shift the log2 of the base to format in (4 for hex, 3 for octal, 1 for binary)
 */
fun Long.Companion.toUnsignedString0(`val`: Long, shift: Int): String {
    // assert shift > 0 && shift <=5 : "Illegal shift value";
    val mag: Int = Long.SIZE_BITS - Long.numberOfLeadingZeros(`val`)
    val chars = max(((mag + (shift - 1)) / shift), 1)

    val buf = ByteArray(chars)
    Long.formatUnsignedLong0(`val`, shift, buf, 0, chars)
    return String.fromByteArray(buf, Charset.LATIN1)
}

/**
 * Format a long (treated as unsigned) into a byte buffer (LATIN1 version). If
 * `len` exceeds the formatted ASCII representation of `val`,
 * `buf` will be padded with leading zeroes.
 *
 * @param val the unsigned long to format
 * @param shift the log2 of the base to format in (4 for hex, 3 for octal, 1 for binary)
 * @param buf the byte buffer to write to
 * @param offset the offset in the destination buffer to start at
 * @param len the number of characters to write
 */
private fun Long.Companion.formatUnsignedLong0(`val`: Long, shift: Int, buf: ByteArray, offset: Int, len: Int) {
    var `val` = `val`
    var charPos = offset + len
    val radix = 1 shl shift
    val mask = radix - 1
    do {
        buf[--charPos] = Int.digits()[(`val`.toInt()) and mask].code.toByte()
        `val` = `val` ushr shift
    } while (charPos > offset)
}

/**
 * Returns a hash code for a `long` value; compatible with
 * `Long.hashCode()`.
 *
 * @param value the value to hash
 * @return a hash code value for a `long` value.
 * @since 1.8
 */
fun Long.Companion.hashCode(value: Long): Int {
    return (value xor (value ushr 32)).toInt()
}
