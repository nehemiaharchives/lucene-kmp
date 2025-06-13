package org.gnit.lucenekmp.tests.geo

import org.gnit.lucenekmp.tests.util.LuceneTestCase

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

    fun nextPointNear(rectangle: org.gnit.lucenekmp.geo.Rectangle): DoubleArray {
        if (rectangle.crossesDateline()) {
            return if (LuceneTestCase.random().nextBoolean()) {
                nextPointNear(org.gnit.lucenekmp.geo.Rectangle(rectangle.minLat, rectangle.maxLat, -180.0, rectangle.maxLon))
            } else {
                nextPointNear(org.gnit.lucenekmp.geo.Rectangle(rectangle.minLat, rectangle.maxLat, rectangle.minLon, 180.0))
            }
        }
        val rnd = LuceneTestCase.random()
        val latRange = rectangle.maxLat - rectangle.minLat
        val lonRange = rectangle.maxLon - rectangle.minLon
        var lat = rectangle.minLat + latRange * (rnd.nextDouble() * 1.02 - 0.01)
        var lon = rectangle.minLon + lonRange * (rnd.nextDouble() * 1.02 - 0.01)
        if (lat > 90) lat = 90.0
        if (lat < -90) lat = -90.0
        if (lon > 180) lon = 180.0
        if (lon < -180) lon = -180.0
        return doubleArrayOf(lat, lon)
    }
}
