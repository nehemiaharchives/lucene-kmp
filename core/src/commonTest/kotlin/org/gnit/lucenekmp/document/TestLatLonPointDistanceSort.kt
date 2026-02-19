package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.geo.GeoEncodingUtils
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.SerialMergeScheduler
import org.gnit.lucenekmp.index.StoredFields
import org.gnit.lucenekmp.search.FieldDoc
import org.gnit.lucenekmp.search.MatchAllDocsQuery
import org.gnit.lucenekmp.search.Sort
import org.gnit.lucenekmp.search.SortField
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.geo.GeoTestUtil
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.SloppyMath
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

/** Simple tests for [LatLonDocValuesField.newDistanceSort]. */
class TestLatLonPointDistanceSort : LuceneTestCase() {

    /** Add three points and sort by distance */
    @Test
    fun testDistanceSort() {
        val dir: Directory = newDirectory()
        val iw = RandomIndexWriter(random(), dir)

        // add some docs
        var doc = Document()
        doc.add(LatLonDocValuesField("location", 40.759011, -73.9844722))
        iw.addDocument(doc)

        doc = Document()
        doc.add(LatLonDocValuesField("location", 40.718266, -74.007819))
        iw.addDocument(doc)

        doc = Document()
        doc.add(LatLonDocValuesField("location", 40.7051157, -74.0088305))
        iw.addDocument(doc)

        val reader: IndexReader = iw.reader
        val searcher = newSearcher(reader)
        iw.close()

        val sort = Sort(LatLonDocValuesField.newDistanceSort("location", 40.7143528, -74.0059731))
        val td = searcher.search(MatchAllDocsQuery(), 3, sort)

        var d = td.scoreDocs[0] as FieldDoc
        assertEquals(462.1028401330431, (d.fields!![0] as Number).toDouble(), 0.0)

        d = td.scoreDocs[1] as FieldDoc
        assertEquals(1054.9842850974826, (d.fields!![0] as Number).toDouble(), 0.0)

        d = td.scoreDocs[2] as FieldDoc
        assertEquals(5285.881528419706, (d.fields!![0] as Number).toDouble(), 0.0)

        reader.close()
        dir.close()
    }

    /** Add two points (one doc missing) and sort by distance */
    @Test
    fun testMissingLast() {
        val dir: Directory = newDirectory()
        val iw = RandomIndexWriter(random(), dir)

        // missing
        var doc = Document()
        iw.addDocument(doc)

        doc = Document()
        doc.add(LatLonDocValuesField("location", 40.718266, -74.007819))
        iw.addDocument(doc)

        doc = Document()
        doc.add(LatLonDocValuesField("location", 40.7051157, -74.0088305))
        iw.addDocument(doc)

        val reader: IndexReader = iw.reader
        val searcher = newSearcher(reader)
        iw.close()

        val sort = Sort(LatLonDocValuesField.newDistanceSort("location", 40.7143528, -74.0059731))
        val td = searcher.search(MatchAllDocsQuery(), 3, sort)

        var d = td.scoreDocs[0] as FieldDoc
        assertEquals(462.1028401330431, (d.fields!![0] as Number).toDouble(), 0.0)

        d = td.scoreDocs[1] as FieldDoc
        assertEquals(1054.9842850974826, (d.fields!![0] as Number).toDouble(), 0.0)

        d = td.scoreDocs[2] as FieldDoc
        assertEquals(Double.POSITIVE_INFINITY, (d.fields!![0] as Number).toDouble(), 0.0)

        reader.close()
        dir.close()
    }

    /** Run a few iterations with just 10 docs, hopefully easy to debug */
    @Test
    fun testRandom() {
        for (iters in 0..<3) { // TODO reduced from 100 to 3 for dev speed
            doRandomTest(3, 5) // TODO reduced from 10, 100 to 3, 10 for dev speed
        }
    }

    @Ignore
    /** Runs with thousands of docs */
    @LuceneTestCase.Companion.Nightly
    @Test
    fun testRandomHuge() {
        for (iters in 0..<3) { // TODO reduced from 10 to 3 for dev speed
            doRandomTest(4, 20) // TODO reduced from 2000, 100 to 4, 20 for dev speed
        }
    }

    // result class used for testing. holds an id+distance.
    // we sort these with Arrays.sort and compare with lucene's results
    internal class Result(var id: Int, var distance: Double) : Comparable<Result> {
        override fun compareTo(o: Result): Int {
            val cmp = distance.compareTo(o.distance)
            if (cmp == 0) {
                return id.compareTo(o.id)
            }
            return cmp
        }

        override fun hashCode(): Int {
            val prime = 31
            var result = 1
            val temp = distance.toBits()
            result = prime * result + (temp xor (temp ushr 32)).toInt()
            result = prime * result + id
            return result
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null) return false
            if (this::class != other::class) return false
            other as Result
            if (distance.toBits() != other.distance.toBits()) return false
            if (id != other.id) return false
            return true
        }

        override fun toString(): String {
            return "Result [id=$id, distance=$distance]"
        }
    }

    private fun doRandomTest(numDocs: Int, numQueries: Int) {
        val dir: Directory = newDirectory()
        val iwc: IndexWriterConfig = newIndexWriterConfig()
        // else seeds may not to reproduce:
        iwc.mergeScheduler = SerialMergeScheduler()
        val writer = RandomIndexWriter(random(), dir, iwc)

        for (i in 0..<numDocs) {
            val doc = Document()
            doc.add(StoredField("id", i))
            doc.add(NumericDocValuesField("id", i.toLong()))
            if (random().nextInt(10) > 7) {
                val latRaw = GeoTestUtil.nextLatitude()
                val lonRaw = GeoTestUtil.nextLongitude()
                // pre-normalize up front, so we can just use quantized value for testing and do simple
                // exact comparisons
                val lat = GeoEncodingUtils.decodeLatitude(GeoEncodingUtils.encodeLatitude(latRaw))
                val lon = GeoEncodingUtils.decodeLongitude(GeoEncodingUtils.encodeLongitude(lonRaw))

                doc.add(LatLonDocValuesField("field", lat, lon))
                doc.add(StoredField("lat", lat))
                doc.add(StoredField("lon", lon))
            } // otherwise "missing"
            writer.addDocument(doc)
        }
        val reader: IndexReader = writer.reader
        val searcher = newSearcher(reader)

        val storedFields: StoredFields = reader.storedFields()
        for (i in 0..<numQueries) {
            val lat = GeoTestUtil.nextLatitude()
            val lon = GeoTestUtil.nextLongitude()
            val missingValue = Double.POSITIVE_INFINITY

            val expected = Array(reader.maxDoc()) { Result(0, 0.0) }

            for (doc in 0..<reader.maxDoc()) {
                val targetDoc = storedFields.document(doc)
                val distance =
                    if (targetDoc.getField("lat") == null) {
                        missingValue // missing
                    } else {
                        val docLatitude = targetDoc.getField("lat")!!.numericValue()!!.toDouble()
                        val docLongitude = targetDoc.getField("lon")!!.numericValue()!!.toDouble()
                        SloppyMath.haversinMeters(lat, lon, docLatitude, docLongitude)
                    }
                val id = targetDoc.getField("id")!!.numericValue()!!.toInt()
                expected[doc] = Result(id, distance)
            }

            expected.sort()

            // randomize the topN a bit
            val topN = TestUtil.nextInt(random(), 1, reader.maxDoc())
            // sort by distance, then ID
            val distanceSort = LatLonDocValuesField.newDistanceSort("field", lat, lon)
            distanceSort.missingValue = missingValue
            val sort = Sort(distanceSort, SortField("id", SortField.Type.INT))

            val topDocs = searcher.search(MatchAllDocsQuery(), topN, sort)
            for (resultNumber in 0..<topN) {
                val fieldDoc = topDocs.scoreDocs[resultNumber] as FieldDoc
                val actual =
                    Result((fieldDoc.fields!![1] as Number).toInt(), (fieldDoc.fields!![0] as Number).toDouble())
                assertEquals(expected[resultNumber], actual)
            }

            // get page2 with searchAfter()
            if (topN < reader.maxDoc()) {
                val page2 = TestUtil.nextInt(random(), 1, reader.maxDoc() - topN)
                val topDocs2 = searcher.searchAfter(topDocs.scoreDocs[topN - 1], MatchAllDocsQuery(), page2, sort)
                for (resultNumber in 0..<page2) {
                    val fieldDoc = topDocs2.scoreDocs[resultNumber] as FieldDoc
                    val actual =
                        Result((fieldDoc.fields!![1] as Number).toInt(), (fieldDoc.fields!![0] as Number).toDouble())
                    assertEquals(expected[topN + resultNumber], actual)
                }
            }
        }
        reader.close()
        writer.close()
        dir.close()
    }
}
