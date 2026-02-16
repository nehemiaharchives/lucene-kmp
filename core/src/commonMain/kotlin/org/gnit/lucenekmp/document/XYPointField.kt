package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.geo.Polygon
import org.gnit.lucenekmp.geo.XYCircle
import org.gnit.lucenekmp.geo.XYEncodingUtils
import org.gnit.lucenekmp.geo.XYGeometry
import org.gnit.lucenekmp.geo.XYPolygon
import org.gnit.lucenekmp.geo.XYRectangle
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.NumericUtils

/**
 * An indexed XY position field.
 *
 *
 * Finding all documents within a range at search time is efficient. Multiple values for the same
 * field in one document is allowed.
 *
 *
 * This field defines static factory methods for common operations:
 *
 *
 *  * [newBoxQuery()][.newBoxQuery] for matching points within a bounding box.
 *  * [newDistanceQuery()][.newDistanceQuery] for matching points within a specified
 * distance.
 *  * [newPolygonQuery()][.newPolygonQuery] for matching points within an arbitrary polygon.
 *  * [newGeometryQuery()][.newGeometryQuery] for matching points within an arbitrary
 * geometry collection.
 *
 *
 *
 * If you also need per-document operations such as sort by distance, add a separate [ ] instance. If you also need to store the value, you should add a separate [ ] instance.
 *
 * @see PointValues
 *
 * @see XYDocValuesField
 */
class XYPointField(name: String, x: Float, y: Float) : Field(name, TYPE) {
    /**
     * Change the values of this field
     *
     * @param x x value.
     * @param y y value.
     */
    fun setLocationValue(x: Float, y: Float) {
        val bytes: ByteArray
        if (!isFieldsDataInitialized()) {
            bytes = ByteArray(8)
            fieldsData = BytesRef(bytes)
        } else {
            bytes = (fieldsData as BytesRef).bytes
        }
        val xEncoded: Int = XYEncodingUtils.encode(x)
        val yEncoded: Int = XYEncodingUtils.encode(y)
        NumericUtils.intToSortableBytes(xEncoded, bytes, 0)
        NumericUtils.intToSortableBytes(yEncoded, bytes, Int.SIZE_BYTES)
    }

    /**
     * Creates a new XYPoint with the specified x and y
     *
     * @param name field name
     * @param x x value.
     * @param y y value.
     */
    init {
        setLocationValue(x, y)
    }

    override fun toString(): String {
        val result = StringBuilder()
        result.append(this::class.simpleName)
        result.append(" <")
        result.append(name)
        result.append(':')

        val bytes: ByteArray = (fieldsData as BytesRef).bytes
        result.append(XYEncodingUtils.decode(bytes, 0))
        result.append(',')
        result.append(XYEncodingUtils.decode(bytes, Int.SIZE_BYTES))

        result.append('>')
        return result.toString()
    }

    companion object {
        /** XYPoint is encoded as integer values so number of bytes is 4  */
        const val BYTES: Int = Int.SIZE_BYTES

        /**
         * Type for an indexed XYPoint
         *
         *
         * Each point stores two dimensions with 4 bytes per dimension.
         */
        val TYPE: FieldType = FieldType()

        init {
            TYPE.setDimensions(2, Int.SIZE_BYTES)
            TYPE.freeze()
        }

        /** helper: checks a fieldinfo and throws exception if its definitely not a XYPoint  */
        fun checkCompatible(fieldInfo: FieldInfo) {
            // point/dv properties could be "unset", if you e.g. used only StoredField with this same name
            // in the segment.
            require(
                !(fieldInfo.pointDimensionCount != 0
                        && fieldInfo.pointDimensionCount != TYPE.pointDimensionCount())
            ) {
                ("field=\""
                        + fieldInfo.name
                        + "\" was indexed with numDims="
                        + fieldInfo.pointDimensionCount
                        + " but this point type has numDims="
                        + TYPE.pointDimensionCount()
                        + ", is the field really a XYPoint")
            }
            require(!(fieldInfo.pointNumBytes != 0 && fieldInfo.pointNumBytes != TYPE.pointNumBytes())) {
                ("field=\""
                        + fieldInfo.name
                        + "\" was indexed with bytesPerDim="
                        + fieldInfo.pointNumBytes
                        + " but this point type has bytesPerDim="
                        + TYPE.pointNumBytes()
                        + ", is the field really a XYPoint")
            }
        }

        // static methods for generating queries
        /**
         * Create a query for matching a bounding box.
         *
         * @param field field name. must not be null.
         * @param minX x lower bound.
         * @param maxX x upper bound.
         * @param minY y lower bound.
         * @param maxY y upper bound.
         * @return query matching points within this box
         * @throws IllegalArgumentException if `field` is null, or the box has invalid coordinates.
         */
        fun newBoxQuery(field: String?, minX: Float, maxX: Float, minY: Float, maxY: Float): Query {
            val rectangle = XYRectangle(minX, maxX, minY, maxY)
            return XYPointInGeometryQuery(field, rectangle)
        }

        /**
         * Create a query for matching points within the specified distance of the supplied location.
         *
         * @param field field name. must not be null.
         * @param x x at the center.
         * @param y y at the center.
         * @param radius maximum distance from the center in cartesian units: must be non-negative and
         * finite.
         * @return query matching points within this distance
         * @throws IllegalArgumentException if `field` is null, location has invalid coordinates, or
         * radius is invalid.
         */
        fun newDistanceQuery(field: String?, x: Float, y: Float, radius: Float): Query {
            val circle = XYCircle(x, y, radius)
            return XYPointInGeometryQuery(field, circle)
        }

        /**
         * Create a query for matching one or more polygons.
         *
         * @param field field name. must not be null.
         * @param polygons array of polygons. must not be null or empty
         * @return query matching points within this polygon
         * @throws IllegalArgumentException if `field` is null, `polygons` is null or empty
         * @see Polygon
         */
        fun newPolygonQuery(field: String?, vararg polygons: XYPolygon): Query {
            return newGeometryQuery(field, *polygons)
        }

        /**
         * create a query to find all indexed shapes that intersect a provided geometry collection. XYLine
         * geometries are not supported.
         *
         * @param field field name. must not be null.
         * @param xyGeometries array of geometries. must not be null or empty.
         * @return query matching points within this geometry collection.
         * @throws IllegalArgumentException if `field` is null, `polygons` is null, empty or
         * contains a null or XYLine geometry.
         * @see XYGeometry
         */
        fun newGeometryQuery(field: String?, vararg xyGeometries: XYGeometry): Query {
            return XYPointInGeometryQuery(field, *xyGeometries)
        }
    }
}
