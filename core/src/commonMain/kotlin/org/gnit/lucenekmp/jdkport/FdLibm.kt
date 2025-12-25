package org.gnit.lucenekmp.jdkport

/**
 * Port of the "Freely Distributable Math Library", version 5.3, from
 * C to Java. Then port from Java to Kotlin Multiplatform for lucene-kmp.
 *
 * <p>The C version of fdlibm relied on the idiom of pointer aliasing
 * a 64-bit double floating-point value as a two-element array of
 * 32-bit integers and reading and writing the two halves of the
 * double independently. This coding pattern was problematic to C
 * optimizers and not directly expressible in Java. Therefore, rather
 * than a memory level overlay, if portions of a double need to be
 * operated on as integer values, the standard library methods for
 * bitwise floating-point to integer conversion,
 * Double.longBitsToDouble and Double.doubleToRawLongBits, are directly
 * or indirectly used.
 *
 * <p>The C version of fdlibm also took some pains to signal the
 * correct IEEE 754 exceptional conditions divide by zero, invalid,
 * overflow and underflow. For example, overflow would be signaled by
 * {@code huge * huge} where {@code huge} was a large constant that
 * would overflow when squared. Since IEEE floating-point exceptional
 * handling is not supported natively in the JVM, such coding patterns
 * have been omitted from this port. For example, rather than {@code
 * return huge * huge}, this port will use {@code return INFINITY}.
 *
 * <p>Various comparison and arithmetic operations in fdlibm could be
 * done either based on the integer view of a value or directly on the
 * floating-point representation. Which idiom is faster may depend on
 * platform specific factors. However, for code clarity if no other
 * reason, this port will favor expressing the semantics of those
 * operations in terms of floating-point operations when convenient to
 * do so.
 */
@Ported(from = "java.lang.FdLibm")
object FdLibm {
    // Constants used by multiple algorithms
    private const val INFINITY: Double = Double.POSITIVE_INFINITY
    private const val TWO24: Double = 1.6777216E7 // 1.67772160000000000000e+07
    private const val TWO54: Double = 1.8014398509481984E16 // 1.80143985094819840000e+16
    private const val HUGE: Double = 1.0e+300

    /*
     * Constants for bit-wise manipulation of IEEE 754 double
     * values. These constants are for the high-order 32-bits of a
     * 64-bit double value: 1 sign bit as the most significant bit,
     * followed by 11 exponent bits, and then the remaining bits as
     * the significand.
     */
    private const val SIGN_BIT: Int = -0x80000000
    private const val EXP_BITS: Int = 0x7ff00000
    private const val EXP_SIGNIF_BITS: Int = 0x7fffffff

    // Use a value that is exactly the Double rounding of 1/3, avoiding the literal-precision warning.
    private const val ONE_THIRD: Double = 1.0 / 3.0

    /**
     * Return the low-order 32 bits of the double argument as an int.
     */
    private fun __LO(x: Double): Int {
        val transducer = Double.doubleToRawLongBits(x)
        return transducer.toInt()
    }

    /**
     * Return a double with its low-order bits of the second argument
     * and the high-order bits of the first argument..
     */
    private fun __LO(x: Double, low: Int): Double {
        val transX = Double.doubleToRawLongBits(x)
        return Double.longBitsToDouble(
            (transX and -0x100000000L) or
                    (low.toLong() and 0x00000000FFFFFFFFL)
        )
    }

    /**
     * Return the high-order 32 bits of the double argument as an int.
     */
    private fun __HI(x: Double): Int {
        val transducer = Double.doubleToRawLongBits(x)
        return (transducer shr 32).toInt()
    }

    /**
     * Return a double with its high-order bits of the second argument
     * and the low-order bits of the first argument..
     */
    private fun __HI(x: Double, high: Int): Double {
        val transX = Double.doubleToRawLongBits(x)
        return Double.longBitsToDouble(
            (transX and 0x00000000FFFFFFFFL) or
                    (((high.toLong())) shl 32)
        )
    }

    /**
     * Return a double with its high-order bits of the first argument
     * and the low-order bits of the second argument..
     */
    private fun __HI_LO(high: Int, low: Int): Double {
        return Double.longBitsToDouble(
            (high.toLong() shl 32) or
                    (low.toLong() and 0xffffffffL)
        )
    }

    /**
     * Return the (natural) logarithm of x
     *
     * Method :
     * 1. Argument Reduction: find k and f such that
     * x = 2^k * (1+f),
     * where  sqrt(2)/2 < 1+f < sqrt(2) .
     *
     * 2. Approximation of log(1+f).
     * Let s = f/(2+f) ; based on log(1+f) = log(1+s) - log(1-s)
     * = 2s + 2/3 s**3 + 2/5 s**5 + .....,
     * = 2s + s*R
     * We use a special Reme algorithm on [0,0.1716] to generate
     * a polynomial of degree 14 to approximate R The maximum error
     * of this polynomial approximation is bounded by 2**-58.45. In
     * other words,
     * 2      4      6      8      10      12      14
     * R(z) ~ Lg1*s +Lg2*s +Lg3*s +Lg4*s +Lg5*s  +Lg6*s  +Lg7*s
     * (the values of Lg1 to Lg7 are listed in the program)
     * and
     * |      2          14          |     -58.45
     * | Lg1*s +...+Lg7*s    -  R(z) | <= 2
     * |                             |
     * Note that 2s = f - s*f = f - hfsq + s*hfsq, where hfsq = f*f/2.
     * In order to guarantee error in log below 1ulp, we compute log
     * by
     * log(1+f) = f - s*(f - R)        (if f is not too large)
     * log(1+f) = f - (hfsq - s*(hfsq+R)).     (better accuracy)
     *
     * 3. Finally,  log(x) = k*ln2 + log(1+f).
     * = k*ln2_hi+(f-(hfsq-(s*(hfsq+R)+k*ln2_lo)))
     * Here ln2 is split into two floating point number:
     * ln2_hi + ln2_lo,
     * where n*ln2_hi is always exact for |n| < 2000.
     *
     * Special cases:
     * log(x) is NaN with signal if x < 0 (including -INF) ;
     * log(+INF) is +INF; log(0) is -INF with signal;
     * log(NaN) is that NaN with no signal.
     *
     * Accuracy:
     * according to an error analysis, the error is always less than
     * 1 ulp (unit in the last place).
     *
     * Constants:
     * The hexadecimal values are the intended ones for the following
     * constants. The decimal values may be used, provided that the
     * compiler will convert from decimal to binary accurately enough
     * to produce the hexadecimal values shown.
     */
    object Log {

        private const val ln2_hi = 0.6931471489369869 // 6.93147180369123816490e-01
        private const val ln2_lo = 1.9082017544954374E-10 // 1.90821492927058770002e-10

        private const val Lg1 = 0.6666666666666735 // 6.666666666666735130e-01
        private const val Lg2 = 0.39999999999281854 // 3.999999999940941908e-01
        private const val Lg3 = 0.2857142874366239 // 2.857142874366239149e-01
        private const val Lg4 = 0.2222219821552045 // 2.222219843214978396e-01
        private const val Lg5 = 0.18183572161618056 // 1.818357216161805012e-01
        private const val Lg6 = 0.15313828342988955 // 1.531383769920937332e-01
        private const val Lg7 = 0.14114807785563244 // 1.479819860511658591e-01

        fun compute(x: Double): Double {
            var x = x
            val hfsq: Double
            val f: Double
            val s: Double
            val z: Double
            val R: Double
            val w: Double
            val t1: Double
            val t2: Double
            val dk: Double
            var k: Int
            var hx: Int
            var i: Int
            val j: Int
            /*unsigned*/
            val lx: Int

            hx = __HI(x) // high word of x
            lx = __LO(x) // low  word of x

            k = 0
            if (hx < 0x00100000) {                  // x < 2**-1022
                if (((hx and EXP_SIGNIF_BITS) or lx) == 0) { // log(+-0) = -inf
                    return /*-TWO54 / 0.0*/ Double.NEGATIVE_INFINITY
                }
                if (hx < 0) {                        // log(-#) = NaN
                    return /*(x - x) / 0.0*/ Double.NaN
                }
                k -= 54
                x *= TWO54 // subnormal number, scale up x
                hx = __HI(x) // high word of x
            }
            if (hx >= EXP_BITS) {
                return x + x
            }
            k += (hx shr 20) - 1023
            hx = hx and 0x000fffff
            i = (hx + 0x95f64) and 0x100000
            x = __HI(x, hx or (i xor 0x3ff00000)) // normalize x or x/2
            k += (i shr 20)
            f = x - 1.0
            if ((0x000fffff and (2 + hx)) < 3) { // |f| < 2**-20
                if (f == 0.0) {
                    if (k == 0) {
                        return 0.0
                    } else {
                        dk = k.toDouble()
                        return dk * ln2_hi + dk * ln2_lo
                    }
                }
                R = f * f * (0.5 - /*0.33333333333333333*/ ONE_THIRD * f)
                if (k == 0) {
                    return f - R
                } else {
                    dk = k.toDouble()
                    return dk * ln2_hi - ((R - dk * ln2_lo) - f)
                }
            }
            s = f / (2.0 + f)
            dk = k.toDouble()
            z = s * s
            i = hx - 0x6147a
            w = z * z
            j = 0x6b851 - hx
            t1 = w * (Lg2 + w * (Lg4 + w * Lg6))
            t2 = z * (Lg1 + w * (Lg3 + w * (Lg5 + w * Lg7)))
            i = i or j
            R = t2 + t1
            if (i > 0) {
                hfsq = 0.5 * f * f
                if (k == 0) {
                    return f - (hfsq - s * (hfsq + R))
                } else {
                    return dk * ln2_hi - ((hfsq - (s * (hfsq + R) + dk * ln2_lo)) - f)
                }
            } else {
                if (k == 0) {
                    return f - s * (f - R)
                } else {
                    return dk * ln2_hi - ((s * (f - R) - dk * ln2_lo) - f)
                }
            }
        }
    }
}
