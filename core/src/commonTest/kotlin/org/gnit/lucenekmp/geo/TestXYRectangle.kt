package org.gnit.lucenekmp.geo

import org.gnit.lucenekmp.tests.geo.ShapeTestUtil
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.jdkport.compare
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class TestXYRectangle : LuceneTestCase() {

    @Test
    fun tesInvalidMinMaxX() {
        val expected = expectThrows(
            IllegalArgumentException::class,
            LuceneTestCase.ThrowingRunnable { XYRectangle(5f, 4f, 3f, 4f) }
        )
        assertTrue(expected!!.message!!.contains("5.0 > 4.0"))
    }

    @Test
    fun tesInvalidMinMaxY() {
        val expected = expectThrows(
            IllegalArgumentException::class,
            LuceneTestCase.ThrowingRunnable { XYRectangle(4f, 5f, 5f, 4f) }
        )
        assertTrue(expected!!.message!!.contains("5.0 > 4.0"))
    }

    @Test
    fun testNaN() {
        var expected = expectThrows(
            IllegalArgumentException::class,
            LuceneTestCase.ThrowingRunnable { XYRectangle(Float.NaN, 4f, 3f, 4f) }
        )
        assertTrue(expected!!.message!!.contains("invalid value NaN"))

        expected = expectThrows(
            IllegalArgumentException::class,
            LuceneTestCase.ThrowingRunnable { XYRectangle(3f, Float.NaN, 3f, 4f) }
        )
        assertTrue(expected!!.message!!.contains("invalid value NaN"))

        expected = expectThrows(
            IllegalArgumentException::class,
            LuceneTestCase.ThrowingRunnable { XYRectangle(3f, 4f, Float.NaN, 4f) }
        )
        assertTrue(expected!!.message!!.contains("invalid value NaN"))

        expected = expectThrows(
            IllegalArgumentException::class,
            LuceneTestCase.ThrowingRunnable { XYRectangle(3f, 4f, 3f, Float.NaN) }
        )
        assertTrue(expected!!.message!!.contains("invalid value NaN"))
    }

    @Test
    fun testPositiveInf() {
        var expected = expectThrows(
            IllegalArgumentException::class,
            LuceneTestCase.ThrowingRunnable { XYRectangle(3f, Float.POSITIVE_INFINITY, 3f, 4f) }
        )
        assertTrue(expected!!.message!!.contains("invalid value Inf"))

        expected = expectThrows(
            IllegalArgumentException::class,
            LuceneTestCase.ThrowingRunnable { XYRectangle(3f, 4f, 3f, Float.POSITIVE_INFINITY) }
        )
        assertTrue(expected!!.message!!.contains("invalid value Inf"))
    }

    @Test
    fun testNegativeInf() {
        var expected = expectThrows(
            IllegalArgumentException::class,
            LuceneTestCase.ThrowingRunnable { XYRectangle(Float.NEGATIVE_INFINITY, 4f, 3f, 4f) }
        )
        assertTrue(expected!!.message!!.contains("invalid value -Inf"))

        expected = expectThrows(
            IllegalArgumentException::class,
            LuceneTestCase.ThrowingRunnable { XYRectangle(3f, 4f, Float.NEGATIVE_INFINITY, 4f) }
        )
        assertTrue(expected!!.message!!.contains("invalid value -Inf"))
    }

    @Test
    fun testEqualsAndHashCode() {
        val rectangle = ShapeTestUtil.nextBox(random())
        val copy = XYRectangle(rectangle.minX, rectangle.maxX, rectangle.minY, rectangle.maxY)
        assertEquals(rectangle, copy)
        assertEquals(rectangle.hashCode(), copy.hashCode())
        val otherRectangle = ShapeTestUtil.nextBox(random())
        if (Float.compare(rectangle.minX, otherRectangle.minX) != 0 ||
            Float.compare(rectangle.maxX, otherRectangle.maxX) != 0 ||
            Float.compare(rectangle.minY, otherRectangle.minY) != 0 ||
            Float.compare(rectangle.maxY, otherRectangle.maxY) != 0) {
            assertNotEquals(rectangle, otherRectangle)
            assertNotEquals(rectangle.hashCode(), otherRectangle.hashCode())
        } else {
            assertEquals(rectangle, otherRectangle)
            assertEquals(rectangle.hashCode(), otherRectangle.hashCode())
        }
    }

    @Test
    fun testRandomCircleToBBox() {
        val iters = atLeast(100)
        for (iter in 0 until iters) {
            val centerX = ShapeTestUtil.nextFloat(random())
            val centerY = ShapeTestUtil.nextFloat(random())
            val radius: Float = if (random().nextBoolean()) {
                random().nextFloat() * TestUtil.nextInt(random(), 1, 100000)
            } else {
                kotlin.math.abs(ShapeTestUtil.nextFloat(random()))
            }
            val bbox = XYRectangle.fromPointDistance(centerX, centerY, radius)
            val component2D = XYGeometry.create(bbox)
            val numPointsToTry = 1000
            for (i in 0 until numPointsToTry) {
                val x = if (random().nextBoolean()) {
                    kotlin.math.min(Float.MAX_VALUE.toDouble(), centerX + radius + random().nextDouble())
                } else {
                    kotlin.math.max(-Float.MAX_VALUE.toDouble(), centerX + radius - random().nextDouble())
                }
                val y = if (random().nextBoolean()) {
                    kotlin.math.min(Float.MAX_VALUE.toDouble(), centerY + radius + random().nextDouble())
                } else {
                    kotlin.math.max(-Float.MAX_VALUE.toDouble(), centerY + radius - random().nextDouble())
                }

                val cartesianSays = component2D.contains(x, y)
                val bboxSays = x >= bbox.minX && x <= bbox.maxX && y >= bbox.minY && y <= bbox.maxY

                if (cartesianSays && !bboxSays) {
                    println("  centerX=" + centerX + " centerY=" + centerY + " radius=" + radius)
                    println("  bbox: x=" + bbox.minX + " to " + bbox.maxX + " y=" + bbox.minY + " to " + bbox.maxY)
                    println("  point: x=" + x + " y=" + y)
                    fail("point was within the distance according to cartesian distance, but the bbox doesn't contain it")
                }
            }
        }
    }
}
