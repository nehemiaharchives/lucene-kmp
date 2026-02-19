package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.geo.Circle
import org.gnit.lucenekmp.geo.XYLine
import org.gnit.lucenekmp.geo.XYPolygon
import org.gnit.lucenekmp.search.Query

abstract class BaseXYShapeDocValueTestCase : BaseXYShapeTestCase() {
    override fun getSupportedQueryRelations(): Array<ShapeField.QueryRelation> {
        return arrayOf(
            ShapeField.QueryRelation.INTERSECTS,
            ShapeField.QueryRelation.WITHIN,
            ShapeField.QueryRelation.DISJOINT
        )
    }

    override fun newRectQuery(
        field: String,
        queryRelation: ShapeField.QueryRelation,
        minX: Double,
        maxX: Double,
        minY: Double,
        maxY: Double
    ): Query {
        return XYShape.newSlowDocValuesBoxQuery(
            field,
            queryRelation,
            minX.toFloat(),
            maxX.toFloat(),
            minY.toFloat(),
            maxY.toFloat()
        )
    }

    override fun newLineQuery(field: String, queryRelation: ShapeField.QueryRelation, vararg lines: Any): Query {
        return ShapeDocValuesField.newGeometryQuery(
            field,
            queryRelation,
            Array(lines.size) { i -> lines[i] as XYLine }
        )
    }

    override fun newPolygonQuery(
        field: String,
        queryRelation: ShapeField.QueryRelation,
        vararg polygons: Any
    ): Query {
        return ShapeDocValuesField.newGeometryQuery(
            field,
            queryRelation,
            Array(polygons.size) { i -> polygons[i] as XYPolygon }
        )
    }

    override fun newPointsQuery(field: String, queryRelation: ShapeField.QueryRelation, vararg points: Any): Query {
        return ShapeDocValuesField.newGeometryQuery(
            field,
            queryRelation,
            Array(points.size) { i -> points[i] as FloatArray }
        )
    }

    override fun newDistanceQuery(field: String, queryRelation: ShapeField.QueryRelation, circle: Any): Query {
        return LatLonShape.newDistanceQuery(field, queryRelation, circle as Circle)
    }
}
