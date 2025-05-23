package org.gnit.lucenekmp.geo

import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.doubleToRawLongBits
import org.gnit.lucenekmp.jdkport.isNaN
import org.gnit.lucenekmp.jdkport.longBitsToDouble
import org.gnit.lucenekmp.util.SloppyMath

/**
 * Basic reusable geo-spatial utility methods
 *
 * @lucene.experimental
 */
object GeoUtils {
    /** Minimum longitude value.  */
    const val MIN_LON_INCL: Double = -180.0

    /** Maximum longitude value.  */
    const val MAX_LON_INCL: Double = 180.0

    /** Minimum latitude value.  */
    const val MIN_LAT_INCL: Double = -90.0

    /** Maximum latitude value.  */
    const val MAX_LAT_INCL: Double = 90.0

    /** min longitude value in radians  */
    val MIN_LON_RADIANS: Double = Math.toRadians(MIN_LON_INCL)

    /** min latitude value in radians  */
    val MIN_LAT_RADIANS: Double = Math.toRadians(MIN_LAT_INCL)

    /** max longitude value in radians  */
    val MAX_LON_RADIANS: Double = Math.toRadians(MAX_LON_INCL)

    /** max latitude value in radians  */
    val MAX_LAT_RADIANS: Double = Math.toRadians(MAX_LAT_INCL)

    // WGS84 earth-ellipsoid parameters
    /** mean earth axis in meters  */ // see http://earth-info.nga.mil/GandG/publications/tr8350.2/wgs84fin.pdf
    const val EARTH_MEAN_RADIUS_METERS: Double = 6371008.7714

    /** validates latitude value is within standard +/-90 coordinate bounds  */
    fun checkLatitude(latitude: Double) {
        require(!(Double.isNaN(latitude) || latitude < MIN_LAT_INCL || latitude > MAX_LAT_INCL)) {
            ("invalid latitude "
                    + latitude
                    + "; must be between "
                    + MIN_LAT_INCL
                    + " and "
                    + MAX_LAT_INCL)
        }
    }

    /** validates longitude value is within standard +/-180 coordinate bounds  */
    fun checkLongitude(longitude: Double) {
        require(!(Double.isNaN(longitude) || longitude < MIN_LON_INCL || longitude > MAX_LON_INCL)) {
            ("invalid longitude "
                    + longitude
                    + "; must be between "
                    + MIN_LON_INCL
                    + " and "
                    + MAX_LON_INCL)
        }
    }

    // some sloppyish stuff, do we really need this to be done in a sloppy way?
    // unless it is performance sensitive, we should try to remove.
    private const val PIO2: Double = Math.PI / 2.0

    /**
     * Returns the trigonometric sine of an angle converted as a cos operation.
     *
     *
     * Note that this is not quite right... e.g. sin(0) != 0
     *
     *
     * Special cases:
     *
     *
     *  * If the argument is `NaN` or an infinity, then the result is `NaN`.
     *
     *
     * @param a an angle, in radians.
     * @return the sine of the argument.
     * @see Math.sin
     */
    // TODO: deprecate/remove this? at least its no longer public.
    fun sloppySin(a: Double): Double {
        return SloppyMath.cos(a - PIO2)
    }

    /**
     * binary search to find the exact sortKey needed to match the specified radius any sort key lte
     * this is a query match.
     */
    fun distanceQuerySortKey(radius: Double): Double {
        // effectively infinite
        if (radius >= SloppyMath.haversinMeters(Double.Companion.MAX_VALUE)) {
            return SloppyMath.haversinMeters(Double.Companion.MAX_VALUE)
        }

        // this is a search through non-negative long space only
        var lo: Long = 0
        var hi: Long = Double.doubleToRawLongBits(Double.Companion.MAX_VALUE)
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val sortKey: Double = Double.longBitsToDouble(mid)
            val midRadius: Double = SloppyMath.haversinMeters(sortKey)
            if (midRadius == radius) {
                return sortKey
            } else if (midRadius > radius) {
                hi = mid - 1
            } else {
                lo = mid + 1
            }
        }

        // not found: this is because a user can supply an arbitrary radius, one that we will never
        // calculate exactly via our haversin method.
        val ceil: Double = Double.longBitsToDouble(lo)
        require(SloppyMath.haversinMeters(ceil) > radius)
        return ceil
    }

    /**
     * Compute the relation between the provided box and distance query. This only works for boxes
     * that do not cross the dateline.
     */
    fun relate(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
        lat: Double,
        lon: Double,
        distanceSortKey: Double,
        axisLat: Double
    ): PointValues.Relation {
        require(!(minLon > maxLon)) { "Box crosses the dateline" }

        if ((lon < minLon || lon > maxLon)
            && (axisLat + Rectangle.AXISLAT_ERROR < minLat
                    || axisLat - Rectangle.AXISLAT_ERROR > maxLat)
        ) {
            // circle not fully inside / crossing axis
            if (SloppyMath.haversinSortKey(
                    lat,
                    lon,
                    minLat,
                    minLon
                ) > distanceSortKey && SloppyMath.haversinSortKey(
                    lat,
                    lon,
                    minLat,
                    maxLon
                ) > distanceSortKey && SloppyMath.haversinSortKey(
                    lat,
                    lon,
                    maxLat,
                    minLon
                ) > distanceSortKey && SloppyMath.haversinSortKey(
                    lat,
                    lon,
                    maxLat,
                    maxLon
                ) > distanceSortKey
            ) {
                // no points inside
                return PointValues.Relation.CELL_OUTSIDE_QUERY
            }
        }

        if (within90LonDegrees(lon, minLon, maxLon)
            && SloppyMath.haversinSortKey(
                lat,
                lon,
                minLat,
                minLon
            ) <= distanceSortKey && SloppyMath.haversinSortKey(
                lat,
                lon,
                minLat,
                maxLon
            ) <= distanceSortKey && SloppyMath.haversinSortKey(
                lat,
                lon,
                maxLat,
                minLon
            ) <= distanceSortKey && SloppyMath.haversinSortKey(
                lat,
                lon,
                maxLat,
                maxLon
            ) <= distanceSortKey
        ) {
            // we are fully enclosed, collect everything within this subtree
            return PointValues.Relation.CELL_INSIDE_QUERY
        }

        return PointValues.Relation.CELL_CROSSES_QUERY
    }

    /** Return whether all points of `[minLon,maxLon]` are within 90 degrees of `lon`.  */
    fun within90LonDegrees(lon: Double, minLon: Double, maxLon: Double): Boolean {
        var lon = lon
        if (maxLon <= lon - 180) {
            lon -= 360.0
        } else if (minLon >= lon + 180) {
            lon += 360.0
        }
        return maxLon - lon < 90 && lon - minLon < 90
    }

    /**
     * Returns a positive value if points a, b, and c are arranged in counter-clockwise order,
     * negative value if clockwise, zero if collinear.
     */
    // see the "Orient2D" method described here:
    // http://www.cs.berkeley.edu/~jrs/meshpapers/robnotes.pdf
    // https://www.cs.cmu.edu/~quake/robust.html
    // Note that this one does not yet have the floating point tricks to be exact!
    fun orient(ax: Double, ay: Double, bx: Double, by: Double, cx: Double, cy: Double): Int {
        val v1 = (bx - ax) * (cy - ay)
        val v2 = (cx - ax) * (by - ay)
        if (v1 > v2) {
            return 1
        } else if (v1 < v2) {
            return -1
        } else {
            return 0
        }
    }

    /** uses orient method to compute whether two line segments cross  */
    fun lineCrossesLine(
        a1x: Double,
        a1y: Double,
        b1x: Double,
        b1y: Double,
        a2x: Double,
        a2y: Double,
        b2x: Double,
        b2y: Double
    ): Boolean {
        return orient(a2x, a2y, b2x, b2y, a1x, a1y) * orient(a2x, a2y, b2x, b2y, b1x, b1y) < 0
                && orient(a1x, a1y, b1x, b1y, a2x, a2y) * orient(a1x, a1y, b1x, b1y, b2x, b2y) < 0
    }

    /** uses orient method to compute whether two line overlap each other  */
    fun lineOverlapLine(
        a1x: Double,
        a1y: Double,
        b1x: Double,
        b1y: Double,
        a2x: Double,
        a2y: Double,
        b2x: Double,
        b2y: Double
    ): Boolean {
        return orient(a2x, a2y, b2x, b2y, a1x, a1y) == 0 && orient(a2x, a2y, b2x, b2y, b1x, b1y) == 0 && orient(
            a1x,
            a1y,
            b1x,
            b1y,
            a2x,
            a2y
        ) == 0 && orient(a1x, a1y, b1x, b1y, b2x, b2y) == 0
    }

    /**
     * uses orient method to compute whether two line segments cross; boundaries included - returning
     * true for lines that terminate on each other.
     *
     *
     * e.g., (plus sign) + == true, and (capital 't') T == true
     *
     *
     * Use [.lineCrossesLine] to exclude lines that terminate on each other from the truth
     * table
     */
    fun lineCrossesLineWithBoundary(
        a1x: Double,
        a1y: Double,
        b1x: Double,
        b1y: Double,
        a2x: Double,
        a2y: Double,
        b2x: Double,
        b2y: Double
    ): Boolean {
        if (orient(a2x, a2y, b2x, b2y, a1x, a1y) * orient(a2x, a2y, b2x, b2y, b1x, b1y) <= 0
            && orient(a1x, a1y, b1x, b1y, a2x, a2y) * orient(a1x, a1y, b1x, b1y, b2x, b2y) <= 0
        ) {
            return true
        }
        return false
    }

    /**
     * used to define the orientation of 3 points -1 = Clockwise 0 = Colinear 1 = Counter-clockwise
     */
    enum class WindingOrder(private val sign: Int) {
        CW(-1),
        COLINEAR(0),
        CCW(1);

        fun sign(): Int {
            return sign
        }

        companion object {
            fun fromSign(sign: Int): WindingOrder {
                if (sign == CW.sign) return CW
                if (sign == COLINEAR.sign) return COLINEAR
                if (sign == CCW.sign) return CCW
                throw IllegalArgumentException("Invalid WindingOrder sign: $sign")
            }
        }
    }
}
