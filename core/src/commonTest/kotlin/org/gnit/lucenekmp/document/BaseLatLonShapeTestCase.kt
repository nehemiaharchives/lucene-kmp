package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.document.ShapeField.QueryRelation
import org.gnit.lucenekmp.geo.Circle
import org.gnit.lucenekmp.geo.Line
import org.gnit.lucenekmp.geo.Point
import org.gnit.lucenekmp.geo.Polygon
import org.gnit.lucenekmp.geo.Rectangle
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.geo.GeoTestUtil
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.QueryUtils
import org.gnit.lucenekmp.tests.util.RandomPicks
import org.gnit.lucenekmp.util.IOUtils
import kotlin.test.assertEquals

/**
 * Base test case for testing geospatial indexing and search functionality for [LatLonShape] *
 */
abstract class BaseLatLonShapeTestCase : BaseLatLonSpatialTestCase() {

    override fun newRectQuery(
        field: String,
        queryRelation: QueryRelation,
        minLon: Double,
        maxLon: Double,
        minLat: Double,
        maxLat: Double
    ): Query {
        return LatLonShape.newBoxQuery(field, queryRelation, minLat, maxLat, minLon, maxLon)
    }

    override fun newLineQuery(field: String, queryRelation: QueryRelation, vararg lines: Any): Query {
        return LatLonShape.newLineQuery(field, queryRelation, *Array(lines.size) { i -> lines[i] as Line })
    }

    override fun newPolygonQuery(field: String, queryRelation: QueryRelation, vararg polygons: Any): Query {
        return LatLonShape.newPolygonQuery(field, queryRelation, *Array(polygons.size) { i -> polygons[i] as Polygon })
    }

    override fun newPointsQuery(field: String, queryRelation: QueryRelation, vararg points: Any): Query {
        return LatLonShape.newPointQuery(field, queryRelation, *Array(points.size) { i -> points[i] as DoubleArray })
    }

    override fun newDistanceQuery(field: String, queryRelation: QueryRelation, circle: Any): Query {
        return LatLonShape.newDistanceQuery(field, queryRelation, circle as Circle)
    }

    open fun testBoundingBoxQueriesEquivalence() {
        val numShapes = atLeast(3) // TODO reduced from 20 to 3 for dev speed

        val dir: Directory = newDirectory()
        val w = RandomIndexWriter(random(), dir)

        for (i in 0..<numShapes) {
            indexRandomShapes(w.w, nextShape())
        }
        if (random().nextBoolean()) {
            w.forceMerge(1)
        }

        ///// search //////
        val reader: IndexReader = w.getReader(true, false)
        w.close()
        val searcher: IndexSearcher = newSearcher(reader)

        val box = GeoTestUtil.nextBox()

        var q1 =
            newRectQuery(
                FIELD_NAME,
                QueryRelation.INTERSECTS,
                box.minLon,
                box.maxLon,
                box.minLat,
                box.maxLat
            )
        var q2 =
            newRectQuery(
                FIELD_NAME,
                QueryRelation.INTERSECTS,
                box.minLon,
                box.maxLon,
                box.minLat,
                box.maxLat
            )
        assertEquals(searcher.count(q1), searcher.count(q2))
        q1 =
            newRectQuery(
                FIELD_NAME,
                QueryRelation.WITHIN,
                box.minLon,
                box.maxLon,
                box.minLat,
                box.maxLat
            )
        q2 =
            newRectQuery(
                FIELD_NAME,
                QueryRelation.WITHIN,
                box.minLon,
                box.maxLon,
                box.minLat,
                box.maxLat
            )
        assertEquals(searcher.count(q1), searcher.count(q2))
        q1 =
            newRectQuery(
                FIELD_NAME,
                QueryRelation.CONTAINS,
                box.minLon,
                box.maxLon,
                box.minLat,
                box.maxLat
            )
        q2 =
            newRectQuery(
                FIELD_NAME,
                QueryRelation.CONTAINS,
                box.minLon,
                box.maxLon,
                box.minLat,
                box.maxLat
            )
        assertEquals(searcher.count(q1), searcher.count(q2))
        q1 =
            newRectQuery(
                FIELD_NAME,
                QueryRelation.DISJOINT,
                box.minLon,
                box.maxLon,
                box.minLat,
                box.maxLat
            )
        q2 =
            newRectQuery(
                FIELD_NAME,
                QueryRelation.DISJOINT,
                box.minLon,
                box.maxLon,
                box.minLat,
                box.maxLat
            )
        assertEquals(searcher.count(q1), searcher.count(q2))

        IOUtils.close(w, reader, dir)
    }

    open fun testBoxQueryEqualsAndHashcode() {
        val rectangle = GeoTestUtil.nextBox()
        val queryRelation = RandomPicks.randomFrom(random(), QueryRelation.entries.toTypedArray())
        val fieldName = "foo"
        val q1 =
            newRectQuery(
                fieldName,
                queryRelation,
                rectangle.minLon,
                rectangle.maxLon,
                rectangle.minLat,
                rectangle.maxLat
            )
        val q2 =
            newRectQuery(
                fieldName,
                queryRelation,
                rectangle.minLon,
                rectangle.maxLon,
                rectangle.minLat,
                rectangle.maxLat
            )
        QueryUtils.checkEqual(q1, q2)
        // different field name
        val q3 =
            newRectQuery(
                "bar",
                queryRelation,
                rectangle.minLon,
                rectangle.maxLon,
                rectangle.minLat,
                rectangle.maxLat
            )
        QueryUtils.checkUnequal(q1, q3)
        // different query relation
        val newQueryRelation = RandomPicks.randomFrom(random(), QueryRelation.entries.toTypedArray())
        val q4 =
            newRectQuery(
                fieldName,
                newQueryRelation,
                rectangle.minLon,
                rectangle.maxLon,
                rectangle.minLat,
                rectangle.maxLat
            )
        if (queryRelation == newQueryRelation) {
            QueryUtils.checkEqual(q1, q4)
        } else {
            QueryUtils.checkUnequal(q1, q4)
        }
        // different shape
        val newRectangle = GeoTestUtil.nextBox()
        val q5 =
            newRectQuery(
                fieldName,
                queryRelation,
                newRectangle.minLon,
                newRectangle.maxLon,
                newRectangle.minLat,
                newRectangle.maxLat
            )
        if (rectangle == newRectangle) {
            QueryUtils.checkEqual(q1, q5)
        } else {
            QueryUtils.checkUnequal(q1, q5)
        }
    }

    open fun testLineQueryEqualsAndHashcode() {
        val line = nextLine()
        val queryRelation = RandomPicks.randomFrom(random(), POINT_LINE_RELATIONS)
        val fieldName = "foo"
        val q1 = newLineQuery(fieldName, queryRelation, line)
        val q2 = newLineQuery(fieldName, queryRelation, line)
        QueryUtils.checkEqual(q1, q2)
        // different field name
        val q3 = newLineQuery("bar", queryRelation, line)
        QueryUtils.checkUnequal(q1, q3)
        // different query relation
        val newQueryRelation = RandomPicks.randomFrom(random(), POINT_LINE_RELATIONS)
        val q4 = newLineQuery(fieldName, newQueryRelation, line)
        if (queryRelation == newQueryRelation) {
            QueryUtils.checkEqual(q1, q4)
        } else {
            QueryUtils.checkUnequal(q1, q4)
        }
        // different shape
        val newLine = nextLine()
        val q5 = newLineQuery(fieldName, queryRelation, newLine)
        if (line == newLine) {
            QueryUtils.checkEqual(q1, q5)
        } else {
            QueryUtils.checkUnequal(q1, q5)
        }
    }

    open fun testPolygonQueryEqualsAndHashcode() {
        val polygon: Polygon = GeoTestUtil.nextPolygon()
        val queryRelation = RandomPicks.randomFrom(random(), QueryRelation.entries.toTypedArray())
        val fieldName = "foo"
        val q1 = newPolygonQuery(fieldName, queryRelation, polygon)
        val q2 = newPolygonQuery(fieldName, queryRelation, polygon)
        QueryUtils.checkEqual(q1, q2)
        // different field name
        val q3 = newPolygonQuery("bar", queryRelation, polygon)
        QueryUtils.checkUnequal(q1, q3)
        // different query relation
        val newQueryRelation = RandomPicks.randomFrom(random(), QueryRelation.entries.toTypedArray())
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
