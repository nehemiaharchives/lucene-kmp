package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.jdkport.floatToIntBits
import org.gnit.lucenekmp.search.FieldComparator
import org.gnit.lucenekmp.search.Pruning
import org.gnit.lucenekmp.search.SortField


/** Sorts by distance from an origin location.  */
internal class XYPointSortField(field: String, x: Float, y: Float) : SortField(field, Type.CUSTOM) {
    val x: Float
    val y: Float

    init {
        requireNotNull(field) { "field must not be null" }
        this.x = x
        this.y = y
        //this.missingValue = Double.POSITIVE_INFINITY
    }

    override fun getComparator(numHits: Int, pruning: Pruning): FieldComparator<*> {
        return XYPointDistanceComparator(field!!, x, y, numHits)
    }

    override var missingValue: Any? = Double.POSITIVE_INFINITY
        get() = super.missingValue as Double
        set(missingValue) {
            require(Double.POSITIVE_INFINITY.equals(missingValue) != false) {
                ("Missing value can only be Double.POSITIVE_INFINITY (missing values last), but got "
                        + missingValue)
            }
            field = missingValue
        }

    override fun hashCode(): Int {
        val prime = 31
        var result = super.hashCode()
        var temp: Long = Float.floatToIntBits(x).toLong()
        result = prime * result + (temp xor (temp ushr 32)).toInt()
        temp = Float.floatToIntBits(y).toLong()
        result = prime * result + (temp xor (temp ushr 32)).toInt()
        return result
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) return true
        if (!super.equals(obj)) return false
        if (this::class != obj!!::class) return false
        val other = obj as XYPointSortField
        if (x != other.x || y != other.y) return false
        return true
    }

    override fun toString(): String {
        val builder = StringBuilder()
        builder.append("<distance:")
        builder.append('"')
        builder.append(field)
        builder.append('"')
        builder.append(" x=")
        builder.append(x)
        builder.append(" y=")
        builder.append(y)
        if (Double.POSITIVE_INFINITY != this.missingValue) {
            builder.append(" missingValue=").append(this.missingValue)
        }
        builder.append('>')
        return builder.toString()
    }
}
