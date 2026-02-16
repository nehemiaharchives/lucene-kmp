package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.document.ShapeField.QueryRelation
import org.gnit.lucenekmp.geo.Circle
import org.gnit.lucenekmp.geo.GeoEncodingUtils
import org.gnit.lucenekmp.geo.GeoUtils
import org.gnit.lucenekmp.geo.LatLonGeometry
import org.gnit.lucenekmp.geo.Point
import org.gnit.lucenekmp.geo.Polygon
import org.gnit.lucenekmp.geo.Rectangle
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.internal.hppc.IntArrayList
import org.gnit.lucenekmp.search.BooleanClause
import org.gnit.lucenekmp.search.BooleanClause.Occur
import org.gnit.lucenekmp.search.BooleanQuery
import org.gnit.lucenekmp.search.BoostQuery
import org.gnit.lucenekmp.search.ConstantScoreQuery
import org.gnit.lucenekmp.search.FieldDoc
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.MatchNoDocsQuery
import org.gnit.lucenekmp.search.PointRangeQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.ScoreDoc
import org.gnit.lucenekmp.search.TopFieldDocs
import org.gnit.lucenekmp.search.TotalHits
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.NumericUtils
import org.gnit.lucenekmp.util.SloppyMath
import okio.IOException

/**
 * An indexed location field.
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
 * If you also need per-document operations such as sort by distance, add a separate [ ] instance. If you also need to store the value, you should add a separate
 * [StoredField] instance.
 *
 *
 * **WARNING**: Values are indexed with some loss of precision from the original `double` values (4.190951585769653E-8 for the latitude component and 8.381903171539307E-8 for
 * longitude).
 *
 * @see PointValues
 *
 * @see LatLonDocValuesField
 */
// TODO ^^^ that is very sandy and hurts the API, usage, and tests tremendously, because what the
// user passes
// to the field is not actually what gets indexed. Float would be 1E-5 error vs 1E-7, but it might
// be
// a better tradeoff then it would be completely transparent to the user and lucene would be
// "lossless".
class LatLonPoint(name: String, latitude: Double, longitude: Double) : Field(name, TYPE) {
    /**
     * Change the values of this field
     *
     * @param latitude latitude value: must be within standard +/-90 coordinate bounds.
     * @param longitude longitude value: must be within standard +/-180 coordinate bounds.
     * @throws IllegalArgumentException if latitude or longitude are out of bounds
     */
    fun setLocationValue(latitude: Double, longitude: Double) {
        val bytes: ByteArray

        if (!isFieldsDataInitialized()) {
            bytes = ByteArray(8)
            fieldsData = BytesRef(bytes)
        } else {
            bytes = (fieldsData as BytesRef).bytes
        }

        val latitudeEncoded: Int = GeoEncodingUtils.encodeLatitude(latitude)
        val longitudeEncoded: Int = GeoEncodingUtils.encodeLongitude(longitude)
        NumericUtils.intToSortableBytes(latitudeEncoded, bytes, 0)
        NumericUtils.intToSortableBytes(longitudeEncoded, bytes, Int.SIZE_BYTES)
    }

    /**
     * Creates a new LatLonPoint with the specified latitude and longitude
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

    override fun toString(): String {
        val result = StringBuilder()
        result.append(this::class.simpleName)
        result.append(" <")
        result.append(name)
        result.append(':')

        val bytes: ByteArray = (fieldsData as BytesRef).bytes
        result.append(GeoEncodingUtils.decodeLatitude(bytes, 0))
        result.append(',')
        result.append(GeoEncodingUtils.decodeLongitude(bytes, Int.SIZE_BYTES))

        result.append('>')
        return result.toString()
    }

    companion object {
        /** LatLonPoint is encoded as integer values so number of bytes is 4  */
        const val BYTES: Int = Int.SIZE_BYTES

        /**
         * Type for an indexed LatLonPoint
         *
         *
         * Each point stores two dimensions with 4 bytes per dimension.
         */
        val TYPE: FieldType = FieldType()

        init {
            TYPE.setDimensions(2, Int.SIZE_BYTES)
            TYPE.freeze()
        }

        /** sugar encodes a single point as a byte array  */
        private fun encode(latitude: Double, longitude: Double): ByteArray {
            val bytes = ByteArray(2 * Int.SIZE_BYTES)
            NumericUtils.intToSortableBytes(GeoEncodingUtils.encodeLatitude(latitude), bytes, 0)
            NumericUtils.intToSortableBytes(
                GeoEncodingUtils.encodeLongitude(longitude),
                bytes,
                Int.SIZE_BYTES
            )
            return bytes
        }

        /** sugar encodes a single point as a byte array, rounding values up  */
        private fun encodeCeil(latitude: Double, longitude: Double): ByteArray {
            val bytes = ByteArray(2 * Int.SIZE_BYTES)
            NumericUtils.intToSortableBytes(
                GeoEncodingUtils.encodeLatitudeCeil(latitude),
                bytes,
                0
            )
            NumericUtils.intToSortableBytes(
                GeoEncodingUtils.encodeLongitudeCeil(longitude),
                bytes,
                Int.SIZE_BYTES
            )
            return bytes
        }

        /** helper: checks a fieldinfo and throws exception if its definitely not a LatLonPoint  */
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
                        + ", is the field really a LatLonPoint")
            }
            require(!(fieldInfo.pointNumBytes != 0 && fieldInfo.pointNumBytes != TYPE.pointNumBytes())) {
                ("field=\""
                        + fieldInfo.name
                        + "\" was indexed with bytesPerDim="
                        + fieldInfo.pointNumBytes
                        + " but this point type has bytesPerDim="
                        + TYPE.pointNumBytes()
                        + ", is the field really a LatLonPoint")
            }
        }

        // static methods for generating queries
        /**
         * Create a query for matching a bounding box.
         *
         *
         * The box may cross over the dateline.
         *
         * @param field field name. must not be null.
         * @param minLatitude latitude lower bound: must be within standard +/-90 coordinate bounds.
         * @param maxLatitude latitude upper bound: must be within standard +/-90 coordinate bounds.
         * @param minLongitude longitude lower bound: must be within standard +/-180 coordinate bounds.
         * @param maxLongitude longitude upper bound: must be within standard +/-180 coordinate bounds.
         * @return query matching points within this box
         * @throws IllegalArgumentException if `field` is null, or the box has invalid coordinates.
         */
        fun newBoxQuery(
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
                return MatchNoDocsQuery("LatLonPoint.newBoxQuery with minLatitude=90.0")
            }
            if (minLongitude == 180.0) {
                if (maxLongitude == 180.0) {
                    // range cannot match as 180.0 can never exist
                    return MatchNoDocsQuery("LatLonPoint.newBoxQuery with minLongitude=maxLongitude=180.0")
                } else if (maxLongitude < minLongitude) {
                    // encodeCeil() with dateline wrapping!
                    minLongitude = -180.0
                }
            }
            val lower = encodeCeil(minLatitude, minLongitude)
            val upper = encode(maxLatitude, maxLongitude)
            // Crosses date line: we just rewrite into OR of two bboxes, with longitude as an open range:
            if (maxLongitude < minLongitude) {
                // Disable coord here because a multi-valued doc could match both rects and get unfairly
                // boosted:
                val q: BooleanQuery.Builder = BooleanQuery.Builder()

                // E.g.: maxLon = -179, minLon = 179
                val leftOpen: ByteArray = lower.copyOf()
                // leave longitude open
                NumericUtils.intToSortableBytes(
                    Int.MIN_VALUE,
                    leftOpen,
                    Int.SIZE_BYTES
                )
                val left: Query = newBoxInternal(field, leftOpen, upper)
                q.add(BooleanClause(left, Occur.SHOULD))

                val rightOpen: ByteArray = upper.copyOf()
                // leave longitude open
                NumericUtils.intToSortableBytes(
                    Int.MAX_VALUE,
                    rightOpen,
                    Int.SIZE_BYTES
                )
                val right: Query = newBoxInternal(field, lower, rightOpen)
                q.add(
                    BooleanClause(
                        right,
                        Occur.SHOULD
                    )
                )
                return ConstantScoreQuery(q.build())
            } else {
                return newBoxInternal(field, lower, upper)
            }
        }

        private fun newBoxInternal(field: String?, min: ByteArray, max: ByteArray): Query {
            return object : PointRangeQuery(field, min, max, 2) {
                override fun toString(dimension: Int, value: ByteArray): String {
                    return if (dimension == 0) {
                        GeoEncodingUtils.decodeLatitude(value, 0).toString()
                    } else if (dimension == 1) {
                        GeoEncodingUtils.decodeLongitude(value, 0).toString()
                    } else {
                        throw AssertionError()
                    }
                }
            }
        }

        /**
         * Create a query for matching points within the specified distance of the supplied location.
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
        fun newDistanceQuery(
            field: String?, latitude: Double, longitude: Double, radiusMeters: Double
        ): Query {
            return LatLonPointDistanceQuery(field, latitude, longitude, radiusMeters)
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
        fun newPolygonQuery(
            field: String?,
            vararg polygons: Polygon
        ): Query {
            return newGeometryQuery(field, QueryRelation.INTERSECTS, *polygons)
        }

        /**
         * Create a query for matching one or more geometries against the provided [ ]. Line geometries are not supported for WITHIN relationship.
         *
         * @param field field name. must not be null.
         * @param queryRelation The relation the points needs to satisfy with the provided geometries,
         * must not be null.
         * @param latLonGeometries array of LatLonGeometries. must not be null or empty.
         * @return query matching points within at least one geometry.
         * @throws IllegalArgumentException if `field` is null, `queryRelation` is null,
         * `latLonGeometries` is null, empty or contain a null.
         * @see LatLonGeometry
         */
        fun newGeometryQuery(
            field: String?, queryRelation: QueryRelation, vararg latLonGeometries: LatLonGeometry
        ): Query {
            if (queryRelation == QueryRelation.INTERSECTS && latLonGeometries.size == 1) {
                if (latLonGeometries[0] is Rectangle) {
                    val rect: Rectangle = latLonGeometries[0] as Rectangle
                    return newBoxQuery(field, rect.minLat, rect.maxLat, rect.minLon, rect.maxLon)
                }
                if (latLonGeometries[0] is Circle) {
                    val circle: Circle = latLonGeometries[0] as Circle
                    return newDistanceQuery(field, circle.lat, circle.lon, circle.radius)
                }
            }
            if (queryRelation == QueryRelation.CONTAINS) {
                return makeContainsGeometryQuery(field, *latLonGeometries)
            }
            return LatLonPointQuery(field, queryRelation, *latLonGeometries)
        }

        private fun makeContainsGeometryQuery(
            field: String?,
            vararg latLonGeometries: LatLonGeometry
        ): Query {
            val builder: BooleanQuery.Builder = BooleanQuery.Builder()
            for (geometry in latLonGeometries) {
                if (geometry !is Point) {
                    return MatchNoDocsQuery(
                        "Contains LatLonPoint.newGeometryQuery with non-point geometries"
                    )
                }
                builder.add(
                    LatLonPointQuery(field, QueryRelation.CONTAINS, geometry),
                    Occur.MUST
                )
            }
            return ConstantScoreQuery(builder.build())
        }

        /**
         * Given a field that indexes point values into a [LatLonPoint] and doc values into [ ], this returns a query that scores documents based on their haversine
         * distance in meters to `(originLat, originLon)`: `score = weight *
         * pivotDistanceMeters / (pivotDistanceMeters + distance)`, ie. score is in the `[0,
         * weight]` range, is equal to `weight` when the document's value is equal to `(originLat, originLon)` and is equal to `weight/2` when the document's value is distant
         * of `pivotDistanceMeters` from `(originLat, originLon)`. In case of multi-valued
         * fields, only the closest point to `(originLat, originLon)` will be considered. This query
         * is typically useful to boost results based on distance by adding this query to a [ ][Occur.SHOULD] clause of a [BooleanQuery].
         */
        fun newDistanceFeatureQuery(
            field: String, weight: Float, originLat: Double, originLon: Double, pivotDistanceMeters: Double
        ): Query {
            var query: Query =
                LatLonPointDistanceFeatureQuery(field, originLat, originLon, pivotDistanceMeters)
            if (weight != 1f) {
                query = BoostQuery(query, weight)
            }
            return query
        }

        /**
         * Finds the `n` nearest indexed points to the provided point, according to Haversine
         * distance.
         *
         *
         * This is functionally equivalent to running [MatchAllDocsQuery] with a [ ][LatLonDocValuesField.newDistanceSort], but is far more efficient since it takes advantage of
         * properties the indexed BKD tree. Multi-valued fields are currently not de-duplicated, so if a
         * document had multiple instances of the specified field that make it into the top n, that
         * document will appear more than once.
         *
         *
         * Documents are ordered by ascending distance from the location. The value returned in [ ] for the hits contains a Double instance with the distance in meters.
         *
         * @param searcher IndexSearcher to find nearest points from.
         * @param field field name. must not be null.
         * @param latitude latitude at the center: must be within standard +/-90 coordinate bounds.
         * @param longitude longitude at the center: must be within standard +/-180 coordinate bounds.
         * @param n the number of nearest neighbors to retrieve.
         * @return TopFieldDocs containing documents ordered by distance, where the field value for each
         * [FieldDoc] is the distance in meters
         * @throws IllegalArgumentException if `field` or `searcher` is null, or if `latitude`, `longitude` or `n` are out-of-bounds
         * @throws IOException if an IOException occurs while finding the points.
         */
        // TODO: what about multi-valued documents what happens
        @Throws(IOException::class)
        fun nearest(
            searcher: IndexSearcher, field: String, latitude: Double, longitude: Double, n: Int
        ): TopFieldDocs {
            GeoUtils.checkLatitude(latitude)
            GeoUtils.checkLongitude(longitude)
            require(n >= 1) { "n must be at least 1; got $n" }
            requireNotNull(field) { "field must not be null" }
            requireNotNull(searcher) { "searcher must not be null" }
            val readers: MutableList<PointValues> =
                ArrayList()
            val docBases = IntArrayList()
            val liveDocs: MutableList<Bits> =
                ArrayList()
            var totalHits = 0
            for (leaf in searcher.indexReader.leaves()) {
                val points: PointValues? = leaf.reader().getPointValues(field)
                if (points != null) {
                    totalHits += points.docCount
                    readers.add(points)
                    docBases.add(leaf.docBase)
                    liveDocs.add(leaf.reader().liveDocs!!)
                }
            }

            val hits: Array<NearestNeighbor.NearestHit> =
                NearestNeighbor.nearest(latitude, longitude, readers, liveDocs, docBases, n)

            // Convert to TopFieldDocs:
            val scoreDocs: Array<ScoreDoc?> =
                kotlin.arrayOfNulls(hits.size)
            for (i in hits.indices) {
                val hit: NearestNeighbor.NearestHit = hits[i]
                val hitDistance: Double = SloppyMath.haversinMeters(hit.distanceSortKey)
                scoreDocs[i] = FieldDoc(hit.docID, 0.0f, arrayOf(hitDistance))
            }
            return TopFieldDocs(
                TotalHits(
                    totalHits.toLong(),
                    TotalHits.Relation.EQUAL_TO
                ), scoreDocs as Array<ScoreDoc>, null
            )
        }
    }
}
