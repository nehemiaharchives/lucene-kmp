package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.geo.XYCircle
import org.gnit.lucenekmp.geo.XYEncodingUtils
import org.gnit.lucenekmp.geo.XYGeometry
import org.gnit.lucenekmp.geo.XYPolygon
import org.gnit.lucenekmp.geo.XYRectangle
import org.gnit.lucenekmp.index.DocValuesType
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.search.FieldDoc
import org.gnit.lucenekmp.search.IndexOrDocValuesQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.SortField

/**
 * An per-document location field.
 *
 *
 * Sorting by distance is efficient. Multiple values for the same field in one document is
 * allowed.
 *
 *
 * This field defines static factory methods for common operations:
 *
 *
 *  * [newSlowBoxQuery()][.newSlowBoxQuery] for matching points within a bounding box.
 *  * [newSlowDistanceQuery()][.newSlowDistanceQuery] for matching points within a specified
 * distance.
 *  * [newSlowPolygonQuery()][.newSlowPolygonQuery] for matching points within an arbitrary
 * polygon.
 *  * [newSlowGeometryQuery()][.newSlowGeometryQuery] for matching points within an
 * arbitrary geometry.
 *  * [newDistanceSort()][.newDistanceSort] for ordering documents by distance from a
 * specified location.
 *
 *
 *
 * If you also need query operations, you should add a separate [XYPointField] instance. If
 * you also need to store the value, you should add a separate [StoredField] instance.
 *
 * @see XYPointField
 */
class XYDocValuesField(name: String, x: Float, y: Float) : Field(name, TYPE) {
    /**
     * Creates a new XYDocValuesField with the specified x and y
     *
     * @param name field name
     * @param x x value.
     * @param y y values.
     * @throws IllegalArgumentException if the field name is null or x or y are infinite or NaN.
     */
    init {
        setLocationValue(x, y)
    }

    /**
     * Change the values of this field
     *
     * @param x x value.
     * @param y y value.
     * @throws IllegalArgumentException if x or y are infinite or NaN.
     */
    fun setLocationValue(x: Float, y: Float) {
        val xEncoded: Int = XYEncodingUtils.encode(x)
        val yEncoded: Int = XYEncodingUtils.encode(y)
        fieldsData = ((xEncoded.toLong()) shl 32) or (yEncoded.toLong() and 0xFFFFFFFFL)
    }

    override fun toString(): String {
        val result = StringBuilder()
        result.append(this::class.simpleName)
        result.append(" <")
        result.append(name)
        result.append(':')

        val currentValue = fieldsData as Long
        result.append(XYEncodingUtils.decode((currentValue shr 32).toInt()))
        result.append(',')
        result.append(XYEncodingUtils.decode((currentValue and 0xFFFFFFFFL).toInt()))

        result.append('>')
        return result.toString()
    }

    companion object {
        /**
         * Type for a XYDocValuesField
         *
         *
         * Each value stores a 64-bit long where the upper 32 bits are the encoded x value, and the
         * lower 32 bits are the encoded y value.
         *
         * @see XYEncodingUtils.decode
         */
        val TYPE: FieldType = FieldType()

        init {
            TYPE.setDocValuesType(DocValuesType.SORTED_NUMERIC)
            TYPE.freeze()
        }

        /** helper: checks a fieldinfo and throws exception if its definitely not a XYDocValuesField  */
        fun checkCompatible(fieldInfo: FieldInfo) {
            // dv properties could be "unset", if you e.g. used only StoredField with this same name in the
            // segment.
            require(
                !(fieldInfo.docValuesType != DocValuesType.NONE
                        && fieldInfo.docValuesType != TYPE.docValuesType())
            ) {
                ("field=\""
                        + fieldInfo.name
                        + "\" was indexed with docValuesType="
                        + fieldInfo.docValuesType
                        + " but this type has docValuesType="
                        + TYPE.docValuesType()
                        + ", is the field really a XYDocValuesField")
            }
        }

        /**
         * Creates a SortField for sorting by distance from a location.
         *
         *
         * This sort orders documents by ascending distance from the location. The value returned in
         * [FieldDoc] for the hits contains a Double instance with the distance in meters.
         *
         *
         * If a document is missing the field, then by default it is treated as having [ ][Double.POSITIVE_INFINITY] distance (missing values sort last).
         *
         *
         * If a document contains multiple values for the field, the *closest* distance to the
         * location is used.
         *
         * @param field field name. must not be null.
         * @param x x at the center.
         * @param y y at the center.
         * @return SortField ordering documents by distance
         * @throws IllegalArgumentException if `field` is null or location has invalid coordinates.
         */
        fun newDistanceSort(field: String, x: Float, y: Float): SortField {
            return XYPointSortField(field, x, y)
        }

        /**
         * Create a query for matching a bounding box using doc values. This query is usually slow as it
         * does not use an index structure and needs to verify documents one-by-one in order to know
         * whether they match. It is best used wrapped in an [IndexOrDocValuesQuery] alongside a
         * [XYPointField.newBoxQuery].
         */
        fun newSlowBoxQuery(
            field: String?, minX: Float, maxX: Float, minY: Float, maxY: Float
        ): Query {
            val rectangle = XYRectangle(minX, maxX, minY, maxY)
            return XYDocValuesPointInGeometryQuery(field, rectangle)
        }

        /**
         * Create a query for matching points within the specified distance of the supplied location. This
         * query is usually slow as it does not use an index structure and needs to verify documents
         * one-by-one in order to know whether they match. It is best used wrapped in an [ ] alongside a [XYPointField.newDistanceQuery].
         *
         * @param field field name. must not be null.
         * @param x x at the center.
         * @param y y at the center: must be within standard +/-180 coordinate bounds.
         * @param radius maximum distance from the center in cartesian distance: must be non-negative and
         * finite.
         * @return query matching points within this distance
         * @throws IllegalArgumentException if `field` is null, location has invalid coordinates, or
         * radius is invalid.
         */
        fun newSlowDistanceQuery(field: String?, x: Float, y: Float, radius: Float): Query {
            val circle = XYCircle(x, y, radius)
            return XYDocValuesPointInGeometryQuery(field, circle)
        }

        /**
         * Create a query for matching points within the supplied polygons. This query is usually slow as
         * it does not use an index structure and needs to verify documents one-by-one in order to know
         * whether they match. It is best used wrapped in an [IndexOrDocValuesQuery] alongside a
         * [XYPointField.newPolygonQuery].
         *
         * @param field field name. must not be null.
         * @param polygons array of polygons. must not be null or empty.
         * @return query matching points within the given polygons.
         * @throws IllegalArgumentException if `field` is null or polygons is empty or contain a
         * null polygon.
         */
        fun newSlowPolygonQuery(field: String?, vararg polygons: XYPolygon): Query {
            return newSlowGeometryQuery(field, *polygons)
        }

        /**
         * Create a query for matching points within the supplied geometries. XYLine geometries are not
         * supported. This query is usually slow as it does not use an index structure and needs to verify
         * documents one-by-one in order to know whether they match. It is best used wrapped in an [ ] alongside a [XYPointField.newGeometryQuery].
         *
         * @param field field name. must not be null.
         * @param geometries array of XY geometries. must not be null or empty.
         * @return query matching points within the given geometries.
         * @throws IllegalArgumentException if `field` is null, `polygons` is null, empty or
         * contains a null or XYLine geometry.
         */
        fun newSlowGeometryQuery(field: String?, vararg geometries: XYGeometry): Query {
            return XYDocValuesPointInGeometryQuery(field, *geometries)
        }
    }
}
