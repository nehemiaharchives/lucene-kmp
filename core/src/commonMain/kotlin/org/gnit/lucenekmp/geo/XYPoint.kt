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
class XYPoint(x: Float, y: Float) : XYGeometry() {
    /** Returns latitude value at given index  */
    /** latitude coordinate  */
    val x: Float = XYEncodingUtils.checkVal(x)

    /** Returns longitude value at given index  */
    /** longitude coordinate  */
    val y: Float = XYEncodingUtils.checkVal(y)

    override fun toComponent2D(): Component2D {
        return Point2D.create(this)
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o !is XYPoint) return false
        val point = o
        return Float.compare(point.x, x) == 0 && Float.compare(point.y, y) == 0
    }

    override fun hashCode(): Int {
        var result = x.hashCode()
        result = 31 * result + y.hashCode()
        return result
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("XYPoint(")
        sb.append(x)
        sb.append(",")
        sb.append(y)
        sb.append(')')
        return sb.toString()
    }
}
