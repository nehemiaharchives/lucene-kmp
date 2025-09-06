package org.gnit.lucenekmp.tests.util

import kotlin.random.Random

object RandomNumbers {
    fun randomIntBetween(random: Random, min: Int, max: Int): Int {
        require(max >= min) { "max must be >= min: $min, $max" }
        val range = max.toLong() - min.toLong()
        return if (range < Int.MAX_VALUE.toLong()) {
            min + random.nextInt((range + 1).toInt())
        } else {
            min + nextLong(random, range + 1).toInt()
        }
    }

    private fun nextLong(random: Random, n: Long): Long {
        require(n > 0) { "n <= 0: $n" }
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
