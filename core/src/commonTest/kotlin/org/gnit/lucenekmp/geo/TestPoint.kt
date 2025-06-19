package org.gnit.lucenekmp.geo

import org.gnit.lucenekmp.tests.geo.GeoTestUtil
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TestPoint : LuceneTestCase() {
    @Test
    fun testInvalidLat() {
        val expected = expectThrows<IllegalArgumentException>(IllegalArgumentException::class) {
            Point(134.14, 45.23)
        }
        assertTrue(expected!!.message!!.contains("invalid latitude 134.14; must be between -90.0 and 90.0"))
    }

    @Test
    fun testInvalidLon() {
        val expected = expectThrows<IllegalArgumentException>(IllegalArgumentException::class) {
            Point(43.5, 180.5)
        }
        assertTrue(expected!!.message!!.contains("invalid longitude 180.5; must be between -180.0 and 180.0"))
    }

    @Test
    fun testEqualsAndHashCode() {
        val point = GeoTestUtil.nextPoint()
        val copy = Point(point.lat, point.lon)

        assertEquals(point, copy)
        assertEquals(point.hashCode(), copy.hashCode())

        val otherPoint = GeoTestUtil.nextPoint()
        if (point.lat.compareTo(otherPoint.lat) != 0 || point.lon.compareTo(otherPoint.lon) != 0) {
            assertNotEquals(point, otherPoint)
        } else {
            assertEquals(point, otherPoint)
            assertEquals(point.hashCode(), otherPoint.hashCode())
        }
    }
}
