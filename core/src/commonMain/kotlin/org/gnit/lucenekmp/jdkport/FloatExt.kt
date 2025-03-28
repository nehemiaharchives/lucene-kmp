package org.gnit.lucenekmp.jdkport

import kotlin.math.abs
import kotlin.math.pow

/**
 * Compares the two specified `float` values. The sign
 * of the integer value returned is the same as that of the
 * integer that would be returned by the call:
 * <pre>
 * Float.valueOf(f1).compareTo(Float.valueOf(f2))
</pre> *
 *
 * @param   f1        the first `float` to compare.
 * @param   f2        the second `float` to compare.
 * @return  the value `0` if `f1` is
 * numerically equal to `f2`; a value less than
 * `0` if `f1` is numerically less than
 * `f2`; and a value greater than `0`
 * if `f1` is numerically greater than
 * `f2`.
 * @since 1.4
 */
fun Float.Companion.compare(f1: Float, f2: Float): Int {
    if (f1 < f2) return -1 // Neither val is NaN, thisVal is smaller

    if (f1 > f2) return 1 // Neither val is NaN, thisVal is larger


    // Cannot use floatToRawIntBits because of possibility of NaNs.
    val thisBits = Float.floatToIntBits(f1)
    val anotherBits = Float.floatToIntBits(f2)

    return (if (thisBits == anotherBits) 0 else  // Values are equal
        (if (thisBits < anotherBits) -1 else  // (-0.0, 0.0) or (!NaN, NaN)
            1)) // (0.0, -0.0) or (NaN, !NaN)
}

/**
 * Returns `true` if the specified number is a Not-a-Number (NaN) value,
 * `false` otherwise.
 *
 * This corresponds to the isNaN operation defined in IEEE 754.
 *
 * @param v the value to be tested.
 * @return `true` if the argument is NaN; `false` otherwise.
 */
fun Float.Companion.isNaN(v: Float): Boolean = v != v


/**
 * Returns true if the argument is a finite floating-point value;
 * returns false otherwise (for NaN and infinity arguments).
 *
 * This method corresponds to the isFinite operation defined in IEEE 754.
 *
 * @param f the float value to be tested
 * @return true if the argument is a finite floating-point value, false otherwise.
 * @since 1.8
 */
fun Float.Companion.isFinite(f: Float): Boolean = abs(f) <= MAX_VALUE

/**
 * Returns an integer representing the raw bits of the given [value] according
 * to the IEEE 754 floating-point "single format" bit layout.
 *
 * This function preserves the exact NaN bit-pattern.
 */
fun Float.Companion.floatToRawIntBits(value: Float): Int {
    // In common code, Float.toBits() returns the raw bit pattern.
    return value.toBits()
}

/**
 * Returns an integer representing the bits of the given [value] according
 * to the IEEE 754 floating-point "single format" bit layout.
 *
 * If [value] is NaN, this function returns 0x7fc00000 (the canonical NaN value).
 */
fun Float.Companion.floatToIntBits(value: Float): Int {
    return if (value.isNaN()) 0x7fc00000 else value.toBits()
}

fun Float.Companion.intBitsToFloat(bits: Int): Float {
    val e = (bits ushr 23) and 0xff
    val mantissa = bits and 0x7fffff
    val sign = if ((bits ushr 31) == 0) 1 else -1

    return when (e) {
        0xff -> {
            // If exponent bits are all ones:
            //   if mantissa is zero, it's infinity; otherwise, it's NaN.
            if (mantissa == 0) {
                if (sign > 0) Float.POSITIVE_INFINITY else Float.NEGATIVE_INFINITY
            } else {
                Float.NaN
            }
        }
        else -> {
            // For normalized numbers, the implicit leading 1 is added;
            // for subnormals (e==0) the mantissa is shifted left.
            val m = if (e == 0) mantissa shl 1 else mantissa or 0x800000
            // The float value is computed as: sign * m * 2^(e - 150)
            // (150 = 127 (bias) + 23 (mantissa bits))
            (sign * m * 2.0.pow((e - 150).toDouble())).toFloat()
        }
    }
}

