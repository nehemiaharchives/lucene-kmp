package org.gnit.lucenekmp.jdkport

import kotlin.math.pow

/** Minimum unbiased exponent of a *normalised* binary-64 value (-1022). */
val Double.Companion.MIN_EXPONENT: Int
    get() = -1022

/** Number of explicit significand bits in a binary-64 value (53). */
val Double.Companion.PRECISION: Int
    get() = 53


/**
 * Maximum exponent a finite `double` variable may have.
 * It is equal to the value returned by
 * `Math.getExponent(Double.MAX_VALUE)`.
 *
 * @since 1.6
 */
val Double.Companion.MAX_EXPONENT: Int  // 1023
    get() = (1 shl (Double.SIZE_BITS - Double.PRECISION - 1)) - 1


/**
 * Returns a representation of the specified double value according to the IEEE 754 "double format" bit layout,
 * preserving the exact NaN bit-pattern.
 *
 * This is analogous to Java’s doubleToRawLongBits.
 *
 * @param value a double-precision floating-point number.
 * @return the raw bits that represent the floating-point number.
 */
fun Double.Companion.doubleToRawLongBits(value: Double): Long {
    // Returns the raw bits, preserving non-canonical NaN values.
    return value.toBits()
}

/**
 * Returns a representation of the specified double value according to the IEEE 754 "double format" bit layout.
 *
 * If [value] is not NaN, this function returns the raw bits (via doubleToRawLongBits);
 * if [value] is NaN, it returns the canonical NaN value 0x7ff8000000000000L.
 *
 * This mimics Java’s doubleToLongBits, which collapses all NaN bit-patterns to a canonical NaN.
 *
 * @param value a double-precision floating-point number.
 * @return the bits that represent the floating-point number.
 */
fun Double.Companion.doubleToLongBits(value: Double): Long {
    return if (!value.isNaN()) doubleToRawLongBits(value) else 0x7ff8000000000000L
}

/**
 * Returns `true` if the specified number is a
 * Not-a-Number (NaN) value, `false` otherwise.
 *
 * @apiNote
 * This method corresponds to the isNaN operation defined in IEEE
 * 754.
 *
 * @param   v   the value to be tested.
 * @return  `true` if the value of the argument is NaN;
 * `false` otherwise.
 */
fun Double.Companion.isNaN(v: Double): Boolean {
    return (v != v)
}

/**
 * Compares the two specified `double` values. The sign
 * of the integer value returned is the same as that of the
 * integer that would be returned by the call:
 * <pre>
 * Double.valueOf(d1).compareTo(Double.valueOf(d2))
</pre> *
 *
 * @param   d1        the first `double` to compare
 * @param   d2        the second `double` to compare
 * @return  the value `0` if `d1` is
 * numerically equal to `d2`; a value less than
 * `0` if `d1` is numerically less than
 * `d2`; and a value greater than `0`
 * if `d1` is numerically greater than
 * `d2`.
 * @since 1.4
 */
fun Double.Companion.compare(d1: Double, d2: Double): Int {
    if (d1 < d2) return -1 // Neither val is NaN, thisVal is smaller

    if (d1 > d2) return 1 // Neither val is NaN, thisVal is larger


    // Cannot use doubleToRawLongBits because of possibility of NaNs.
    val thisBits = Double.doubleToLongBits(d1)
    val anotherBits = Double.doubleToLongBits(d2)

    return (if (thisBits == anotherBits) 0 else  // Values are equal
        (if (thisBits < anotherBits) -1 else  // (-0.0, 0.0) or (!NaN, NaN)
            1)) // (0.0, -0.0) or (NaN, !NaN)
}

fun Double.Companion.longBitsToDouble(bits: Long): Double {
    // Extract the sign, exponent, and fraction bits.
    val sign = if ((bits ushr 63) == 0L) 1.0 else -1.0
    val exponent = ((bits shr 52) and 0x7FFL).toInt()
    val fraction = bits and 0xFFFFFFFFFFFFFL

    // Handle special cases: infinities and NaN.
    if (exponent == 0x7FF) {
        return if (fraction == 0L) {
            if (sign > 0) Double.POSITIVE_INFINITY else Double.NEGATIVE_INFINITY
        } else {
            Double.NaN
        }
    }

    // For normal numbers, set the implicit bit.
    // For subnormals (exponent == 0) the significand is adjusted by shifting left.
    val m = if (exponent == 0) fraction shl 1 else fraction or (1L shl 52)

    // The true exponent is (exponent - 1075) where 1075 = 1023 + 52.
    // This computes: sign * (1.fraction) * 2^(exponent-1023) for normals,
    // and sign * (fraction * 2) * 2^(-1075) for subnormals.
    return sign * m.toDouble() * 2.0.pow(exponent - 1075.0)
}