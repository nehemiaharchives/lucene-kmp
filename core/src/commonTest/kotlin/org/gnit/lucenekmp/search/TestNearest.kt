package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.LatLonDocValuesField
import org.gnit.lucenekmp.document.LatLonPoint
import org.gnit.lucenekmp.document.StoredField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.geo.GeoEncodingUtils
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.SerialMergeScheduler
import org.gnit.lucenekmp.index.StoredFields
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.geo.GeoTestUtil
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.SloppyMath
import kotlin.test.Test
import kotlin.test.assertEquals

class TestNearest : LuceneTestCase() {

    @Test
    fun testNearestNeighborWithDeletedDocs() {
        val dir: Directory = newDirectory()
        val w = RandomIndexWriter(random(), dir, getIndexWriterConfig())
        var doc = Document()
        doc.add(LatLonPoint("point", 40.0, 50.0))
        doc.add(StringField("id", "0", Field.Store.YES))
        w.addDocument(doc)

        doc = Document()
        doc.add(LatLonPoint("point", 45.0, 55.0))
        doc.add(StringField("id", "1", Field.Store.YES))
        w.addDocument(doc)

        var r = w.reader
        // can't wrap because we require Lucene60PointsFormat directly but e.g. ParallelReader wraps
        // with its own points impl:
        var s = newSearcher(r, false)
        var hit = LatLonPoint.nearest(s, "point", 40.0, 50.0, 1).scoreDocs[0] as FieldDoc
        assertEquals("0", r.storedFields().document(hit.doc).getField("id")!!.stringValue())
        r.close()

        w.deleteDocuments(Term("id", "0"))
        r = w.reader
        // can't wrap because we require Lucene60PointsFormat directly but e.g. ParallelReader wraps
        // with its own points impl:
        s = newSearcher(r, false)
        hit = LatLonPoint.nearest(s, "point", 40.0, 50.0, 1).scoreDocs[0] as FieldDoc
        assertEquals("1", r.storedFields().document(hit.doc).getField("id")!!.stringValue())
        r.close()
        w.close()
        dir.close()
    }

    @Test
    fun testNearestNeighborWithAllDeletedDocs() {
        val dir: Directory = newDirectory()
        val w = RandomIndexWriter(random(), dir, getIndexWriterConfig())
        var doc = Document()
        doc.add(LatLonPoint("point", 40.0, 50.0))
        doc.add(StringField("id", "0", Field.Store.YES))
        w.addDocument(doc)
        doc = Document()
        doc.add(LatLonPoint("point", 45.0, 55.0))
        doc.add(StringField("id", "1", Field.Store.YES))
        w.addDocument(doc)

        var r = w.reader
        // can't wrap because we require Lucene60PointsFormat directly but e.g. ParallelReader wraps
        // with its own points impl:
        var s = newSearcher(r, false)
        val hit = LatLonPoint.nearest(s, "point", 40.0, 50.0, 1).scoreDocs[0] as FieldDoc
        assertEquals("0", r.storedFields().document(hit.doc).getField("id")!!.stringValue())
        r.close()

        w.deleteDocuments(Term("id", "0"))
        w.deleteDocuments(Term("id", "1"))
        r = w.reader
        // can't wrap because we require Lucene60PointsFormat directly but e.g. ParallelReader wraps
        // with its own points impl:
        s = newSearcher(r, false)
        assertEquals(0, LatLonPoint.nearest(s, "point", 40.0, 50.0, 1).scoreDocs.size)
        r.close()
        w.close()
        dir.close()
    }

    @Test
    fun testTieBreakByDocID() {
        val dir: Directory = newDirectory()
        val w = IndexWriter(dir, getIndexWriterConfig())
        var doc = Document()
        doc.add(LatLonPoint("point", 40.0, 50.0))
        doc.add(StringField("id", "0", Field.Store.YES))
        w.addDocument(doc)
        doc = Document()
        doc.add(LatLonPoint("point", 40.0, 50.0))
        doc.add(StringField("id", "1", Field.Store.YES))
        w.addDocument(doc)

        val r = DirectoryReader.open(w)
        // can't wrap because we require Lucene60PointsFormat directly but e.g. ParallelReader wraps
        // with its own points impl:
        val hits = LatLonPoint.nearest(newSearcher(r, false), "point", 45.0, 50.0, 2).scoreDocs
        assertEquals("0", r.storedFields().document(hits[0].doc).getField("id")!!.stringValue())
        assertEquals("1", r.storedFields().document(hits[1].doc).getField("id")!!.stringValue())

        r.close()
        w.close()
        dir.close()
    }

    @Test
    fun testNearestNeighborWithNoDocs() {
        val dir: Directory = newDirectory()
        val w = RandomIndexWriter(random(), dir, getIndexWriterConfig())
        val r = w.reader
        // can't wrap because we require Lucene60PointsFormat directly but e.g. ParallelReader wraps
        // with its own points impl:
        assertEquals(
            0,
            LatLonPoint.nearest(newSearcher(r, false), "point", 40.0, 50.0, 1).scoreDocs.size
        )
        r.close()
        w.close()
        dir.close()
    }

    private fun quantizeLat(latRaw: Double): Double {
        return GeoEncodingUtils.decodeLatitude(GeoEncodingUtils.encodeLatitude(latRaw))
    }

    private fun quantizeLon(lonRaw: Double): Double {
        return GeoEncodingUtils.decodeLongitude(GeoEncodingUtils.encodeLongitude(lonRaw))
    }

    @Test
    fun testNearestNeighborRandom() {
        val numPoints = atLeast(1000)
        val dir =
            if (numPoints > 100000) {
                newFSDirectory(createTempDir(this::class.simpleName!!))
            } else {
                newDirectory()
            }
        val lats = DoubleArray(numPoints)
        val lons = DoubleArray(numPoints)

        val iwc = getIndexWriterConfig()
        iwc.setMergePolicy(newLogMergePolicy())
        iwc.setMergeScheduler(SerialMergeScheduler())
        val w = RandomIndexWriter(random(), dir, iwc)
        for (id in 0..<numPoints) {
            lats[id] = quantizeLat(GeoTestUtil.nextLatitude())
            lons[id] = quantizeLon(GeoTestUtil.nextLongitude())
            val doc = Document()
            doc.add(LatLonPoint("point", lats[id], lons[id]))
            doc.add(LatLonDocValuesField("point", lats[id], lons[id]))
            doc.add(StoredField("id", id))
            w.addDocument(doc)
        }

        if (random().nextBoolean()) {
            w.forceMerge(1)
        }

        val r = w.reader
        if (VERBOSE) {
            println("TEST: reader=$r")
        }
        // can't wrap because we require Lucene60PointsFormat directly but e.g. ParallelReader wraps
        // with its own points impl:
        val s = newSearcher(r, false)
        val iters = atLeast(100)
        for (iter in 0..<iters) {
            if (VERBOSE) {
                println("\nTEST: iter=$iter")
            }
            val pointLat = GeoTestUtil.nextLatitude()
            val pointLon = GeoTestUtil.nextLongitude()

            // dumb brute force search to get the expected result:
            val expectedHits = arrayOfNulls<FieldDoc>(lats.size)
            for (id in lats.indices) {
                val distance = SloppyMath.haversinMeters(pointLat, pointLon, lats[id], lons[id])
                val hit = FieldDoc(id, 0.0f, arrayOf(distance))
                expectedHits[id] = hit
            }

            expectedHits.sortWith(
                Comparator { a, b ->
                    val cmp = ((a!!.fields!![0] as Double)).compareTo(b!!.fields!![0] as Double)
                    if (cmp != 0) {
                        cmp
                    } else {
                        // tie break by smaller docID:
                        a.doc - b.doc
                    }
                }
            )

            val topN = TestUtil.nextInt(random(), 1, lats.size)

            if (VERBOSE) {
                println("\nhits for pointLat=$pointLat pointLon=$pointLon")
            }

            // Also test with MatchAllDocsQuery, sorting by distance:
            val fieldDocs =
                s.search(
                    MatchAllDocsQuery(),
                    topN,
                    Sort(LatLonDocValuesField.newDistanceSort("point", pointLat, pointLon))
                )

            val hits = LatLonPoint.nearest(s, "point", pointLat, pointLon, topN).scoreDocs
            val storedFields: StoredFields = r.storedFields()
            for (i in 0..<topN) {
                val expected = expectedHits[i]!!
                val expected2 = fieldDocs.scoreDocs[i] as FieldDoc
                val actual = hits[i] as FieldDoc
                val actualDoc = storedFields.document(actual.doc)

                if (VERBOSE) {
                    println("hit $i")
                    println(
                        "  expected id=${expected.doc} lat=${lats[expected.doc]} lon=${lons[expected.doc]} distance=${expected.fields!![0] as Double} meters"
                    )
                    println(
                        "  actual id=${actualDoc.getField("id")} distance=${actual.fields!![0]} meters"
                    )
                }

                assertEquals(expected.doc, actual.doc)
                assertEquals(expected.fields!![0] as Double, actual.fields!![0] as Double, 0.0)

                assertEquals(expected2.doc, actual.doc)
                assertEquals(expected2.fields!![0] as Double, actual.fields!![0] as Double, 0.0)
            }
        }

        r.close()
        w.close()
        dir.close()
    }

    private fun getIndexWriterConfig(): IndexWriterConfig {
        val iwc = newIndexWriterConfig()
        iwc.setCodec(TestUtil.getDefaultCodec())
        return iwc
    }
}
