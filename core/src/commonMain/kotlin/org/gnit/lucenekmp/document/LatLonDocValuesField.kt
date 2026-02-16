package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.geo.Circle
import org.gnit.lucenekmp.geo.GeoEncodingUtils.decodeLatitude
import org.gnit.lucenekmp.geo.GeoEncodingUtils.decodeLongitude
import org.gnit.lucenekmp.geo.GeoEncodingUtils.encodeLatitude
import org.gnit.lucenekmp.geo.GeoEncodingUtils.encodeLongitude
import org.gnit.lucenekmp.geo.LatLonGeometry
import org.gnit.lucenekmp.geo.Point
import org.gnit.lucenekmp.geo.Polygon
import org.gnit.lucenekmp.geo.Rectangle
import org.gnit.lucenekmp.index.DocValuesType
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.search.FieldDoc
import org.gnit.lucenekmp.search.IndexOrDocValuesQuery
import org.gnit.lucenekmp.search.MatchNoDocsQuery
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
 *  * [newDistanceSort()][.newDistanceSort] for ordering documents by distance from a
 * specified location.
 *
 *
 *
 * If you also need query operations, you should add a separate [LatLonPoint] instance. If
 * you also need to store the value, you should add a separate [StoredField] instance.
 *
 *
 * **WARNING**: Values are indexed with some loss of precision from the original `double` values (4.190951585769653E-8 for the latitude component and 8.381903171539307E-8 for
 * longitude).
 *
 * @see LatLonPoint
 */
class LatLonDocValuesField(name: String, latitude: Double, longitude: Double) : Field(name, TYPE) {
    /**
     * Creates a new LatLonDocValuesField with the specified latitude and longitude
     *
     * @param name field name
     * @param latitude latitude value: must be within standard +/-90 coordinate bounds.
     * @param longitude longitude value: must be within standard +/-180 coordinate bounds.
     * @throws IllegalArgumentException if the field name is null or latitude or longitude are out of
     * bounds
     */
    init {
        setLocationValue(latitude, longitude)
    }

    /**
     * Change the values of this field
     *
     * @param latitude latitude value: must be within standard +/-90 coordinate bounds.
     * @param longitude longitude value: must be within standard +/-180 coordinate bounds.
     * @throws IllegalArgumentException if latitude or longitude are out of bounds
     */
    fun setLocationValue(latitude: Double, longitude: Double) {
        val latitudeEncoded: Int = encodeLatitude(latitude)
        val longitudeEncoded: Int = encodeLongitude(longitude)
        fieldsData = ((latitudeEncoded.toLong()) shl 32) or (longitudeEncoded.toLong() and 0xFFFFFFFFL)
    }

    override fun toString(): String {
        val result = StringBuilder()
        result.append(this::class.simpleName)
        result.append(" <")
        result.append(name)
        result.append(':')

        val currentValue = fieldsData as Long
        result.append(decodeLatitude((currentValue shr 32).toInt()))
        result.append(',')
        result.append(decodeLongitude((currentValue and 0xFFFFFFFFL).toInt()))

        result.append('>')
        return result.toString()
    }

    companion object {
        /**
         * Type for a LatLonDocValuesField
         *
         *
         * Each value stores a 64-bit long where the upper 32 bits are the encoded latitude, and the
         * lower 32 bits are the encoded longitude.
         *
         * @see decodeLatitude
         * @see decodeLongitude
         */
        val TYPE: FieldType = FieldType()

        init {
            TYPE.setDocValuesType(DocValuesType.SORTED_NUMERIC)
            TYPE.freeze()
        }

        /**
         * helper: checks a fieldinfo and throws exception if its definitely not a LatLonDocValuesField
         */
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
                        + ", is the field really a LatLonDocValuesField")
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
         * @param latitude latitude at the center: must be within standard +/-90 coordinate bounds.
         * @param longitude longitude at the center: must be within standard +/-180 coordinate bounds.
         * @return SortField ordering documents by distance
         * @throws IllegalArgumentException if `field` is null or location has invalid coordinates.
         */
        fun newDistanceSort(field: String, latitude: Double, longitude: Double): SortField {
            return LatLonPointSortField(field, latitude, longitude)
        }

        /**
         * Create a query for matching a bounding box using doc values. This query is usually slow as it
         * does not use an index structure and needs to verify documents one-by-one in order to know
         * whether they match. It is best used wrapped in an [IndexOrDocValuesQuery] alongside a
         * [LatLonPoint.newBoxQuery].
         */
        fun newSlowBoxQuery(
            field: String?,
            minLatitude: Double,
            maxLatitude: Double,
            minLongitude: Double,
            maxLongitude: Double
        ): Query {
            // exact double values of lat=90.0D and lon=180.0D must be treated special as they are not
            // represented in the encoding
            // and should not drag in extra bogus junk! TODO: should encodeCeil just throw
            // ArithmeticException to be less trappy here
            var minLongitude = minLongitude
            if (minLatitude == 90.0) {
                // range cannot match as 90.0 can never exist
                return MatchNoDocsQuery("LatLonDocValuesField.newBoxQuery with minLatitude=90.0")
            }
            if (minLongitude == 180.0) {
                if (maxLongitude == 180.0) {
                    // range cannot match as 180.0 can never exist
                    return MatchNoDocsQuery(
                        "LatLonDocValuesField.newBoxQuery with minLongitude=maxLongitude=180.0"
                    )
                } else if (maxLongitude < minLongitude) {
                    // encodeCeil() with dateline wrapping!
                    minLongitude = -180.0
                }
            }
            return LatLonDocValuesBoxQuery(field, minLatitude, maxLatitude, minLongitude, maxLongitude)
        }

        /**
         * Create a query for matching points within the specified distance of the supplied location. This
         * query is usually slow as it does not use an index structure and needs to verify documents
         * one-by-one in order to know whether they match. It is best used wrapped in an [ ] alongside a [LatLonPoint.newDistanceQuery].
         *
         * @param field field name. must not be null.
         * @param latitude latitude at the center: must be within standard +/-90 coordinate bounds.
         * @param longitude longitude at the center: must be within standard +/-180 coordinate bounds.
         * @param radiusMeters maximum distance from the center in meters: must be non-negative and
         * finite.
         * @return query matching points within this distance
         * @throws IllegalArgumentException if `field` is null, location has invalid coordinates, or
         * radius is invalid.
         */
        fun newSlowDistanceQuery(
            field: String?, latitude: Double, longitude: Double, radiusMeters: Double
        ): Query {
            val circle = Circle(latitude, longitude, radiusMeters)
            return newSlowGeometryQuery(field, ShapeField.QueryRelation.INTERSECTS, circle)
        }

        /**
         * Create a query for matching points within the supplied polygons. This query is usually slow as
         * it does not use an index structure and needs to verify documents one-by-one in order to know
         * whether they match. It is best used wrapped in an [IndexOrDocValuesQuery] alongside a
         * [LatLonPoint.newPolygonQuery].
         *
         * @param field field name. must not be null.
         * @param polygons array of polygons. must not be null or empty.
         * @return query matching points within the given polygons.
         * @throws IllegalArgumentException if `field` is null or polygons is empty or contain a
         * null polygon.
         */
        fun newSlowPolygonQuery(field: String?, vararg polygons: Polygon): Query {
            return newSlowGeometryQuery(field, ShapeField.QueryRelation.INTERSECTS, *polygons)
        }

        /**
         * Create a query for matching one or more geometries against the provided [ ]. Line geometries are not supported for WITHIN relationship. This
         * query is usually slow as it does not use an index structure and needs to verify documents
         * one-by-one in order to know whether they match. It is best used wrapped in an [ ] alongside a [LatLonPoint.newGeometryQuery].
         *
         * @param field field name. must not be null.
         * @param queryRelation The relation the points needs to satisfy with the provided geometries,
         * must not be null.
         * @param latLonGeometries array of LatLonGeometries. must not be null or empty.
         * @return query matching points within the given polygons.
         * @throws IllegalArgumentException if `field` is null, `queryRelation` is null,
         * `latLonGeometries` is null, empty or contain a null or line geometry.
         */
        fun newSlowGeometryQuery(
            field: String?, queryRelation: ShapeField.QueryRelation, vararg latLonGeometries: LatLonGeometry
        ): Query {
            if (queryRelation == ShapeField.QueryRelation.INTERSECTS && latLonGeometries.size == 1 && latLonGeometries[0] is Rectangle) {
                val geometry: LatLonGeometry = latLonGeometries[0]
                val rect: Rectangle = geometry as Rectangle
                return newSlowBoxQuery(field, rect.minLat, rect.maxLat, rect.minLon, rect.maxLon)
            }
            if (queryRelation == ShapeField.QueryRelation.CONTAINS) {
                for (geometry in latLonGeometries) {
                    if ((geometry is Point) == false) {
                        return MatchNoDocsQuery(
                            "Contains LatLonDocValuesField.newSlowGeometryQuery with non-point geometries"
                        )
                    }
                }
            }
            return LatLonDocValuesQuery(field, queryRelation, *latLonGeometries)
        }
    }
}
