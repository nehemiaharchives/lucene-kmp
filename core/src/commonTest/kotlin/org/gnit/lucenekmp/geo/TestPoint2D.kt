package org.gnit.lucenekmp.geo

import org.gnit.lucenekmp.index.PointValues.Relation
import org.gnit.lucenekmp.tests.geo.GeoTestUtil
import org.gnit.lucenekmp.jdkport.StrictMath
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestPoint2D : LuceneTestCase() {
    @Test
    fun testTriangleDisjoint() {
        val point2D: Component2D = Point2D.create(Point(0.0, 0.0))
        val ax = 4.0
        val ay = 4.0
        val bx = 5.0
        val by = 5.0
        val cx = 5.0
        val cy = 4.0
        assertFalse(point2D.intersectsTriangle(ax, ay, bx, by, cx, cy))
        assertFalse(point2D.intersectsLine(ax, ay, bx, by))
        assertFalse(point2D.containsTriangle(ax, ay, bx, by, cx, cy))
        assertFalse(point2D.containsLine(ax, ay, bx, by))
        assertEquals(
            Component2D.WithinRelation.DISJOINT,
            point2D.withinTriangle(
                ax,
                ay,
                random().nextBoolean(),
                bx,
                by,
                random().nextBoolean(),
                cx,
                cy,
                random().nextBoolean()
            )
        )
    }

    @Test
    fun testTriangleIntersects() {
        val point2D: Component2D = Point2D.create(Point(0.0, 0.0))
        val ax = 0.0
        val ay = 0.0
        val bx = 1.0
        val by = 0.0
        val cx = 0.0
        val cy = 1.0
        assertTrue(point2D.intersectsTriangle(ax, ay, bx, by, cx, cy))
        assertTrue(point2D.intersectsLine(ax, ay, bx, by))
        assertFalse(point2D.containsTriangle(ax, ay, bx, by, cx, cy))
        assertFalse(point2D.containsLine(ax, ay, bx, by))
        assertEquals(
            Component2D.WithinRelation.CANDIDATE,
            point2D.withinTriangle(
                ax,
                ay,
                random().nextBoolean(),
                bx,
                by,
                random().nextBoolean(),
                cx,
                cy,
                random().nextBoolean()
            )
        )
    }

    @Test
    fun testTriangleContains() {
        val point2D: Component2D = Point2D.create(Point(0.0, 0.0))
        val ax = 0.0
        val ay = 0.0
        assertTrue(point2D.contains(ax, ay))
        assertEquals(
            Component2D.WithinRelation.CANDIDATE,
            point2D.withinTriangle(
                ax,
                ay,
                random().nextBoolean(),
                ax,
                ay,
                random().nextBoolean(),
                ax,
                ay,
                random().nextBoolean()
            )
        )
    }

    @Test
    fun testRandomTriangles() {
        val point2D: Component2D = Point2D.create(Point(GeoTestUtil.nextLatitude(), GeoTestUtil.nextLongitude()))
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

            val r = point2D.relate(tMinX, tMaxX, tMinY, tMaxY)
            if (r == Relation.CELL_OUTSIDE_QUERY) {
                assertFalse(point2D.intersectsTriangle(ax, ay, bx, by, cx, cy))
                assertFalse(point2D.intersectsLine(ax, ay, bx, by))
                assertFalse(point2D.containsTriangle(ax, ay, bx, by, cx, cy))
                assertFalse(point2D.containsLine(ax, ay, bx, by))
                assertEquals(
                    Component2D.WithinRelation.DISJOINT,
                    point2D.withinTriangle(
                        ax,
                        ay,
                        random().nextBoolean(),
                        bx,
                        by,
                        random().nextBoolean(),
                        cx,
                        cy,
                        random().nextBoolean()
                    )
                )
            }
        }
    }
}

