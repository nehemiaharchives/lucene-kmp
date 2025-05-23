package org.gnit.lucenekmp.geo

import org.gnit.lucenekmp.jdkport.compare


/**
 * Represents a point on the earth's surface. You can construct the point directly with `double` coordinates.
 *
 *
 * NOTES:
 *
 *
 *  1. latitude/longitude values must be in decimal degrees.
 *  1. For more advanced GeoSpatial indexing and query operations see the `spatial-extras`
 * module
 *
 */
class Point(lat: Double, lon: Double) : LatLonGeometry() {
    /** Returns latitude value at given index  */
    /** latitude coordinate  */
    val lat: Double

    /** Returns longitude value at given index  */
    /** longitude coordinate  */
    val lon: Double

    /** Creates a new Point from the supplied latitude/longitude.  */
    init {
        GeoUtils.checkLatitude(lat)
        GeoUtils.checkLongitude(lon)
        this.lat = lat
        this.lon = lon
    }

    override fun toComponent2D(): Component2D {
        return Point2D.create(this)
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o !is Point) return false
        val point = o
        return Double.compare(point.lat, lat) == 0 && Double.compare(point.lon, lon) == 0
    }

    override fun hashCode(): Int {
        var result = lat.hashCode()
        result = 31 * result + lon.hashCode()
        return result
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("Point(")
        sb.append(lon)
        sb.append(",")
        sb.append(lat)
        sb.append(')')
        return sb.toString()
    }
}
