package org.gnit.lucenekmp.geo

import org.gnit.lucenekmp.tests.geo.GeoTestUtil
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestTessellator : LuceneTestCase() {

    @Test
    fun testLinesIntersect() {
        val rect = GeoTestUtil.nextBoxNotCrossingDateline()
        assertTrue(
            Tessellator.linesIntersect(
                rect.minLon,
                rect.minLat,
                rect.maxLon,
                rect.maxLat,
                rect.maxLon,
                rect.minLat,
                rect.minLon,
                rect.maxLat
            )
        )
        assertFalse(
            Tessellator.linesIntersect(
                rect.minLon,
                rect.maxLat,
                rect.maxLon,
                rect.maxLat,
                rect.minLon - 1.0,
                rect.minLat,
                rect.minLon - 1.0,
                rect.maxLat
            )
        )
    }

    @Test
    fun testSimpleTessellation() {
        var poly = GeoTestUtil.createRegularPolygon(0.0, 0.0, 100000.0, 100000)
        val inner = Polygon(
            doubleArrayOf(-1.0, -1.0, 0.5, 1.0, 1.0, 0.5, -1.0),
            doubleArrayOf(1.0, -1.0, -0.5, -1.0, 1.0, 0.5, 1.0)
        )
        val inner2 = Polygon(
            doubleArrayOf(-1.0, -1.0, 0.5, 1.0, 1.0, 0.5, -1.0),
            doubleArrayOf(-2.0, -4.0, -3.5, -4.0, -2.0, -2.5, -2.0)
        )
        poly = Polygon(poly.getPolyLats(), poly.getPolyLons(), inner, inner2)
        assertTrue(Tessellator.tessellate(poly, random().nextBoolean()).size > 0)
    }
}
