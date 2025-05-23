package org.gnit.lucenekmp.geo

import org.gnit.lucenekmp.jdkport.compare
import org.gnit.lucenekmp.jdkport.isFinite

/**
 * Represents a circle on the earth's surface.
 *
 *
 * NOTES:
 *
 *
 *  1. Latitude/longitude values must be in decimal degrees.
 *  1. Radius must be in meters.
 *  1. For more advanced GeoSpatial indexing and query operations see the `spatial-extras`
 * module
 *
 *
 * @lucene.experimental
 */
class Circle(lat: Double, lon: Double, radiusMeters: Double) : LatLonGeometry() {
    /** Returns the center's latitude  */
    /** Center latitude  */
    val lat: Double

    /** Returns the center's longitude  */
    /** Center longitude  */
    val lon: Double

    /** Returns the radius in meters  */
    /** radius in meters  */
    val radius: Double

    /** Creates a new circle from the supplied latitude/longitude center and a radius in meters..  */
    init {
        GeoUtils.checkLatitude(lat)
        GeoUtils.checkLongitude(lon)
        require(!(Double.isFinite(radiusMeters) == false || radiusMeters < 0)) { "radiusMeters: '$radiusMeters' is invalid" }
        this.lat = lat
        this.lon = lon
        this.radius = radiusMeters
    }

    override fun toComponent2D(): Component2D {
        return Circle2D.create(this)
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o !is Circle) return false
        val circle = o
        return Double.compare(lat, circle.lat) == 0 && Double.compare(
            lon,
            circle.lon
        ) == 0 && Double.compare(
            this.radius, circle.radius
        ) == 0
    }

    override fun hashCode(): Int {
        var result = lat.hashCode()
        result = 31 * result + lon.hashCode()
        result = 31 * result + this.radius.hashCode()
        return result
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("Circle(")
        sb.append("[$lat,$lon]")
        sb.append(" radius = " + this.radius + " meters")
        sb.append(')')
        return sb.toString()
    }
}
