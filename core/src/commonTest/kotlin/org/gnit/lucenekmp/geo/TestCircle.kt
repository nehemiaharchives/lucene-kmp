package org.gnit.lucenekmp.geo

import org.gnit.lucenekmp.tests.geo.GeoTestUtil
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TestCircle : LuceneTestCase() {

    @Test
    fun testInvalidLat() {
        val expected = expectThrows<IllegalArgumentException>(IllegalArgumentException::class) {
            Circle(134.14, 45.23, 1000.0)
        }
        assertTrue(expected!!.message!!.contains("invalid latitude 134.14; must be between -90.0 and 90.0"))
    }

    @Test
    fun testInvalidLon() {
        val expected = expectThrows<IllegalArgumentException>(IllegalArgumentException::class) {
            Circle(43.5, 180.5, 1000.0)
        }
        assertTrue(expected!!.message!!.contains("invalid longitude 180.5; must be between -180.0 and 180.0"))
    }

    @Test
    fun testNegativeRadius() {
        val expected = expectThrows<IllegalArgumentException>(IllegalArgumentException::class) {
            Circle(43.5, 45.23, -1000.0)
        }
        assertTrue(expected!!.message!!.contains("radiusMeters: '-1000.0' is invalid"))
    }

    @Test
    fun testInfiniteRadius() {
        val expected = expectThrows<IllegalArgumentException>(IllegalArgumentException::class) {
            Circle(43.5, 45.23, Double.POSITIVE_INFINITY)
        }
        assertTrue(expected!!.message!!.contains("radiusMeters: 'Infinity' is invalid"))
    }

    private fun nextCircle(): Circle {
        val lat = GeoTestUtil.nextLatitude()
        val lon = GeoTestUtil.nextLongitude()
        val radiusMeters = random().nextDouble() * GeoUtils.EARTH_MEAN_RADIUS_METERS * PI / 2.0 + 1.0
        return Circle(lat, lon, radiusMeters)
    }

    @Test
    fun testEqualsAndHashCode() {
        val circle = nextCircle()
        val copy = Circle(circle.lat, circle.lon, circle.radius)
        assertEquals(circle, copy)
        assertEquals(circle.hashCode(), copy.hashCode())
        val otherCircle = nextCircle()
        if (circle.lon.compareTo(otherCircle.lon) != 0 ||
            circle.lat.compareTo(otherCircle.lat) != 0 ||
            circle.radius.compareTo(otherCircle.radius) != 0) {
            assertNotEquals(circle, otherCircle)
            assertNotEquals(circle.hashCode(), otherCircle.hashCode())
        } else {
            assertEquals(circle, otherCircle)
            assertEquals(circle.hashCode(), otherCircle.hashCode())
        }
    }
}

