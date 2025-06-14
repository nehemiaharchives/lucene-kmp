package org.gnit.lucenekmp.tests.geo

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.geo.Circle
import org.gnit.lucenekmp.geo.GeoUtils
import kotlin.math.PI

object GeoTestUtil {
    private const val MIN_LAT_INCL: Double = -90.0
    private const val MAX_LAT_INCL: Double = 90.0
    private const val MIN_LON_INCL: Double = -180.0
    private const val MAX_LON_INCL: Double = 180.0

    fun nextLatitude(): Double {
        return MIN_LAT_INCL + (MAX_LAT_INCL - MIN_LAT_INCL) * LuceneTestCase.random().nextDouble()
    }

    fun nextLongitude(): Double {
        return MIN_LON_INCL + (MAX_LON_INCL - MIN_LON_INCL) * LuceneTestCase.random().nextDouble()
    }

    fun nextCircle(): Circle {
        val lat = nextLatitude()
        val lon = nextLongitude()
        val radiusMeters = LuceneTestCase.random().nextDouble() * GeoUtils.EARTH_MEAN_RADIUS_METERS * PI / 2.0 + 1.0
        return Circle(lat, lon, radiusMeters)
    }
}
