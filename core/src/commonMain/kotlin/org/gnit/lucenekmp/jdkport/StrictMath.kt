package org.gnit.lucenekmp.jdkport

object StrictMath {

    /**
     * Returns the trigonometric sine of an angle. Special cases:
     *  * If the argument is NaN or an infinity, then the
     * result is NaN.
     *  * If the argument is zero, then the result is a zero with the
     * same sign as the argument.
     *
     * @param   a   an angle, in radians.
     * @return  the sine of the argument.
     */
    fun sin(a: Double): Double {

        // implement following if more strict calculation is needed or unit tests fail
        //return java.lang.FdLibm.Sin.compute(a)

        return kotlin.math.sin(a)
    }


    /**
     * Returns the trigonometric cosine of an angle. Special cases:
     *  * If the argument is NaN or an infinity, then the
     * result is NaN.
     *  * If the argument is zero, then the result is `1.0`.
     *
     *
     * @param   a   an angle, in radians.
     * @return  the cosine of the argument.
     */
    fun cos(a: Double): Double {

        // implement following if more strict calculation is needed or unit tests fail
        //return java.lang.FdLibm.Cos.compute(a)

        return kotlin.math.cos(a)
    }

    /**
     * Returns the arc sine of a value; the returned angle is in the
     * range -pi/2 through pi/2.  Special cases:
     *  * If the argument is NaN or its absolute value is greater
     * than 1, then the result is NaN.
     *  * If the argument is zero, then the result is a zero with the
     * same sign as the argument.
     *
     * @param   a the value whose arc sine is to be returned.
     * @ return  the arc sine of the argument.
     */
    fun asin(a: Double): Double {

        // implement following if more strict calculation is needed or unit tests fail
        //return java.lang.FdLibm.Asin.compute(a)

        return kotlin.math.asin(a)
    }

    /**
     * Converts an angle measured in degrees to an approximately
     * equivalent angle measured in radians.  The conversion from
     * degrees to radians is generally inexact.
     *
     * @param   angdeg   an angle, in degrees
     * @return  the measurement of the angle `angdeg`
     * in radians.
     */
    fun toRadians(angdeg: Double): Double {
        return Math.toRadians(angdeg)
    }


    /**
     * Returns the correctly rounded positive square root of a
     * `double` value.
     * Special cases:
     *  * If the argument is NaN or less than zero, then the result
     * is NaN.
     *  * If the argument is positive infinity, then the result is positive
     * infinity.
     *  * If the argument is positive zero or negative zero, then the
     * result is the same as the argument.
     * Otherwise, the result is the `double` value closest to
     * the true mathematical square root of the argument value.
     *
     * @param   a   a value.
     * @return  the positive square root of `a`.
     */
    fun sqrt(a: Double): Double {

        // implement following if more strict calculation is needed or unit tests fail
        //return java.lang.FdLibm.Sqrt.compute(a)

        return kotlin.math.sqrt(a)
    }


    /**
     * Returns the smaller of two `double` values.  That
     * is, the result is the value closer to negative infinity. If the
     * arguments have the same value, the result is that same
     * value. If either value is NaN, then the result is NaN.  Unlike
     * the numerical comparison operators, this method considers
     * negative zero to be strictly smaller than positive zero. If one
     * argument is positive zero and the other is negative zero, the
     * result is negative zero.
     *
     * @param   a   an argument.
     * @param   b   another argument.
     * @return  the smaller of `a` and `b`.
     */
    fun min(a: Double, b: Double): Double {
        return kotlin.math.min(a, b)
    }

    fun min(a: Int, b: Int): Int {
        return kotlin.math.min(a, b)
    }

    fun min(a: Float, b: Float): Float {
        return kotlin.math.min(a, b)
    }

    /**
     * Returns the greater of two `double` values.  That
     * is, the result is the argument closer to positive infinity. If
     * the arguments have the same value, the result is that same
     * value. If either value is NaN, then the result is NaN.  Unlike
     * the numerical comparison operators, this method considers
     * negative zero to be strictly smaller than positive zero. If one
     * argument is positive zero and the other negative zero, the
     * result is positive zero.
     *
     * @param   a   an argument.
     * @param   b   another argument.
     * @return  the larger of `a` and `b`.
     */
    fun max(a: Double, b: Double): Double {
        return kotlin.math.max(a, b)
    }

    fun max(a: Int, b: Int): Int {
        return kotlin.math.max(a, b)
    }

    /**
     * Returns the natural logarithm (base *e*) of a `double`
     * value. Special cases:
     *  * If the argument is NaN or less than zero, then the result
     * is NaN.
     *  * If the argument is positive infinity, then the result is
     * positive infinity.
     *  * If the argument is positive zero or negative zero, then the
     * result is negative infinity.
     *  * If the argument is `1.0`, then the result is positive
     * zero.
     *
     *
     * @param   a   a value
     * @return  the value ln&nbsp;`a`, the natural logarithm of
     * `a`.
     */
    fun log(a: Double): Double {
        return FdLibm.Log.compute(a)
    }

    /**
     * Returns the absolute value of a `float` value.
     * If the argument is not negative, the argument is returned.
     * If the argument is negative, the negation of the argument is returned.
     * Special cases:
     *  * If the argument is positive zero or negative zero, the
     * result is positive zero.
     *  * If the argument is infinite, the result is positive infinity.
     *  * If the argument is NaN, the result is NaN.
     *
     * @apiNote As implied by the above, one valid implementation of
     * this method is given by the expression below which computes a
     * `float` with the same exponent and significand as the
     * argument but with a guaranteed zero sign bit indicating a
     * positive value: <br></br>
     * `Float.intBitsToFloat(0x7fffffff & Float.floatToRawIntBits(a))`
     *
     * @param   a   the argument whose absolute value is to be determined
     * @return  the absolute value of the argument.
     */
    fun abs(a: Float): Float {
        return kotlin.math.abs(a)
    }

    /**
     * Returns the absolute value of a `double` value.
     * If the argument is not negative, the argument is returned.
     * If the argument is negative, the negation of the argument is returned.
     * Special cases:
     *  * If the argument is positive zero or negative zero, the result
     * is positive zero.
     *  * If the argument is infinite, the result is positive infinity.
     *  * If the argument is NaN, the result is NaN.
     *
     * @apiNote As implied by the above, one valid implementation of
     * this method is given by the expression below which computes a
     * `double` with the same exponent and significand as the
     * argument but with a guaranteed zero sign bit indicating a
     * positive value: <br></br>
     * `Double.longBitsToDouble((Double.doubleToRawLongBits(a)<<1)>>>1)`
     *
     * @param   a   the argument whose absolute value is to be determined
     * @return  the absolute value of the argument.
     */
    fun abs(a: Double): Double {
        return kotlin.math.abs(a)
    }

}
