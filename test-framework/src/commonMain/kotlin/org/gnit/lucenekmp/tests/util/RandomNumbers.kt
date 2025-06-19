package org.gnit.lucenekmp.tests.util

import kotlin.random.Random

object RandomNumbers {
    fun randomIntBetween(random: Random, start: Int, end: Int): Int {
        require(end >= start)
        return if (start == end) start else random.nextInt(start, end + 1)
    }
}
