package org.gnit.lucenekmp.tests.util

import org.gnit.lucenekmp.jdkport.assert
import kotlin.random.Random

object RandomNumbers {
    fun randomIntBetween(random: Random, min: Int, max: Int): Int {
        assert(max >= min) { "max must be >= min: $min, $max" }
        val range = max.toLong() - min.toLong()
        return if (range < Int.MAX_VALUE.toLong()) {
            min + random.nextInt((range + 1).toInt())
        } else {
            min + nextLong(random, range + 1).toInt()
        }
    }

    /**
     * A random long between `min` (inclusive) and `max` (inclusive).
     */
    fun randomLongBetween(r: Random, min: Long, max: Long): Long {
        assert(max >= min) { "max must be >= min: $min, $max" }
        var range = max - min
        if (range < 0) {
            range -= Long.MAX_VALUE
            if (range == Long.MIN_VALUE) {
                // Full spectrum.
                return r.nextLong()
            } else {
                val first: Long = r.nextLong() and Long.MAX_VALUE
                val second: Long =
                    if (range == Long.MAX_VALUE) (r.nextLong() and Long.MAX_VALUE) else nextLong(
                        r,
                        range + 1
                    )
                return min + first + second
            }
        } else {
            val second: Long =
                if (range == Long.MAX_VALUE) (r.nextLong() and Long.MAX_VALUE) else nextLong(
                    r,
                    range + 1
                )
            return min + second
        }
    }

    private fun nextLong(random: Random, n: Long): Long {
        assert(n > 0) { "n <= 0: $n" }
        var value = random.nextLong()
        val range = n - 1
        if ((n and range) == 0L) {
            value = value and range
        } else {
            var u = value ushr 1
            var v: Long
            do {
                v = u % n
                if (u + range - v >= 0) {
                    value = v
                    break
                }
                u = random.nextLong() ushr 1
            } while (true)
        }
        return value
    }
}
