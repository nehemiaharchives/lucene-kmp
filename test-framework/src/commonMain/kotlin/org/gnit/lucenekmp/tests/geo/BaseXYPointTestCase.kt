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
import org.gnit.lucenekmp.geo.Component2D
import org.gnit.lucenekmp.geo.XYCircle
import org.gnit.lucenekmp.geo.XYGeometry
import org.gnit.lucenekmp.geo.XYPoint
import org.gnit.lucenekmp.geo.XYPolygon
import org.gnit.lucenekmp.geo.XYRectangle
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexReader
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
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.isNaN
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.search.IndexSearcher
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
import org.gnit.lucenekmp.util.bkd.BKDWriter
import kotlin.math.sqrt
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/** Abstract class to do basic tests for a xy spatial impl (high level fields and queries)  */
abstract class BaseXYPointTestCase : LuceneTestCase() {
    // TODO: remove these hooks once all subclasses can pass with new random!
    protected fun nextX(): Float {
        return ShapeTestUtil.nextFloat(random())
    }

    protected fun nextY(): Float {
        return ShapeTestUtil.nextFloat(random())
    }

    protected fun nextBox(): XYRectangle {
        return ShapeTestUtil.nextBox(random())
    }

    protected fun nextPolygon(): XYPolygon {
        return ShapeTestUtil.nextPolygon()
    }

    protected fun nextGeometry(): Array<XYGeometry> {
        val len: Int = random().nextInt(4) + 1
        val geometries: Array<XYGeometry> = Array(len)
        /*for (i in 0..<len)*/ {
            when (random().nextInt(3)) {
                0 -> /*geometries[i] =*/ XYPoint(nextX(), nextY())
                1 -> /*geometries[i] =*/ nextBox()
                else -> /*geometries[i] =*/ nextPolygon()
            }
        }
        return geometries
    }

    /** Valid values that should not cause exception  */
    open fun testIndexExtremeValues() {
        val document = Document()
        addPointToDoc("foo", document, Float.MAX_VALUE, Float.MAX_VALUE)
        addPointToDoc("foo", document, Float.MAX_VALUE, -Float.MAX_VALUE)
        addPointToDoc("foo", document, -Float.MAX_VALUE, Float.MAX_VALUE)
        addPointToDoc("foo", document, -Float.MAX_VALUE, -Float.MAX_VALUE)
    }

    /** NaN: illegal  */
    open fun testIndexNaNValues() {
        val document = Document()
        var expected: IllegalArgumentException = expectThrows(IllegalArgumentException::class) {
                addPointToDoc("foo", document, Float.NaN, 50.0f)
            }
        assertTrue(expected.message!!.contains("invalid value"))

        expected = expectThrows(IllegalArgumentException::class) {
                addPointToDoc("foo", document, 50.0f, Float.NaN)
            }
        assertTrue(expected.message!!.contains("invalid value"))
    }

    /** Inf: illegal  */
    open fun testIndexInfValues() {
        val document = Document()
        var expected: IllegalArgumentException = expectThrows(IllegalArgumentException::class) {
                addPointToDoc("foo", document, Float.POSITIVE_INFINITY, 0.0f)
            }
        assertTrue(expected.message!!.contains("invalid value"))

        expected = expectThrows(IllegalArgumentException::class) {
                addPointToDoc("foo", document, Float.NEGATIVE_INFINITY, 0.0f)
            }
        assertTrue(expected.message!!.contains("invalid value"))

        expected = expectThrows(IllegalArgumentException::class) {
                addPointToDoc("foo", document, 0.0f, Float.POSITIVE_INFINITY)
            }
        assertTrue(expected.message!!.contains("invalid value"))

        expected = expectThrows(IllegalArgumentException::class) {
                addPointToDoc("foo", document, 0.0f, Float.NEGATIVE_INFINITY)
            }
        assertTrue(expected.message!!.contains("invalid value"))
    }

    /** Add a single point and search for it in a box  */
    @Throws(Exception::class)
    open fun testBoxBasics() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)

        // add a doc with a point
        val document = Document()
        addPointToDoc("field", document, 18.313694f, -65.227444f)
        writer.addDocument(document)

        // search and verify we found our doc
        val reader: IndexReader = writer.reader
        val searcher: IndexSearcher = newSearcher(reader)
        assertEquals(1, searcher.count(newRectQuery("field", 18f, 19f, -66f, -65f)).toLong())

        reader.close()
        writer.close()
        dir.close()
    }

    /** null field name not allowed  */
    open fun testBoxNull() {
        val expected: IllegalArgumentException =
            expectThrows(IllegalArgumentException::class) {
                newRectQuery(null, 18f, 19f, -66f, -65f)
            }
        assertTrue(expected.message!!.contains("field must not be null"))
    }

    // box should not accept invalid x/y
    @Throws(Exception::class)
    open fun testBoxInvalidCoordinates() {
        expectThrows(Exception::class) {
            newRectQuery("field", Float.NaN, Float.NaN, Float.NaN, Float.NaN)
        }
    }

    /** test we can search for a point  */
    @Throws(Exception::class)
    open fun testDistanceBasics() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)

        // add a doc with a location
        val document = Document()
        addPointToDoc("field", document, 18.313694f, -65.227444f)
        writer.addDocument(document)

        // search within 50km and verify we found our doc
        val reader: IndexReader = writer.reader
        val searcher: IndexSearcher = newSearcher(reader)
        assertEquals(1, searcher.count(newDistanceQuery("field", 18f, -65f, 20f)).toLong())

        reader.close()
        writer.close()
        dir.close()
    }

    /** null field name not allowed  */
    open fun testDistanceNull() {
        val expected: IllegalArgumentException = expectThrows(IllegalArgumentException::class) {
                newDistanceQuery(null, 18f, -65f, 50000f)
            }
        assertTrue(expected.message!!.contains("field must not be null"))
    }

    /** distance query should not accept invalid x/y as origin  */
    @Throws(Exception::class)
    open fun testDistanceIllegal() {
        expectThrows(Exception::class) {
            newDistanceQuery("field", Float.NaN, Float.NaN, 120000f)
        }
    }

    /** negative distance queries are not allowed  */
    open fun testDistanceNegative() {
        val expected: IllegalArgumentException = expectThrows(IllegalArgumentException::class) {
                newDistanceQuery("field", 18f, 19f, -1f)
            }
        assertTrue(expected.message!!.contains("radius"))
    }

    /** NaN distance queries are not allowed  */
    open fun testDistanceNaN() {
        val expected: IllegalArgumentException = expectThrows(IllegalArgumentException::class) {
                newDistanceQuery("field", 18f, 19f, Float.NaN)
            }
        assertTrue(expected.message!!.contains("radius"))
        assertTrue(expected.message!!.contains("NaN"))
    }

    /** Inf distance queries are not allowed  */
    open fun testDistanceInf() {
        var expected: IllegalArgumentException = expectThrows(IllegalArgumentException::class) {
            newDistanceQuery("field", 18f, 19f, Float.POSITIVE_INFINITY)
        }
        assertTrue(expected.message!!.contains("radius"), expected.message)
        assertTrue(expected.message!!.contains("finite"), expected.message)

        expected = expectThrows(IllegalArgumentException::class) {
                newDistanceQuery("field", 18f, 19f, Float.NEGATIVE_INFINITY)
            }
        assertTrue(expected.message!!.contains("radius"), expected.message)
        assertTrue(expected.message!!.contains("bigger than 0"), expected.message)
    }

    /** test we can search for a polygon  */
    @Throws(Exception::class)
    open fun testPolygonBasics() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)

        // add a doc with a point
        val document = Document()
        addPointToDoc("field", document, 18.313694f, -65.227444f)
        writer.addDocument(document)

        // search and verify we found our doc
        val reader: IndexReader = writer.reader
        val searcher: IndexSearcher = newSearcher(reader)
        assertEquals(
            1,
            searcher.count(
                newPolygonQuery(
                    "field",
                    XYPolygon(
                        floatArrayOf(18f, 18f, 19f, 19f, 18f), floatArrayOf(-66f, -65f, -65f, -66f, -66f)
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
        addPointToDoc("field", document, 18.313694f, -65.227444f)
        writer.addDocument(document)

        // search and verify we found our doc
        val reader: IndexReader = writer.reader
        val searcher: IndexSearcher = newSearcher(reader)
        val inner =
            XYPolygon(
                floatArrayOf(18.5f, 18.5f, 18.7f, 18.7f, 18.5f),
                floatArrayOf(-65.7f, -65.4f, -65.4f, -65.7f, -65.7f)
            )
        val outer =
            XYPolygon(
                floatArrayOf(18f, 18f, 19f, 19f, 18f), floatArrayOf(-66f, -65f, -65f, -66f, -66f), inner
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
        addPointToDoc("field", document, 18.313694f, -65.227444f)
        writer.addDocument(document)

        // search and verify we found our doc
        val reader: IndexReader = writer.reader
        val searcher: IndexSearcher = newSearcher(reader)
        val inner =
            XYPolygon(
                floatArrayOf(18.2f, 18.2f, 18.4f, 18.4f, 18.2f),
                floatArrayOf(-65.3f, -65.2f, -65.2f, -65.3f, -65.3f)
            )
        val outer =
            XYPolygon(
                floatArrayOf(18f, 18f, 19f, 19f, 18f), floatArrayOf(-66f, -65f, -65f, -66f, -66f), inner
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
        addPointToDoc("field", document, 18.313694f, -65.227444f)
        writer.addDocument(document)

        // search and verify we found our doc
        val reader: IndexReader = writer.reader
        val searcher: IndexSearcher = newSearcher(reader)
        val a =
            XYPolygon(floatArrayOf(28f, 28f, 29f, 29f, 28f), floatArrayOf(-56f, -55f, -55f, -56f, -56f))
        val b =
            XYPolygon(floatArrayOf(18f, 18f, 19f, 19f, 18f), floatArrayOf(-66f, -65f, -65f, -66f, -66f))
        assertEquals(1, searcher.count(newPolygonQuery("field", a, b)).toLong())

        reader.close()
        writer.close()
        dir.close()
    }

    /** null field name not allowed  */
    open fun testPolygonNullField() {
        val expected: IllegalArgumentException = expectThrows(IllegalArgumentException::class) {
                newPolygonQuery(
                    null,
                    XYPolygon(
                        floatArrayOf(18f, 18f, 19f, 19f, 18f), floatArrayOf(-66f, -65f, -65f, -66f, -66f)
                    )
                )
            }
        assertTrue(expected.message!!.contains("field must not be null"))
    }

    // A particularly tricky adversary for BKD tree:
    @Throws(Exception::class)
    open fun testSamePointManyTimes() {
        val numPoints: Int = atLeast(1000)

        // Every doc has 2 points:
        val theX = nextX()
        val theY = nextY()

        val xs = FloatArray(numPoints)
        Arrays.fill(xs, theX)

        val ys = FloatArray(numPoints)
        Arrays.fill(ys, theY)

        verify(xs, ys)
    }

    // A particularly tricky adversary for BKD tree:
    @Throws(Exception::class)
    open fun testLowCardinality() {
        val numPoints: Int = atLeast(1000)
        val cardinality: Int = TestUtil.nextInt(random(), 2, 20)

        val diffXs = FloatArray(cardinality)
        val diffYs = FloatArray(cardinality)
        for (i in 0..<cardinality) {
            diffXs[i] = nextX()
            diffYs[i] = nextY()
        }

        val xs = FloatArray(numPoints)
        val ys = FloatArray(numPoints)
        for (i in 0..<numPoints) {
            val index: Int = random().nextInt(cardinality)
            xs[i] = diffXs[index]
            ys[i] = diffYs[index]
        }
        verify(xs, ys)
    }

    @Throws(Exception::class)
    open fun testAllYEqual() {
        val numPoints: Int = atLeast(1000)
        val y = nextY()
        val xs = FloatArray(numPoints)
        val ys = FloatArray(numPoints)

        var haveRealDoc = false

        for (docID in 0..<numPoints) {
            val x: Int = random().nextInt(20)
            if (x == 17) {
                // Some docs don't have a point:
                ys[docID] = Float.NaN
                if (VERBOSE) {
                    println("  doc=$docID is missing")
                }
                continue
            }

            if (docID > 0 && x == 14 && haveRealDoc) {
                var oldDocID: Int
                while (true) {
                    oldDocID = random().nextInt(docID)
                    if (Float.isNaN(ys[oldDocID]) == false) {
                        break
                    }
                }

                // Fully identical point:
                ys[docID] = xs[oldDocID]
                if (VERBOSE) {
                    println("  doc=" + docID + " y=" + y + " x=" + xs[docID] + " (same x/y as doc=" + oldDocID + ")")
                }
            } else {
                xs[docID] = nextX()
                haveRealDoc = true
                if (VERBOSE) {
                    println("  doc=" + docID + " y=" + y + " x=" + xs[docID])
                }
            }
            ys[docID] = y
        }

        verify(xs, ys)
    }

    @Throws(Exception::class)
    open fun testAllXEqual() {
        val numPoints: Int = atLeast(1000)
        val theX = nextX()
        val xs = FloatArray(numPoints)
        val ys = FloatArray(numPoints)

        var haveRealDoc = false

        for (docID in 0..<numPoints) {
            val x: Int = random().nextInt(20)
            if (x == 17) {
                // Some docs don't have a point:
                ys[docID] = Float.NaN
                if (VERBOSE) {
                    println("  doc=$docID is missing")
                }
                continue
            }

            if (docID > 0 && x == 14 && haveRealDoc) {
                var oldDocID: Int
                while (true) {
                    oldDocID = random().nextInt(docID)
                    if (Float.isNaN(ys[oldDocID]) == false) {
                        break
                    }
                }

                // Fully identical point:
                ys[docID] = ys[oldDocID]
                if (VERBOSE) {
                    println("  doc=" + docID + " y=" + ys[docID] + " x=" + theX + " (same X/y as doc=" + oldDocID + ")")
                }
            } else {
                ys[docID] = nextY()
                haveRealDoc = true
                if (VERBOSE) {
                    println("  doc=" + docID + " y=" + ys[docID] + " x=" + theX)
                }
            }
            xs[docID] = theX
        }

        verify(xs, ys)
    }

    @Throws(Exception::class)
    open fun testMultiValued() {
        val numPoints: Int = atLeast(1000)
        // Every doc has 2 points:
        val xs = FloatArray(2 * numPoints)
        val ys = FloatArray(2 * numPoints)
        val dir: Directory = newDirectory()
        val iwc: IndexWriterConfig = newIndexWriterConfig()

        // We rely on docID order:
        iwc.setMergePolicy(newLogMergePolicy())
        // and on seeds being able to reproduce:
        iwc.setMergeScheduler(SerialMergeScheduler())
        val w = RandomIndexWriter(random(), dir, iwc)

        for (id in 0..<numPoints) {
            val doc = Document()
            xs[2 * id] = nextX()
            ys[2 * id] = nextY()
            doc.add(StringField("id", "" + id, Field.Store.YES))
            addPointToDoc(FIELD_NAME, doc, xs[2 * id], ys[2 * id])
            xs[2 * id + 1] = nextX()
            ys[2 * id + 1] = nextY()
            addPointToDoc(FIELD_NAME, doc, xs[2 * id + 1], ys[2 * id + 1])

            if (VERBOSE) {
                println("id=$id")
                println("  x=" + xs[2 * id] + " y=" + ys[2 * id])
                println("  x=" + xs[2 * id + 1] + " y=" + ys[2 * id + 1])
            }
            w.addDocument(doc)
        }

        // TODO: share w/ verify; just need parallel array of the expected ids
        if (random().nextBoolean()) {
            w.forceMerge(1)
        }
        val r: IndexReader = w.reader
        w.close()

        val s: IndexSearcher = newSearcher(r)

        val iters: Int = atLeast(25)
        for (iter in 0..<iters) {
            val rect: XYRectangle = nextBox()

            if (VERBOSE) {
                println("\nTEST: iter=$iter rect=$rect")
            }

            val query: Query = newRectQuery(FIELD_NAME, rect.minX, rect.maxX, rect.minY, rect.maxY)

            val hits: FixedBitSet = searchIndex(s, query, r.maxDoc())

            var fail = false

            val storedFields: StoredFields = s.storedFields()
            for (docID in 0..<ys.size / 2) {
                val yDoc1 = ys[2 * docID]
                val xDoc1 = xs[2 * docID]
                val yDoc2 = ys[2 * docID + 1]
                val xDoc2 = xs[2 * docID + 1]

                val result1 = rectContainsPoint(rect, xDoc1.toDouble(), yDoc1.toDouble())
                val result2 = rectContainsPoint(rect, xDoc2.toDouble(), yDoc2.toDouble())

                val expected = result1 || result2

                if (hits.get(docID) != expected) {
                    val id: String? = storedFields.document(docID).get("id")
                    if (expected) {
                        println("TEST: id=$id docID=$docID should match but did not")
                    } else {
                        println("TEST: id=$id docID=$docID should not match but did")
                    }
                    println("  rect=$rect")
                    println("  x=$xDoc1 y=$yDoc1\n  x=$xDoc2 y=$yDoc2")
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
        doTestRandom(10)
    }

    @Throws(Exception::class)
    open fun testRandomMedium() {
        doTestRandom(1000)
    }

    /*@org.apache.lucene.tests.util.LuceneTestCase.Nightly*/
    @Throws(Exception::class)
    open fun testRandomBig() {
        assumeFalse(
            "Direct codec can OOME on this test",
            TestUtil.getDocValuesFormat(FIELD_NAME) == "Direct"
        )
        doTestRandom(200000)
    }

    @Throws(Exception::class)
    private fun doTestRandom(count: Int) {
        val numPoints: Int = atLeast(count)

        if (VERBOSE) {
            println("TEST: numPoints=$numPoints")
        }

        val xs = FloatArray(numPoints)
        val ys = FloatArray(numPoints)

        var haveRealDoc = false

        for (id in 0..<numPoints) {
            val x: Int = random().nextInt(20)
            if (x == 17) {
                // Some docs don't have a point:
                ys[id] = Float.NaN
                if (VERBOSE) {
                    println("  id=$id is missing")
                }
                continue
            }

            if (id > 0 && x < 3 && haveRealDoc) {
                var oldID: Int
                while (true) {
                    oldID = random().nextInt(id)
                    if (Float.isNaN(ys[oldID]) == false) {
                        break
                    }
                }

                if (x == 0) {
                    // Identical x to old point
                    ys[id] = ys[oldID]
                    xs[id] = nextX()
                    if (VERBOSE) {
                        println(
                            "  id=" + id + " x=" + xs[id] + " y=" + ys[id] + " (same x as doc=" + oldID + ")"
                        )
                    }
                } else if (x == 1) {
                    // Identical y to old point
                    ys[id] = nextY()
                    xs[id] = xs[oldID]
                    if (VERBOSE) {
                        println(
                            "  id=" + id + " x=" + xs[id] + " y=" + ys[id] + " (same y as doc=" + oldID + ")"
                        )
                    }
                } else {
                    assert(x == 2)
                    // Fully identical point:
                    xs[id] = xs[oldID]
                    ys[id] = ys[oldID]
                    if (VERBOSE) {
                        println(
                            ("  id="
                                    + id
                                    + " x="
                                    + xs[id]
                                    + " y="
                                    + ys[id]
                                    + " (same X/y as doc="
                                    + oldID
                                    + ")")
                        )
                    }
                }
            } else {
                xs[id] = nextX()
                ys[id] = nextY()
                haveRealDoc = true
                if (VERBOSE) {
                    println("  id=" + id + " x=" + xs[id] + " y=" + ys[id])
                }
            }
        }

        verify(xs, ys)
    }

    protected abstract fun addPointToDoc(field: String, doc: Document, x: Float, y: Float)

    protected abstract fun newRectQuery(
        field: String?, minX: Float, maxX: Float, minY: Float, maxY: Float
    ): Query

    protected abstract fun newDistanceQuery(
        field: String?, centerX: Float, centerY: Float, radius: Float
    ): Query

    protected abstract fun newPolygonQuery(field: String?, vararg polygon: XYPolygon): Query

    protected abstract fun newGeometryQuery(field: String, vararg geometries: XYGeometry): Query

    @Throws(Exception::class)
    private fun verify(xs: FloatArray, ys: FloatArray) {
        // NaN means missing for the doc!!!!!
        verifyRandomRectangles(xs, ys)
        verifyRandomDistances(xs, ys)
        verifyRandomPolygons(xs, ys)
        verifyRandomGeometries(xs, ys)
    }

    @Throws(Exception::class)
    protected fun verifyRandomRectangles(xs: FloatArray, ys: FloatArray) {
        val iwc: IndexWriterConfig = newIndexWriterConfig()
        // Else seeds may not reproduce:
        iwc.setMergeScheduler(SerialMergeScheduler())
        // Else we can get O(N^2) merging:
        val mbd: Int = iwc.maxBufferedDocs
        if (mbd != -1 && mbd < xs.size / 100) {
            iwc.setMaxBufferedDocs(xs.size / 100)
        }
        val dir: Directory
        if (xs.size > 100000) {
            // Avoid slow codecs like SimpleText
            iwc.setCodec(TestUtil.getDefaultCodec())
            dir = newFSDirectory(createTempDir(this::class.simpleName!!))
        } else {
            dir = newDirectory()
        }

        val deleted: MutableSet<Int> = mutableSetOf()
        // RandomIndexWriter is too slow here:
        val w = IndexWriter(dir, iwc)
        indexPoints(xs, ys, deleted, w)
        val r: IndexReader = DirectoryReader.open(w)
        w.close()

        val s: IndexSearcher = newSearcher(r)

        val iters: Int = atLeast(25)

        val liveDocs: Bits? = MultiBits.getLiveDocs(s.indexReader)
        val maxDoc: Int = s.indexReader.maxDoc()

        for (iter in 0..<iters) {
            if (VERBOSE) {
                println("\nTEST: iter=$iter s=$s")
            }

            val rect: XYRectangle = nextBox()

            val query: Query = newRectQuery(FIELD_NAME, rect.minX, rect.maxX, rect.minY, rect.maxY)

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
                } else if (Float.isNaN(xs[id]) || Float.isNaN(ys[id])) {
                    expected = false
                } else {
                    expected = rectContainsPoint(rect, xs[id].toDouble(), ys[id].toDouble())
                }

                if (hits.get(docID) != expected) {
                    buildError(
                        docID,
                        expected,
                        id,
                        xs,
                        ys,
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
    protected fun verifyRandomDistances(xs: FloatArray, ys: FloatArray) {
        val iwc: IndexWriterConfig = newIndexWriterConfig()
        // Else seeds may not reproduce:
        iwc.setMergeScheduler(SerialMergeScheduler())
        // Else we can get O(N^2) merging:
        val mbd: Int = iwc.maxBufferedDocs
        if (mbd != -1 && mbd < xs.size / 100) {
            iwc.setMaxBufferedDocs(xs.size / 100)
        }
        val dir: Directory
        if (xs.size > 100000) {
            // Avoid slow codecs like SimpleText
            iwc.setCodec(TestUtil.getDefaultCodec())
            dir = newFSDirectory(createTempDir(this::class.simpleName!!))
        } else {
            dir = newDirectory()
        }

        val deleted: MutableSet<Int> = mutableSetOf()
        // RandomIndexWriter is too slow here:
        val w = IndexWriter(dir, iwc)
        indexPoints(xs, ys, deleted, w)
        val r: IndexReader = DirectoryReader.open(w)
        w.close()

        val s: IndexSearcher = newSearcher(r)

        val iters: Int = atLeast(25)

        val liveDocs: Bits? = MultiBits.getLiveDocs(s.indexReader)
        val maxDoc: Int = s.indexReader.maxDoc()

        for (iter in 0..<iters) {
            if (VERBOSE) {
                println("\nTEST: iter=$iter s=$s")
            }

            // Distance
            val centerX = nextX()
            val centerY = nextY()

            // So the query can cover at most 50% of the cartesian space:
            val radius: Float = random().nextFloat() * Float.MAX_VALUE / 2

            if (VERBOSE) {
                println("  radius = $radius")
            }

            val query: Query = newDistanceQuery(FIELD_NAME, centerX, centerY, radius)

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
                } else if (Float.isNaN(xs[id]) || Float.isNaN(ys[id])) {
                    expected = false
                } else {
                    expected = cartesianDistance(centerX.toDouble(), centerY.toDouble(), xs[id].toDouble(), ys[id].toDouble()) <= radius
                }

                if (hits.get(docID) != expected) {
                    val explain =
                        { b: StringBuilder ->
                            if (Double.isNaN(xs[id].toDouble()) == false) {
                                val distance = cartesianDistance(centerX.toDouble(), centerY.toDouble(), xs[id].toDouble(), ys[id].toDouble())
                                b.append("  centerX=")
                                    .append(centerX)
                                    .append(" centerY=")
                                    .append(centerY)
                                    .append(" distance=")
                                    .append(distance)
                                    .append(" vs radius=")
                                    .append(radius)
                            }
                        }
                    buildError(docID, expected, id, xs, ys, query, liveDocs, explain)
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
    protected fun verifyRandomPolygons(xs: FloatArray, ys: FloatArray) {
        val iwc: IndexWriterConfig = newIndexWriterConfig()
        // Else seeds may not reproduce:
        iwc.setMergeScheduler(SerialMergeScheduler())
        // Else we can get O(N^2) merging:
        val mbd: Int = iwc.maxBufferedDocs
        if (mbd != -1 && mbd < xs.size / 100) {
            iwc.setMaxBufferedDocs(xs.size / 100)
        }
        val dir: Directory
        if (xs.size > 100000) {
            // Avoid slow codecs like SimpleText
            iwc.setCodec(TestUtil.getDefaultCodec())
            dir = newFSDirectory(createTempDir(this::class.simpleName!!))
        } else {
            dir = newDirectory()
        }

        val deleted: MutableSet<Int> = mutableSetOf()
        // RandomIndexWriter is too slow here:
        val w = IndexWriter(dir, iwc)
        indexPoints(xs, ys, deleted, w)
        val r: IndexReader = DirectoryReader.open(w)
        w.close()

        // We can't wrap with "exotic" readers because points needs to work:
        val s: IndexSearcher = newSearcher(r)

        val iters: Int = atLeast(75)

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
                } else if (Float.isNaN(xs[id]) || Float.isNaN(ys[id])) {
                    expected = false
                } else {
                    expected = ShapeTestUtil.containsSlowly(polygon, xs[id].toDouble(), ys[id].toDouble())
                }

                if (hits.get(docID) != expected) {
                    buildError(
                        docID,
                        expected,
                        id,
                        xs,
                        ys,
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
    protected fun verifyRandomGeometries(xs: FloatArray, ys: FloatArray) {
        val iwc: IndexWriterConfig = newIndexWriterConfig()
        // Else seeds may not reproduce:
        iwc.setMergeScheduler(SerialMergeScheduler())
        // Else we can get O(N^2) merging:
        val mbd: Int = iwc.maxBufferedDocs
        if (mbd != -1 && mbd < xs.size / 100) {
            iwc.setMaxBufferedDocs(xs.size / 100)
        }
        val dir: Directory
        if (xs.size > 100000) {
            // Avoid slow codecs like SimpleText
            iwc.setCodec(TestUtil.getDefaultCodec())
            dir = newFSDirectory(createTempDir(this::class.simpleName!!))
        } else {
            dir = newDirectory()
        }

        val deleted: MutableSet<Int> = mutableSetOf()
        // RandomIndexWriter is too slow here:
        val w = IndexWriter(dir, iwc)
        indexPoints(xs, ys, deleted, w)
        val r: IndexReader = DirectoryReader.open(w)
        w.close()

        // We can't wrap with "exotic" readers because points needs to work:
        val s: IndexSearcher = newSearcher(r)

        val iters: Int = atLeast(75)

        val liveDocs: Bits? = MultiBits.getLiveDocs(s.indexReader)
        val maxDoc: Int = s.indexReader.maxDoc()

        for (iter in 0..<iters) {
            if (VERBOSE) {
                println("\nTEST: iter=$iter s=$s")
            }

            // geometries
            val geometries: Array<XYGeometry> = nextGeometry()
            val query: Query = newGeometryQuery(FIELD_NAME, *geometries)
            val component2D: Component2D = XYGeometry.create(*geometries)

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
                } else if (Float.isNaN(xs[id]) || Float.isNaN(ys[id])) {
                    expected = false
                } else {
                    expected = component2D.contains(xs[id].toDouble(), ys[id].toDouble())
                }

                if (hits.get(docID) != expected) {
                    buildError(
                        docID,
                        expected,
                        id,
                        xs,
                        ys,
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
    private fun indexPoints(xs: FloatArray, ys: FloatArray, deleted: MutableSet<Int>, w: IndexWriter) {
        for (id in xs.indices) {
            val doc = Document()
            doc.add(StringField("id", "" + id, Field.Store.NO))
            doc.add(NumericDocValuesField("id", id.toLong()))
            if (Float.isNaN(xs[id]) == false && Float.isNaN(ys[id]) == false) {
                addPointToDoc(FIELD_NAME, doc, xs[id], ys[id])
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
        xs: FloatArray,
        ys: FloatArray,
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
        b.append("  x=").append(xs[id]).append(" y=").append(ys[id]).append("\n")
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
        val rect: XYRectangle = ShapeTestUtil.nextBox(random())
        val dir: Directory = newDirectory()
        val iwc: IndexWriterConfig = newIndexWriterConfig()
        // Else seeds may not reproduce:
        iwc.setMergeScheduler(SerialMergeScheduler())
        val w = RandomIndexWriter(random(), dir, iwc)
        for (i in 0..2) {
            val y: Float
            if (i == 0) {
                y = rect.minY
            } else if (i == 1) {
                y = ((rect.minY.toDouble() + rect.maxY) / 2.0).toFloat()
            } else {
                y = rect.maxY
            }
            for (j in 0..2) {
                val x: Float
                if (j == 0) {
                    x = rect.minX
                } else if (j == 1) {
                    if (i == 1) {
                        continue
                    }
                    x = ((rect.minX.toDouble() + rect.maxX) / 2.0).toFloat()
                } else {
                    x = rect.maxX
                }

                val doc = Document()
                addPointToDoc(FIELD_NAME, doc, x, y)
                w.addDocument(doc)
            }
        }
        val r: IndexReader = w.reader
        val s: IndexSearcher = newSearcher(r, false)
        // exact edge cases
        assertEquals(8, s.count(newRectQuery(FIELD_NAME, rect.minX, rect.maxX, rect.minY, rect.maxY)).toLong())
        // expand 1 ulp in each direction if possible and test a slightly larger box!
        if (rect.minX != -Float.MAX_VALUE) {
            assertEquals(
                8,
                s.count(
                    newRectQuery(FIELD_NAME, Math.nextDown(rect.minX), rect.maxX, rect.minY, rect.maxY)
                ).toLong()
            )
        }
        if (rect.maxX != Float.MAX_VALUE) {
            assertEquals(
                8,
                s.count(
                    newRectQuery(FIELD_NAME, rect.minX, Math.nextUp(rect.maxX), rect.minY, rect.maxY)
                ).toLong()
            )
        }
        if (rect.minY != -Float.MAX_VALUE) {
            assertEquals(
                8,
                s.count(
                    newRectQuery(FIELD_NAME, rect.minX, rect.maxX, Math.nextDown(rect.minY), rect.maxY)
                ).toLong()
            )
        }
        if (rect.maxY != Float.MAX_VALUE) {
            assertEquals(
                8,
                s.count(
                    newRectQuery(FIELD_NAME, rect.minX, rect.maxX, rect.minY, Math.nextUp(rect.maxY))
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
            doRandomDistanceTest(10, 100)
        }
    }

    /** Runs with thousands of docs  */
    /*@org.apache.lucene.tests.util.LuceneTestCase.Nightly*/
    @Throws(Exception::class)
    open fun testRandomDistanceHuge() {
        for (iters in 0..9) {
            doRandomDistanceTest(2000, 100)
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
            val x = nextX()
            val y = nextY()

            // pre-normalize up front, so we can just use quantized value for testing and do simple exact
            // comparisons
            val doc = Document()
            addPointToDoc("field", doc, x, y)
            doc.add(StoredField("x", x))
            doc.add(StoredField("y", y))
            writer.addDocument(doc)
        }
        val reader: IndexReader = writer.reader
        val searcher: IndexSearcher = newSearcher(reader)

        val storedFields: StoredFields = reader.storedFields()
        for (i in 0..<numQueries) {
            val circle: XYCircle = ShapeTestUtil.nextCircle()
            val x: Float = circle.x
            val y: Float = circle.y
            val radius: Float = circle.radius

            val expected = BitSet()
            for (doc in 0..<reader.maxDoc()) {
                val docX = storedFields.document(doc).getField("x")!!.numericValue()!!.toFloat()
                val docY = storedFields.document(doc).getField("y")!!.numericValue()!!.toFloat()
                val distance = cartesianDistance(x.toDouble(), y.toDouble(), docX.toDouble(), docY.toDouble())
                if (distance <= radius) {
                    expected.set(doc)
                }
            }

            val topDocs: TopDocs =
                searcher.search(
                    newDistanceQuery("field", x, y, radius), reader.maxDoc(), Sort.INDEXORDER
                )
            val actual = BitSet()
            for (doc in topDocs.scoreDocs) {
                actual.set(doc.doc)
            }

            try {
                assertEquals(expected, actual)
            } catch (e: AssertionError) {
                println("center: ($x,$y), radius=$radius")
                var doc = 0
                while (doc < reader.maxDoc()) {
                    val docX = storedFields.document(doc).getField("x")!!.numericValue()!!.toFloat()
                    val docY = storedFields.document(doc).getField("y")!!.numericValue()!!.toFloat()
                    val distance = cartesianDistance(x.toDouble(), y.toDouble(), docX.toDouble(), docY.toDouble())
                    println("$doc: ($x,$y), distance=$distance")
                    doc++
                }
                throw e
            }
        }
        reader.close()
        writer.close()
        dir.close()
    }

    open fun testEquals() {
        var q1: Query
        var q2: Query

        val rect: XYRectangle = nextBox()

        q1 = newRectQuery("field", rect.minX, rect.maxX, rect.minY, rect.maxY)
        q2 = newRectQuery("field", rect.minX, rect.maxX, rect.minY, rect.maxY)
        assertEquals(q1, q2)

        val x = nextX()
        val y = nextY()
        q1 = newDistanceQuery("field", x, y, 10000.0f)
        q2 = newDistanceQuery("field", x, y, 10000.0f)
        assertEquals(q1, q2)
        assertFalse(q1 == newDistanceQuery("field2", x, y, 10000.0f))

        val xs = FloatArray(5)
        val ys = FloatArray(5)
        xs[0] = rect.minX
        ys[0] = rect.minY
        xs[1] = rect.maxX
        ys[1] = rect.minY
        xs[2] = rect.maxX
        ys[2] = rect.maxY
        xs[3] = rect.minX
        ys[3] = rect.maxY
        xs[4] = rect.minX
        ys[4] = rect.minY
        q1 = newPolygonQuery("field", XYPolygon(xs, ys))
        q2 = newPolygonQuery("field", XYPolygon(xs, ys))
        assertEquals(q1, q2)
        assertFalse(q1 == newPolygonQuery("field2", XYPolygon(xs, ys)))
    }

    /** return topdocs over a small set of points in field "point"  */
    @Throws(Exception::class)
    private fun searchSmallSet(query: Query, size: Int): TopDocs {
        // this is a simple systematic test, indexing these points
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
            addPointToDoc("point", doc, p[0].toFloat(), p[1].toFloat())
            writer.addDocument(doc)
        }

        // add explicit multi-valued docs
        run {
            var i = 0
            while (i < pts.size) {
                val doc = Document()
                addPointToDoc("point", doc, pts[i][0].toFloat(), pts[i][1].toFloat())
                addPointToDoc("point", doc, pts[i + 1][0].toFloat(), pts[i + 1][1].toFloat())
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

        val reader: IndexReader = writer.reader
        writer.close()

        val searcher: IndexSearcher = newSearcher(reader)
        val topDocs: TopDocs = searcher.search(query, size)
        reader.close()
        directory.close()
        return topDocs
    }

    @Throws(Exception::class)
    open fun testSmallSetRect() {
        val td: TopDocs = searchSmallSet(newRectQuery("point", 32.778f, 32.779f, -96.778f, -96.777f), 5)
        assertEquals(4, td.totalHits.value)
    }

    @Throws(Exception::class)
    open fun testSmallSetRect2() {
        val td: TopDocs = searchSmallSet(newRectQuery("point", -45.0f, -44.0f, -180.0f, 180.0f), 20)
        assertEquals(2, td.totalHits.value)
    }

    @Throws(Exception::class)
    open fun testSmallSetMultiValued() {
        val td: TopDocs = searchSmallSet(newRectQuery("point", 32.755f, 32.776f, -180f, 180.770f), 20)
        // 3 single valued docs + 2 multi-valued docs
        assertEquals(5, td.totalHits.value)
    }

    @Throws(Exception::class)
    open fun testSmallSetWholeSpace() {
        val td: TopDocs =
            searchSmallSet(
                newRectQuery(
                    "point", -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE
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
                    XYPolygon(
                        floatArrayOf(
                            33.073130f,
                            32.9942669f,
                            32.938386f,
                            33.0374494f,
                            33.1369762f,
                            33.1162747f,
                            33.073130f,
                            33.073130f
                        ),
                        floatArrayOf(
                            -96.7682647f,
                            -96.8280029f,
                            -96.6288757f,
                            -96.4929199f,
                            -96.6041564f,
                            -96.7449188f,
                            -96.76826477f,
                            -96.7682647f
                        )
                    )
                ),
                5
            )
        assertEquals(2, td.totalHits.value)
    }

    @Throws(Exception::class)
    open fun testSmallSetPolyWholeSpace() {
        val td: TopDocs =
            searchSmallSet(
                newPolygonQuery(
                    "point",
                    XYPolygon(
                        floatArrayOf(
                            -Float.MAX_VALUE,
                            Float.MAX_VALUE,
                            Float.MAX_VALUE,
                            -Float.MAX_VALUE,
                            -Float.MAX_VALUE
                        ),
                        floatArrayOf(
                            -Float.MAX_VALUE,
                            -Float.MAX_VALUE,
                            Float.MAX_VALUE,
                            Float.MAX_VALUE,
                            -Float.MAX_VALUE
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
            searchSmallSet(newDistanceQuery("point", 32.94823588839368f, -96.4538113027811f, 6.0f), 20)
        assertEquals(11, td.totalHits.value)
    }

    @Throws(Exception::class)
    open fun testSmallSetTinyDistance() {
        val td: TopDocs = searchSmallSet(newDistanceQuery("point", 40.720611f, -73.998776f, 0.1f), 20)
        assertEquals(2, td.totalHits.value)
    }

    /** Explicitly large  */
    @Throws(Exception::class)
    open fun testSmallSetHugeDistance() {
        val td: TopDocs =
            searchSmallSet(
                newDistanceQuery("point", 32.94823588839368f, -96.4538113027811f, Float.MAX_VALUE), 20
            )
        assertEquals(24, td.totalHits.value)
    }

    companion object {
        protected const val FIELD_NAME: String = "point"

        fun rectContainsPoint(rect: XYRectangle, x: Double, y: Double): Boolean {
            if (y < rect.minY || y > rect.maxY) {
                return false
            }
            return x >= rect.minX && x <= rect.maxX
        }

        fun cartesianDistance(x1: Double, y1: Double, x2: Double, y2: Double): Double {
            val diffX = x1 - x2
            val diffY = y1 - y2
            return sqrt(diffX * diffX + diffY * diffY)
        }
    }
}
