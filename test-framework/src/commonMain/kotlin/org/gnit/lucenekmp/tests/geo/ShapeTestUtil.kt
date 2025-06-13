package org.gnit.lucenekmp.tests.geo

import org.gnit.lucenekmp.geo.XYPolygon
import org.gnit.lucenekmp.geo.XYRectangle
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.random.Random

/** Utilities for generating random XY shapes for tests. */
object ShapeTestUtil {
    private fun nextFloat(random: Random): Float {
        val sign = if (random.nextBoolean()) 1f else -1f
        return random.nextFloat() * Float.MAX_VALUE * sign
    }

    private fun nextBox(random: Random): XYRectangle {
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

    private fun trianglePolygon(box: XYRectangle): XYPolygon {
        val polyX = floatArrayOf(box.minX, box.maxX, box.maxX, box.minX)
        val polyY = floatArrayOf(box.minY, box.minY, box.maxY, box.minY)
        return XYPolygon(polyX, polyY)
    }

    private fun boxPolygon(box: XYRectangle): XYPolygon {
        val polyX = floatArrayOf(box.minX, box.maxX, box.maxX, box.minX, box.minX)
        val polyY = floatArrayOf(box.minY, box.minY, box.maxY, box.maxY, box.minY)
        return XYPolygon(polyX, polyY)
    }

    /** Returns a simple random polygon for testing. */
    fun nextPolygon(): XYPolygon {
        val random = LuceneTestCase.random()
        val box = nextBox(random)
        return if (random.nextBoolean()) boxPolygon(box) else trianglePolygon(box)
    }
}
