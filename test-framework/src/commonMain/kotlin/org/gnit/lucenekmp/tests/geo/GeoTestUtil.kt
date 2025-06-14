package org.gnit.lucenekmp.tests.geo

import org.gnit.lucenekmp.tests.util.LuceneTestCase

import org.gnit.lucenekmp.geo.Line

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

    fun nextLine(): Line {
        val size = LuceneTestCase.random().nextInt(2, 6)
        val lats = DoubleArray(size)
        val lons = DoubleArray(size)
        for (i in 0 until size) {
            lats[i] = nextLatitude()
            lons[i] = nextLongitude()
        }
        return Line(lats, lons)
    }
}
