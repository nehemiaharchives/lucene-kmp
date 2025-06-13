package org.gnit.lucenekmp.tests.geo

import org.gnit.lucenekmp.geo.XYRectangle
import kotlin.random.Random

object ShapeTestUtil {
    fun nextFloat(random: Random): Float {
        return ((random.nextDouble() * 2.0) - 1.0).toFloat() * Float.MAX_VALUE
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
            val x = x0
            x0 = x1
            x1 = x
        }
        if (y1 < y0) {
            val y = y0
            y0 = y1
            y1 = y
        }
        return XYRectangle(x0, x1, y0, y1)
    }
}
