package org.gnit.lucenekmp.tests.geo

import org.gnit.lucenekmp.geo.XYCircle
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.random.Random

object ShapeTestUtil {
    private fun nextFloat(random: Random): Float {
        val sign = if (random.nextBoolean()) 1f else -1f
        return random.nextFloat() * Float.MAX_VALUE * sign
    }

    fun nextCircle(): XYCircle {
        val random = LuceneTestCase.random()
        val x = nextFloat(random)
        val y = nextFloat(random)
        var radius = 0f
        while (radius == 0f) {
            radius = random.nextFloat() * Float.MAX_VALUE / 2f
        }
        return XYCircle(x, y, radius)
    }
}
