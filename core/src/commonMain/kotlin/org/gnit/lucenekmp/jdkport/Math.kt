package org.gnit.lucenekmp.jdkport

object Math {
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
        val intBits = Float.floatToRawIntBits(a)
        val biasedExp = ((intBits and FloatConsts.EXP_BIT_MASK)
                shr (FloatConsts.SIGNIFICAND_WIDTH - 1))
        val shift: Int = (FloatConsts.SIGNIFICAND_WIDTH - 2
                + FloatConsts.EXP_BIAS) - biasedExp
        if ((shift and -32) == 0) { // shift >= 0 && shift < 32
            // a is a finite number such that pow(2,-32) <= ulp(a) < 1
            var r = ((intBits and FloatConsts.SIGNIF_BIT_MASK)
                    or (FloatConsts.SIGNIF_BIT_MASK + 1))
            if (intBits < 0) {
                r = -r
            }
            // In the comments below each Java expression evaluates to the value
            // the corresponding mathematical expression:
            // (r) evaluates to a / ulp(a)
            // (r >> shift) evaluates to floor(a * 2)
            // ((r >> shift) + 1) evaluates to floor((a + 1/2) * 2)
            // (((r >> shift) + 1) >> 1) evaluates to floor(a + 1/2)
            return ((r shr shift) + 1) shr 1
        } else {
            // a is either
            // - a finite number with abs(a) < exp(2,FloatConsts.SIGNIFICAND_WIDTH-32) < 1/2
            // - a finite number with ulp(a) >= 1 and hence a is a mathematical integer
            // - an infinity or NaN
            return a.toInt()
        }
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
}