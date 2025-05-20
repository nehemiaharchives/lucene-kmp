package org.gnit.lucenekmp.jdkport

import kotlin.text.HexFormat

/**
 * Returns the value obtained by rotating the two's complement binary
 * representation of the specified `int` value left by the
 * specified number of bits.  (Bits shifted out of the left hand, or
 * high-order, side reenter on the right, or low-order.)
 *
 *
 * Note that left rotation with a negative distance is equivalent to
 * right rotation: `rotateLeft(val, -distance) == rotateRight(val,
 * distance)`.  Note also that rotation by any multiple of 32 is a
 * no-op, so all but the last five bits of the rotation distance can be
 * ignored, even if the distance is negative: `rotateLeft(val,
 * distance) == rotateLeft(val, distance & 0x1F)`.
 *
 * @param i the value whose bits are to be rotated left
 * @param distance the number of bit positions to rotate left
 * @return the value obtained by rotating the two's complement binary
 * representation of the specified `int` value left by the
 * specified number of bits.
 * @since 1.5
 */
fun Int.Companion.rotateLeft(i: Int, distance: Int): Int {
    return (i shl distance) or (i ushr -distance)
}

// A configuration flag and digit table – adjust as needed.
const val COMPACT_STRINGS = true

/**
 * Returns a string representation of the unsigned integer value of [i] in binary (base 2).
 *
 * The unsigned value is calculated by interpreting [i] as an unsigned 32‑bit integer.
 */
fun Int.Companion.toBinaryString(i: Int): String = toUnsignedString0(i, shift = 1)

private fun toUnsignedString0(value: Int, shift: Int): String {
    // Compute the number of significant bits in the unsigned representation.
    // (Equivalent to: Integer.SIZE - Integer.numberOfLeadingZeros(value))
    val mag = 32 - value.countLeadingZeroBits()
    // Compute the number of digits needed in the given radix (radix = 1 shl shift).
    val chars = maxOf((mag + (shift - 1)) / shift, 1)
    return if (COMPACT_STRINGS) {
        // Compact strings: allocate a CharArray of length [chars]
        val buf = CharArray(chars)
        formatUnsignedInt(value, shift, buf, chars)
        buf.concatToString()
    } else {
        // For UTF16 version: (In Java this uses a byte array sized for UTF16 encoding;
        // here we use a CharArray directly since a Char represents a UTF16 code unit.)
        val buf = CharArray(chars)
        formatUnsignedIntUTF16(value, shift, buf, chars)
        buf.concatToString()
    }
}

private val digits = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray()

/**
 * Formats the unsigned integer [value] (using a base of 2^[shift]) into [buf] (LATIN1 version).
 * [len] is the total number of characters to write (leading zero‐padded if necessary).
 */
private fun formatUnsignedInt(value: Int, shift: Int, buf: CharArray, len: Int) {
    var v = value
    var charPos = len
    val radix = 1 shl shift
    val mask = radix - 1
    do {
        buf[--charPos] = digits[v and mask]
        v = v ushr shift
    } while (charPos > 0)
}

/**
 * Converts the argument to a `long` by an unsigned
 * conversion.  In an unsigned conversion to a `long`, the
 * high-order 32 bits of the `long` are zero and the
 * low-order 32 bits are equal to the bits of the integer
 * argument.
 *
 * Consequently, zero and positive `int` values are mapped
 * to a numerically equal `long` value and negative `int` values are mapped to a `long` value equal to the
 * input plus 2<sup>32</sup>.
 *
 * @param  x the value to convert to an unsigned `long`
 * @return the argument converted to `long` by an unsigned
 * conversion
 * @since 1.8
 */
fun Int.Companion.toUnsignedLong(x: Int): Long {
    return (x.toLong()) and 0xffffffffL
}


/**
 * Formats the unsigned integer [value] (using a base of 2^[shift]) into [buf] (UTF16 version).
 * [len] is the total number of characters to write (leading zero‐padded if necessary).
 */
private fun formatUnsignedIntUTF16(value: Int, shift: Int, buf: CharArray, len: Int) {
    var v = value
    var charPos = len
    val radix = 1 shl shift
    val mask = radix - 1
    do {
        buf[--charPos] = digits[v and mask]
        v = v ushr shift
    } while (charPos > 0)
}

@OptIn(ExperimentalStdlibApi::class)
private val NoLeadingZeroHexFormat = HexFormat {
    number {
        removeLeadingZeros = true
    }
}

@OptIn(ExperimentalStdlibApi::class)
fun Int.Companion.toHexString(i: Int): String {
    return i.toHexString(NoLeadingZeroHexFormat)
}

/**
 * Returns the number of zero bits preceding the highest-order ("leftmost") one-bit in the
 * two's complement binary representation of the specified [Int] value.  Returns 32 if the
 * value is zero.
 *
 * @param i the value whose leading zeros are to be counted
 * @return the number of leading zero bits in the two's complement binary representation of
 * the specified `int` value; zero if the value is zero.
 */
fun Int.Companion.numberOfLeadingZeros(i: Int): Int {
    // HD, Count leading 0's
    var i = i
    if (i <= 0) return if (i == 0) 32 else 0
    var n = 31
    if (i >= 1 shl 16) {
        n -= 16
        i = i ushr 16
    }
    if (i >= 1 shl 8) {
        n -= 8
        i = i ushr 8
    }
    if (i >= 1 shl 4) {
        n -= 4
        i = i ushr 4
    }
    if (i >= 1 shl 2) {
        n -= 2
        i = i ushr 2
    }
    return n - (i ushr 1)
}

/**
 * Returns the number of zero bits following the lowest-order ("rightmost")
 * one-bit in the two's complement binary representation of the specified
 * `int` value.  Returns 32 if the specified value has no
 * one-bits in its two's complement representation, in other words if it is
 * equal to zero.
 *
 * @param i the value whose number of trailing zeros is to be computed
 * @return the number of zero bits following the lowest-order ("rightmost")
 * one-bit in the two's complement binary representation of the
 * specified `int` value, or 32 if the value is equal
 * to zero.
 * @since 1.5
 */
fun Int.Companion.numberOfTrailingZeros(i: Int): Int {
    // HD, Count trailing 0's
    var i = i
    i = i.inv() and (i - 1)
    if (i <= 0) return i and 32
    var n = 1
    if (i > 1 shl 16) {
        n += 16
        i = i ushr 16
    }
    if (i > 1 shl 8) {
        n += 8
        i = i ushr 8
    }
    if (i > 1 shl 4) {
        n += 4
        i = i ushr 4
    }
    if (i > 1 shl 2) {
        n += 2
        i = i ushr 2
    }
    return n + (i ushr 1)
}

/**
 * Returns the number of one-bits in the two's complement binary
 * representation of the specified [Int] value. This function is sometimes
 * referred to as the population count.
 *
 * @param i the value whose bits are to be counted
 * @return the number of one-bits in the two's complement binary representation of [i]
 */
fun Int.Companion.bitCount(i: Int): Int {
    var x = i
    x = x - ((x ushr 1) and 0x55555555)
    x = (x and 0x33333333) + ((x ushr 2) and 0x33333333)
    x = (x + (x ushr 4)) and 0x0f0f0f0f
    x = x + (x ushr 8)
    x = x + (x ushr 16)
    return x and 0x3f
}


/**
 * Compares two [Int] values numerically.
 *
 * This is the same as the Java method `Integer.compare(int x, int y)`.
 *
 * @param x the first value to compare
 * @param y the second value to compare
 * @return a negative integer, zero, or a positive integer as the first argument is less than,
 * equal to, or greater than the second.
 */
fun Int.Companion.compare(x: Int, y: Int): Int{
    return if (x < y) -1 else (if (x == y) 0 else 1)
}

/**
 * Returns the signum function of the specified `int` value.  (The
 * return value is -1 if the specified value is negative; 0 if the
 * specified value is zero; and 1 if the specified value is positive.)
 *
 * @param i the value whose signum is to be computed
 * @return the signum function of the specified `int` value.
 * @since 1.5
 */
fun Int.Companion.signum(i: Int): Int {
    // HD, Section 2-7
    return (i shr 31) or (-i ushr 31)
}

fun Int.Companion.digits(): CharArray {
    return charArrayOf(
        '0', '1', '2', '3', '4', '5',
        '6', '7', '8', '9', 'a', 'b',
        'c', 'd', 'e', 'f', 'g', 'h',
        'i', 'j', 'k', 'l', 'm', 'n',
        'o', 'p', 'q', 'r', 's', 't',
        'u', 'v', 'w', 'x', 'y', 'z'
    )
}

/**
 * Returns the value obtained by reversing the order of the bytes in the
 * two's complement representation of the specified `int` value.
 *
 * @param i the value whose bytes are to be reversed
 * @return the value obtained by reversing the bytes in the specified
 * `int` value.
 * @since 1.5
 */
fun Int.Companion.reverseBytes(i: Int): Int {
    return (i shl 24) or
            ((i and 0xff00) shl 8) or
            ((i ushr 8) and 0xff00) or
            (i ushr 24)
}
