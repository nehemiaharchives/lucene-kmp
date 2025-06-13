package org.gnit.lucenekmp.tests.geo

import org.gnit.lucenekmp.geo.XYCircle
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.random.Random

/** Utility methods for generating random cartesian shapes. */
object ShapeTestUtil {
    /** Returns the next pseudorandom circle. */
    fun nextCircle(): XYCircle {
        val random = LuceneTestCase.random()
        val x = nextFloat(random)
        val y = nextFloat(random)
        var radius = 0f
        while (radius == 0f) {
            radius = random.nextFloat() * (Float.MAX_VALUE / 2)
        }
        return XYCircle(x, y, radius)
    }

    fun nextFloat(random: Random): Float {
        val value = random.nextFloat() * Float.MAX_VALUE
        return if (random.nextBoolean()) value else -value
    }
}
