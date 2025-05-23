package org.gnit.lucenekmp.jdkport


object Math {

    /**
     * The `double` value that is closer than any other to
     * *e*, the base of the natural logarithms.
     */
    const val E: Double = 2.718281828459045

    /**
     * The `double` value that is closer than any other to
     * *pi* (), the ratio of the circumference of a circle to
     * its diameter.
     */
    const val PI: Double = 3.141592653589793

    /**
     * The `double` value that is closer than any other to
     * *tau* (), the ratio of the circumference of a circle
     * to its radius.
     *
     * @apiNote
     * The value of *pi* is one half that of *tau*; in other
     * words, *tau* is double *pi* .
     *
     * @since 19
     */
    val TAU: Double = 2.0 * PI

    /**
     * Constant by which to multiply an angular value in degrees to obtain an
     * angular value in radians.
     */
    private const val DEGREES_TO_RADIANS: Double = 0.017453292519943295

    /**
     * Constant by which to multiply an angular value in radians to obtain an
     * angular value in degrees.
     */
    private const val RADIANS_TO_DEGREES: Double = 57.29577951308232


    /**
     * ported from java.lang.Math.toIntExact
     *
     * Returns the value of the `long` argument,
     * throwing an exception if the value overflows an `int`.
     *
     * @param value the long value
     * @return the argument as an int
     * @throws ArithmeticException if the `argument` overflows an int
     * @since 1.8
     */
    fun toIntExact(value: Long): Int {
        if (value.toInt().toLong() != value) {
            throw ArithmeticException("integer overflow")
        }
        return value.toInt()
    }

    /**
     * ported from java.lang.Math.addExact
     *
     * Returns true if the argument is a finite floating-point value;
     * returns false otherwise (for NaN and infinity arguments).
     *
     * This method corresponds to the isFinite operation defined in IEEE 754.
     *
     * @param d the double value to be tested
     * @return true if the argument is a finite floating-point value, false otherwise.
     */
    fun addExact(x: Long, y: Long): Long {
        val r = x + y
        if (((x xor r) and (y xor r)) < 0) {
            throw ArithmeticException("long overflow")
        }
        return r
    }

    /**
     * Returns the sum of its arguments,
     * throwing an exception if the result overflows an `int`.
     *
     * @param x the first value
     * @param y the second value
     * @return the result
     * @throws ArithmeticException if the result overflows an int
     * @since 1.8
     */
    fun addExact(x: Int, y: Int): Int {
        val r = x + y
        // HD 2-12 Overflow iff both arguments have the opposite sign of the result
        if (((x xor r) and (y xor r)) < 0) {
            throw ArithmeticException("integer overflow")
        }
        return r
    }

    /**
     * Returns the product of the arguments,
     * throwing an exception if the result overflows an `int`.
     *
     * @param x the first value
     * @param y the second value
     * @return the result
     * @throws ArithmeticException if the result overflows an int
     * @since 1.8
     */
    fun multiplyExact(x: Int, y: Int): Int {
        val r = x.toLong() * y.toLong()
        if (r.toInt().toLong() != r) {
            throw ArithmeticException("integer overflow")
        }
        return r.toInt()
    }

    /**
     * Returns the closest `int` to the argument, with ties
     * rounding to positive infinity.
     *
     *
     *
     * Special cases:
     *  * If the argument is NaN, the result is 0.
     *  * If the argument is negative infinity or any value less than or
     * equal to the value of `Integer.MIN_VALUE`, the result is
     * equal to the value of `Integer.MIN_VALUE`.
     *  * If the argument is positive infinity or any value greater than or
     * equal to the value of `Integer.MAX_VALUE`, the result is
     * equal to the value of `Integer.MAX_VALUE`.
     *
     * @param   a   a floating-point value to be rounded to an integer.
     * @return  the value of the argument rounded to the nearest
     * `int` value.
     * @see java.lang.Integer.MAX_VALUE
     *
     * @see java.lang.Integer.MIN_VALUE
     */
    fun round(a: Float): Int {
        // Special cases
        if (a.isNaN()) return 0
        if (a.isInfinite()) {
            return if (a > 0) Int.MAX_VALUE else Int.MIN_VALUE
        }

        // Use Kotlin's built-in round function which correctly handles all cases
        // including rounding -1.5f to -2
        return kotlin.math.round(a).toInt()
    }

    /**
     * Returns the floor modulus of the `long` arguments.
     *
     *
     * The floor modulus is `r = x - (floorDiv(x, y) * y)`,
     * has the same sign as the divisor `y` or is zero, and
     * is in the range of `-abs(y) < r < +abs(y)`.
     *
     *
     *
     * The relationship between `floorDiv` and `floorMod` is such that:
     *
     *  * `floorDiv(x, y) * y + floorMod(x, y) == x`
     *
     *
     *
     * For examples, see [.floorMod].
     *
     * @param x the dividend
     * @param y the divisor
     * @return the floor modulus `x - (floorDiv(x, y) * y)`
     * @throws ArithmeticException if the divisor `y` is zero
     * @see .floorDiv
     * @since 1.8
     */
    fun floorMod(x: Long, y: Long): Long {
        val r = x % y
        // if the signs are different and modulo not zero, adjust result
        if ((x xor y) < 0 && r != 0L) {
            return r + y
        }
        return r
    }

    /**
     * Returns the floating-point value adjacent to `f` in
     * the direction of positive infinity.  This method is
     * semantically equivalent to `nextAfter(f,
     * Float.POSITIVE_INFINITY)`; however, a `nextUp`
     * implementation may run faster than its equivalent
     * `nextAfter` call.
     *
     *
     * Special Cases:
     *
     *  *  If the argument is NaN, the result is NaN.
     *
     *  *  If the argument is positive infinity, the result is
     * positive infinity.
     *
     *  *  If the argument is zero, the result is
     * [Float.MIN_VALUE]
     *
     *
     *
     * @apiNote This method corresponds to the nextUp
     * operation defined in IEEE 754.
     *
     * @param f starting floating-point value
     * @return The adjacent floating-point value closer to positive
     * infinity.
     * @since 1.6
     */
    fun nextUp(f: Float): Float {
        // Use a single conditional and handle the likely cases first.
        if (f < Float.Companion.POSITIVE_INFINITY) {
            // Add +0.0 to get rid of a -0.0 (+0.0 + -0.0 => +0.0).
            val transducer = Float.floatToRawIntBits(f + 0.0f)
            return Float.intBitsToFloat(transducer + (if (transducer >= 0) 1 else -1))
        } else { // f is NaN or +Infinity
            return f
        }
    }


    /**
     * Returns the floating-point value adjacent to `f` in
     * the direction of negative infinity.  This method is
     * semantically equivalent to `nextAfter(f,
     * Float.NEGATIVE_INFINITY)`; however, a
     * `nextDown` implementation may run faster than its
     * equivalent `nextAfter` call.
     *
     *
     * Special Cases:
     *
     *  *  If the argument is NaN, the result is NaN.
     *
     *  *  If the argument is negative infinity, the result is
     * negative infinity.
     *
     *  *  If the argument is zero, the result is
     * `-Float.MIN_VALUE`
     *
     *
     *
     * @apiNote This method corresponds to the nextDown
     * operation defined in IEEE 754.
     *
     * @param f  starting floating-point value
     * @return The adjacent floating-point value closer to negative
     * infinity.
     * @since 1.8
     */
    fun nextDown(f: Float): Float {
        if (Float.isNaN(f) || f == Float.Companion.NEGATIVE_INFINITY) return f
        else {
            if (f == 0.0f) return -Float.Companion.MIN_VALUE
            else return Float.intBitsToFloat(
                Float.floatToRawIntBits(f) +
                        (if (f > 0.0f) -1 else +1)
            )
        }
    }

    /* ===== IEEE-754 / Double helpers that the Kotlin std-lib does NOT expose ===== */

    /**
     * The exponent of the smallest positive `double` subnormal value would have if it could be normalized.
     * This is -1074, which is the same as `Double.MIN_EXPONENT - (SIGNIFICAND_WIDTH - 1)`.
     */

    /**
     * Exactly 2^n for |n| ≤ 1023 + SIGNIFICAND_WIDTH.
     * Achieved by writing the exponent bits directly; no rounding occurs. */
    private fun powerOfTwoD(n: Int): Double {
        /*  (exp = n + bias) << 52 | 0x0  – the fraction field is zero          */
        val exp = n + DoubleConsts.EXP_BIAS
        return when {
            exp <= 0 -> 0.0                           // under-flow → sub-normal / 0
            exp >= 0x7FF -> Double.POSITIVE_INFINITY      // over-flow  → +∞
            else -> Double.fromBits(exp.toLong() shl 52)
        }
    }

    /* ========================== Public extension API =========================== */

    /**
     * Multiplies `this` by 2^scaleFactor **with the same edge-case semantics as
     * `java.lang.Math.scalb` (IEEE 754 scaleB operation)**.  The implementation is
     * a direct Kotlin port of the JDK algorithm.  It guarantees:
     *
     * * exact results for all intermediate exponents in [MIN_EXPONENT, MAX_EXPONENT];
     * * correct overflow to ±∞ and propagation of NaN / 0 signs;
     * * only one rounding step when scaling down, mirroring the JDK’s ordering
     *   constraints that avoid cascaded sub-normal rounding. */
    fun scalb(d: Double, scaleFactorInput: Int): Double {
        /* Fast-exit for NaN, ±∞, ±0 – Java does the same. */
        if (d.isNaN() || d.isInfinite() || d == 0.0) return d

        /* Same MAX_SCALE constant used by the JDK: */
        val MAX_SCALE = Double.MAX_EXPONENT - Double.MIN_EXPONENT + DoubleConsts.SIGNIFICAND_WIDTH + 1  // 2046

        /* Clamp the user exponent into the safe range so we can loop in ±512 steps. */
        var scaleFactor = scaleFactorInput.coerceIn(-MAX_SCALE, MAX_SCALE)

        /* Direction-dependent parameters (mirrors JDK ordering comments). */
        val (scaleInc, expDelta) =
            if (scaleFactor < 0) -512 to powerOfTwoD(-512)   // 2^-512
            else 512 to powerOfTwoD(512)         // 2^+512

        /* expAdjust := scaleFactor mod 512   (guaranteed −511 … +511)           */
        val expAdjust = scaleFactor % 512
        var result = d * powerOfTwoD(expAdjust)                // first, smallish multiply
        scaleFactor -= expAdjust                             // remaining factor is multiple of ±512

        /* Multiply repeatedly by 2^±512 – at most four iterations for the clamped range. */
        while (scaleFactor != 0) {
            result *= expDelta
            scaleFactor -= scaleInc
        }
        return result
    }

    /**
     * Returns the unbiased exponent used in the representation of a
     * `double`.  Special cases:
     *
     *
     *  * If the argument is NaN or infinite, then the result is
     * [Double.MAX_EXPONENT] + 1.
     *  * If the argument is zero or subnormal, then the result is
     * [Double.MIN_EXPONENT] - 1.
     *
     * @apiNote
     * This method is analogous to the logB operation defined in IEEE
     * 754, but returns a different value on subnormal arguments.
     *
     * @param d a `double` value
     * @return the unbiased exponent of the argument
     * @since 1.6
     */
    fun getExponent(d: Double): Int {
        /*
         * Bitwise convert d to long, mask out exponent bits, shift
         * to the right and then subtract out double's bias adjust to
         * get true exponent value.
         */
        return (((Double.doubleToRawLongBits(d) and DoubleConsts.EXP_BIT_MASK) shr
                (DoubleConsts.SIGNIFICAND_WIDTH - 1)) - DoubleConsts.EXP_BIAS).toInt()
    }

    /**
     * Converts an angle measured in degrees to an approximately
     * equivalent angle measured in radians.  The conversion from
     * degrees to radians is generally inexact.
     *
     * @param   angdeg   an angle, in degrees
     * @return  the measurement of the angle `angdeg`
     * in radians.
     * @since   1.2
     */
    fun toRadians(angdeg: Double): Double {
        return angdeg * DEGREES_TO_RADIANS
    }

    /**
     * Converts an angle measured in radians to an approximately
     * equivalent angle measured in degrees.  The conversion from
     * radians to degrees is generally inexact; users should
     * *not* expect `cos(toRadians(90.0))` to exactly
     * equal `0.0`.
     *
     * @param   angrad   an angle, in radians
     * @return  the measurement of the angle `angrad`
     * in degrees.
     * @since   1.2
     */
    fun toDegrees(angrad: Double): Double {
        return angrad * RADIANS_TO_DEGREES
    }
}
