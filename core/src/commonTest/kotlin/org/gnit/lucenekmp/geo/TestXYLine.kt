package org.gnit.lucenekmp.geo

import org.gnit.lucenekmp.tests.geo.ShapeTestUtil
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TestXYLine : LuceneTestCase() {

    @Test
    fun testLineNullXs() {
        assertFailsWith<NullPointerException> {
            XYLine(null as FloatArray, floatArrayOf(-66f, -65f, -65f, -66f, -66f))
        }
    }

    @Test
    fun testPolygonNullYs() {
        assertFailsWith<NullPointerException> {
            XYLine(floatArrayOf(18f, 18f, 19f, 19f, 18f), null as FloatArray)
        }
    }

    @Test
    fun testLineEnoughPoints() {
        val e = expectThrows(IllegalArgumentException::class) {
            XYLine(floatArrayOf(18f), floatArrayOf(-66f))
        }
        assertTrue(e!!.message!!.contains("at least 2 line points required"))
    }

    @Test
    fun testLinesBogus() {
        val e = expectThrows(IllegalArgumentException::class) {
            XYLine(floatArrayOf(18f, 18f, 19f, 19f), floatArrayOf(-66f, -65f, -65f, -66f, -66f))
        }
        assertTrue(e!!.message!!.contains("must be equal length"))
    }

    @Test
    fun testLineNaN() {
        val e = expectThrows(IllegalArgumentException::class) {
            XYLine(floatArrayOf(18f, 18f, 19f, Float.NaN, 18f), floatArrayOf(-66f, -65f, -65f, -66f, -66f))
        }
        assertTrue(e!!.message!!.contains("invalid value NaN"))
    }

    @Test
    fun testLinePositiveInfinite() {
        val e = expectThrows(IllegalArgumentException::class) {
            XYLine(floatArrayOf(18f, 18f, 19f, 19f, 18f),
                floatArrayOf(-66f, Float.POSITIVE_INFINITY, -65f, -66f, -66f))
        }
        assertTrue(e!!.message!!.contains("invalid value Inf"))
    }

    @Test
    fun testLineNegativeInfinite() {
        val e = expectThrows(IllegalArgumentException::class) {
            XYLine(floatArrayOf(18f, 18f, 19f, 19f, 18f),
                floatArrayOf(-66f, -65f, -65f, Float.NEGATIVE_INFINITY, -66f))
        }
        assertTrue(e!!.message!!.contains("invalid value -Inf"))
    }

    @Test
    fun testEqualsAndHashCode() {
        val line = ShapeTestUtil.nextLine()
        val copy = XYLine(line.getX(), line.getY())
        assertEquals(line, copy)
        assertEquals(line.hashCode(), copy.hashCode())
        val otherLine = ShapeTestUtil.nextLine()
        if (!line.getX().contentEquals(otherLine.getX()) || !line.getY().contentEquals(otherLine.getY())) {
            assertNotEquals(line, otherLine)
            assertNotEquals(line.hashCode(), otherLine.hashCode())
        } else {
            assertEquals(line, otherLine)
            assertEquals(line.hashCode(), otherLine.hashCode())
        }
    }
}

