package org.gnit.lucenekmp.jdkport

import jdk.internal.vm.annotation.IntrinsicCandidate

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
}
