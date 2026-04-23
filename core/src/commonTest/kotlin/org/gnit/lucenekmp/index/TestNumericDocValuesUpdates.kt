package org.gnit.lucenekmp.index

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okio.IOException
import org.gnit.lucenekmp.codecs.DocValuesFormat
import org.gnit.lucenekmp.document.BinaryDocValuesField
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.Field.Store
import org.gnit.lucenekmp.document.LongPoint
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.SortedDocValuesField
import org.gnit.lucenekmp.document.SortedSetDocValuesField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.jdkport.CountDownLatch
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.FieldDoc
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Sort
import org.gnit.lucenekmp.search.SortField
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.store.NRTCachingDirectory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.codecs.asserting.AssertingCodec
import org.gnit.lucenekmp.tests.codecs.asserting.AssertingDocValuesFormat
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.RandomPicks
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.IOUtils
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TestNumericDocValuesUpdates : LuceneTestCase() {

    private fun doc(id: Int): Document {
        return doc(id, (id + 1).toLong())
    }

    private fun doc(id: Int, value: Long): Document {
        val doc = Document()
        doc.add(StringField("id", "doc-$id", Store.NO))
        doc.add(NumericDocValuesField("val", value))
        return doc
    }

    @Test
    @Throws(Exception::class)
    fun testMultipleUpdatesSameDoc() {
        val dir = newDirectory()
        val conf = newIndexWriterConfig(MockAnalyzer(random()))

        conf.setMaxBufferedDocs(3) // small number of docs, so use a tiny maxBufferedDocs

        val writer = IndexWriter(dir, conf)

        writer.updateDocument(Term("id", "doc-1"), doc(1, 1000000000L))
        writer.updateNumericDocValue(Term("id", "doc-1"), "val", 1000001111L)
        writer.updateDocument(Term("id", "doc-2"), doc(2, 2000000000L))
        writer.updateDocument(Term("id", "doc-2"), doc(2, 2222222222L))
        writer.updateNumericDocValue(Term("id", "doc-1"), "val", 1111111111L)

        val reader: DirectoryReader =
            if (random().nextBoolean()) {
                writer.commit()
                DirectoryReader.open(dir)
            } else {
                DirectoryReader.open(writer)
            }
        val searcher = IndexSearcher(reader)

        var td =
            searcher.search(
                TermQuery(Term("id", "doc-1")),
                1,
                Sort(SortField("val", SortField.Type.LONG))
            )
        assertEquals(1, td.scoreDocs.size, "doc-1 missing?")
        assertEquals(1111111111L, (td.scoreDocs[0] as FieldDoc).fields!![0], "doc-1 value")

        td =
            searcher.search(
                TermQuery(Term("id", "doc-2")),
                1,
                Sort(SortField("val", SortField.Type.LONG))
            )
        assertEquals(1, td.scoreDocs.size, "doc-2 missing?")
        assertEquals(2222222222L, (td.scoreDocs[0] as FieldDoc).fields!![0], "doc-2 value")

        IOUtils.close(reader, writer, dir)
    }

    @Test
    @Throws(Exception::class)
    fun testBiasedMixOfRandomUpdates() {
        // 3 types of operations: add, updated, updateDV.
        // rather then randomizing equally, we'll pick (random) cutoffs so each test run is biased,
        // in terms of some ops happen more often then others
        val ADD_CUTOFF = TestUtil.nextInt(random(), 1, 98)
        val UPD_CUTOFF = TestUtil.nextInt(random(), ADD_CUTOFF + 1, 99)

        val dir = newDirectory()
        val conf = newIndexWriterConfig(MockAnalyzer(random()))

        val writer = IndexWriter(dir, conf)

        val numOperations = atLeast(1000)
        val expected: MutableMap<Int, Long> = HashMap(numOperations / 3)

        // start with at least one doc before any chance of updates
        val numSeedDocs = atLeast(1)
        for (i in 0 until numSeedDocs) {
            val value = random().nextLong()
            expected[i] = value
            writer.addDocument(doc(i, value))
        }

        for (i in 0 until numOperations) {
            val op = TestUtil.nextInt(random(), 1, 100)
            val value = random().nextLong()
            if (op <= ADD_CUTOFF) {
                val id = expected.size
                expected[id] = value
                writer.addDocument(doc(id, value))
            } else {
                val id = TestUtil.nextInt(random(), 0, expected.size - 1)
                expected[id] = value
                if (op <= UPD_CUTOFF) {
                    writer.updateDocument(Term("id", "doc-$id"), doc(id, value))
                } else {
                    writer.updateNumericDocValue(Term("id", "doc-$id"), "val", value)
                }
            }
        }

        writer.commit()

        val reader = DirectoryReader.open(dir)
        val searcher = IndexSearcher(reader)

        // TODO: make more efficient if max numOperations is going to be increased much
        for ((key, value) in expected) {
            val id = "doc-$key"
            val td =
                searcher.search(
                    TermQuery(Term("id", id)),
                    1,
                    Sort(SortField("val", SortField.Type.LONG))
                )
            assertEquals(1L, td.totalHits.value, "$id missing?")
            assertEquals(value, (td.scoreDocs[0] as FieldDoc).fields!![0], "$id value")
        }

        IOUtils.close(reader, writer, dir)
    }

    @Test
    @Throws(IOException::class)
    fun testUpdatesAreFlushed() {
        val dir = newDirectory()
        val writer =
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random(), MockTokenizer.WHITESPACE, false))
                    .setRAMBufferSizeMB(16.0)
            )
        writer.addDocument(doc(0)) // val=1
        writer.addDocument(doc(1)) // val=2
        writer.addDocument(doc(3)) // val=2
        writer.commit()
        writer.config.setRAMBufferSizeMB(0.00000001)
        assertEquals(1, writer.getFlushDeletesCount())
        writer.updateNumericDocValue(Term("id", "doc-0"), "val", 5L)
        assertEquals(2, writer.getFlushDeletesCount())
        writer.updateNumericDocValue(Term("id", "doc-1"), "val", 6L)
        assertEquals(3, writer.getFlushDeletesCount())
        writer.updateNumericDocValue(Term("id", "doc-2"), "val", 7L)
        assertEquals(4, writer.getFlushDeletesCount())
        writer.config.setRAMBufferSizeMB(1000.0)
        writer.updateNumericDocValue(Term("id", "doc-2"), "val", 7L)
        assertEquals(4, writer.getFlushDeletesCount())
        writer.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testSimple() {
        val dir = newDirectory()
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        // make sure random config doesn't flush on us
        conf.setMaxBufferedDocs(10)
        conf.setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH.toDouble())
        val writer = IndexWriter(dir, conf)
        writer.addDocument(doc(0)) // val=1
        writer.addDocument(doc(1)) // val=2
        if (random().nextBoolean()) { // randomly commit before the update is sent
            writer.commit()
        }
        writer.updateNumericDocValue(Term("id", "doc-0"), "val", 2L) // doc=0, exp=2

        val reader: DirectoryReader =
            if (random().nextBoolean()) {
                writer.close()
                DirectoryReader.open(dir)
            } else {
                DirectoryReader.open(writer).also { writer.close() }
            }

        assertEquals(1, reader.leaves().size)
        val r = reader.leaves()[0].reader()
        val ndv = r.getNumericDocValues("val")!!
        assertEquals(0, ndv.nextDoc())
        assertEquals(2, ndv.longValue())
        assertEquals(1, ndv.nextDoc())
        assertEquals(2, ndv.longValue())
        reader.close()

        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testUpdateFewSegments() {
        val dir = newDirectory()
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        conf.setMaxBufferedDocs(2) // generate few segments
        conf.setMergePolicy(NoMergePolicy.INSTANCE) // prevent merges for this test
        val writer = IndexWriter(dir, conf)
        val numDocs = 10
        val expectedValues = LongArray(numDocs)
        for (i in 0 until numDocs) {
            writer.addDocument(doc(i))
            expectedValues[i] = (i + 1).toLong()
        }
        writer.commit()

        // update few docs
        for (i in 0 until numDocs) {
            if (random().nextDouble() < 0.4) {
                val value = ((i + 1) * 2).toLong()
                writer.updateNumericDocValue(Term("id", "doc-$i"), "val", value)
                expectedValues[i] = value
            }
        }

        val reader: DirectoryReader =
            if (random().nextBoolean()) {
                writer.close()
                DirectoryReader.open(dir)
            } else {
                DirectoryReader.open(writer).also { writer.close() }
            }

        for (context in reader.leaves()) {
            val r = context.reader()
            val ndv = r.getNumericDocValues("val")
            assertNotNull(ndv)
            for (i in 0 until r.maxDoc()) {
                val expected = expectedValues[i + context.docBase]
                assertEquals(i, ndv.nextDoc())
                val actual = ndv.longValue()
                assertEquals(expected, actual)
            }
        }

        reader.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testReopen() {
        val dir = newDirectory()
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        val writer = IndexWriter(dir, conf)
        writer.addDocument(doc(0))
        writer.addDocument(doc(1))

        val isNRT = random().nextBoolean()
        val reader1: DirectoryReader =
            if (isNRT) {
                DirectoryReader.open(writer)
            } else {
                writer.commit()
                DirectoryReader.open(dir)
            }
        println("TEST: isNRT=$isNRT")

        // update doc
        writer.updateNumericDocValue(Term("id", "doc-0"), "val", 10L) // update doc-0's value to 10
        if (!isNRT) {
            writer.commit()
        }

        // reopen reader and assert only it sees the update
        println("TEST: openIfChanged")
        val reader2 =
            if (isNRT) {
                DirectoryReader.openIfChanged(reader1, writer)
            } else {
                DirectoryReader.open(dir)
            }
        assertNotNull(reader2)
        assertTrue(reader1 !== reader2)
        val dvs1 = reader1.leaves()[0].reader().getNumericDocValues("val")!!
        assertEquals(0, dvs1.nextDoc())
        assertEquals(1, dvs1.longValue())

        val dvs2 = reader2.leaves()[0].reader().getNumericDocValues("val")!!
        assertEquals(0, dvs2.nextDoc())
        assertEquals(10, dvs2.longValue())

        writer.close()
        IOUtils.close(reader1, reader2, dir)
    }

    @Test
    @Throws(Exception::class)
    fun testUpdatesAndDeletes() {
        // create an index with a segment with only deletes, a segment with both
        // deletes and updates and a segment with only updates
        val dir = newDirectory()
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        conf.setMaxBufferedDocs(10) // control segment flushing
        conf.setMergePolicy(NoMergePolicy.INSTANCE) // prevent merges for this test
        val writer = IndexWriter(dir, conf)

        for (i in 0 until 6) {
            writer.addDocument(doc(i))
            if (i % 2 == 1) {
                writer.commit() // create 2-docs segments
            }
        }

        // delete doc-1 and doc-2
        writer.deleteDocuments(Term("id", "doc-1"), Term("id", "doc-2")) // 1st and 2nd segments

        // update docs 3 and 5
        writer.updateNumericDocValue(Term("id", "doc-3"), "val", 17L)
        writer.updateNumericDocValue(Term("id", "doc-5"), "val", 17L)

        val reader: DirectoryReader =
            if (random().nextBoolean()) {
                writer.close()
                DirectoryReader.open(dir)
            } else {
                DirectoryReader.open(writer).also { writer.close() }
            }

        val liveDocs = MultiBits.getLiveDocs(reader)
        val expectedLiveDocs = booleanArrayOf(true, false, false, true, true, true)
        for (i in expectedLiveDocs.indices) {
            assertEquals(expectedLiveDocs[i], liveDocs!!.get(i))
        }

        val expectedValues = longArrayOf(1, 2, 3, 17, 5, 17)
        val ndv = MultiDocValues.getNumericValues(reader, "val")!!
        for (i in expectedValues.indices) {
            assertEquals(i, ndv.nextDoc())
            assertEquals(expectedValues[i], ndv.longValue())
        }

        reader.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testUpdatesWithDeletes() {
        // update and delete different documents in the same commit session
        val dir = newDirectory()
        val conf =
            newIndexWriterConfig(MockAnalyzer(random()))
                .setMergePolicy(NoMergePolicy.INSTANCE) // otherwise a singleton merge could get rid of the delete
        conf.setMaxBufferedDocs(10) // control segment flushing
        val writer = IndexWriter(dir, conf)

        writer.addDocument(doc(0))
        writer.addDocument(doc(1))

        if (random().nextBoolean()) {
            writer.commit()
        }

        writer.deleteDocuments(Term("id", "doc-0"))
        writer.updateNumericDocValue(Term("id", "doc-1"), "val", 17L)

        val reader: DirectoryReader =
            if (random().nextBoolean()) {
                writer.close()
                DirectoryReader.open(dir)
            } else {
                DirectoryReader.open(writer).also { writer.close() }
            }

        val r = reader.leaves()[0].reader()
        assertFalse(r.liveDocs!!.get(0))
        val values = r.getNumericDocValues("val")!!
        assertEquals(1, values.advance(1))
        assertEquals(17, values.longValue())

        reader.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testMultipleDocValuesTypes() {
        val dir = newDirectory()
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        conf.setMaxBufferedDocs(10) // prevent merges
        val writer = IndexWriter(dir, conf)

        for (i in 0 until 4) {
            val doc = Document()
            doc.add(StringField("dvUpdateKey", "dv", Store.NO))
            doc.add(NumericDocValuesField("ndv", i.toLong()))
            doc.add(BinaryDocValuesField("bdv", newBytesRef(i.toString())))
            doc.add(SortedDocValuesField("sdv", newBytesRef(i.toString())))
            doc.add(SortedSetDocValuesField("ssdv", newBytesRef(i.toString())))
            doc.add(SortedSetDocValuesField("ssdv", newBytesRef((i * 2).toString())))
            writer.addDocument(doc)
        }
        writer.commit()

        // update all docs' ndv field
        writer.updateNumericDocValue(Term("dvUpdateKey", "dv"), "ndv", 17L)
        writer.close()

        val reader = DirectoryReader.open(dir)
        val r = reader.leaves()[0].reader()
        val ndv = r.getNumericDocValues("ndv")!!
        val bdv = r.getBinaryDocValues("bdv")!!
        val sdv = r.getSortedDocValues("sdv")!!
        val ssdv = r.getSortedSetDocValues("ssdv")!!
        for (i in 0 until r.maxDoc()) {
            assertEquals(i, ndv.nextDoc())
            assertEquals(17, ndv.longValue())
            assertEquals(i, bdv.nextDoc())
            val term = bdv.binaryValue()!!
            assertEquals(newBytesRef(i.toString()), term)
            assertEquals(i, sdv.nextDoc())
            var sortedTerm = sdv.lookupOrd(sdv.ordValue())
            assertEquals(newBytesRef(i.toString()), sortedTerm)
            assertEquals(i, ssdv.nextDoc())

            var ord = ssdv.nextOrd()
            sortedTerm = ssdv.lookupOrd(ord)
            assertEquals(i, sortedTerm!!.utf8ToString().toInt())
            if (i == 0) {
                assertEquals(1, ssdv.docValueCount())
            } else {
                assertEquals(2, ssdv.docValueCount())
                ord = ssdv.nextOrd()
                sortedTerm = ssdv.lookupOrd(ord)
                assertEquals(i * 2, sortedTerm!!.utf8ToString().toInt())
            }
        }

        reader.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testMultipleNumericDocValues() {
        val dir = newDirectory()
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        conf.setMaxBufferedDocs(10) // prevent merges
        val writer = IndexWriter(dir, conf)

        for (i in 0 until 2) {
            val doc = Document()
            doc.add(StringField("dvUpdateKey", "dv", Store.NO))
            doc.add(NumericDocValuesField("ndv1", i.toLong()))
            doc.add(NumericDocValuesField("ndv2", i.toLong()))
            writer.addDocument(doc)
        }
        writer.commit()

        // update all docs' ndv1 field
        writer.updateNumericDocValue(Term("dvUpdateKey", "dv"), "ndv1", 17L)
        writer.close()

        val reader = DirectoryReader.open(dir)
        val r = reader.leaves()[0].reader()
        val ndv1 = r.getNumericDocValues("ndv1")!!
        val ndv2 = r.getNumericDocValues("ndv2")!!
        for (i in 0 until r.maxDoc()) {
            assertEquals(i, ndv1.nextDoc())
            assertEquals(17, ndv1.longValue())
            assertEquals(i, ndv2.nextDoc())
            assertEquals(i.toLong(), ndv2.longValue())
        }

        reader.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testDocumentWithNoValue() {
        val dir = newDirectory()
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        val writer = IndexWriter(dir, conf)

        for (i in 0 until 2) {
            val doc = Document()
            doc.add(StringField("dvUpdateKey", "dv", Store.NO))
            if (i == 0) { // index only one document with value
                doc.add(NumericDocValuesField("ndv", 5L))
            }
            writer.addDocument(doc)
        }
        writer.commit()

        // update all docs' ndv field
        writer.updateNumericDocValue(Term("dvUpdateKey", "dv"), "ndv", 17L)
        writer.close()

        val reader = DirectoryReader.open(dir)
        val r = reader.leaves()[0].reader()
        val ndv = r.getNumericDocValues("ndv")!!
        for (i in 0 until r.maxDoc()) {
            assertEquals(i, ndv.nextDoc())
            assertEquals(17, ndv.longValue(), "doc=$i has wrong numeric doc value")
        }

        reader.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testUpdateNonNumericDocValuesField() {
        // we don't support adding new fields or updating existing non-numeric-dv
        // fields through numeric updates
        val dir = newDirectory()
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        val writer = IndexWriter(dir, conf)

        val doc = Document()
        doc.add(StringField("key", "doc", Store.NO))
        doc.add(StringField("foo", "bar", Store.NO))
        writer.addDocument(doc) // flushed document
        writer.commit()
        writer.addDocument(doc) // in-memory document

        assertFailsWith<IllegalArgumentException> {
            writer.updateNumericDocValue(Term("key", "doc"), "ndv", 17L)
        }

        assertFailsWith<IllegalArgumentException> {
            writer.updateNumericDocValue(Term("key", "doc"), "foo", 17L)
        }

        writer.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testDifferentDVFormatPerField() {
        // test relies on separate instances of the "same thing"
        assert(TestUtil.getDefaultDocValuesFormat() !== TestUtil.getDefaultDocValuesFormat())
        val dir = newDirectory()
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        conf.setCodec(
            object : AssertingCodec() {
                override fun getDocValuesFormatForField(field: String): DocValuesFormat {
                    return TestUtil.getDefaultDocValuesFormat()
                }
            }
        )
        val writer = IndexWriter(dir, conf)

        val doc = Document()
        doc.add(StringField("key", "doc", Store.NO))
        doc.add(NumericDocValuesField("ndv", 5L))
        doc.add(SortedDocValuesField("sorted", newBytesRef("value")))
        writer.addDocument(doc) // flushed document
        writer.commit()
        writer.addDocument(doc) // in-memory document

        writer.updateNumericDocValue(Term("key", "doc"), "ndv", 17L)
        writer.close()

        val reader = DirectoryReader.open(dir)

        val ndv = MultiDocValues.getNumericValues(reader, "ndv")!!
        val sdv = MultiDocValues.getSortedValues(reader, "sorted")!!
        for (i in 0 until reader.maxDoc()) {
            assertEquals(i, ndv.nextDoc())
            assertEquals(17, ndv.longValue())
            assertEquals(i, sdv.nextDoc())
            val term = sdv.lookupOrd(sdv.ordValue())
            assertEquals(newBytesRef("value"), term)
        }

        reader.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testUpdateSameDocMultipleTimes() {
        val dir = newDirectory()
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        val writer = IndexWriter(dir, conf)

        val doc = Document()
        doc.add(StringField("key", "doc", Store.NO))
        doc.add(NumericDocValuesField("ndv", 5L))
        writer.addDocument(doc) // flushed document
        writer.commit()
        writer.addDocument(doc) // in-memory document

        writer.updateNumericDocValue(Term("key", "doc"), "ndv", 17L) // update existing field
        writer.updateNumericDocValue(
            Term("key", "doc"),
            "ndv",
            3L
        ) // update existing field 2nd time in this commit
        writer.close()

        val reader = DirectoryReader.open(dir)
        val ndv = MultiDocValues.getNumericValues(reader, "ndv")!!
        for (i in 0 until reader.maxDoc()) {
            assertEquals(i, ndv.nextDoc())
            assertEquals(3, ndv.longValue())
        }
        reader.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testSegmentMerges() {
        val dir = newDirectory()
        val random = random()
        var conf = newIndexWriterConfig(MockAnalyzer(random))
        var writer = IndexWriter(dir, conf)

        var docid = 0
        val numRounds = atLeast(10)
        for (rnd in 0 until numRounds) {
            var doc = Document()
            doc.add(StringField("key", "doc", Store.NO))
            doc.add(NumericDocValuesField("ndv", -1L))
            val numDocs = atLeast(30)
            for (i in 0 until numDocs) {
                doc.removeField("id")
                doc.add(StringField("id", docid.toString(), Store.NO))
                writer.addDocument(doc)
                docid++
            }

            val value = (rnd + 1).toLong()
            writer.updateNumericDocValue(Term("key", "doc"), "ndv", value)

            if (random.nextDouble() < 0.2) { // randomly delete one doc
                writer.deleteDocuments(Term("id", random.nextInt(docid).toString()))
            }

            // randomly commit or reopen-IW (or nothing), before forceMerge
            if (random.nextDouble() < 0.4) {
                writer.commit()
            } else if (random.nextDouble() < 0.1) {
                writer.close()
                conf = newIndexWriterConfig(MockAnalyzer(random))
                writer = IndexWriter(dir, conf)
            }

            // add another document with the current value, to be sure forceMerge has
            // something to merge (for instance, it could be that CMS finished merging
            // all segments down to 1 before the delete was applied, so when
            // forceMerge is called, the index will be with one segment and deletes
            // and some MPs might now merge it, thereby invalidating test's
            // assumption that the reader has no deletes).
            doc = Document()
            doc.add(StringField("id", docid.toString(), Store.NO))
            doc.add(StringField("key", "doc", Store.NO))
            doc.add(NumericDocValuesField("ndv", value))
            writer.addDocument(doc)
            docid++

            writer.forceMerge(1, true)

            val reader: DirectoryReader =
                if (random.nextBoolean()) {
                    writer.commit()
                    DirectoryReader.open(dir)
                } else {
                    DirectoryReader.open(writer)
                }

            assertEquals(1, reader.leaves().size)
            val r = reader.leaves()[0].reader()
            assertNull(r.liveDocs, "index should have no deletes after forceMerge")
            val ndv = r.getNumericDocValues("ndv")
            assertNotNull(ndv)
            for (i in 0 until r.maxDoc()) {
                assertEquals(i, ndv.nextDoc())
                assertEquals(value, ndv.longValue())
            }
            reader.close()
        }

        writer.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testUpdateDocumentByMultipleTerms() {
        // make sure the order of updates is respected, even when multiple terms affect same document
        val dir = newDirectory()
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        val writer = IndexWriter(dir, conf)

        val doc = Document()
        doc.add(StringField("k1", "v1", Store.NO))
        doc.add(StringField("k2", "v2", Store.NO))
        doc.add(NumericDocValuesField("ndv", 5L))
        writer.addDocument(doc) // flushed document
        writer.commit()
        writer.addDocument(doc) // in-memory document

        writer.updateNumericDocValue(Term("k1", "v1"), "ndv", 17L)
        writer.updateNumericDocValue(Term("k2", "v2"), "ndv", 3L)
        writer.close()

        val reader = DirectoryReader.open(dir)
        val ndv = MultiDocValues.getNumericValues(reader, "ndv")!!
        for (i in 0 until reader.maxDoc()) {
            assertEquals(i, ndv.nextDoc())
            assertEquals(3, ndv.longValue())
        }
        reader.close()
        dir.close()
    }

    class OneSortDoc(
        val id: Int,
        var value: Long,
        val sortValue: Long
    ) : Comparable<OneSortDoc> {
        var deleted: Boolean = false

        override fun compareTo(other: OneSortDoc): Int {
            var cmp = sortValue.compareTo(other.sortValue)
            if (cmp == 0) {
                cmp = id.compareTo(other.id)
                assert(cmp != 0)
            }
            return cmp
        }
    }

    @Test
    @Throws(Exception::class)
    fun testSortedIndex() {
        val dir = newDirectory()
        val iwc = newIndexWriterConfig()
        iwc.setIndexSort(Sort(SortField("sort", SortField.Type.LONG)))
        val w = RandomIndexWriter(random(), dir, iwc)

        val valueRange = TestUtil.nextInt(random(), 1, 1000)
        val sortValueRange = TestUtil.nextInt(random(), 1, 1000)

        val refreshChance = TestUtil.nextInt(random(), 5, 200)
        val deleteChance = TestUtil.nextInt(random(), 2, 100)

        var deletedCount = 0

        val docs = mutableListOf<OneSortDoc>()
        var r = w.getReader(true, false)

        val numIters = atLeast(1000)
        for (iter in 0 until numIters) {
            val value = random().nextInt(valueRange).toLong()
            if (docs.isEmpty() || random().nextInt(3) == 1) {
                val id = docs.size
                // add new doc
                val doc = Document()
                doc.add(newStringField("id", id.toString(), Field.Store.YES))
                doc.add(NumericDocValuesField("number", value))
                val sortValue = random().nextInt(sortValueRange).toLong()
                doc.add(NumericDocValuesField("sort", sortValue))
                w.addDocument(doc)

                docs.add(OneSortDoc(id, value, sortValue))
            } else {
                // update existing doc value
                val idToUpdate = random().nextInt(docs.size)
                w.updateNumericDocValue(Term("id", idToUpdate.toString()), "number", value)

                docs[idToUpdate].value = value
            }

            if (random().nextInt(deleteChance) == 0) {
                val idToDelete = random().nextInt(docs.size)
                w.deleteDocuments(Term("id", idToDelete.toString()))
                if (!docs[idToDelete].deleted) {
                    docs[idToDelete].deleted = true
                    deletedCount++
                }
            }

            if (random().nextInt(refreshChance) == 0) {
                val r2 = w.getReader(true, false)
                r.close()
                r = r2

                var liveCount = 0

                for (ctx in r.leaves()) {
                    val leafReader = ctx.reader()
                    val values = leafReader.getNumericDocValues("number")!!
                    val sortValues = leafReader.getNumericDocValues("sort")!!
                    val liveDocs = leafReader.liveDocs
                    val storedFields = leafReader.storedFields()

                    var lastSortValue = Long.MIN_VALUE
                    for (i in 0 until leafReader.maxDoc()) {
                        val doc = storedFields.document(i)
                        val sortDoc = docs[doc.get("id")!!.toInt()]

                        assertEquals(i, values.nextDoc())
                        assertEquals(i, sortValues.nextDoc())

                        if (liveDocs != null && !liveDocs.get(i)) {
                            assertTrue(sortDoc.deleted)
                            continue
                        }
                        assertFalse(sortDoc.deleted)

                        assertEquals(sortDoc.value, values.longValue())

                        val sortValue = sortValues.longValue()
                        assertEquals(sortDoc.sortValue, sortValue)

                        assertTrue(sortValue >= lastSortValue)
                        lastSortValue = sortValue
                        liveCount++
                    }
                }

                assertEquals(docs.size - deletedCount, liveCount)
            }
        }

        IOUtils.close(r, w, dir)
    }

    @Test
    @Throws(Exception::class)
    fun testManyReopensAndFields() {
        val dir = newDirectory()
        val random = random()
        val conf = newIndexWriterConfig(MockAnalyzer(random))
        val lmp = newLogMergePolicy()
        lmp.mergeFactor = 3
        conf.setMergePolicy(lmp)
        val writer = IndexWriter(dir, conf)

        val isNRT = random.nextBoolean()
        var reader: DirectoryReader =
            if (isNRT) {
                DirectoryReader.open(writer)
            } else {
                writer.commit()
                DirectoryReader.open(dir)
            }

        val numFields = random.nextInt(4) + 3
        val fieldValues = LongArray(numFields) { 1L }

        val numRounds = atLeast(15)
        var docID = 0
        for (i in 0 until numRounds) {
            val numDocs = atLeast(5)
            for (j in 0 until numDocs) {
                val doc = Document()
                doc.add(StringField("id", "doc-$docID", Store.YES))
                doc.add(StringField("key", "all", Store.NO)) // update key
                // add all fields with their current value
                for (f in fieldValues.indices) {
                    doc.add(NumericDocValuesField("f$f", fieldValues[f]))
                }
                writer.addDocument(doc)
                ++docID
            }

            val fieldIdx = random.nextInt(fieldValues.size)

            val updateField = "f$fieldIdx"
            writer.updateNumericDocValue(Term("key", "all"), updateField, ++fieldValues[fieldIdx])

            if (random.nextDouble() < 0.2) {
                val deleteDoc = random.nextInt(docID) // might also delete an already deleted document, ok!
                writer.deleteDocuments(Term("id", "doc-$deleteDoc"))
            }

            // verify reader
            if (!isNRT) {
                writer.commit()
            }

            val newReader =
                if (isNRT) {
                    DirectoryReader.openIfChanged(reader, writer)
                } else {
                    DirectoryReader.open(dir)
                }
            assertNotNull(newReader)
            reader.close()
            reader = newReader
            assertTrue(reader.numDocs() > 0) // we delete at most one document per round
            for (context in reader.leaves()) {
                val r = context.reader()
                val liveDocs = r.liveDocs
                val storedFields = r.storedFields()
                for (field in fieldValues.indices) {
                    val f = "f$field"
                    val ndv = r.getNumericDocValues(f)
                    assertNotNull(ndv)
                    val maxDoc = r.maxDoc()
                    for (doc in 0 until maxDoc) {
                        if (liveDocs == null || liveDocs.get(doc)) {
                            assertEquals(doc, ndv.advance(doc), "advanced to wrong doc in seg=$r")
                            assertEquals(
                                fieldValues[field],
                                ndv.longValue(),
                                "invalid value for docID=$doc id=${storedFields.document(doc).get("id")}, field=$f, reader=$r doc=${storedFields.document(doc)}"
                            )
                        }
                    }
                }
            }
        }

        writer.close()
        IOUtils.close(reader, dir)
    }

    private abstract class TimedThread(private val name: String) {
        var failure: Throwable? = null
        private var job: Job? = null

        abstract fun runBody()

        fun start() {
            job = CoroutineScope(Dispatchers.Default).launch {
                try {
                    runBody()
                } catch (t: Throwable) {
                    failure = t
                    throw t
                }
            }
        }

        fun join() {
            runBlocking {
                job?.join()
            }
            if (failure != null) {
                throw RuntimeException("thread $name failed", failure)
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testUpdateSegmentWithNoDocValues() {
        val dir = newDirectory()
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        // prevent merges, otherwise by the time updates are applied
        // (writer.close()), the segments might have merged and that update becomes
        // legit.
        conf.setMergePolicy(NoMergePolicy.INSTANCE)
        val writer = IndexWriter(dir, conf)

        // first segment with NDV
        var doc = Document()
        doc.add(StringField("id", "doc0", Store.NO))
        doc.add(NumericDocValuesField("ndv", 3L))
        writer.addDocument(doc)
        doc = Document()
        doc.add(StringField("id", "doc4", Store.NO)) // document without 'ndv' field
        writer.addDocument(doc)
        writer.commit()

        // second segment with no NDV
        doc = Document()
        doc.add(StringField("id", "doc1", Store.NO))
        writer.addDocument(doc)
        doc = Document()
        doc.add(StringField("id", "doc2", Store.NO)) // document that isn't updated
        writer.addDocument(doc)
        writer.commit()

        // update document in the first segment - should not affect docsWithField of
        // the document without NDV field
        writer.updateNumericDocValue(Term("id", "doc0"), "ndv", 5L)

        // update document in the second segment - field should be added and we should
        // be able to handle the other document correctly (e.g. no NPE)
        writer.updateNumericDocValue(Term("id", "doc1"), "ndv", 5L)
        writer.close()

        val reader = DirectoryReader.open(dir)
        for (context in reader.leaves()) {
            val r = context.reader()
            val ndv = r.getNumericDocValues("ndv")!!
            assertEquals(0, ndv.nextDoc())
            assertEquals(5L, ndv.longValue())
            // docID 1 has no ndv value
            assertTrue(ndv.nextDoc() > 1)
        }
        reader.close()

        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testUpdateSegmentWithNoDocValues2() {
        val dir = newDirectory()
        var conf = newIndexWriterConfig(MockAnalyzer(random()))
        // prevent merges, otherwise by the time updates are applied
        // (writer.close()), the segments might have merged and that update becomes
        // legit.
        conf.setMergePolicy(NoMergePolicy.INSTANCE)
        var writer = IndexWriter(dir, conf)

        // first segment with NDV
        var doc = Document()
        doc.add(StringField("id", "doc0", Store.NO))
        doc.add(NumericDocValuesField("ndv", 3L))
        writer.addDocument(doc)
        doc = Document()
        doc.add(StringField("id", "doc4", Store.NO)) // document without 'ndv' field
        writer.addDocument(doc)
        writer.commit()

        // second segment with no NDV, but another dv field "foo"
        doc = Document()
        doc.add(StringField("id", "doc1", Store.NO))
        doc.add(NumericDocValuesField("foo", 3L))
        writer.addDocument(doc)
        doc = Document()
        doc.add(StringField("id", "doc2", Store.NO)) // document that isn't updated
        writer.addDocument(doc)
        writer.commit()

        // update document in the first segment - should not affect docsWithField of
        // the document without NDV field
        writer.updateNumericDocValue(Term("id", "doc0"), "ndv", 5L)

        // update document in the second segment - field should be added and we should
        // be able to handle the other document correctly (e.g. no NPE)
        writer.updateNumericDocValue(Term("id", "doc1"), "ndv", 5L)
        writer.close()

        var reader = DirectoryReader.open(dir)
        for (context in reader.leaves()) {
            val r = context.reader()
            val ndv = r.getNumericDocValues("ndv")!!
            assertEquals(0, ndv.nextDoc())
            assertEquals(5L, ndv.longValue())
            assertTrue(ndv.nextDoc() > 1)
        }
        reader.close()

        TestUtil.checkIndex(dir)

        conf = newIndexWriterConfig(MockAnalyzer(random()))
        writer = IndexWriter(dir, conf)
        writer.forceMerge(1)
        writer.close()

        reader = DirectoryReader.open(dir)
        val ar = getOnlyLeafReader(reader)
        assertEquals(DocValuesType.NUMERIC, ar.fieldInfos.fieldInfo("foo")!!.docValuesType)
        val searcher = IndexSearcher(reader)
        var td =
            searcher.search(
                TermQuery(Term("id", "doc0")),
                1,
                Sort(SortField("ndv", SortField.Type.LONG))
            )
        assertEquals(5L, (td.scoreDocs[0] as FieldDoc).fields!![0])
        td =
            searcher.search(
                TermQuery(Term("id", "doc1")),
                1,
                Sort(SortField("ndv", SortField.Type.LONG), SortField("foo", SortField.Type.LONG))
            )
        assertEquals(5L, (td.scoreDocs[0] as FieldDoc).fields!![0])
        assertEquals(3L, (td.scoreDocs[0] as FieldDoc).fields!![1])
        td =
            searcher.search(
                TermQuery(Term("id", "doc2")),
                1,
                Sort(SortField("ndv", SortField.Type.LONG))
            )
        assertEquals(0L, (td.scoreDocs[0] as FieldDoc).fields!![0])
        td =
            searcher.search(
                TermQuery(Term("id", "doc4")),
                1,
                Sort(SortField("ndv", SortField.Type.LONG))
            )
        assertEquals(0L, (td.scoreDocs[0] as FieldDoc).fields!![0])
        reader.close()

        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testUpdateSegmentWithPostingButNoDocValues() {
        val dir = newDirectory()
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        // prevent merges, otherwise by the time updates are applied
        // (writer.close()), the segments might have merged and that update becomes
        // legit.
        conf.setMergePolicy(NoMergePolicy.INSTANCE)
        val writer = IndexWriter(dir, conf)

        // first segment with ndv and ndv2 fields
        var doc = Document()
        doc.add(StringField("id", "doc0", Store.NO))
        doc.add(NumericDocValuesField("ndv", 5L))
        doc.add(StringField("ndv2", "10", Store.NO))
        doc.add(NumericDocValuesField("ndv2", 10L))
        writer.addDocument(doc)
        writer.commit()

        // second segment with no ndv and ndv2 fields
        doc = Document()
        doc.add(StringField("id", "doc1", Store.NO))
        writer.addDocument(doc)
        writer.commit()

        // update docValues of "ndv" field in the second segment
        // since global "ndv" field is docValues only field this is allowed
        writer.updateNumericDocValue(Term("id", "doc1"), "ndv", 5L)

        // update docValues of "ndv2" field in the second segment
        // since global "ndv2" field is not docValues only field this NOT allowed
        val exception = assertFailsWith<IllegalArgumentException> {
            writer.updateNumericDocValue(Term("id", "doc1"), "ndv2", 10L)
        }
        val expectedErrMsg =
            "Can't update [NUMERIC] doc values; the field [ndv2] must be doc values only field, but is also indexed with postings."
        assertEquals(expectedErrMsg, exception.message)
        writer.close()

        val reader = DirectoryReader.open(dir)
        for (context in reader.leaves()) {
            val r = context.reader()
            val ndv = r.getNumericDocValues("ndv")!!
            for (i in 0 until r.maxDoc()) {
                assertEquals(i, ndv.nextDoc())
                assertEquals(5L, ndv.longValue())
            }
        }
        reader.close()

        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testUpdateNumericDVFieldWithSameNameAsPostingField() {
        // this used to fail because FieldInfos.Builder neglected to update
        // globalFieldMaps.docValuesTypes map
        val dir = newDirectory()
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        val writer = IndexWriter(dir, conf)

        val doc = Document()
        doc.add(StringField("f", "mock-value", Store.NO))
        doc.add(NumericDocValuesField("f", 5L))
        writer.addDocument(doc)
        writer.commit()

        val exception = assertFailsWith<IllegalArgumentException> {
            writer.updateNumericDocValue(Term("f", "mock-value"), "f", 17L)
        }
        val expectedErrMsg =
            "Can't update [NUMERIC] doc values; the field [f] must be doc values only field, but is also indexed with postings."
        assertEquals(expectedErrMsg, exception.message)

        writer.close()

        val r = DirectoryReader.open(dir)
        val ndv = r.leaves()[0].reader().getNumericDocValues("f")!!
        assertEquals(0, ndv.nextDoc())
        assertEquals(5, ndv.longValue())
        r.close()

        dir.close()
    }

    @Test
    @OptIn(ExperimentalAtomicApi::class)
    @Throws(Exception::class)
    fun testStressMultiThreading() {
        val dir = newDirectory()
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        val writer = IndexWriter(dir, conf)

        // create index
        val numFields = TestUtil.nextInt(random(), 1, 4)
        val numDocs = if (TEST_NIGHTLY) atLeast(2000) else atLeast(200)
        for (i in 0 until numDocs) {
            val doc = Document()
            doc.add(StringField("id", "doc$i", Store.NO))
            val group = random().nextDouble()
            val g =
                if (group < 0.1) {
                    "g0"
                } else if (group < 0.5) {
                    "g1"
                } else if (group < 0.8) {
                    "g2"
                } else {
                    "g3"
                }
            doc.add(StringField("updKey", g, Store.NO))
            for (j in 0 until numFields) {
                val value = random().nextInt().toLong()
                doc.add(NumericDocValuesField("f$j", value))
                doc.add(NumericDocValuesField("cf$j", value * 2)) // control, always updated to f * 2
            }
            writer.addDocument(doc)
        }

        val numThreads = if (TEST_NIGHTLY) TestUtil.nextInt(random(), 3, 6) else 2
        val done = CountDownLatch(numThreads)
        val numUpdates = AtomicInt(atLeast(100))

        // same thread updates a field as well as reopens
        val threads = Array(numThreads) { i ->
            object : TimedThread("UpdateThread-$i") {
                override fun runBody() {
                    var reader: DirectoryReader? = null
                    try {
                        val random = random()
                        while (numUpdates.fetchAndAdd(-1) > 0) {
                            val group = random.nextDouble()
                            val t =
                                if (group < 0.1) {
                                    Term("updKey", "g0")
                                } else if (group < 0.5) {
                                    Term("updKey", "g1")
                                } else if (group < 0.8) {
                                    Term("updKey", "g2")
                                } else {
                                    Term("updKey", "g3")
                                }

                            val field = random.nextInt(numFields)
                            val f = "f$field"
                            val cf = "cf$field"
                            val updValue = random.nextInt().toLong()
                            writer.updateDocValues(
                                t,
                                NumericDocValuesField(f, updValue),
                                NumericDocValuesField(cf, updValue * 2)
                            )

                            if (random.nextDouble() < 0.2) {
                                // delete a random document
                                val doc = random.nextInt(numDocs)
                                writer.deleteDocuments(Term("id", "doc$doc"))
                            }

                            if (random.nextDouble() < 0.05) { // commit every 20 updates on average
                                writer.commit()
                            }

                            if (random.nextDouble() < 0.1) {
                                if (reader == null) {
                                    reader = DirectoryReader.open(writer)
                                } else {
                                    val r2 = DirectoryReader.openIfChanged(reader, writer)
                                    if (r2 != null) {
                                        reader.close()
                                        reader = r2
                                    }
                                }
                            }
                        }
                    } finally {
                        reader?.close()
                        done.countDown()
                    }
                }
            }
        }

        for (t in threads) {
            t.start()
        }
        done.await()
        for (t in threads) {
            t.join()
        }
        writer.close()

        val reader = DirectoryReader.open(dir)
        for (context in reader.leaves()) {
            val r = context.reader()
            for (i in 0 until numFields) {
                val ndv = r.getNumericDocValues("f$i")!!
                val control = r.getNumericDocValues("cf$i")!!
                val liveDocs = r.liveDocs
                for (j in 0 until r.maxDoc()) {
                    if (liveDocs == null || liveDocs.get(j)) {
                        assertEquals(j, ndv.advance(j))
                        assertEquals(j, control.advance(j))
                        assertEquals(control.longValue(), ndv.longValue() * 2)
                    }
                }
            }
        }
        reader.close()

        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testUpdateDifferentDocsInDifferentGens() {
        val dir = newDirectory()
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        conf.setMaxBufferedDocs(4)
        val writer = IndexWriter(dir, conf)
        val numDocs = atLeast(10)
        for (i in 0 until numDocs) {
            val doc = Document()
            doc.add(StringField("id", "doc$i", Store.NO))
            val value = random().nextInt().toLong()
            doc.add(NumericDocValuesField("f", value))
            doc.add(NumericDocValuesField("cf", value * 2))
            writer.addDocument(doc)
        }

        val numGens = atLeast(5)
        for (i in 0 until numGens) {
            val doc = random().nextInt(numDocs)
            val t = Term("id", "doc$doc")
            val value = random().nextLong()
            writer.updateDocValues(
                t,
                NumericDocValuesField("f", value),
                NumericDocValuesField("cf", value * 2)
            )
            val reader = DirectoryReader.open(writer)
            for (context in reader.leaves()) {
                val r = context.reader()
                val fndv = r.getNumericDocValues("f")!!
                val cfndv = r.getNumericDocValues("cf")!!
                for (j in 0 until r.maxDoc()) {
                    assertEquals(j, fndv.nextDoc())
                    assertEquals(j, cfndv.nextDoc())
                    assertEquals(cfndv.longValue(), fndv.longValue() * 2)
                }
            }
            reader.close()
        }
        writer.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testChangeCodec() {
        val dir = newDirectory()
        var conf = newIndexWriterConfig(MockAnalyzer(random()))
        conf.setMergePolicy(NoMergePolicy.INSTANCE) // disable merges to simplify test assertions.
        conf.setCodec(
            object : AssertingCodec() {
                override fun getDocValuesFormatForField(field: String): DocValuesFormat {
                    return TestUtil.getDefaultDocValuesFormat()
                }
            }
        )
        var writer = IndexWriter(dir, conf)
        var doc = Document()
        doc.add(StringField("id", "d0", Store.NO))
        doc.add(NumericDocValuesField("f1", 5L))
        doc.add(NumericDocValuesField("f2", 13L))
        writer.addDocument(doc)
        writer.close()

        // change format
        conf = newIndexWriterConfig(MockAnalyzer(random()))
        conf.setMergePolicy(NoMergePolicy.INSTANCE) // disable merges to simplify test assertions.
        conf.setCodec(
            object : AssertingCodec() {
                override fun getDocValuesFormatForField(field: String): DocValuesFormat {
                    return AssertingDocValuesFormat()
                }
            }
        )
        writer = IndexWriter(dir, conf)
        doc = Document()
        doc.add(StringField("id", "d1", Store.NO))
        doc.add(NumericDocValuesField("f1", 17L))
        doc.add(NumericDocValuesField("f2", 2L))
        writer.addDocument(doc)
        writer.updateNumericDocValue(Term("id", "d0"), "f1", 12L)
        writer.close()

        val reader = DirectoryReader.open(dir)
        val f1 = MultiDocValues.getNumericValues(reader, "f1")!!
        val f2 = MultiDocValues.getNumericValues(reader, "f2")!!
        assertEquals(0, f1.nextDoc())
        assertEquals(12L, f1.longValue())
        assertEquals(0, f2.nextDoc())
        assertEquals(13L, f2.longValue())
        assertEquals(1, f1.nextDoc())
        assertEquals(17L, f1.longValue())
        assertEquals(1, f2.nextDoc())
        assertEquals(2L, f2.longValue())
        reader.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testAddIndexes() {
        val dir1 = newDirectory()
        var conf = newIndexWriterConfig(MockAnalyzer(random()))
        var writer = IndexWriter(dir1, conf)

        val numDocs = atLeast(50)
        val numTerms = TestUtil.nextInt(random(), 1, numDocs / 5)
        val randomTerms = linkedSetOf<String>()
        while (randomTerms.size < numTerms) {
            randomTerms.add(TestUtil.randomSimpleString(random()))
        }

        // create first index
        for (i in 0 until numDocs) {
            val doc = Document()
            doc.add(StringField("id", RandomPicks.randomFrom(random(), randomTerms), Store.NO))
            doc.add(NumericDocValuesField("ndv", 4L))
            doc.add(NumericDocValuesField("control", 8L))
            writer.addDocument(doc)
        }

        if (random().nextBoolean()) {
            writer.commit()
        }

        // update some docs to a random value
        val value = random().nextInt().toLong()
        val term = Term("id", RandomPicks.randomFrom(random(), randomTerms))
        writer.updateDocValues(
            term,
            NumericDocValuesField("ndv", value),
            NumericDocValuesField("control", value * 2)
        )
        writer.close()

        val dir2 = newDirectory()
        conf = newIndexWriterConfig(MockAnalyzer(random()))
        writer = IndexWriter(dir2, conf)
        if (random().nextBoolean()) {
            writer.addIndexes(dir1)
        } else {
            val reader = DirectoryReader.open(dir1)
            TestUtil.addIndexesSlowly(writer, reader)
            reader.close()
        }
        writer.close()

        val reader = DirectoryReader.open(dir2)
        for (context in reader.leaves()) {
            val r = context.reader()
            val ndv = r.getNumericDocValues("ndv")!!
            val control = r.getNumericDocValues("control")!!
            for (i in 0 until r.maxDoc()) {
                assertEquals(i, ndv.nextDoc())
                assertEquals(i, control.nextDoc())
                assertEquals(ndv.longValue() * 2, control.longValue())
            }
        }
        reader.close()

        IOUtils.close(dir1, dir2)
    }

    @Test
    @Throws(Exception::class)
    fun testAddNewFieldAfterAddIndexes() {
        val dir1 = newDirectory()
        var conf = newIndexWriterConfig(MockAnalyzer(random())).setMergePolicy(NoMergePolicy.INSTANCE)
        val numDocs = atLeast(50)
        var writer = IndexWriter(dir1, conf)
        for (i in 0 until numDocs) {
            val doc = Document()
            doc.add(StringField("id", i.toString(), Store.NO))
            doc.add(NumericDocValuesField("a1", 0L))
            doc.add(NumericDocValuesField("a2", 1L))
            writer.addDocument(doc)
        }
        writer.close()

        val dir2 = newDirectory()
        conf = newIndexWriterConfig(MockAnalyzer(random())).setMergePolicy(NoMergePolicy.INSTANCE)
        writer = IndexWriter(dir2, conf)
        for (i in 0 until numDocs) {
            val doc = Document()
            doc.add(StringField("id", i.toString(), Store.NO))
            doc.add(NumericDocValuesField("i1", 0L))
            doc.add(NumericDocValuesField("i2", 1L))
            doc.add(NumericDocValuesField("i3", 2L))
            writer.addDocument(doc)
        }
        writer.close()

        val mainDir = newDirectory()
        conf = newIndexWriterConfig(MockAnalyzer(random())).setMergePolicy(NoMergePolicy.INSTANCE)
        writer = IndexWriter(mainDir, conf)
        writer.addIndexes(dir1, dir2)

        val originalFieldInfos = mutableListOf<FieldInfos>()
        var reader = DirectoryReader.open(writer)
        for (leaf in reader.leaves()) {
            originalFieldInfos.add(leaf.reader().fieldInfos)
        }
        reader.close()
        assertTrue(originalFieldInfos.size > 0)

        // update all doc values
        val value = random().nextInt().toLong()
        for (i in 0 until numDocs) {
            val term = Term("id", newBytesRef(i.toString()))
            writer.updateDocValues(term, NumericDocValuesField("ndv", value))
        }

        reader = DirectoryReader.open(writer)
        for (i in reader.leaves().indices) {
            val leafReader = reader.leaves()[i].reader()
            val origFieldInfos = originalFieldInfos[i]
            val newFieldInfos = leafReader.fieldInfos
            ensureConsistentFieldInfos(origFieldInfos, newFieldInfos)
            assertEquals(DocValuesType.NUMERIC, newFieldInfos.fieldInfo("ndv")!!.docValuesType)
            val ndv = leafReader.getNumericDocValues("ndv")!!
            for (docId in 0 until leafReader.maxDoc()) {
                assertEquals(docId, ndv.nextDoc())
                assertEquals(ndv.longValue(), value)
            }
        }
        reader.close()
        writer.close()
        IOUtils.close(dir1, dir2, mainDir)
    }

    @Test
    @Throws(Exception::class)
    fun testUpdatesAfterAddIndexes() {
        val dir1 = newDirectory()
        var conf = newIndexWriterConfig(MockAnalyzer(random())).setMergePolicy(NoMergePolicy.INSTANCE)
        val numDocs = atLeast(50)
        var writer = IndexWriter(dir1, conf)
        for (i in 0 until numDocs) {
            val doc = Document()
            doc.add(StringField("id", i.toString(), Store.NO))
            doc.add(NumericDocValuesField("ndv", 4L))
            doc.add(NumericDocValuesField("control", 8L))
            doc.add(LongPoint("i1", 4L))
            writer.addDocument(doc)
        }
        writer.close()

        val dir2 = newDirectory()
        conf = newIndexWriterConfig(MockAnalyzer(random())).setMergePolicy(NoMergePolicy.INSTANCE)
        writer = IndexWriter(dir2, conf)
        for (i in numDocs until numDocs * 2) {
            val doc = Document()
            doc.add(StringField("id", i.toString(), Store.NO))
            doc.add(NumericDocValuesField("ndv", 2L))
            doc.add(NumericDocValuesField("control", 4L))
            doc.add(LongPoint("i2", 16L))
            doc.add(LongPoint("i2", 24L))
            writer.addDocument(doc)
        }
        writer.close()

        val mainDir = newDirectory()
        conf = newIndexWriterConfig(MockAnalyzer(random())).setMergePolicy(NoMergePolicy.INSTANCE)
        writer = IndexWriter(mainDir, conf)
        writer.addIndexes(dir1, dir2)

        val originalFieldInfos = mutableListOf<FieldInfos>()
        var reader = DirectoryReader.open(writer)
        for (leaf in reader.leaves()) {
            originalFieldInfos.add(leaf.reader().fieldInfos)
        }
        reader.close()
        assertTrue(originalFieldInfos.size > 0)

        // update some docs to a random value
        val value = random().nextInt().toLong()
        val term = Term("id", newBytesRef((random().nextInt(numDocs) * 2).toString()))
        writer.updateDocValues(
            term,
            NumericDocValuesField("ndv", value),
            NumericDocValuesField("control", value * 2)
        )

        reader = DirectoryReader.open(writer)
        for (i in reader.leaves().indices) {
            val leafReader = reader.leaves()[i].reader()
            val origFieldInfos = originalFieldInfos[i]
            val newFieldInfos = leafReader.fieldInfos
            ensureConsistentFieldInfos(origFieldInfos, newFieldInfos)
            assertNotNull(newFieldInfos.fieldInfo("ndv"))
            assertEquals(DocValuesType.NUMERIC, newFieldInfos.fieldInfo("ndv")!!.docValuesType)
            assertEquals(DocValuesType.NUMERIC, newFieldInfos.fieldInfo("control")!!.docValuesType)
            val ndv = leafReader.getNumericDocValues("ndv")!!
            val control = leafReader.getNumericDocValues("control")!!
            for (docId in 0 until leafReader.maxDoc()) {
                assertEquals(docId, ndv.nextDoc())
                assertEquals(docId, control.nextDoc())
                assertEquals(ndv.longValue() * 2, control.longValue())
            }
        }
        val searcher = IndexSearcher(reader)
        assertEquals(numDocs, searcher.count(LongPoint.newExactQuery("i1", 4L)))
        assertEquals(numDocs, searcher.count(LongPoint.newExactQuery("i2", 16L)))
        assertEquals(numDocs, searcher.count(LongPoint.newExactQuery("i2", 24L)))
        reader.close()
        writer.close()
        IOUtils.close(dir1, dir2, mainDir)
    }

    private fun ensureConsistentFieldInfos(old: FieldInfos, after: FieldInfos) {
        for (fi in old) {
            assertNotNull(after.fieldInfo(fi.number))
            assertNotNull(after.fieldInfo(fi.name))
            assertEquals(fi.number, after.fieldInfo(fi.name)!!.number)
            assertTrue(fi.docValuesGen <= after.fieldInfo(fi.name)!!.docValuesGen)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testDeleteUnusedUpdatesFiles() {
        val dir = newDirectory()
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        val writer = IndexWriter(dir, conf)

        val doc = Document()
        doc.add(StringField("id", "d0", Store.NO))
        doc.add(NumericDocValuesField("f1", 1L))
        doc.add(NumericDocValuesField("f2", 1L))
        writer.addDocument(doc)

        // update each field twice to make sure all unneeded files are deleted
        for (f in arrayOf("f1", "f2")) {
            writer.updateNumericDocValue(Term("id", "d0"), f, 2L)
            writer.commit()
            val numFiles = dir.listAll().size

            // update again, number of files shouldn't change (old field's gen is
            // removed)
            writer.updateNumericDocValue(Term("id", "d0"), f, 3L)
            writer.commit()

            assertEquals(numFiles, dir.listAll().size)
        }

        writer.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testTonsOfUpdates() {
        // LUCENE-5248: make sure that when there are many updates, we don't use too much RAM
        val dir = newDirectory()
        val random = random()
        val conf = newIndexWriterConfig(MockAnalyzer(random))
        conf.setRAMBufferSizeMB(IndexWriterConfig.DEFAULT_RAM_BUFFER_SIZE_MB)
        conf.setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH) // don't flush by doc
        val writer = IndexWriter(dir, conf)

        // test data: lots of documents (few 10Ks) and lots of update terms (few hundreds)
        val numDocs = if (TEST_NIGHTLY) atLeast(20000) else atLeast(200)
        val numNumericFields = atLeast(5)
        val numTerms = TestUtil.nextInt(random, 10, 100) // terms should affect many docs
        val updateTerms = linkedSetOf<String>()
        while (updateTerms.size < numTerms) {
            updateTerms.add(TestUtil.randomSimpleString(random))
        }

        // build a large index with many NDV fields and update terms
        for (i in 0 until numDocs) {
            val doc = Document()
            val numUpdateTerms = TestUtil.nextInt(random, 1, numTerms / 10)
            for (j in 0 until numUpdateTerms) {
                doc.add(StringField("upd", RandomPicks.randomFrom(random, updateTerms), Store.NO))
            }
            for (j in 0 until numNumericFields) {
                val value = random.nextInt().toLong()
                doc.add(NumericDocValuesField("f$j", value))
                doc.add(NumericDocValuesField("cf$j", value * 2))
            }
            writer.addDocument(doc)
        }

        writer.commit() // commit so there's something to apply to

        // set to flush every 2048 bytes (approximately every 12 updates), so we get
        // many flushes during numeric updates
        writer.config.setRAMBufferSizeMB(2048.0 / 1024 / 1024)
        val numUpdates = atLeast(100)
        for (i in 0 until numUpdates) {
            val field = random.nextInt(numNumericFields)
            val updateTerm = Term("upd", RandomPicks.randomFrom(random, updateTerms))
            val value = random.nextInt().toLong()
            writer.updateDocValues(
                updateTerm,
                NumericDocValuesField("f$field", value),
                NumericDocValuesField("cf$field", value * 2)
            )
        }

        writer.close()

        val reader = DirectoryReader.open(dir)
        for (context in reader.leaves()) {
            for (i in 0 until numNumericFields) {
                val r = context.reader()
                val f = r.getNumericDocValues("f$i")!!
                val cf = r.getNumericDocValues("cf$i")!!
                for (j in 0 until r.maxDoc()) {
                    assertEquals(j, f.nextDoc())
                    assertEquals(j, cf.nextDoc())
                    assertEquals(cf.longValue(), f.longValue() * 2, "reader=$r, field=f$i, doc=$j")
                }
            }
        }
        reader.close()

        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testUpdatesOrder() {
        val dir = newDirectory()
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        val writer = IndexWriter(dir, conf)

        val doc = Document()
        doc.add(StringField("upd", "t1", Store.NO))
        doc.add(StringField("upd", "t2", Store.NO))
        doc.add(NumericDocValuesField("f1", 1L))
        doc.add(NumericDocValuesField("f2", 1L))
        writer.addDocument(doc)
        writer.updateNumericDocValue(Term("upd", "t1"), "f1", 2L) // update f1 to 2
        writer.updateNumericDocValue(Term("upd", "t1"), "f2", 2L) // update f2 to 2
        writer.updateNumericDocValue(Term("upd", "t2"), "f1", 3L) // update f1 to 3
        writer.updateNumericDocValue(Term("upd", "t2"), "f2", 3L) // update f2 to 3
        writer.updateNumericDocValue(Term("upd", "t1"), "f1", 4L) // update f1 to 4 (but not f2)
        writer.close()

        val reader = DirectoryReader.open(dir)
        var dvs = reader.leaves()[0].reader().getNumericDocValues("f1")!!
        assertEquals(0, dvs.nextDoc())
        assertEquals(4, dvs.longValue())
        dvs = reader.leaves()[0].reader().getNumericDocValues("f2")!!
        assertEquals(0, dvs.nextDoc())
        assertEquals(3, dvs.longValue())
        reader.close()

        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testUpdateAllDeletedSegment() {
        val dir = newDirectory()
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        val writer = IndexWriter(dir, conf)

        val doc = Document()
        doc.add(StringField("id", "doc", Store.NO))
        doc.add(NumericDocValuesField("f1", 1L))
        writer.addDocument(doc)
        writer.addDocument(doc)
        writer.commit()
        writer.deleteDocuments(Term("id", "doc")) // delete all docs in the first segment
        writer.addDocument(doc)
        writer.updateNumericDocValue(Term("id", "doc"), "f1", 2L)
        writer.close()

        val reader = DirectoryReader.open(dir)
        assertEquals(1, reader.leaves().size)
        val dvs = reader.leaves()[0].reader().getNumericDocValues("f1")!!
        assertEquals(0, dvs.nextDoc())
        assertEquals(2, dvs.longValue())

        reader.close()

        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testUpdateTwoNonexistingTerms() {
        val dir = newDirectory()
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        val writer = IndexWriter(dir, conf)

        val doc = Document()
        doc.add(StringField("id", "doc", Store.NO))
        doc.add(NumericDocValuesField("f1", 1L))
        writer.addDocument(doc)
        // update w/ multiple nonexisting terms in same field
        writer.updateNumericDocValue(Term("c", "foo"), "f1", 2L)
        writer.updateNumericDocValue(Term("c", "bar"), "f1", 2L)
        writer.close()

        val reader = DirectoryReader.open(dir)
        assertEquals(1, reader.leaves().size)
        val dvs = reader.leaves()[0].reader().getNumericDocValues("f1")!!
        assertEquals(0, dvs.nextDoc())
        assertEquals(1, dvs.longValue())
        reader.close()

        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testIOContext() {
        // LUCENE-5591: make sure we pass an IOContext with an approximate
        // segmentSize in FlushInfo
        val dir = newDirectory()
        var conf = newIndexWriterConfig(MockAnalyzer(random()))
        // we want a single large enough segment so that a doc-values update writes a large file
        conf.setMergePolicy(NoMergePolicy.INSTANCE)
        conf.setMaxBufferedDocs(Int.MAX_VALUE) // manually flush
        conf.setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH.toDouble())
        var writer = IndexWriter(dir, conf)
        for (i in 0 until 100) {
            writer.addDocument(doc(i))
        }
        writer.commit()
        writer.close()

        val cachingDir = NRTCachingDirectory(dir, 100.0, 1 / (1024.0 * 1024.0))
        conf = newIndexWriterConfig(MockAnalyzer(random()))
        // we want a single large enough segment so that a doc-values update writes a large file
        conf.setMergePolicy(NoMergePolicy.INSTANCE)
        conf.setMaxBufferedDocs(Int.MAX_VALUE) // manually flush
        conf.setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH.toDouble())
        writer = IndexWriter(cachingDir, conf)
        writer.updateNumericDocValue(Term("id", "doc-0"), "val", 100L)
        val reader = DirectoryReader.open(writer) // flush
        assertEquals(0, cachingDir.listCachedFiles().size)

        IOUtils.close(reader, writer, cachingDir)
    }
}
