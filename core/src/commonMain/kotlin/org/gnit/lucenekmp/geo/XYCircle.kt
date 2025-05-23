package org.gnit.lucenekmp.geo

import org.gnit.lucenekmp.jdkport.compare
import org.gnit.lucenekmp.jdkport.isFinite

/**
 * Represents a circle on the XY plane.
 *
 *
 * NOTES:
 *
 *
 *  1. X/Y precision is float.
 *  1. Radius precision is float.
 *
 *
 * @lucene.experimental
 */
class XYCircle(x: Float, y: Float, radius: Float) : XYGeometry() {
    /** Returns the center's x  */
    /** Center x  */
    val x: Float

    /** Returns the center's y  */
    /** Center y  */
    val y: Float

    /** Returns the radius  */
    /** radius  */
    val radius: Float

    /** Creates a new circle from the supplied x/y center and radius.  */
    init {
        require(!(radius <= 0)) { "radius must be bigger than 0, got $radius" }
        require(Float.isFinite(radius) != false) { "radius must be finite, got $radius" }
        this.x = XYEncodingUtils.checkVal(x)
        this.y = XYEncodingUtils.checkVal(y)
        this.radius = radius
    }

    override fun toComponent2D(): Component2D {
        return Circle2D.create(this)
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o !is XYCircle) return false
        val circle = o
        return Float.compare(x, circle.x) == 0 && Float.compare(
            y,
            circle.y
        ) == 0 && Float.compare(radius, circle.radius) == 0
    }

    override fun hashCode(): Int {
        var result = x.hashCode()
        result = 31 * result + y.hashCode()
        result = 31 * result + radius.hashCode()
        return result
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("XYCircle(")
        sb.append("[$x,$y]")
        sb.append(" radius = $radius")
        sb.append(')')
        return sb.toString()
    }
}
