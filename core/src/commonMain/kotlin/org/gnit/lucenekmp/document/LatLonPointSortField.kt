package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.geo.GeoUtils
import org.gnit.lucenekmp.jdkport.doubleToLongBits
import org.gnit.lucenekmp.search.FieldComparator
import org.gnit.lucenekmp.search.Pruning
import org.gnit.lucenekmp.search.SortField


/** Sorts by distance from an origin location.  */
internal class LatLonPointSortField(field: String, latitude: Double, longitude: Double) : SortField(field, Type.CUSTOM) {
    val latitude: Double
    val longitude: Double
    private var missingValueObject: Any? = Double.POSITIVE_INFINITY

    init {
        requireNotNull(field) { "field must not be null" }
        GeoUtils.checkLatitude(latitude)
        GeoUtils.checkLongitude(longitude)
        this.latitude = latitude
        this.longitude = longitude
    }

    override fun getComparator(numHits: Int, pruning: Pruning): FieldComparator<*> {
        return LatLonPointDistanceComparator(field!!, latitude, longitude, numHits)
    }

    override var missingValue: Any?
        get() = missingValueObject
        set(missingValue) {
            require(Double.POSITIVE_INFINITY.equals(missingValue) != false) {
                ("Missing value can only be Double.POSITIVE_INFINITY (missing values last), but got "
                        + missingValue)
            }
            missingValueObject = missingValue
        }

    override fun hashCode(): Int {
        val prime = 31
        var result = super.hashCode()
        var temp: Long = Double.doubleToLongBits(latitude)
        result = prime * result + (temp xor (temp ushr 32)).toInt()
        temp = Double.doubleToLongBits(longitude)
        result = prime * result + (temp xor (temp ushr 32)).toInt()
        return result
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) return true
        if (!super.equals(obj)) return false
        if (this::class != obj!!::class) return false
        val other = obj as LatLonPointSortField
        if (Double.doubleToLongBits(latitude) != Double.doubleToLongBits(other.latitude)) return false
        if (Double.doubleToLongBits(longitude) != Double.doubleToLongBits(other.longitude)) return false
        return true
    }

    override fun toString(): String {
        val builder = StringBuilder()
        builder.append("<distance:")
        builder.append('"')
        builder.append(field)
        builder.append('"')
        builder.append(" latitude=")
        builder.append(latitude)
        builder.append(" longitude=")
        builder.append(longitude)
        if (Double.POSITIVE_INFINITY != this.missingValue) {
            builder.append(" missingValue=").append(this.missingValue)
        }
        builder.append('>')
        return builder.toString()
    }
}
