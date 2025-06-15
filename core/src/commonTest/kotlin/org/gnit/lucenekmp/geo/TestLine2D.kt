package org.gnit.lucenekmp.geo

import org.gnit.lucenekmp.tests.geo.GeoTestUtil
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.index.PointValues.Relation
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TestLine2D : LuceneTestCase() {

    @Test
    fun testTriangleDisjoint() {
        val line = Line(doubleArrayOf(0.0, 1.0, 2.0, 3.0), doubleArrayOf(0.0, 0.0, 2.0, 2.0))
        val line2D = Line2D.create(line)
        val ax = 4.0
        val ay = 4.0
        val bx = 5.0
        val by = 5.0
        val cx = 5.0
        val cy = 4.0
        assertFalse(line2D.intersectsTriangle(ax, ay, bx, by, cx, cy))
        assertFalse(line2D.intersectsLine(ax, ay, bx, by))
        assertFalse(line2D.containsTriangle(ax, ay, bx, by, cx, cy))
        assertFalse(line2D.containsLine(ax, ay, bx, by))
        assertEquals(
            Component2D.WithinRelation.DISJOINT,
            line2D.withinTriangle(ax, ay, true, bx, by, true, cx, cy, true)
        )
    }

    @Test
    fun testTriangleIntersects() {
        val line = Line(doubleArrayOf(0.5, 0.0, 1.0, 2.0, 3.0), doubleArrayOf(0.5, 0.0, 0.0, 2.0, 2.0))
        val line2D = Line2D.create(line)
        val ax = 0.0
        val ay = 0.0
        val bx = 1.0
        val by = 0.0
        val cx = 0.0
        val cy = 1.0
        assertTrue(line2D.intersectsTriangle(ax, ay, bx, by, cx, cy))
        assertTrue(line2D.intersectsLine(ax, ay, bx, by))
        assertFalse(line2D.containsTriangle(ax, ay, bx, by, cx, cy))
        assertFalse(line2D.containsLine(ax, ay, bx, by))
        assertEquals(
            Component2D.WithinRelation.NOTWITHIN,
            line2D.withinTriangle(ax, ay, true, bx, by, true, cx, cy, true)
        )
    }

    @Test
    fun testTriangleContains() {
        val line = Line(doubleArrayOf(0.5, 0.0, 1.0, 2.0, 3.0), doubleArrayOf(0.5, 0.0, 0.0, 2.0, 2.0))
        val line2D = Line2D.create(line)
        val ax = -10.0
        val ay = -10.0
        val bx = 4.0
        val by = -10.0
        val cx = 4.0
        val cy = 30.0
        assertTrue(line2D.intersectsTriangle(ax, ay, bx, by, cx, cy))
        assertFalse(line2D.intersectsLine(bx, by, cx, cy))
        assertFalse(line2D.containsTriangle(ax, ay, bx, by, cx, cy))
        assertFalse(line2D.containsLine(bx, by, cx, cy))
        assertEquals(
            Component2D.WithinRelation.CANDIDATE,
            line2D.withinTriangle(ax, ay, true, bx, by, true, cx, cy, true)
        )
    }

    @Test
    fun testRandomTriangles() {
        val line = GeoTestUtil.nextLine()
        val line2D = Line2D.create(line)

        for (i in 0 until 100) {
            val ax = GeoTestUtil.nextLongitude()
            val ay = GeoTestUtil.nextLatitude()
            val bx = GeoTestUtil.nextLongitude()
            val by = GeoTestUtil.nextLatitude()
            val cx = GeoTestUtil.nextLongitude()
            val cy = GeoTestUtil.nextLatitude()

            val tMinX = min(min(ax, bx), cx)
            val tMaxX = max(max(ax, bx), cx)
            val tMinY = min(min(ay, by), cy)
            val tMaxY = max(max(ay, by), cy)

            val r = line2D.relate(tMinX, tMaxX, tMinY, tMaxY)
            if (r == Relation.CELL_OUTSIDE_QUERY) {
                assertFalse(line2D.intersectsTriangle(ax, ay, bx, by, cx, cy))
                assertFalse(line2D.intersectsLine(ax, ay, bx, by))
                assertFalse(line2D.containsTriangle(ax, ay, bx, by, cx, cy))
                assertFalse(line2D.containsLine(ax, ay, bx, by))
                assertEquals(
                    Component2D.WithinRelation.DISJOINT,
                    line2D.withinTriangle(ax, ay, true, bx, by, true, cx, cy, true)
                )
            } else if (line2D.containsTriangle(ax, ay, bx, by, cx, cy)) {
                assertNotEquals(
                    Component2D.WithinRelation.CANDIDATE,
                    line2D.withinTriangle(ax, ay, true, bx, by, true, cx, cy, true)
                )
            }
        }
    }
}
