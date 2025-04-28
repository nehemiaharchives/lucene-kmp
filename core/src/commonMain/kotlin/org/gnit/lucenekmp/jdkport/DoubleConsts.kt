package org.gnit.lucenekmp.jdkport

/**
 * ported from jdk.internal.math.DoubleConsts
 */
object DoubleConsts {
    /**
     * The number of logical bits in the significand of a
     * `double` number, including the implicit bit.
     */
    val SIGNIFICAND_WIDTH: Int = Double.PRECISION

    /**
     * The exponent the smallest positive `double`
     * subnormal value would have if it could be normalized..
     */
    val MIN_SUB_EXPONENT: Int = Double.MIN_EXPONENT - (SIGNIFICAND_WIDTH - 1) // -1074

    /**
     * Bias used in representing a `double` exponent.
     */
    val EXP_BIAS: Int = (1 shl (Double.SIZE_BITS - SIGNIFICAND_WIDTH - 1)) - 1 // 1023

    /**
     * Bit mask to isolate the sign bit of a `double`.
     */
    val SIGN_BIT_MASK: Long = 1L shl (Double.SIZE_BITS - 1)

    /**
     * Bit mask to isolate the exponent field of a `double`.
     */
    val EXP_BIT_MASK: Long = ((1L shl (Double.SIZE_BITS - SIGNIFICAND_WIDTH)) - 1) shl (SIGNIFICAND_WIDTH - 1)

    /**
     * Bit mask to isolate the significand field of a `double`.
     */
    val SIGNIF_BIT_MASK: Long = (1L shl (SIGNIFICAND_WIDTH - 1)) - 1

    /**
     * Bit mask to isolate the magnitude bits (combined exponent and
     * significand fields) of a `double`.
     */
    val MAG_BIT_MASK: Long = EXP_BIT_MASK or SIGNIF_BIT_MASK
}