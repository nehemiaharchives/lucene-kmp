package org.gnit.lucenekmp.geo

import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.compare
import org.gnit.lucenekmp.jdkport.doubleToLongBits
import org.gnit.lucenekmp.jdkport.isNaN
import org.gnit.lucenekmp.util.SloppyMath
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/** Represents a lat/lon rectangle.  */
class Rectangle(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double) :
    LatLonGeometry() {
    /** maximum longitude value (in degrees)  */
    val minLat: Double

    /** minimum longitude value (in degrees)  */
    val minLon: Double

    /** maximum latitude value (in degrees)  */
    val maxLat: Double

    /** minimum latitude value (in degrees)  */
    val maxLon: Double

    override fun toComponent2D(): Component2D {
        return Rectangle2D.create(this)
    }

    override fun toString(): String {
        val b: StringBuilder = StringBuilder()
        b.append("Rectangle(lat=")
        b.append(minLat)
        b.append(" TO ")
        b.append(maxLat)
        b.append(" lon=")
        b.append(minLon)
        b.append(" TO ")
        b.append(maxLon)
        if (maxLon < minLon) {
            b.append(" [crosses dateline!]")
        }
        b.append(")")

        return b.toString()
    }

    /** Returns true if this bounding box crosses the dateline  */
    fun crossesDateline(): Boolean {
        return maxLon < minLon
    }

    /**
     * Constructs a bounding box by first validating the provided latitude and longitude coordinates
     */
    init {
        GeoUtils.checkLatitude(minLat)
        GeoUtils.checkLatitude(maxLat)
        GeoUtils.checkLongitude(minLon)
        GeoUtils.checkLongitude(maxLon)
        this.minLon = minLon
        this.maxLon = maxLon
        this.minLat = minLat
        this.maxLat = maxLat
        require(maxLat >= minLat)

        // NOTE: cannot assert maxLon >= minLon since this rect could cross the dateline
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || this::class != o::class) return false

        val rectangle = o as Rectangle

        if (Double.compare(rectangle.minLat, minLat) != 0) return false
        if (Double.compare(rectangle.minLon, minLon) != 0) return false
        if (Double.compare(rectangle.maxLat, maxLat) != 0) return false
        return Double.compare(rectangle.maxLon, maxLon) == 0
    }

    override fun hashCode(): Int {
        var result: Int
        var temp: Long = Double.doubleToLongBits(minLat)
        result = (temp xor (temp ushr 32)).toInt()
        temp = Double.doubleToLongBits(minLon)
        result = 31 * result + (temp xor (temp ushr 32)).toInt()
        temp = Double.doubleToLongBits(maxLat)
        result = 31 * result + (temp xor (temp ushr 32)).toInt()
        temp = Double.doubleToLongBits(maxLon)
        result = 31 * result + (temp xor (temp ushr 32)).toInt()
        return result
    }

    companion object {
        /**
         * returns true if rectangle (defined by minLat, maxLat, minLon, maxLon) contains the lat lon
         * point
         */
        fun containsPoint(
            lat: Double,
            lon: Double,
            minLat: Double,
            maxLat: Double,
            minLon: Double,
            maxLon: Double
        ): Boolean {
            return lat >= minLat && lat <= maxLat && lon >= minLon && lon <= maxLon
        }

        /** Compute Bounding Box for a circle using WGS-84 parameters  */
        fun fromPointDistance(
            centerLat: Double, centerLon: Double, radiusMeters: Double
        ): Rectangle {
            GeoUtils.checkLatitude(centerLat)
            GeoUtils.checkLongitude(centerLon)
            val radLat: Double = Math.toRadians(centerLat)
            val radLon: Double = Math.toRadians(centerLon)
            // LUCENE-7143
            val radDistance: Double = (radiusMeters + 7E-2) / GeoUtils.EARTH_MEAN_RADIUS_METERS
            var minLat = radLat - radDistance
            var maxLat = radLat + radDistance
            var minLon: Double
            var maxLon: Double

            if (minLat > GeoUtils.MIN_LAT_RADIANS && maxLat < GeoUtils.MAX_LAT_RADIANS) {
                val deltaLon: Double = SloppyMath.asin(
                    GeoUtils.sloppySin(radDistance) / SloppyMath.cos(radLat)
                )
                minLon = radLon - deltaLon
                if (minLon < GeoUtils.MIN_LON_RADIANS) {
                    minLon += 2.0 * Math.PI
                }
                maxLon = radLon + deltaLon
                if (maxLon > GeoUtils.MAX_LON_RADIANS) {
                    maxLon -= 2.0 * Math.PI
                }
            } else {
                // a pole is within the distance
                minLat = max(minLat, GeoUtils.MIN_LAT_RADIANS)
                maxLat = min(maxLat, GeoUtils.MAX_LAT_RADIANS)
                minLon = GeoUtils.MIN_LON_RADIANS
                maxLon = GeoUtils.MAX_LON_RADIANS
            }

            return Rectangle(
                Math.toDegrees(minLat),
                Math.toDegrees(maxLat),
                Math.toDegrees(minLon),
                Math.toDegrees(maxLon)
            )
        }

        /** maximum error from [.axisLat]. logic must be prepared to handle this  */
        val AXISLAT_ERROR: Double =
            Math.toDegrees(0.1 / GeoUtils.EARTH_MEAN_RADIUS_METERS)

        /**
         * Calculate the latitude of a circle's intersections with its bbox meridians.
         *
         *
         * **NOTE:** the returned value will be +/- [.AXISLAT_ERROR] of the actual value.
         *
         * @param centerLat The latitude of the circle center
         * @param radiusMeters The radius of the circle in meters
         * @return A latitude
         */
        fun axisLat(centerLat: Double, radiusMeters: Double): Double {
            // A spherical triangle with:
            // r is the radius of the circle in radians
            // l1 is the latitude of the circle center
            // l2 is the latitude of the point at which the circle intersect's its bbox longitudes
            // We know r is tangent to the bbox meridians at l2, therefore it is a right angle.
            // So from the law of cosines, with the angle of l1 being 90, we have:
            // cos(l1) = cos(r) * cos(l2) + sin(r) * sin(l2) * cos(90)
            // The second part cancels out because cos(90) == 0, so we have:
            // cos(l1) = cos(r) * cos(l2)
            // Solving for l2, we get:
            // l2 = acos( cos(l1) / cos(r) )
            // We ensure r is in the range (0, PI/2) and l1 in the range (0, PI/2]. This means we
            // cannot divide by 0, and we will always get a positive value in the range [0, 1) as
            // the argument to arc cosine, resulting in a range (0, PI/2].
            val PIO2: Double = Math.PI / 2.0
            var l1: Double = Math.toRadians(centerLat)
            val r: Double = (radiusMeters + 7E-2) / GeoUtils.EARTH_MEAN_RADIUS_METERS

            // if we are within radius range of a pole, the lat is the pole itself
            if (abs(l1) + r >= GeoUtils.MAX_LAT_RADIANS) {
                return if (centerLat >= 0) GeoUtils.MAX_LAT_INCL else GeoUtils.MIN_LAT_INCL
            }

            // adjust l1 as distance from closest pole, to form a right triangle with bbox meridians
            // and ensure it is in the range (0, PI/2]
            l1 = if (centerLat >= 0) PIO2 - l1 else l1 + PIO2

            var l2 = acos(cos(l1) / cos(r))
            require(!Double.isNaN(l2))

            // now adjust back to range [-pi/2, pi/2], ie latitude in radians
            l2 = if (centerLat >= 0) PIO2 - l2 else l2 - PIO2

            return Math.toDegrees(l2)
        }

        /** Returns the bounding box over an array of polygons  */
        fun fromPolygon(polygons: Array<Polygon>): Rectangle {
            // compute bounding box
            var minLat = Double.Companion.POSITIVE_INFINITY
            var maxLat = Double.Companion.NEGATIVE_INFINITY
            var minLon = Double.Companion.POSITIVE_INFINITY
            var maxLon = Double.Companion.NEGATIVE_INFINITY

            for (i in polygons.indices) {
                minLat = min(polygons[i].minLat, minLat)
                maxLat = max(polygons[i].maxLat, maxLat)
                minLon = min(polygons[i].minLon, minLon)
                maxLon = max(polygons[i].maxLon, maxLon)
            }

            return Rectangle(minLat, maxLat, minLon, maxLon)
        }
    }
}
