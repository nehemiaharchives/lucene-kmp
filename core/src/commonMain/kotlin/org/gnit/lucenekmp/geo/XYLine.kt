package org.gnit.lucenekmp.geo

import kotlin.math.max
import kotlin.math.min

/**
 * Represents a line in cartesian space. You can construct the Line directly with `float[]`,
 * `float[]` x, y arrays coordinates.
 */
class XYLine(x: FloatArray, y: FloatArray) : XYGeometry() {
    /** array of x coordinates  */
    private val x: FloatArray

    /** array of y coordinates  */
    private val y: FloatArray

    /** minimum x of this line's bounding box  */
    val minX: Float

    /** maximum y of this line's bounding box  */
    val maxX: Float

    /** minimum y of this line's bounding box  */
    val minY: Float

    /** maximum y of this line's bounding box  */
    val maxY: Float

    /** Creates a new Line from the supplied X/Y array.  */
    init {
        requireNotNull(x) { "x must not be null" }
        requireNotNull(y) { "y must not be null" }
        require(x.size == y.size) { "x and y must be equal length" }
        require(x.size >= 2) { "at least 2 line points required" }

        // compute bounding box
        var minX = Float.Companion.MAX_VALUE
        var minY = Float.Companion.MAX_VALUE
        var maxX = -Float.Companion.MAX_VALUE
        var maxY = -Float.Companion.MAX_VALUE
        for (i in x.indices) {
            minX = min(XYEncodingUtils.checkVal(x[i]), minX)
            minY = min(XYEncodingUtils.checkVal(y[i]), minY)
            maxX = max(x[i], maxX)
            maxY = max(y[i], maxY)
        }
        this.x = x.copyOf()
        this.y = y.copyOf()

        this.minX = minX
        this.maxX = maxX
        this.minY = minY
        this.maxY = maxY
    }

    /** returns the number of vertex points  */
    fun numPoints(): Int {
        return x.size
    }

    /** Returns x value at given index  */
    fun getX(vertex: Int): Float {
        return x[vertex]
    }

    /** Returns y value at given index  */
    fun getY(vertex: Int): Float {
        return y[vertex]
    }

    /** Returns a copy of the internal x array  */
    fun getX(): FloatArray {
        return x.copyOf()
    }

    /** Returns a copy of the internal y array  */
    fun getY(): FloatArray {
        return y.copyOf()
    }

    override fun toComponent2D(): Component2D {
        return Line2D.create(this)
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o !is XYLine) return false
        val line = o
        return x.contentEquals(line.x) && y.contentEquals(line.y)
    }

    override fun hashCode(): Int {
        var result = x.contentHashCode()
        result = 31 * result + y.contentHashCode()
        return result
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("XYLine(")
        for (i in x.indices) {
            sb.append("[").append(x[i]).append(", ").append(y[i]).append("]")
        }
        sb.append(')')
        return sb.toString()
    }
}
