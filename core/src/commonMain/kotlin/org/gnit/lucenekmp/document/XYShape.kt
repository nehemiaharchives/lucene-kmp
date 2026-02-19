package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.document.ShapeField.QueryRelation
import org.gnit.lucenekmp.document.ShapeField.Triangle
import org.gnit.lucenekmp.geo.Tessellator
import org.gnit.lucenekmp.geo.XYCircle
import org.gnit.lucenekmp.geo.XYEncodingUtils.encode
import org.gnit.lucenekmp.geo.XYGeometry
import org.gnit.lucenekmp.geo.XYLine
import org.gnit.lucenekmp.geo.XYPoint
import org.gnit.lucenekmp.geo.XYPolygon
import org.gnit.lucenekmp.geo.XYRectangle
import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.search.BooleanClause
import org.gnit.lucenekmp.search.BooleanQuery
import org.gnit.lucenekmp.search.ConstantScoreQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.util.BytesRef

/**
 * A cartesian shape utility class for indexing and searching geometries whose vertices are unitless
 * x, y values.
 */
object XYShape {
    /** create indexable fields for cartesian polygon geometry */
    fun createIndexableFields(fieldName: String, polygon: XYPolygon): Array<Field> {
        return createIndexableFields(fieldName, polygon, false)
    }

    /** create doc value field for X,Y polygon geometry without creating indexable fields */
    fun createDocValueField(fieldName: String, polygon: XYPolygon): XYShapeDocValuesField {
        return createDocValueField(fieldName, polygon, false)
    }

    /**
     * create indexable fields for cartesian polygon geometry. If [checkSelfIntersections] is
     * set to true, the validity of the provided polygon is checked with a small performance penalty.
     */
    fun createIndexableFields(
        fieldName: String,
        polygon: XYPolygon,
        checkSelfIntersections: Boolean
    ): Array<Field> {
        val tessellation = Tessellator.tessellate(polygon, checkSelfIntersections)
        val fields = Array<Field>(tessellation.size) { i -> Triangle(fieldName, tessellation[i]) }
        return fields
    }

    /** create doc value field for lat lon polygon geometry without creating indexable fields. */
    fun createDocValueField(
        fieldName: String,
        polygon: XYPolygon,
        checkSelfIntersections: Boolean
    ): XYShapeDocValuesField {
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
        return XYShapeDocValuesField(fieldName, triangles)
    }

    /** create indexable fields for cartesian line geometry */
    fun createIndexableFields(fieldName: String, line: XYLine): Array<Field> {
        val numPoints = line.numPoints()
        val fields = Array<Field>(numPoints - 1) { i ->
            Triangle(
                fieldName,
                encode(line.getX(i)),
                encode(line.getY(i)),
                encode(line.getX(i + 1)),
                encode(line.getY(i + 1)),
                encode(line.getX(i)),
                encode(line.getY(i))
            )
        }
        return fields
    }

    /** create doc value field for x, y line geometry without creating indexable fields. */
    fun createDocValueField(fieldName: String, line: XYLine): XYShapeDocValuesField {
        val numPoints = line.numPoints()
        val triangles = ArrayList<ShapeField.DecodedTriangle>(numPoints - 1)
        for (i in 0..<numPoints - 1) {
            val t = ShapeField.DecodedTriangle()
            t.type = ShapeField.DecodedTriangle.TYPE.LINE
            t.setValues(
                encode(line.getX(i)),
                encode(line.getY(i)),
                true,
                encode(line.getX(i + 1)),
                encode(line.getY(i + 1)),
                true,
                encode(line.getX(i)),
                encode(line.getY(i)),
                true
            )
            triangles.add(t)
        }
        return XYShapeDocValuesField(fieldName, triangles)
    }

    /** create indexable fields for cartesian point geometry */
    fun createIndexableFields(fieldName: String, x: Float, y: Float): Array<Field> {
        return arrayOf(Triangle(fieldName, encode(x), encode(y), encode(x), encode(y), encode(x), encode(y)))
    }

    /**
     * create a [XYShapeDocValuesField] for cartesian points without creating indexable fields.
     */
    fun createDocValueField(fieldName: String, x: Float, y: Float): XYShapeDocValuesField {
        val triangles = ArrayList<ShapeField.DecodedTriangle>(1)
        val t = ShapeField.DecodedTriangle()
        t.type = ShapeField.DecodedTriangle.TYPE.POINT
        t.setValues(encode(x), encode(y), true, encode(x), encode(y), true, encode(x), encode(y), true)
        triangles.add(t)
        return XYShapeDocValuesField(fieldName, triangles)
    }

    /** create a [XYShapeDocValuesField] from an existing encoded representation */
    fun createDocValueField(fieldName: String, binaryValue: BytesRef): XYShapeDocValuesField {
        return XYShapeDocValuesField(fieldName, binaryValue)
    }

    /** create a [XYShapeDocValuesField] from a precomputed tessellation */
    fun createDocValueField(
        fieldName: String,
        tessellation: List<ShapeField.DecodedTriangle>
    ): XYShapeDocValuesField {
        return XYShapeDocValuesField(fieldName, tessellation)
    }

    /** create a query to find all cartesian shapes that intersect a defined bounding box */
    fun newBoxQuery(
        field: String,
        queryRelation: QueryRelation,
        minX: Float,
        maxX: Float,
        minY: Float,
        maxY: Float
    ): Query {
        val rectangle = XYRectangle(minX, maxX, minY, maxY)
        return newGeometryQuery(field, queryRelation, rectangle)
    }

    /**
     * create a docvalue query to find all cartesian shapes that intersect a defined bounding box
     */
    fun newSlowDocValuesBoxQuery(
        field: String,
        queryRelation: QueryRelation,
        minX: Float,
        maxX: Float,
        minY: Float,
        maxY: Float
    ): Query {
        throw UnsupportedOperationException("XYShape.newSlowDocValuesBoxQuery is not ported yet")
    }

    /**
     * create a query to find all cartesian shapes that intersect a provided linestring (or array of
     * linestrings)
     */
    fun newLineQuery(field: String, queryRelation: QueryRelation, vararg lines: XYLine): Query {
        return newGeometryQuery(field, queryRelation, *lines)
    }

    /**
     * create a query to find all cartesian shapes that intersect a provided polygon (or array of
     * polygons)
     */
    fun newPolygonQuery(field: String, queryRelation: QueryRelation, vararg polygons: XYPolygon): Query {
        return newGeometryQuery(field, queryRelation, *polygons)
    }

    /**
     * create a query to find all indexed shapes that comply the [QueryRelation] with the
     * provided point
     */
    fun newPointQuery(field: String, queryRelation: QueryRelation, vararg points: FloatArray): Query {
        val pointArray = Array(points.size) { i -> XYPoint(points[i][0], points[i][1]) }
        return newGeometryQuery(field, queryRelation, *pointArray)
    }

    /**
     * create a query to find all cartesian shapes that intersect a provided circle (or arrays of
     * circles)
     */
    fun newDistanceQuery(field: String, queryRelation: QueryRelation, vararg circle: XYCircle): Query {
        return newGeometryQuery(field, queryRelation, *circle)
    }

    /**
     * create a query to find all indexed geo shapes that intersect a provided geometry collection
     * note: Components do not support dateline crossing
     */
    fun newGeometryQuery(field: String, queryRelation: QueryRelation, vararg xyGeometries: XYGeometry): Query {
        if (queryRelation == QueryRelation.CONTAINS && xyGeometries.size > 1) {
            val builder = BooleanQuery.Builder()
            for (i in xyGeometries.indices) {
                builder.add(newGeometryQuery(field, queryRelation, xyGeometries[i]), BooleanClause.Occur.MUST)
            }
            return ConstantScoreQuery(builder.build())
        }
        return XYShapeQuery(field, queryRelation, *xyGeometries)
    }

    /** Factory method for creating the [XYShapeDocValues] */
    fun createXYShapeDocValues(bytesRef: BytesRef): XYShapeDocValues {
        return XYShapeDocValues(bytesRef)
    }
}
