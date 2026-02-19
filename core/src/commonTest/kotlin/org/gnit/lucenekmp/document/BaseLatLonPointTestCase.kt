package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.document.ShapeField.QueryRelation
import org.gnit.lucenekmp.geo.Circle
import org.gnit.lucenekmp.geo.Line
import org.gnit.lucenekmp.geo.Point
import org.gnit.lucenekmp.geo.Polygon
import org.gnit.lucenekmp.geo.Rectangle
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.geo.GeoTestUtil
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.QueryUtils
import org.gnit.lucenekmp.tests.util.RandomPicks
import org.gnit.lucenekmp.util.IOUtils
import kotlin.test.assertEquals

/**
 * Base test case for testing geospatial indexing and search functionality for [LatLonPoint] *
 */
abstract class BaseLatLonPointTestCase : BaseLatLonSpatialTestCase() {

    override fun newRectQuery(
        field: String,
        queryRelation: QueryRelation,
        minLon: Double,
        maxLon: Double,
        minLat: Double,
        maxLat: Double
    ): Query {
        return LatLonPoint.newGeometryQuery(
            field,
            queryRelation,
            Rectangle(minLat, maxLat, minLon, maxLon)
        )
    }

    override fun newLineQuery(field: String, queryRelation: QueryRelation, vararg lines: Any): Query {
        return LatLonPoint.newGeometryQuery(field, queryRelation, *Array(lines.size) { i -> lines[i] as Line })
    }

    override fun newPolygonQuery(field: String, queryRelation: QueryRelation, vararg polygons: Any): Query {
        return LatLonPoint.newGeometryQuery(field, queryRelation, *Array(polygons.size) { i -> polygons[i] as Polygon })
    }

    override fun newDistanceQuery(field: String, queryRelation: QueryRelation, circle: Any): Query {
        return LatLonPoint.newGeometryQuery(field, queryRelation, circle as Circle)
    }

    override fun newPointsQuery(field: String, queryRelation: QueryRelation, vararg points: Any): Query {
        val pointsArray = Array(points.size) { Point(0.0, 0.0) }
        for (i in points.indices) {
            val point = points[i] as DoubleArray
            pointsArray[i] = Point(point[0], point[1])
        }
        return LatLonPoint.newGeometryQuery(field, queryRelation, *pointsArray)
    }

    open fun testBoundingBoxQueriesEquivalence() {
        val numShapes = atLeast(8) // TODO reduced from 20 to 8 for dev speed

        val dir: Directory = newDirectory()
        val w = RandomIndexWriter(random(), dir)

        for (i in 0..<numShapes) {
            indexRandomShapes(w.w, arrayOf(nextShape()))
        }
        if (random().nextInt(5) == 0) {
            w.forceMerge(1)
        }

        ///// search //////
        val reader: IndexReader = w.reader
        w.close()
        val searcher: IndexSearcher = newSearcher(reader)

        val box = GeoTestUtil.nextBox()

        val q1 = LatLonPoint.newBoxQuery(FIELD_NAME, box.minLat, box.maxLat, box.minLon, box.maxLon)
        val q2 = LatLonPointQuery(FIELD_NAME, QueryRelation.INTERSECTS, box)
        assertEquals(searcher.count(q1), searcher.count(q2))

        IOUtils.close(w, reader, dir)
    }

    open fun testQueryEqualsAndHashcode() {
        val polygon = GeoTestUtil.nextPolygon()
        val queryRelation =
            RandomPicks.randomFrom(
                random(),
                arrayOf(QueryRelation.INTERSECTS, QueryRelation.DISJOINT)
            )
        val fieldName = "foo"
        val q1 = newPolygonQuery(fieldName, queryRelation, polygon)
        val q2 = newPolygonQuery(fieldName, queryRelation, polygon)
        QueryUtils.checkEqual(q1, q2)
        // different field name
        val q3 = newPolygonQuery("bar", queryRelation, polygon)
        QueryUtils.checkUnequal(q1, q3)
        // different query relation
        val newQueryRelation =
            RandomPicks.randomFrom(
                random(),
                arrayOf(QueryRelation.INTERSECTS, QueryRelation.DISJOINT)
            )
        val q4 = newPolygonQuery(fieldName, newQueryRelation, polygon)
        if (queryRelation == newQueryRelation) {
            QueryUtils.checkEqual(q1, q4)
        } else {
            QueryUtils.checkUnequal(q1, q4)
        }
        // different shape
        val newPolygon = GeoTestUtil.nextPolygon()
        val q5 = newPolygonQuery(fieldName, queryRelation, newPolygon)
        if (polygon == newPolygon) {
            QueryUtils.checkEqual(q1, q5)
        } else {
            QueryUtils.checkUnequal(q1, q5)
        }
    }
}
