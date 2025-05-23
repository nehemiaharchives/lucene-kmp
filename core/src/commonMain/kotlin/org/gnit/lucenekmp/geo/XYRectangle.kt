package org.gnit.lucenekmp.geo

import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.compare
import org.gnit.lucenekmp.jdkport.floatToIntBits
import org.gnit.lucenekmp.jdkport.isFinite
import kotlin.math.max
import kotlin.math.min

/** Represents a x/y cartesian rectangle.  */
class XYRectangle(minX: Float, maxX: Float, minY: Float, maxY: Float) : XYGeometry() {
    /** minimum x value  */
    val minX: Float

    /** minimum y value  */
    val maxX: Float

    /** maximum x value  */
    val minY: Float

    /** maximum y value  */
    val maxY: Float

    /** Constructs a bounding box by first validating the provided x and y coordinates  */
    init {
        require(!(minX > maxX)) { "minX must be lower than maxX, got $minX > $maxX" }
        require(!(minY > maxY)) { "minY must be lower than maxY, got $minY > $maxY" }
        this.minX = XYEncodingUtils.checkVal(minX)
        this.maxX = XYEncodingUtils.checkVal(maxX)
        this.minY = XYEncodingUtils.checkVal(minY)
        this.maxY = XYEncodingUtils.checkVal(maxY)
    }

    override fun toComponent2D(): Component2D {
        return Rectangle2D.create(this)
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || this::class != o::class) return false

        val rectangle = o as XYRectangle

        if (Float.compare(rectangle.minX, minX) != 0) return false
        if (Float.compare(rectangle.minY, minY) != 0) return false
        if (Float.compare(rectangle.maxX, maxX) != 0) return false
        return Float.compare(rectangle.maxY, maxY) == 0
    }

    override fun hashCode(): Int {
        var result: Int
        var temp: Long
        temp = Float.floatToIntBits(minX).toLong()
        result = (temp xor (temp ushr 32)).toInt()
        temp = Float.floatToIntBits(minY).toLong()
        result = 31 * result + (temp xor (temp ushr 32)).toInt()
        temp = Float.floatToIntBits(maxX).toLong()
        result = 31 * result + (temp xor (temp ushr 32)).toInt()
        temp = Float.floatToIntBits(maxY).toLong()
        result = 31 * result + (temp xor (temp ushr 32)).toInt()
        return result
    }

    override fun toString(): String {
        val b = StringBuilder()
        b.append("XYRectangle(x=")
        b.append(minX)
        b.append(" TO ")
        b.append(maxX)
        b.append(" y=")
        b.append(minY)
        b.append(" TO ")
        b.append(maxY)
        b.append(")")

        return b.toString()
    }

    companion object {
        /** Compute Bounding Box for a circle in cartesian geometry  */
        fun fromPointDistance(x: Float, y: Float, radius: Float): XYRectangle {
            XYEncodingUtils.checkVal(x)
            XYEncodingUtils.checkVal(y)
            require(!(radius < 0)) { "radius must be bigger than 0, got $radius" }
            require(Float.isFinite(radius) != false) { "radius must be finite, got $radius" }
            // LUCENE-9243: We round up the bounding box to avoid
            // numerical errors.
            val distanceBox = Math.nextUp(radius)
            val minX = max(-Float.Companion.MAX_VALUE, x - distanceBox)
            val maxX = min(Float.Companion.MAX_VALUE, x + distanceBox)
            val minY = max(-Float.Companion.MAX_VALUE, y - distanceBox)
            val maxY = min(Float.Companion.MAX_VALUE, y + distanceBox)
            return XYRectangle(minX, maxX, minY, maxY)
        }
    }
}
