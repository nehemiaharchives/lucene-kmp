package org.gnit.lucenekmp.geo

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TestXYPoint : LuceneTestCase() {

    /** point values cannot be NaN */
    @Test
    fun testNaN() {
        var expected = expectThrows(IllegalArgumentException::class) {
            XYPoint(Float.NaN, 45.23f)
        }
        assertTrue(expected!!.message!!.contains("invalid value NaN"))

        expected = expectThrows(IllegalArgumentException::class) {
            XYPoint(43.5f, Float.NaN)
        }
        assertTrue(expected!!.message!!.contains("invalid value NaN"))
    }

    /** point values must be finite */
    @Test
    fun testPositiveInf() {
        var expected = expectThrows(IllegalArgumentException::class) {
            XYPoint(Float.POSITIVE_INFINITY, 45.23f)
        }
        assertTrue(expected!!.message!!.contains("invalid value Inf"))

        expected = expectThrows(IllegalArgumentException::class) {
            XYPoint(43.5f, Float.POSITIVE_INFINITY)
        }
        assertTrue(expected!!.message!!.contains("invalid value Inf"))
    }

    /** point values must be finite */
    @Test
    fun testNegativeInf() {
        var expected = expectThrows(IllegalArgumentException::class) {
            XYPoint(Float.NEGATIVE_INFINITY, 45.23f)
        }
        assertTrue(expected!!.message!!.contains("invalid value -Inf"))

        expected = expectThrows(IllegalArgumentException::class) {
            XYPoint(43.5f, Float.NEGATIVE_INFINITY)
        }
        assertTrue(expected!!.message!!.contains("invalid value -Inf"))
    }

    /** equals and hashcode */
    @Test
    fun testEqualsAndHashCode() {
        val point = XYPoint(random().nextFloat(), random().nextFloat())
        val copy = XYPoint(point.x, point.y)
        assertEquals(point, copy)
        assertEquals(point.hashCode(), copy.hashCode())
        val otherPoint = XYPoint(random().nextFloat(), random().nextFloat())
        if (point.x.compareTo(otherPoint.x) != 0 || point.y.compareTo(otherPoint.y) != 0) {
            assertNotEquals(point, otherPoint)
            // it is possible to have hashcode collisions
        } else {
            assertEquals(point, otherPoint)
            assertEquals(point.hashCode(), otherPoint.hashCode())
        }
    }
}

