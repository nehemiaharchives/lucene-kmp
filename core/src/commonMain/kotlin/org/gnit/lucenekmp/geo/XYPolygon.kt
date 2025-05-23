package org.gnit.lucenekmp.geo

import kotlin.math.max
import kotlin.math.min

/**
 * Represents a polygon in cartesian space. You can construct the Polygon directly with `float[]`, `float[]` x, y arrays coordinates.
 */
class XYPolygon(x: FloatArray, y: FloatArray, vararg holes: XYPolygon) : XYGeometry() {
    private val x: FloatArray
    private val y: FloatArray
    private val holes: Array<XYPolygon>

    /** minimum x of this polygon's bounding box area  */
    val minX: Float

    /** maximum x of this polygon's bounding box area  */
    val maxX: Float

    /** minimum y of this polygon's bounding box area  */
    val minY: Float

    /** maximum y of this polygon's bounding box area  */
    val maxY: Float

    /** winding order of the vertices  */
    private val windingOrder: GeoUtils.WindingOrder

    /** Creates a new Polygon from the supplied x, y arrays, and optionally any holes.  */
    init {
        requireNotNull(x) { "x must not be null" }
        requireNotNull(y) { "y must not be null" }
        requireNotNull(holes) { "holes must not be null" }
        require(x.size == y.size) { "x and y must be equal length" }
        require(x.size >= 4) { "at least 4 polygon points required" }
        require(x[0] == x[x.size - 1]) {
            ("first and last points of the polygon must be the same (it must close itself): x[0]="
                    + x[0]
                    + " x["
                    + (x.size - 1)
                    + "]="
                    + x[x.size - 1])
        }
        require(y[0] == y[y.size - 1]) {
            ("first and last points of the polygon must be the same (it must close itself): y[0]="
                    + y[0]
                    + " y["
                    + (y.size - 1)
                    + "]="
                    + y[y.size - 1])
        }
        for (i in holes.indices) {
            val inner: XYPolygon = holes[i]
            require(inner.holes.isEmpty()) { "holes may not contain holes: polygons may not nest." }
        }
        this.x = x.copyOf()
        this.y = y.copyOf()
        this.holes = (holes as Array<XYPolygon>).copyOf()

        // compute bounding box
        var minX: Float = XYEncodingUtils.checkVal(x[0])
        var maxX = x[0]
        var minY: Float = XYEncodingUtils.checkVal(y[0])
        var maxY = y[0]

        var windingSum = 0.0
        val numPts = x.size - 1
        var i = 1
        var j = 0
        while (i < numPts) {
            minX = min(XYEncodingUtils.checkVal(x[i]), minX)
            maxX = max(x[i], maxX)
            minY = min(XYEncodingUtils.checkVal(y[i]), minY)
            maxY = max(y[i], maxY)
            // compute signed area
            windingSum +=
                (
                        (x[j] - x[numPts]) * (y[i] - y[numPts]) - (y[j] - y[numPts]) * (x[i] - x[numPts])).toDouble()
            j = i++
        }
        this.minX = minX
        this.maxX = maxX
        this.minY = minY
        this.maxY = maxY
        this.windingOrder =
            if (windingSum < 0) GeoUtils.WindingOrder.CCW else GeoUtils.WindingOrder.CW
    }

    /** returns the number of vertex points  */
    fun numPoints(): Int {
        return x.size
    }

    val polyX: FloatArray
        /** Returns a copy of the internal x array  */
        get() = x.copyOf()

    /** Returns x value at given index  */
    fun getPolyX(vertex: Int): Float {
        return x[vertex]
    }

    val polyY: FloatArray
        /** Returns a copy of the internal y array  */
        get() = y.copyOf()

    /** Returns y value at given index  */
    fun getPolyY(vertex: Int): Float {
        return y[vertex]
    }

    /** Returns a copy of the internal holes array  */
    fun getHoles(): Array<XYPolygon> {
        return holes.copyOf()
    }

    fun getHole(i: Int): XYPolygon {
        return holes[i]
    }

    /** Returns the winding order (CW, COLINEAR, CCW) for the polygon shell  */
    fun getWindingOrder(): GeoUtils.WindingOrder {
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
        result = prime * result + x.contentHashCode()
        result = prime * result + y.contentHashCode()
        return result
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) return true
        if (obj == null) return false
        if (this::class != obj::class) return false
        val other = obj as XYPolygon
        if (!holes.contentEquals(other.holes)) return false
        if (!x.contentEquals(other.x)) return false
        if (!y.contentEquals(other.y)) return false
        return true
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("XYPolygon")
        for (i in x.indices) {
            sb.append("[").append(x[i]).append(", ").append(y[i]).append("] ")
        }
        if (holes.isNotEmpty()) {
            sb.append(", holes=")
            sb.append(holes.contentToString())
        }
        return sb.toString()
    }
}
