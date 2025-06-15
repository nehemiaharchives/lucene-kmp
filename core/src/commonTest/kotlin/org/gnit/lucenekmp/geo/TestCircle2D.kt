package org.gnit.lucenekmp.geo

import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.tests.geo.GeoTestUtil
import org.gnit.lucenekmp.tests.geo.ShapeTestUtil
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.jdkport.StrictMath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TestCircle2D : LuceneTestCase() {
    @Test
    fun testTriangleDisjoint() {
        val circle2D = if (random().nextBoolean()) {
            val circle = Circle(0.0, 0.0, 100.0)
            LatLonGeometry.create(circle)
        } else {
            val xyCircle = XYCircle(0f, 0f, 1f)
            XYGeometry.create(xyCircle)
        }
        val ax = 4.0
        val ay = 4.0
        val bx = 5.0
        val by = 5.0
        val cx = 5.0
        val cy = 4.0
        assertFalse(circle2D.intersectsTriangle(ax, ay, bx, by, cx, cy))
        assertFalse(circle2D.intersectsLine(ax, ay, bx, by))
        assertFalse(circle2D.containsTriangle(ax, ay, bx, by, cx, cy))
        assertFalse(circle2D.containsLine(ax, ay, bx, by))
        assertEquals(
            Component2D.WithinRelation.DISJOINT,
            circle2D.withinTriangle(ax, ay, true, bx, by, true, cx, cy, true)
        )
    }

    @Test
    fun testTriangleIntersects() {
        val circle2D = if (random().nextBoolean()) {
            val circle = Circle(0.0, 0.0, 1_000_000.0)
            LatLonGeometry.create(circle)
        } else {
            val xyCircle = XYCircle(0f, 0f, 10f)
            XYGeometry.create(xyCircle)
        }
        val ax = -20.0
        val ay = 1.0
        val bx = 20.0
        val by = 1.0
        val cx = 0.0
        val cy = 90.0
        assertTrue(circle2D.intersectsTriangle(ax, ay, bx, by, cx, cy))
        assertTrue(circle2D.intersectsLine(ax, ay, bx, by))
        assertFalse(circle2D.containsTriangle(ax, ay, bx, by, cx, cy))
        assertFalse(circle2D.containsLine(ax, ay, bx, by))
        assertEquals(
            Component2D.WithinRelation.NOTWITHIN,
            circle2D.withinTriangle(ax, ay, true, bx, by, true, cx, cy, true)
        )
    }

    @Test
    fun testTriangleDateLineIntersects() {
        val circle2D = LatLonGeometry.create(Circle(0.0, 179.0, 222400.0))
        val ax = -179.0
        val ay = 1.0
        val bx = -179.0
        val by = -1.0
        val cx = -178.0
        val cy = 0.0
        assertTrue(circle2D.intersectsTriangle(ax, ay, bx, by, cx, cy))
        assertTrue(circle2D.intersectsLine(ax, ay, bx, by))
        assertFalse(circle2D.containsTriangle(ax, ay, bx, by, cx, cy))
        assertFalse(circle2D.containsLine(ax, ay, bx, by))
        assertEquals(
            Component2D.WithinRelation.NOTWITHIN,
            circle2D.withinTriangle(ax, ay, true, bx, by, true, cx, cy, true)
        )
    }

    @Test
    fun testTriangleContains() {
        val circle2D = if (random().nextBoolean()) {
            val circle = Circle(0.0, 0.0, 1_000_000.0)
            LatLonGeometry.create(circle)
        } else {
            val xyCircle = XYCircle(0f, 0f, 1f)
            XYGeometry.create(xyCircle)
        }
        val ax = 0.25
        val ay = 0.25
        val bx = 0.5
        val by = 0.5
        val cx = 0.5
        val cy = 0.25
        assertTrue(circle2D.intersectsTriangle(ax, ay, bx, by, cx, cy))
        assertTrue(circle2D.intersectsLine(ax, ay, bx, by))
        assertTrue(circle2D.containsTriangle(ax, ay, bx, by, cx, cy))
        assertTrue(circle2D.containsLine(ax, ay, bx, by))
        assertEquals(
            Component2D.WithinRelation.NOTWITHIN,
            circle2D.withinTriangle(ax, ay, true, bx, by, true, cx, cy, true)
        )
    }

    @Test
    fun testTriangleWithin() {
        val circle2D = if (random().nextBoolean()) {
            val circle = Circle(0.0, 0.0, 1000.0)
            LatLonGeometry.create(circle)
        } else {
            val xyCircle = XYCircle(0f, 0f, 1f)
            XYGeometry.create(xyCircle)
        }
        val ax = -20.0
        val ay = -20.0
        val bx = 20.0
        val by = -20.0
        val cx = 0.0
        val cy = 20.0
        assertTrue(circle2D.intersectsTriangle(ax, ay, bx, by, cx, cy))
        assertFalse(circle2D.intersectsLine(bx, by, cx, cy))
        assertFalse(circle2D.containsTriangle(ax, ay, bx, by, cx, cy))
        assertFalse(circle2D.containsLine(bx, by, cx, cy))
        assertEquals(
            Component2D.WithinRelation.CANDIDATE,
            circle2D.withinTriangle(ax, ay, true, bx, by, true, cx, cy, true)
        )
    }

    @Test
    fun testRandomTriangles() {
        val circle2D = if (random().nextBoolean()) {
            val circle = GeoTestUtil.nextCircle()
            LatLonGeometry.create(circle)
        } else {
            val circle = ShapeTestUtil.nextCircle()
            XYGeometry.create(circle)
        }
        for (i in 0 until 100) {
            val ax = GeoTestUtil.nextLongitude()
            val ay = GeoTestUtil.nextLatitude()
            val bx = GeoTestUtil.nextLongitude()
            val by = GeoTestUtil.nextLatitude()
            val cx = GeoTestUtil.nextLongitude()
            val cy = GeoTestUtil.nextLatitude()

            val tMinX = StrictMath.min(StrictMath.min(ax, bx), cx)
            val tMaxX = StrictMath.max(StrictMath.max(ax, bx), cx)
            val tMinY = StrictMath.min(StrictMath.min(ay, by), cy)
            val tMaxY = StrictMath.max(StrictMath.max(ay, by), cy)

            val r = circle2D.relate(tMinX, tMaxX, tMinY, tMaxY)
            if (r == PointValues.Relation.CELL_OUTSIDE_QUERY) {
                assertFalse(circle2D.intersectsTriangle(ax, ay, bx, by, cx, cy))
                assertFalse(circle2D.intersectsLine(ax, ay, bx, by))
                assertFalse(circle2D.containsTriangle(ax, ay, bx, by, cx, cy))
                assertFalse(circle2D.containsLine(ax, ay, bx, by))
                assertEquals(
                    Component2D.WithinRelation.DISJOINT,
                    circle2D.withinTriangle(ax, ay, true, bx, by, true, cx, cy, true)
                )
            } else if (r == PointValues.Relation.CELL_INSIDE_QUERY) {
                assertTrue(circle2D.intersectsTriangle(ax, ay, bx, by, cx, cy))
                assertTrue(circle2D.intersectsLine(ax, ay, bx, by))
                assertTrue(circle2D.containsTriangle(ax, ay, bx, by, cx, cy))
                assertTrue(circle2D.containsLine(ax, ay, bx, by))
                assertNotEquals(
                    Component2D.WithinRelation.CANDIDATE,
                    circle2D.withinTriangle(ax, ay, true, bx, by, true, cx, cy, true)
                )
            }
        }
    }

    @Test
    fun testLineIntersects() {
        val circle2D = if (random().nextBoolean()) {
            val circle = Circle(0.0, 0.0, 35000.0)
            LatLonGeometry.create(circle)
        } else {
            val xyCircle = XYCircle(0f, 0f, 0.3f)
            XYGeometry.create(xyCircle)
        }
        val ax = -0.25
        val ay = 0.25
        val bx = 0.25
        val by = 0.25
        val cx = 0.2
        val cy = 0.25
        assertTrue(circle2D.intersectsLine(ax, ay, bx, by))
        assertFalse(circle2D.intersectsLine(bx, by, cx, cy))
        assertFalse(circle2D.intersectsLine(cx, cy, bx, by))
    }
}
