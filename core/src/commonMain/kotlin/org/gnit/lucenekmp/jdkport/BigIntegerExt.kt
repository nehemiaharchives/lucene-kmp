package org.gnit.lucenekmp.jdkport

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.Sign
import kotlin.experimental.and
import kotlin.random.Random

private const val MAX_CONSTANT = 16

private val posConst: Array<BigInteger?> = arrayOfNulls(MAX_CONSTANT + 1)
private val negConst: Array<BigInteger?> = arrayOfNulls(MAX_CONSTANT + 1)

/**
 * Returns a BigInteger whose value is equal to that of the
 * specified `long`.
 */
fun BigInteger.Companion.valueOf(value: Long): BigInteger {
    if (value == 0L) return BigInteger.ZERO

    if (value > 0 && value <= MAX_CONSTANT) {
        val pos = posConst[value.toInt()]
        if (pos != null) return pos
    } else if (value < 0 && value >= -MAX_CONSTANT) {
        val neg = negConst[-value.toInt()]
        if (neg != null) return neg
    }

    return BigInteger.fromLong(value)
}

/**
 * Port of Java BigInteger(int numBits, Random rnd):
 * returns a non-negative random BigInteger in [0, 2^numBits - 1].
 */
fun randomBigInteger(numBits: Int, rnd: Random): BigInteger {
    val magnitude = randomBits(numBits, rnd)
    try {
        return BigInteger.fromByteArray(magnitude, Sign.POSITIVE)
    } finally {
        Arrays.fill(magnitude, 0.toByte())
    }
}

private fun randomBits(numBits: Int, rnd: Random): ByteArray {
    require(numBits >= 0) { "numBits must be non-negative" }
    val numBytes = ((numBits.toLong() + 7) / 8).toInt()
    val randomBits = ByteArray(numBytes)

    if (numBytes > 0) {
        rnd.nextBytes(randomBits)
        val excessBits = 8 * numBytes - numBits
        randomBits[0] = randomBits[0] and ((1 shl (8 - excessBits)) - 1).toByte()
    }

    return randomBits
}
