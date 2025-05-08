package org.gnit.lucenekmp.jdkport

import com.ionspin.kotlin.bignum.integer.BigInteger

// Constants
private const val MAX_CONSTANT = 16

private val posConst: Array<BigInteger?> =
    kotlin.arrayOfNulls(MAX_CONSTANT + 1)

private val negConst: Array<BigInteger?> =
    kotlin.arrayOfNulls(MAX_CONSTANT + 1)

/**
 * Returns a BigInteger whose value is equal to that of the
 * specified `long`.
 *
 * @apiNote This static factory method is provided in preference
 * to a (`long`) constructor because it allows for reuse of
 * frequently used BigIntegers.
 *
 * @param  value The value of the BigInteger to return.
 * @return a BigInteger with the specified value.
 */
fun BigInteger.Companion.valueOf(value: Long): BigInteger {
    // Return stashed constants for values in defined range
    if (value == 0L) return BigInteger.ZERO

    if (value > 0 && value <= MAX_CONSTANT) {
        val pos = posConst[value.toInt()]
        if (pos != null) return pos
    } else if (value < 0 && value >= -MAX_CONSTANT) {
        val neg = negConst[-value.toInt()]
        if (neg != null) return neg
    }

    return BigInteger(value)
}
