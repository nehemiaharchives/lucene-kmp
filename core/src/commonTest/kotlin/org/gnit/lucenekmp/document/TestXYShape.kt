package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.document.ShapeField.QueryRelation
import org.gnit.lucenekmp.geo.Component2D
import org.gnit.lucenekmp.geo.Tessellator
import org.gnit.lucenekmp.geo.XYCircle
import org.gnit.lucenekmp.geo.XYGeometry
import org.gnit.lucenekmp.geo.XYLine
import org.gnit.lucenekmp.geo.XYPoint
import org.gnit.lucenekmp.geo.XYPolygon
import org.gnit.lucenekmp.geo.XYRectangle
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.geo.ShapeTestUtil
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.IOUtils
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals

/** Test case for indexing cartesian shapes and search by bounding box, lines, and polygons */
class TestXYShape : LuceneTestCase() {
    companion object {
        private const val FIELDNAME = "field"

        private fun addPolygonsToDoc(field: String, doc: Document, polygon: XYPolygon) {
            val fields = XYShape.createIndexableFields(field, polygon)
            for (f in fields) {
                doc.add(f)
            }
        }

        private fun addLineToDoc(field: String, doc: Document, line: XYLine) {
            val fields = XYShape.createIndexableFields(field, line)
            for (f in fields) {
                doc.add(f)
            }
        }

        private fun areBoxDisjoint(r1: XYRectangle, r2: XYRectangle): Boolean {
            return (r1.minX <= r2.minX && r1.minY <= r2.minY && r1.maxX >= r2.maxX && r1.maxY >= r2.maxY)
        }

        private fun toPolygon(r: XYRectangle): XYPolygon {
            return XYPolygon(
                floatArrayOf(r.minX, r.maxX, r.maxX, r.minX, r.minX),
                floatArrayOf(r.minY, r.minY, r.maxY, r.maxY, r.minY)
            )
        }
    }

    private fun newRectQuery(field: String, minX: Float, maxX: Float, minY: Float, maxY: Float): Query {
        return XYShape.newBoxQuery(field, QueryRelation.INTERSECTS, minX, maxX, minY, maxY)
    }

    /** test we can search for a point with a standard number of vertices */
    @Test
    fun testBasicIntersects() {
        val numVertices = TestUtil.nextInt(random(), 50, 100)
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)

        val p = ShapeTestUtil.createRegularPolygon(0.0, 90.0, atLeast(1000000).toDouble(), numVertices)
        var document = Document()
        addPolygonsToDoc(FIELDNAME, document, p)
        writer.addDocument(document)

        document = Document()
        val x = FloatArray(p.numPoints() - 1)
        val y = FloatArray(p.numPoints() - 1)
        for (i in x.indices) {
            x[i] = p.getPolyX(i)
            y[i] = p.getPolyY(i)
        }
        val l = XYLine(x, y)
        addLineToDoc(FIELDNAME, document, l)
        writer.addDocument(document)

        val reader: IndexReader = writer.reader
        writer.close()
        val searcher: IndexSearcher = newSearcher(reader)
        val minX = min(x[0], x[1])
        val minY = min(y[0], y[1])
        val maxX = max(x[0], x[1])
        val maxY = max(y[0], y[1])
        var q = newRectQuery(FIELDNAME, minX, maxX, minY, maxY)
        assertEquals(2, searcher.count(q))

        q = newRectQuery(FIELDNAME, p.minX - 1f, p.minX + 1f, p.minY - 1f, p.minY + 1f)
        assertEquals(0, searcher.count(q))

        q = XYShape.newPolygonQuery(
            FIELDNAME,
            QueryRelation.INTERSECTS,
            XYPolygon(
                floatArrayOf(minX, minX, maxX, maxX, minX),
                floatArrayOf(minY, maxY, maxY, minY, minY)
            )
        )
        assertEquals(2, searcher.count(q))

        q = XYShape.newLineQuery(
            FIELDNAME,
            QueryRelation.INTERSECTS,
            XYLine(floatArrayOf(minX, minX, maxX, maxX), floatArrayOf(minY, maxY, maxY, minY))
        )
        assertEquals(2, searcher.count(q))

        IOUtils.close(reader, dir)
    }

    @Test
    fun testBoundingBoxQueries() {
        val random = random()
        var r1 = ShapeTestUtil.nextBox(random)
        var r2 = ShapeTestUtil.nextBox(random)
        var p: XYPolygon
        while (true) {
            if (areBoxDisjoint(r1, r2)) {
                p = toPolygon(r2)
                try {
                    Tessellator.tessellate(p, random.nextBoolean())
                    break
                } catch (_: Exception) {
                    // ignore, try other combination
                }
            }
            r1 = ShapeTestUtil.nextBox(random)
            r2 = ShapeTestUtil.nextBox(random)
        }

        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random, dir)

        val document = Document()
        addPolygonsToDoc(FIELDNAME, document, p)
        writer.addDocument(document)

        val reader: IndexReader = writer.reader
        writer.close()
        val searcher: IndexSearcher = newSearcher(reader)
        var q: Query = newRectQuery(FIELDNAME, r2.minX, r2.maxX, r2.minY, r2.maxY)
        assertEquals(1, searcher.count(q))
        q = newRectQuery(FIELDNAME, r1.minX, r1.maxX, r1.minY, r1.maxY)
        assertEquals(1, searcher.count(q))
        q = XYShape.newBoxQuery(FIELDNAME, QueryRelation.WITHIN, r1.minX, r1.maxX, r1.minY, r1.maxY)
        assertEquals(1, searcher.count(q))

        IOUtils.close(reader, dir)
    }

    @Test
    fun testPointIndexAndDistanceQuery() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        val document = Document()
        val pX = ShapeTestUtil.nextFloat(random())
        val pY = ShapeTestUtil.nextFloat(random())
        val fields = XYShape.createIndexableFields(FIELDNAME, pX, pY)
        for (f in fields) {
            document.add(f)
        }
        writer.addDocument(document)

        val r: IndexReader = writer.reader
        writer.close()
        val s: IndexSearcher = newSearcher(r)
        val circle = ShapeTestUtil.nextCircle()
        val circle2D: Component2D = XYGeometry.create(circle)
        val expected: Int
        val expectedDisjoint: Int
        if (circle2D.contains(pX.toDouble(), pY.toDouble())) {
            expected = 1
            expectedDisjoint = 0
        } else {
            expected = 0
            expectedDisjoint = 1
        }

        var q: Query = XYShape.newDistanceQuery(FIELDNAME, QueryRelation.INTERSECTS, circle)
        assertEquals(expected, s.count(q))

        q = XYShape.newDistanceQuery(FIELDNAME, QueryRelation.WITHIN, circle)
        assertEquals(expected, s.count(q))

        q = XYShape.newDistanceQuery(FIELDNAME, QueryRelation.DISJOINT, circle)
        assertEquals(expectedDisjoint, s.count(q))

        IOUtils.close(r, dir)
    }

    @Test
    fun testContainsWrappingBooleanQuery() {
        val ys = floatArrayOf(-30f, -30f, 30f, 30f, -30f)
        val xs = floatArrayOf(-30f, 30f, 30f, -30f, -30f)
        val polygon = XYPolygon(xs, ys)

        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        val document = Document()
        addPolygonsToDoc(FIELDNAME, document, polygon)
        writer.addDocument(document)

        val r: IndexReader = writer.reader
        writer.close()
        val s: IndexSearcher = newSearcher(r)

        val geometries = arrayOf<XYGeometry>(XYRectangle(0f, 1f, 0f, 1f), XYPoint(4f, 4f))
        val q = XYShape.newGeometryQuery(FIELDNAME, QueryRelation.CONTAINS, *geometries)
        val topDocs: TopDocs = s.search(q, 1)
        assertEquals(1, topDocs.scoreDocs.size)
        assertEquals(1.0, topDocs.scoreDocs[0].score.toDouble(), 0.0)
        IOUtils.close(r, dir)
    }

    @Test
    fun testContainsIndexedGeometryCollection() {
        val dir: Directory = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        val polygon = XYPolygon(
            floatArrayOf(-132f, 132f, 132f, -132f, -132f),
            floatArrayOf(-64f, -64f, 64f, 64f, -64f)
        )
        val polygonFields = XYShape.createIndexableFields(FIELDNAME, polygon)
        val pointFields = XYShape.createIndexableFields(FIELDNAME, 5f, 5f)
        val numDocs = random().nextInt(1000)
        for (i in 0..<numDocs) {
            val doc = Document()
            for (f in polygonFields) {
                doc.add(f)
            }
            for (j in 0..<10) {
                for (f in pointFields) {
                    doc.add(f)
                }
            }
            w.addDocument(doc)
        }
        w.forceMerge(1)

        val reader: IndexReader = w.reader
        w.close()
        val searcher: IndexSearcher = newSearcher(reader)
        val polygonQuery = XYPolygon(
            floatArrayOf(4f, 6f, 6f, 4f, 4f),
            floatArrayOf(4f, 4f, 6f, 6f, 4f)
        )
        var query: Query = XYShape.newGeometryQuery(FIELDNAME, QueryRelation.CONTAINS, polygonQuery)
        assertEquals(0, searcher.count(query))

        val rectangle = XYRectangle(4f, 6f, 4f, 6f)
        query = XYShape.newGeometryQuery(FIELDNAME, QueryRelation.CONTAINS, rectangle)
        assertEquals(0, searcher.count(query))

        val circle = XYCircle(5f, 5f, 1f)
        query = XYShape.newGeometryQuery(FIELDNAME, QueryRelation.CONTAINS, circle)
        assertEquals(0, searcher.count(query))

        IOUtils.close(reader, dir)
    }
}
