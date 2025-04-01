package org.gnit.lucenekmp.jdkport

object FloatConsts {

    /**
     * The number of logical bits in the significand of a
     * `float` number, including the implicit bit.
     */
    val SIGNIFICAND_WIDTH: Int = Float.PRECISION

    /**
     * The exponent the smallest positive `float`
     * subnormal value would have if it could be normalized.
     */
    val MIN_SUB_EXPONENT: Int = Float.MIN_EXPONENT - (SIGNIFICAND_WIDTH - 1) // -149

    /**
     * Bias used in representing a `float` exponent.
     */
    val EXP_BIAS: Int = (1 shl (Float.SIZE - SIGNIFICAND_WIDTH - 1)) - 1 // 127

    /**
     * Bit mask to isolate the sign bit of a `float`.
     */
    val SIGN_BIT_MASK: Int = 1 shl (Float.SIZE - 1)

    /**
     * Bit mask to isolate the exponent field of a `float`.
     */
    val EXP_BIT_MASK: Int = ((1 shl (Float.SIZE - SIGNIFICAND_WIDTH)) - 1) shl (SIGNIFICAND_WIDTH - 1)

    /**
     * Bit mask to isolate the significand field of a `float`.
     */
    val SIGNIF_BIT_MASK: Int = (1 shl (SIGNIFICAND_WIDTH - 1)) - 1

    /**
     * Bit mask to isolate the magnitude bits (combined exponent and
     * significand fields) of a `float`.
     */
    val MAG_BIT_MASK: Int = EXP_BIT_MASK or SIGNIF_BIT_MASK

}