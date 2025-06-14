package org.gnit.lucenekmp.tests.geo

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.geo.Point

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

    fun nextPoint(): Point {
        val lat = nextLatitude()
        val lon = nextLongitude()
        return Point(lat, lon)
    }
}
