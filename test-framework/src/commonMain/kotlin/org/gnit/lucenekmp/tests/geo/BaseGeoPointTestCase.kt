package org.gnit.lucenekmp.tests.geo

import okio.IOException
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.codecs.FilterCodec
import org.gnit.lucenekmp.codecs.PointsFormat
import org.gnit.lucenekmp.codecs.PointsReader
import org.gnit.lucenekmp.codecs.PointsWriter
import org.gnit.lucenekmp.codecs.lucene90.Lucene90PointsReader
import org.gnit.lucenekmp.codecs.lucene90.Lucene90PointsWriter
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.StoredField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.geo.Circle
import org.gnit.lucenekmp.geo.Component2D
import org.gnit.lucenekmp.geo.GeoEncodingUtils
import org.gnit.lucenekmp.geo.GeoUtils
import org.gnit.lucenekmp.geo.LatLonGeometry
import org.gnit.lucenekmp.geo.Polygon
import org.gnit.lucenekmp.geo.Rectangle
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.MultiBits
import org.gnit.lucenekmp.index.MultiDocValues
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.SegmentWriteState
import org.gnit.lucenekmp.index.SerialMergeScheduler
import org.gnit.lucenekmp.index.StoredFields
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.BitSet
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.isNaN
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.MatchNoDocsQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.Sort
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.FixedBitSetCollector
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.SloppyMath
import org.gnit.lucenekmp.util.bkd.BKDWriter
import kotlin.math.nextDown
import kotlin.math.nextUp
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Abstract class to do basic tests for a geospatial impl (high level fields and queries) NOTE: This
 * test focuses on geospatial (distance queries, polygon queries, etc) indexing and search, not any
 * underlying storage format or encoding: it merely supplies two hooks for the encoding so that
 * tests can be exact. The [stretch] goal is for this test to be so thorough in testing a new geo
 * impl that if this test passes, then all Lucene tests should also pass. Ie, if there is some bug
 * in a given geo impl that this test fails to catch then this test needs to be improved!
 */
abstract class BaseGeoPointTestCase : LuceneTestCase() {
    // TODO: remove these hooks once all subclasses can pass with new random!
    protected fun nextLongitude(): Double {
        return GeoTestUtil.nextLongitude()
    }

    protected fun nextLatitude(): Double {
        return GeoTestUtil.nextLatitude()
    }

    protected fun nextBox(): Rectangle {
        return GeoTestUtil.nextBox()
    }

    protected fun nextCircle(): Circle {
        return GeoTestUtil.nextCircle()
    }

    protected fun nextPolygon(): Polygon {
        return GeoTestUtil.nextPolygon()
    }

    protected fun nextGeometry(): Array<LatLonGeometry> {
        val length: Int = random().nextInt(4) + 1
        val geometries: Array<LatLonGeometry> = Array(length)
        /*for (i in 0..<length) */{
            val geometry: LatLonGeometry
            when (random().nextInt(3)) {
                0 -> geometry = nextBox()
                1 -> geometry = nextCircle()
                else -> geometry = nextPolygon()
            }
            /*geometries[i] =*/ geometry
        }
        return geometries
    }

    /** Valid values that should not cause exception  */
    open fun testIndexExtremeValues() {
        val document = Document()
        addPointToDoc("foo", document, 90.0, 180.0)
        addPointToDoc("foo", document, 90.0, -180.0)
        addPointToDoc("foo", document, -90.0, 180.0)
        addPointToDoc("foo", document, -90.0, -180.0)
    }

    /** Invalid values  */
    open fun testIndexOutOfRangeValues() {
        val document = Document()
        var expected: IllegalArgumentException

        expected = expectThrows(IllegalArgumentException::class) {
                addPointToDoc("foo", document, 90.0.nextUp(), 50.0)
            }
        assertTrue(expected.message!!.contains("invalid latitude"))

        expected = expectThrows(IllegalArgumentException::class) {
                addPointToDoc("foo", document, (-90.0).nextDown(), 50.0)
            }
        assertTrue(expected.message!!.contains("invalid latitude"))

        expected = expectThrows(IllegalArgumentException::class) {
                addPointToDoc("foo", document, 90.0, 180.0.nextUp())
            }
        assertTrue(expected.message!!.contains("invalid longitude"))

        expected = expectThrows(IllegalArgumentException::class) {
                addPointToDoc("foo", document, 90.0, (-180.0).nextDown())
            }
        assertTrue(expected.message!!.contains("invalid longitude"))
    }

    /** NaN: illegal  */
    open fun testIndexNaNValues() {
        val document = Document()
        var expected: IllegalArgumentException

        expected = expectThrows(IllegalArgumentException::class) {
                addPointToDoc("foo", document, Double.NaN, 50.0)
            }
        assertTrue(expected.message!!.contains("invalid latitude"))

        expected = expectThrows(IllegalArgumentException::class) {
                addPointToDoc("foo", document, 50.0, Double.NaN)
            }
        assertTrue(expected.message!!.contains("invalid longitude"))
    }

    /** Inf: illegal  */
    open fun testIndexInfValues() {
        val document = Document()
        var expected: IllegalArgumentException

        expected = expectThrows(IllegalArgumentException::class) {
                addPointToDoc("foo", document, Double.POSITIVE_INFINITY, 50.0)
            }
        assertTrue(expected.message!!.contains("invalid latitude"))

        expected = expectThrows(IllegalArgumentException::class) {
                addPointToDoc("foo", document, Double.NEGATIVE_INFINITY, 50.0)
            }
        assertTrue(expected.message!!.contains("invalid latitude"))

        expected = expectThrows(IllegalArgumentException::class) {
                addPointToDoc("foo", document, 50.0, Double.POSITIVE_INFINITY)
            }
        assertTrue(expected.message!!.contains("invalid longitude"))

        expected = expectThrows(IllegalArgumentException::class) {
                addPointToDoc("foo", document, 50.0, Double.NEGATIVE_INFINITY)
            }
        assertTrue(expected.message!!.contains("invalid longitude"))
    }

    /** Add a single point and search for it in a box  */ // NOTE: we don't currently supply an exact search, only ranges, because of the lossiness...
    @Throws(Exception::class)
    open fun testBoxBasics() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)

        // add a doc with a point
        val document = Document()
        addPointToDoc("field", document, 18.313694, -65.227444)
        writer.addDocument(document)

        // search and verify we found our doc
        val reader = writer.reader
        val searcher: IndexSearcher = newSearcher(reader)
        assertEquals(1, searcher.count(newRectQuery("field", 18.0, 19.0, -66.0, -65.0)).toLong())

        reader.close()
        writer.close()
        dir.close()
    }

    /** null field name not allowed  */
    open fun testBoxNull() {
        val expected: IllegalArgumentException = expectThrows(IllegalArgumentException::class) {
                newRectQuery(null, 18.0, 19.0, -66.0, -65.0)
            }
        assertTrue(expected.message!!.contains("field must not be null"))
    }

    // box should not accept invalid lat/lon
    @Throws(Exception::class)
    open fun testBoxInvalidCoordinates() {
        expectThrows(Exception::class) {
            newRectQuery("field", -92.0, -91.0, 179.0, 181.0)
        }
    }

    /** test we can search for a point  */
    @Throws(Exception::class)
    open fun testDistanceBasics() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)

        // add a doc with a location
        val document = Document()
        addPointToDoc("field", document, 18.313694, -65.227444)
        writer.addDocument(document)

        // search within 50km and verify we found our doc
        val reader = writer.reader
        val searcher: IndexSearcher = newSearcher(reader)
        assertEquals(1, searcher.count(newDistanceQuery("field", 18.0, -65.0, 50000.0)).toLong())

        reader.close()
        writer.close()
        dir.close()
    }

    /** null field name not allowed  */
    open fun testDistanceNull() {
        val expected: IllegalArgumentException = expectThrows(IllegalArgumentException::class) {
                newDistanceQuery(null, 18.0, -65.0, 50000.0)
            }
        assertTrue(expected.message!!.contains("field must not be null"))
    }

    /** distance query should not accept invalid lat/lon as origin  */
    @Throws(Exception::class)
    open fun testDistanceIllegal() {
        expectThrows(Exception::class) {
            newDistanceQuery("field", 92.0, 181.0, 120000.0)
        }
    }

    /** negative distance queries are not allowed  */
    open fun testDistanceNegative() {
        val expected: IllegalArgumentException = expectThrows(IllegalArgumentException::class) {
                newDistanceQuery("field", 18.0, 19.0, -1.0)
            }
        assertTrue(expected.message!!.contains("radiusMeters"))
        assertTrue(expected.message!!.contains("invalid"))
    }

    /** NaN distance queries are not allowed  */
    open fun testDistanceNaN() {
        val expected: IllegalArgumentException = expectThrows(IllegalArgumentException::class) {
                newDistanceQuery("field", 18.0, 19.0, Double.NaN)
            }
        assertTrue(expected.message!!.contains("radiusMeters"))
        assertTrue(expected.message!!.contains("invalid"))
    }

    /** Inf distance queries are not allowed  */
    open fun testDistanceInf() {
        var expected: IllegalArgumentException

        expected = expectThrows(IllegalArgumentException::class) {
                newDistanceQuery("field", 18.0, 19.0, Double.POSITIVE_INFINITY)
            }
        assertTrue(expected.message!!.contains("radiusMeters"))
        assertTrue(expected.message!!.contains("invalid"))

        expected = expectThrows(IllegalArgumentException::class) {
                newDistanceQuery("field", 18.0, 19.0, Double.NEGATIVE_INFINITY)
            }
        assertTrue(expected.message!!.contains("radiusMeters"), expected.message)
        assertTrue(expected.message!!.contains("invalid"))
    }

    /** test we can search for a polygon  */
    @Throws(Exception::class)
    open fun testPolygonBasics() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)

        // add a doc with a point
        val document = Document()
        addPointToDoc("field", document, 18.313694, -65.227444)
        writer.addDocument(document)

        // search and verify we found our doc
        val reader = writer.reader
        val searcher: IndexSearcher = newSearcher(reader)
        assertEquals(
            1,
            searcher.count(
                newPolygonQuery(
                    "field",
                    Polygon(
                        doubleArrayOf(18.0, 18.0, 19.0, 19.0, 18.0), doubleArrayOf(-66.0, -65.0, -65.0, -66.0, -66.0)
                    )
                )
            ).toLong()
        )

        reader.close()
        writer.close()
        dir.close()
    }

    /** test we can search for a polygon with a hole (but still includes the doc)  */
    @Throws(Exception::class)
    open fun testPolygonHole() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)

        // add a doc with a point
        val document = Document()
        addPointToDoc("field", document, 18.313694, -65.227444)
        writer.addDocument(document)

        // search and verify we found our doc
        val reader = writer.reader
        val searcher: IndexSearcher = newSearcher(reader)
        val inner =
            Polygon(
                doubleArrayOf(18.5, 18.5, 18.7, 18.7, 18.5),
                doubleArrayOf(-65.7, -65.4, -65.4, -65.7, -65.7)
            )
        val outer =
            Polygon(
                doubleArrayOf(18.0, 18.0, 19.0, 19.0, 18.0), doubleArrayOf(-66.0, -65.0, -65.0, -66.0, -66.0), inner
            )
        assertEquals(1, searcher.count(newPolygonQuery("field", outer)).toLong())

        reader.close()
        writer.close()
        dir.close()
    }

    /** test we can search for a polygon with a hole (that excludes the doc)  */
    @Throws(Exception::class)
    open fun testPolygonHoleExcludes() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)

        // add a doc with a point
        val document = Document()
        addPointToDoc("field", document, 18.313694, -65.227444)
        writer.addDocument(document)

        // search and verify we found our doc
        val reader = writer.reader
        val searcher: IndexSearcher = newSearcher(reader)
        val inner =
            Polygon(
                doubleArrayOf(18.2, 18.2, 18.4, 18.4, 18.2),
                doubleArrayOf(-65.3, -65.2, -65.2, -65.3, -65.3)
            )
        val outer =
            Polygon(
                doubleArrayOf(18.0, 18.0, 19.0, 19.0, 18.0), doubleArrayOf(-66.0, -65.0, -65.0, -66.0, -66.0), inner
            )
        assertEquals(0, searcher.count(newPolygonQuery("field", outer)).toLong())

        reader.close()
        writer.close()
        dir.close()
    }

    /** test we can search for a multi-polygon  */
    @Throws(Exception::class)
    open fun testMultiPolygonBasics() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)

        // add a doc with a point
        val document = Document()
        addPointToDoc("field", document, 18.313694, -65.227444)
        writer.addDocument(document)

        // search and verify we found our doc
        val reader = writer.reader
        val searcher: IndexSearcher = newSearcher(reader)
        val a =
            Polygon(doubleArrayOf(28.0, 28.0, 29.0, 29.0, 28.0), doubleArrayOf(-56.0, -55.0, -55.0, -56.0, -56.0))
        val b =
            Polygon(doubleArrayOf(18.0, 18.0, 19.0, 19.0, 18.0), doubleArrayOf(-66.0, -65.0, -65.0, -66.0, -66.0))
        assertEquals(1, searcher.count(newPolygonQuery("field", a, b)).toLong())

        reader.close()
        writer.close()
        dir.close()
    }

    /** null field name not allowed  */
    open fun testPolygonNullField() {
        val expected: IllegalArgumentException =
            expectThrows(
                IllegalArgumentException::class,
                {
                    newPolygonQuery(
                        null,
                        Polygon(
                            doubleArrayOf(18.0, 18.0, 19.0, 19.0, 18.0), doubleArrayOf(-66.0, -65.0, -65.0, -66.0, -66.0)
                        )
                    )
                })
        assertTrue(expected.message!!.contains("field must not be null"))
    }

    // A particularly tricky adversary for BKD tree:
    @Throws(Exception::class)
    open fun testSamePointManyTimes() {
        val numPoints: Int = atLeast(10) // TODO reduced from 1000 to 10 for dev speed

        // Every doc has 2 points:
        val theLat = nextLatitude()
        val theLon = nextLongitude()

        val lats = DoubleArray(numPoints)
        Arrays.fill(lats, theLat)

        val lons = DoubleArray(numPoints)
        Arrays.fill(lons, theLon)

        verify(lats, lons)
    }

    // A particularly tricky adversary for BKD tree:
    @Throws(Exception::class)
    open fun testLowCardinality() {
        val numPoints: Int = atLeast(10) // TODO reduced from 1000 to 10 for dev speed
        val cardinality: Int = TestUtil.nextInt(random(), 2, 20)

        val diffLons = DoubleArray(cardinality)
        val diffLats = DoubleArray(cardinality)
        for (i in 0..<cardinality) {
            diffLats[i] = nextLatitude()
            diffLons[i] = nextLongitude()
        }

        val lats = DoubleArray(numPoints)
        val lons = DoubleArray(numPoints)
        for (i in 0..<numPoints) {
            val index: Int = random().nextInt(cardinality)
            lats[i] = diffLats[index]
            lons[i] = diffLons[index]
        }

        verify(lats, lons)
    }

    @Throws(Exception::class)
    open fun testAllLatEqual() {
        val numPoints: Int = atLeast(10) // TODO reduced from 1000 to 10 for dev speed
        val lat = nextLatitude()
        val lats = DoubleArray(numPoints)
        val lons = DoubleArray(numPoints)

        var haveRealDoc = false

        for (docID in 0..<numPoints) {
            val x: Int = random().nextInt(20)
            if (x == 17) {
                // Some docs don't have a point:
                lats[docID] = Double.NaN
                if (VERBOSE) {
                    println("  doc=$docID is missing")
                }
                continue
            }

            if (docID > 0 && x == 14 && haveRealDoc) {
                var oldDocID: Int
                while (true) {
                    oldDocID = random().nextInt(docID)
                    if (Double.isNaN(lats[oldDocID]) == false) {
                        break
                    }
                }

                // Fully identical point:
                lons[docID] = lons[oldDocID]
                if (VERBOSE) {
                    println("  doc=" + docID + " lat=" + lat + " lon=" + lons[docID] + " (same lat/lon as doc=" + oldDocID + ")")
                }
            } else {
                lons[docID] = nextLongitude()
                haveRealDoc = true
                if (VERBOSE) {
                    println("  doc=" + docID + " lat=" + lat + " lon=" + lons[docID])
                }
            }
            lats[docID] = lat
        }

        verify(lats, lons)
    }

    @Throws(Exception::class)
    open fun testAllLonEqual() {
        val numPoints: Int = atLeast(10) // TODO reduced from 1000 to 10 for dev speed
        val theLon = nextLongitude()
        val lats = DoubleArray(numPoints)
        val lons = DoubleArray(numPoints)

        var haveRealDoc = false

        // System.out.println("theLon=" + theLon);
        for (docID in 0..<numPoints) {
            val x: Int = random().nextInt(20)
            if (x == 17) {
                // Some docs don't have a point:
                lats[docID] = Double.NaN
                if (VERBOSE) {
                    println("  doc=$docID is missing")
                }
                continue
            }

            if (docID > 0 && x == 14 && haveRealDoc) {
                var oldDocID: Int
                while (true) {
                    oldDocID = random().nextInt(docID)
                    if (Double.isNaN(lats[oldDocID]) == false) {
                        break
                    }
                }

                // Fully identical point:
                lats[docID] = lats[oldDocID]
                if (VERBOSE) {
                    println("  doc=" + docID + " lat=" + lats[docID] + " lon=" + theLon + " (same lat/lon as doc=" + oldDocID + ")")
                }
            } else {
                lats[docID] = nextLatitude()
                haveRealDoc = true
                if (VERBOSE) {
                    println("  doc=" + docID + " lat=" + lats[docID] + " lon=" + theLon)
                }
            }
            lons[docID] = theLon
        }

        verify(lats, lons)
    }

    @Throws(Exception::class)
    open fun testMultiValued() {
        val numPoints: Int = atLeast(10) // TODO reduced from 1000 to 10 for dev speed
        // Every doc has 2 points:
        val lats = DoubleArray(2 * numPoints)
        val lons = DoubleArray(2 * numPoints)
        val dir: Directory = newDirectory()
        val iwc: IndexWriterConfig = newIndexWriterConfig()

        // We rely on docID order:
        iwc.setMergePolicy(newLogMergePolicy())
        // and on seeds being able to reproduce:
        iwc.setMergeScheduler(SerialMergeScheduler())
        val w = RandomIndexWriter(random(), dir, iwc)

        for (id in 0..<numPoints) {
            val doc = Document()
            lats[2 * id] = quantizeLat(nextLatitude())
            lons[2 * id] = quantizeLon(nextLongitude())
            doc.add(StringField("id", "" + id, Field.Store.YES))
            addPointToDoc(FIELD_NAME, doc, lats[2 * id], lons[2 * id])
            lats[2 * id + 1] = quantizeLat(nextLatitude())
            lons[2 * id + 1] = quantizeLon(nextLongitude())
            addPointToDoc(FIELD_NAME, doc, lats[2 * id + 1], lons[2 * id + 1])

            if (VERBOSE) {
                println("id=$id")
                println("  lat=" + lats[2 * id] + " lon=" + lons[2 * id])
                println("  lat=" + lats[2 * id + 1] + " lon=" + lons[2 * id + 1])
            }
            w.addDocument(doc)
        }

        // TODO: share w/ verify; just need parallel array of the expected ids
        if (random().nextBoolean()) {
            w.forceMerge(1)
        }
        val r = w.reader
        w.close()

        val s: IndexSearcher = newSearcher(r)

        val iters: Int = atLeast(5) // TODO reduced from 25 to 5 for dev speed
        for (iter in 0..<iters) {
            val rect: Rectangle = nextBox()

            if (VERBOSE) {
                println("\nTEST: iter=$iter rect=$rect")
            }

            val query: Query = newRectQuery(FIELD_NAME, rect.minLat, rect.maxLat, rect.minLon, rect.maxLon)

            val hits: FixedBitSet = searchIndex(s, query, r.maxDoc())

            var fail = false

            val storedFields: StoredFields = s.storedFields()
            for (docID in 0..<lats.size / 2) {
                val latDoc1 = lats[2 * docID]
                val lonDoc1 = lons[2 * docID]
                val latDoc2 = lats[2 * docID + 1]
                val lonDoc2 = lons[2 * docID + 1]

                val result1 = rectContainsPoint(rect, latDoc1, lonDoc1)
                val result2 = rectContainsPoint(rect, latDoc2, lonDoc2)

                val expected = result1 || result2

                if (hits.get(docID) != expected) {
                    val id: String? = storedFields.document(docID).get("id")
                    if (expected) {
                        println("TEST: id=$id docID=$docID should match but did not")
                    } else {
                        println("TEST: id=$id docID=$docID should not match but did")
                    }
                    println("  rect=$rect")
                    println("  lat=$latDoc1 lon=$lonDoc1\n  lat=$latDoc2 lon=$lonDoc2")
                    println("  result1=$result1 result2=$result2")
                    fail = true
                }
            }

            if (fail) {
                fail("some hits were wrong")
            }
        }
        r.close()
        dir.close()
    }

    @Throws(Exception::class)
    open fun testRandomTiny() {
        // Make sure single-leaf-node case is OK:
        doTestRandom(1) // TODO reduced from 10 to 1 for dev speed
    }

    @Throws(Exception::class)
    open fun testRandomMedium() {
        doTestRandom(3) // TODO reduced from 1000 to 3 for dev speed
    }

    /*@org.apache.lucene.tests.util.LuceneTestCase.Nightly*/
    @Throws(Exception::class)
    open fun testRandomBig() {
        // TODO no-op
        /*assumeFalse(
            "Direct codec can OOME on this test",
            TestUtil.getDocValuesFormat(FIELD_NAME) == "Direct"
        )
        doTestRandom(200000)*/
    }

    @Throws(Exception::class)
    private fun doTestRandom(count: Int) {
        val numPoints: Int = atLeast(count)

        if (VERBOSE) {
            println("TEST: numPoints=$numPoints")
        }

        val lats = DoubleArray(numPoints)
        val lons = DoubleArray(numPoints)

        var haveRealDoc = false

        for (id in 0..<numPoints) {
            val x: Int = random().nextInt(20)
            if (x == 17) {
                // Some docs don't have a point:
                lats[id] = Double.NaN
                if (VERBOSE) {
                    println("  id=$id is missing")
                }
                continue
            }

            if (id > 0 && x < 3 && haveRealDoc) {
                var oldID: Int
                while (true) {
                    oldID = random().nextInt(id)
                    if (Double.isNaN(lats[oldID]) == false) {
                        break
                    }
                }

                if (x == 0) {
                    // Identical lat to old point
                    lats[id] = lats[oldID]
                    lons[id] = nextLongitude()
                    if (VERBOSE) {
                        println("  id=" + id + " lat=" + lats[id] + " lon=" + lons[id] + " (same lat as doc=" + oldID + ")")
                    }
                } else if (x == 1) {
                    // Identical lon to old point
                    lats[id] = nextLatitude()
                    lons[id] = lons[oldID]
                    if (VERBOSE) {
                        println("  id=" + id + " lat=" + lats[id]+ " lon=" + lons[id] + " (same lon as doc=" + oldID + ")")
                    }
                } else {
                    assert(x == 2)
                    // Fully identical point:
                    lats[id] = lats[oldID]
                    lons[id] = lons[oldID]
                    if (VERBOSE) {
                        println("  id=" + id+ " lat=" + lats[id] + " lon=" + lons[id] + " (same lat/lon as doc=" + oldID+ ")")
                    }
                }
            } else {
                lats[id] = nextLatitude()
                lons[id] = nextLongitude()
                haveRealDoc = true
                if (VERBOSE) {
                    println("  id=" + id + " lat=" + lats[id] + " lon=" + lons[id])
                }
            }
        }

        verify(lats, lons)
    }

    /**
     * Override this to quantize randomly generated lat, so the test won't fail due to quantization
     * errors, which are 1) annoying to debug, and 2) should never affect "real" usage terribly.
     */
    protected open fun quantizeLat(lat: Double): Double {
        return lat
    }

    /**
     * Override this to quantize randomly generated lon, so the test won't fail due to quantization
     * errors, which are 1) annoying to debug, and 2) should never affect "real" usage terribly.
     */
    protected open fun quantizeLon(lon: Double): Double {
        return lon
    }

    protected abstract fun addPointToDoc(field: String, doc: Document, lat: Double, lon: Double)

    protected abstract fun newRectQuery(
        field: String?, minLat: Double, maxLat: Double, minLon: Double, maxLon: Double
    ): Query

    protected abstract fun newDistanceQuery(
        field: String?, centerLat: Double, centerLon: Double, radiusMeters: Double
    ): Query

    protected abstract fun newPolygonQuery(field: String?, vararg polygon: Polygon): Query

    protected abstract fun newGeometryQuery(field: String, vararg geometry: LatLonGeometry): Query

    @Throws(Exception::class)
    private fun verify(lats: DoubleArray, lons: DoubleArray) {
        // quantize each value the same way the index does
        // NaN means missing for the doc!!!!!
        for (i in lats.indices) {
            if (!Double.isNaN(lats[i])) {
                lats[i] = quantizeLat(lats[i])
            }
        }
        for (i in lons.indices) {
            if (!Double.isNaN(lons[i])) {
                lons[i] = quantizeLon(lons[i])
            }
        }
        verifyRandomRectangles(lats, lons)
        verifyRandomDistances(lats, lons)
        verifyRandomPolygons(lats, lons)
        verifyRandomGeometries(lats, lons)
    }

    @Throws(Exception::class)
    protected fun verifyRandomRectangles(lats: DoubleArray, lons: DoubleArray) {
        val iwc: IndexWriterConfig = newIndexWriterConfig()
        // Else seeds may not reproduce:
        iwc.setMergeScheduler(SerialMergeScheduler())
        // Else we can get O(N^2) merging:
        val mbd: Int = iwc.maxBufferedDocs
        if (mbd != -1 && mbd < lats.size / 100) {
            iwc.setMaxBufferedDocs(lats.size / 100)
        }
        val dir: Directory
        if (lats.size > 100000) {
            // Avoid slow codecs like SimpleText
            iwc.setCodec(TestUtil.getDefaultCodec())
            dir = newFSDirectory(createTempDir(this::class.simpleName!!))
        } else {
            dir = newDirectory()
        }

        val deleted: MutableSet<Int> = mutableSetOf()
        // RandomIndexWriter is too slow here:
        val w = IndexWriter(dir, iwc)
        indexPoints(lats, lons, deleted, w)

        val r = DirectoryReader.open(w)
        w.close()

        val s: IndexSearcher = newSearcher(r)

        val iters: Int = atLeast(5) // TODO reduced from 25 to 5 for dev speed

        val liveDocs: Bits? = MultiBits.getLiveDocs(s.indexReader)
        val maxDoc: Int = s.indexReader.maxDoc()

        for (iter in 0..<iters) {
            if (VERBOSE) {
                println("\nTEST: iter=$iter s=$s")
            }

            val rect: Rectangle = nextBox()

            val query: Query = newRectQuery(FIELD_NAME, rect.minLat, rect.maxLat, rect.minLon, rect.maxLon)

            if (VERBOSE) {
                println("  query=$query")
            }

            val hits: FixedBitSet = searchIndex(s, query, maxDoc)

            var fail = false
            val docIDToID: NumericDocValues = MultiDocValues.getNumericValues(r, "id")!!
            for (docID in 0..<maxDoc) {
                assertEquals(docID.toLong(), docIDToID.nextDoc().toLong())
                val id = docIDToID.longValue().toInt()
                val expected: Boolean
                if (liveDocs != null && liveDocs.get(docID) == false) {
                    // document is deleted
                    expected = false
                } else if (Double.isNaN(lats[id])) {
                    expected = false
                } else {
                    expected = rectContainsPoint(rect, lats[id], lons[id])
                }

                if (hits.get(docID) != expected) {
                    buildError(
                        docID,
                        expected,
                        id,
                        lats,
                        lons,
                        query,
                        liveDocs
                    ) { b: StringBuilder -> b.append("  rect=").append(rect) }
                    fail = true
                }
            }
            if (fail) {
                fail("some hits were wrong")
            }
        }

        IOUtils.close(r, dir)
    }

    @Throws(Exception::class)
    protected fun verifyRandomDistances(lats: DoubleArray, lons: DoubleArray) {
        val iwc: IndexWriterConfig = newIndexWriterConfig()
        // Else seeds may not reproduce:
        iwc.setMergeScheduler(SerialMergeScheduler())
        // Else we can get O(N^2) merging:
        val mbd: Int = iwc.maxBufferedDocs
        if (mbd != -1 && mbd < lats.size / 100) {
            iwc.setMaxBufferedDocs(lats.size / 100)
        }
        val dir: Directory
        if (lats.size > 100000) {
            // Avoid slow codecs like SimpleText
            iwc.setCodec(TestUtil.getDefaultCodec())
            dir = newFSDirectory(createTempDir(this::class.simpleName!!))
        } else {
            dir = newDirectory()
        }

        val deleted: MutableSet<Int> = mutableSetOf()
        // RandomIndexWriter is too slow here:
        val w = IndexWriter(dir, iwc)
        indexPoints(lats, lons, deleted, w)

        val r = DirectoryReader.open(w)
        w.close()

        val s: IndexSearcher = newSearcher(r)

        val iters: Int = atLeast(5) // TODO reduced from 25 to 5 for dev speed

        val liveDocs: Bits? = MultiBits.getLiveDocs(s.indexReader)
        val maxDoc: Int = s.indexReader.maxDoc()

        for (iter in 0..<iters) {
            if (VERBOSE) {
                println("\nTEST: iter=$iter s=$s")
            }

            // Distance
            val centerLat = nextLatitude()
            val centerLon = nextLongitude()

            // So the query can cover at most 50% of the earth's surface:
            val radiusMeters: Double =
                random().nextDouble() * GeoUtils.EARTH_MEAN_RADIUS_METERS * Math.PI / 2.0 + 1.0

            if (VERBOSE) {
                println("  radiusMeters = $radiusMeters")
            }

            val query: Query = newDistanceQuery(FIELD_NAME, centerLat, centerLon, radiusMeters)

            if (VERBOSE) {
                println("  query=$query")
            }

            val hits: FixedBitSet = searchIndex(s, query, maxDoc)

            var fail = false
            val docIDToID: NumericDocValues = MultiDocValues.getNumericValues(r, "id")!!
            for (docID in 0..<maxDoc) {
                assertEquals(docID.toLong(), docIDToID.nextDoc().toLong())
                val id = docIDToID.longValue().toInt()
                val expected: Boolean
                if (liveDocs != null && liveDocs.get(docID) == false) {
                    // document is deleted
                    expected = false
                } else if (Double.isNaN(lats[id])) {
                    expected = false
                } else {
                    expected =
                        SloppyMath.haversinMeters(centerLat, centerLon, lats[id], lons[id]) <= radiusMeters
                }

                if (hits.get(docID) != expected) {
                    val explain =
                        { b: StringBuilder ->
                            if (Double.isNaN(lats[id]) == false) {
                                val distanceMeters: Double =
                                    SloppyMath.haversinMeters(centerLat, centerLon, lats[id], lons[id])
                                b.append("  centerLat=")
                                    .append(centerLat)
                                    .append(" centerLon=")
                                    .append(centerLon)
                                    .append(" distanceMeters=")
                                    .append(distanceMeters)
                                    .append(" vs radiusMeters=")
                                    .append(radiusMeters)
                            }
                        }
                    buildError(docID, expected, id, lats, lons, query, liveDocs, explain)
                    fail = true
                }
            }
            if (fail) {
                fail("some hits were wrong")
            }
        }

        IOUtils.close(r, dir)
    }

    @Throws(Exception::class)
    protected fun verifyRandomPolygons(lats: DoubleArray, lons: DoubleArray) {
        val iwc: IndexWriterConfig = newIndexWriterConfig()
        // Else seeds may not reproduce:
        iwc.setMergeScheduler(SerialMergeScheduler())
        // Else we can get O(N^2) merging:
        val mbd: Int = iwc.maxBufferedDocs
        if (mbd != -1 && mbd < lats.size / 100) {
            iwc.setMaxBufferedDocs(lats.size / 100)
        }
        val dir: Directory
        if (lats.size > 100000) {
            // Avoid slow codecs like SimpleText
            iwc.setCodec(TestUtil.getDefaultCodec())
            dir = newFSDirectory(createTempDir(this::class.simpleName!!))
        } else {
            dir = newDirectory()
        }

        val deleted: MutableSet<Int> = mutableSetOf()
        // RandomIndexWriter is too slow here:
        val w = IndexWriter(dir, iwc)
        indexPoints(lats, lons, deleted, w)

        val r = DirectoryReader.open(w)
        w.close()

        // We can't wrap with "exotic" readers because points needs to work:
        val s: IndexSearcher = newSearcher(r)

        val iters: Int = atLeast(5) // TODO reduced from 75 to 5 for dev speed

        val liveDocs: Bits? = MultiBits.getLiveDocs(s.indexReader)
        val maxDoc: Int = s.indexReader.maxDoc()

        for (iter in 0..<iters) {
            if (VERBOSE) {
                println("\nTEST: iter=$iter s=$s")
            }

            // Polygon
            val polygon = nextPolygon()
            val query: Query = newPolygonQuery(FIELD_NAME, polygon)

            if (VERBOSE) {
                println("  query=$query")
            }

            val hits: FixedBitSet = searchIndex(s, query, maxDoc)

            var fail = false
            val docIDToID: NumericDocValues = MultiDocValues.getNumericValues(r, "id")!!
            for (docID in 0..<maxDoc) {
                assertEquals(docID.toLong(), docIDToID.nextDoc().toLong())
                val id = docIDToID.longValue().toInt()
                val expected: Boolean
                if (liveDocs != null && liveDocs.get(docID) == false) {
                    // document is deleted
                    expected = false
                } else if (Double.isNaN(lats[id])) {
                    expected = false
                } else {
                    expected = GeoTestUtil.containsSlowly(polygon, lats[id], lons[id])
                }

                if (hits.get(docID) != expected) {
                    buildError(
                        docID,
                        expected,
                        id,
                        lats,
                        lons,
                        query,
                        liveDocs
                    ) { b: StringBuilder -> b.append("  polygon=").append(polygon) }
                    fail = true
                }
            }
            if (fail) {
                fail("some hits were wrong")
            }
        }

        IOUtils.close(r, dir)
    }

    @Throws(Exception::class)
    protected fun verifyRandomGeometries(lats: DoubleArray, lons: DoubleArray) {
        val iwc: IndexWriterConfig = newIndexWriterConfig()
        // Else seeds may not reproduce:
        iwc.setMergeScheduler(SerialMergeScheduler())
        // Else we can get O(N^2) merging:
        val mbd: Int = iwc.maxBufferedDocs
        if (mbd != -1 && mbd < lats.size / 100) {
            iwc.setMaxBufferedDocs(lats.size / 100)
        }
        val dir: Directory
        if (lats.size > 100000) {
            // Avoid slow codecs like SimpleText
            iwc.setCodec(TestUtil.getDefaultCodec())
            dir = newFSDirectory(createTempDir(this::class.simpleName!!))
        } else {
            dir = newDirectory()
        }

        val deleted: MutableSet<Int> = mutableSetOf()

        // RandomIndexWriter is too slow here:
        val w = IndexWriter(dir, iwc)
        indexPoints(lats, lons, deleted, w)

        val r = DirectoryReader.open(w)
        w.close()

        // We can't wrap with "exotic" readers because points needs to work:
        val s: IndexSearcher = newSearcher(r)

        val iters: Int = atLeast(5) // TODO reduced from 75 to 5 for dev speed

        val liveDocs: Bits? = MultiBits.getLiveDocs(s.indexReader)
        val maxDoc: Int = s.indexReader.maxDoc()

        for (iter in 0..<iters) {
            if (VERBOSE) {
                println("\nTEST: iter=$iter s=$s")
            }

            // Polygon
            val geometries: Array<LatLonGeometry> = nextGeometry()
            val query: Query = newGeometryQuery(FIELD_NAME, *geometries)

            if (VERBOSE) {
                println("  query=$query")
            }

            val hits: FixedBitSet = searchIndex(s, query, maxDoc)

            val component2D: Component2D = LatLonGeometry.create(*geometries)

            var fail = false
            val docIDToID: NumericDocValues = MultiDocValues.getNumericValues(r, "id")!!
            for (docID in 0..<maxDoc) {
                assertEquals(docID.toLong(), docIDToID.nextDoc().toLong())
                val id = docIDToID.longValue().toInt()
                val expected: Boolean
                if (liveDocs != null && liveDocs.get(docID) == false) {
                    // document is deleted
                    expected = false
                } else if (Double.isNaN(lats[id])) {
                    expected = false
                } else {
                    expected = component2D.contains(quantizeLon(lons[id]), quantizeLat(lats[id]))
                }

                if (hits.get(docID) != expected) {
                    buildError(
                        docID,
                        expected,
                        id,
                        lats,
                        lons,
                        query,
                        liveDocs
                    ) { b: StringBuilder -> b.append("  geometry=").append(geometries.contentToString()) }
                    fail = true
                }
            }
            if (fail) {
                fail("some hits were wrong")
            }
        }

        IOUtils.close(r, dir)
    }

    @Throws(IOException::class)
    private fun indexPoints(lats: DoubleArray, lons: DoubleArray, deleted: MutableSet<Int>, w: IndexWriter) {
        for (id in lats.indices) {
            val doc = Document()
            doc.add(StringField("id", "" + id, Field.Store.NO))
            doc.add(NumericDocValuesField("id", id.toLong()))
            if (Double.isNaN(lats[id]) == false) {
                addPointToDoc(FIELD_NAME, doc, lats[id], lons[id])
            }
            w.addDocument(doc)
            if (id > 0 && random().nextInt(100) == 42) {
                val idToDelete: Int = random().nextInt(id)
                w.deleteDocuments(Term("id", "" + idToDelete))
                deleted.add(idToDelete)
                if (VERBOSE) {
                    println("  delete id=$idToDelete")
                }
            }
        }

        if (random().nextBoolean()) {
            w.forceMerge(1)
        }
    }

    @Throws(IOException::class)
    private fun searchIndex(s: IndexSearcher, query: Query, maxDoc: Int): FixedBitSet {
        return s.search(query, FixedBitSetCollector.createManager(maxDoc))
    }

    private fun buildError(
        docID: Int,
        expected: Boolean,
        id: Int,
        lats: DoubleArray,
        lons: DoubleArray,
        query: Query,
        liveDocs: Bits?,
        explain: (StringBuilder) -> Unit /*java.util.function.Consumer<StringBuilder>*/
    ) {
        val b = StringBuilder()
        if (expected) {
            b.append("FAIL: id=").append(id).append(" should match but did not\n")
        } else {
            b.append("FAIL: id=").append(id).append(" should not match but did\n")
        }
        b.append("  query=").append(query).append(" docID=").append(docID).append("\n")
        b.append("  lat=").append(lats[id]).append(" lon=").append(lons[id]).append("\n")
        b.append("  deleted=").append(liveDocs != null && liveDocs.get(docID) == false)
        explain(b)
        if (true) {
            fail("wrong hit (first of possibly more):\n\n$b")
        } else {
            println(b.toString())
        }
    }

    @Throws(Exception::class)
    open fun testRectBoundariesAreInclusive() {
        var rect: Rectangle
        // TODO: why this dateline leniency
        while (true) {
            rect = nextBox()
            if (rect.crossesDateline() == false) {
                break
            }
        }
        // this test works in quantized space: for testing inclusiveness of exact edges it must be aware
        // of index-time quantization!
        rect =
            Rectangle(
                quantizeLat(rect.minLat),
                quantizeLat(rect.maxLat),
                quantizeLon(rect.minLon),
                quantizeLon(rect.maxLon)
            )
        val dir: Directory = newDirectory()
        val iwc: IndexWriterConfig = newIndexWriterConfig()
        // Else seeds may not reproduce:
        iwc.setMergeScheduler(SerialMergeScheduler())
        val w = RandomIndexWriter(random(), dir, iwc)
        for (x in 0..2) {
            val lat: Double
            if (x == 0) {
                lat = rect.minLat
            } else if (x == 1) {
                lat = quantizeLat((rect.minLat + rect.maxLat) / 2.0)
            } else {
                lat = rect.maxLat
            }
            for (y in 0..2) {
                val lon: Double
                if (y == 0) {
                    lon = rect.minLon
                } else if (y == 1) {
                    if (x == 1) {
                        continue
                    }
                    lon = quantizeLon((rect.minLon + rect.maxLon) / 2.0)
                } else {
                    lon = rect.maxLon
                }

                val doc = Document()
                addPointToDoc(FIELD_NAME, doc, lat, lon)
                w.addDocument(doc)
            }
        }
        val r = w.reader
        val s: IndexSearcher = newSearcher(r, false)
        // exact edge cases
        assertEquals(
            8, s.count(newRectQuery(FIELD_NAME, rect.minLat, rect.maxLat, rect.minLon, rect.maxLon)).toLong()
        )

        // expand 1 ulp in each direction if possible and test a slightly larger box!
        if (rect.minLat != -90.0) {
            assertEquals(
                8,
                s.count(
                    newRectQuery(
                        FIELD_NAME, rect.minLat.nextDown(), rect.maxLat, rect.minLon, rect.maxLon
                    )
                ).toLong()
            )
        }
        if (rect.maxLat != 90.0) {
            assertEquals(
                8,
                s.count(
                    newRectQuery(
                        FIELD_NAME, rect.minLat, rect.maxLat.nextUp(), rect.minLon, rect.maxLon
                    )
                ).toLong()
            )
        }
        if (rect.minLon != -180.0) {
            assertEquals(
                8,
                s.count(
                    newRectQuery(
                        FIELD_NAME, rect.minLat, rect.maxLat, rect.minLon.nextDown(), rect.maxLon
                    )
                ).toLong()
            )
        }
        if (rect.maxLon != 180.0) {
            assertEquals(
                8,
                s.count(
                    newRectQuery(
                        FIELD_NAME, rect.minLat, rect.maxLat, rect.minLon, rect.maxLon.nextUp()
                    )
                ).toLong()
            )
        }

        // now shrink 1 ulp in each direction if possible: it should not include bogus stuff
        // we can't shrink if values are already at extremes, and
        // we can't do this if rectangle is actually a line or we will create a cross-dateline query
        if (rect.minLat != 90.0 && rect.maxLat != -90.0 && rect.minLon != 80.0 && rect.maxLon != -180.0 && rect.minLon != rect.maxLon) {
            // note we put points on "sides" not just "corners" so we just shrink all 4 at once for now:
            // it should exclude all points!
            assertEquals(
                0,
                s.count(
                    newRectQuery(
                        FIELD_NAME,
                        rect.minLat.nextUp(),
                        rect.maxLat.nextDown(),
                        rect.minLon.nextUp(),
                        rect.maxLon.nextDown()
                    )
                ).toLong()
            )
        }

        r.close()
        w.close()
        dir.close()
    }

    /** Run a few iterations with just 10 docs, hopefully easy to debug  */
    @Throws(Exception::class)
    open fun testRandomDistance() {
        val numIters: Int = atLeast(1)
        for (iters in 0..<numIters) {
            doRandomDistanceTest(3, 3) // TODO reduced from 10, 100 to 3, 3
        }
    }

    /** Runs with thousands of docs  */
    /*@org.apache.lucene.tests.util.LuceneTestCase.Nightly*/
    @Throws(Exception::class)
    open fun testRandomDistanceHuge() {
        for (iters in 0..9) {
            doRandomDistanceTest(3, 5) // TODO reduced from 2000, 100 to 3, 5
        }
    }

    @Throws(IOException::class)
    private fun doRandomDistanceTest(numDocs: Int, numQueries: Int) {
        val dir: Directory = newDirectory()
        val iwc: IndexWriterConfig = newIndexWriterConfig()
        // Else seeds may not reproduce:
        iwc.setMergeScheduler(SerialMergeScheduler())
        val pointsInLeaf: Int = 2 + random().nextInt(4)
        val `in`: Codec = TestUtil.getDefaultCodec()
        iwc.setCodec(
            object : FilterCodec(`in`.name, `in`) {
                override fun pointsFormat(): PointsFormat {
                    return object : PointsFormat() {
                        @Throws(IOException::class)
                        override fun fieldsWriter(writeState: SegmentWriteState): PointsWriter {
                            return Lucene90PointsWriter(
                                writeState, pointsInLeaf, BKDWriter.DEFAULT_MAX_MB_SORT_IN_HEAP.toDouble()
                            )
                        }

                        @Throws(IOException::class)
                        override fun fieldsReader(readState: SegmentReadState): PointsReader {
                            return Lucene90PointsReader(readState)
                        }
                    }
                }
            })
        val writer = RandomIndexWriter(random(), dir, iwc)

        for (i in 0..<numDocs) {
            val latRaw = nextLatitude()
            val lonRaw = nextLongitude()
            // pre-normalize up front, so we can just use quantized value for testing and do simple exact
            // comparisons
            val lat = quantizeLat(latRaw)
            val lon = quantizeLon(lonRaw)
            val doc = Document()
            addPointToDoc("field", doc, lat, lon)
            doc.add(StoredField("lat", lat))
            doc.add(StoredField("lon", lon))
            writer.addDocument(doc)
        }
        val reader = writer.reader
        val searcher: IndexSearcher = newSearcher(reader)

        val storedFields: StoredFields = reader.storedFields()
        for (i in 0..<numQueries) {
            val lat = nextLatitude()
            val lon = nextLongitude()
            val radius: Double = 50000000.0 * random().nextDouble()

            val expected = BitSet()
            for (doc in 0..<reader.maxDoc()) {
                val docLatitude =
                    storedFields.document(doc).getField("lat")!!.numericValue()!!.toDouble()
                val docLongitude =
                    storedFields.document(doc).getField("lon")!!.numericValue()!!.toDouble()
                val distance: Double = SloppyMath.haversinMeters(lat, lon, docLatitude, docLongitude)
                if (distance <= radius) {
                    expected.set(doc)
                }
            }

            val topDocs: TopDocs =
                searcher.search(
                    newDistanceQuery("field", lat, lon, radius), reader.maxDoc(), Sort.INDEXORDER
                )
            val actual = BitSet()
            for (doc in topDocs.scoreDocs) {
                actual.set(doc.doc)
            }

            try {
                assertEquals(expected, actual)
            } catch (e: AssertionError) {
                println("center: ($lat,$lon), radius=$radius")
                var doc = 0
                while (doc < reader.maxDoc()) {
                    val docLatitude =
                        storedFields.document(doc).getField("lat")!!.numericValue()!!.toDouble()
                    val docLongitude =
                        storedFields.document(doc).getField("lon")!!.numericValue()!!.toDouble()
                    val distance: Double = SloppyMath.haversinMeters(lat, lon, docLatitude, docLongitude)
                    println(
                        "$doc: ($docLatitude,$docLongitude), distance=$distance"
                    )
                    doc++
                }
                throw e
            }
        }
        reader.close()
        writer.close()
        dir.close()
    }

    @Throws(Exception::class)
    open fun testEquals() {
        var q1: Query
        var q2: Query

        val rect: Rectangle = nextBox()

        q1 = newRectQuery("field", rect.minLat, rect.maxLat, rect.minLon, rect.maxLon)
        q2 = newRectQuery("field", rect.minLat, rect.maxLat, rect.minLon, rect.maxLon)
        assertEquals(q1, q2)
        // for "impossible" ranges LatLonPoint.newBoxQuery will return MatchNoDocsQuery
        // changing the field is unrelated to that.
        if (q1 is MatchNoDocsQuery == false) {
            assertFalse(
                q1 == newRectQuery("field2", rect.minLat, rect.maxLat, rect.minLon, rect.maxLon)
            )
        }

        val lat = nextLatitude()
        val lon = nextLongitude()
        q1 = newDistanceQuery("field", lat, lon, 10000.0)
        q2 = newDistanceQuery("field", lat, lon, 10000.0)
        assertEquals(q1, q2)
        assertFalse(q1 == newDistanceQuery("field2", lat, lon, 10000.0))

        val lats = DoubleArray(5)
        val lons = DoubleArray(5)
        lats[0] = rect.minLat
        lons[0] = rect.minLon
        lats[1] = rect.maxLat
        lons[1] = rect.minLon
        lats[2] = rect.maxLat
        lons[2] = rect.maxLon
        lats[3] = rect.minLat
        lons[3] = rect.maxLon
        lats[4] = rect.minLat
        lons[4] = rect.minLon
        q1 = newPolygonQuery("field", Polygon(lats, lons))
        q2 = newPolygonQuery("field", Polygon(lats, lons))
        assertEquals(q1, q2)
        assertFalse(q1 == newPolygonQuery("field2", Polygon(lats, lons)))
    }

    /** return topdocs over a small set of points in field "point"  */
    @Throws(Exception::class)
    private fun searchSmallSet(query: Query, size: Int): TopDocs {
        // this is a simple systematic test, indexing these points
        // TODO: fragile: does not understand quantization in any way yet uses extremely high precision!
        val pts: Array<DoubleArray> =
            arrayOf(
                doubleArrayOf(32.763420, -96.774),
                doubleArrayOf(32.7559529921407, -96.7759895324707),
                doubleArrayOf(32.77866942010977, -96.77701950073242),
                doubleArrayOf(32.7756745755423, -96.7706036567688),
                doubleArrayOf(27.703618681345585, -139.73458170890808),
                doubleArrayOf(32.94823588839368, -96.4538113027811),
                doubleArrayOf(33.06047141970814, -96.65084838867188),
                doubleArrayOf(32.778650, -96.7772),
                doubleArrayOf(-88.56029371730983, -177.23537676036358),
                doubleArrayOf(33.541429799076354, -26.779373834241003),
                doubleArrayOf(26.774024500421728, -77.35379276106497),
                doubleArrayOf(-90.0, -14.796283808944777),
                doubleArrayOf(32.94823588839368, -178.8538113027811),
                doubleArrayOf(32.94823588839368, 178.8538113027811),
                doubleArrayOf(40.720611, -73.998776),
                doubleArrayOf(-44.5, -179.5)
            )

        val directory: Directory = newDirectory()

        // TODO: must these simple tests really rely on docid order
        val iwc: IndexWriterConfig = newIndexWriterConfig(MockAnalyzer(random()))
        iwc.setMaxBufferedDocs(TestUtil.nextInt(random(), 100, 1000))
        iwc.setMergePolicy(newLogMergePolicy())
        // Else seeds may not reproduce:
        iwc.setMergeScheduler(SerialMergeScheduler())
        val writer = RandomIndexWriter(random(), directory, iwc)

        for (p in pts) {
            val doc = Document()
            addPointToDoc("point", doc, p[0], p[1])
            writer.addDocument(doc)
        }

        // add explicit multi-valued docs
        run {
            var i = 0
            while (i < pts.size) {
                val doc = Document()
                addPointToDoc("point", doc, pts[i][0], pts[i][1])
                addPointToDoc("point", doc, pts[i + 1][0], pts[i + 1][1])
                writer.addDocument(doc)
                i += 2
            }
        }

        // index random string documents
        for (i in 0..<random().nextInt(10)) {
            val doc = Document()
            doc.add(StringField("string", i.toString(), Field.Store.NO))
            writer.addDocument(doc)
        }

        val reader = writer.reader
        writer.close()

        val searcher: IndexSearcher = newSearcher(reader)
        val topDocs: TopDocs = searcher.search(query, size)
        reader.close()
        directory.close()
        return topDocs
    }

    @Throws(Exception::class)
    open fun testSmallSetRect() {
        val td: TopDocs = searchSmallSet(newRectQuery("point", 32.778, 32.779, -96.778, -96.777), 5)
        assertEquals(4, td.totalHits.value)
    }

    @Throws(Exception::class)
    open fun testSmallSetDateline() {
        val td: TopDocs = searchSmallSet(newRectQuery("point", -45.0, -44.0, 179.0, -179.0), 20)
        assertEquals(2, td.totalHits.value)
    }

    @Throws(Exception::class)
    open fun testSmallSetMultiValued() {
        val td: TopDocs = searchSmallSet(newRectQuery("point", 32.755, 32.776, -96.454, -96.770), 20)
        // 3 single valued docs + 2 multi-valued docs
        assertEquals(5, td.totalHits.value)
    }

    @Throws(Exception::class)
    open fun testSmallSetWholeMap() {
        val td: TopDocs =
            searchSmallSet(
                newRectQuery(
                    "point",
                    GeoUtils.MIN_LAT_INCL,
                    GeoUtils.MAX_LAT_INCL,
                    GeoUtils.MIN_LON_INCL,
                    GeoUtils.MAX_LON_INCL
                ),
                20
            )
        assertEquals(24, td.totalHits.value)
    }

    @Throws(Exception::class)
    open fun testSmallSetPoly() {
        val td: TopDocs =
            searchSmallSet(
                newPolygonQuery(
                    "point",
                    Polygon(
                        doubleArrayOf(
                            33.073130,
                            32.9942669,
                            32.938386,
                            33.0374494,
                            33.1369762,
                            33.1162747,
                            33.073130,
                            33.073130
                        ),
                        doubleArrayOf(
                            -96.7682647, -96.8280029, -96.6288757, -96.4929199,
                            -96.6041564, -96.7449188, -96.76826477, -96.7682647
                        )
                    )
                ),
                5
            )
        assertEquals(2, td.totalHits.value)
    }

    @Throws(Exception::class)
    open fun testSmallSetPolyWholeMap() {
        val td: TopDocs =
            searchSmallSet(
                newPolygonQuery(
                    "point",
                    Polygon(
                        doubleArrayOf(
                            GeoUtils.MIN_LAT_INCL,
                            GeoUtils.MAX_LAT_INCL,
                            GeoUtils.MAX_LAT_INCL,
                            GeoUtils.MIN_LAT_INCL,
                            GeoUtils.MIN_LAT_INCL
                        ),
                        doubleArrayOf(
                            GeoUtils.MIN_LON_INCL,
                            GeoUtils.MIN_LON_INCL,
                            GeoUtils.MAX_LON_INCL,
                            GeoUtils.MAX_LON_INCL,
                            GeoUtils.MIN_LON_INCL
                        )
                    )
                ),
                20
            )
        assertEquals(24, td.totalHits.value, "testWholeMap failed")
    }

    @Throws(Exception::class)
    open fun testSmallSetDistance() {
        val td: TopDocs =
            searchSmallSet(newDistanceQuery("point", 32.94823588839368, -96.4538113027811, 6000.0), 20)
        assertEquals(2, td.totalHits.value)
    }

    @Throws(Exception::class)
    open fun testSmallSetTinyDistance() {
        val td: TopDocs = searchSmallSet(newDistanceQuery("point", 40.720611, -73.998776, 1.0), 20)
        assertEquals(2, td.totalHits.value)
    }

    /** see https://issues.apache.org/jira/browse/LUCENE-6905  */
    @Throws(Exception::class)
    open fun testSmallSetDistanceNotEmpty() {
        val td: TopDocs =
            searchSmallSet(
                newDistanceQuery("point", -88.56029371730983, -177.23537676036358, 7757.999232959935),
                20
            )
        assertEquals(2, td.totalHits.value)
    }

    /** Explicitly large  */
    @Throws(Exception::class)
    open fun testSmallSetHugeDistance() {
        val td: TopDocs =
            searchSmallSet(
                newDistanceQuery("point", 32.94823588839368, -96.4538113027811, 6000000.0), 20
            )
        assertEquals(16, td.totalHits.value)
    }

    @Throws(Exception::class)
    open fun testSmallSetDistanceDateline() {
        val td: TopDocs =
            searchSmallSet(
                newDistanceQuery("point", 32.94823588839368, -179.9538113027811, 120000.0), 20
            )
        assertEquals(3, td.totalHits.value)
    }

    @Throws(Exception::class)
    open fun testNarrowPolygonCloseToNorthPole() {
        val iwc: IndexWriterConfig = newIndexWriterConfig()
        iwc.setMergeScheduler(SerialMergeScheduler())
        val dir: Directory = newDirectory()
        val w = IndexWriter(dir, iwc)

        // index point closes to Lat 90
        val doc = Document()
        val base = Int.MAX_VALUE
        addPointToDoc(
            FIELD_NAME,
            doc,
            GeoEncodingUtils.decodeLatitude(base - 2),
            GeoEncodingUtils.decodeLongitude(base - 2)
        )
        w.addDocument(doc)
        w.flush()

        // query testing
        val reader = DirectoryReader.open(w)
        val s: IndexSearcher = newSearcher(reader)

        val minLat: Double = GeoEncodingUtils.decodeLatitude(base - 3)
        val maxLat: Double = GeoEncodingUtils.decodeLatitude(base)
        val minLon: Double = GeoEncodingUtils.decodeLongitude(base - 3)
        val maxLon: Double = GeoEncodingUtils.decodeLongitude(base)

        val query: Query =
            newPolygonQuery(
                FIELD_NAME,
                Polygon(
                    doubleArrayOf(minLat, minLat, maxLat, maxLat, minLat),
                    doubleArrayOf(minLon, maxLon, maxLon, minLon, minLon)
                )
            )

        assertEquals(1, s.count(query).toLong())
        IOUtils.close(w, reader, dir)
    }

    companion object {
        protected const val FIELD_NAME: String = "point"

        fun rectContainsPoint(rect: Rectangle, pointLat: Double, pointLon: Double): Boolean {
            assert(Double.isNaN(pointLat) == false)

            if (pointLat < rect.minLat || pointLat > rect.maxLat) {
                return false
            }

            if (rect.minLon <= rect.maxLon) {
                return pointLon >= rect.minLon && pointLon <= rect.maxLon
            } else {
                // Rect crosses dateline:
                return pointLon <= rect.maxLon || pointLon >= rect.minLon
            }
        }
    }
}
