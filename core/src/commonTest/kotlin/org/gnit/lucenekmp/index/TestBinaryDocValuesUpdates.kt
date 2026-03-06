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
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.SortedDocValuesField
import org.gnit.lucenekmp.document.SortedSetDocValuesField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.jdkport.CountDownLatch
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.DocIdSetIterator.Companion.NO_MORE_DOCS
import org.gnit.lucenekmp.search.Sort
import org.gnit.lucenekmp.search.SortField
import org.gnit.lucenekmp.store.NRTCachingDirectory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.codecs.asserting.AssertingCodec
import org.gnit.lucenekmp.tests.codecs.asserting.AssertingDocValuesFormat
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.RandomPicks
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
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

class TestBinaryDocValuesUpdates : LuceneTestCase() {

    companion object {
        @Throws(IOException::class)
        fun getValue(bdv: BinaryDocValues): Long {
            val term = bdv.binaryValue()!!
            var idx = term.offset
            assert(term.length > 0)
            var b = term.bytes[idx++]
            var value = b.toLong() and 0x7FL
            var shift = 7
            while ((b.toLong() and 0x80L) != 0L) {
                b = term.bytes[idx++]
                value = value or ((b.toLong() and 0x7FL) shl shift)
                shift += 7
            }
            return value
        }

        // encodes a long into a BytesRef as VLong so that we get varying number of bytes when we update
        fun toBytes(valueIn: Long): BytesRef {
            var value = valueIn
            val bytes = newBytesRef(10) // negative longs may take 10 bytes
            var upto = 0
            while ((value and 0x7FL.inv()) != 0L) {
                bytes.bytes[bytes.offset + upto++] = ((value and 0x7FL) or 0x80L).toByte()
                value = value ushr 7
            }
            bytes.bytes[bytes.offset + upto++] = value.toByte()
            bytes.length = upto
            return bytes
        }
    }

    private fun doc(id: Int): Document {
        val doc = Document()
        doc.add(StringField("id", "doc-$id", Store.NO))
        doc.add(BinaryDocValuesField("val", toBytes((id + 1).toLong())))
        return doc
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
        writer.updateBinaryDocValue(Term("id", "doc-0"), "val", toBytes(5))
        assertEquals(2, writer.getFlushDeletesCount())
        writer.updateBinaryDocValue(Term("id", "doc-1"), "val", toBytes(6))
        assertEquals(3, writer.getFlushDeletesCount())
        writer.updateBinaryDocValue(Term("id", "doc-2"), "val", toBytes(7))
        assertEquals(4, writer.getFlushDeletesCount())
        writer.config.setRAMBufferSizeMB(1000.0)
        writer.updateBinaryDocValue(Term("id", "doc-2"), "val", toBytes(7))
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
        writer.updateBinaryDocValue(Term("id", "doc-0"), "val", toBytes(2)) // doc=0, exp=2

        val reader: DirectoryReader =
            if (random().nextBoolean()) {
                writer.close()
                DirectoryReader.open(dir)
            } else {
                DirectoryReader.open(writer).also { writer.close() }
            }

        assertEquals(1, reader.leaves().size)
        val r = reader.leaves()[0].reader()
        val bdv = r.getBinaryDocValues("val")!!
        assertEquals(0, bdv.nextDoc())
        assertEquals(2, getValue(bdv))
        assertEquals(1, bdv.nextDoc())
        assertEquals(2, getValue(bdv))
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
                writer.updateBinaryDocValue(Term("id", "doc-$i"), "val", toBytes(value))
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
            val bdv = r.getBinaryDocValues("val")
            assertNotNull(bdv)
            for (i in 0 until r.maxDoc()) {
                assertEquals(i, bdv.nextDoc())
                val expected = expectedValues[i + context.docBase]
                val actual = getValue(bdv)
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
        println("TEST: isNRT=$isNRT reader1=$reader1")

        // update doc
        writer.updateBinaryDocValue(Term("id", "doc-0"), "val", toBytes(10)) // update doc-0's value to 10
        if (!isNRT) {
            writer.commit()
        }

        println("TEST: now reopen")

        // reopen reader and assert only it sees the update
        val reader2 =
            if (isNRT) {
                DirectoryReader.openIfChanged(reader1, writer)
            } else {
                DirectoryReader.open(dir)
            }
        assertNotNull(reader2)
        assertTrue(reader1 !== reader2)

        val bdv1 = reader1.leaves()[0].reader().getBinaryDocValues("val")!!
        val bdv2 = reader2.leaves()[0].reader().getBinaryDocValues("val")!!
        assertEquals(0, bdv1.nextDoc())
        assertEquals(1, getValue(bdv1))
        assertEquals(0, bdv2.nextDoc())
        assertEquals(10, getValue(bdv2))

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
        writer.updateBinaryDocValue(Term("id", "doc-3"), "val", toBytes(17L))
        writer.updateBinaryDocValue(Term("id", "doc-5"), "val", toBytes(17L))

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
        val bdv = MultiDocValues.getBinaryValues(reader, "val")!!
        for (i in expectedValues.indices) {
            assertEquals(i, bdv.nextDoc())
            assertEquals(expectedValues[i], getValue(bdv))
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
                .setMergePolicy(NoMergePolicy.INSTANCE) // otherwise the delete might force a merge
        conf.setMaxBufferedDocs(10) // control segment flushing
        val writer = IndexWriter(dir, conf)

        writer.addDocument(doc(0))
        writer.addDocument(doc(1))

        if (random().nextBoolean()) {
            writer.commit()
        }

        writer.deleteDocuments(Term("id", "doc-0"))
        writer.updateBinaryDocValue(Term("id", "doc-1"), "val", toBytes(17L))

        val reader: DirectoryReader =
            if (random().nextBoolean()) {
                writer.close()
                DirectoryReader.open(dir)
            } else {
                DirectoryReader.open(writer).also { writer.close() }
            }

        val r = reader.leaves()[0].reader()
        assertFalse(r.liveDocs!!.get(0))
        val bdv = r.getBinaryDocValues("val")!!
        assertEquals(1, bdv.advance(1))
        assertEquals(17, getValue(bdv))

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

        // update all docs' bdv field
        writer.updateBinaryDocValue(Term("dvUpdateKey", "dv"), "bdv", toBytes(17L))
        writer.close()

        val reader = DirectoryReader.open(dir)
        val r = reader.leaves()[0].reader()
        val ndv = r.getNumericDocValues("ndv")!!
        val bdv = r.getBinaryDocValues("bdv")!!
        val sdv = r.getSortedDocValues("sdv")!!
        val ssdv = r.getSortedSetDocValues("ssdv")!!
        for (i in 0 until r.maxDoc()) {
            assertEquals(i, ndv.nextDoc())
            assertEquals(i.toLong(), ndv.longValue())
            assertEquals(i, bdv.nextDoc())
            assertEquals(17L, getValue(bdv))
            assertEquals(i, sdv.nextDoc())
            var term = sdv.lookupOrd(sdv.ordValue())
            assertEquals(newBytesRef(i.toString()), term)
            assertEquals(i, ssdv.nextDoc())
            var ord = ssdv.nextOrd()
            term = ssdv.lookupOrd(ord)
            assertEquals(i, term!!.utf8ToString().toInt())
            // For the i=0 case, we added the same value twice, which was dedup'd by IndexWriter so it has
            // only one value:
            if (i == 0) {
                assertEquals(1, ssdv.docValueCount())
            } else {
                assertEquals(2, ssdv.docValueCount())
                ord = ssdv.nextOrd()
                term = ssdv.lookupOrd(ord)
                assertEquals(i * 2, term!!.utf8ToString().toInt())
            }
        }

        reader.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testMultipleBinaryDocValues() {
        val dir = newDirectory()
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        conf.setMaxBufferedDocs(10) // prevent merges
        val writer = IndexWriter(dir, conf)

        for (i in 0 until 2) {
            val doc = Document()
            doc.add(StringField("dvUpdateKey", "dv", Store.NO))
            doc.add(BinaryDocValuesField("bdv1", toBytes(i.toLong())))
            doc.add(BinaryDocValuesField("bdv2", toBytes(i.toLong())))
            writer.addDocument(doc)
        }
        writer.commit()

        // update all docs' bdv1 field
        writer.updateBinaryDocValue(Term("dvUpdateKey", "dv"), "bdv1", toBytes(17L))
        writer.close()

        val reader = DirectoryReader.open(dir)
        val r = reader.leaves()[0].reader()

        val bdv1 = r.getBinaryDocValues("bdv1")!!
        val bdv2 = r.getBinaryDocValues("bdv2")!!
        for (i in 0 until r.maxDoc()) {
            assertEquals(i, bdv1.nextDoc())
            assertEquals(17L, getValue(bdv1))
            assertEquals(i, bdv2.nextDoc())
            assertEquals(i.toLong(), getValue(bdv2))
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
                doc.add(BinaryDocValuesField("bdv", toBytes(5L)))
            }
            writer.addDocument(doc)
        }
        writer.commit()

        // update all docs' bdv field
        writer.updateBinaryDocValue(Term("dvUpdateKey", "dv"), "bdv", toBytes(17L))
        writer.close()

        val reader = DirectoryReader.open(dir)
        val r = reader.leaves()[0].reader()
        val bdv = r.getBinaryDocValues("bdv")!!
        for (i in 0 until r.maxDoc()) {
            assertEquals(i, bdv.nextDoc())
            assertEquals(17L, getValue(bdv))
        }

        reader.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testUpdateNonBinaryDocValuesField() {
        // we don't support adding new fields or updating existing non-binary-dv
        // fields through binary updates
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
            writer.updateBinaryDocValue(Term("key", "doc"), "bdv", toBytes(17L))
        }

        assertFailsWith<IllegalArgumentException> {
            writer.updateBinaryDocValue(Term("key", "doc"), "foo", toBytes(17L))
        }

        writer.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testDifferentDVFormatPerField() {
        // test relies on separate instances of "same thing"
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
        doc.add(BinaryDocValuesField("bdv", toBytes(5L)))
        doc.add(SortedDocValuesField("sorted", newBytesRef("value")))
        writer.addDocument(doc) // flushed document
        writer.commit()
        writer.addDocument(doc) // in-memory document

        writer.updateBinaryDocValue(Term("key", "doc"), "bdv", toBytes(17L))
        writer.close()

        val reader = DirectoryReader.open(dir)

        val bdv = MultiDocValues.getBinaryValues(reader, "bdv")!!
        val sdv = MultiDocValues.getSortedValues(reader, "sorted")!!
        for (i in 0 until reader.maxDoc()) {
            assertEquals(i, bdv.nextDoc())
            assertEquals(17L, getValue(bdv))
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
        doc.add(BinaryDocValuesField("bdv", toBytes(5L)))
        writer.addDocument(doc) // flushed document
        writer.commit()
        writer.addDocument(doc) // in-memory document

        writer.updateBinaryDocValue(Term("key", "doc"), "bdv", toBytes(17L)) // update existing field
        writer.updateBinaryDocValue(Term("key", "doc"), "bdv", toBytes(3L)) // update existing field 2nd time in this commit
        writer.close()

        val reader = DirectoryReader.open(dir)
        val bdv = MultiDocValues.getBinaryValues(reader, "bdv")!!
        for (i in 0 until reader.maxDoc()) {
            assertEquals(i, bdv.nextDoc())
            assertEquals(3L, getValue(bdv))
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
            doc.add(BinaryDocValuesField("bdv", toBytes(-1L)))
            val numDocs = atLeast(30)
            for (i in 0 until numDocs) {
                doc.removeField("id")
                doc.add(StringField("id", docid.toString(), Store.NO))
                docid++
                writer.addDocument(doc)
            }

            val value = (rnd + 1).toLong()
            writer.updateBinaryDocValue(Term("key", "doc"), "bdv", toBytes(value))

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
            docid++
            doc.add(StringField("key", "doc", Store.NO))
            doc.add(BinaryDocValuesField("bdv", toBytes(value)))
            writer.addDocument(doc)

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
            val bdv = r.getBinaryDocValues("bdv")
            assertNotNull(bdv)
            for (i in 0 until r.maxDoc()) {
                assertEquals(i, bdv.nextDoc())
                assertEquals(value, getValue(bdv))
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
        doc.add(BinaryDocValuesField("bdv", toBytes(5L)))
        writer.addDocument(doc) // flushed document
        writer.commit()
        writer.addDocument(doc) // in-memory document

        writer.updateBinaryDocValue(Term("k1", "v1"), "bdv", toBytes(17L))
        writer.updateBinaryDocValue(Term("k2", "v2"), "bdv", toBytes(3L))
        writer.close()

        val reader = DirectoryReader.open(dir)
        val bdv = MultiDocValues.getBinaryValues(reader, "bdv")!!
        for (i in 0 until reader.maxDoc()) {
            assertEquals(i, bdv.nextDoc())
            assertEquals(3L, getValue(bdv))
        }
        reader.close()
        dir.close()
    }

    class OneSortDoc(
        val id: Int,
        var value: BytesRef,
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

        val numIters = if (TEST_NIGHTLY) atLeast(1000) else atLeast(100)
        for (iter in 0 until numIters) {
            val value = toBytes(random().nextInt(valueRange).toLong())
            if (docs.isEmpty() || random().nextInt(3) == 1) {
                val id = docs.size
                val doc = Document()
                doc.add(newStringField("id", id.toString(), Field.Store.YES))
                doc.add(BinaryDocValuesField("number", value))
                val sortValue = random().nextInt(sortValueRange)
                doc.add(NumericDocValuesField("sort", sortValue.toLong()))
                w.addDocument(doc)
                docs.add(OneSortDoc(id, value, sortValue.toLong()))
            } else {
                val idToUpdate = random().nextInt(docs.size)
                w.updateBinaryDocValue(Term("id", idToUpdate.toString()), "number", value)
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
                    val values = leafReader.getBinaryDocValues("number")!!
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

                        assertEquals(sortDoc.value, values.binaryValue())

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
                doc.add(StringField("id", "doc-$docID", Store.NO))
                doc.add(StringField("key", "all", Store.NO))
                for (f in fieldValues.indices) {
                    doc.add(BinaryDocValuesField("f$f", toBytes(fieldValues[f])))
                }
                writer.addDocument(doc)
                ++docID
            }

            val fieldIdx = random.nextInt(fieldValues.size)
            val updateField = "f$fieldIdx"
            writer.updateBinaryDocValue(Term("key", "all"), updateField, toBytes(++fieldValues[fieldIdx]))

            if (random.nextDouble() < 0.2) {
                val deleteDoc = random.nextInt(docID)
                writer.deleteDocuments(Term("id", "doc-$deleteDoc"))
            }

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
            assertTrue(reader.numDocs() > 0)
            for (context in reader.leaves()) {
                val r = context.reader()
                val liveDocs = r.liveDocs
                for (field in fieldValues.indices) {
                    val f = "f$field"
                    val bdv = r.getBinaryDocValues(f)
                    assertNotNull(bdv)
                    val maxDoc = r.maxDoc()
                    for (doc in 0 until maxDoc) {
                        if (liveDocs == null || liveDocs.get(doc)) {
                            assertEquals(doc, bdv.advance(doc))
                            assertEquals(
                                fieldValues[field],
                                getValue(bdv),
                                "invalid value for doc=$doc, field=$f, reader=$r"
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

        // first segment with BDV
        var doc = Document()
        doc.add(StringField("id", "doc0", Store.NO))
        doc.add(BinaryDocValuesField("bdv", toBytes(3L)))
        writer.addDocument(doc)
        doc = Document()
        doc.add(StringField("id", "doc4", Store.NO)) // document without 'bdv' field
        writer.addDocument(doc)
        writer.commit()

        // second segment with no BDV
        doc = Document()
        doc.add(StringField("id", "doc1", Store.NO))
        writer.addDocument(doc)
        doc = Document()
        doc.add(StringField("id", "doc2", Store.NO)) // document that isn't updated
        writer.addDocument(doc)
        writer.commit()

        // update document in the first segment - should not affect docsWithField of
        // the document without BDV field
        writer.updateBinaryDocValue(Term("id", "doc0"), "bdv", toBytes(5L))

        // update document in the second segment - field should be added and we should
        // be able to handle the other document correctly (e.g. no NPE)
        writer.updateBinaryDocValue(Term("id", "doc1"), "bdv", toBytes(5L))
        writer.close()

        val reader = DirectoryReader.open(dir)
        for (context in reader.leaves()) {
            val r = context.reader()
            val bdv = r.getBinaryDocValues("bdv")!!
            assertEquals(0, bdv.nextDoc())
            assertEquals(5L, getValue(bdv))
            assertEquals(NO_MORE_DOCS, bdv.nextDoc())
        }
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

        // first segment with BDV
        val doc = Document()
        doc.add(StringField("id", "doc0", Store.NO))
        doc.add(StringField("bdv", "mock-value", Store.NO))
        doc.add(BinaryDocValuesField("bdv", toBytes(5L)))
        writer.addDocument(doc)
        writer.commit()

        // second segment with no BDV
        val doc2 = Document()
        doc2.add(StringField("id", "doc1", Store.NO))
        doc2.add(StringField("bdv", "mock-value", Store.NO))
        var exception = assertFailsWith<IllegalArgumentException> { writer.addDocument(doc2) }
        var expectedErrMsg =
            "cannot change field \"bdv\" from doc values type=BINARY to inconsistent doc values type=NONE"
        assertEquals(expectedErrMsg, exception.message)

        doc2.add(BinaryDocValuesField("bdv", toBytes(10L)))
        writer.addDocument(doc2)

        // update doc values of bdv field in the second segment
        exception = assertFailsWith<IllegalArgumentException> {
            writer.updateBinaryDocValue(Term("id", "doc1"), "bdv", toBytes(5L))
        }
        expectedErrMsg =
            "Can't update [BINARY] doc values; the field [bdv] must be doc values only field, but is also indexed with postings."
        assertEquals(expectedErrMsg, exception.message)

        writer.commit()
        writer.close()

        val reader = DirectoryReader.open(dir)
        val r1 = reader.leaves()[0].reader()
        val bdv1 = r1.getBinaryDocValues("bdv")!!
        assertEquals(0, bdv1.nextDoc())
        assertEquals(5L, getValue(bdv1))
        val r2 = reader.leaves()[1].reader()
        val bdv2 = r2.getBinaryDocValues("bdv")!!
        assertEquals(1, bdv2.nextDoc())
        assertEquals(10L, getValue(bdv2))

        reader.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testUpdateBinaryDVFieldWithSameNameAsPostingField() {
        // this used to fail because FieldInfos.Builder neglected to update
        // globalFieldMaps.docValuesTypes map
        val dir = newDirectory()
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        val writer = IndexWriter(dir, conf)

        val doc = Document()
        doc.add(StringField("f", "mock-value", Store.NO))
        doc.add(BinaryDocValuesField("f", toBytes(5L)))
        writer.addDocument(doc)
        writer.commit()

        val exception = assertFailsWith<IllegalArgumentException> {
            writer.updateBinaryDocValue(Term("f", "mock-value"), "f", toBytes(17L))
        }
        val expectedErrMsg =
            "Can't update [BINARY] doc values; the field [f] must be doc values only field, but is also indexed with postings."
        assertEquals(expectedErrMsg, exception.message)
        writer.close()

        val r = DirectoryReader.open(dir)
        val bdv = r.leaves()[0].reader().getBinaryDocValues("f")!!
        assertEquals(0, bdv.nextDoc())
        assertEquals(5L, getValue(bdv))
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
                doc.add(BinaryDocValuesField("f$j", toBytes(value)))
                doc.add(BinaryDocValuesField("cf$j", toBytes(value * 2)))
            }
            writer.addDocument(doc)
        }

        val numThreads = /*if (TEST_NIGHTLY) TestUtil.nextInt(random(), 3, 6) else*/ 2

        val done = CountDownLatch(numThreads)
        val numUpdates = AtomicInt(atLeast(100))

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
                                BinaryDocValuesField(f, toBytes(updValue)),
                                BinaryDocValuesField(cf, toBytes(updValue * 2))
                            )

                            if (random.nextDouble() < 0.2) {
                                val doc = random.nextInt(numDocs)
                                writer.deleteDocuments(Term("id", "doc$doc"))
                            }

                            if (random.nextDouble() < 0.05) {
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
                val bdv = r.getBinaryDocValues("f$i")!!
                val control = r.getBinaryDocValues("cf$i")!!
                val liveDocs = r.liveDocs
                for (j in 0 until r.maxDoc()) {
                    if (liveDocs == null || liveDocs.get(j)) {
                        assertEquals(j, bdv.advance(j))
                        assertEquals(j, control.advance(j))
                        assertEquals(getValue(control), getValue(bdv) * 2)
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
            doc.add(BinaryDocValuesField("f", toBytes(value)))
            doc.add(BinaryDocValuesField("cf", toBytes(value * 2)))
            writer.addDocument(doc)
        }

        val numGens = atLeast(5)
        for (i in 0 until numGens) {
            val doc = random().nextInt(numDocs)
            val t = Term("id", "doc$doc")
            val value = random().nextLong()
            writer.updateDocValues(
                t,
                BinaryDocValuesField("f", toBytes(value)),
                BinaryDocValuesField("cf", toBytes(value * 2))
            )
            val reader = DirectoryReader.open(writer)
            for (context in reader.leaves()) {
                val r = context.reader()
                val fbdv = r.getBinaryDocValues("f")!!
                val cfbdv = r.getBinaryDocValues("cf")!!
                for (j in 0 until r.maxDoc()) {
                    assertEquals(j, fbdv.nextDoc())
                    assertEquals(j, cfbdv.nextDoc())
                    assertEquals(getValue(cfbdv), getValue(fbdv) * 2)
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
        conf.setMergePolicy(NoMergePolicy.INSTANCE)
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
        doc.add(BinaryDocValuesField("f1", toBytes(5L)))
        doc.add(BinaryDocValuesField("f2", toBytes(13L)))
        writer.addDocument(doc)
        writer.close()

        conf = newIndexWriterConfig(MockAnalyzer(random()))
        conf.setMergePolicy(NoMergePolicy.INSTANCE)
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
        doc.add(BinaryDocValuesField("f1", toBytes(17L)))
        doc.add(BinaryDocValuesField("f2", toBytes(2L)))
        writer.addDocument(doc)
        writer.updateBinaryDocValue(Term("id", "d0"), "f1", toBytes(12L))
        writer.close()

        val reader = DirectoryReader.open(dir)
        val f1 = MultiDocValues.getBinaryValues(reader, "f1")!!
        val f2 = MultiDocValues.getBinaryValues(reader, "f2")!!
        assertEquals(0, f1.nextDoc())
        assertEquals(0, f2.nextDoc())
        assertEquals(12L, getValue(f1))
        assertEquals(13L, getValue(f2))
        assertEquals(1, f1.nextDoc())
        assertEquals(1, f2.nextDoc())
        assertEquals(17L, getValue(f1))
        assertEquals(2L, getValue(f2))
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

        for (i in 0 until numDocs) {
            val doc = Document()
            doc.add(StringField("id", RandomPicks.randomFrom(random(), randomTerms), Store.NO))
            doc.add(BinaryDocValuesField("bdv", toBytes(4L)))
            doc.add(BinaryDocValuesField("control", toBytes(8L)))
            writer.addDocument(doc)
        }

        if (random().nextBoolean()) {
            writer.commit()
        }

        val value = random().nextInt().toLong()
        val term = Term("id", RandomPicks.randomFrom(random(), randomTerms))
        writer.updateDocValues(
            term,
            BinaryDocValuesField("bdv", toBytes(value)),
            BinaryDocValuesField("control", toBytes(value * 2))
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
            val bdv = r.getBinaryDocValues("bdv")!!
            val control = r.getBinaryDocValues("control")!!
            for (i in 0 until r.maxDoc()) {
                assertEquals(i, bdv.nextDoc())
                assertEquals(i, control.nextDoc())
                assertEquals(getValue(bdv) * 2, getValue(control))
            }
        }
        reader.close()

        IOUtils.close(dir1, dir2)
    }

    @Test
    @Throws(Exception::class)
    fun testDeleteUnusedUpdatesFiles() {
        val dir = newDirectory()
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        val writer = IndexWriter(dir, conf)

        val doc = Document()
        doc.add(StringField("id", "d0", Store.NO))
        doc.add(BinaryDocValuesField("f1", toBytes(1L)))
        doc.add(BinaryDocValuesField("f2", toBytes(1L)))
        writer.addDocument(doc)

        for (f in arrayOf("f1", "f2")) {
            writer.updateBinaryDocValue(Term("id", "d0"), f, toBytes(2L))
            writer.commit()
            val numFiles = dir.listAll().size

            writer.updateBinaryDocValue(Term("id", "d0"), f, toBytes(3L))
            writer.commit()

            assertEquals(numFiles, dir.listAll().size)
        }

        writer.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testTonsOfUpdates() {
        val dir = newDirectory()
        val random = random()
        val conf = newIndexWriterConfig(MockAnalyzer(random))
        conf.setRAMBufferSizeMB(IndexWriterConfig.DEFAULT_RAM_BUFFER_SIZE_MB)
        conf.setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH)
        val writer = IndexWriter(dir, conf)

        val numDocs = atLeast(20000)
        val numBinaryFields = atLeast(5)
        val numTerms = TestUtil.nextInt(random, 10, 100)
        val updateTerms = linkedSetOf<String>()
        while (updateTerms.size < numTerms) {
            updateTerms.add(TestUtil.randomSimpleString(random))
        }

        for (i in 0 until numDocs) {
            val doc = Document()
            val numUpdateTerms = TestUtil.nextInt(random, 1, numTerms / 10)
            for (j in 0 until numUpdateTerms) {
                doc.add(StringField("upd", RandomPicks.randomFrom(random, updateTerms), Store.NO))
            }
            for (j in 0 until numBinaryFields) {
                val value = random.nextInt().toLong()
                doc.add(BinaryDocValuesField("f$j", toBytes(value)))
                doc.add(BinaryDocValuesField("cf$j", toBytes(value * 2)))
            }
            writer.addDocument(doc)
        }

        writer.commit()

        writer.config.setRAMBufferSizeMB(2048.0 / 1024 / 1024)
        val numUpdates = atLeast(100)
        for (i in 0 until numUpdates) {
            val field = random.nextInt(numBinaryFields)
            val updateTerm = Term("upd", RandomPicks.randomFrom(random, updateTerms))
            val value = random.nextInt().toLong()
            writer.updateDocValues(
                updateTerm,
                BinaryDocValuesField("f$field", toBytes(value)),
                BinaryDocValuesField("cf$field", toBytes(value * 2))
            )
        }

        writer.close()

        val reader = DirectoryReader.open(dir)
        for (context in reader.leaves()) {
            for (i in 0 until numBinaryFields) {
                val r = context.reader()
                val f = r.getBinaryDocValues("f$i")!!
                val cf = r.getBinaryDocValues("cf$i")!!
                for (j in 0 until r.maxDoc()) {
                    assertEquals(j, f.nextDoc())
                    assertEquals(j, cf.nextDoc())
                    assertEquals(
                        getValue(cf),
                        getValue(f) * 2,
                        "reader=$r, field=f$i, doc=$j"
                    )
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
        doc.add(BinaryDocValuesField("f1", toBytes(1L)))
        doc.add(BinaryDocValuesField("f2", toBytes(1L)))
        writer.addDocument(doc)
        writer.updateBinaryDocValue(Term("upd", "t1"), "f1", toBytes(2L))
        writer.updateBinaryDocValue(Term("upd", "t1"), "f2", toBytes(2L))
        writer.updateBinaryDocValue(Term("upd", "t2"), "f1", toBytes(3L))
        writer.updateBinaryDocValue(Term("upd", "t2"), "f2", toBytes(3L))
        writer.updateBinaryDocValue(Term("upd", "t1"), "f1", toBytes(4L))
        writer.close()

        val reader = DirectoryReader.open(dir)
        var bdv = reader.leaves()[0].reader().getBinaryDocValues("f1")!!
        assertEquals(0, bdv.nextDoc())
        assertEquals(4L, getValue(bdv))
        bdv = reader.leaves()[0].reader().getBinaryDocValues("f2")!!
        assertEquals(0, bdv.nextDoc())
        assertEquals(3L, getValue(bdv))
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
        doc.add(BinaryDocValuesField("f1", toBytes(1L)))
        writer.addDocument(doc)
        writer.addDocument(doc)
        writer.commit()
        writer.deleteDocuments(Term("id", "doc"))
        writer.addDocument(doc)
        writer.updateBinaryDocValue(Term("id", "doc"), "f1", toBytes(2L))
        writer.close()

        val reader = DirectoryReader.open(dir)
        assertEquals(1, reader.leaves().size)
        val bdv = reader.leaves()[0].reader().getBinaryDocValues("f1")!!
        assertEquals(0, bdv.nextDoc())
        assertEquals(2L, getValue(bdv))
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
        doc.add(BinaryDocValuesField("f1", toBytes(1L)))
        writer.addDocument(doc)
        writer.updateBinaryDocValue(Term("c", "foo"), "f1", toBytes(2L))
        writer.updateBinaryDocValue(Term("c", "bar"), "f1", toBytes(2L))
        writer.close()

        val reader = DirectoryReader.open(dir)
        assertEquals(1, reader.leaves().size)
        val bdv = reader.leaves()[0].reader().getBinaryDocValues("f1")!!
        assertEquals(0, bdv.nextDoc())
        assertEquals(1L, getValue(bdv))
        reader.close()

        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testIOContext() {
        val dir = newDirectory()
        var conf = newIndexWriterConfig(MockAnalyzer(random()))
        conf.setMergePolicy(NoMergePolicy.INSTANCE)
        conf.setMaxBufferedDocs(Int.MAX_VALUE)
        conf.setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH.toDouble())
        var writer = IndexWriter(dir, conf)
        for (i in 0 until 100) {
            writer.addDocument(doc(i))
        }
        writer.commit()
        writer.close()

        val cachingDir = NRTCachingDirectory(dir, 100.0, 1 / (1024.0 * 1024.0))
        conf = newIndexWriterConfig(MockAnalyzer(random()))
        conf.setMergePolicy(NoMergePolicy.INSTANCE)
        conf.setMaxBufferedDocs(Int.MAX_VALUE)
        conf.setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH.toDouble())
        writer = IndexWriter(cachingDir, conf)
        writer.updateBinaryDocValue(Term("id", "doc-0"), "val", toBytes(100L))
        val reader = DirectoryReader.open(writer)
        assertEquals(0, cachingDir.listCachedFiles().size)

        IOUtils.close(reader, writer, cachingDir)
    }
}
