package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.document.ShapeField.QueryRelation
import org.gnit.lucenekmp.document.ShapeField.Triangle
import org.gnit.lucenekmp.geo.Circle
import org.gnit.lucenekmp.geo.GeoEncodingUtils.encodeLatitude
import org.gnit.lucenekmp.geo.GeoEncodingUtils.encodeLongitude
import org.gnit.lucenekmp.geo.GeoUtils
import org.gnit.lucenekmp.geo.LatLonGeometry
import org.gnit.lucenekmp.geo.Line
import org.gnit.lucenekmp.geo.Point
import org.gnit.lucenekmp.geo.Polygon
import org.gnit.lucenekmp.geo.Rectangle
import org.gnit.lucenekmp.geo.Tessellator
import org.gnit.lucenekmp.search.BooleanClause
import org.gnit.lucenekmp.search.BooleanQuery
import org.gnit.lucenekmp.search.ConstantScoreQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.util.BytesRef

/**
 * A geo shape utility class for indexing and searching GIS geometries whose vertices are latitude,
 * longitude values (in decimal degrees).
 *
 * This class defines static factory methods for common indexing and search operations:
 *
 * - [createIndexableFields] for indexing polygon, line, and point geometries.
 * - [createDocValueField] for indexing polygon, line, and point doc values, and from existing
 *   encoded bytes or tessellation.
 * - [newBoxQuery], [newLineQuery], [newPolygonQuery], [newPointQuery], and [newGeometryQuery] for
 *   matching geo shapes that have a [QueryRelation] with provided geometry.
 * - [createLatLonShapeDocValues] for creating [LatLonShapeDocValues].
 *
 * WARNING: Like [LatLonPoint], vertex values are indexed with some loss of precision from the
 * original `Double` values.
 */
object LatLonShape {

    /** create indexable fields for polygon geometry. */
    fun createIndexableFields(fieldName: String, polygon: Polygon): Array<Field> {
        return createIndexableFields(fieldName, polygon, false)
    }

    /** create doc value field for lat lon polygon geometry without creating indexable fields */
    fun createDocValueField(fieldName: String, polygon: Polygon): LatLonShapeDocValuesField {
        return createDocValueField(fieldName, polygon, false)
    }

    /**
     * create indexable fields for polygon geometry. If [checkSelfIntersections] is set to true,
     * the validity of the provided polygon is checked with a small performance penalty.
     */
    fun createIndexableFields(
        fieldName: String,
        polygon: Polygon,
        checkSelfIntersections: Boolean
    ): Array<Field> {
        val tessellation = Tessellator.tessellate(polygon, checkSelfIntersections)
        val fields = Array<Field>(tessellation.size) { i -> Triangle(fieldName, tessellation[i]) }
        return fields
    }

    /** create doc value field for lat lon polygon geometry without creating indexable fields. */
    fun createDocValueField(
        fieldName: String,
        polygon: Polygon,
        checkSelfIntersections: Boolean
    ): LatLonShapeDocValuesField {
        val tessellation = Tessellator.tessellate(polygon, checkSelfIntersections)
        val triangles = ArrayList<ShapeField.DecodedTriangle>(tessellation.size)
        for (t in tessellation) {
            val dt = ShapeField.DecodedTriangle()
            dt.type = ShapeField.DecodedTriangle.TYPE.TRIANGLE
            dt.setValues(
                t.getEncodedX(0),
                t.getEncodedY(0),
                t.isEdgefromPolygon(0),
                t.getEncodedX(1),
                t.getEncodedY(1),
                t.isEdgefromPolygon(0),
                t.getEncodedX(2),
                t.getEncodedY(2),
                t.isEdgefromPolygon(2)
            )
            triangles.add(dt)
        }
        return LatLonShapeDocValuesField(fieldName, triangles)
    }

    /** create indexable fields for line geometry */
    fun createIndexableFields(fieldName: String, line: Line): Array<Field> {
        val numPoints = line.numPoints()
        val fields = Array<Field>(numPoints - 1) { i ->
            Triangle(
                fieldName,
                encodeLongitude(line.getLon(i)),
                encodeLatitude(line.getLat(i)),
                encodeLongitude(line.getLon(i + 1)),
                encodeLatitude(line.getLat(i + 1)),
                encodeLongitude(line.getLon(i)),
                encodeLatitude(line.getLat(i))
            )
        }
        return fields
    }

    /** create doc value field for lat lon line geometry without creating indexable fields. */
    fun createDocValueField(fieldName: String, line: Line): LatLonShapeDocValuesField {
        val numPoints = line.numPoints()
        val triangles = ArrayList<ShapeField.DecodedTriangle>(numPoints - 1)
        for (i in 0..<numPoints - 1) {
            val t = ShapeField.DecodedTriangle()
            t.type = ShapeField.DecodedTriangle.TYPE.LINE
            t.setValues(
                encodeLongitude(line.getLon(i)),
                encodeLatitude(line.getLat(i)),
                true,
                encodeLongitude(line.getLon(i + 1)),
                encodeLatitude(line.getLat(i + 1)),
                true,
                encodeLongitude(line.getLon(i)),
                encodeLatitude(line.getLat(i)),
                true
            )
            triangles.add(t)
        }
        return LatLonShapeDocValuesField(fieldName, triangles)
    }

    /** create indexable fields for point geometry */
    fun createIndexableFields(fieldName: String, lat: Double, lon: Double): Array<Field> {
        return arrayOf(
            Triangle(
                fieldName,
                encodeLongitude(lon),
                encodeLatitude(lat),
                encodeLongitude(lon),
                encodeLatitude(lat),
                encodeLongitude(lon),
                encodeLatitude(lat)
            )
        )
    }

    /** create doc value field for lat lon point geometry without creating indexable fields. */
    fun createDocValueField(fieldName: String, lat: Double, lon: Double): LatLonShapeDocValuesField {
        val triangles = ArrayList<ShapeField.DecodedTriangle>(1)
        val t = ShapeField.DecodedTriangle()
        t.type = ShapeField.DecodedTriangle.TYPE.POINT
        t.setValues(
            encodeLongitude(lon),
            encodeLatitude(lat),
            true,
            encodeLongitude(lon),
            encodeLatitude(lat),
            true,
            encodeLongitude(lon),
            encodeLatitude(lat),
            true
        )
        triangles.add(t)
        return LatLonShapeDocValuesField(fieldName, triangles)
    }

    /** create a [LatLonShapeDocValuesField] from an existing encoded representation */
    fun createDocValueField(fieldName: String, binaryValue: BytesRef): LatLonShapeDocValuesField {
        return LatLonShapeDocValuesField(fieldName, binaryValue)
    }

    /** create a [LatLonShapeDocValuesField] from an existing tessellation */
    fun createDocValueField(
        fieldName: String,
        tessellation: List<ShapeField.DecodedTriangle>
    ): LatLonShapeDocValuesField {
        return LatLonShapeDocValuesField(fieldName, tessellation)
    }

    /** create a shape docvalue field from indexable fields */
    fun createDocValueField(fieldName: String, indexableFields: Array<Field>): LatLonShapeDocValuesField {
        val tess = ArrayList<ShapeField.DecodedTriangle>(indexableFields.size)
        val scratch = ByteArray(7 * Int.SIZE_BYTES)
        for (f in indexableFields) {
            val br = f.binaryValue()!!
            require(br.length == 7 * ShapeField.BYTES)
            br.bytes.copyInto(scratch, 0, br.offset, br.offset + (7 * ShapeField.BYTES))
            val t = ShapeField.DecodedTriangle()
            ShapeField.decodeTriangle(scratch, t)
            tess.add(t)
        }
        return LatLonShapeDocValuesField(fieldName, tess)
    }

    /** create a query to find all indexed geo shapes that intersect a defined bounding box */
    fun newBoxQuery(
        field: String,
        queryRelation: QueryRelation,
        minLatitude: Double,
        maxLatitude: Double,
        minLongitude: Double,
        maxLongitude: Double
    ): Query {
        if (queryRelation == QueryRelation.CONTAINS && minLongitude > maxLongitude) {
            val builder = BooleanQuery.Builder()
            builder.add(
                newBoxQuery(field, queryRelation, minLatitude, maxLatitude, minLongitude, GeoUtils.MAX_LON_INCL),
                BooleanClause.Occur.MUST
            )
            builder.add(
                newBoxQuery(field, queryRelation, minLatitude, maxLatitude, GeoUtils.MIN_LON_INCL, maxLongitude),
                BooleanClause.Occur.MUST
            )
            return builder.build()
        }
        return LatLonShapeBoundingBoxQuery(
            field,
            queryRelation,
            Rectangle(minLatitude, maxLatitude, minLongitude, maxLongitude)
        )
    }

    /** create a docvalue query to find all geo shapes that intersect a defined bounding box */
    fun newSlowDocValuesBoxQuery(
        field: String,
        queryRelation: QueryRelation,
        minLatitude: Double,
        maxLatitude: Double,
        minLongitude: Double,
        maxLongitude: Double
    ): Query {
        if (queryRelation == QueryRelation.CONTAINS && minLongitude > maxLongitude) {
            val builder = BooleanQuery.Builder()
            builder.add(
                newBoxQuery(field, queryRelation, minLatitude, maxLatitude, minLongitude, GeoUtils.MAX_LON_INCL),
                BooleanClause.Occur.MUST
            )
            builder.add(
                newBoxQuery(field, queryRelation, minLatitude, maxLatitude, GeoUtils.MIN_LON_INCL, maxLongitude),
                BooleanClause.Occur.MUST
            )
            return builder.build()
        }
        return LatLonDocValuesField.newSlowGeometryQuery(
            field,
            queryRelation,
            Rectangle(minLatitude, maxLatitude, minLongitude, maxLongitude)
        )
    }

    /**
     * create a query to find all indexed geo shapes that intersect a provided linestring (or array
     * of linestrings) note: does not support dateline crossing
     */
    fun newLineQuery(field: String, queryRelation: QueryRelation, vararg lines: Line): Query {
        return newGeometryQuery(field, queryRelation, *lines)
    }

    /**
     * create a query to find all indexed geo shapes that intersect a provided polygon (or array of
     * polygons) note: does not support dateline crossing
     */
    fun newPolygonQuery(field: String, queryRelation: QueryRelation, vararg polygons: Polygon): Query {
        return newGeometryQuery(field, queryRelation, *polygons)
    }

    /**
     * create a query to find all indexed shapes that comply the [QueryRelation] with the provided
     * points
     */
    fun newPointQuery(field: String, queryRelation: QueryRelation, vararg points: DoubleArray): Query {
        val pointArray = Array(points.size) { i -> Point(points[i][0], points[i][1]) }
        return newGeometryQuery(field, queryRelation, *pointArray)
    }

    /** create a query to find all polygons that intersect a provided circle. */
    fun newDistanceQuery(field: String, queryRelation: QueryRelation, vararg circle: Circle): Query {
        return newGeometryQuery(field, queryRelation, *circle)
    }

    /** create a query for geometries. */
    fun newGeometryQuery(
        field: String,
        queryRelation: QueryRelation,
        vararg latLonGeometries: LatLonGeometry
    ): Query {
        if (latLonGeometries.size == 1) {
            val geometry = latLonGeometries[0]
            if (geometry is Rectangle) {
                return newBoxQuery(
                    field,
                    queryRelation,
                    geometry.minLat,
                    geometry.maxLat,
                    geometry.minLon,
                    geometry.maxLon
                )
            }
            return LatLonShapeQuery(field, queryRelation, geometry)
        }
        if (queryRelation == QueryRelation.CONTAINS) {
            return makeContainsGeometryQuery(field, *latLonGeometries)
        }
        return LatLonShapeQuery(field, queryRelation, *latLonGeometries)
    }

    /** Factory method for creating [LatLonShapeDocValues]. */
    fun createLatLonShapeDocValues(bytesRef: BytesRef): LatLonShapeDocValues {
        return LatLonShapeDocValues(bytesRef)
    }

    private fun makeContainsGeometryQuery(field: String, vararg latLonGeometries: LatLonGeometry): Query {
        val builder = BooleanQuery.Builder()
        for (geometry in latLonGeometries) {
            if (geometry is Rectangle) {
                builder.add(
                    newBoxQuery(
                        field,
                        QueryRelation.CONTAINS,
                        geometry.minLat,
                        geometry.maxLat,
                        geometry.minLon,
                        geometry.maxLon
                    ),
                    BooleanClause.Occur.MUST
                )
            } else {
                builder.add(
                    LatLonShapeQuery(field, QueryRelation.CONTAINS, geometry),
                    BooleanClause.Occur.MUST
                )
            }
        }
        return ConstantScoreQuery(builder.build())
    }
}
