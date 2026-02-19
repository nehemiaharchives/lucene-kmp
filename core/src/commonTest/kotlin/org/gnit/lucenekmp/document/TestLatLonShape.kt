package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.document.ShapeField.QueryRelation
import org.gnit.lucenekmp.geo.Circle
import org.gnit.lucenekmp.geo.GeoEncodingUtils
import org.gnit.lucenekmp.geo.GeoUtils
import org.gnit.lucenekmp.geo.LatLonGeometry
import org.gnit.lucenekmp.geo.Line
import org.gnit.lucenekmp.geo.Point
import org.gnit.lucenekmp.geo.Polygon
import org.gnit.lucenekmp.geo.Rectangle
import org.gnit.lucenekmp.geo.Tessellator
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.SerialMergeScheduler
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.geo.GeoTestUtil
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.RandomNumbers
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.IOUtils
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Test case for indexing polygons and querying by bounding box */
class TestLatLonShape : LuceneTestCase() {
    companion object {
        private const val FIELDNAME = "field"

        /** quantizes a latitude value to be consistent with index encoding */
        private fun quantizeLat(rawLat: Double): Double {
            return GeoEncodingUtils.decodeLatitude(GeoEncodingUtils.encodeLatitude(rawLat))
        }

        /** quantizes a longitude value to be consistent with index encoding */
        private fun quantizeLon(rawLon: Double): Double {
            return GeoEncodingUtils.decodeLongitude(GeoEncodingUtils.encodeLongitude(rawLon))
        }
    }

    private fun addPolygonsToDoc(field: String, doc: Document, polygon: Polygon) {
        val fields = LatLonShape.createIndexableFields(field, polygon, random().nextBoolean())
        for (f in fields) {
            doc.add(f)
        }
    }

    private fun addLineToDoc(field: String, doc: Document, line: Line) {
        val fields = LatLonShape.createIndexableFields(field, line)
        for (f in fields) {
            doc.add(f)
        }
    }

    private fun newRectQuery(field: String, minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): Query {
        return LatLonShape.newBoxQuery(field, QueryRelation.INTERSECTS, minLat, maxLat, minLon, maxLon)
    }

    @Ignore
    @Test
    fun testRandomPolygons() {
        var numVertices: Int
        val numPolys = RandomNumbers.randomIntBetween(random(), 10, 20)

        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)

        var polygon: Polygon
        var document: Document
        for (i in 0..<numPolys) {
            document = Document()
            numVertices = TestUtil.nextInt(random(), 100000, 200000)
            polygon = GeoTestUtil.createRegularPolygon(0.0, 0.0, atLeast(1000000).toDouble(), numVertices)
            addPolygonsToDoc(FIELDNAME, document, polygon)
            writer.addDocument(document)
        }

        val reader: IndexReader = writer.reader
        val searcher = newSearcher(reader)
        assertEquals(0, searcher.count(newRectQuery("field", -89.9, -89.8, -179.9, -179.8)))

        reader.close()
        writer.close()
        dir.close()
    }

    /** test we can search for a point with a standard number of vertices */
    @Test
    fun testBasicIntersects() {
        val numVertices = TestUtil.nextInt(random(), 50, 100)
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)

        val p = GeoTestUtil.createRegularPolygon(0.0, 90.0, atLeast(1000000).toDouble(), numVertices)
        var document = Document()
        addPolygonsToDoc(FIELDNAME, document, p)
        writer.addDocument(document)

        document = Document()
        val lats = DoubleArray(p.numPoints() - 1)
        val lons = DoubleArray(p.numPoints() - 1)
        for (i in lats.indices) {
            lats[i] = p.getPolyLat(i)
            lons[i] = p.getPolyLon(i)
        }
        val l = Line(lats, lons)
        addLineToDoc(FIELDNAME, document, l)
        writer.addDocument(document)

        val reader: IndexReader = writer.reader
        writer.close()
        val searcher = newSearcher(reader)
        val minLat = min(lats[0], lats[1])
        val minLon = min(lons[0], lons[1])
        val maxLat = max(lats[0], lats[1])
        val maxLon = max(lons[0], lons[1])
        var q = newRectQuery(FIELDNAME, minLat, maxLat, minLon, maxLon)
        assertEquals(2, searcher.count(q))

        q = newRectQuery(FIELDNAME, p.minLat - 1.0, p.minLat + 1.0, p.minLon - 1.0, p.minLon + 1.0)
        assertEquals(0, searcher.count(q))

        IOUtils.close(reader, dir)
    }

    @Test
    fun testBasicContains() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)

        var polyLats = doubleArrayOf(-10.0, -10.0, 10.0, 10.0, -10.0)
        var polyLons = doubleArrayOf(-10.0, 10.0, 10.0, -10.0, -10.0)
        val p = Polygon(polyLats, polyLons)
        var document = Document()
        addPolygonsToDoc(FIELDNAME, document, p)
        writer.addDocument(document)

        document = Document()
        val lats = DoubleArray(p.numPoints() - 1)
        val lons = DoubleArray(p.numPoints() - 1)
        for (i in lats.indices) {
            lats[i] = p.getPolyLat(i)
            lons[i] = p.getPolyLon(i)
        }
        val l = Line(lats, lons)
        addLineToDoc(FIELDNAME, document, l)
        writer.addDocument(document)

        val reader: IndexReader = writer.reader
        writer.close()
        var searcher = newSearcher(reader)
        polyLats = doubleArrayOf(-5.0, -5.0, 5.0, 5.0, -5.0)
        polyLons = doubleArrayOf(-5.0, 5.0, 5.0, -5.0, -5.0)
        val queryPolygon = Polygon(polyLats, polyLons)
        var q: Query = LatLonShape.newPolygonQuery(FIELDNAME, QueryRelation.CONTAINS, queryPolygon)
        assertEquals(1, searcher.count(q))

        searcher = newSearcher(reader)
        q = LatLonShape.newBoxQuery(FIELDNAME, QueryRelation.CONTAINS, 0.0, 0.0, 0.0, 0.0)
        assertEquals(1, searcher.count(q))
        IOUtils.close(reader, dir)
    }

    /** test random polygons with a single hole */
    @Test
    fun testPolygonWithHole() {
        val numVertices = TestUtil.nextInt(random(), 50, 100)
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)

        val inner =
            Polygon(
                doubleArrayOf(-1.0, -1.0, 1.0, 1.0, -1.0),
                doubleArrayOf(-91.0, -89.0, -89.0, -91.0, -91.0)
            )
        val outer = GeoTestUtil.createRegularPolygon(0.0, -90.0, atLeast(1000000).toDouble(), numVertices)

        val document = Document()
        addPolygonsToDoc(FIELDNAME, document, Polygon(outer.getPolyLats(), outer.getPolyLons(), inner))
        writer.addDocument(document)

        val reader: IndexReader = writer.reader
        writer.close()
        val searcher = newSearcher(reader)

        val q = newRectQuery(
            FIELDNAME,
            inner.minLat + 1e-6,
            inner.maxLat - 1e-6,
            inner.minLon + 1e-6,
            inner.maxLon - 1e-6
        )
        assertEquals(0, searcher.count(q))

        IOUtils.close(reader, dir)
    }

    /** test we can search for a point with a large number of vertices */
    @Test
    fun testLargeVertexPolygon() {
        val numVertices =
            if (TEST_NIGHTLY) TestUtil.nextInt(random(), 200000, 500000)
            else TestUtil.nextInt(random(), 20000, 50000)
        val iwc: IndexWriterConfig = newIndexWriterConfig()
        iwc.mergeScheduler = SerialMergeScheduler()
        val mbd = iwc.maxBufferedDocs
        if (mbd != -1 && mbd < numVertices / 100) {
            iwc.maxBufferedDocs = numVertices / 100
        }
        val dir: Directory = newFSDirectory(createTempDir(this::class.simpleName!!))
        val writer = IndexWriter(dir, iwc)

        val p = GeoTestUtil.createRegularPolygon(0.0, 90.0, atLeast(1000000).toDouble(), numVertices)
        var document = Document()
        addPolygonsToDoc(FIELDNAME, document, p)
        writer.addDocument(document)

        val inner =
            Polygon(
                doubleArrayOf(-1.0, -1.0, 1.0, 1.0, -1.0),
                doubleArrayOf(-91.0, -89.0, -89.0, -91.0, -91.0)
            )
        val outer = GeoTestUtil.createRegularPolygon(0.0, -90.0, atLeast(1000000).toDouble(), numVertices)

        document = Document()
        addPolygonsToDoc(FIELDNAME, document, Polygon(outer.getPolyLats(), outer.getPolyLons(), inner))
        writer.addDocument(document)

        val reader: IndexReader = DirectoryReader.open(writer)
        writer.close()
        val searcher = newSearcher(reader)
        var q = newRectQuery(FIELDNAME, -1.0, 1.0, p.minLon, p.maxLon)
        assertEquals(1, searcher.count(q))

        q = newRectQuery(FIELDNAME, p.minLat - 1.0, p.minLat + 1.0, p.minLon - 1.0, p.minLon + 1.0)
        assertEquals(0, searcher.count(q))

        q = newRectQuery(
            FIELDNAME,
            inner.minLat + 1e-6,
            inner.maxLat - 1e-6,
            inner.minLon + 1e-6,
            inner.maxLon - 1e-6
        )
        assertEquals(0, searcher.count(q))

        IOUtils.close(reader, dir)
    }

    @Test
    fun testWithinDateLine() {
        val dir: Directory = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        val doc = Document()

        val indexPoly1 =
            Polygon(
                doubleArrayOf(-7.5, 15.0, 15.0, 0.0, -7.5),
                doubleArrayOf(-180.0, -180.0, -176.0, -176.0, -180.0)
            )

        val indexPoly2 =
            Polygon(
                doubleArrayOf(15.0, -7.5, -15.0, -10.0, 15.0, 15.0),
                doubleArrayOf(180.0, 180.0, 176.0, 174.0, 176.0, 180.0)
            )

        addPolygonsToDoc("test", doc, indexPoly1)
        addPolygonsToDoc("test", doc, indexPoly2)

        w.addDocument(doc)
        w.forceMerge(1)

        val reader: IndexReader = w.reader
        w.close()
        val searcher = newSearcher(reader)

        val searchPoly =
            arrayOf(
                Polygon(
                    doubleArrayOf(-20.0, 20.0, 20.0, -20.0, -20.0),
                    doubleArrayOf(-180.0, -180.0, -170.0, -170.0, -180.0)
                ),
                Polygon(
                    doubleArrayOf(20.0, -20.0, -20.0, 20.0, 20.0),
                    doubleArrayOf(180.0, 180.0, 170.0, 170.0, 180.0)
                )
            )

        var q: Query = LatLonShape.newPolygonQuery("test", QueryRelation.WITHIN, *searchPoly)
        assertEquals(1, searcher.count(q))

        q = LatLonShape.newPolygonQuery("test", QueryRelation.INTERSECTS, *searchPoly)
        assertEquals(1, searcher.count(q))

        q = LatLonShape.newPolygonQuery("test", QueryRelation.DISJOINT, *searchPoly)
        assertEquals(0, searcher.count(q))

        q = LatLonShape.newPolygonQuery("test", QueryRelation.CONTAINS, *searchPoly)
        assertEquals(0, searcher.count(q))

        q = LatLonShape.newBoxQuery("test", QueryRelation.WITHIN, -20.0, 20.0, 170.0, -170.0)
        assertEquals(1, searcher.count(q))

        q = LatLonShape.newBoxQuery("test", QueryRelation.INTERSECTS, -20.0, 20.0, 170.0, -170.0)
        assertEquals(1, searcher.count(q))

        q = LatLonShape.newBoxQuery("test", QueryRelation.DISJOINT, -20.0, 20.0, 170.0, -170.0)
        assertEquals(0, searcher.count(q))

        q = LatLonShape.newBoxQuery("test", QueryRelation.CONTAINS, -20.0, 20.0, 170.0, -170.0)
        assertEquals(0, searcher.count(q))

        IOUtils.close(w, reader, dir)
    }

    @Test
    fun testContainsDateLine() {
        val dir: Directory = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        val doc = Document()

        val indexPoly1 =
            Polygon(
                doubleArrayOf(-2.0, -2.0, 2.0, 2.0, -2.0),
                doubleArrayOf(178.0, 180.0, 180.0, 178.0, 178.0)
            )

        val indexPoly2 =
            Polygon(
                doubleArrayOf(-2.0, -2.0, 2.0, 2.0, -2.0),
                doubleArrayOf(-180.0, -178.0, -178.0, -180.0, -180.0)
            )

        addPolygonsToDoc("test", doc, indexPoly1)
        addPolygonsToDoc("test", doc, indexPoly2)

        w.addDocument(doc)
        w.forceMerge(1)

        val reader: IndexReader = w.reader
        w.close()
        val searcher = newSearcher(reader)

        val searchPoly =
            arrayOf(
                Polygon(
                    doubleArrayOf(-1.0, -1.0, 1.0, 1.0, -1.0),
                    doubleArrayOf(179.0, 180.0, 180.0, 179.0, 179.0)
                ),
                Polygon(
                    doubleArrayOf(-1.0, -1.0, 1.0, 1.0, -1.0),
                    doubleArrayOf(-180.0, -179.0, -179.0, -180.0, -180.0)
                )
            )
        var q: Query

        q = LatLonShape.newPolygonQuery("test", QueryRelation.INTERSECTS, *searchPoly)
        assertEquals(1, searcher.count(q))

        q = LatLonShape.newPolygonQuery("test", QueryRelation.DISJOINT, *searchPoly)
        assertEquals(0, searcher.count(q))

        q = LatLonShape.newPolygonQuery("test", QueryRelation.WITHIN, *searchPoly)
        assertEquals(0, searcher.count(q))

        q = LatLonShape.newBoxQuery("test", QueryRelation.INTERSECTS, -1.0, 1.0, 179.0, -179.0)
        assertEquals(1, searcher.count(q))

        q = LatLonShape.newBoxQuery("test", QueryRelation.WITHIN, -1.0, 1.0, 179.0, -179.0)
        assertEquals(0, searcher.count(q))

        q = LatLonShape.newBoxQuery("test", QueryRelation.DISJOINT, -1.0, 1.0, 179.0, -179.0)
        assertEquals(0, searcher.count(q))

        IOUtils.close(w, reader, dir)
    }

    @Test
    fun testLUCENE8454() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)

        val poly =
            Polygon(
                doubleArrayOf(-1.490648725633769E-132, 90.0, 90.0, -1.490648725633769E-132),
                doubleArrayOf(0.0, 0.0, 180.0, 0.0)
            )

        val document = Document()
        addPolygonsToDoc(FIELDNAME, document, poly)
        writer.addDocument(document)

        val reader: IndexReader = writer.reader
        writer.close()
        val searcher = newSearcher(reader)

        val q =
            LatLonShape.newBoxQuery(
                FIELDNAME,
                QueryRelation.DISJOINT,
                -29.46555603761226,
                0.0,
                8.381903171539307E-8,
                0.9999999403953552
            )
        assertEquals(1, searcher.count(q))

        IOUtils.close(reader, dir)
    }

    @Test
    fun testPointIndexAndQuery() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        val document = Document()
        var p = GeoTestUtil.nextPoint()
        val qLat = GeoEncodingUtils.decodeLatitude(GeoEncodingUtils.encodeLatitude(p.lat))
        val qLon = GeoEncodingUtils.decodeLongitude(GeoEncodingUtils.encodeLongitude(p.lon))
        p = Point(qLat, qLon)
        val fields = LatLonShape.createIndexableFields(FIELDNAME, p.lat, p.lon)
        for (f in fields) {
            document.add(f)
        }
        writer.addDocument(document)

        val r: IndexReader = writer.reader
        writer.close()
        val s = newSearcher(r)

        val q = LatLonShape.newPointQuery(FIELDNAME, QueryRelation.INTERSECTS, doubleArrayOf(p.lat, p.lon))
        assertEquals(1, s.count(q))
        IOUtils.close(r, dir)
    }

    @Test
    fun testLUCENE8669() {
        val dir: Directory = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        val doc = Document()

        val indexPoly1 =
            Polygon(
                doubleArrayOf(-7.5, 15.0, 15.0, 0.0, -7.5),
                doubleArrayOf(-180.0, -180.0, -176.0, -176.0, -180.0)
            )

        val indexPoly2 =
            Polygon(
                doubleArrayOf(15.0, -7.5, -15.0, -10.0, 15.0, 15.0),
                doubleArrayOf(180.0, 180.0, 176.0, 174.0, 176.0, 180.0)
            )

        addPolygonsToDoc(FIELDNAME, doc, indexPoly1)
        addPolygonsToDoc(FIELDNAME, doc, indexPoly2)
        w.addDocument(doc)
        w.forceMerge(1)

        val reader: IndexReader = w.reader
        w.close()
        val searcher = newSearcher(reader)

        val searchPoly =
            arrayOf(
                Polygon(
                    doubleArrayOf(-20.0, 20.0, 20.0, -20.0, -20.0),
                    doubleArrayOf(-180.0, -180.0, -170.0, -170.0, -180.0)
                ),
                Polygon(
                    doubleArrayOf(20.0, -20.0, -20.0, 20.0, 20.0),
                    doubleArrayOf(180.0, 180.0, 170.0, 170.0, 180.0)
                )
            )

        var q: Query = LatLonShape.newPolygonQuery(FIELDNAME, QueryRelation.WITHIN, *searchPoly)
        assertEquals(1, searcher.count(q))

        q = LatLonShape.newPolygonQuery(FIELDNAME, QueryRelation.INTERSECTS, *searchPoly)
        assertEquals(1, searcher.count(q))

        q = LatLonShape.newPolygonQuery(FIELDNAME, QueryRelation.DISJOINT, *searchPoly)
        assertEquals(0, searcher.count(q))

        q = LatLonShape.newBoxQuery(FIELDNAME, QueryRelation.WITHIN, -20.0, 20.0, 170.0, -170.0)
        assertEquals(1, searcher.count(q))

        q = LatLonShape.newBoxQuery(FIELDNAME, QueryRelation.INTERSECTS, -20.0, 20.0, 170.0, -170.0)
        assertEquals(1, searcher.count(q))

        q = LatLonShape.newBoxQuery(FIELDNAME, QueryRelation.DISJOINT, -20.0, 20.0, 170.0, -170.0)
        assertEquals(0, searcher.count(q))

        IOUtils.close(w, reader, dir)
    }

    @Test
    fun testLUCENE8679() {
        val alat = 1.401298464324817E-45
        val alon = 24.76789767911785
        val blat = 34.26468306870807
        val blon = -52.67048754768767
        val polygon =
            Polygon(
                doubleArrayOf(-14.448264200949083, 0.0, 0.0, -14.448264200949083, -14.448264200949083),
                doubleArrayOf(0.9999999403953552, 0.9999999403953552, 124.50086371762484, 124.50086371762484, 0.9999999403953552)
            )
        val polygon2D = LatLonGeometry.create(polygon)
        var intersects =
            polygon2D.intersectsTriangle(
                quantizeLon(alon), quantizeLat(blat),
                quantizeLon(blon), quantizeLat(blat),
                quantizeLon(alon), quantizeLat(alat)
            )

        assertTrue(intersects)

        intersects =
            polygon2D.intersectsTriangle(
                quantizeLon(alon), quantizeLat(blat),
                quantizeLon(alon), quantizeLat(alat),
                quantizeLon(blon), quantizeLat(blat)
            )

        assertTrue(intersects)
    }

    @Test
    fun testTriangleTouchingEdges() {
        val p = Polygon(doubleArrayOf(0.0, 0.0, 1.0, 1.0, 0.0), doubleArrayOf(0.0, 1.0, 1.0, 0.0, 0.0))
        val polygon2D = LatLonGeometry.create(p)
        var containsTriangle =
            polygon2D.containsTriangle(
                quantizeLon(0.5), quantizeLat(0.0),
                quantizeLon(1.0), quantizeLat(0.5),
                quantizeLon(0.5), quantizeLat(1.0)
            )
        var intersectsTriangle =
            polygon2D.intersectsTriangle(
                quantizeLon(0.5), quantizeLat(0.0),
                quantizeLon(1.0), quantizeLat(0.5),
                quantizeLon(0.5), quantizeLat(1.0)
            )
        assertTrue(intersectsTriangle)
        assertTrue(containsTriangle)

        containsTriangle =
            polygon2D.containsTriangle(
                quantizeLon(0.5), quantizeLat(0.0),
                quantizeLon(1.0), quantizeLat(0.5),
                quantizeLon(0.5), quantizeLat(0.75)
            )
        intersectsTriangle =
            polygon2D.intersectsTriangle(
                quantizeLon(0.5), quantizeLat(0.0),
                quantizeLon(1.0), quantizeLat(0.5),
                quantizeLon(0.5), quantizeLat(0.75)
            )
        assertTrue(intersectsTriangle)
        assertTrue(containsTriangle)

        containsTriangle =
            polygon2D.containsTriangle(
                quantizeLon(0.5), quantizeLat(0.5),
                quantizeLon(0.5), quantizeLat(0.0),
                quantizeLon(0.75), quantizeLat(0.75)
            )
        intersectsTriangle =
            polygon2D.intersectsTriangle(
                quantizeLon(0.5), quantizeLat(0.0),
                quantizeLon(1.0), quantizeLat(0.5),
                quantizeLon(0.5), quantizeLat(0.75)
            )
        assertTrue(intersectsTriangle)
        assertTrue(containsTriangle)

        containsTriangle =
            polygon2D.containsTriangle(
                quantizeLon(1.0), quantizeLat(0.5),
                quantizeLon(2.0), quantizeLat(0.0),
                quantizeLon(2.0), quantizeLat(2.0)
            )
        intersectsTriangle =
            polygon2D.intersectsTriangle(
                quantizeLon(1.0), quantizeLat(0.5),
                quantizeLon(2.0), quantizeLat(0.0),
                quantizeLon(2.0), quantizeLat(2.0)
            )
        assertTrue(intersectsTriangle)
        assertFalse(containsTriangle)

        containsTriangle =
            polygon2D.containsTriangle(
                quantizeLon(0.5), quantizeLat(0.0),
                quantizeLon(2.0), quantizeLat(0.5),
                quantizeLon(0.5), quantizeLat(1.0)
            )
        intersectsTriangle =
            polygon2D.intersectsTriangle(
                quantizeLon(0.5), quantizeLat(0.0),
                quantizeLon(2.0), quantizeLat(0.5),
                quantizeLon(0.5), quantizeLat(1.0)
            )
        assertTrue(intersectsTriangle)
        assertFalse(containsTriangle)

        containsTriangle =
            polygon2D.containsTriangle(
                quantizeLon(0.0), quantizeLat(0.0),
                quantizeLon(0.0), quantizeLat(1.0),
                quantizeLon(0.5), quantizeLat(0.5)
            )
        intersectsTriangle =
            polygon2D.intersectsTriangle(
                quantizeLon(0.0), quantizeLat(0.0),
                quantizeLon(0.0), quantizeLat(1.0),
                quantizeLon(0.5), quantizeLat(0.5)
            )
        assertTrue(intersectsTriangle)
        assertTrue(containsTriangle)

        containsTriangle =
            polygon2D.containsTriangle(
                quantizeLon(0.0), quantizeLat(1.0),
                quantizeLon(1.5), quantizeLat(1.5),
                quantizeLon(1.0), quantizeLat(1.0)
            )
        intersectsTriangle =
            polygon2D.intersectsTriangle(
                quantizeLon(0.0), quantizeLat(1.0),
                quantizeLon(1.5), quantizeLat(1.5),
                quantizeLon(1.0), quantizeLat(1.0)
            )
        assertTrue(intersectsTriangle)
        assertFalse(containsTriangle)
    }

    @Test
    fun testLUCENE8736() {
        val dir: Directory = newDirectory()
        val w = RandomIndexWriter(random(), dir)

        val indexPoly1 = Polygon(doubleArrayOf(4.0, 4.0, 3.0, 3.0, 4.0), doubleArrayOf(3.0, 4.0, 4.0, 3.0, 3.0))
        val indexPoly2 = Polygon(doubleArrayOf(2.0, 2.0, 1.0, 1.0, 2.0), doubleArrayOf(6.0, 7.0, 7.0, 6.0, 6.0))
        val indexPoly3 = Polygon(doubleArrayOf(1.0, 1.0, 0.0, 0.0, 1.0), doubleArrayOf(3.0, 4.0, 4.0, 3.0, 3.0))
        val indexPoly4 = Polygon(doubleArrayOf(2.0, 2.0, 1.0, 1.0, 2.0), doubleArrayOf(0.0, 1.0, 1.0, 0.0, 0.0))

        var doc = Document()
        addPolygonsToDoc(FIELDNAME, doc, indexPoly1)
        w.addDocument(doc)
        doc = Document()
        addPolygonsToDoc(FIELDNAME, doc, indexPoly2)
        w.addDocument(doc)
        doc = Document()
        addPolygonsToDoc(FIELDNAME, doc, indexPoly3)
        w.addDocument(doc)
        doc = Document()
        addPolygonsToDoc(FIELDNAME, doc, indexPoly4)
        w.addDocument(doc)
        w.forceMerge(1)

        val reader: IndexReader = w.reader
        w.close()
        val searcher = newSearcher(reader)

        val searchPoly =
            arrayOf(Polygon(doubleArrayOf(4.0, 4.0, 0.0, 0.0, 4.0), doubleArrayOf(0.0, 7.0, 7.0, 0.0, 0.0)))

        val q = LatLonShape.newPolygonQuery(FIELDNAME, QueryRelation.WITHIN, *searchPoly)
        assertEquals(4, searcher.count(q))

        IOUtils.close(w, reader, dir)
    }

    @Test
    fun testTriangleCrossingPolygonVertices() {
        val p = Polygon(doubleArrayOf(0.0, 0.0, -5.0, -10.0, -5.0, 0.0), doubleArrayOf(-1.0, 1.0, 5.0, 0.0, -5.0, -1.0))
        val polygon2D = LatLonGeometry.create(p)
        val intersectsTriangle =
            polygon2D.intersectsTriangle(
                quantizeLon(-5.0), quantizeLat(0.0),
                quantizeLon(10.0), quantizeLat(0.0),
                quantizeLon(-5.0), quantizeLat(-15.0)
            )
        assertTrue(intersectsTriangle)
    }

    @Test
    fun testLineCrossingPolygonVertices() {
        val p = Polygon(doubleArrayOf(0.0, -1.0, 0.0, 1.0, 0.0), doubleArrayOf(-1.0, 0.0, 1.0, 0.0, -1.0))
        val polygon2D = LatLonGeometry.create(p)
        val intersectsTriangle =
            polygon2D.intersectsTriangle(
                quantizeLon(-1.5), quantizeLat(0.0),
                quantizeLon(1.5), quantizeLat(0.0),
                quantizeLon(-1.5), quantizeLat(0.0)
            )
        assertTrue(intersectsTriangle)
    }

    @Test
    fun testLineSharedLine() {
        val l = Line(doubleArrayOf(0.0, 0.0, 0.0, 0.0), doubleArrayOf(-2.0, -1.0, 0.0, 1.0))
        val l2d = LatLonGeometry.create(l)
        val intersectsLine = l2d.intersectsLine(quantizeLon(-5.0), quantizeLat(0.0), quantizeLon(5.0), quantizeLat(0.0))
        assertTrue(intersectsLine)
    }

    @Test
    fun testLUCENE9055() {
        val dir: Directory = newDirectory()
        val w = RandomIndexWriter(random(), dir)

        val indexPoly1 = Polygon(doubleArrayOf(5.0, 6.0, 10.0, 10.0, 5.0), doubleArrayOf(5.0, 10.0, 10.0, 5.0, 5.0))
        val indexPoly2 = Polygon(doubleArrayOf(6.0, 6.0, 9.0, 9.0, 6.0), doubleArrayOf(6.0, 9.0, 9.0, 6.0, 6.0))

        var doc = Document()
        addPolygonsToDoc(FIELDNAME, doc, indexPoly1)
        w.addDocument(doc)
        doc = Document()
        addPolygonsToDoc(FIELDNAME, doc, indexPoly2)
        w.addDocument(doc)
        w.forceMerge(1)

        val reader: IndexReader = w.reader
        w.close()
        val searcher = newSearcher(reader)

        val searchLine = Line(doubleArrayOf(0.0, 5.0, 7.0), doubleArrayOf(0.0, 5.0, 7.0))

        val q = LatLonShape.newLineQuery(FIELDNAME, QueryRelation.INTERSECTS, searchLine)
        assertEquals(2, searcher.count(q))

        IOUtils.close(w, reader, dir)
    }

    @Test
    fun testIndexAndQuerySamePolygon() {
        val dir: Directory = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        val doc = Document()
        var polygon: Polygon
        while (true) {
            try {
                polygon = GeoTestUtil.nextPolygon()
                val lats = DoubleArray(polygon.numPoints())
                val lons = DoubleArray(polygon.numPoints())
                for (i in 0..<polygon.numPoints()) {
                    lats[i] = GeoEncodingUtils.decodeLatitude(GeoEncodingUtils.encodeLatitude(polygon.getPolyLat(i)))
                    lons[i] = GeoEncodingUtils.decodeLongitude(GeoEncodingUtils.encodeLongitude(polygon.getPolyLon(i)))
                }
                polygon = Polygon(lats, lons)
                Tessellator.tessellate(polygon, random().nextBoolean())
                break
            } catch (_: Exception) {
            }
        }
        addPolygonsToDoc(FIELDNAME, doc, polygon)
        w.addDocument(doc)
        w.forceMerge(1)

        val reader: IndexReader = w.reader
        w.close()
        val searcher = newSearcher(reader)

        var q: Query = LatLonShape.newPolygonQuery(FIELDNAME, QueryRelation.WITHIN, polygon)
        assertEquals(1, searcher.count(q))
        q = LatLonShape.newPolygonQuery(FIELDNAME, QueryRelation.INTERSECTS, polygon)
        assertEquals(1, searcher.count(q))
        q = LatLonShape.newPolygonQuery(FIELDNAME, QueryRelation.DISJOINT, polygon)
        assertEquals(0, searcher.count(q))

        IOUtils.close(w, reader, dir)
    }

    @Test
    fun testPointIndexAndDistanceQuery() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        val document = Document()
        val p = GeoTestUtil.nextPoint()
        val fields = LatLonShape.createIndexableFields(FIELDNAME, p.lat, p.lon)
        for (f in fields) {
            document.add(f)
        }
        writer.addDocument(document)

        val r: IndexReader = writer.reader
        writer.close()
        val s = newSearcher(r)

        val lat = GeoTestUtil.nextLatitude()
        val lon = GeoTestUtil.nextLongitude()
        val radiusMeters = random().nextDouble() * GeoUtils.EARTH_MEAN_RADIUS_METERS * PI / 2.0 + 1.0
        val circle = Circle(lat, lon, radiusMeters)
        val circle2D = LatLonGeometry.create(circle)
        val expected: Int
        val expectedDisjoint: Int
        if (circle2D.contains(p.lon, p.lat)) {
            expected = 1
            expectedDisjoint = 0
        } else {
            expected = 0
            expectedDisjoint = 1
        }

        var q = LatLonShape.newDistanceQuery(FIELDNAME, QueryRelation.INTERSECTS, circle)
        assertEquals(expected, s.count(q))

        q = LatLonShape.newDistanceQuery(FIELDNAME, QueryRelation.WITHIN, circle)
        assertEquals(expected, s.count(q))

        q = LatLonShape.newDistanceQuery(FIELDNAME, QueryRelation.DISJOINT, circle)
        assertEquals(expectedDisjoint, s.count(q))

        IOUtils.close(r, dir)
    }

    @Test
    fun testLucene9239() {
        val lats = doubleArrayOf(-22.350172194105966, 90.0, 90.0, -22.350172194105966, -22.350172194105966)
        val lons =
            doubleArrayOf(
                49.931598911327825,
                49.931598911327825,
                51.40819689137876,
                51.408196891378765,
                49.931598911327825
            )
        val polygon = Polygon(lats, lons)

        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        val document = Document()
        addPolygonsToDoc(FIELDNAME, document, polygon)
        writer.addDocument(document)

        val r: IndexReader = writer.reader
        writer.close()
        val s = newSearcher(r)

        val circle = Circle(78.01086555431775, 0.9513280497489234, 1097753.4254892308)
        val q = LatLonShape.newDistanceQuery(FIELDNAME, QueryRelation.CONTAINS, circle)
        assertEquals(0, s.count(q))

        IOUtils.close(r, dir)
    }

    @Test
    fun testContainsWrappingBooleanQuery() {
        val lats = doubleArrayOf(-30.0, -30.0, 30.0, 30.0, -30.0)
        val lons = doubleArrayOf(-30.0, 30.0, 30.0, -30.0, -30.0)
        val polygon = Polygon(lats, lons)

        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        val document = Document()
        addPolygonsToDoc(FIELDNAME, document, polygon)
        writer.addDocument(document)

        val r: IndexReader = writer.reader
        writer.close()
        val s = newSearcher(r)

        val geometries = arrayOf<org.gnit.lucenekmp.geo.LatLonGeometry>(Rectangle(0.0, 1.0, 0.0, 1.0), Point(4.0, 4.0))
        val q = LatLonShape.newGeometryQuery(FIELDNAME, QueryRelation.CONTAINS, *geometries)
        val topDocs: TopDocs = s.search(q, 1)
        assertEquals(1, topDocs.scoreDocs.size)
        assertEquals(1.0f, topDocs.scoreDocs[0].score, 0.0f)
        IOUtils.close(r, dir)
    }

    @Test
    fun testContainsGeometryCollectionIntersectsPoint() {
        val polygon = Polygon(doubleArrayOf(-64.0, -64.0, 64.0, 64.0, -64.0), doubleArrayOf(-132.0, 132.0, 132.0, -132.0, -132.0))
        doTestContainsGeometryCollectionIntersects(
            LatLonShape.createIndexableFields(FIELDNAME, polygon),
            LatLonShape.createIndexableFields(FIELDNAME, 5.0, 5.0)
        )
    }

    @Test
    fun testContainsGeometryCollectionIntersectsLine() {
        val polygon = Polygon(doubleArrayOf(-64.0, -64.0, 64.0, 64.0, -64.0), doubleArrayOf(-132.0, 132.0, 132.0, -132.0, -132.0))
        val line = Line(doubleArrayOf(5.0, 5.1), doubleArrayOf(5.0, 5.1))
        doTestContainsGeometryCollectionIntersects(
            LatLonShape.createIndexableFields(FIELDNAME, polygon),
            LatLonShape.createIndexableFields(FIELDNAME, line)
        )
    }

    @Test
    fun testContainsGeometryCollectionIntersectsPolygon() {
        val polygon = Polygon(doubleArrayOf(-64.0, -64.0, 64.0, 64.0, -64.0), doubleArrayOf(-132.0, 132.0, 132.0, -132.0, -132.0))
        val polygonInside = Polygon(doubleArrayOf(5.0, 5.0, 5.1, 5.1, 5.0), doubleArrayOf(5.0, 5.1, 5.1, 5.0, 5.0))
        doTestContainsGeometryCollectionIntersects(
            LatLonShape.createIndexableFields(FIELDNAME, polygon),
            LatLonShape.createIndexableFields(FIELDNAME, polygonInside)
        )
    }

    @Test
    fun testFlatPolygonDoesNotContainIntersectingLine() {
        val lons = doubleArrayOf(-0.001, -0.001, 0.001, 0.001, -0.001)
        val lats = doubleArrayOf(1e-10, 0.0, -1e-10, 0.0, 1e-10)
        val polygon = Polygon(lats, lons)
        val line = Line(doubleArrayOf(0.0, 0.001), doubleArrayOf(0.0, 0.0))

        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        val document = Document()
        addPolygonsToDoc(FIELDNAME, document, polygon)
        writer.addDocument(document)

        val r: IndexReader = writer.reader
        writer.close()
        val s = newSearcher(r)

        val q = LatLonShape.newGeometryQuery(FIELDNAME, QueryRelation.CONTAINS, line)
        val topDocs: TopDocs = s.search(q, 1)
        assertEquals(0, topDocs.scoreDocs.size, "Polygon should not contain the line,")

        IOUtils.close(r, dir)
    }

    private fun doTestContainsGeometryCollectionIntersects(containsFields: Array<Field>, intersectsField: Array<Field>) {
        val dir: Directory = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        val numDocs = random().nextInt(1000)
        for (i in 0..<numDocs) {
            val doc = Document()
            for (f in containsFields) {
                doc.add(f)
            }
            for (j in 0..<10) {
                for (f in intersectsField) {
                    doc.add(f)
                }
            }
            w.addDocument(doc)
        }
        w.forceMerge(1)

        val reader: IndexReader = w.reader
        w.close()
        val searcher = newSearcher(reader)

        val polygonQuery = Polygon(doubleArrayOf(4.0, 4.0, 6.0, 6.0, 4.0), doubleArrayOf(4.0, 6.0, 6.0, 4.0, 4.0))
        var query: Query = LatLonShape.newGeometryQuery(FIELDNAME, QueryRelation.CONTAINS, polygonQuery)
        assertEquals(0, searcher.count(query))

        val rectangle = Rectangle(4.0, 6.0, 4.0, 6.0)
        query = LatLonShape.newGeometryQuery(FIELDNAME, QueryRelation.CONTAINS, rectangle)
        assertEquals(0, searcher.count(query))

        val circle = Circle(5.0, 5.0, 10000.0)
        query = LatLonShape.newGeometryQuery(FIELDNAME, QueryRelation.CONTAINS, circle)
        assertEquals(0, searcher.count(query))

        IOUtils.close(w, reader, dir)
    }
}
