package org.gnit.lucenekmp.tests.util

import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.Ported
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.doubleToLongBits
import org.gnit.lucenekmp.jdkport.floatToIntBits
import org.gnit.lucenekmp.jdkport.intBitsToFloat
import org.gnit.lucenekmp.jdkport.isInfinite
import org.gnit.lucenekmp.jdkport.isNaN
import org.gnit.lucenekmp.jdkport.longBitsToDouble
import kotlin.math.nextUp
import kotlin.random.Random

/**
 * Utility classes for selecting numbers at random, but not necessarily
 * in an uniform way. The implementation will try to pick "evil" numbers
 * more often than uniform selection would. This includes exact range
 * boundaries, numbers very close to range boundaries, numbers very close
 * (or equal) to zero, etc.
 *
 * The exact method of selection is implementation-dependent and
 * may change (if we find even more evil ways).
 */
@Ported(from = "com.carrotsearch.randomizedtesting.generators.BiasedNumbers")
object BiasedNumbers {
    private const val EVIL_RANGE_LEFT = 1
    private const val EVIL_RANGE_RIGHT = 1
    private const val EVIL_VERY_CLOSE_RANGE_ENDS = 20
    private const val EVIL_ZERO_OR_NEAR = 5
    private const val EVIL_SIMPLE_PROPORTION = 10
    private const val EVIL_RANDOM_REPRESENTATION_BITS = 10

    /**
     * A random double between `min` (inclusive) and `max`
     * (inclusive). If you wish to have an exclusive range,
     * use [Math.nextAfter] to adjust the range.
     *
     * The code was inspired by GeoTestUtil from Apache Lucene.
     *
     * @param min Left range boundary, inclusive. May be [Double.NEGATIVE_INFINITY], but not NaN.
     * @param max Right range boundary, inclusive. May be [Double.POSITIVE_INFINITY], but not NaN.
     */
    fun randomDoubleBetween(r: Random, min: Double, max: Double): Double {
        var min = min
        var max = max
        assert(max >= min) { "max must be >= min: $min, $max" }
        assert(!Double.isNaN(min) && !Double.isNaN(max))

        val hasZero = min <= 0 && max >= 0

        var pick: Int = r.nextInt(
            EVIL_RANGE_LEFT +
                    EVIL_RANGE_RIGHT +
                    EVIL_VERY_CLOSE_RANGE_ENDS +
                    (if (hasZero) EVIL_ZERO_OR_NEAR else 0) +
                    EVIL_SIMPLE_PROPORTION +
                    EVIL_RANDOM_REPRESENTATION_BITS
        )

        // Exact range ends
        pick -= EVIL_RANGE_LEFT
        if (pick < 0 || min == max) {
            return min
        }

        pick -= EVIL_RANGE_RIGHT
        if (pick < 0) {
            return max
        }

        // If we're dealing with infinities, adjust them to discrete values.
        assert(min != max)
        if (min.isInfinite()) {
            min = min.nextUp()
        }
        if (max.isInfinite()) {
            max = Math.nextAfter(max, Double.NEGATIVE_INFINITY)
        }

        // Numbers "very" close to range ends. "very" means a few floating point
        // representation steps (ulps) away.
        pick -= EVIL_VERY_CLOSE_RANGE_ENDS
        if (pick < 0) {
            if (r.nextBoolean()) {
                return fuzzUp(r, min, max)
            } else {
                return fuzzDown(r, max, min)
            }
        }

        // Zero or near-zero values, if within the range.
        if (hasZero) {
            pick -= EVIL_ZERO_OR_NEAR
            if (pick < 0) {
                val v: Int = r.nextInt(4)
                if (v == 0) {
                    return 0.0
                } else if (v == 1) {
                    return -0.0
                } else if (v == 2) {
                    return fuzzDown(r, 0.0, min)
                } else if (v == 3) {
                    return fuzzUp(r, 0.0, max)
                }
            }
        }

        // Simple proportional selection.
        pick -= EVIL_SIMPLE_PROPORTION
        if (pick < 0) {
            return min + (max - min) * r.nextDouble()
        }

        // Random representation space selection. This will be heavily biased
        // and overselect from the set of tiny values, if they're allowed.
        pick -= EVIL_RANDOM_REPRESENTATION_BITS
        if (pick < 0) {
            val from = toSortable(min)
            val to = toSortable(max)
            return fromSortable(RandomNumbers.randomLongBetween(r, from, to))
        }

        throw RuntimeException("Unreachable.")
    }

    /**
     * Fuzzify the input value by decreasing it by a few ulps, but never past min.
     */
    fun fuzzDown(r: Random, v: Double, min: Double): Double {
        var v = v
        assert(v >= min)
        var steps: Int = RandomNumbers.randomIntBetween(r, 1, 10)
        while (steps > 0 && v > min) {
            v = Math.nextAfter(v, Double.NEGATIVE_INFINITY)
            steps--
        }
        return v
    }

    /**
     * Fuzzify the input value by increasing it by a few ulps, but never past max.
     */
    fun fuzzUp(r: Random, v: Double, max: Double): Double {
        var v = v
        assert(v <= max)
        var steps: Int = RandomNumbers.randomIntBetween(r, 1, 10)
        while (steps > 0 && v < max) {
            v = v.nextUp()
            steps--
        }
        return v
    }

    private fun fromSortable(sortable: Long): Double {
        return Double.longBitsToDouble(flip(sortable))
    }

    private fun toSortable(value: Double): Long {
        return flip(Double.doubleToLongBits(value))
    }

    private fun flip(bits: Long): Long {
        return bits xor ((bits shr 63) and 0x7fffffffffffffffL)
    }

    /**
     * A random float between `min` (inclusive) and `max`
     * (inclusive). If you wish to have an exclusive range,
     * use [Math.nextAfter] to adjust the range.
     *
     * The code was inspired by GeoTestUtil from Apache Lucene.
     *
     * @param min Left range boundary, inclusive. May be [Float.NEGATIVE_INFINITY], but not NaN.
     * @param max Right range boundary, inclusive. May be [Float.POSITIVE_INFINITY], but not NaN.
     */
    fun randomFloatBetween(r: Random, min: Float, max: Float): Float {
        var min = min
        var max = max
        assert(max >= min) { "max must be >= min: $min, $max" }
        assert(!Float.isNaN(min) && !Float.isNaN(max))

        val hasZero = min <= 0 && max >= 0

        var pick: Int = r.nextInt(
            EVIL_RANGE_LEFT +
                    EVIL_RANGE_RIGHT +
                    EVIL_VERY_CLOSE_RANGE_ENDS +
                    (if (hasZero) EVIL_ZERO_OR_NEAR else 0) +
                    EVIL_SIMPLE_PROPORTION +
                    EVIL_RANDOM_REPRESENTATION_BITS
        )

        // Exact range ends
        pick -= EVIL_RANGE_LEFT
        if (pick < 0 || min == max) {
            return min
        }

        pick -= EVIL_RANGE_RIGHT
        if (pick < 0) {
            return max
        }

        // If we're dealing with infinities, adjust them to discrete values.
        assert(min != max)
        if (Float.isInfinite(min)) {
            min = Math.nextUp(min)
        }
        if (Float.isInfinite(max)) {
            max = Math.nextAfter(max, Double.NEGATIVE_INFINITY)
        }

        // Numbers "very" close to range ends. "very" means a few floating point
        // representation steps (ulps) away.
        pick -= EVIL_VERY_CLOSE_RANGE_ENDS
        if (pick < 0) {
            if (r.nextBoolean()) {
                return fuzzUp(r, min, max)
            } else {
                return fuzzDown(r, max, min)
            }
        }

        // Zero or near-zero values, if within the range.
        if (hasZero) {
            pick -= EVIL_ZERO_OR_NEAR
            if (pick < 0) {
                val v: Int = r.nextInt(4)
                if (v == 0) {
                    return 0f
                } else if (v == 1) {
                    return -0.0f
                } else if (v == 2) {
                    return fuzzDown(r, 0f, min)
                } else if (v == 3) {
                    return fuzzUp(r, 0f, max)
                }
            }
        }

        // Simple proportional selection.
        pick -= EVIL_SIMPLE_PROPORTION
        if (pick < 0) {
            return (min + ((max.toDouble() - min) * r.nextDouble())).toFloat()
        }

        // Random representation space selection. This will be heavily biased
        // and overselect from the set of tiny values, if they're allowed.
        pick -= EVIL_RANDOM_REPRESENTATION_BITS
        if (pick < 0) {
            val from = toSortable(min)
            val to = toSortable(max)
            return fromSortable(RandomNumbers.randomIntBetween(r, from, to))
        }

        throw RuntimeException("Unreachable.")
    }

    /**
     * Fuzzify the input value by decreasing it by a few ulps, but never past min.
     */
    fun fuzzDown(r: Random, v: Float, min: Float): Float {
        var v = v
        assert(v >= min)
        var steps: Int = RandomNumbers.randomIntBetween(r, 1, 10)
        while (steps > 0 && v > min) {
            v = Math.nextAfter(v, Double.NEGATIVE_INFINITY)
            steps--
        }
        return v
    }

    /**
     * Fuzzify the input value by increasing it by a few ulps, but never past max.
     */
    fun fuzzUp(r: Random, v: Float, max: Float): Float {
        var v = v
        assert(v <= max)
        var steps: Int = RandomNumbers.randomIntBetween(r, 1, 10)
        while (steps > 0 && v < max) {
            v = Math.nextUp(v)
            steps--
        }
        return v
    }

    private fun fromSortable(sortable: Int): Float {
        return Float.intBitsToFloat(flip(sortable))
    }

    private fun toSortable(value: Float): Int {
        return BiasedNumbers.flip(Float.floatToIntBits(value))
    }

    private fun flip(floatBits: Int): Int {
        return floatBits xor ((floatBits shr 31) and 0x7fffffff)
    }
}
