package org.gnit.lucenekmp.geo

import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.tests.geo.ShapeTestUtil
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TestRectangle2D : LuceneTestCase() {

    @Test
    fun testTriangleDisjoint() {
        val rectangle = XYRectangle(0f, 1f, 0f, 1f)
        val rectangle2D: Component2D = Rectangle2D.create(rectangle)
        val ax = 4f
        val ay = 4f
        val bx = 5f
        val by = 5f
        val cx = 5f
        val cy = 4f
        assertFalse(rectangle2D.intersectsTriangle(ax.toDouble(), ay.toDouble(), bx.toDouble(), by.toDouble(), cx.toDouble(), cy.toDouble()))
        assertFalse(rectangle2D.intersectsLine(ax.toDouble(), ay.toDouble(), bx.toDouble(), by.toDouble()))
        assertFalse(rectangle2D.containsTriangle(ax.toDouble(), ay.toDouble(), bx.toDouble(), by.toDouble(), cx.toDouble(), cy.toDouble()))
        assertFalse(rectangle2D.containsLine(ax.toDouble(), ay.toDouble(), bx.toDouble(), by.toDouble()))
        assertEquals(
            Component2D.WithinRelation.DISJOINT,
            rectangle2D.withinTriangle(
                ax.toDouble(),
                ay.toDouble(),
                random().nextBoolean(),
                bx.toDouble(),
                by.toDouble(),
                random().nextBoolean(),
                cx.toDouble(),
                cy.toDouble(),
                random().nextBoolean()
            )
        )
    }

    @Test
    fun testTriangleIntersects() {
        val rectangle = XYRectangle(0f, 1f, 0f, 1f)
        val rectangle2D: Component2D = Rectangle2D.create(rectangle)
        val ax = 0.5f
        val ay = 0.5f
        val bx = 2f
        val by = 2f
        val cx = 0.5f
        val cy = 2f
        assertTrue(rectangle2D.intersectsTriangle(ax.toDouble(), ay.toDouble(), bx.toDouble(), by.toDouble(), cx.toDouble(), cy.toDouble()))
        assertTrue(rectangle2D.intersectsLine(ax.toDouble(), ay.toDouble(), bx.toDouble(), by.toDouble()))
        assertFalse(rectangle2D.containsTriangle(ax.toDouble(), ay.toDouble(), bx.toDouble(), by.toDouble(), cx.toDouble(), cy.toDouble()))
        assertFalse(rectangle2D.containsLine(ax.toDouble(), ay.toDouble(), bx.toDouble(), by.toDouble()))
        assertEquals(
            Component2D.WithinRelation.NOTWITHIN,
            rectangle2D.withinTriangle(ax.toDouble(), ay.toDouble(), true, bx.toDouble(), by.toDouble(), true, cx.toDouble(), cy.toDouble(), true)
        )
    }

    @Test
    fun testTriangleContains() {
        val rectangle = XYRectangle(0f, 1f, 0f, 1f)
        val rectangle2D: Component2D = Rectangle2D.create(rectangle)
        val ax = 0.25f
        val ay = 0.25f
        val bx = 0.5f
        val by = 0.5f
        val cx = 0.5f
        val cy = 0.25f
        assertTrue(rectangle2D.intersectsTriangle(ax.toDouble(), ay.toDouble(), bx.toDouble(), by.toDouble(), cx.toDouble(), cy.toDouble()))
        assertTrue(rectangle2D.intersectsLine(ax.toDouble(), ay.toDouble(), bx.toDouble(), by.toDouble()))
        assertTrue(rectangle2D.containsTriangle(ax.toDouble(), ay.toDouble(), bx.toDouble(), by.toDouble(), cx.toDouble(), cy.toDouble()))
        assertTrue(rectangle2D.containsLine(ax.toDouble(), ay.toDouble(), bx.toDouble(), by.toDouble()))
        assertEquals(
            Component2D.WithinRelation.NOTWITHIN,
            rectangle2D.withinTriangle(ax.toDouble(), ay.toDouble(), true, bx.toDouble(), by.toDouble(), true, cx.toDouble(), cy.toDouble(), true)
        )
    }

    @Test
    fun testRandomTriangles() {
        val r: Random = random()
        val rectangle = ShapeTestUtil.nextBox(r)
        val rectangle2D: Component2D = Rectangle2D.create(rectangle)
        for (i in 0 until 100) {
            val ax = ShapeTestUtil.nextFloat(r)
            val ay = ShapeTestUtil.nextFloat(r)
            val bx = ShapeTestUtil.nextFloat(r)
            val by = ShapeTestUtil.nextFloat(r)
            val cx = ShapeTestUtil.nextFloat(r)
            val cy = ShapeTestUtil.nextFloat(r)

            val tMinX = kotlin.math.min(kotlin.math.min(ax, bx), cx)
            val tMaxX = kotlin.math.max(kotlin.math.max(ax, bx), cx)
            val tMinY = kotlin.math.min(kotlin.math.min(ay, by), cy)
            val tMaxY = kotlin.math.max(kotlin.math.max(ay, by), cy)

            val relation = rectangle2D.relate(tMinX.toDouble(), tMaxX.toDouble(), tMinY.toDouble(), tMaxY.toDouble())
            if (relation == PointValues.Relation.CELL_OUTSIDE_QUERY) {
                assertFalse(rectangle2D.intersectsTriangle(ax.toDouble(), ay.toDouble(), bx.toDouble(), by.toDouble(), cx.toDouble(), cy.toDouble()))
                assertFalse(rectangle2D.intersectsLine(ax.toDouble(), ay.toDouble(), bx.toDouble(), by.toDouble()))
                assertFalse(rectangle2D.containsTriangle(ax.toDouble(), ay.toDouble(), bx.toDouble(), by.toDouble(), cx.toDouble(), cy.toDouble()))
                assertFalse(rectangle2D.containsLine(ax.toDouble(), ay.toDouble(), bx.toDouble(), by.toDouble()))
                assertEquals(
                    Component2D.WithinRelation.DISJOINT,
                    rectangle2D.withinTriangle(ax.toDouble(), ay.toDouble(), true, bx.toDouble(), by.toDouble(), true, cx.toDouble(), cy.toDouble(), true)
                )
            } else if (relation == PointValues.Relation.CELL_INSIDE_QUERY) {
                assertTrue(rectangle2D.intersectsTriangle(ax.toDouble(), ay.toDouble(), bx.toDouble(), by.toDouble(), cx.toDouble(), cy.toDouble()))
                assertTrue(rectangle2D.intersectsLine(ax.toDouble(), ay.toDouble(), bx.toDouble(), by.toDouble()))
                assertTrue(rectangle2D.containsTriangle(ax.toDouble(), ay.toDouble(), bx.toDouble(), by.toDouble(), cx.toDouble(), cy.toDouble()))
                assertTrue(rectangle2D.containsLine(ax.toDouble(), ay.toDouble(), bx.toDouble(), by.toDouble()))
            }
        }
    }

    @Test
    fun testEqualsAndHashCode() {
        val r: Random = random()
        val xyRectangle = ShapeTestUtil.nextBox(r)
        val rectangle2D: Component2D = Rectangle2D.create(xyRectangle)

        val copy = Rectangle2D.create(xyRectangle)
        assertEquals(rectangle2D, copy)
        assertEquals(rectangle2D.hashCode(), copy.hashCode())

        val otherXYRectangle = ShapeTestUtil.nextBox(r)
        val otherRectangle2D: Component2D = Rectangle2D.create(otherXYRectangle)

        if (rectangle2D.minX.compareTo(otherRectangle2D.minX) != 0 ||
            rectangle2D.maxX.compareTo(otherRectangle2D.maxX) != 0 ||
            rectangle2D.minY.compareTo(otherRectangle2D.minY) != 0 ||
            rectangle2D.maxY.compareTo(otherRectangle2D.maxY) != 0
        ) {
            assertNotEquals(rectangle2D, otherRectangle2D)
            assertNotEquals(rectangle2D.hashCode(), otherRectangle2D.hashCode())
        } else {
            assertEquals(rectangle2D, otherRectangle2D)
            assertEquals(rectangle2D.hashCode(), otherRectangle2D.hashCode())
        }
    }
}
