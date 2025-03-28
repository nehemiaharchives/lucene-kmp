package org.gnit.lucenekmp.jdkport

import space.kscience.kmath.operations.BigInt
import space.kscience.kmath.operations.BigIntField
import space.kscience.kmath.operations.toBigInt

fun BigInt.Companion.valueOf(value: Long): BigInt = value.toBigInt()

/**
 * Returns a byte array containing the two's-complement representation of this [BigInt].
 * The byte array is in big-endian order (most significant byte first) and has the minimum length
 * required to represent the number (including at least one sign bit).
 *
 * This implementation is inspired by Java's BigInteger.toByteArray().
 */
fun BigInt.toByteArray(): ByteArray {
    // Special case for zero.
    if (this == BigIntField.zero) return byteArrayOf(0)

    // Calculate the bit length without using toString(2)
    val absValue = this.abs()
    var bitLength = 0
    var tempValue = absValue
    while (tempValue > BigIntField.zero) {
        bitLength++
        tempValue = tempValue / 2.toBigInt()
    }

    // Total bits required include a sign bit.
    val totalBits = bitLength + 1
    val byteLen = (totalBits + 7) / 8  // ceiling division by 8

    // We'll compute the magnitude as if the number were non-negative.
    // The result will be in big-endian order.
    val positiveBytes = ByteArray(byteLen)
    var temp = this.abs()
    for (i in (byteLen - 1) downTo 0) {
        // Use % instead of mod for the modulo operation
        positiveBytes[i] = (temp % 256.toBigInt()).toString().toInt().toByte()
        temp = temp / 256.toBigInt()
    }

    return if (this < BigIntField.zero) {
        // For negative numbers, compute two's complement:
        // 1. Invert all bytes.
        // 2. Add one.
        var carry = 1
        val result = ByteArray(byteLen)
        for (i in (byteLen - 1) downTo 0) {
            // Invert the byte; note: use and 0xff to work with unsigned values.
            val inverted = (positiveBytes[i].toInt().inv()) and 0xff
            val sum = inverted + carry
            result[i] = (sum and 0xff).toByte()
            carry = if (sum > 0xff) 1 else 0
        }
        result
    } else {
        // For non-negative numbers, if the most significant bit is 1,
        // we need an extra leading 0 byte to indicate positive sign.
        if (positiveBytes[0].toInt() and 0x80 != 0) {
            byteArrayOf(0) + positiveBytes
        } else {
            positiveBytes
        }
    }
}

/**
 * Constructs a [BigInt] from a big-endian two’s complement byte array.
 *
 * This function interprets the given [bytes] as an unsigned big-endian number,
 * then adjusts it to a signed value if the highest (sign) bit is set.
 */
fun BigInt.Companion.fromByteArray(bytes: ByteArray): BigInt {
    var result = BigInt.ZERO
    // Iterate over each byte, multiplying the current result by 256 and adding the byte value (masked to 0..255)
    for (b in bytes) {
        result = result * 256.toBigInt() + (b.toInt() and 0xFF).toBigInt()
    }
    // If the highest bit is set, adjust for two's complement representation:
    // subtract 256^(number of bytes) to obtain the signed value.
    if (bytes.isNotEmpty() && bytes[0] < 0) {
        result -= 256.toBigInt().pow(bytes.size.toUInt())
    }
    return result
}
