package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.document.ShapeField.QueryRelation
import org.gnit.lucenekmp.search.Query

/** Base test class for LatLonShape doc values */
abstract class BaseLatLonShapeDocValueTestCase : BaseLatLonSpatialTestCase() {

    override fun getSupportedQueryRelations(): Array<QueryRelation> {
        return arrayOf(
            QueryRelation.INTERSECTS,
            QueryRelation.WITHIN,
            QueryRelation.DISJOINT
        )
    }

    override fun newRectQuery(
        field: String,
        queryRelation: QueryRelation,
        minLon: Double,
        maxLon: Double,
        minLat: Double,
        maxLat: Double
    ): Query {
        throw UnsupportedOperationException("LatLonShape.newSlowDocValuesBoxQuery is not ported yet")
    }

    override fun newLineQuery(field: String, queryRelation: QueryRelation, vararg lines: Any): Query {
        throw UnsupportedOperationException("ShapeDocValuesField.newGeometryQuery(Line) is not ported yet")
    }

    override fun newPolygonQuery(field: String, queryRelation: QueryRelation, vararg polygons: Any): Query {
        throw UnsupportedOperationException("ShapeDocValuesField.newGeometryQuery(Polygon) is not ported yet")
    }

    override fun newPointsQuery(field: String, queryRelation: QueryRelation, vararg points: Any): Query {
        throw UnsupportedOperationException("ShapeDocValuesField.newGeometryQuery(points) is not ported yet")
    }

    override fun newDistanceQuery(field: String, queryRelation: QueryRelation, circle: Any): Query {
        throw UnsupportedOperationException("LatLonShape.newDistanceQuery is not ported yet")
    }
}
