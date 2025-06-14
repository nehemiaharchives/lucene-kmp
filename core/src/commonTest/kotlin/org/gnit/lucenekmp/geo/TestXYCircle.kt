package org.gnit.lucenekmp.geo

import org.gnit.lucenekmp.tests.geo.ShapeTestUtil
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TestXYCircle : LuceneTestCase() {

    /** point values cannot be NaN */
    @Test
    fun testNaN() {
        var expected = expectThrows(IllegalArgumentException::class) {
            XYCircle(Float.NaN, 45.23f, 35.5f)
        }
        assertTrue(expected!!.message!!.contains("invalid value NaN"))

        expected = expectThrows(IllegalArgumentException::class) {
            XYCircle(43.5f, Float.NaN, 35.5f)
        }
        assertTrue(expected!!.message!!.contains("invalid value NaN"))
    }

    /** point values must be finite */
    @Test
    fun testPositiveInf() {
        var expected = expectThrows(IllegalArgumentException::class) {
            XYCircle(Float.POSITIVE_INFINITY, 45.23f, 35.5f)
        }
        assertTrue(expected!!.message!!.contains("invalid value Inf"))

        expected = expectThrows(IllegalArgumentException::class) {
            XYCircle(43.5f, Float.POSITIVE_INFINITY, 35.5f)
        }
        assertTrue(expected!!.message!!.contains("invalid value Inf"))
    }

    /** point values must be finite */
    @Test
    fun testNegativeInf() {
        var expected = expectThrows(IllegalArgumentException::class) {
            XYCircle(Float.NEGATIVE_INFINITY, 45.23f, 35.5f)
        }
        assertTrue(expected!!.message!!.contains("invalid value -Inf"))

        expected = expectThrows(IllegalArgumentException::class) {
            XYCircle(43.5f, Float.NEGATIVE_INFINITY, 35.5f)
        }
        assertTrue(expected!!.message!!.contains("invalid value -Inf"))
    }

    /** radius must be positive */
    @Test
    fun testNegativeRadius() {
        val expected = expectThrows(IllegalArgumentException::class) {
            XYCircle(43.5f, 45.23f, -1000f)
        }
        assertTrue(expected!!.message!!.contains("radius must be bigger than 0"))
    }

    /** radius must be finite */
    @Test
    fun testInfiniteRadius() {
        val expected = expectThrows(IllegalArgumentException::class) {
            XYCircle(43.5f, 45.23f, Float.POSITIVE_INFINITY)
        }
        assertTrue(expected!!.message!!.contains("radius must be finite"))
    }

    /** equals and hashcode */
    @Test
    fun testEqualsAndHashCode() {
        val circle = ShapeTestUtil.nextCircle()
        val copy = XYCircle(circle.x, circle.y, circle.radius)
        assertEquals(circle, copy)
        assertEquals(circle.hashCode(), copy.hashCode())
        val other = ShapeTestUtil.nextCircle()
        if (circle.x.compareTo(other.x) != 0 ||
            circle.y.compareTo(other.y) != 0 ||
            circle.radius.compareTo(other.radius) != 0) {
            assertNotEquals(circle, other)
            assertNotEquals(circle.hashCode(), other.hashCode())
        } else {
            assertEquals(circle, other)
            assertEquals(circle.hashCode(), other.hashCode())
        }
    }
}
