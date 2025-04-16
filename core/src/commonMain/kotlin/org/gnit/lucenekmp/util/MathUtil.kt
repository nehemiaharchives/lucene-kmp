package org.gnit.lucenekmp.util

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.math.pow

/** Math static utility methods.  */
object MathUtil {
    /**
     * Returns `x <= 0 ? 0 : Math.floor(Math.log(x) / Math.log(base))`
     *
     * @param base must be `> 1`
     */
    fun log(x: Long, base: Int): Int {
        var x = x
        if (base == 2) {
            // This specialized method is 30x faster.
            return if (x <= 0) 0 else 63 - x.countLeadingZeroBits() /* java.lang.Long.numberOfLeadingZeros(x)*/
        } else require(base > 1) { "base must be > 1" }
        var ret = 0
        while (x >= base) {
            x /= base.toLong()
            ret++
        }
        return ret
    }

    fun log(x: Int, base: Int): Int {
        var x = x
        if (base == 2) {
            // This specialized method is 30x faster.
            return if (x <= 0) 0 else 31 - x.countLeadingZeroBits() /* java.lang.Integer.numberOfLeadingZeros(x)*/
        } else require(base > 1) { "base must be > 1" }
        var ret = 0
        while (x >= base) {
            x /= base
            ret++
        }
        return ret
    }

    /** Calculates logarithm in a given base with doubles.  */
    fun log(base: Double, x: Double): Double {
        return ln(x) / ln(base)
    }

    /**
     * Return the greatest common divisor of `a` and `b`, consistently with
     * [BigInteger.gcd].
     *
     *
     * **NOTE**: A greatest common divisor must be positive, but `2^64` cannot be
     * expressed as a long although it is the GCD of [Long.MIN_VALUE] and `0` and the
     * GCD of [Long.MIN_VALUE] and [Long.MIN_VALUE]. So in these 2 cases, and only them,
     * this method will return [Long.MIN_VALUE].
     */
    // see
    // http://en.wikipedia.org/wiki/Binary_GCD_algorithm#Iterative_version_in_C.2B.2B_using_ctz_.28count_trailing_zeros.29
    fun gcd(a: Long, b: Long): Long {
        var a = a
        var b = b
        a = abs(a.toDouble()).toLong()
        b = abs(b.toDouble()).toLong()
        if (a == 0L) {
            return b
        } else if (b == 0L) {
            return a
        }
        val commonTrailingZeros: Int = (a or b).countTrailingZeroBits() /* java.lang.Long.numberOfTrailingZeros(a or b)*/
        a = a ushr a.countTrailingZeroBits() /*java.lang.Long.numberOfTrailingZeros(a)*/
        while (true) {
            b = b ushr b.countTrailingZeroBits() /*java.lang.Long.numberOfTrailingZeros(b)*/
            if (a == b) {
                break
            } else if (a > b || a == Long.MIN_VALUE) { // MIN_VALUE is treated as 2^64
                val tmp = a
                a = b
                b = tmp
            }
            if (a == 1L) {
                break
            }
            b -= a
        }
        return a shl commonTrailingZeros
    }

    /**
     * Calculates inverse hyperbolic sine of a `double` value.
     *
     *
     * Special cases:
     *
     *
     *  * If the argument is NaN, then the result is NaN.
     *  * If the argument is zero, then the result is a zero with the same sign as the argument.
     *  * If the argument is infinite, then the result is infinity with the same sign as the
     * argument.
     *
     */
    fun asinh(a: Double): Double {
        var a = a
        val sign: Double
        // check the sign bit of the raw representation to handle -0
        if (/*java.lang.Double.doubleToRawLongBits(a)*/ a.toRawBits() < 0) {
            a = abs(a)
            sign = -1.0
        } else {
            sign = 1.0
        }

        return sign * ln(sqrt(a * a + 1.0) + a)
    }

    /**
     * Calculates inverse hyperbolic cosine of a `double` value.
     *
     *
     * Special cases:
     *
     *
     *  * If the argument is NaN, then the result is NaN.
     *  * If the argument is +1, then the result is a zero.
     *  * If the argument is positive infinity, then the result is positive infinity.
     *  * If the argument is less than 1, then the result is NaN.
     *
     */
    fun acosh(a: Double): Double {
        return ln(sqrt(a * a - 1.0) + a)
    }

    /**
     * Calculates inverse hyperbolic tangent of a `double` value.
     *
     *
     * Special cases:
     *
     *
     *  * If the argument is NaN, then the result is NaN.
     *  * If the argument is zero, then the result is a zero with the same sign as the argument.
     *  * If the argument is +1, then the result is positive infinity.
     *  * If the argument is -1, then the result is negative infinity.
     *  * If the argument's absolute value is greater than 1, then the result is NaN.
     *
     */
    fun atanh(a: Double): Double {
        var a = a
        val mult: Double
        // check the sign bit of the raw representation to handle -0
        if (/*java.lang.Double.doubleToRawLongBits(a)*/ a.toRawBits() < 0) {
            a = abs(a)
            mult = -0.5
        } else {
            mult = 0.5
        }
        return mult * ln((1.0 + a) / (1.0 - a))
    }

    /**
     * Return a relative error bound for a sum of `numValues` positive doubles, computed using
     * recursive summation, ie. sum = x1 + ... + xn. NOTE: This only works if all values are POSITIVE
     * so that Σ |xi| == |Σ xi|. This uses formula 3.5 from Higham, Nicholas J. (1993), "The accuracy
     * of floating point summation", SIAM Journal on Scientific Computing.
     */
    fun sumRelativeErrorBound(numValues: Int): Double {
        if (numValues <= 1) {
            return 0.0
        }
        // u = unit roundoff in the paper, also called machine precision or machine epsilon
        val u: Double = 1.0 * 2.0.pow(-52) /*java.lang.Math.scalb(1.0, -52)*/
        return (numValues - 1) * u
    }

    /**
     * Return the maximum possible sum across `numValues` non-negative doubles, assuming one sum
     * yielded `sum`.
     *
     * @see .sumRelativeErrorBound
     */
    fun sumUpperBound(sum: Double, numValues: Int): Double {
        if (numValues <= 2) {
            // When there are only two clauses, the sum is always the same regardless
            // of the order.
            return sum
        }

        // The error of sums depends on the order in which values are summed up. In
        // order to avoid this issue, we compute an upper bound of the value that
        // the sum may take. If the max relative error is b, then it means that two
        // sums are always within 2*b of each other.
        // For conjunctions, we could skip this error factor since the order in which
        // scores are summed up is predictable, but in practice, this wouldn't help
        // much since the delta that is introduced by this error factor is usually
        // cancelled by the float cast.
        val b = sumRelativeErrorBound(numValues)
        return (1.0 + 2 * b) * sum
    }
}
