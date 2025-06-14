package org.gnit.lucenekmp.geo

import org.gnit.lucenekmp.tests.geo.ShapeTestUtil
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TestXYPolygon : LuceneTestCase() {

    @Test
    fun testPolygonLine() {
        val expected = expectThrows<IllegalArgumentException>(IllegalArgumentException::class) {
            XYPolygon(floatArrayOf(18f, 18f, 18f), floatArrayOf(-66f, -65f, -66f))
        }
        assertTrue(expected!!.message!!.contains("at least 4 polygon points required"))
    }

    @Test
    fun testPolygonBogus() {
        val expected = expectThrows<IllegalArgumentException>(IllegalArgumentException::class) {
            XYPolygon(floatArrayOf(18f, 18f, 19f, 19f), floatArrayOf(-66f, -65f, -65f, -66f, -66f))
        }
        assertTrue(expected!!.message!!.contains("must be equal length"))
    }

    @Test
    fun testPolygonNotClosed() {
        val expected = expectThrows<IllegalArgumentException>(IllegalArgumentException::class) {
            XYPolygon(floatArrayOf(18f, 18f, 19f, 19f, 19f), floatArrayOf(-66f, -65f, -65f, -66f, -67f))
        }
        assertTrue(expected!!.message!!.contains("it must close itself"))
    }

    @Test
    fun testPolygonNaN() {
        val expected = expectThrows<IllegalArgumentException>(IllegalArgumentException::class) {
            XYPolygon(floatArrayOf(18f, 18f, 19f, Float.NaN, 18f), floatArrayOf(-66f, -65f, -65f, -66f, -66f))
        }
        assertTrue(expected!!.message!!.contains("invalid value NaN"))
    }

    @Test
    fun testPolygonPositiveInfinite() {
        val expected = expectThrows<IllegalArgumentException>(IllegalArgumentException::class) {
            XYPolygon(floatArrayOf(18f, 18f, 19f, 19f, 18f), floatArrayOf(-66f, Float.POSITIVE_INFINITY, -65f, -66f, -66f))
        }
        assertTrue(expected!!.message!!.contains("invalid value Inf"))
    }

    @Test
    fun testPolygonNegativeInfinite() {
        val expected = expectThrows<IllegalArgumentException>(IllegalArgumentException::class) {
            XYPolygon(floatArrayOf(18f, 18f, 19f, 19f, 18f), floatArrayOf(-66f, -65f, -65f, Float.NEGATIVE_INFINITY, -66f))
        }
        assertTrue(expected!!.message!!.contains("invalid value -Inf"))
    }

    @Test
    fun testEqualsAndHashCode() {
        val polygon = ShapeTestUtil.nextPolygon()
        val copy = XYPolygon(polygon.polyX, polygon.polyY, *polygon.getHoles())
        assertEquals(polygon, copy)
        assertEquals(polygon.hashCode(), copy.hashCode())
        val otherPolygon = ShapeTestUtil.nextPolygon()
        if (!polygon.polyX.contentEquals(otherPolygon.polyX) ||
            !polygon.polyY.contentEquals(otherPolygon.polyY) ||
            !polygon.getHoles().contentEquals(otherPolygon.getHoles())) {
            assertNotEquals(polygon, otherPolygon)
            assertNotEquals(polygon.hashCode(), otherPolygon.hashCode())
        } else {
            assertEquals(polygon, otherPolygon)
            assertEquals(polygon.hashCode(), otherPolygon.hashCode())
        }
    }
}
