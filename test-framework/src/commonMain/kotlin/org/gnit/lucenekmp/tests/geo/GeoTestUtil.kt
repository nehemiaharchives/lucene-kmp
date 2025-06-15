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

    fun nextLatitudeBetween(minLat: Double, maxLat: Double): Double {
        return minLat + (maxLat - minLat) * LuceneTestCase.random().nextDouble()
    }

    fun nextLongitudeBetween(minLon: Double, maxLon: Double): Double {
        return minLon + (maxLon - minLon) * LuceneTestCase.random().nextDouble()
    }

    fun nextPointNear(rectangle: org.gnit.lucenekmp.geo.Rectangle): DoubleArray {
        val deltaLat = (rectangle.maxLat - rectangle.minLat).coerceAtLeast(0.01) / 10.0
        val deltaLon = (rectangle.maxLon - rectangle.minLon).coerceAtLeast(0.01) / 10.0
        var lat = nextLatitudeBetween(rectangle.minLat - deltaLat, rectangle.maxLat + deltaLat)
        var lon = nextLongitudeBetween(rectangle.minLon - deltaLon, rectangle.maxLon + deltaLon)
        lat = lat.coerceIn(MIN_LAT_INCL, MAX_LAT_INCL)
        lon = lon.coerceIn(MIN_LON_INCL, MAX_LON_INCL)
        return doubleArrayOf(lat, lon)
    }

    fun nextPointNear(polygon: org.gnit.lucenekmp.geo.Polygon): DoubleArray {
        val deltaLat = (polygon.maxLat - polygon.minLat).coerceAtLeast(0.01) / 10.0
        val deltaLon = (polygon.maxLon - polygon.minLon).coerceAtLeast(0.01) / 10.0
        var lat = nextLatitudeBetween(polygon.minLat - deltaLat, polygon.maxLat + deltaLat)
        var lon = nextLongitudeBetween(polygon.minLon - deltaLon, polygon.maxLon + deltaLon)
        lat = lat.coerceIn(MIN_LAT_INCL, MAX_LAT_INCL)
        lon = lon.coerceIn(MIN_LON_INCL, MAX_LON_INCL)
        return doubleArrayOf(lat, lon)
    }

    fun nextBoxNear(polygon: org.gnit.lucenekmp.geo.Polygon): org.gnit.lucenekmp.geo.Rectangle {
        val p1 = nextPointNear(polygon)
        val p2 = nextPointNear(polygon)
        val minLat = kotlin.math.min(p1[0], p2[0]).coerceIn(MIN_LAT_INCL, MAX_LAT_INCL)
        val maxLat = kotlin.math.max(p1[0], p2[0]).coerceIn(MIN_LAT_INCL, MAX_LAT_INCL)
        val minLon = kotlin.math.min(p1[1], p2[1]).coerceIn(MIN_LON_INCL, MAX_LON_INCL)
        val maxLon = kotlin.math.max(p1[1], p2[1]).coerceIn(MIN_LON_INCL, MAX_LON_INCL)
        return org.gnit.lucenekmp.geo.Rectangle(minLat, maxLat, minLon, maxLon)
    }

    fun createRegularPolygon(
        centerLat: Double,
        centerLon: Double,
        radiusMeters: Double,
        gons: Int
    ): org.gnit.lucenekmp.geo.Polygon {
        val polyLats = DoubleArray(gons + 1)
        val polyLons = DoubleArray(gons + 1)
        val latRadius = radiusMeters / 111000.0
        val lonRadius = radiusMeters / (111000.0 * kotlin.math.cos(kotlin.math.PI * centerLat / 180.0))
        for (i in 0 until gons) {
            val angle = 2.0 * kotlin.math.PI * i / gons
            polyLats[i] = centerLat + latRadius * kotlin.math.cos(angle)
            polyLons[i] = centerLon + lonRadius * kotlin.math.sin(angle)
        }
        polyLats[gons] = polyLats[0]
        polyLons[gons] = polyLons[0]
        return org.gnit.lucenekmp.geo.Polygon(polyLats, polyLons)
    }

    fun nextPolygon(): org.gnit.lucenekmp.geo.Polygon {
        val radius = LuceneTestCase.random().nextDouble() * 10000 + 100
        val latRadius = radius / 111000.0
        val centerLat = nextLatitudeBetween(MIN_LAT_INCL + latRadius, MAX_LAT_INCL - latRadius)
        val lonRadius = radius / (111000.0 * kotlin.math.cos(kotlin.math.PI * centerLat / 180.0))
        val centerLon = nextLongitudeBetween(MIN_LON_INCL + lonRadius, MAX_LON_INCL - lonRadius)
        val gons = LuceneTestCase.atLeast(4)
        return createRegularPolygon(centerLat, centerLon, radius, gons)
    }

    fun containsSlowly(polygon: org.gnit.lucenekmp.geo.Polygon, longitude: Double, latitude: Double): Boolean {
        if (polygon.getHoles().isNotEmpty()) {
            throw UnsupportedOperationException("this testing method does not support holes")
        }
        if (latitude < polygon.minLat || latitude > polygon.maxLat || longitude < polygon.minLon || longitude > polygon.maxLon) {
            return false
        }
        var c = false
        val verty = polygon.getPolyLats()
        val vertx = polygon.getPolyLons()
        var j = 1
        var i = 0
        while (j < verty.size) {
            if ((latitude == verty[j] && latitude == verty[i]) || ((latitude <= verty[j] && latitude >= verty[i]) != (latitude >= verty[j] && latitude <= verty[i]))) {
                if ((longitude == vertx[j] && longitude == vertx[i]) || ((longitude <= vertx[j] && longitude >= vertx[i]) != (longitude >= vertx[j] && longitude <= vertx[i]) && org.gnit.lucenekmp.geo.GeoUtils.orient(vertx[i], verty[i], vertx[j], verty[j], longitude, latitude) == 0)) {
                    return true
                } else if (((verty[i] > latitude) != (verty[j] > latitude)) && (longitude < (vertx[j] - vertx[i]) * (latitude - verty[i]) / (verty[j] - verty[i]) + vertx[i])) {
                    c = !c
                }
            }
            i++
            j++
        }
        return c
    }
}
