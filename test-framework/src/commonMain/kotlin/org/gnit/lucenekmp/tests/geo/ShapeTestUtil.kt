package org.gnit.lucenekmp.tests.geo

import kotlin.random.Random
import org.gnit.lucenekmp.geo.XYRectangle

object ShapeTestUtil {
    fun nextFloat(random: Random): Float {
        // generate a float value in the inclusive range [-Float.MAX_VALUE, Float.MAX_VALUE]
        val sign = if (random.nextBoolean()) -1 else 1
        return (random.nextDouble() * Float.MAX_VALUE) .toFloat() * sign
    }

    fun nextBox(random: Random): XYRectangle {
        var x0 = nextFloat(random)
        var x1 = nextFloat(random)
        while (x0 == x1) {
            x1 = nextFloat(random)
        }
        var y0 = nextFloat(random)
        var y1 = nextFloat(random)
        while (y0 == y1) {
            y1 = nextFloat(random)
        }
        if (x1 < x0) {
            val tmp = x0
            x0 = x1
            x1 = tmp
        }
        if (y1 < y0) {
            val tmp = y0
            y0 = y1
            y1 = tmp
        }
        return XYRectangle(x0, x1, y0, y1)
    }
}
