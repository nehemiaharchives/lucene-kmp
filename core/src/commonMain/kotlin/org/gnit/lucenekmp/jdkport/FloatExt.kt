package org.gnit.lucenekmp.jdkport

import kotlin.math.abs

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
 * Returns `true` if the specified number is infinitely
 * large in magnitude, `false` otherwise.
 *
 * @apiNote
 * This method corresponds to the isInfinite operation defined in
 * IEEE 754.
 *
 * @param   v   the value to be tested.
 * @return  `true` if the argument is positive infinity or
 * negative infinity; `false` otherwise.
 */
fun Float.Companion.isInfinite(v: Float): Boolean {
    return abs(v) > MAX_VALUE
}

/**
 * Returns an integer representing the raw bits of the given [value] according
 * to the IEEE 754 floating-point "single format" bit layout.
 *
 * This function preserves the exact NaN bit-pattern.
 */
fun Float.Companion.floatToRawIntBits(value: Float): Int {
    // In common code, Float.toBits() returns the raw bit pattern.
    return value.toRawBits()
}

/**
 * Returns an integer representing the bits of the given [value] according
 * to the IEEE 754 floating-point "single format" bit layout.
 *
 * If [value] is NaN, this function returns 0x7fc00000 (the canonical NaN value).
 */
fun Float.Companion.floatToIntBits(value: Float): Int {
    return if (value.isNaN()) 0x7fc00000 else value.toRawBits()
}

fun Float.Companion.intBitsToFloat(bits: Int): Float {
    return Float.fromBits(bits)
}

val Float.Companion.PRECISION: Int
    get() = 24

val Float.Companion.MIN_EXPONENT: Int
    get() = 1 - Float.MAX_EXPONENT // -126

val Float.Companion.MAX_EXPONENT: Int
    get() = (1 shl (Float.SIZE - Float.PRECISION - 1)) - 1 // 127

/**
 * A constant holding the smallest positive normal value of type
 * `float`, 2<sup>-126</sup>.  It is equal to the
 * hexadecimal floating-point literal `0x1.0p-126f` and also
 * equal to `Float.intBitsToFloat(0x00800000)`.
 *
 * @since 1.6
 */
val Float.Companion.MIN_NORMAL: Float
    get() = 1.1754944E-38f // 1.17549435E-38f


/**
 * The number of bits used to represent a `float` value.
 *
 * @since 1.5
 */
val Float.Companion.SIZE: Int
    get() = 32
