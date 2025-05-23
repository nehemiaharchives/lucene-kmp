package org.gnit.lucenekmp.geo

import kotlin.math.max
import kotlin.math.min

/**
 * Represents a line on the earth's surface. You can construct the Line directly with `double[]` coordinates.
 *
 *
 * NOTES:
 *
 *
 *  1. All latitude/longitude values must be in decimal degrees.
 *  1. For more advanced GeoSpatial indexing and query operations see the `spatial-extras`
 * module
 *
 */
class Line(lats: DoubleArray, lons: DoubleArray) : LatLonGeometry() {
    /** array of latitude coordinates  */
    private val lats: DoubleArray

    /** array of longitude coordinates  */
    private val lons: DoubleArray

    /** minimum latitude of this line's bounding box  */
    val minLat: Double

    /** maximum latitude of this line's bounding box  */
    val maxLat: Double

    /** minimum longitude of this line's bounding box  */
    val minLon: Double

    /** maximum longitude of this line's bounding box  */
    val maxLon: Double

    /** Creates a new Line from the supplied latitude/longitude array.  */
    init {
        requireNotNull(lats) { "lats must not be null" }
        requireNotNull(lons) { "lons must not be null" }
        require(lats.size == lons.size) { "lats and lons must be equal length" }
        require(lats.size >= 2) { "at least 2 line points required" }

        // compute bounding box
        var minLat = lats[0]
        var minLon = lons[0]
        var maxLat = lats[0]
        var maxLon = lons[0]
        for (i in lats.indices) {
            GeoUtils.checkLatitude(lats[i])
            GeoUtils.checkLongitude(lons[i])
            minLat = min(lats[i], minLat)
            minLon = min(lons[i], minLon)
            maxLat = max(lats[i], maxLat)
            maxLon = max(lons[i], maxLon)
        }

        this.lats = lats.copyOf()
        this.lons = lons.copyOf()
        this.minLat = minLat
        this.maxLat = maxLat
        this.minLon = minLon
        this.maxLon = maxLon
    }

    /** returns the number of vertex points  */
    fun numPoints(): Int {
        return lats.size
    }

    /** Returns latitude value at given index  */
    fun getLat(vertex: Int): Double {
        return lats[vertex]
    }

    /** Returns longitude value at given index  */
    fun getLon(vertex: Int): Double {
        return lons[vertex]
    }

    /** Returns a copy of the internal latitude array  */
    fun getLats(): DoubleArray {
        return lats.copyOf()
    }

    /** Returns a copy of the internal longitude array  */
    fun getLons(): DoubleArray {
        return lons.copyOf()
    }

    override fun toComponent2D(): Component2D {
        return Line2D.create(this)
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o !is Line) return false
        val line = o
        return lats.contentEquals(line.lats) && lons.contentEquals(line.lons)
    }

    override fun hashCode(): Int {
        var result = lats.contentHashCode()
        result = 31 * result + lons.contentHashCode()
        return result
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("Line(")
        for (i in lats.indices) {
            sb.append("[").append(lons[i]).append(", ").append(lats[i]).append("]")
        }
        sb.append(')')
        return sb.toString()
    }

    /** prints lines as geojson  */
    fun toGeoJSON(): String {
        val sb = StringBuilder()
        sb.append("[")
        sb.append(Polygon.verticesToGeoJSON(lats, lons))
        sb.append("]")
        return sb.toString()
    }
}
