package org.gnit.lucenekmp.geo

import org.gnit.lucenekmp.geo.GeoUtils.WindingOrder;
import org.gnit.lucenekmp.jdkport.ParseException
import kotlin.math.max
import kotlin.math.min

/**
 * Represents a closed polygon on the earth's surface. You can either construct the Polygon directly
 * yourself with `double[]` coordinates, or use [Polygon.fromGeoJSON] if you have a
 * polygon already encoded as a [GeoJSON](http://geojson.org/geojson-spec.html) string.
 *
 *
 * NOTES:
 *
 *
 *  1. Coordinates must be in clockwise order, except for holes. Holes must be in
 * counter-clockwise order.
 *  1. The polygon must be closed: the first and last coordinates need to have the same values.
 *  1. The polygon must not be self-crossing, otherwise may result in unexpected behavior.
 *  1. All latitude/longitude values must be in decimal degrees.
 *  1. Polygons cannot cross the 180th meridian. Instead, use two polygons: one on each side.
 *  1. For more advanced GeoSpatial indexing and query operations see the `spatial-extras`
 * module
 *
 *
 * @lucene.experimental
 */
class Polygon(polyLats: DoubleArray, polyLons: DoubleArray, vararg holes: Polygon) :
    LatLonGeometry() {
    private val polyLats: DoubleArray
    private val polyLons: DoubleArray
    private val holes: Array<Polygon>

    /** minimum latitude of this polygon's bounding box area  */
    val minLat: Double

    /** maximum latitude of this polygon's bounding box area  */
    val maxLat: Double

    /** minimum longitude of this polygon's bounding box area  */
    val minLon: Double

    /** maximum longitude of this polygon's bounding box area  */
    val maxLon: Double

    /** winding order of the vertices  */
    private val windingOrder: GeoUtils.WindingOrder

    /** Creates a new Polygon from the supplied latitude/longitude array, and optionally any holes.  */
    init {
        requireNotNull(polyLats) { "polyLats must not be null" }
        requireNotNull(polyLons) { "polyLons must not be null" }
        requireNotNull(holes) { "holes must not be null" }
        require(polyLats.size == polyLons.size) { "polyLats and polyLons must be equal length" }
        require(polyLats.size >= 4) { "at least 4 polygon points required" }
        require(polyLats[0] == polyLats[polyLats.size - 1]) {
            ("first and last points of the polygon must be the same (it must close itself): polyLats[0]="
                    + polyLats[0]
                    + " polyLats["
                    + (polyLats.size - 1)
                    + "]="
                    + polyLats[polyLats.size - 1])
        }
        require(polyLons[0] == polyLons[polyLons.size - 1]) {
            ("first and last points of the polygon must be the same (it must close itself): polyLons[0]="
                    + polyLons[0]
                    + " polyLons["
                    + (polyLons.size - 1)
                    + "]="
                    + polyLons[polyLons.size - 1])
        }
        for (i in polyLats.indices) {
            GeoUtils.checkLatitude(polyLats[i])
            GeoUtils.checkLongitude(polyLons[i])
        }
        for (i in holes.indices) {
            val inner: Polygon = holes[i]
            require(inner.holes.isEmpty()) { "holes may not contain holes: polygons may not nest." }
        }
        this.polyLats = polyLats.copyOf()
        this.polyLons = polyLons.copyOf()
        this.holes = (holes as Array<Polygon>).copyOf()

        // compute bounding box
        var minLat = polyLats[0]
        var maxLat = polyLats[0]
        var minLon = polyLons[0]
        var maxLon = polyLons[0]

        var windingSum = 0.0
        val numPts = polyLats.size - 1
        var i = 1
        var j = 0
        while (i < numPts) {
            minLat = min(polyLats[i], minLat)
            maxLat = max(polyLats[i], maxLat)
            minLon = min(polyLons[i], minLon)
            maxLon = max(polyLons[i], maxLon)
            // compute signed area
            windingSum +=
                ((polyLons[j] - polyLons[numPts]) * (polyLats[i] - polyLats[numPts])
                        - (polyLats[j] - polyLats[numPts]) * (polyLons[i] - polyLons[numPts]))
            j = i++
        }
        this.minLat = minLat
        this.maxLat = maxLat
        this.minLon = minLon
        this.maxLon = maxLon
        this.windingOrder =
            if (windingSum < 0) WindingOrder.CCW else WindingOrder.CW
    }

    /** returns the number of vertex points  */
    fun numPoints(): Int {
        return polyLats.size
    }

    /** Returns a copy of the internal latitude array  */
    fun getPolyLats(): DoubleArray {
        return polyLats.copyOf()
    }

    /** Returns latitude value at given index  */
    fun getPolyLat(vertex: Int): Double {
        return polyLats[vertex]
    }

    /** Returns a copy of the internal longitude array  */
    fun getPolyLons(): DoubleArray {
        return polyLons.copyOf()
    }

    /** Returns longitude value at given index  */
    fun getPolyLon(vertex: Int): Double {
        return polyLons[vertex]
    }

    /** Returns a copy of the internal holes array  */
    fun getHoles(): Array<Polygon> {
        return holes.copyOf()
    }

    fun getHole(i: Int): Polygon {
        return holes[i]
    }

    /** Returns the winding order (CW, COLINEAR, CCW) for the polygon shell  */
    fun getWindingOrder(): WindingOrder {
        return this.windingOrder
    }

    /** returns the number of holes for the polygon  */
    fun numHoles(): Int {
        return holes.size
    }

    override fun toComponent2D(): Component2D {
        return Polygon2D.create(this)
    }

    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        result = prime * result + holes.contentHashCode()
        result = prime * result + polyLats.contentHashCode()
        result = prime * result + polyLons.contentHashCode()
        return result
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) return true
        if (obj == null) return false
        if (this::class != obj::class) return false
        val other = obj as Polygon
        if (!holes.contentEquals(other.holes)) return false
        if (!polyLats.contentEquals(other.polyLats)) return false
        if (!polyLons.contentEquals(other.polyLons)) return false
        return true
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("Polygon")
        for (i in polyLats.indices) {
            sb.append("[").append(polyLats[i]).append(", ").append(polyLons[i]).append("] ")
        }
        if (holes.isNotEmpty()) {
            sb.append(", holes=")
            sb.append(holes.contentToString())
        }
        return sb.toString()
    }

    /** prints polygons as geojson  */
    fun toGeoJSON(): String {
        val sb = StringBuilder()
        sb.append("[")
        sb.append(verticesToGeoJSON(polyLats, polyLons))
        for (hole in holes) {
            sb.append(",")
            sb.append(verticesToGeoJSON(hole.polyLats, hole.polyLons))
        }
        sb.append("]")
        return sb.toString()
    }

    companion object {
        fun verticesToGeoJSON(lats: DoubleArray, lons: DoubleArray): String {
            val sb = StringBuilder()
            sb.append('[')
            for (i in lats.indices) {
                sb.append("[").append(lons[i]).append(", ").append(lats[i]).append("]")
                if (i != lats.size - 1) {
                    sb.append(", ")
                }
            }
            sb.append(']')
            return sb.toString()
        }

        /**
         * Parses a standard GeoJSON polygon string. The type of the incoming GeoJSON object must be a
         * Polygon or MultiPolygon, optionally embedded under a "type: Feature". A Polygon will return as
         * a length 1 array, while a MultiPolygon will be 1 or more in length.
         *
         *
         * See [the GeoJSON specification](http://geojson.org/geojson-spec.html).
         */
        @Throws(ParseException::class)
        fun fromGeoJSON(geojson: String): Array<Polygon>? {
            return SimpleGeoJSONPolygonParser(geojson).parse()
        }
    }
}
