package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.StoredField
import org.gnit.lucenekmp.document.XYDocValuesField
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.SerialMergeScheduler
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.compare
import org.gnit.lucenekmp.jdkport.doubleToLongBits
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.geo.ShapeTestUtil.nextFloat
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals

/** Simple tests for {@link XYDocValuesField#newDistanceSort} */
class TestXYPointDistanceSort : LuceneTestCase() {

    private fun cartesianDistance(x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val diffX = x1 - x2
        val diffY = y1 - y2
        return sqrt(diffX * diffX + diffY * diffY)
    }

    /** Add three points and sort by distance  */
    @Test
    @Throws(Exception::class)
    fun testDistanceSort() {
        val dir: Directory = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        val originX = 40.7143528f
        val originY = -74.0059731f

        // add some docs
        var doc = Document()
        val x1 = 40.759011f
        val y1 = -73.9844722f
        doc.add(XYDocValuesField("location", x1, y1))
        iw.addDocument(doc)
        val d1 = cartesianDistance(x1.toDouble(), y1.toDouble(), originX.toDouble(), originY.toDouble())

        doc = Document()
        val x2 = 40.718266f
        val y2 = -74.007819f
        doc.add(XYDocValuesField("location", x2, y2))
        iw.addDocument(doc)
        val d2 = cartesianDistance(x2.toDouble(), y2.toDouble(), originX.toDouble(), originY.toDouble())

        doc = Document()
        val x3 = 40.7051157f
        val y3 = -74.0088305f
        doc.add(XYDocValuesField("location", x3, y3))
        iw.addDocument(doc)
        val d3 = cartesianDistance(x3.toDouble(), y3.toDouble(), originX.toDouble(), originY.toDouble())

        val reader: IndexReader = iw.reader
        val searcher: IndexSearcher = newSearcher(reader)
        iw.close()

        val sort = Sort(XYDocValuesField.newDistanceSort("location", originX, originY))
        val td: TopDocs = searcher.search(MatchAllDocsQuery(), 3, sort)

        var d: FieldDoc = td.scoreDocs[0] as FieldDoc
        assertEquals(d2, d.fields!![0] as Double, 0.0)

        d = td.scoreDocs[1] as FieldDoc
        assertEquals(d3, d.fields!![0] as Double, 0.0)

        d = td.scoreDocs[2] as FieldDoc
        assertEquals(d1, d.fields!![0] as Double, 0.0)

        reader.close()
        dir.close()
    }

    /** Add two points (one doc missing) and sort by distance  */
    @Test
    @Throws(Exception::class)
    fun testMissingLast() {
        val dir: Directory = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        val originX = 40.7143528f
        val originY = -74.0059731f

        // missing
        var doc = Document()
        iw.addDocument(doc)

        doc = Document()
        val x2 = 40.718266f
        val y2 = -74.007819f
        doc.add(XYDocValuesField("location", x2, y2))
        iw.addDocument(doc)
        val d2 = cartesianDistance(x2.toDouble(), y2.toDouble(), originX.toDouble(), originY.toDouble())

        doc = Document()
        val x3 = 40.7051157f
        val y3 = -74.0088305f
        doc.add(XYDocValuesField("location", x3, y3))
        iw.addDocument(doc)
        val d3 = cartesianDistance(x3.toDouble(), y3.toDouble(), originX.toDouble(), originY.toDouble())

        val reader: IndexReader = iw.reader
        val searcher: IndexSearcher = newSearcher(reader)
        iw.close()

        val sort = Sort(XYDocValuesField.newDistanceSort("location", originX, originY))
        val td: TopDocs = searcher.search(MatchAllDocsQuery(), 3, sort)

        var d: FieldDoc = td.scoreDocs[0] as FieldDoc
        assertEquals(d2, d.fields!![0] as Double, 0.0)

        d = td.scoreDocs[1] as FieldDoc
        assertEquals(d3, d.fields!![0] as Double, 0.0)

        d = td.scoreDocs[2] as FieldDoc
        assertEquals(Double.POSITIVE_INFINITY, d.fields!![0] as Double, 0.0)

        reader.close()
        dir.close()
    }

    /** Run a few iterations with just 10 docs, hopefully easy to debug  */
    @Test
    @Throws(Exception::class)
    fun testRandom() {
        for (iters in 0..99) {
            doRandomTest(10, 100)
        }
    }

    /** Runs with thousands of docs  */
    @Test
    @Companion.Nightly
    @Throws(Exception::class)
    fun testRandomHuge() {
        for (iters in 0..9) {
            doRandomTest(200, 100) // TODO reduced from 2000, 100 to 200, 100 for dev speed
        }
    }

    // result class used for testing. holds an id+distance.
    // we sort these with Arrays.sort and compare with lucene's results
    class Result internal constructor(var id: Int, var distance: Double) : Comparable<Result> {
        override fun compareTo(o: Result): Int {
            val cmp: Int = Double.compare(distance, o.distance)
            if (cmp == 0) {
                return Int.compare(id, o.id)
            }
            return cmp
        }

        override fun hashCode(): Int {
            val prime = 31
            var result = 1
            val temp: Long
            temp = Double.doubleToLongBits(distance)
            result = prime * result + (temp xor (temp ushr 32)).toInt()
            result = prime * result + id
            return result
        }

        override fun equals(obj: Any?): Boolean {
            if (this === obj) return true
            if (obj == null) return false
            if (this::class != obj::class) return false
            val other: Result = obj as Result
            if (Double.doubleToLongBits(distance) != Double.doubleToLongBits(other.distance)) return false
            if (id != other.id) return false
            return true
        }

        override fun toString(): String {
            return "Result [id=$id, distance=$distance]"
        }
    }

    @Throws(IOException::class)
    private fun doRandomTest(numDocs: Int, numQueries: Int) {
        val dir: Directory = newDirectory()
        val iwc = newIndexWriterConfig()
        // else seeds may not to reproduce:
        iwc.setMergeScheduler(SerialMergeScheduler())
        val writer = RandomIndexWriter(random(), dir, iwc)

        for (i in 0..<numDocs) {
            val doc = Document()
            doc.add(StoredField("id", i))
            doc.add(NumericDocValuesField("id", i.toLong()))
            if (random().nextInt(10) > 7) {
                val x = nextFloat(random())
                val y = nextFloat(random())

                doc.add(XYDocValuesField("field", x, y))
                doc.add(StoredField("x", x))
                doc.add(StoredField("y", y))
            } // otherwise "missing"

            writer.addDocument(doc)
        }
        val reader: IndexReader = writer.reader
        val storedFields = reader.storedFields()
        val searcher: IndexSearcher = newSearcher(reader)

        for (i in 0..<numQueries) {
            val x = nextFloat(random())
            val y = nextFloat(random())
            val missingValue = Double.POSITIVE_INFINITY

            val expected: Array<Result> = arrayOfNulls<Result>(reader.maxDoc()) as Array<Result>

            for (doc in 0..<reader.maxDoc()) {
                val targetDoc = storedFields.document(doc)
                val distance: Double
                if (targetDoc.getField("x") == null) {
                    distance = missingValue // missing
                } else {
                    val docX: Double = targetDoc.getField("x")!!.numericValue()!!.toDouble()
                    val docY: Double = targetDoc.getField("y")!!.numericValue()!!.toDouble()
                    distance = cartesianDistance(x.toDouble(), y.toDouble(), docX, docY)
                }
                val id: Int = targetDoc.getField("id")!!.numericValue()!!.toInt()
                expected[doc] = Result(id, distance)
            }

            Arrays.sort(expected)

            // randomize the topN a bit
            val topN = TestUtil.nextInt(random(), 1, reader.maxDoc())
            // sort by distance, then ID
            val distanceSort: SortField = XYDocValuesField.newDistanceSort("field", x, y)
            distanceSort.missingValue = missingValue
            val sort = Sort(distanceSort, SortField("id", SortField.Type.INT))

            val topDocs: TopDocs = searcher.search(MatchAllDocsQuery(), topN, sort)
            for (resultNumber in 0..<topN) {
                val fieldDoc: FieldDoc = topDocs.scoreDocs[resultNumber] as FieldDoc
                val actual = Result((fieldDoc.fields!![1] as Int), (fieldDoc.fields!![0] as Double))
                assertEquals(expected[resultNumber], actual)
            }

            // get page2 with searchAfter()
            if (topN < reader.maxDoc()) {
                val page2 = TestUtil.nextInt(random(), 1, reader.maxDoc() - topN)
                val topDocs2: TopDocs =
                    searcher.searchAfter(topDocs.scoreDocs[topN - 1], MatchAllDocsQuery(), page2, sort)
                for (resultNumber in 0..<page2) {
                    val fieldDoc: FieldDoc = topDocs2.scoreDocs[resultNumber] as FieldDoc
                    val actual = Result((fieldDoc.fields!![1] as Int), (fieldDoc.fields!![0] as Double))
                    assertEquals(expected[topN + resultNumber], actual)
                }
            }
        }
        reader.close()
        writer.close()
        dir.close()
    }
}
