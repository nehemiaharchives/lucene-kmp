package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.StrictMath
import org.gnit.lucenekmp.jdkport.doubleToRawLongBits
import org.gnit.lucenekmp.jdkport.longBitsToDouble
import kotlin.math.min
import kotlin.math.sqrt


/* some code derived from jodk: http://code.google.com/p/jodk/ (apache 2.0)
 * asin() derived from fdlibm: http://www.netlib.org/fdlibm/e_asin.c (public domain):
 * =============================================================================
 * Copyright (C) 1993 by Sun Microsystems, Inc. All rights reserved.
 *
 * Developed at SunSoft, a Sun Microsystems, Inc. business.
 * Permission to use, copy, modify, and distribute this
 * software is freely granted, provided that this notice
 * is preserved.
 * =============================================================================
 */
/** Math functions that trade off accuracy for speed.  */
object SloppyMath {
    /**
     * Returns the Haversine distance in meters between two points specified in decimal degrees
     * (latitude/longitude). This works correctly even if the dateline is between the two points.
     *
     *
     * Error is at most 4E-1 (40cm) from the actual haversine distance, but is typically much
     * smaller for reasonable distances: around 1E-5 (0.01mm) for distances less than 1000km.
     *
     * @param lat1 Latitude of the first point.
     * @param lon1 Longitude of the first point.
     * @param lat2 Latitude of the second point.
     * @param lon2 Longitude of the second point.
     * @return distance in meters.
     */
    fun haversinMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        return haversinMeters(haversinSortKey(lat1, lon1, lat2, lon2))
    }

    /**
     * Returns the Haversine distance in meters between two points given the previous result from
     * [.haversinSortKey]
     *
     * @return distance in meters.
     */
    fun haversinMeters(sortKey: Double): Double {
        return TO_METERS * 2 * asin(min(1.0, sqrt(sortKey * 0.5)))
    }

    /**
     * Returns a sort key for distance. This is less expensive to compute than [ ][.haversinMeters], but it always compares the same. This can be
     * converted into an actual distance with [.haversinMeters], which effectively does
     * the second half of the computation.
     */
    fun haversinSortKey(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val x1: Double = Math.toRadians(lat1)
        val x2: Double = Math.toRadians(lat2)
        val h1 = 1 - cos(x1 - x2)
        val h2 = 1 - cos(Math.toRadians(lon1 - lon2))
        val h = h1 + cos(x1) * cos(x2) * h2
        // clobber crazy precision so subsequent rounding does not create ties.
        return Double.longBitsToDouble(Double.doubleToRawLongBits(h) and -0x8L)
    }

    /**
     * Returns the trigonometric cosine of an angle.
     *
     *
     * Error is around 1E-15.
     *
     *
     * Special cases:
     *
     *
     *  * If the argument is `NaN` or an infinity, then the result is `NaN`.
     *
     *
     * @param a an angle, in radians.
     * @return the cosine of the argument.
     * @see Math.cos
     */
    fun cos(a: Double): Double {
        var a = a
        if (a < 0.0) {
            a = -a
        }
        if (a > SIN_COS_MAX_VALUE_FOR_INT_MODULO) {
            return kotlin.math.cos(a)
        }
        // index: possibly outside tables range.
        var index = (a * SIN_COS_INDEXER + 0.5).toInt()
        val delta = (a - index * SIN_COS_DELTA_HI) - index * SIN_COS_DELTA_LO
        // Making sure index is within tables range.
        // Last value of each table is the same than first, so we ignore it (tabs size minus one) for
        // modulo.
        index = index and (SIN_COS_TABS_SIZE - 2) // index % (SIN_COS_TABS_SIZE-1)
        val indexCos = cosTab[index]
        val indexSin = sinTab[index]
        return (indexCos
                + delta
                * (-indexSin
                + delta
                * (-indexCos * ONE_DIV_F2
                + delta * (indexSin * ONE_DIV_F3 + delta * indexCos * ONE_DIV_F4))))
    }

    /**
     * Returns the arc sine of a value.
     *
     * <p>The returned angle is in the range <i>-pi</i>/2 through <i>pi</i>/2. Error is around 1E-7.
     *
     * <p>Special cases:
     *
     * <ul>
     *   <li>If the argument is {@code NaN} or its absolute value is greater than 1, then the result
     *       is {@code NaN}.
     * </ul>
     *
     * @param a the value whose arc sine is to be returned.
     * @return arc sine of the argument
     * @see Math#asin(double)
     */
    // because asin(-x) = -asin(x), asin(x) only needs to be computed on [0,1].
    // ---> we only have to compute asin(x) on [0,1].
    // For values not close to +-1, we use look-up tables;
    // for values near +-1, we use code derived from fdlibm.
    fun asin(a: Double): Double {
        var a = a
        val negateResult: Boolean
        if (a < 0.0) {
            a = -a
            negateResult = true
        } else {
            negateResult = false
        }
        if (a <= ASIN_MAX_VALUE_FOR_TABS) {
            val index = (a * ASIN_INDEXER + 0.5).toInt()
            val delta = a - index * ASIN_DELTA
            val result =
                (asinTab[index]
                        + delta
                        * (asinDer1DivF1Tab[index]
                        + delta
                        * (asinDer2DivF2Tab[index]
                        + delta
                        * (asinDer3DivF3Tab[index] + delta * asinDer4DivF4Tab[index]))))
            return if (negateResult) -result else result
        } else { // value > ASIN_MAX_VALUE_FOR_TABS, or value is NaN
            // This part is derived from fdlibm.
            if (a < 1.0) {
                val t = (1.0 - a) * 0.5
                val p =
                    (t
                            * (ASIN_PS0
                            + t
                            * (ASIN_PS1
                            + t * (ASIN_PS2 + t * (ASIN_PS3 + t * (ASIN_PS4 + t * ASIN_PS5))))))
                val q = 1.0 + t * (ASIN_QS1 + t * (ASIN_QS2 + t * (ASIN_QS3 + t * ASIN_QS4)))
                val s = sqrt(t)
                val z = s + s * (p / q)
                val result = ASIN_PIO2_HI - ((z + z) - ASIN_PIO2_LO)
                return if (negateResult) -result else result
            } else { // value >= 1.0, or value is NaN
                if (a == 1.0) {
                    return if (negateResult) -Math.PI / 2 else Math.PI / 2
                } else {
                    return Double.Companion.NaN
                }
            }
        }
    }

    // Earth's mean radius, in meters and kilometers; see
    // http://earth-info.nga.mil/GandG/publications/tr8350.2/wgs84fin.pdf
    private const val TO_METERS = 6371008.7714 // equatorial radius

    // cos/asin
    private const val ONE_DIV_F2 = 1 / 2.0
    private const val ONE_DIV_F3 = 1 / 6.0
    private const val ONE_DIV_F4 = 1 / 24.0

    // 1.57079632673412561417e+00 first 33 bits of pi/2
    private val PIO2_HI: Double = Double.longBitsToDouble(0x3FF921FB54400000L)

    // 6.07710050650619224932e-11 pi/2 - PIO2_HI
    private val PIO2_LO: Double = Double.longBitsToDouble(0x3DD0B4611A626331L)
    private val TWOPI_HI = 4 * PIO2_HI
    private val TWOPI_LO = 4 * PIO2_LO
    private const val SIN_COS_TABS_SIZE = (1 shl 11) + 1
    private val SIN_COS_DELTA_HI = TWOPI_HI / (SIN_COS_TABS_SIZE - 1)
    private val SIN_COS_DELTA_LO = TWOPI_LO / (SIN_COS_TABS_SIZE - 1)
    private val SIN_COS_INDEXER = 1 / (SIN_COS_DELTA_HI + SIN_COS_DELTA_LO)
    private val sinTab = DoubleArray(SIN_COS_TABS_SIZE)
    private val cosTab = DoubleArray(SIN_COS_TABS_SIZE)

    // Max abs value for fast modulo, above which we use regular angle normalization.
    // This value must be < (Integer.MAX_VALUE / SIN_COS_INDEXER), to stay in range of int type.
    // The higher it is, the higher the error, but also the faster it is for lower values.
    // If you set it to ((Integer.MAX_VALUE / SIN_COS_INDEXER) * 0.99), worse accuracy on double range
    // is about 1e-10.
    val SIN_COS_MAX_VALUE_FOR_INT_MODULO: Double = ((Int.Companion.MAX_VALUE shr 9) / SIN_COS_INDEXER) * 0.99

    // Supposed to be >= sin(77.2deg), as fdlibm code is supposed to work with values > 0.975,
    // but seems to work well enough as long as value >= sin(25deg).
    private val ASIN_MAX_VALUE_FOR_TABS: Double = StrictMath.sin(Math.toRadians(73.0))

    private const val ASIN_TABS_SIZE = (1 shl 13) + 1
    private val ASIN_DELTA = ASIN_MAX_VALUE_FOR_TABS / (ASIN_TABS_SIZE - 1)
    private val ASIN_INDEXER = 1 / ASIN_DELTA
    private val asinTab = DoubleArray(ASIN_TABS_SIZE)
    private val asinDer1DivF1Tab = DoubleArray(ASIN_TABS_SIZE)
    private val asinDer2DivF2Tab = DoubleArray(ASIN_TABS_SIZE)
    private val asinDer3DivF3Tab = DoubleArray(ASIN_TABS_SIZE)
    private val asinDer4DivF4Tab = DoubleArray(ASIN_TABS_SIZE)

    // 1.57079632679489655800e+00
    private val ASIN_PIO2_HI: Double = Double.longBitsToDouble(0x3FF921FB54442D18L)

    // 6.12323399573676603587e-17
    private val ASIN_PIO2_LO: Double = Double.longBitsToDouble(0x3C91A62633145C07L)

    //  1.66666666666666657415e-01
    private val ASIN_PS0: Double = Double.longBitsToDouble(0x3fc5555555555555L)

    // -3.25565818622400915405e-01
    private val ASIN_PS1: Double = Double.longBitsToDouble(-0x402b29edfc149083L)

    //  2.01212532134862925881e-01
    private val ASIN_PS2: Double = Double.longBitsToDouble(0x3fc9c1550e884455L)

    // -4.00555345006794114027e-02
    private val ASIN_PS3: Double = Double.longBitsToDouble(-0x405b7dd74a9770c5L)

    //  7.91534994289814532176e-04
    private val ASIN_PS4: Double = Double.longBitsToDouble(0x3f49efe07501b288L)

    //  3.47933107596021167570e-05
    private val ASIN_PS5: Double = Double.longBitsToDouble(0x3f023de10dfdf709L)

    // -2.40339491173441421878e+00
    private val ASIN_QS1: Double = Double.longBitsToDouble(-0x3ffcc5d8e375d2b5L)

    //  2.02094576023350569471e+00
    private val ASIN_QS2: Double = Double.longBitsToDouble(0x40002ae59c598ac8L)

    // -6.88283971605453293030e-01
    private val ASIN_QS3: Double = Double.longBitsToDouble(-0x4019f993e472fea7L)

    //  7.70381505559019352791e-02
    private val ASIN_QS4: Double = Double.longBitsToDouble(0x3fb3b8c5b12e9282L)

    /* Initializes look-up tables. */
    init {
        // sin and cos
        val SIN_COS_PI_INDEX = (SIN_COS_TABS_SIZE - 1) / 2
        val SIN_COS_PI_MUL_2_INDEX = 2 * SIN_COS_PI_INDEX
        val SIN_COS_PI_MUL_0_5_INDEX = SIN_COS_PI_INDEX / 2
        val SIN_COS_PI_MUL_1_5_INDEX = 3 * SIN_COS_PI_INDEX / 2
        for (i in 0..<SIN_COS_TABS_SIZE) {
            // angle: in [0,2*PI].
            val angle = i * SIN_COS_DELTA_HI + i * SIN_COS_DELTA_LO
            var sinAngle: Double = StrictMath.sin(angle)
            var cosAngle: Double = StrictMath.cos(angle)
            // For indexes corresponding to null cosine or sine, we make sure the value is zero
            // and not an epsilon. This allows for a much better accuracy for results close to zero.
            if (i == SIN_COS_PI_INDEX) {
                sinAngle = 0.0
            } else if (i == SIN_COS_PI_MUL_2_INDEX) {
                sinAngle = 0.0
            } else if (i == SIN_COS_PI_MUL_0_5_INDEX) {
                cosAngle = 0.0
            } else if (i == SIN_COS_PI_MUL_1_5_INDEX) {
                cosAngle = 0.0
            }
            sinTab[i] = sinAngle
            cosTab[i] = cosAngle
        }

        // asin
        for (i in 0..<ASIN_TABS_SIZE) {
            // x: in [0,ASIN_MAX_VALUE_FOR_TABS].
            val x = i * ASIN_DELTA
            asinTab[i] = StrictMath.asin(x)
            val oneMinusXSqInv = 1.0 / (1 - x * x)
            val oneMinusXSqInv0_5: Double = StrictMath.sqrt(oneMinusXSqInv)
            val oneMinusXSqInv1_5 = oneMinusXSqInv0_5 * oneMinusXSqInv
            val oneMinusXSqInv2_5 = oneMinusXSqInv1_5 * oneMinusXSqInv
            val oneMinusXSqInv3_5 = oneMinusXSqInv2_5 * oneMinusXSqInv
            asinDer1DivF1Tab[i] = oneMinusXSqInv0_5
            asinDer2DivF2Tab[i] = (x * oneMinusXSqInv1_5) * ONE_DIV_F2
            asinDer3DivF3Tab[i] = ((1 + 2 * x * x) * oneMinusXSqInv2_5) * ONE_DIV_F3
            asinDer4DivF4Tab[i] = ((5 + 2 * x * (2 + x * (5 - 2 * x))) * oneMinusXSqInv3_5) * ONE_DIV_F4
        }
    }
}
