package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.document.ShapeField.QueryRelation
import org.gnit.lucenekmp.geo.Circle
import org.gnit.lucenekmp.geo.Line
import org.gnit.lucenekmp.geo.Point
import org.gnit.lucenekmp.geo.Polygon
import org.gnit.lucenekmp.geo.Rectangle
import org.gnit.lucenekmp.search.Query

/**
 * Base test case for testing geospatial indexing and search functionality for [LatLonDocValuesField] *
 */
abstract class BaseLatLonDocValueTestCase : BaseLatLonSpatialTestCase() {

    override fun newRectQuery(
        field: String,
        queryRelation: QueryRelation,
        minLon: Double,
        maxLon: Double,
        minLat: Double,
        maxLat: Double
    ): Query {
        return LatLonDocValuesField.newSlowGeometryQuery(
            field,
            queryRelation,
            Rectangle(minLat, maxLat, minLon, maxLon)
        )
    }

    override fun newLineQuery(field: String, queryRelation: QueryRelation, vararg lines: Any): Query {
        return LatLonDocValuesField.newSlowGeometryQuery(
            field,
            queryRelation,
            *Array(lines.size) { i -> lines[i] as Line }
        )
    }

    override fun newPolygonQuery(field: String, queryRelation: QueryRelation, vararg polygons: Any): Query {
        return LatLonDocValuesField.newSlowGeometryQuery(
            field,
            queryRelation,
            *Array(polygons.size) { i -> polygons[i] as Polygon }
        )
    }

    override fun newDistanceQuery(field: String, queryRelation: QueryRelation, circle: Any): Query {
        return LatLonDocValuesField.newSlowGeometryQuery(field, queryRelation, circle as Circle)
    }

    override fun newPointsQuery(field: String, queryRelation: QueryRelation, vararg points: Any): Query {
        val pointsArray = Array(points.size) { Point(0.0, 0.0) }
        for (i in points.indices) {
            val point = points[i] as DoubleArray
            pointsArray[i] = Point(point[0], point[1])
        }
        return LatLonDocValuesField.newSlowGeometryQuery(field, queryRelation, *pointsArray)
    }
}
