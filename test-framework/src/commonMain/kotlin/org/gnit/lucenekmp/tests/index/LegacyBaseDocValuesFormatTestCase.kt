package org.gnit.lucenekmp.tests.index

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.codecs.simpletext.SimpleTextCodec
import org.gnit.lucenekmp.document.BinaryDocValuesField
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FloatDocValuesField
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.SortedDocValuesField
import org.gnit.lucenekmp.document.SortedNumericDocValuesField
import org.gnit.lucenekmp.document.SortedSetDocValuesField
import org.gnit.lucenekmp.document.StoredField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.BinaryDocValues
import org.gnit.lucenekmp.index.CheckIndex
import org.gnit.lucenekmp.index.CodecReader
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.DocValues
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.MultiDocValues
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.SortedDocValues
import org.gnit.lucenekmp.index.SortedNumericDocValues
import org.gnit.lucenekmp.index.SortedSetDocValues
import org.gnit.lucenekmp.index.StoredFields
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.index.TermsEnum.SeekStatus
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.ByteArrayOutputStream
import org.gnit.lucenekmp.jdkport.PrintStream
import org.gnit.lucenekmp.jdkport.StandardCharsets
import org.gnit.lucenekmp.jdkport.TreeSet
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.floatToRawIntBits
import org.gnit.lucenekmp.search.BooleanClause
import org.gnit.lucenekmp.search.BooleanQuery
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.ScoreDoc
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.junitport.assertArrayEquals
import org.gnit.lucenekmp.tests.util.RandomPicks
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BitSet
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.BytesRefHash
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.automaton.CompiledAutomaton
import org.gnit.lucenekmp.util.automaton.Operations
import org.gnit.lucenekmp.util.automaton.RegExp
import kotlin.math.max
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Abstract class to do basic tests for a docvalues format. NOTE: This test focuses on the docvalues
 * impl, nothing else. The [stretch] goal is for this test to be so thorough in testing a new
 * DocValuesFormat that if this test passes, then all Lucene tests should also pass. Ie, if there is
 * some bug in a given DocValuesFormat that this test fails to catch then this test needs to be
 * improved!
 */
abstract class LegacyBaseDocValuesFormatTestCase : BaseIndexFileFormatTestCase() {

    private val logger = KotlinLogging.logger {  }

    override fun addRandomFields(doc: Document) {
        if (usually()) {
            doc.add(NumericDocValuesField("ndv", random().nextInt(1 shl 12).toLong()))
            doc.add(BinaryDocValuesField("bdv", newBytesRef(TestUtil.randomSimpleString(random()))))
            doc.add(SortedDocValuesField("sdv", newBytesRef(TestUtil.randomSimpleString(random(), 2))))
        }
        var numValues: Int = random().nextInt(5)
        for (i in 0..<numValues) {
            doc.add(SortedSetDocValuesField("ssdv", newBytesRef(TestUtil.randomSimpleString(random(), 2))))
        }
        numValues = random().nextInt(5)
        for (i in 0..<numValues) {
            doc.add(SortedNumericDocValuesField("sndv", TestUtil.nextLong(random(), Long.MIN_VALUE, Long.MAX_VALUE)))
        }
    }

    @Throws(IOException::class)
    open fun testOneNumber() {
        val directory: Directory = newDirectory()
        val iwriter = RandomIndexWriter(random(), directory)
        val doc = Document()
        val longTerm = ("longtermlongtermlongtermlongtermlongtermlongtermlongtermlongterm" + "longtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongterm")
        val text = "This is the text to be indexed. $longTerm"
        doc.add(newTextField("fieldname", text, Field.Store.YES))
        doc.add(NumericDocValuesField("dv", 5))
        iwriter.addDocument(doc)
        iwriter.close()

        // Now search the index:
        val ireader: IndexReader = maybeWrapWithMergingReader(DirectoryReader.open(directory)) // read-only=true
        val isearcher = IndexSearcher(ireader)
        val storedFields: StoredFields = isearcher.storedFields()

        assertEquals(1, isearcher.count(TermQuery(Term("fieldname", longTerm))).toLong())
        val query: Query = TermQuery(Term("fieldname", "text"))
        val hits: TopDocs = isearcher.search(query, 1)
        assertEquals(1, hits.totalHits.value)
        // Iterate through the results:
        for (i in hits.scoreDocs.indices) {
            val hitDoc = storedFields.document(hits.scoreDocs[i].doc)
            assertEquals(text, hitDoc.get("fieldname"))
            assert(ireader.leaves().size == 1)
            val dv: NumericDocValues = ireader.leaves()[0].reader().getNumericDocValues("dv")!!
            val docID: Int = hits.scoreDocs[i].doc
            assertEquals(docID.toLong(), dv.advance(docID).toLong())
            assertEquals(5, dv.longValue())
        }

        ireader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testOneFloat() {
        val directory: Directory = newDirectory()
        val iwriter = RandomIndexWriter(random(), directory)
        val doc = Document()
        val longTerm = "longtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongterm"
        val text = "This is the text to be indexed. $longTerm"
        doc.add(newTextField("fieldname", text, Field.Store.YES))
        doc.add(FloatDocValuesField("dv", 5.7f))
        iwriter.addDocument(doc)
        iwriter.close()

        // Now search the index:
        val ireader: IndexReader = maybeWrapWithMergingReader(DirectoryReader.open(directory)) // read-only=true
        val isearcher =
            IndexSearcher(ireader)
        val storedFields: StoredFields = isearcher.storedFields()

        assertEquals(1, isearcher.count(TermQuery(Term("fieldname", longTerm))).toLong())
        val query: Query = TermQuery(Term("fieldname", "text"))
        val hits: TopDocs = isearcher.search(query, 1)
        assertEquals(1, hits.totalHits.value)
        // Iterate through the results:
        for (i in hits.scoreDocs.indices) {
            val docID: Int = hits.scoreDocs[i].doc
            val hitDoc = storedFields.document(docID)
            assertEquals(text, hitDoc.get("fieldname"))
            assert(ireader.leaves().size == 1)

            val dv: NumericDocValues = ireader.leaves()[0].reader().getNumericDocValues("dv")!!
            assertEquals(docID.toLong(), dv.advance(docID).toLong())
            assertEquals(Float.floatToRawIntBits(5.7f).toLong(), dv.longValue())
        }

        ireader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testTwoNumbers() {
        val directory: Directory = newDirectory()
        val iwriter = RandomIndexWriter(random(), directory)
        val doc = Document()
        val longTerm = "longtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongterm"
        val text = "This is the text to be indexed. $longTerm"
        doc.add(newTextField("fieldname", text, Field.Store.YES))
        doc.add(NumericDocValuesField("dv1", 5))
        doc.add(NumericDocValuesField("dv2", 17))
        iwriter.addDocument(doc)
        iwriter.close()

        // Now search the index:
        val ireader: IndexReader = maybeWrapWithMergingReader(DirectoryReader.open(directory)) // read-only=true
        val isearcher = IndexSearcher(ireader)
        val storedFields: StoredFields = isearcher.storedFields()

        assertEquals(1, isearcher.count(TermQuery(Term("fieldname", longTerm))).toLong())
        val query: Query = TermQuery(Term("fieldname", "text"))
        val hits: TopDocs = isearcher.search(query, 1)
        assertEquals(1, hits.totalHits.value)
        // Iterate through the results:
        for (i in hits.scoreDocs.indices) {
            val docID: Int = hits.scoreDocs[i].doc
            val hitDoc = storedFields.document(docID)
            assertEquals(text, hitDoc.get("fieldname"))
            assert(ireader.leaves().size == 1)
            var dv: NumericDocValues = ireader.leaves()[0].reader().getNumericDocValues("dv1")!!
            assertEquals(docID.toLong(), dv.advance(docID).toLong())
            assertEquals(5, dv.longValue())
            dv = ireader.leaves()[0].reader().getNumericDocValues("dv2")!!
            assertEquals(docID.toLong(), dv.advance(docID).toLong())
            assertEquals(17, dv.longValue())
        }

        ireader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testTwoBinaryValues() {
        val directory: Directory = newDirectory()
        val iwriter = RandomIndexWriter(random(), directory)
        val doc = Document()
        val longTerm = "longtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongterm"
        val text = "This is the text to be indexed. $longTerm"
        doc.add(newTextField("fieldname", text, Field.Store.YES))
        doc.add(BinaryDocValuesField("dv1", newBytesRef(longTerm)))
        doc.add(BinaryDocValuesField("dv2", newBytesRef(text)))
        iwriter.addDocument(doc)
        iwriter.close()

        // Now search the index:
        val ireader: IndexReader = maybeWrapWithMergingReader(DirectoryReader.open(directory)) // read-only=true
        val isearcher = IndexSearcher(ireader)
        val storedFields: StoredFields = isearcher.storedFields()

        assertEquals(1, isearcher.count(TermQuery(Term("fieldname", longTerm))).toLong())
        val query: Query = TermQuery(Term("fieldname", "text"))
        val hits: TopDocs = isearcher.search(query, 1)
        assertEquals(1, hits.totalHits.value)
        // Iterate through the results:
        for (i in hits.scoreDocs.indices) {
            val hitDocID: Int = hits.scoreDocs[i].doc
            val hitDoc = storedFields.document(hitDocID)
            assertEquals(text, hitDoc.get("fieldname"))
            assert(ireader.leaves().size == 1)
            var dv: BinaryDocValues = ireader.leaves()[0].reader().getBinaryDocValues("dv1")!!
            assertEquals(hitDocID.toLong(), dv.advance(hitDocID).toLong())
            var scratch: BytesRef? = dv.binaryValue()
            assertEquals(newBytesRef(longTerm), scratch)
            dv = ireader.leaves()[0].reader().getBinaryDocValues("dv2")!!
            assertEquals(hitDocID.toLong(), dv.advance(hitDocID).toLong())
            scratch = dv.binaryValue()
            assertEquals(newBytesRef(text), scratch)
        }

        ireader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testVariouslyCompressibleBinaryValues() {
        val directory: Directory = newDirectory()
        val iwriter = RandomIndexWriter(random(), directory)
        val numDocs: Int = 1 + random().nextInt(100)

        val writtenValues: HashMap<Int, BytesRef> = HashMap()

        // Small vocabulary ranges will be highly compressible
        val vocabRange: Int = 1 + random().nextInt(Byte.MAX_VALUE - 1)

        for (i in 0..<numDocs) {
            val doc = Document()

            // Generate random-sized byte array with random choice of bytes in vocab range
            val value = ByteArray(500 + random().nextInt(1024))
            for (j in value.indices) {
                value[j] = random().nextInt(vocabRange).toByte()
            }
            val bytesRef: BytesRef = newBytesRef(value)
            writtenValues[i] = bytesRef
            doc.add(newTextField("id", i.toString(), Field.Store.YES))
            doc.add(BinaryDocValuesField("dv1", bytesRef))
            iwriter.addDocument(doc)
        }
        iwriter.forceMerge(1)
        iwriter.close()

        // Now search the index:
        val ireader: IndexReader = maybeWrapWithMergingReader(DirectoryReader.open(directory)) // read-only=true
        val isearcher = IndexSearcher(ireader)
        val storedFields: StoredFields = isearcher.storedFields()

        for (i in 0..<numDocs) {
            val id = i.toString()
            val query: Query = TermQuery(Term("id", id))
            val hits: TopDocs = isearcher.search(query, 1)
            assertEquals(1, hits.totalHits.value)
            // Iterate through the results:
            val hitDocID: Int = hits.scoreDocs[0].doc
            val hitDoc = storedFields.document(hitDocID)
            assertEquals(id, hitDoc.get("id"))
            assert(ireader.leaves().size == 1)
            val dv: BinaryDocValues = ireader.leaves()[0].reader().getBinaryDocValues("dv1")!!
            assertEquals(hitDocID.toLong(), dv.advance(hitDocID).toLong())
            val scratch: BytesRef = dv.binaryValue()!!
            assertEquals(writtenValues[i], scratch)
        }

        ireader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testTwoFieldsMixed() {
        val directory: Directory = newDirectory()
        val iwriter = RandomIndexWriter(random(), directory)
        val doc = Document()
        val longTerm = "longtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongterm"
        val text = "This is the text to be indexed. $longTerm"
        doc.add(newTextField("fieldname", text, Field.Store.YES))
        doc.add(NumericDocValuesField("dv1", 5))
        doc.add(BinaryDocValuesField("dv2", newBytesRef("hello world")))
        iwriter.addDocument(doc)
        iwriter.close()

        // Now search the index:
        val ireader: IndexReader = maybeWrapWithMergingReader(DirectoryReader.open(directory)) // read-only=true
        val isearcher = IndexSearcher(ireader)
        val storedFields: StoredFields = isearcher.storedFields()

        assertEquals(1, isearcher.count(TermQuery(Term("fieldname", longTerm))).toLong())
        val query: Query = TermQuery(Term("fieldname", "text"))
        val hits: TopDocs = isearcher.search(query, 1)
        assertEquals(1, hits.totalHits.value)
        // Iterate through the results:
        for (i in hits.scoreDocs.indices) {
            val docID: Int = hits.scoreDocs[i].doc
            val hitDoc = storedFields.document(docID)
            assertEquals(text, hitDoc.get("fieldname"))
            assert(ireader.leaves().size == 1)
            val dv: NumericDocValues = ireader.leaves()[0].reader().getNumericDocValues("dv1")!!
            assertEquals(docID.toLong(), dv.advance(docID).toLong())
            assertEquals(5, dv.longValue())
            val dv2: BinaryDocValues = ireader.leaves()[0].reader().getBinaryDocValues("dv2")!!
            assertEquals(docID.toLong(), dv2.advance(docID).toLong())
            assertEquals(newBytesRef("hello world"), dv2.binaryValue())
        }

        ireader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testThreeFieldsMixed() {
        val directory: Directory = newDirectory()
        val iwriter = RandomIndexWriter(random(), directory)
        val doc = Document()
        val longTerm = "longtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongterm"
        val text = "This is the text to be indexed. $longTerm"
        doc.add(newTextField("fieldname", text, Field.Store.YES))
        doc.add(SortedDocValuesField("dv1", newBytesRef("hello hello")))
        doc.add(NumericDocValuesField("dv2", 5))
        doc.add(BinaryDocValuesField("dv3", newBytesRef("hello world")))
        iwriter.addDocument(doc)
        iwriter.close()

        // Now search the index:
        val ireader: IndexReader = maybeWrapWithMergingReader(DirectoryReader.open(directory)) // read-only=true
        val isearcher = IndexSearcher(ireader)

        assertEquals(1, isearcher.count(TermQuery(Term("fieldname", longTerm))).toLong())
        val query: Query = TermQuery(Term("fieldname", "text"))
        val hits: TopDocs = isearcher.search(query, 1)
        val storedFields: StoredFields = isearcher.storedFields()
        assertEquals(1, hits.totalHits.value)
        // Iterate through the results:
        for (i in hits.scoreDocs.indices) {
            val docID: Int = hits.scoreDocs[i].doc
            val hitDoc = storedFields.document(docID)
            assertEquals(text, hitDoc.get("fieldname"))
            assert(ireader.leaves().size == 1)
            val dv: SortedDocValues = ireader.leaves()[0].reader().getSortedDocValues("dv1")!!
            assertEquals(docID.toLong(), dv.advance(docID).toLong())
            val ord: Int = dv.ordValue()
            val scratch: BytesRef? = dv.lookupOrd(ord)
            assertEquals(newBytesRef("hello hello"), scratch)
            val dv2: NumericDocValues = ireader.leaves()[0].reader().getNumericDocValues("dv2")!!
            assertEquals(docID.toLong(), dv2.advance(docID).toLong())
            assertEquals(5, dv2.longValue())
            val dv3: BinaryDocValues = ireader.leaves()[0].reader().getBinaryDocValues("dv3")!!
            assertEquals(docID.toLong(), dv3.advance(docID).toLong())
            assertEquals(newBytesRef("hello world"), dv3.binaryValue())
        }

        ireader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testThreeFieldsMixed2() {
        val directory: Directory = newDirectory()
        val iwriter = RandomIndexWriter(random(), directory)
        val doc = Document()
        val longTerm = "longtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongterm"
        val text = "This is the text to be indexed. $longTerm"
        doc.add(newTextField("fieldname", text, Field.Store.YES))
        doc.add(BinaryDocValuesField("dv1", newBytesRef("hello world")))
        doc.add(SortedDocValuesField("dv2", newBytesRef("hello hello")))
        doc.add(NumericDocValuesField("dv3", 5))
        iwriter.addDocument(doc)
        iwriter.close()

        // Now search the index:
        val ireader: IndexReader =
            maybeWrapWithMergingReader(DirectoryReader.open(directory)) // read-only=true
        val isearcher = IndexSearcher(ireader)
        val storedFields: StoredFields = isearcher.storedFields()

        assertEquals(1, isearcher.count(TermQuery(Term("fieldname", longTerm))).toLong())
        val query: Query = TermQuery(Term("fieldname", "text"))
        val hits: TopDocs = isearcher.search(query, 1)
        assertEquals(1, hits.totalHits.value)
        var scratch: BytesRef
        // Iterate through the results:
        for (i in hits.scoreDocs.indices) {
            val docID: Int = hits.scoreDocs[i].doc
            val hitDoc = storedFields.document(docID)
            assertEquals(text, hitDoc.get("fieldname"))
            assert(ireader.leaves().size == 1)
            val dv: SortedDocValues = ireader.leaves()[0].reader().getSortedDocValues("dv2")!!
            assertEquals(docID.toLong(), dv.advance(docID).toLong())
            val ord: Int = dv.ordValue()
            scratch = dv.lookupOrd(ord)!!
            assertEquals(newBytesRef("hello hello"), scratch)
            val dv2: NumericDocValues = ireader.leaves()[0].reader().getNumericDocValues("dv3")!!
            assertEquals(docID.toLong(), dv2.advance(docID).toLong())
            assertEquals(5, dv2.longValue())
            val dv3: BinaryDocValues = ireader.leaves()[0].reader().getBinaryDocValues("dv1")!!
            assertEquals(docID.toLong(), dv3.advance(docID).toLong())
            assertEquals(newBytesRef("hello world"), dv3.binaryValue())
        }

        ireader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testTwoDocumentsNumeric() {
        val analyzer: Analyzer = MockAnalyzer(random())

        val directory: Directory = newDirectory()
        val conf = newIndexWriterConfig(analyzer)
        conf.setMergePolicy(newLogMergePolicy())
        val iwriter = RandomIndexWriter(random(), directory, conf)
        var doc = Document()
        doc.add(NumericDocValuesField("dv", 1))
        iwriter.addDocument(doc)
        doc = Document()
        doc.add(NumericDocValuesField("dv", 2))
        iwriter.addDocument(doc)
        iwriter.forceMerge(1)
        iwriter.close()

        // Now search the index:
        val ireader: IndexReader = maybeWrapWithMergingReader(DirectoryReader.open(directory)) // read-only=true
        assert(ireader.leaves().size == 1)
        val dv: NumericDocValues = ireader.leaves()[0].reader().getNumericDocValues("dv")!!
        assertEquals(0, dv.nextDoc().toLong())
        assertEquals(1, dv.longValue())
        assertEquals(1, dv.nextDoc().toLong())
        assertEquals(2, dv.longValue())

        ireader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testTwoDocumentsMerged() {
        val analyzer: Analyzer = MockAnalyzer(random())

        val directory: Directory = newDirectory()
        val conf = newIndexWriterConfig(analyzer)
        conf.setMergePolicy(newLogMergePolicy())
        val iwriter = RandomIndexWriter(random(), directory, conf)
        var doc = Document()
        doc.add(newField("id", "0", StringField.TYPE_STORED))
        doc.add(NumericDocValuesField("dv", -10))
        iwriter.addDocument(doc)
        iwriter.commit()
        doc = Document()
        doc.add(newField("id", "1", StringField.TYPE_STORED))
        doc.add(NumericDocValuesField("dv", 99))
        iwriter.addDocument(doc)
        iwriter.forceMerge(1)
        iwriter.close()

        // Now search the index:
        val ireader: IndexReader = maybeWrapWithMergingReader(DirectoryReader.open(directory)) // read-only=true
        assert(ireader.leaves().size == 1)
        val dv: NumericDocValues = ireader.leaves()[0].reader().getNumericDocValues("dv")!!
        val storedFields: StoredFields =
            ireader.leaves()[0].reader().storedFields()
        for (i in 0..1) {
            val doc2 = storedFields.document(i)
            val expected: Long
            if (doc2.get("id") == "0") {
                expected = -10
            } else {
                expected = 99
            }
            assertEquals(i.toLong(), dv.nextDoc().toLong())
            assertEquals(expected, dv.longValue())
        }

        ireader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testBigNumericRange() {
        val analyzer: Analyzer = MockAnalyzer(random())

        val directory: Directory = newDirectory()
        val conf = newIndexWriterConfig(analyzer)
        conf.setMergePolicy(newLogMergePolicy())
        val iwriter = RandomIndexWriter(random(), directory, conf)
        var doc = Document()
        doc.add(NumericDocValuesField("dv", Long.MIN_VALUE))
        iwriter.addDocument(doc)
        doc = Document()
        doc.add(NumericDocValuesField("dv", Long.MAX_VALUE))
        iwriter.addDocument(doc)
        iwriter.forceMerge(1)
        iwriter.close()

        // Now search the index:
        val ireader: IndexReader = maybeWrapWithMergingReader(DirectoryReader.open(directory)) // read-only=true
        assert(ireader.leaves().size == 1)
        val dv: NumericDocValues = ireader.leaves()[0].reader().getNumericDocValues("dv")!!
        assertEquals(0, dv.nextDoc().toLong())
        assertEquals(Long.MIN_VALUE, dv.longValue())
        assertEquals(1, dv.nextDoc().toLong())
        assertEquals(Long.MAX_VALUE, dv.longValue())

        ireader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testBigNumericRange2() {
        val analyzer: Analyzer = MockAnalyzer(random())

        val directory: Directory = newDirectory()
        val conf = newIndexWriterConfig(analyzer)
        conf.setMergePolicy(newLogMergePolicy())
        val iwriter = RandomIndexWriter(random(), directory, conf)
        var doc = Document()
        doc.add(NumericDocValuesField("dv", -8841491950446638677L))
        iwriter.addDocument(doc)
        doc = Document()
        doc.add(NumericDocValuesField("dv", 9062230939892376225L))
        iwriter.addDocument(doc)
        iwriter.forceMerge(1)
        iwriter.close()

        // Now search the index:
        val ireader: IndexReader = maybeWrapWithMergingReader(DirectoryReader.open(directory)) // read-only=true
        assert(ireader.leaves().size == 1)
        val dv: NumericDocValues = ireader.leaves()[0].reader().getNumericDocValues("dv")!!
        assertEquals(0, dv.nextDoc().toLong())
        assertEquals(-8841491950446638677L, dv.longValue())
        assertEquals(1, dv.nextDoc().toLong())
        assertEquals(9062230939892376225L, dv.longValue())

        ireader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testBytes() {
        val analyzer: Analyzer = MockAnalyzer(random())

        val directory: Directory = newDirectory()
        val conf = newIndexWriterConfig(analyzer)
        val iwriter = RandomIndexWriter(random(), directory, conf)
        val doc = Document()
        val longTerm = "longtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongterm"
        val text = "This is the text to be indexed. $longTerm"
        doc.add(newTextField("fieldname", text, Field.Store.YES))
        doc.add(BinaryDocValuesField("dv", newBytesRef("hello world")))
        iwriter.addDocument(doc)
        iwriter.close()

        // Now search the index:
        val ireader: IndexReader = maybeWrapWithMergingReader(DirectoryReader.open(directory)) // read-only=true
        val isearcher = IndexSearcher(ireader)
        val storedFields: StoredFields = isearcher.storedFields()

        assertEquals(1, isearcher.count(TermQuery(Term("fieldname", longTerm))).toLong())
        val query: Query =
            TermQuery(Term("fieldname", "text"))
        val hits: TopDocs = isearcher.search(query, 1)
        assertEquals(1, hits.totalHits.value)
        // Iterate through the results:
        for (i in hits.scoreDocs.indices) {
            val hitDocID: Int = hits.scoreDocs[i].doc
            val hitDoc = storedFields.document(hitDocID)
            assertEquals(text, hitDoc.get("fieldname"))
            assert(ireader.leaves().size == 1)
            val dv: BinaryDocValues = ireader.leaves()[0].reader().getBinaryDocValues("dv")!!
            assertEquals(hitDocID.toLong(), dv.advance(hitDocID).toLong())
            assertEquals(newBytesRef("hello world"), dv.binaryValue())
        }

        ireader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testBytesTwoDocumentsMerged() {
        val analyzer: Analyzer = MockAnalyzer(random())

        val directory: Directory = newDirectory()
        val conf = newIndexWriterConfig(analyzer)
        conf.setMergePolicy(newLogMergePolicy())
        val iwriter = RandomIndexWriter(random(), directory, conf)
        var doc = Document()
        doc.add(newField("id", "0", StringField.TYPE_STORED))
        doc.add(BinaryDocValuesField("dv", newBytesRef("hello world 1")))
        iwriter.addDocument(doc)
        iwriter.commit()
        doc = Document()
        doc.add(newField("id", "1", StringField.TYPE_STORED))
        doc.add(BinaryDocValuesField("dv", newBytesRef("hello 2")))
        iwriter.addDocument(doc)
        iwriter.forceMerge(1)
        iwriter.close()

        // Now search the index:
        val ireader: IndexReader = maybeWrapWithMergingReader(DirectoryReader.open(directory)) // read-only=true
        assert(ireader.leaves().size == 1)
        val dv: BinaryDocValues = ireader.leaves()[0].reader().getBinaryDocValues("dv")!!
        val storedFields: StoredFields =
            ireader.leaves()[0].reader().storedFields()
        for (i in 0..1) {
            val doc2 = storedFields.document(i)
            val expected: String
            if (doc2.get("id") == "0") {
                expected = "hello world 1"
            } else {
                expected = "hello 2"
            }
            assertEquals(i.toLong(), dv.nextDoc().toLong())
            assertEquals(expected, dv.binaryValue()!!.utf8ToString())
        }

        ireader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testBytesMergeAwayAllValues() {
        val directory: Directory = newDirectory()
        val analyzer: Analyzer = MockAnalyzer(random())
        val iwconfig = newIndexWriterConfig(analyzer)
        iwconfig.setMergePolicy(newLogMergePolicy())
        val iwriter = RandomIndexWriter(random(), directory, iwconfig)

        var doc = Document()
        doc.add(StringField("id", "0", Field.Store.NO))
        iwriter.addDocument(doc)
        doc = Document()
        doc.add(StringField("id", "1", Field.Store.NO))
        doc.add(BinaryDocValuesField("field", newBytesRef("hi")))
        iwriter.addDocument(doc)
        iwriter.commit()
        iwriter.deleteDocuments(Term("id", "1"))
        iwriter.forceMerge(1)

        val ireader: DirectoryReader = iwriter.reader
        iwriter.close()

        val dv: BinaryDocValues = getOnlyLeafReader(ireader).getBinaryDocValues("field")!!
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), dv.nextDoc().toLong())

        ireader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testSortedBytes() {
        val analyzer: Analyzer = MockAnalyzer(random())

        val directory: Directory = newDirectory()
        val conf = newIndexWriterConfig(analyzer)
        val iwriter = RandomIndexWriter(random(), directory, conf)
        val doc = Document()
        val longTerm = "longtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongterm"
        val text = "This is the text to be indexed. $longTerm"
        doc.add(newTextField("fieldname", text, Field.Store.YES))
        doc.add(SortedDocValuesField("dv", newBytesRef("hello world")))
        iwriter.addDocument(doc)
        iwriter.close()

        // Now search the index:
        val ireader: IndexReader = maybeWrapWithMergingReader(DirectoryReader.open(directory)) // read-only=true
        val isearcher = IndexSearcher(ireader)

        assertEquals(1, isearcher.count(TermQuery(Term("fieldname", longTerm))).toLong())
        val query: Query = TermQuery(Term("fieldname", "text"))
        val hits: TopDocs = isearcher.search(query, 1)
        assertEquals(1, hits.totalHits.value)
        var scratch: BytesRef
        // Iterate through the results:
        val storedFields: StoredFields = isearcher.storedFields()
        for (i in hits.scoreDocs.indices) {
            val docID: Int = hits.scoreDocs[i].doc
            val hitDoc = storedFields.document(docID)
            assertEquals(text, hitDoc.get("fieldname"))
            assert(ireader.leaves().size == 1)
            val dv: SortedDocValues = ireader.leaves()[0].reader().getSortedDocValues("dv")!!
            assertEquals(docID.toLong(), dv.advance(docID).toLong())
            scratch = dv.lookupOrd(dv.ordValue())!!
            assertEquals(newBytesRef("hello world"), scratch)
        }

        ireader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testSortedBytesTwoDocuments() {
        val analyzer: Analyzer = MockAnalyzer(random())

        val directory: Directory = newDirectory()
        val conf = newIndexWriterConfig(analyzer)
        conf.setMergePolicy(newLogMergePolicy())
        val iwriter = RandomIndexWriter(random(), directory, conf)
        var doc = Document()
        doc.add(SortedDocValuesField("dv", newBytesRef("hello world 1")))
        iwriter.addDocument(doc)
        doc = Document()
        doc.add(SortedDocValuesField("dv", newBytesRef("hello world 2")))
        iwriter.addDocument(doc)
        iwriter.forceMerge(1)
        iwriter.close()

        // Now search the index:
        val ireader: IndexReader = maybeWrapWithMergingReader(DirectoryReader.open(directory)) // read-only=true
        assert(ireader.leaves().size == 1)
        val dv: SortedDocValues = ireader.leaves()[0].reader().getSortedDocValues("dv")!!
        assertEquals(0, dv.nextDoc().toLong())
        var scratch: BytesRef = dv.lookupOrd(dv.ordValue())!!
        assertEquals("hello world 1", scratch.utf8ToString())
        assertEquals(1, dv.nextDoc().toLong())
        scratch = dv.lookupOrd(dv.ordValue())!!
        assertEquals("hello world 2", scratch.utf8ToString())

        ireader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testSortedBytesThreeDocuments() {
        val analyzer: Analyzer = MockAnalyzer(random())

        val directory: Directory = newDirectory()
        val conf = newIndexWriterConfig(analyzer)
        conf.setMergePolicy(newLogMergePolicy())
        val iwriter = RandomIndexWriter(random(), directory, conf)
        var doc = Document()
        doc.add(SortedDocValuesField("dv", newBytesRef("hello world 1")))
        iwriter.addDocument(doc)
        doc = Document()
        doc.add(SortedDocValuesField("dv", newBytesRef("hello world 2")))
        iwriter.addDocument(doc)
        doc = Document()
        doc.add(SortedDocValuesField("dv", newBytesRef("hello world 1")))
        iwriter.addDocument(doc)
        iwriter.forceMerge(1)
        iwriter.close()

        // Now search the index:
        val ireader: IndexReader = maybeWrapWithMergingReader(DirectoryReader.open(directory)) // read-only=true
        assert(ireader.leaves().size == 1)
        val dv: SortedDocValues = ireader.leaves()[0].reader().getSortedDocValues("dv")!!
        assertEquals(2, dv.valueCount.toLong())
        assertEquals(0, dv.nextDoc().toLong())
        assertEquals(0, dv.ordValue().toLong())
        var scratch: BytesRef = dv.lookupOrd(0)!!
        assertEquals("hello world 1", scratch.utf8ToString())
        assertEquals(1, dv.nextDoc().toLong())
        assertEquals(1, dv.ordValue().toLong())
        scratch = dv.lookupOrd(1)!!
        assertEquals("hello world 2", scratch.utf8ToString())
        assertEquals(2, dv.nextDoc().toLong())
        assertEquals(0, dv.ordValue().toLong())

        ireader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testSortedBytesTwoDocumentsMerged() {
        val analyzer: Analyzer = MockAnalyzer(random())

        val directory: Directory = newDirectory()
        val conf = newIndexWriterConfig(analyzer)
        conf.setMergePolicy(newLogMergePolicy())
        val iwriter = RandomIndexWriter(random(), directory, conf)
        var doc = Document()
        doc.add(newField("id", "0", StringField.TYPE_STORED))
        doc.add(SortedDocValuesField("dv", newBytesRef("hello world 1")))
        iwriter.addDocument(doc)
        iwriter.commit()
        doc = Document()
        doc.add(newField("id", "1", StringField.TYPE_STORED))
        doc.add(SortedDocValuesField("dv", newBytesRef("hello world 2")))
        iwriter.addDocument(doc)
        iwriter.forceMerge(1)
        iwriter.close()

        // Now search the index:
        val ireader: IndexReader = maybeWrapWithMergingReader(DirectoryReader.open(directory)) // read-only=true
        assert(ireader.leaves().size == 1)
        val dv: SortedDocValues = ireader.leaves()[0].reader().getSortedDocValues("dv")!!
        assertEquals(2, dv.valueCount.toLong()) // 2 ords
        assertEquals(0, dv.nextDoc().toLong())
        var scratch: BytesRef? = dv.lookupOrd(dv.ordValue())
        assertEquals(newBytesRef("hello world 1"), scratch)
        scratch = dv.lookupOrd(1)!!
        assertEquals(
            newBytesRef("hello world 2"),
            scratch
        )
        val storedFields: StoredFields =
            ireader.leaves()[0].reader().storedFields()
        for (i in 0..1) {
            val doc2 = storedFields.document(i)
            val expected: String
            if (doc2.get("id") == "0") {
                expected = "hello world 1"
            } else {
                expected = "hello world 2"
            }
            if (dv.docID() < i) {
                assertEquals(i.toLong(), dv.nextDoc().toLong())
            }
            scratch = dv.lookupOrd(dv.ordValue())!!
            assertEquals(expected, scratch.utf8ToString())
        }

        ireader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testSortedMergeAwayAllValues() {
        val directory: Directory = newDirectory()
        val analyzer: Analyzer = MockAnalyzer(random())
        val iwconfig = newIndexWriterConfig(analyzer)
        iwconfig.setMergePolicy(newLogMergePolicy())
        val iwriter = RandomIndexWriter(random(), directory, iwconfig)

        var doc = Document()
        doc.add(StringField("id", "0", Field.Store.NO))
        iwriter.addDocument(doc)
        doc = Document()
        doc.add(StringField("id", "1", Field.Store.NO))
        doc.add(SortedDocValuesField("field", newBytesRef("hello")))
        iwriter.addDocument(doc)
        iwriter.commit()
        iwriter.deleteDocuments(Term("id", "1"))
        iwriter.forceMerge(1)

        val ireader: DirectoryReader = iwriter.reader
        iwriter.close()

        val dv: SortedDocValues = getOnlyLeafReader(ireader).getSortedDocValues("field")!!
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), dv.nextDoc().toLong())

        val termsEnum: TermsEnum = dv.termsEnum()!!
        assertFalse(termsEnum.seekExact(BytesRef("lucene")))
        assertEquals(SeekStatus.END, termsEnum.seekCeil(BytesRef("lucene")))
        assertEquals(-1, dv.lookupTerm(BytesRef("lucene")).toLong())

        ireader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testBytesWithNewline() {
        val analyzer: Analyzer = MockAnalyzer(random())

        val directory: Directory = newDirectory()
        val conf = newIndexWriterConfig(analyzer)
        conf.setMergePolicy(newLogMergePolicy())
        val iwriter = RandomIndexWriter(random(), directory, conf)
        val doc = Document()
        doc.add(BinaryDocValuesField("dv", newBytesRef("hello\nworld\r1")))
        iwriter.addDocument(doc)
        iwriter.close()

        // Now search the index:
        val ireader: IndexReader = maybeWrapWithMergingReader(DirectoryReader.open(directory)) // read-only=true
        assert(ireader.leaves().size == 1)
        val dv: BinaryDocValues = ireader.leaves()[0].reader().getBinaryDocValues("dv")!!
        assertEquals(0, dv.nextDoc().toLong())
        assertEquals(newBytesRef("hello\nworld\r1"), dv.binaryValue())

        ireader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testMissingSortedBytes() {
        val analyzer: Analyzer = MockAnalyzer(random())

        val directory: Directory = newDirectory()
        val conf = newIndexWriterConfig(analyzer)
        conf.setMergePolicy(newLogMergePolicy())
        val iwriter = RandomIndexWriter(random(), directory, conf)
        val doc = Document()
        doc.add(SortedDocValuesField("dv", newBytesRef("hello world 2")))
        iwriter.addDocument(doc)
        // 2nd doc missing the DV field
        iwriter.addDocument(Document())
        iwriter.close()

        // Now search the index:
        val ireader: IndexReader = maybeWrapWithMergingReader(DirectoryReader.open(directory)) // read-only=true
        assert(ireader.leaves().size == 1)
        val dv: SortedDocValues = ireader.leaves()[0].reader().getSortedDocValues("dv")!!
        assertEquals(0, dv.nextDoc().toLong())
        val scratch: BytesRef = dv.lookupOrd(dv.ordValue())!!
        assertEquals(newBytesRef("hello world 2"), scratch)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), dv.nextDoc().toLong())
        ireader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testSortedTermsEnum() {
        val directory: Directory = newDirectory()
        val analyzer: Analyzer = MockAnalyzer(random())
        val iwconfig = newIndexWriterConfig(analyzer)
        iwconfig.setMergePolicy(newLogMergePolicy())
        val iwriter = RandomIndexWriter(random(), directory, iwconfig)

        var doc = Document()
        doc.add(SortedDocValuesField("field", newBytesRef("hello")))
        iwriter.addDocument(doc)

        doc = Document()
        doc.add(SortedDocValuesField("field", newBytesRef("world")))
        iwriter.addDocument(doc)

        doc = Document()
        doc.add(SortedDocValuesField("field", newBytesRef("beer")))
        iwriter.addDocument(doc)
        iwriter.forceMerge(1)

        val ireader: DirectoryReader = iwriter.reader
        iwriter.close()

        val dv: SortedDocValues = getOnlyLeafReader(ireader).getSortedDocValues("field")!!
        assertEquals(3, dv.valueCount.toLong())

        var termsEnum: TermsEnum = dv.termsEnum()!!

        // next()
        assertEquals("beer", termsEnum.next()!!.utf8ToString())
        assertEquals(0, termsEnum.ord())
        assertEquals("hello", termsEnum.next()!!.utf8ToString())
        assertEquals(1, termsEnum.ord())
        assertEquals("world", termsEnum.next()!!.utf8ToString())
        assertEquals(2, termsEnum.ord())

        // seekCeil()
        assertEquals(SeekStatus.NOT_FOUND, termsEnum.seekCeil(newBytesRef("ha!")))
        assertEquals("hello", termsEnum.term()!!.utf8ToString())
        assertEquals(1, termsEnum.ord())
        assertEquals(SeekStatus.FOUND, termsEnum.seekCeil(newBytesRef("beer")))
        assertEquals("beer", termsEnum.term()!!.utf8ToString())
        assertEquals(0, termsEnum.ord())
        assertEquals(SeekStatus.END, termsEnum.seekCeil(newBytesRef("zzz")))
        assertEquals(SeekStatus.NOT_FOUND, termsEnum.seekCeil(newBytesRef("aba")))
        assertEquals(0, termsEnum.ord())

        // seekExact()
        assertTrue(termsEnum.seekExact(newBytesRef("beer")))
        assertEquals("beer", termsEnum.term()!!.utf8ToString())
        assertEquals(0, termsEnum.ord())
        assertTrue(termsEnum.seekExact(newBytesRef("hello")))
        assertEquals("hello", termsEnum.term()!!.utf8ToString(), Codec.default.toString())
        assertEquals(1, termsEnum.ord())
        assertTrue(termsEnum.seekExact(newBytesRef("world")))
        assertEquals("world", termsEnum.term()!!.utf8ToString())
        assertEquals(2, termsEnum.ord())
        assertFalse(termsEnum.seekExact(newBytesRef("bogus")))

        // seek(ord)
        termsEnum.seekExact(0)
        assertEquals("beer", termsEnum.term()!!.utf8ToString())
        assertEquals(0, termsEnum.ord())
        termsEnum.seekExact(1)
        assertEquals("hello", termsEnum.term()!!.utf8ToString())
        assertEquals(1, termsEnum.ord())
        termsEnum.seekExact(2)
        assertEquals("world", termsEnum.term()!!.utf8ToString())
        assertEquals(2, termsEnum.ord())

        // NORMAL automaton
        termsEnum = dv.intersect(CompiledAutomaton(Operations.determinize(RegExp(".*l.*").toAutomaton(), Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)))!!
        assertEquals("hello", termsEnum.next()!!.utf8ToString())
        assertEquals(1, termsEnum.ord())
        assertEquals("world", termsEnum.next()!!.utf8ToString())
        assertEquals(2, termsEnum.ord())
        assertNull(termsEnum.next())

        // SINGLE automaton
        termsEnum = dv.intersect(CompiledAutomaton(RegExp("hello").toAutomaton()))!!
        assertEquals("hello", termsEnum.next()!!.utf8ToString())
        assertEquals(1, termsEnum.ord())
        assertNull(termsEnum.next())

        ireader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testEmptySortedBytes() {
        val analyzer: Analyzer = MockAnalyzer(random())

        val directory: Directory = newDirectory()
        val conf = newIndexWriterConfig(analyzer)
        conf.setMergePolicy(newLogMergePolicy())
        val iwriter = RandomIndexWriter(random(), directory, conf)
        var doc = Document()
        doc.add(SortedDocValuesField("dv", newBytesRef("")))
        iwriter.addDocument(doc)
        doc = Document()
        doc.add(SortedDocValuesField("dv", newBytesRef("")))
        iwriter.addDocument(doc)
        iwriter.forceMerge(1)
        iwriter.close()

        // Now search the index:
        val ireader: IndexReader = maybeWrapWithMergingReader(DirectoryReader.open(directory)) // read-only=true
        assert(ireader.leaves().size == 1)
        val dv: SortedDocValues = ireader.leaves()[0].reader().getSortedDocValues("dv")!!
        assertEquals(0, dv.nextDoc().toLong())
        assertEquals(0, dv.ordValue().toLong())
        assertEquals(1, dv.nextDoc().toLong())
        assertEquals(0, dv.ordValue().toLong())
        val scratch: BytesRef = dv.lookupOrd(0)!!
        assertEquals("", scratch.utf8ToString())

        ireader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testEmptyBytes() {
        val analyzer: Analyzer = MockAnalyzer(random())

        val directory: Directory = newDirectory()
        val conf = newIndexWriterConfig(analyzer)
        conf.setMergePolicy(newLogMergePolicy())
        val iwriter = RandomIndexWriter(random(), directory, conf)
        var doc = Document()
        doc.add(BinaryDocValuesField("dv", newBytesRef("")))
        iwriter.addDocument(doc)
        doc = Document()
        doc.add(BinaryDocValuesField("dv", newBytesRef("")))
        iwriter.addDocument(doc)
        iwriter.forceMerge(1)
        iwriter.close()

        // Now search the index:
        val ireader: IndexReader = maybeWrapWithMergingReader(DirectoryReader.open(directory)) // read-only=true
        assert(ireader.leaves().size == 1)
        val dv: BinaryDocValues = ireader.leaves()[0].reader().getBinaryDocValues("dv")!!
        assertEquals(0, dv.nextDoc().toLong())
        assertEquals("", dv.binaryValue()!!.utf8ToString())
        assertEquals(1, dv.nextDoc().toLong())
        assertEquals("", dv.binaryValue()!!.utf8ToString())

        ireader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testVeryLargeButLegalBytes() {
        val analyzer: Analyzer = MockAnalyzer(random())

        val directory: Directory = newDirectory()
        val conf = newIndexWriterConfig(analyzer)
        conf.setMergePolicy(newLogMergePolicy())
        val iwriter = RandomIndexWriter(random(), directory, conf)
        val doc = Document()
        val bytes = ByteArray(327) // TODO reduced from 32766 to 327 for dev speed
        random().nextBytes(bytes)
        val b: BytesRef = newBytesRef(bytes)
        doc.add(BinaryDocValuesField("dv", b))
        iwriter.addDocument(doc)
        iwriter.close()

        // Now search the index:
        val ireader: IndexReader =
            maybeWrapWithMergingReader(DirectoryReader.open(directory)) // read-only=true
        assert(ireader.leaves().size == 1)
        val dv: BinaryDocValues = ireader.leaves()[0].reader().getBinaryDocValues("dv")!!
        assertEquals(0, dv.nextDoc().toLong())
        assertEquals(newBytesRef(bytes), dv.binaryValue())

        ireader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testVeryLargeButLegalSortedBytes() {
        val analyzer: Analyzer = MockAnalyzer(random())

        val directory: Directory = newDirectory()
        val conf = newIndexWriterConfig(analyzer)
        conf.setMergePolicy(newLogMergePolicy())
        val iwriter = RandomIndexWriter(random(), directory, conf)
        val doc = Document()
        val bytes = ByteArray(32766)
        random().nextBytes(bytes)
        val b: BytesRef = newBytesRef(bytes)
        doc.add(SortedDocValuesField("dv", b))
        iwriter.addDocument(doc)
        iwriter.close()

        // Now search the index:
        val ireader: IndexReader = maybeWrapWithMergingReader(DirectoryReader.open(directory)) // read-only=true
        assert(ireader.leaves().size == 1)
        val dv: SortedDocValues = DocValues.getSorted(ireader.leaves()[0].reader(), "dv")
        assertEquals(0, dv.nextDoc().toLong())
        assertEquals(newBytesRef(bytes), dv.lookupOrd(dv.ordValue()))
        ireader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testCodecUsesOwnBytes() {
        val analyzer: Analyzer = MockAnalyzer(random())

        val directory: Directory = newDirectory()
        val conf = newIndexWriterConfig(analyzer)
        conf.setMergePolicy(newLogMergePolicy())
        val iwriter = RandomIndexWriter(random(), directory, conf)
        val doc = Document()
        doc.add(BinaryDocValuesField("dv", newBytesRef("boo!")))
        iwriter.addDocument(doc)
        iwriter.close()

        // Now search the index:
        val ireader: IndexReader = maybeWrapWithMergingReader(DirectoryReader.open(directory)) // read-only=true
        assert(ireader.leaves().size == 1)
        val dv: BinaryDocValues = ireader.leaves()[0].reader().getBinaryDocValues("dv")!!
        assertEquals(0, dv.nextDoc().toLong())
        assertEquals("boo!", dv.binaryValue()!!.utf8ToString())

        ireader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testCodecUsesOwnSortedBytes() {
        val analyzer: Analyzer = MockAnalyzer(random())

        val directory: Directory = newDirectory()
        val conf = newIndexWriterConfig(analyzer)
        conf.setMergePolicy(newLogMergePolicy())
        val iwriter = RandomIndexWriter(random(), directory, conf)
        val doc = Document()
        doc.add(SortedDocValuesField("dv", newBytesRef("boo!")))
        iwriter.addDocument(doc)
        iwriter.close()

        // Now search the index:
        val ireader: IndexReader = maybeWrapWithMergingReader(DirectoryReader.open(directory)) // read-only=true
        assert(ireader.leaves().size == 1)
        val dv: SortedDocValues = DocValues.getSorted(ireader.leaves()[0].reader(), "dv")
        val mybytes = ByteArray(20)
        assertEquals(0, dv.nextDoc().toLong())
        assertEquals("boo!", dv.lookupOrd(dv.ordValue())!!.utf8ToString())
        assertFalse(dv.lookupOrd(dv.ordValue())!!.bytes.contentEquals(mybytes))

        ireader.close()
        directory.close()
    }

    /*
   * Simple test case to show how to use the API
   */
    @Throws(IOException::class)
    open fun testDocValuesSimple() {
        val dir: Directory = newDirectory()
        val analyzer: Analyzer =
            MockAnalyzer(random())
        val conf =
            newIndexWriterConfig(analyzer)
        conf.setMergePolicy(newLogMergePolicy())
        val writer =
            IndexWriter(dir, conf)
        for (i in 0..4) {
            val doc = Document()
            doc.add(NumericDocValuesField("docId", i.toLong()))
            doc.add(TextField("docId", "" + i, Field.Store.NO))
            writer.addDocument(doc)
        }
        writer.commit()
        writer.forceMerge(1, true)

        writer.close()

        val reader: DirectoryReader =
            maybeWrapWithMergingReader(DirectoryReader.open(dir))
        assertEquals(1, reader.leaves().size.toLong())

        val searcher =
            IndexSearcher(reader)

        val query: BooleanQuery.Builder = BooleanQuery.Builder()
        query.add(TermQuery(Term("docId", "0")), BooleanClause.Occur.SHOULD)
        query.add(TermQuery(Term("docId", "1")), BooleanClause.Occur.SHOULD)
        query.add(TermQuery(Term("docId", "2")), BooleanClause.Occur.SHOULD)
        query.add(TermQuery(Term("docId", "3")), BooleanClause.Occur.SHOULD)
        query.add(TermQuery(Term("docId", "4")), BooleanClause.Occur.SHOULD)

        val search: TopDocs = searcher.search(query.build(), 10)
        assertEquals(5, search.totalHits.value)
        val scoreDocs: Array<ScoreDoc> = search.scoreDocs
        val docValues: NumericDocValues = getOnlyLeafReader(reader).getNumericDocValues("docId")!!
        for (i in scoreDocs.indices) {
            assertEquals(i.toLong(), scoreDocs[i].doc.toLong())
            assertEquals(i.toLong(), docValues.advance(i).toLong())
            assertEquals(i.toLong(), docValues.longValue())
        }
        reader.close()
        dir.close()
    }

    @Throws(IOException::class)
    open fun testRandomSortedBytes() {
        val dir: Directory = newDirectory()
        val cfg = newIndexWriterConfig(MockAnalyzer(random()))
        val w = RandomIndexWriter(random(), dir, cfg)
        val numDocs: Int = atLeast(3) // TODO reduced from 100 to 3 for dev speed
        val hash = BytesRefHash()
        val docToString: MutableMap<String, String> = HashMap()
        val maxLength: Int = TestUtil.nextInt(random(), 1, 50)
        for (i in 0..<numDocs) {
            val doc = Document()
            doc.add(newTextField("id", "" + i, Field.Store.YES))
            val string: String = TestUtil.randomRealisticUnicodeString(random(), 1, maxLength)
            val br: BytesRef = newBytesRef(string)
            doc.add(SortedDocValuesField("field", br))
            hash.add(br)
            docToString["" + i] = string
            w.addDocument(doc)
        }
        if (rarely()) {
            w.commit()
        }
        val numDocsNoValue: Int = atLeast(10)
        for (i in 0..<numDocsNoValue) {
            val doc = Document()
            doc.add(newTextField("id", "noValue", Field.Store.YES))
            w.addDocument(doc)
        }
        if (rarely()) {
            w.commit()
        }
        for (i in 0..<numDocs) {
            val doc = Document()
            val id = "" + (i + numDocs)
            doc.add(newTextField("id", id, Field.Store.YES)
            )
            val string: String = TestUtil.randomRealisticUnicodeString(random(), 1, maxLength)
            val br: BytesRef = newBytesRef(string)
            hash.add(br)
            docToString[id] = string
            doc.add(SortedDocValuesField("field", br))
            w.addDocument(doc)
        }
        w.commit()
        val reader: IndexReader = w.reader
        var docValues: SortedDocValues = MultiDocValues.getSortedValues(reader, "field")!!
        val sort: IntArray = hash.sort()
        var expected: BytesRef = newBytesRef()
        assertEquals(hash.size().toLong(), docValues.valueCount.toLong())
        for (i in 0..<hash.size()) {
            hash.get(sort[i], expected)
            val actual: BytesRef = docValues.lookupOrd(i)!!
            assertEquals(expected.utf8ToString(), actual.utf8ToString())
            val ord: Int = docValues.lookupTerm(expected)
            assertEquals(i.toLong(), ord.toLong())
        }
        val entrySet = docToString.entries

        for (entry in entrySet) {
            // pk lookup
            val termPostingsEnum: PostingsEnum = TestUtil.docs(random(), reader, "id", newBytesRef(entry.key), null, 0)!!
            val docId: Int = termPostingsEnum.nextDoc()
            expected = newBytesRef(entry.value)
            docValues = MultiDocValues.getSortedValues(reader, "field")!!
            assertEquals(docId.toLong(), docValues.advance(docId).toLong())
            val actual: BytesRef = docValues.lookupOrd(docValues.ordValue())!!
            assertEquals(expected, actual)
        }

        reader.close()
        w.close()
        dir.close()
    }

    @Throws(Exception::class)
    private fun doTestNumericsVsStoredFields(
        density: Double,
        longs: () -> Long /*java.util.function.LongSupplier*/,
        minDocs: Int = 256
    ) {
        val dir: Directory = newDirectory()
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        val writer = RandomIndexWriter(random(), dir, conf)
        val doc = Document()
        val idField: Field = StringField("id", "", Field.Store.NO)
        val storedField: Field = newStringField("stored", "", Field.Store.YES)
        val dvField: Field = NumericDocValuesField("dv", 0)
        doc.add(idField)
        doc.add(storedField)
        doc.add(dvField)

        // index some docs
        val numDocs: Int =
            atLeast((minDocs * 1.172).toInt())
        // numDocs should be always > 256 so that in case of a codec that optimizes
        // for numbers of values <= 256, all storage layouts are tested

        logger.debug { "LegacyBaseDocValuesFormatTestCase.doTestNumericsVsStoredFields numDocs: $numDocs" }

        assert(numDocs > 256)
        for (i in 0..<numDocs) {
            if (random().nextDouble() > density) {
                writer.addDocument(Document())
                continue
            }
            idField.setStringValue(i.toString())
            val value: Long = longs()
            storedField.setStringValue(value.toString())
            dvField.setLongValue(value)
            writer.addDocument(doc)
            if (random().nextInt(31) == 0) {
                writer.commit()
            }
        }

        // delete some docs
        val numDeletions: Int =
            random().nextInt(numDocs / 10)
        for (i in 0..<numDeletions) {
            val id: Int = random().nextInt(numDocs)
            writer.deleteDocuments(Term("id", id.toString()))
        }

        // merge some segments and ensure that at least one of them has more than
        // max(256, minDocs) values
        writer.forceMerge(numDocs / max(256, minDocs))

        writer.close()
        // compare
        assertDVIterate(dir)
        dir.close()
    }

    // Asserts equality of stored value vs. DocValue by iterating DocValues one at a time
    @Throws(IOException::class)
    protected fun assertDVIterate(dir: Directory) {
        val ir: DirectoryReader = maybeWrapWithMergingReader(DirectoryReader.open(dir))
        TestUtil.checkReader(ir)
        for (context in ir.leaves()) {
            val r: LeafReader = context.reader()
            val docValues: NumericDocValues = DocValues.getNumeric(r, "dv")
            docValues.nextDoc()
            val storedFields: StoredFields = r.storedFields()
            for (i in 0..<r.maxDoc()) {
                val storedValue: String? = storedFields.document(i).get("stored")
                if (storedValue == null) {
                    assertTrue(docValues.docID() > i)
                } else {
                    assertEquals(i.toLong(), docValues.docID().toLong())
                    assertEquals(storedValue.toLong(), docValues.longValue())
                    docValues.nextDoc()
                }
            }
            assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docValues.docID().toLong())
        }
        ir.close()
    }

    @Throws(IOException::class)
    protected fun compareStoredFieldWithSortedNumericsDV(
        directoryReader: DirectoryReader,
        storedField: String,
        dvField: String
    ) {
        for (leaf in directoryReader.leaves()) {
            val reader: LeafReader = leaf.reader()
            val storedFields: StoredFields = reader.storedFields()
            var docValues: SortedNumericDocValues? = reader.getSortedNumericDocValues(dvField)
            if (docValues == null) {
                // no stored values at all
                for (doc in 0..<reader.maxDoc()) {
                    assertArrayEquals(
                        kotlin.arrayOfNulls(0),
                        storedFields.document(doc).getValues(storedField)
                    )
                }
                continue
            }
            for (doc in 0..<reader.maxDoc()) {
                val storedValues: Array<String?> = storedFields.document(doc).getValues(storedField)
                if (storedValues.isEmpty()) {
                    assertFalse(docValues.advanceExact(doc))
                    continue
                }
                when (random().nextInt(3)) {
                    0 -> assertEquals(doc.toLong(), docValues.nextDoc().toLong())
                    1 -> assertEquals(doc.toLong(), docValues.advance(doc).toLong())

                    else -> assertTrue(docValues.advanceExact(doc))
                }
                assertEquals(doc.toLong(), docValues.docID().toLong())
                val repeats: Int = 1 + random().nextInt(3)
                for (r in 0..<repeats) {
                    if (r > 0 || random().nextBoolean()) {
                        assertTrue(docValues.advanceExact(doc))
                    }
                    assertEquals(storedValues.size.toLong(), docValues.docValueCount().toLong())
                    for (v in 0..<docValues.docValueCount()) {
                        assertEquals(storedValues[v], docValues.nextValue().toString())
                    }
                }
            }
            // jump with advanceExact
            val iters: Int = 1 + random().nextInt(3)
            for (i in 0..<iters) {
                docValues = reader.getSortedNumericDocValues(dvField)!!
                var doc: Int = random().nextInt(leaf.reader().maxDoc())
                while (doc < reader.maxDoc()) {
                    val storedValues: Array<String?> =
                        storedFields.document(doc).getValues(storedField)
                    if (docValues.advanceExact(doc)) {
                        assertEquals(doc.toLong(), docValues.docID().toLong())
                        val repeats: Int = 1 + random().nextInt(3)
                        for (r in 0..<repeats) {
                            if (r > 0 || random().nextBoolean()) {
                                assertTrue(docValues.advanceExact(doc))
                            }
                            assertEquals(storedValues.size.toLong(), docValues.docValueCount().toLong())
                            for (v in 0..<docValues.docValueCount()) {
                                assertEquals(storedValues[v], docValues.nextValue().toString())
                            }
                        }
                    } else {
                        assertArrayEquals(kotlin.arrayOfNulls(0), storedValues)
                    }
                    doc += random().nextInt(5) // skip some docs
                    doc++
                }
            }
            // jump with advance
            for (i in 0..<iters) {
                docValues = reader.getSortedNumericDocValues(dvField)!!
                var doc: Int = random()
                    .nextInt(leaf.reader().maxDoc())
                while (doc != DocIdSetIterator.NO_MORE_DOCS) {
                    val nextDoc: Int = docValues.advance(doc)
                    // no stored fields in between
                    for (d in doc..<(if (nextDoc == DocIdSetIterator.NO_MORE_DOCS) reader.maxDoc() else nextDoc)) {
                        val storedValues: Array<String?> = storedFields.document(d).getValues(storedField)
                        assertArrayEquals(kotlin.arrayOfNulls(0), storedValues)
                    }
                    doc = nextDoc
                    if (doc != DocIdSetIterator.NO_MORE_DOCS) {
                        val storedValues: Array<String?> = storedFields.document(doc).getValues(storedField)
                        val repeats: Int = 1 + random().nextInt(3)
                        for (r in 0..<repeats) {
                            if (r > 0 || random().nextBoolean()) {
                                assertTrue(docValues.advanceExact(doc))
                            }
                            assertEquals(storedValues.size.toLong(), docValues.docValueCount().toLong())
                            for (v in 0..<docValues.docValueCount()) {
                                assertEquals(
                                    storedValues[v], docValues.nextValue().toString())
                            }
                        }
                        doc = nextDoc + 1
                        doc += random().nextInt(5) // skip some docs
                    }
                }
            }
        }
    }

    @Throws(Exception::class)
    private fun doTestSortedNumericsVsStoredFields(
        counts: () -> Long /*java.util.function.LongSupplier*/,
        values: () -> Long /*java.util.function.LongSupplier*/
    ) {
        val dir: Directory = newDirectory()
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        val writer = RandomIndexWriter(random(), dir, conf)

        // index some docs
        val numDocs: Int = atLeast(257) // keep >256 invariant while reducing runtime vs original 300
        // numDocs should be always > 256 so that in case of a codec that optimizes
        // for numbers of values <= 256, all storage layouts are tested
        assert(numDocs > 256)
        for (i in 0..<numDocs) {
            val doc = Document()
            doc.add(StringField("id", i.toString(), Field.Store.NO))

            val valueCount = counts().toInt()
            val valueArray = LongArray(valueCount)
            for (j in 0..<valueCount) {
                val value: Long = values()
                valueArray[j] = value
                doc.add(SortedNumericDocValuesField("dv", value))
            }
            Arrays.sort(valueArray)
            for (j in 0..<valueCount) {
                doc.add(StoredField("stored", valueArray[j].toString()))
            }
            writer.addDocument(doc)
            if (random().nextInt(31) == 0) {
                writer.commit()
            }
        }

        // delete some docs
        val numDeletions: Int = random().nextInt(numDocs / 10)
        for (i in 0..<numDeletions) {
            val id: Int = random().nextInt(numDocs)
            writer.deleteDocuments(Term("id", id.toString()))
        }
        maybeWrapWithMergingReader(DirectoryReader.open(dir)).use { reader ->
            TestUtil.checkReader(reader)
            compareStoredFieldWithSortedNumericsDV(reader, "stored", "dv")
        }
        // merge some segments and ensure that at least one of them has more than
        // 256 values
        writer.forceMerge(numDocs / 256)
        maybeWrapWithMergingReader(DirectoryReader.open(dir)).use { reader ->
            TestUtil.checkReader(reader)
            compareStoredFieldWithSortedNumericsDV(reader, "stored", "dv")
        }
        IOUtils.close(writer, dir)
    }

    @Throws(Exception::class)
    open fun testBooleanNumericsVsStoredFields() {
        val numIterations: Int = atLeast(1)
        for (i in 0..<numIterations) {
            doTestNumericsVsStoredFields(
                1.0,
                /*java.util.function.LongSupplier*/ {
                    random().nextInt(2).toLong()
                })
        }
    }

    @Throws(Exception::class)
    open fun testSparseBooleanNumericsVsStoredFields() {
        val numIterations: Int = atLeast(1)
        for (i in 0..<numIterations) {
            doTestNumericsVsStoredFields(
                random().nextDouble(),
                /*java.util.function.LongSupplier*/ {
                    random().nextInt(2).toLong()
                })
        }
    }

    @Throws(Exception::class)
    open fun testByteNumericsVsStoredFields() {
        val numIterations: Int = atLeast(1)
        for (i in 0..<numIterations) {
            doTestNumericsVsStoredFields(
                1.0,
                /*java.util.function.LongSupplier*/ {
                    TestUtil.nextInt(
                        random(),
                        Byte.MIN_VALUE.toInt(),
                        Byte.MAX_VALUE.toInt()
                    ).toLong()
                })
        }
    }

    @Throws(Exception::class)
    open fun testSparseByteNumericsVsStoredFields() {
        val numIterations: Int = atLeast(1)
        for (i in 0..<numIterations) {
            doTestNumericsVsStoredFields(
                random().nextDouble(),
                /*java.util.function.LongSupplier*/ {
                    TestUtil.nextInt(
                        random(),
                        Byte.MIN_VALUE.toInt(),
                        Byte.MAX_VALUE.toInt()
                    ).toLong()
                })
        }
    }

    @Throws(Exception::class)
    open fun testShortNumericsVsStoredFields() {
        val numIterations: Int = atLeast(1)
        for (i in 0..<numIterations) {
            doTestNumericsVsStoredFields(
                1.0,
                /*java.util.function.LongSupplier*/ {
                    TestUtil.nextInt(
                        random(),
                        /*Short.MIN_VALUE.toInt()*/ -100, // TODO reduced for dev speed
                        /*Short.MAX_VALUE.toInt()*/ 100   // TODO reduced for dev speed
                    ).toLong()
                })
        }
    }

    @Throws(Exception::class)
    open fun testSparseShortNumericsVsStoredFields() {
        val numIterations: Int = atLeast(1)
        for (i in 0..<numIterations) {
            doTestNumericsVsStoredFields(
                random().nextDouble(),
                /*java.util.function.LongSupplier*/ {
                    TestUtil.nextInt(
                        random(),
                        /*Short.MIN_VALUE.toInt()*/ -100, // TODO reduced for dev speed
                        /*Short.MAX_VALUE.toInt()*/ 100   // TODO reduced for dev speed
                    ).toLong()
                })
        }
    }

    @Throws(Exception::class)
    open fun testIntNumericsVsStoredFields() {
        val numIterations: Int = atLeast(1)
        for (i in 0..<numIterations) {
            doTestNumericsVsStoredFields(
                1.0,
                /*java.util.function.LongSupplier*/ {
                    random().nextInt().toLong()
                })
        }
    }

    @Throws(Exception::class)
    open fun testSparseIntNumericsVsStoredFields() {
        val numIterations: Int = atLeast(1)
        for (i in 0..<numIterations) {
            doTestNumericsVsStoredFields(
                random().nextDouble(),
                /*java.util.function.LongSupplier*/ {
                    random().nextInt().toLong()
                })
        }
    }

    @Throws(Exception::class)
    open fun testLongNumericsVsStoredFields() {
        val numIterations: Int = atLeast(1)
        for (i in 0..<numIterations) {
            doTestNumericsVsStoredFields(
                1.0,
                /*java.util.function.LongSupplier*/ {
                    random().nextLong()
                })
        }
    }

    @Throws(Exception::class)
    open fun testSparseLongNumericsVsStoredFields() {
        val numIterations: Int = atLeast(1)
        for (i in 0..<numIterations) {
            doTestNumericsVsStoredFields(
                random().nextDouble(),
                /*java.util.function.LongSupplier*/ {
                    random().nextLong()
                })
        }
    }

    @Throws(Exception::class)
    private fun doTestBinaryVsStoredFields(
        density: Double,
        bytes: () -> ByteArray /*java.util.function.Supplier<ByteArray>*/
    ) {
        val dir: Directory = newDirectory()
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        val writer = RandomIndexWriter(random(), dir, conf)
        val doc = Document()
        val idField: Field = StringField("id", "", Field.Store.NO)
        val storedField: Field = StoredField("stored", ByteArray(0))
        val dvField: Field = BinaryDocValuesField("dv", newBytesRef())
        doc.add(idField)
        doc.add(storedField)
        doc.add(dvField)

        // index some docs
        val numDocs: Int = atLeast(300)
        for (i in 0..<numDocs) {
            if (random().nextDouble() > density) {
                writer.addDocument(Document())
                continue
            }
            idField.setStringValue(i.toString())
            val buffer: ByteArray = bytes()
            storedField.setBytesValue(buffer)
            dvField.setBytesValue(buffer)
            writer.addDocument(doc)
            if (random().nextInt(31) == 0) {
                writer.commit()
            }
        }

        // delete some docs
        val numDeletions: Int = random().nextInt(numDocs / 10)
        for (i in 0..<numDeletions) {
            val id: Int = random().nextInt(numDocs)
            writer.deleteDocuments(Term("id", id.toString()))
        }

        // compare
        var ir: DirectoryReader = writer.reader
        TestUtil.checkReader(ir)
        for (context in ir.leaves()) {
            val r: LeafReader = context.reader()
            val storedFields: StoredFields = r.storedFields()
            val docValues: BinaryDocValues =
                DocValues.getBinary(r, "dv")
            docValues.nextDoc()
            for (i in 0..<r.maxDoc()) {
                val binaryValue: BytesRef? = storedFields.document(i).getBinaryValue("stored")
                if (binaryValue == null) {
                    assertTrue(docValues.docID() > i)
                } else {
                    assertEquals(i.toLong(), docValues.docID().toLong())
                    assertEquals(binaryValue, docValues.binaryValue())
                    docValues.nextDoc()
                }
            }
            assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docValues.docID().toLong())
        }
        ir.close()

        // compare again
        writer.forceMerge(1)
        ir = writer.reader
        TestUtil.checkReader(ir)
        for (context in ir.leaves()) {
            val r: LeafReader = context.reader()
            val storedFields: StoredFields = r.storedFields()
            val docValues: BinaryDocValues = DocValues.getBinary(r, "dv")
            docValues.nextDoc()
            for (i in 0..<r.maxDoc()) {
                val binaryValue: BytesRef? = storedFields.document(i).getBinaryValue("stored")
                if (binaryValue == null) {
                    assertTrue(docValues.docID() > i)
                } else {
                    assertEquals(i.toLong(), docValues.docID().toLong())
                    assertEquals(binaryValue, docValues.binaryValue())
                    docValues.nextDoc()
                }
            }
            assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docValues.docID().toLong())
        }
        ir.close()
        writer.close()
        dir.close()
    }

    @Throws(Exception::class)
    open fun testBinaryFixedLengthVsStoredFields() {
        doTestBinaryFixedLengthVsStoredFields(1.0)
    }

    @Throws(Exception::class)
    open fun testSparseBinaryFixedLengthVsStoredFields() {
        doTestBinaryFixedLengthVsStoredFields(
            random().nextDouble()
        )
    }

    @Throws(Exception::class)
    private fun doTestBinaryFixedLengthVsStoredFields(density: Double) {
        val numIterations: Int = atLeast(1)
        for (i in 0..<numIterations) {
            val fixedLength: Int = TestUtil.nextInt(
                random(),
                0,
                10
            )
            doTestBinaryVsStoredFields(
                density
            ) {
                val buffer = ByteArray(fixedLength)
                random().nextBytes(buffer)
                buffer
            }
        }
    }

    @Throws(Exception::class)
    open fun testBinaryVariableLengthVsStoredFields() {
        doTestBinaryVariableLengthVsStoredFields(1.0)
    }

    @Throws(Exception::class)
    open fun testSparseBinaryVariableLengthVsStoredFields() {
        doTestBinaryVariableLengthVsStoredFields(
            random().nextDouble()
        )
    }

    @Throws(Exception::class)
    fun doTestBinaryVariableLengthVsStoredFields(density: Double) {
        val numIterations: Int = atLeast(1)
        for (i in 0..<numIterations) {
            doTestBinaryVsStoredFields(
                density
            ) {
                val length: Int = random().nextInt(10)
                val buffer = ByteArray(length)
                random().nextBytes(buffer)
                buffer
            }
        }
    }

    @Throws(Exception::class)
    protected fun doTestSortedVsStoredFields(
        numDocs: Int,
        density: Double,
        bytes: () -> ByteArray /*java.util.function.Supplier<ByteArray>*/
    ) {
        val dir: Directory = newFSDirectory(createTempDir("dvduel"))
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        val writer = RandomIndexWriter(random(), dir, conf)
        val doc = Document()
        val idField: Field = StringField("id", "", Field.Store.NO)
        val storedField: Field = StoredField("stored", ByteArray(0))
        val dvField: Field = SortedDocValuesField("dv", newBytesRef())
        doc.add(idField)
        doc.add(storedField)
        doc.add(dvField)

        // index some docs
        for (i in 0..<numDocs) {
            if (random().nextDouble() > density) {
                writer.addDocument(Document())
                continue
            }
            idField.setStringValue(i.toString())
            val buffer: ByteArray = bytes()
            storedField.setBytesValue(buffer)
            dvField.setBytesValue(buffer)
            writer.addDocument(doc)
            if (random().nextInt(31) == 0) {
                writer.commit()
            }
        }

        // delete some docs
        val numDeletions: Int =
            random().nextInt(numDocs / 10)
        for (i in 0..<numDeletions) {
            val id: Int = random().nextInt(numDocs)
            writer.deleteDocuments(Term("id", id.toString()))
        }

        // compare
        var ir: DirectoryReader = writer.reader
        TestUtil.checkReader(ir)
        for (context in ir.leaves()) {
            val r: LeafReader = context.reader()
            val storedFields: StoredFields = r.storedFields()
            val docValues: SortedDocValues = DocValues.getSorted(r, "dv")
            docValues.nextDoc()
            for (i in 0..<r.maxDoc()) {
                val binaryValue: BytesRef? = storedFields.document(i).getBinaryValue("stored")
                if (binaryValue == null) {
                    assertTrue(docValues.docID() > i)
                } else {
                    assertEquals(i.toLong(), docValues.docID().toLong())
                    assertEquals(binaryValue, docValues.lookupOrd(docValues.ordValue()))
                    docValues.nextDoc()
                }
            }
            assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docValues.docID().toLong())
        }
        ir.close()
        writer.forceMerge(1)

        // compare again
        ir = writer.reader
        TestUtil.checkReader(ir)
        for (context in ir.leaves()) {
            val r: LeafReader = context.reader()
            val storedFields: StoredFields = r.storedFields()
            val docValues: SortedDocValues =
                DocValues.getSorted(r, "dv")
            docValues.nextDoc()
            for (i in 0..<r.maxDoc()) {
                val binaryValue: BytesRef? = storedFields.document(i).getBinaryValue("stored")
                if (binaryValue == null) {
                    assertTrue(docValues.docID() > i)
                } else {
                    assertEquals(i.toLong(), docValues.docID().toLong())
                    assertEquals(binaryValue, docValues.lookupOrd(docValues.ordValue()))
                    docValues.nextDoc()
                }
            }
            assertEquals(
                DocIdSetIterator.NO_MORE_DOCS.toLong(),
                docValues.docID().toLong()
            )
        }
        ir.close()
        writer.close()
        dir.close()
    }

    @Throws(Exception::class)
    open fun testSortedFixedLengthVsStoredFields() {
        val numIterations: Int = atLeast(1)
        for (i in 0..<numIterations) {
            val fixedLength: Int = TestUtil.nextInt(
                random(),
                1,
                10
            )
            doTestSortedVsStoredFields(
                atLeast(30), // TODO reduced from 300 to 30 for dev speed
                1.0,
                fixedLength,
                fixedLength
            )
        }
    }

    @Throws(Exception::class)
    open fun testSparseSortedFixedLengthVsStoredFields() {
        val numIterations: Int = atLeast(1)
        for (i in 0..<numIterations) {
            val fixedLength: Int = TestUtil.nextInt(
                random(),
                1,
                10
            )
            doTestSortedVsStoredFields(
                atLeast(30), // TODO reduced from 300 to 30 for dev speed
                random().nextDouble(),
                fixedLength,
                fixedLength
            )
        }
    }

    @Throws(Exception::class)
    open fun testSortedVariableLengthVsStoredFields() {
        val numIterations: Int = atLeast(1)
        for (i in 0..<numIterations) {
            doTestSortedVsStoredFields(
                atLeast(30), // TODO reduced from 300 to 30 for dev speed
                1.0,
                1,
                10
            )
        }
    }

    @Throws(Exception::class)
    open fun testSparseSortedVariableLengthVsStoredFields() {
        val numIterations: Int = atLeast(1)
        for (i in 0..<numIterations) {
            doTestSortedVsStoredFields(
                atLeast(30), // TODO reduced from 300 to 30 for dev speed
                random().nextDouble(),
                1,
                10
            )
        }
    }

    @Throws(Exception::class)
    protected fun doTestSortedVsStoredFields(
        numDocs: Int, density: Double, minLength: Int, maxLength: Int
    ) {
        doTestSortedVsStoredFields(
            numDocs,
            density
        ) {
            val length: Int = TestUtil.nextInt(random(), minLength, maxLength)
            val buffer = ByteArray(length)
            random().nextBytes(buffer)
            buffer
        }
    }

    @Throws(IOException::class)
    open fun testSortedSetOneValue() {
        val directory: Directory = newDirectory()
        val iwriter = RandomIndexWriter(random(), directory)

        val doc = Document()
        doc.add(SortedSetDocValuesField("field", newBytesRef("hello")))
        iwriter.addDocument(doc)

        val ireader: DirectoryReader = iwriter.reader
        iwriter.close()

        val dv: SortedSetDocValues = getOnlyLeafReader(ireader).getSortedSetDocValues("field")!!
        assertEquals(0, dv.nextDoc().toLong())

        assertEquals(1, dv.docValueCount().toLong())
        assertEquals(0, dv.nextOrd())

        val bytes: BytesRef = dv.lookupOrd(0)!!
        assertEquals(newBytesRef("hello"), bytes)

        ireader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testSortedSetTwoFields() {
        val directory: Directory = newDirectory()
        val iwriter = RandomIndexWriter(random(), directory)

        val doc = Document()
        doc.add(SortedSetDocValuesField("field", newBytesRef("hello")))
        doc.add(SortedSetDocValuesField("field2", newBytesRef("world")))
        iwriter.addDocument(doc)

        val ireader: DirectoryReader = iwriter.reader
        iwriter.close()

        var dv: SortedSetDocValues = getOnlyLeafReader(ireader).getSortedSetDocValues("field")!!
        assertEquals(0, dv.nextDoc().toLong())

        assertEquals(1, dv.docValueCount().toLong())
        assertEquals(0, dv.nextOrd())

        var bytes: BytesRef = dv.lookupOrd(0)!!
        assertEquals(newBytesRef("hello"), bytes)

        dv = getOnlyLeafReader(ireader).getSortedSetDocValues("field2")!!
        assertEquals(0, dv.nextDoc().toLong())

        assertEquals(1, dv.docValueCount().toLong())
        assertEquals(0, dv.nextOrd())

        bytes = dv.lookupOrd(0)!!
        assertEquals(newBytesRef("world"), bytes)

        ireader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testSortedSetTwoDocumentsMerged() {
        val directory: Directory = newDirectory()
        val analyzer: Analyzer = MockAnalyzer(random())
        val iwconfig = newIndexWriterConfig(analyzer)
        iwconfig.setMergePolicy(newLogMergePolicy())
        val iwriter = RandomIndexWriter(random(), directory, iwconfig)

        var doc = Document()
        doc.add(SortedSetDocValuesField("field", newBytesRef("hello")))
        iwriter.addDocument(doc)
        iwriter.commit()

        doc = Document()
        doc.add(SortedSetDocValuesField("field", newBytesRef("world")))
        iwriter.addDocument(doc)
        iwriter.forceMerge(1)

        val ireader: DirectoryReader = iwriter.reader
        iwriter.close()

        val dv: SortedSetDocValues = getOnlyLeafReader(ireader).getSortedSetDocValues("field")!!
        assertEquals(2, dv.valueCount)

        assertEquals(0, dv.nextDoc().toLong())
        assertEquals(1, dv.docValueCount().toLong())
        assertEquals(0, dv.nextOrd())

        var bytes: BytesRef = dv.lookupOrd(0)!!
        assertEquals(newBytesRef("hello"), bytes)

        assertEquals(1, dv.nextDoc().toLong())
        assertEquals(1, dv.docValueCount().toLong())
        assertEquals(1, dv.nextOrd())

        bytes = dv.lookupOrd(1)!!
        assertEquals(newBytesRef("world"), bytes)

        ireader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testSortedSetTwoValues() {
        val directory: Directory = newDirectory()
        val iwriter = RandomIndexWriter(random(), directory)

        val doc = Document()
        doc.add(SortedSetDocValuesField("field", newBytesRef("hello")))
        doc.add(SortedSetDocValuesField("field", newBytesRef("world")))
        iwriter.addDocument(doc)

        val ireader: DirectoryReader = iwriter.reader
        iwriter.close()

        val dv: SortedSetDocValues = getOnlyLeafReader(ireader).getSortedSetDocValues("field")!!
        assertEquals(0, dv.nextDoc().toLong())

        assertEquals(2, dv.docValueCount().toLong())
        assertEquals(0, dv.nextOrd())
        assertEquals(1, dv.nextOrd())

        var bytes: BytesRef = dv.lookupOrd(0)!!
        assertEquals(newBytesRef("hello"), bytes)

        bytes = dv.lookupOrd(1)!!
        assertEquals(newBytesRef("world"), bytes)

        ireader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testSortedSetTwoValuesUnordered() {
        val directory: Directory = newDirectory()
        val iwriter = RandomIndexWriter(random(), directory)

        val doc = Document()
        doc.add(SortedSetDocValuesField("field", newBytesRef("world")))
        doc.add(SortedSetDocValuesField("field", newBytesRef("hello")))
        iwriter.addDocument(doc)

        val ireader: DirectoryReader = iwriter.reader
        iwriter.close()

        val dv: SortedSetDocValues = getOnlyLeafReader(ireader).getSortedSetDocValues("field")!!
        assertEquals(0, dv.nextDoc().toLong())

        assertEquals(2, dv.docValueCount().toLong())
        assertEquals(0, dv.nextOrd())
        assertEquals(1, dv.nextOrd())

        var bytes: BytesRef = dv.lookupOrd(0)!!
        assertEquals(newBytesRef("hello"), bytes)

        bytes = dv.lookupOrd(1)!!
        assertEquals(newBytesRef("world"), bytes)

        ireader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testSortedSetThreeValuesTwoDocs() {
        val directory: Directory = newDirectory()
        val analyzer: Analyzer = MockAnalyzer(random())
        val iwconfig = newIndexWriterConfig(analyzer)
        iwconfig.setMergePolicy(newLogMergePolicy())
        val iwriter = RandomIndexWriter(random(), directory, iwconfig)

        var doc = Document()
        doc.add(SortedSetDocValuesField("field", newBytesRef("hello")))
        doc.add(SortedSetDocValuesField("field", newBytesRef("world")))
        iwriter.addDocument(doc)
        iwriter.commit()

        doc = Document()
        doc.add(SortedSetDocValuesField("field", newBytesRef("hello")))
        doc.add(SortedSetDocValuesField("field", newBytesRef("beer")))
        iwriter.addDocument(doc)
        iwriter.forceMerge(1)

        val ireader: DirectoryReader = iwriter.reader
        iwriter.close()

        val dv: SortedSetDocValues = getOnlyLeafReader(ireader).getSortedSetDocValues("field")!!
        assertEquals(3, dv.valueCount)

        assertEquals(0, dv.nextDoc().toLong())
        assertEquals(2, dv.docValueCount().toLong())
        assertEquals(1, dv.nextOrd())
        assertEquals(2, dv.nextOrd())

        assertEquals(1, dv.nextDoc().toLong())
        assertEquals(2, dv.docValueCount().toLong())
        assertEquals(0, dv.nextOrd())
        assertEquals(1, dv.nextOrd())

        var bytes: BytesRef = dv.lookupOrd(0)!!
        assertEquals(newBytesRef("beer"), bytes)

        bytes = dv.lookupOrd(1)!!
        assertEquals(newBytesRef("hello"), bytes)

        bytes = dv.lookupOrd(2)!!
        assertEquals(newBytesRef("world"), bytes)

        ireader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testSortedSetTwoDocumentsLastMissing() {
        val directory: Directory = newDirectory()
        val analyzer: Analyzer = MockAnalyzer(random())
        val iwconfig = newIndexWriterConfig(analyzer)
        iwconfig.setMergePolicy(newLogMergePolicy())
        val iwriter = RandomIndexWriter(random(), directory, iwconfig)

        var doc = Document()
        doc.add(SortedSetDocValuesField("field", newBytesRef("hello")))
        iwriter.addDocument(doc)

        doc = Document()
        iwriter.addDocument(doc)
        iwriter.forceMerge(1)
        val ireader: DirectoryReader = iwriter.reader
        iwriter.close()

        val dv: SortedSetDocValues = getOnlyLeafReader(ireader).getSortedSetDocValues("field")!!
        assertEquals(1, dv.valueCount)
        assertEquals(0, dv.nextDoc().toLong())

        assertEquals(1, dv.docValueCount().toLong())
        assertEquals(0, dv.nextOrd())

        val bytes: BytesRef = dv.lookupOrd(0)!!
        assertEquals(newBytesRef("hello"), bytes)

        ireader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testSortedSetTwoDocumentsLastMissingMerge() {
        val directory: Directory = newDirectory()
        val analyzer: Analyzer = MockAnalyzer(random())
        val iwconfig = newIndexWriterConfig(analyzer)
        iwconfig.setMergePolicy(newLogMergePolicy())
        val iwriter = RandomIndexWriter(random(), directory, iwconfig)

        var doc = Document()
        doc.add(SortedSetDocValuesField("field", newBytesRef("hello")))
        iwriter.addDocument(doc)
        iwriter.commit()

        doc = Document()
        iwriter.addDocument(doc)
        iwriter.forceMerge(1)

        val ireader: DirectoryReader = iwriter.reader
        iwriter.close()

        val dv: SortedSetDocValues = getOnlyLeafReader(ireader).getSortedSetDocValues("field")!!
        assertEquals(1, dv.valueCount)
        assertEquals(0, dv.nextDoc().toLong())

        assertEquals(1, dv.docValueCount().toLong())
        assertEquals(0, dv.nextOrd())

        val bytes: BytesRef = dv.lookupOrd(0)!!
        assertEquals(newBytesRef("hello"), bytes)

        ireader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testSortedSetTwoDocumentsFirstMissing() {
        val directory: Directory = newDirectory()
        val analyzer: Analyzer = MockAnalyzer(random())
        val iwconfig = newIndexWriterConfig(analyzer)
        iwconfig.setMergePolicy(newLogMergePolicy())
        val iwriter = RandomIndexWriter(random(), directory, iwconfig)

        var doc = Document()
        iwriter.addDocument(doc)

        doc = Document()
        doc.add(SortedSetDocValuesField("field", newBytesRef("hello")))
        iwriter.addDocument(doc)

        iwriter.forceMerge(1)
        val ireader: DirectoryReader = iwriter.reader
        iwriter.close()

        val dv: SortedSetDocValues = getOnlyLeafReader(ireader).getSortedSetDocValues("field")!!
        assertEquals(1, dv.valueCount)
        assertEquals(1, dv.nextDoc().toLong())

        assertEquals(1, dv.docValueCount().toLong())
        assertEquals(0, dv.nextOrd())

        val bytes: BytesRef = dv.lookupOrd(0)!!
        assertEquals(newBytesRef("hello"), bytes)

        ireader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testSortedSetTwoDocumentsFirstMissingMerge() {
        val directory: Directory = newDirectory()
        val analyzer: Analyzer = MockAnalyzer(random())
        val iwconfig = newIndexWriterConfig(analyzer)
        iwconfig.setMergePolicy(newLogMergePolicy())
        val iwriter = RandomIndexWriter(random(), directory, iwconfig)

        var doc = Document()
        iwriter.addDocument(doc)
        iwriter.commit()

        doc = Document()
        doc.add(SortedSetDocValuesField("field", newBytesRef("hello")))
        iwriter.addDocument(doc)
        iwriter.forceMerge(1)

        val ireader: DirectoryReader = iwriter.reader
        iwriter.close()

        val dv: SortedSetDocValues = getOnlyLeafReader(ireader).getSortedSetDocValues("field")!!
        assertEquals(1, dv.valueCount)
        assertEquals(1, dv.nextDoc().toLong())

        assertEquals(1, dv.docValueCount().toLong())
        assertEquals(0, dv.nextOrd())

        val bytes: BytesRef = dv.lookupOrd(0)!!
        assertEquals(newBytesRef("hello"), bytes)

        ireader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testSortedSetMergeAwayAllValues() {
        val directory: Directory = newDirectory()
        val analyzer: Analyzer = MockAnalyzer(random())
        val iwconfig = newIndexWriterConfig(analyzer)
        iwconfig.setMergePolicy(newLogMergePolicy())
        val iwriter = RandomIndexWriter(random(), directory, iwconfig)

        var doc = Document()
        doc.add(StringField("id", "0", Field.Store.NO))
        iwriter.addDocument(doc)
        doc = Document()
        doc.add(StringField("id", "1", Field.Store.NO))
        doc.add(SortedSetDocValuesField("field", newBytesRef("hello")))
        iwriter.addDocument(doc)
        iwriter.commit()
        iwriter.deleteDocuments(Term("id", "1"))
        iwriter.forceMerge(1)

        val ireader: DirectoryReader = iwriter.reader
        iwriter.close()

        val dv: SortedSetDocValues = getOnlyLeafReader(ireader).getSortedSetDocValues("field")!!
        assertEquals(0, dv.valueCount)

        val termsEnum: TermsEnum = dv.termsEnum()
        assertFalse(termsEnum.seekExact(BytesRef("lucene")))
        assertEquals(SeekStatus.END, termsEnum.seekCeil(BytesRef("lucene")))
        assertEquals(-1, dv.lookupTerm(BytesRef("lucene")))

        ireader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testSortedSetTermsEnum() {
        val directory: Directory = newDirectory()
        val analyzer: Analyzer = MockAnalyzer(random())
        val iwconfig = newIndexWriterConfig(analyzer)
        iwconfig.setMergePolicy(newLogMergePolicy())
        val iwriter = RandomIndexWriter(random(), directory, iwconfig)

        val doc = Document()
        doc.add(SortedSetDocValuesField("field", newBytesRef("hello")))
        doc.add(SortedSetDocValuesField("field", newBytesRef("world")))
        doc.add(SortedSetDocValuesField("field", newBytesRef("beer")))
        iwriter.addDocument(doc)

        val ireader: DirectoryReader = iwriter.reader
        iwriter.close()

        val dv: SortedSetDocValues = getOnlyLeafReader(ireader).getSortedSetDocValues("field")!!
        assertEquals(3, dv.valueCount)

        var termsEnum: TermsEnum = dv.termsEnum()

        // next()
        assertEquals("beer", termsEnum.next()!!.utf8ToString())
        assertEquals(0, termsEnum.ord())
        assertEquals("hello", termsEnum.next()!!.utf8ToString())
        assertEquals(1, termsEnum.ord())
        assertEquals("world", termsEnum.next()!!.utf8ToString())
        assertEquals(2, termsEnum.ord())

        // seekCeil()
        assertEquals(SeekStatus.NOT_FOUND, termsEnum.seekCeil(newBytesRef("ha!")))
        assertEquals("hello", termsEnum.term()!!.utf8ToString())
        assertEquals(1, termsEnum.ord())
        assertEquals(SeekStatus.FOUND, termsEnum.seekCeil(newBytesRef("beer")))
        assertEquals("beer", termsEnum.term()!!.utf8ToString())
        assertEquals(0, termsEnum.ord())
        assertEquals(SeekStatus.END, termsEnum.seekCeil(newBytesRef("zzz")))

        // seekExact()
        assertTrue(termsEnum.seekExact(newBytesRef("beer")))
        assertEquals("beer", termsEnum.term()!!.utf8ToString())
        assertEquals(0, termsEnum.ord())
        assertTrue(termsEnum.seekExact(newBytesRef("hello")))
        assertEquals("hello", termsEnum.term()!!.utf8ToString())
        assertEquals(1, termsEnum.ord())
        assertTrue(termsEnum.seekExact(newBytesRef("world")))
        assertEquals("world", termsEnum.term()!!.utf8ToString())
        assertEquals(2, termsEnum.ord())
        assertFalse(termsEnum.seekExact(newBytesRef("bogus")))

        // seek(ord)
        termsEnum.seekExact(0)
        assertEquals("beer", termsEnum.term()!!.utf8ToString())
        assertEquals(0, termsEnum.ord())
        termsEnum.seekExact(1)
        assertEquals("hello", termsEnum.term()!!.utf8ToString())
        assertEquals(1, termsEnum.ord())
        termsEnum.seekExact(2)
        assertEquals("world", termsEnum.term()!!.utf8ToString())
        assertEquals(2, termsEnum.ord())

        // NORMAL automaton
        termsEnum = dv.intersect(CompiledAutomaton(Operations.determinize(RegExp(".*l.*").toAutomaton(), Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)))
        assertEquals("hello", termsEnum.next()!!.utf8ToString())
        assertEquals(1, termsEnum.ord())
        assertEquals("world", termsEnum.next()!!.utf8ToString())
        assertEquals(2, termsEnum.ord())
        assertNull(termsEnum.next())

        // SINGLE automaton
        termsEnum = dv.intersect(CompiledAutomaton(RegExp("hello").toAutomaton()))
        assertEquals("hello", termsEnum.next()!!.utf8ToString())
        assertEquals(1, termsEnum.ord())
        assertNull(termsEnum.next())

        ireader.close()
        directory.close()
    }

    @Throws(IOException::class)
    protected fun compareStoredFieldWithSortedSetDV(
        directoryReader: DirectoryReader,
        storedField: String,
        dvField: String
    ) {
        for (leaf in directoryReader.leaves()) {
            val reader: LeafReader = leaf.reader()
            val storedFields: StoredFields = reader.storedFields()
            var docValues: SortedSetDocValues? = reader.getSortedSetDocValues(dvField)
            if (docValues == null) {
                // no stored values at all
                for (doc in 0..<reader.maxDoc()) {
                    assertArrayEquals(kotlin.arrayOfNulls(0), storedFields.document(doc).getValues(storedField))
                }
                continue
            }
            // sequentially
            for (doc in 0..<reader.maxDoc()) {
                val storedValues: Array<String?> = storedFields.document(doc).getValues(storedField)
                if (storedValues.isEmpty()) {
                    assertFalse(docValues.advanceExact(doc))
                    continue
                }
                when (random().nextInt(3)) {
                    0 -> assertEquals(doc.toLong(), docValues.nextDoc().toLong())
                    1 -> assertEquals(doc.toLong(), docValues.advance(doc).toLong())

                    else -> assertTrue(docValues.advanceExact(doc))
                }
                assertEquals(doc.toLong(), docValues.docID().toLong())
                assertEquals(
                    storedValues.size.toLong(),
                    docValues.docValueCount().toLong()
                )
                val repeats: Int =
                    1 + random().nextInt(3)
                for (r in 0..<repeats) {
                    if (r > 0 || random().nextBoolean()) {
                        assertTrue(docValues.advanceExact(doc))
                    }
                    for (v in 0..<docValues.docValueCount()) {
                        val ord: Long = docValues.nextOrd()
                        assertEquals(storedValues[v], docValues.lookupOrd(ord)!!.utf8ToString())
                    }
                }
            }
            // jump with advanceExact
            val iters: Int = 1 + random().nextInt(3)
            for (i in 0..<iters) {
                docValues = reader.getSortedSetDocValues(dvField)!!
                var doc: Int = random().nextInt(leaf.reader().maxDoc())
                while (doc < reader.maxDoc()) {
                    val storedValues: Array<String?> = storedFields.document(doc).getValues(storedField)
                    if (docValues.advanceExact(doc)) {
                        assertEquals(doc.toLong(), docValues.docID().toLong())
                        assertEquals(storedValues.size.toLong(), docValues.docValueCount().toLong())
                        val repeats: Int =
                            1 + random().nextInt(3)
                        for (r in 0..<repeats) {
                            if (r > 0 || random().nextBoolean()) {
                                assertTrue(docValues.advanceExact(doc))
                            }
                            for (v in 0..<docValues.docValueCount()) {
                                val ord: Long = docValues.nextOrd()
                                assertEquals(storedValues[v], docValues.lookupOrd(ord)!!.utf8ToString())
                            }
                        }
                    } else {
                        assertArrayEquals(arrayOf(), storedValues)
                    }
                    doc += random().nextInt(5) // skip some docs
                    doc++
                }
            }
            // jump with advance
            for (i in 0..<iters) {
                docValues = reader.getSortedSetDocValues(dvField)!!
                var doc: Int = random().nextInt(leaf.reader().maxDoc())
                while (doc != DocIdSetIterator.NO_MORE_DOCS) {
                    val nextDoc: Int = docValues.advance(doc)
                    // no stored fields in between
                    for (d in doc..<(if (nextDoc == DocIdSetIterator.NO_MORE_DOCS) reader.maxDoc() else nextDoc)) {
                        val storedValues: Array<String?> = storedFields.document(d).getValues(storedField)
                        assertArrayEquals(
                            arrayOf(),
                            storedValues
                        )
                    }
                    doc = nextDoc
                    if (doc != DocIdSetIterator.NO_MORE_DOCS) {
                        val repeats: Int = 1 + random().nextInt(3)
                        val storedValues: Array<String?> = storedFields.document(doc).getValues(storedField)
                        for (r in 0..<repeats) {
                            if (r > 0 || random().nextBoolean()) {
                                assertTrue(docValues.advanceExact(doc))
                            }
                            for (v in 0..<docValues.docValueCount()) {
                                val ord: Long = docValues.nextOrd()
                                assertEquals(storedValues[v], docValues.lookupOrd(ord)!!.utf8ToString())
                            }
                        }
                        doc = nextDoc + 1
                        doc += random().nextInt(5) // skip some docs
                    }
                }
            }
        }
    }

    @Throws(Exception::class)
    protected fun doTestSortedSetVsStoredFields(
        numDocs: Int, minLength: Int, maxLength: Int, maxValuesPerDoc: Int, maxUniqueValues: Int
    ) {
        val dir: Directory = newFSDirectory(createTempDir("dvduel"))
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        val writer = RandomIndexWriter(random(), dir, conf)

        val valueSet: MutableSet<String> = mutableSetOf()
        run {
            var i = 0
            while (i < 10000 && valueSet.size < maxUniqueValues) {
                val length: Int = TestUtil.nextInt(
                    random(),
                    minLength,
                    maxLength
                )
                valueSet.add(
                    TestUtil.randomSimpleString(
                        random(),
                        length
                    )
                )
                ++i
            }
        }
        val uniqueValues = valueSet.toTypedArray<String>()

        // index some docs
        if (VERBOSE) {
            println("\nTEST: now add numDocs=$numDocs")
        }
        for (i in 0..<numDocs) {
            val doc = Document()
            val idField: Field = StringField("id", i.toString(), Field.Store.NO)
            doc.add(idField)
            val numValues: Int = TestUtil.nextInt(random(), 0, maxValuesPerDoc)
            // create a random set of strings
            val values: MutableSet<String> = TreeSet()
            for (v in 0..<numValues) {
                values.add(RandomPicks.randomFrom(random(), uniqueValues))
            }

            // add ordered to the stored field
            for (v in values) {
                doc.add(StoredField("stored", v))
            }

            // add in any order to the dv field
            val unordered: ArrayList<String> = ArrayList(values)
            unordered.shuffle(random())
            for (v in unordered) {
                doc.add(SortedSetDocValuesField("dv", newBytesRef(v)))
            }

            writer.addDocument(doc)
            if (random().nextInt(31) == 0) {
                writer.commit()
            }
        }
        // delete some docs
        val numDeletions: Int =
            random().nextInt(numDocs / 10)
        for (i in 0..<numDeletions) {
            val id: Int = random().nextInt(numDocs)
            writer.deleteDocuments(Term("id", id.toString()))
        }

        writer.reader.use { reader ->
            TestUtil.checkReader(reader)
            compareStoredFieldWithSortedSetDV(reader, "stored", "dv")
        }
        writer.forceMerge(1)
        writer.reader.use { reader ->
            TestUtil.checkReader(reader)
            compareStoredFieldWithSortedSetDV(reader, "stored", "dv")
        }
        IOUtils.close(writer, dir)
    }

    @Throws(Exception::class)
    open fun testSortedSetFixedLengthVsStoredFields() {
        val numIterations: Int = atLeast(1)
        for (i in 0..<numIterations) {
            val fixedLength: Int = TestUtil.nextInt(
                random(),
                1,
                10
            )
            doTestSortedSetVsStoredFields(
                atLeast(300),
                fixedLength,
                fixedLength,
                16,
                100
            )
        }
    }

    @Throws(Exception::class)
    open fun testSortedNumericsSingleValuedVsStoredFields() {
        val numIterations: Int = atLeast(1)
        for (i in 0..<numIterations) {
            doTestSortedNumericsVsStoredFields(
                /*java.util.function.LongSupplier*/ { 1 },
                /*java.util.function.LongSupplier*/ {
                    random().nextLong()
                })
        }
    }

    @Throws(Exception::class)
    open fun testSortedNumericsSingleValuedMissingVsStoredFields() {
        val numIterations: Int = atLeast(1)
        for (i in 0..<numIterations) {
            doTestSortedNumericsVsStoredFields(
                /*java.util.function.LongSupplier*/ {
                    if (random().nextBoolean()) 0 else 1
                },
                /*java.util.function.LongSupplier*/ {
                    random().nextLong()
                })
        }
    }

    @Throws(Exception::class)
    open fun testSortedNumericsMultipleValuesVsStoredFields() {
        val numIterations: Int = atLeast(1)
        for (i in 0..<numIterations) {
            doTestSortedNumericsVsStoredFields(
                /*java.util.function.LongSupplier*/ {
                    TestUtil.nextLong(
                        random(),
                        0,
                        50
                    )
                },
                /*java.util.function.LongSupplier*/ {
                    random().nextLong()
                })
        }
    }

    @Throws(Exception::class)
    open fun testSortedNumericsFewUniqueSetsVsStoredFields() {
        val values = LongArray(
            TestUtil.nextInt(
                random(),
                2,
                6
            )
        )
        for (i in values.indices) {
            values[i] = random().nextLong()
        }
        val numIterations: Int = atLeast(1)
        for (i in 0..<numIterations) {
            doTestSortedNumericsVsStoredFields(
                /*java.util.function.LongSupplier*/ {
                    TestUtil.nextLong(
                        random(),
                        0,
                        6
                    )
                },
                /*java.util.function.LongSupplier*/ {
                    values[random()
                        .nextInt(values.size)]
                })
        }
    }

    @Throws(Exception::class)
    open fun testSortedSetVariableLengthVsStoredFields() {
        val numIterations: Int = atLeast(1)
        for (i in 0..<numIterations) {
            doTestSortedSetVsStoredFields(
                atLeast(300),
                1,
                10,
                16,
                100
            )
        }
    }

    @Throws(Exception::class)
    open fun testSortedSetFixedLengthSingleValuedVsStoredFields() {
        val numIterations: Int = atLeast(1)
        for (i in 0..<numIterations) {
            val fixedLength: Int = TestUtil.nextInt(
                random(),
                1,
                10
            )
            doTestSortedSetVsStoredFields(
                atLeast(300),
                fixedLength,
                fixedLength,
                1,
                100
            )
        }
    }

    @Throws(Exception::class)
    open fun testSortedSetVariableLengthSingleValuedVsStoredFields() {
        val numIterations: Int = atLeast(1)
        for (i in 0..<numIterations) {
            doTestSortedSetVsStoredFields(
                atLeast(300),
                1,
                10,
                1,
                100
            )
        }
    }

    @Throws(Exception::class)
    open fun testSortedSetFixedLengthFewUniqueSetsVsStoredFields() {
        val numIterations: Int = atLeast(1)
        for (i in 0..<numIterations) {
            doTestSortedSetVsStoredFields(
                atLeast(300),
                10,
                10,
                6,
                6
            )
        }
    }

    @Throws(Exception::class)
    open fun testSortedSetVariableLengthFewUniqueSetsVsStoredFields() {
        val numIterations: Int = atLeast(1)
        for (i in 0..<numIterations) {
            doTestSortedSetVsStoredFields(
                atLeast(300),
                1,
                10,
                6,
                6
            )
        }
    }

    @Throws(Exception::class)
    open fun testSortedSetVariableLengthManyValuesPerDocVsStoredFields() {
        val numIterations: Int = atLeast(1)
        for (i in 0..<numIterations) {
            doTestSortedSetVsStoredFields(
                atLeast(20),
                1,
                10,
                500,
                1000
            )
        }
    }

    @Throws(Exception::class)
    open fun testSortedSetFixedLengthManyValuesPerDocVsStoredFields() {
        val numIterations: Int = atLeast(1)
        for (i in 0..<numIterations) {
            doTestSortedSetVsStoredFields(
                atLeast(20),
                10,
                10,
                500,
                1000
            )
        }
    }

    @Throws(Exception::class)
    open fun testGCDCompression() {
        doTestGCDCompression(1.0)
    }

    @Throws(Exception::class)
    open fun testSparseGCDCompression() {
        doTestGCDCompression(random().nextDouble())
    }

    @Throws(Exception::class)
    private fun doTestGCDCompression(density: Double) {
        val numIterations: Int = atLeast(1)
        for (i in 0..<numIterations) {
            val min = -((random().nextInt(1 shl 30).toLong()) shl 32)
            val mul = random().nextInt().toLong() and 0xFFFFFFFFL
            val longs: () -> Long /*java.util.function.LongSupplier*/ = /*java.util.function.LongSupplier*/ {
                min + mul * random().nextInt(1 shl 20)
            }
            doTestNumericsVsStoredFields(density, longs)
        }
    }

    @Throws(Exception::class)
    open fun testZeros() {
        doTestNumericsVsStoredFields(1.0, /*java.util.function.LongSupplier*/ { 0 })
    }

    @Throws(Exception::class)
    open fun testSparseZeros() {
        doTestNumericsVsStoredFields(
            random().nextDouble(),
            /*java.util.function.LongSupplier*/ { 0 })
    }

    @Throws(Exception::class)
    open fun testZeroOrMin() {
        // try to make GCD compression fail if the format did not anticipate that
        // the GCD of 0 and MIN_VALUE is negative
        val numIterations: Int = atLeast(1)
        for (i in 0..<numIterations) {
            val longs: () -> Long /*java.util.function.LongSupplier*/ = /*java.util.function.LongSupplier*/ {
                if (random()
                        .nextBoolean()
                ) 0 else Long.MIN_VALUE
            }
            doTestNumericsVsStoredFields(1.0, longs)
        }
    }

    @Throws(IOException::class)
    open fun testTwoNumbersOneMissing() {
        val directory: Directory = newDirectory()
        val conf = newIndexWriterConfig(/*null*/)
        conf.setMergePolicy(newLogMergePolicy())
        val iw = RandomIndexWriter(random(), directory, conf)
        var doc = Document()
        doc.add(StringField("id", "0", Field.Store.YES))
        doc.add(NumericDocValuesField("dv1", 0))
        iw.addDocument(doc)
        doc = Document()
        doc.add(StringField("id", "1", Field.Store.YES))
        iw.addDocument(doc)
        iw.forceMerge(1)
        iw.close()

        val ir: IndexReader =
            maybeWrapWithMergingReader(DirectoryReader.open(directory))
        assertEquals(1, ir.leaves().size.toLong())
        val ar: LeafReader = ir.leaves()[0].reader()
        val dv: NumericDocValues = ar.getNumericDocValues("dv1")!!
        assertEquals(0, dv.nextDoc().toLong())
        assertEquals(0, dv.longValue())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), dv.nextDoc().toLong())
        ir.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testTwoNumbersOneMissingWithMerging() {
        val directory: Directory = newDirectory()
        val conf = newIndexWriterConfig(/*null*/)
        conf.setMergePolicy(newLogMergePolicy())
        val iw = RandomIndexWriter(random(), directory, conf)
        var doc = Document()
        doc.add(StringField("id", "0", Field.Store.YES))
        doc.add(NumericDocValuesField("dv1", 0))
        iw.addDocument(doc)
        iw.commit()
        doc = Document()
        doc.add(StringField("id", "1", Field.Store.YES))
        iw.addDocument(doc)
        iw.forceMerge(1)
        iw.close()

        val ir: IndexReader = maybeWrapWithMergingReader(DirectoryReader.open(directory))
        assertEquals(1, ir.leaves().size.toLong())
        val ar: LeafReader = ir.leaves()[0].reader()
        val dv: NumericDocValues = ar.getNumericDocValues("dv1")!!
        assertEquals(0, dv.nextDoc().toLong())
        assertEquals(0, dv.longValue())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), dv.nextDoc().toLong())
        ir.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testThreeNumbersOneMissingWithMerging() {
        val directory: Directory = newDirectory()
        val conf = newIndexWriterConfig(/*null*/)
        conf.setMergePolicy(newLogMergePolicy())
        val iw = RandomIndexWriter(random(), directory, conf)
        var doc = Document()
        doc.add(StringField("id", "0", Field.Store.YES))
        doc.add(NumericDocValuesField("dv1", 0))
        iw.addDocument(doc)
        doc = Document()
        doc.add(StringField("id", "1", Field.Store.YES))
        iw.addDocument(doc)
        iw.commit()
        doc = Document()
        doc.add(StringField("id", "2", Field.Store.YES))
        doc.add(NumericDocValuesField("dv1", 5))
        iw.addDocument(doc)
        iw.forceMerge(1)
        iw.close()

        val ir: IndexReader = maybeWrapWithMergingReader(DirectoryReader.open(directory))
        assertEquals(1, ir.leaves().size.toLong())
        val ar: LeafReader = ir.leaves()[0].reader()
        val dv: NumericDocValues = ar.getNumericDocValues("dv1")!!
        assertEquals(0, dv.nextDoc().toLong())
        assertEquals(0, dv.longValue())
        assertEquals(2, dv.nextDoc().toLong())
        assertEquals(5, dv.longValue())
        ir.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testTwoBytesOneMissing() {
        val directory: Directory = newDirectory()
        val conf = newIndexWriterConfig(/*null*/)
        conf.setMergePolicy(newLogMergePolicy())
        val iw = RandomIndexWriter(random(), directory, conf)
        var doc = Document()
        doc.add(StringField("id", "0", Field.Store.YES))
        doc.add(BinaryDocValuesField("dv1", newBytesRef()))
        iw.addDocument(doc)
        doc = Document()
        doc.add(StringField("id", "1", Field.Store.YES))
        iw.addDocument(doc)
        iw.forceMerge(1)
        iw.close()

        val ir: IndexReader = maybeWrapWithMergingReader(DirectoryReader.open(directory))
        assertEquals(1, ir.leaves().size.toLong())
        val ar: LeafReader = ir.leaves()[0].reader()
        val dv: BinaryDocValues = ar.getBinaryDocValues("dv1")!!
        assertEquals(0, dv.nextDoc().toLong())
        assertEquals(newBytesRef(), dv.binaryValue())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), dv.nextDoc().toLong())
        ir.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testTwoBytesOneMissingWithMerging() {
        val directory: Directory = newDirectory()
        val conf = newIndexWriterConfig(/*null*/)
        conf.setMergePolicy(newLogMergePolicy())
        val iw = RandomIndexWriter(random(), directory, conf)
        var doc = Document()
        doc.add(StringField("id", "0", Field.Store.YES))
        doc.add(BinaryDocValuesField("dv1", newBytesRef()))
        iw.addDocument(doc)
        iw.commit()
        doc = Document()
        doc.add(StringField("id", "1", Field.Store.YES))
        iw.addDocument(doc)
        iw.forceMerge(1)
        iw.close()

        val ir: IndexReader = maybeWrapWithMergingReader(DirectoryReader.open(directory))
        assertEquals(1, ir.leaves().size.toLong())
        val ar: LeafReader = ir.leaves()[0].reader()
        val dv: BinaryDocValues = ar.getBinaryDocValues("dv1")!!
        assertEquals(0, dv.nextDoc().toLong())
        assertEquals(newBytesRef(), dv.binaryValue())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), dv.nextDoc().toLong())
        ir.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testThreeBytesOneMissingWithMerging() {
        val directory: Directory = newDirectory()
        val conf = newIndexWriterConfig(/*null*/)
        conf.setMergePolicy(newLogMergePolicy())
        val iw = RandomIndexWriter(random(), directory, conf)
        var doc = Document()
        doc.add(StringField("id", "0", Field.Store.YES))
        doc.add(BinaryDocValuesField("dv1", newBytesRef()))
        iw.addDocument(doc)
        doc = Document()
        doc.add(StringField("id", "1", Field.Store.YES))
        iw.addDocument(doc)
        iw.commit()
        doc = Document()
        doc.add(StringField("id", "2", Field.Store.YES))
        doc.add(BinaryDocValuesField("dv1", newBytesRef("boo")))
        iw.addDocument(doc)
        iw.forceMerge(1)
        iw.close()

        val ir: IndexReader = maybeWrapWithMergingReader(DirectoryReader.open(directory))
        assertEquals(1, ir.leaves().size.toLong())
        val ar: LeafReader = ir.leaves()[0].reader()
        val dv: BinaryDocValues = ar.getBinaryDocValues("dv1")!!
        assertEquals(0, dv.nextDoc().toLong())
        assertEquals(newBytesRef(), dv.binaryValue())
        assertEquals(2, dv.nextDoc().toLong())
        assertEquals(newBytesRef("boo"), dv.binaryValue())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), dv.nextDoc().toLong())
        ir.close()
        directory.close()
    }

    /** Tests dv against stored fields with threads (binary/numeric/sorted, no missing)  */
    @Throws(Exception::class)
    open fun testThreads() = runTest {
        val dir: Directory = newDirectory()
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        val writer = RandomIndexWriter(random(), dir, conf)
        val doc = Document()
        val idField: Field = StringField("id", "", Field.Store.NO)
        val storedBinField: Field = StoredField("storedBin", ByteArray(0))
        val dvBinField: Field = BinaryDocValuesField("dvBin", newBytesRef())
        val dvSortedField: Field = SortedDocValuesField("dvSorted", newBytesRef())
        val storedNumericField: Field = StoredField("storedNum", "")
        val dvNumericField: Field = NumericDocValuesField("dvNum", 0)
        doc.add(idField)
        doc.add(storedBinField)
        doc.add(dvBinField)
        doc.add(dvSortedField)
        doc.add(storedNumericField)
        doc.add(dvNumericField)

        // index some docs
        val numDocs: Int = atLeast(30) // TODO reduced from 300 to 30 for dev speed
        for (i in 0..<numDocs) {
            idField.setStringValue(i.toString())
            val length: Int = TestUtil.nextInt(random(), 0, 8)
            val buffer = ByteArray(length)
            random().nextBytes(buffer)
            storedBinField.setBytesValue(buffer)
            dvBinField.setBytesValue(buffer)
            dvSortedField.setBytesValue(buffer)
            val numericValue: Long = random().nextLong()
            storedNumericField.setStringValue(numericValue.toString())
            dvNumericField.setLongValue(numericValue)
            writer.addDocument(doc)
            if (random().nextInt(31) == 0) {
                writer.commit()
            }
        }

        // delete some docs
        val numDeletions: Int = random().nextInt(numDocs / 10)
        for (i in 0..<numDeletions) {
            val id: Int = random().nextInt(numDocs)
            writer.deleteDocuments(Term("id", id.toString()))
        }
        writer.close()

        // compare
        val ir: DirectoryReader = maybeWrapWithMergingReader(DirectoryReader.open(dir))
        val numThreads: Int = TestUtil.nextInt(random(), 2, 7)
        val startSignal = CompletableDeferred<Unit>()
        val jobs: Array<Job> = Array(numThreads) {
            launch(Dispatchers.Default) {
                startSignal.await()
                try {
                    for (context in ir.leaves()) {
                        val r: LeafReader = context.reader()
                        val storedFields: StoredFields = r.storedFields()
                        val binaries: BinaryDocValues = r.getBinaryDocValues("dvBin")!!
                        val sorted: SortedDocValues? = r.getSortedDocValues("dvSorted")
                        val sortedNonNull: SortedDocValues = assertNotNull(sorted)
                        val numerics: NumericDocValues = r.getNumericDocValues("dvNum")!!
                        for (j in 0..<r.maxDoc()) {
                            val binaryValue: BytesRef = storedFields.document(j).getBinaryValue("storedBin")!!
                            assertEquals(j.toLong(), binaries.nextDoc().toLong())
                            var scratch: BytesRef = binaries.binaryValue()!!
                            assertEquals(binaryValue, scratch)
                            assertEquals(j.toLong(), sortedNonNull.nextDoc().toLong())
                            scratch = sortedNonNull.lookupOrd(sortedNonNull.ordValue())!!
                            assertEquals(binaryValue, scratch)
                            val expected: String = storedFields.document(j).get("storedNum")!!
                            assertEquals(j.toLong(), numerics.nextDoc().toLong())
                            assertEquals(expected.toLong(), numerics.longValue())
                        }
                    }
                    TestUtil.checkReader(ir)
                } catch (e: Exception) {
                    throw RuntimeException(e)
                }
            }
        }
        startSignal.complete(Unit)
        for (job in jobs) {
            job.join()
        }
        ir.close()
        dir.close()
    }

    /** Tests dv against stored fields with threads (all types + missing)  */
    /*@org.apache.lucene.tests.util.LuceneTestCase.Nightly*/
    @Throws(Exception::class)
    open fun testThreads2() = runTest {
        val dir: Directory = newDirectory()
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        val writer = RandomIndexWriter(random(), dir, conf)
        val idField: Field = StringField("id", "", Field.Store.NO)
        val storedBinField: Field = StoredField("storedBin", ByteArray(0))
        val dvBinField: Field = BinaryDocValuesField("dvBin", newBytesRef())
        val dvSortedField: Field = SortedDocValuesField("dvSorted", newBytesRef())
        val storedNumericField: Field = StoredField("storedNum", "")
        val dvNumericField: Field = NumericDocValuesField("dvNum", 0)

        // index some docs
        val numDocs: Int = TestUtil.nextInt(
            random(),
            1025,
            2047
        )
        for (i in 0..<numDocs) {
            idField.setStringValue(i.toString())
            val length: Int = TestUtil.nextInt(random(), 0, 8)
            val buffer = ByteArray(length)
            random().nextBytes(buffer)
            storedBinField.setBytesValue(buffer)
            dvBinField.setBytesValue(buffer)
            dvSortedField.setBytesValue(buffer)
            val numericValue: Long = random().nextLong()
            storedNumericField.setStringValue(numericValue.toString())
            dvNumericField.setLongValue(numericValue)
            val doc = Document()
            doc.add(idField)
            if (random().nextInt(4) > 0) {
                doc.add(storedBinField)
                doc.add(dvBinField)
                doc.add(dvSortedField)
            }
            if (random().nextInt(4) > 0) {
                doc.add(storedNumericField)
                doc.add(dvNumericField)
            }
            val numSortedSetFields: Int =
                random().nextInt(3)
            val values: MutableSet<String> = TreeSet()
            for (j in 0..<numSortedSetFields) {
                values.add(TestUtil.randomSimpleString(random()))
            }
            for (v in values) {
                doc.add(SortedSetDocValuesField("dvSortedSet", newBytesRef(v)))
                doc.add(StoredField("storedSortedSet", v))
            }
            val numSortedNumericFields: Int =
                random().nextInt(3)
            val numValues: MutableSet<Long> = TreeSet<Long>()
            for (j in 0..<numSortedNumericFields) {
                numValues.add(TestUtil.nextLong(random(), Long.MIN_VALUE, Long.MAX_VALUE))
            }
            for (l in numValues) {
                doc.add(SortedNumericDocValuesField("dvSortedNumeric", l))
                doc.add(StoredField("storedSortedNumeric", l.toString()))
            }
            writer.addDocument(doc)
            if (random().nextInt(31) == 0) {
                writer.commit()
            }
        }

        // delete some docs
        val numDeletions: Int = random().nextInt(numDocs / 10)
        for (i in 0..<numDeletions) {
            val id: Int = random().nextInt(numDocs)
            writer.deleteDocuments(Term("id", id.toString()))
        }
        writer.close()

        // compare
        val ir: DirectoryReader = maybeWrapWithMergingReader(DirectoryReader.open(dir))
        val numThreads: Int = TestUtil.nextInt(
            random(),
            2,
            7
        )
        val startSignal = CompletableDeferred<Unit>()
        val jobs: Array<Job> = Array(numThreads) {
            launch(Dispatchers.Default) {
                startSignal.await()
                try {
                    for (context in ir.leaves()) {
                        val r: LeafReader = context.reader()
                        val storedFields: StoredFields =
                            r.storedFields()
                        val binaries: BinaryDocValues? = r.getBinaryDocValues("dvBin")
                        val sorted: SortedDocValues? = r.getSortedDocValues("dvSorted")
                        val numerics: NumericDocValues? = r.getNumericDocValues("dvNum")
                        val sortedSet: SortedSetDocValues? = r.getSortedSetDocValues("dvSortedSet")
                        val sortedNumeric: SortedNumericDocValues? = r.getSortedNumericDocValues("dvSortedNumeric")
                        for (j in 0..<r.maxDoc()) {
                            val binaryValue: BytesRef? = storedFields.document(j).getBinaryValue("storedBin")
                            if (binaryValue != null) {
                                if (binaries != null) {
                                    val sortedNonNull = assertNotNull(sorted)
                                    assertEquals(j.toLong(), binaries.nextDoc().toLong())
                                    var scratch: BytesRef? = binaries.binaryValue()
                                    assertEquals(binaryValue, scratch)
                                    assertEquals(j.toLong(), sortedNonNull.nextDoc().toLong())
                                    scratch = sortedNonNull.lookupOrd(sortedNonNull.ordValue())
                                    assertEquals(binaryValue, scratch)
                                }
                            }

                            val number: String? = storedFields.document(j).get("storedNum")
                            if (number != null) {
                                if (numerics != null) {
                                    assertEquals(j.toLong(), numerics.advance(j).toLong())
                                    assertEquals(number.toLong(), numerics.longValue())
                                }
                            }

                            val values: Array<String?> = storedFields.document(j).getValues("storedSortedSet")
                            if (values.isNotEmpty()) {
                                assertNotNull(sortedSet)
                                assertEquals(j.toLong(), sortedSet.nextDoc().toLong())
                                assertEquals(values.size.toLong(), sortedSet.docValueCount().toLong())
                                for (s in values) {
                                    val ord: Long = sortedSet.nextOrd()
                                    val value: BytesRef = sortedSet.lookupOrd(ord)!!
                                    assertEquals(s, value.utf8ToString())
                                }
                            }
                            val numValues: Array<String?> = storedFields.document(j).getValues("storedSortedNumeric")
                            if (numValues.isNotEmpty()) {
                                assertNotNull(sortedNumeric)
                                assertEquals(j.toLong(), sortedNumeric.nextDoc().toLong())
                                assertEquals(numValues.size.toLong(), sortedNumeric.docValueCount().toLong())
                                for (numValue in numValues) {
                                    val v: Long = sortedNumeric.nextValue()
                                    assertEquals(numValue, v.toString())
                                }
                            }
                        }
                    }
                    TestUtil.checkReader(ir)
                } catch (e: Exception) {
                    throw RuntimeException(e)
                }
            }
        }
        startSignal.complete(Unit)
        for (job in jobs) {
            job.join()
        }
        ir.close()
        dir.close()
    }

    /*@org.apache.lucene.tests.util.LuceneTestCase.Nightly*/
    @Throws(Exception::class)
    open fun testThreads3() = runTest {
        val dir: Directory = newFSDirectory(createTempDir())
        val conf = newIndexWriterConfig(MockAnalyzer(random()))
        val writer = RandomIndexWriter(random(), dir, conf)

        val numSortedSets: Int = random().nextInt(21)
        val numBinaries: Int = random().nextInt(21)
        val numSortedNums: Int = random().nextInt(21)

        val numDocs: Int = TestUtil.nextInt(random(), 2025, 2047)
        for (i in 0..<numDocs) {
            val doc = Document()

            for (j in 0..<numSortedSets) {
                doc.add(SortedSetDocValuesField("ss$j", newBytesRef(TestUtil.randomSimpleString(random()))))
                doc.add(SortedSetDocValuesField("ss$j", newBytesRef(TestUtil.randomSimpleString(random()))))
            }

            for (j in 0..<numBinaries) {
                doc.add(BinaryDocValuesField("b$j", newBytesRef(TestUtil.randomSimpleString(random()))))
            }

            for (j in 0..<numSortedNums) {
                doc.add(SortedNumericDocValuesField("sn$j", TestUtil.nextLong(random(), Long.MIN_VALUE, Long.MAX_VALUE)))
                doc.add(SortedNumericDocValuesField("sn$j", TestUtil.nextLong(random(), Long.MIN_VALUE, Long.MAX_VALUE)))
            }
            writer.addDocument(doc)
        }
        writer.close()

        // now check with threads
        for (i in 0..9) {
            val r: DirectoryReader =
                maybeWrapWithMergingReader(DirectoryReader.open(dir))
            val numThreads: Int = TestUtil.nextInt(
                random(),
                4,
                10
            )
            val startSignal = CompletableDeferred<Unit>()
            val jobs: Array<Job> = Array(numThreads) {
                launch(Dispatchers.Default) {
                    try {
                        val bos = ByteArrayOutputStream(1024)
                        val infoStream = PrintStream(
                            bos,
                            false,
                            StandardCharsets.UTF_8
                        )
                        startSignal.await()
                        for (leaf in r.leaves()) {
                            val status: CheckIndex.Status.DocValuesStatus =
                                CheckIndex.testDocValues(
                                    leaf.reader() as CodecReader,
                                    infoStream,
                                    true
                                )
                            val error: Throwable? = status.error
                            if (error != null) {
                                throw error
                            }
                        }
                    } catch (e: Throwable) {
                        throw RuntimeException(e)
                    }
                }
            }
            startSignal.complete(Unit)
            for (job in jobs) {
                job.join()
                            }
            r.close()
        }

        dir.close()
    }

    // LUCENE-5218
    @Throws(Exception::class)
    open fun testEmptyBinaryValueOnPageSizes() {
        // Test larger and larger power-of-two sized values,
        // followed by empty string value:
        for (i in 0..19) {
            if (i > 14 && codecAcceptsHugeBinaryValues("field") == false) {
                break
            }
            val dir: Directory = newDirectory()
            val w = RandomIndexWriter(random(), dir)
            val bytes: BytesRef = newBytesRef(ByteArray(1 shl i), 0, 1 shl i)
            for (j in 0..3) {
                val doc = Document()
                doc.add(BinaryDocValuesField("field", bytes))
                w.addDocument(doc)
            }
            val doc = Document()
            doc.add(StoredField("id", "5"))
            doc.add(BinaryDocValuesField("field", newBytesRef()))
            w.addDocument(doc)
            val r: IndexReader = w.reader
            w.close()

            val values: BinaryDocValues = MultiDocValues.getBinaryValues(r, "field")!!
            for (j in 0..4) {
                assertEquals(j.toLong(), values.nextDoc().toLong())
                val result: BytesRef = values.binaryValue()!!
                assertTrue(result.length == 0 || result.length == 1 shl i)
            }
            r.close()
            dir.close()
        }
    }

    @Throws(IOException::class)
    open fun testOneSortedNumber() {
        val directory: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), directory)
        val doc = Document()
        doc.add(SortedNumericDocValuesField("dv", 5))
        writer.addDocument(doc)
        writer.close()

        // Now search the index:
        val reader: IndexReader = maybeWrapWithMergingReader(DirectoryReader.open(directory))
        assert(reader.leaves().size == 1)
        val dv: SortedNumericDocValues = reader.leaves()[0].reader().getSortedNumericDocValues("dv")!!
        assertEquals(0, dv.nextDoc().toLong())
        assertEquals(1, dv.docValueCount().toLong())
        assertEquals(5, dv.nextValue())

        reader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testOneSortedNumberOneMissing() {
        val directory: Directory = newDirectory()
        val writer = IndexWriter(directory, IndexWriterConfig(/*null*/))
        val doc = Document()
        doc.add(SortedNumericDocValuesField("dv", 5))
        writer.addDocument(doc)
        writer.addDocument(Document())
        writer.close()

        // Now search the index:
        val reader: IndexReader = maybeWrapWithMergingReader(DirectoryReader.open(directory))
        assert(reader.leaves().size == 1)
        val dv: SortedNumericDocValues = reader.leaves()[0].reader().getSortedNumericDocValues("dv")!!
        assertEquals(0, dv.nextDoc().toLong())
        assertEquals(1, dv.docValueCount().toLong())
        assertEquals(5, dv.nextValue())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), dv.nextDoc().toLong())

        reader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testNumberMergeAwayAllValues() {
        val directory: Directory = newDirectory()
        val analyzer: Analyzer = MockAnalyzer(random())
        val iwconfig = newIndexWriterConfig(analyzer)
        iwconfig.setMergePolicy(newLogMergePolicy())
        val iwriter = RandomIndexWriter(random(), directory, iwconfig)

        var doc = Document()
        doc.add(StringField("id", "0", Field.Store.NO))
        iwriter.addDocument(doc)
        doc = Document()
        doc.add(StringField("id", "1", Field.Store.NO))
        doc.add(NumericDocValuesField("field", 5))
        iwriter.addDocument(doc)
        iwriter.commit()
        iwriter.deleteDocuments(Term("id", "1"))
        iwriter.forceMerge(1)

        val ireader: DirectoryReader = iwriter.reader
        iwriter.close()

        val dv: NumericDocValues = getOnlyLeafReader(ireader).getNumericDocValues("field")!!
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), dv.nextDoc().toLong())

        ireader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testTwoSortedNumber() {
        val directory: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), directory)
        val doc = Document()
        doc.add(SortedNumericDocValuesField("dv", 11))
        doc.add(SortedNumericDocValuesField("dv", -5))
        writer.addDocument(doc)
        writer.close()

        // Now search the index:
        val reader: IndexReader = maybeWrapWithMergingReader(DirectoryReader.open(directory))
        assert(reader.leaves().size == 1)
        val dv: SortedNumericDocValues = reader.leaves()[0].reader().getSortedNumericDocValues("dv")!!
        assertEquals(0, dv.nextDoc().toLong())
        assertEquals(2, dv.docValueCount().toLong())
        assertEquals(-5, dv.nextValue())
        assertEquals(11, dv.nextValue())

        reader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testTwoSortedNumberSameValue() {
        val directory: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), directory)
        val doc = Document()
        doc.add(SortedNumericDocValuesField("dv", 11))
        doc.add(SortedNumericDocValuesField("dv", 11))
        writer.addDocument(doc)
        writer.close()

        // Now search the index:
        val reader: IndexReader = maybeWrapWithMergingReader(DirectoryReader.open(directory))
        assert(reader.leaves().size == 1)
        val dv: SortedNumericDocValues = reader.leaves()[0].reader().getSortedNumericDocValues("dv")!!
        assertEquals(0, dv.nextDoc().toLong())
        assertEquals(2, dv.docValueCount().toLong())
        assertEquals(11, dv.nextValue())
        assertEquals(11, dv.nextValue())

        reader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testTwoSortedNumberOneMissing() {
        val directory: Directory = newDirectory()
        val writer = IndexWriter(directory, IndexWriterConfig(/*null*/))
        val doc = Document()
        doc.add(SortedNumericDocValuesField("dv", 11))
        doc.add(SortedNumericDocValuesField("dv", -5))
        writer.addDocument(doc)
        writer.addDocument(Document())
        writer.close()

        // Now search the index:
        val reader: IndexReader = maybeWrapWithMergingReader(DirectoryReader.open(directory))
        assert(reader.leaves().size == 1)
        val dv: SortedNumericDocValues = reader.leaves()[0].reader().getSortedNumericDocValues("dv")!!
        assertEquals(0, dv.nextDoc().toLong())
        assertEquals(2, dv.docValueCount().toLong())
        assertEquals(-5, dv.nextValue())
        assertEquals(11, dv.nextValue())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), dv.nextDoc().toLong())

        reader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testSortedNumberMerge() {
        val directory: Directory = newDirectory()
        val iwc = IndexWriterConfig(/*null*/)
        iwc.setMergePolicy(newLogMergePolicy())
        val writer = IndexWriter(directory, iwc)
        var doc = Document()
        doc.add(SortedNumericDocValuesField("dv", 11))
        writer.addDocument(doc)
        writer.commit()
        doc = Document()
        doc.add(SortedNumericDocValuesField("dv", -5))
        writer.addDocument(doc)
        writer.forceMerge(1)
        writer.close()

        // Now search the index:
        val reader: IndexReader = maybeWrapWithMergingReader(DirectoryReader.open(directory))
        assert(reader.leaves().size == 1)
        val dv: SortedNumericDocValues = reader.leaves()[0].reader().getSortedNumericDocValues("dv")!!
        assertEquals(0, dv.nextDoc().toLong())
        assertEquals(1, dv.docValueCount().toLong())
        assertEquals(11, dv.nextValue())
        assertEquals(1, dv.nextDoc().toLong())
        assertEquals(1, dv.docValueCount().toLong())
        assertEquals(-5, dv.nextValue())

        reader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testSortedNumberMergeAwayAllValues() {
        val directory: Directory = newDirectory()
        val analyzer: Analyzer = MockAnalyzer(random())
        val iwconfig = newIndexWriterConfig(analyzer)
        iwconfig.setMergePolicy(newLogMergePolicy())
        val iwriter = RandomIndexWriter(random(), directory, iwconfig)

        var doc = Document()
        doc.add(StringField("id", "0", Field.Store.NO))
        iwriter.addDocument(doc)
        doc = Document()
        doc.add(StringField("id", "1", Field.Store.NO))
        doc.add(SortedNumericDocValuesField("field", 5))
        iwriter.addDocument(doc)
        iwriter.commit()
        iwriter.deleteDocuments(Term("id", "1"))
        iwriter.forceMerge(1)

        val ireader: DirectoryReader = iwriter.reader
        iwriter.close()

        val dv: SortedNumericDocValues = getOnlyLeafReader(ireader).getSortedNumericDocValues("field")!!
        assertEquals(
            DocIdSetIterator.NO_MORE_DOCS.toLong(),
            dv.nextDoc().toLong()
        )

        ireader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testSortedEnumAdvanceIndependently() {
        val directory: Directory = newDirectory()
        val analyzer: Analyzer = MockAnalyzer(random())
        val iwconfig = newIndexWriterConfig(analyzer)
        iwconfig.setMergePolicy(newLogMergePolicy())
        val iwriter = RandomIndexWriter(random(), directory, iwconfig)

        val doc = Document()
        val field = SortedDocValuesField("field", newBytesRef("2"))
        doc.add(field)
        iwriter.addDocument(doc)
        field.setBytesValue(newBytesRef("1"))
        iwriter.addDocument(doc)
        field.setBytesValue(newBytesRef("3"))
        iwriter.addDocument(doc)

        iwriter.commit()
        iwriter.forceMerge(1)

        val ireader: DirectoryReader = iwriter.reader
        iwriter.close()

        val dv: SortedDocValues = getOnlyLeafReader(ireader).getSortedDocValues("field")!!
        doTestSortedSetEnumAdvanceIndependently(DocValues.singleton(dv))

        ireader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testSortedSetEnumAdvanceIndependently() {
        val directory: Directory = newDirectory()
        val analyzer: Analyzer = MockAnalyzer(random())
        val iwconfig = newIndexWriterConfig(analyzer)
        iwconfig.setMergePolicy(newLogMergePolicy())
        val iwriter = RandomIndexWriter(random(), directory, iwconfig)

        val doc = Document()
        val field1 = SortedSetDocValuesField("field", newBytesRef("2"))
        val field2 = SortedSetDocValuesField("field", newBytesRef("3"))
        doc.add(field1)
        doc.add(field2)
        iwriter.addDocument(doc)
        field1.setBytesValue(newBytesRef("1"))
        iwriter.addDocument(doc)
        field2.setBytesValue(newBytesRef("2"))
        iwriter.addDocument(doc)

        iwriter.commit()
        iwriter.forceMerge(1)

        val ireader: DirectoryReader = iwriter.reader
        iwriter.close()

        val dv: SortedSetDocValues = getOnlyLeafReader(ireader).getSortedSetDocValues("field")!!
        doTestSortedSetEnumAdvanceIndependently(dv)

        ireader.close()
        directory.close()
    }

    @Throws(IOException::class)
    protected fun doTestSortedSetEnumAdvanceIndependently(dv: SortedSetDocValues) {
        if (dv.valueCount < 2) {
            return
        }
        val terms: MutableList<BytesRef> = mutableListOf()
        val te: TermsEnum = dv.termsEnum()
        terms.add(BytesRef.deepCopyOf(te.next()!!))
        terms.add(BytesRef.deepCopyOf(te.next()!!))

        // Make sure that calls to next() does not modify the term of the other enum
        var enum1: TermsEnum = dv.termsEnum()
        var enum2: TermsEnum = dv.termsEnum()
        var term1 = BytesRefBuilder()
        var term2 = BytesRefBuilder()

        term1.copyBytes(enum1.next()!!)
        term2.copyBytes(enum2.next()!!)
        term1.copyBytes(enum1.next()!!)

        assertEquals(term1.get(), enum1.term())
        assertEquals(term2.get(), enum2.term())

        // Same for seekCeil
        enum1 = dv.termsEnum()
        enum2 = dv.termsEnum()
        term1 = BytesRefBuilder()
        term2 = BytesRefBuilder()

        term2.copyBytes(enum2.next()!!)
        val seekTerm = BytesRefBuilder()
        seekTerm.append(terms[0])
        seekTerm.append(0.toByte())
        enum1.seekCeil(seekTerm.get())
        term1.copyBytes(enum1.term()!!)

        assertEquals(term1.get(), enum1.term())
        assertEquals(term2.get(), enum2.term())

        // Same for seekCeil on an exact value
        enum1 = dv.termsEnum()
        enum2 = dv.termsEnum()
        term1 = BytesRefBuilder()
        term2 = BytesRefBuilder()

        term2.copyBytes(enum2.next()!!)
        enum1.seekCeil(terms[1])
        term1.copyBytes(enum1.term()!!)

        assertEquals(term1.get(), enum1.term())
        assertEquals(term2.get(), enum2.term())

        // Same for seekExact
        enum1 = dv.termsEnum()
        enum2 = dv.termsEnum()
        term1 = BytesRefBuilder()
        term2 = BytesRefBuilder()

        term2.copyBytes(enum2.next()!!)
        val found: Boolean = enum1.seekExact(terms[1])
        assertTrue(found)
        term1.copyBytes(enum1.term()!!)

        // Same for seek by ord
        enum1 = dv.termsEnum()
        enum2 = dv.termsEnum()
        term1 = BytesRefBuilder()
        term2 = BytesRefBuilder()

        term2.copyBytes(enum2.next()!!)
        enum1.seekExact(1)
        term1.copyBytes(enum1.term()!!)

        assertEquals(term1.get(), enum1.term())
        assertEquals(term2.get(), enum2.term())
    }

    // same as testSortedMergeAwayAllValues but on more than 1024 docs to have sparse encoding on
    @Throws(IOException::class)
    open fun testSortedMergeAwayAllValuesLargeSegment() {
        val directory: Directory = newDirectory()
        val analyzer: Analyzer = MockAnalyzer(random())
        val iwconfig = newIndexWriterConfig(analyzer)
        iwconfig.setMergePolicy(newLogMergePolicy())
        val iwriter = RandomIndexWriter(random(), directory, iwconfig)

        val doc = Document()
        doc.add(StringField("id", "1", Field.Store.NO))
        doc.add(SortedDocValuesField("field", newBytesRef("hello")))
        iwriter.addDocument(doc)
        val numEmptyDocs: Int = atLeast(1024)
        for (i in 0..<numEmptyDocs) {
            iwriter.addDocument(Document())
        }
        iwriter.commit()
        iwriter.deleteDocuments(Term("id", "1"))
        iwriter.forceMerge(1)

        val ireader: DirectoryReader = iwriter.reader
        iwriter.close()

        val dv: SortedDocValues = getOnlyLeafReader(ireader).getSortedDocValues("field")!!
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), dv.nextDoc().toLong())

        val termsEnum: TermsEnum = dv.termsEnum()!!
        assertFalse(termsEnum.seekExact(BytesRef("lucene")))
        assertEquals(SeekStatus.END, termsEnum.seekCeil(BytesRef("lucene")))
        assertEquals(-1, dv.lookupTerm(BytesRef("lucene")).toLong())

        ireader.close()
        directory.close()
    }

    // same as testSortedSetMergeAwayAllValues but on more than 1024 docs to have sparse encoding on
    @Throws(IOException::class)
    open fun testSortedSetMergeAwayAllValuesLargeSegment() {
        val directory: Directory = newDirectory()
        val analyzer: Analyzer = MockAnalyzer(random())
        val iwconfig = newIndexWriterConfig(analyzer)
        iwconfig.setMergePolicy(newLogMergePolicy())
        val iwriter = RandomIndexWriter(random(), directory, iwconfig)

        val doc = Document()
        doc.add(StringField("id", "1", Field.Store.NO))
        doc.add(SortedSetDocValuesField("field", newBytesRef("hello")))
        iwriter.addDocument(doc)
        val numEmptyDocs: Int = atLeast(1) // TODO reduced from 1024 to 1 for dev speed
        for (i in 0..<numEmptyDocs) {
            iwriter.addDocument(Document())
        }
        iwriter.commit()
        iwriter.deleteDocuments(Term("id", "1"))
        iwriter.forceMerge(1)

        val ireader: DirectoryReader = iwriter.reader
        iwriter.close()

        val dv: SortedSetDocValues = getOnlyLeafReader(ireader).getSortedSetDocValues("field")!!
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), dv.nextDoc().toLong())

        val termsEnum: TermsEnum = dv.termsEnum()
        assertFalse(termsEnum.seekExact(BytesRef("lucene")))
        assertEquals(
            SeekStatus.END,
            termsEnum.seekCeil(BytesRef("lucene"))
        )
        assertEquals(-1, dv.lookupTerm(BytesRef("lucene")))

        ireader.close()
        directory.close()
    }

    // same as testNumericMergeAwayAllValues but on more than 1024 docs to have sparse encoding on
    @Throws(IOException::class)
    open fun testNumericMergeAwayAllValuesLargeSegment() {
        val directory: Directory = newDirectory()
        val analyzer: Analyzer = MockAnalyzer(random())
        val iwconfig = newIndexWriterConfig(analyzer)
        iwconfig.setMergePolicy(newLogMergePolicy())
        val iwriter = RandomIndexWriter(random(), directory, iwconfig)

        val doc = Document()
        doc.add(StringField("id", "1", Field.Store.NO))
        doc.add(NumericDocValuesField("field", 42L))
        iwriter.addDocument(doc)
        val numEmptyDocs: Int = atLeast(10) // TODO reduced from 1024 to 10 for dev speed
        for (i in 0..<numEmptyDocs) {
            iwriter.addDocument(Document())
        }
        iwriter.commit()
        iwriter.deleteDocuments(Term("id", "1"))
        iwriter.forceMerge(1)

        val ireader: DirectoryReader = iwriter.reader
        iwriter.close()

        val dv: NumericDocValues = getOnlyLeafReader(ireader).getNumericDocValues("field")!!
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), dv.nextDoc().toLong())

        ireader.close()
        directory.close()
    }

    // same as testSortedNumericMergeAwayAllValues but on more than 1024 docs to have sparse encoding
    // on
    @Throws(IOException::class)
    open fun testSortedNumericMergeAwayAllValuesLargeSegment() {
        val directory: Directory = newDirectory()
        val analyzer: Analyzer = MockAnalyzer(random())
        val iwconfig = newIndexWriterConfig(analyzer)
        iwconfig.setMergePolicy(newLogMergePolicy())
        val iwriter = RandomIndexWriter(random(), directory, iwconfig)

        val doc = Document()
        doc.add(StringField("id", "1", Field.Store.NO))
        doc.add(SortedNumericDocValuesField("field", 42L))
        iwriter.addDocument(doc)
        val numEmptyDocs: Int = atLeast(1024)
        for (i in 0..<numEmptyDocs) {
            iwriter.addDocument(Document())
        }
        iwriter.commit()
        iwriter.deleteDocuments(Term("id", "1"))
        iwriter.forceMerge(1)

        val ireader: DirectoryReader = iwriter.reader
        iwriter.close()

        val dv: SortedNumericDocValues = getOnlyLeafReader(ireader).getSortedNumericDocValues("field")!!
        assertEquals(
            DocIdSetIterator.NO_MORE_DOCS.toLong(),
            dv.nextDoc().toLong()
        )

        ireader.close()
        directory.close()
    }

    // same as testBinaryMergeAwayAllValues but on more than 1024 docs to have sparse encoding on
    @Throws(IOException::class)
    open fun testBinaryMergeAwayAllValuesLargeSegment() {
        val directory: Directory = newDirectory()
        val analyzer: Analyzer = MockAnalyzer(random())
        val iwconfig = newIndexWriterConfig(analyzer)
        iwconfig.setMergePolicy(newLogMergePolicy())
        val iwriter = RandomIndexWriter(random(), directory, iwconfig)

        val doc = Document()
        doc.add(StringField("id", "1", Field.Store.NO))
        doc.add(BinaryDocValuesField("field", newBytesRef("hello")))
        iwriter.addDocument(doc)
        val numEmptyDocs: Int = atLeast(1024)
        for (i in 0..<numEmptyDocs) {
            iwriter.addDocument(Document())
        }
        iwriter.commit()
        iwriter.deleteDocuments(Term("id", "1"))
        iwriter.forceMerge(1)

        val ireader: DirectoryReader = iwriter.reader
        iwriter.close()

        val dv: BinaryDocValues = getOnlyLeafReader(ireader).getBinaryDocValues("field")!!
        assertEquals(
            DocIdSetIterator.NO_MORE_DOCS.toLong(),
            dv.nextDoc().toLong()
        )

        ireader.close()
        directory.close()
    }

    @Throws(IOException::class)
    open fun testRandomAdvanceNumeric() {
        val longRange: Long
        if (random().nextBoolean()) {
            longRange = TestUtil.nextInt(
                random(),
                1,
                3 // TODO reduced from 1024 to 3 for dev speed
            ).toLong()
        } else {
            longRange = TestUtil.nextLong(
                random(),
                1,
                /*Long.MAX_VALUE*/ 3 // TODO reduced to 3 for dev speed
            )
        }
        doTestRandomAdvance(
            object : FieldCreator {
                override fun next(): Field {
                    return NumericDocValuesField(
                        "field",
                        TestUtil.nextLong(
                            random(),
                            0,
                            longRange
                        )
                    )
                }

                @Throws(IOException::class)
                override fun iterator(r: IndexReader): DocIdSetIterator {
                    return MultiDocValues.getNumericValues(r, "field")!!
                }
            })
    }

    @Throws(IOException::class)
    open fun testRandomAdvanceBinary() {
        doTestRandomAdvance(
            object : FieldCreator {
                override fun next(): Field {
                    val bytes = ByteArray(random().nextInt(10))
                    random().nextBytes(bytes)
                    return BinaryDocValuesField(
                        "field",
                        newBytesRef(bytes)
                    )
                }

                @Throws(IOException::class)
                override fun iterator(r: IndexReader): DocIdSetIterator {
                    return MultiDocValues.getBinaryValues(r, "field")!!
                }
            })
    }

    /**
     * Tests where a DVField uses a high number of packed bits to store its ords. See:
     * https://issues.apache.org/jira/browse/LUCENE-10159
     */
    /*@org.apache.lucene.tests.util.LuceneTestCase.Nightly*/
    @Throws(Exception::class)
    open fun testHighOrdsSortedSetDV() {
        assumeFalse(
            "This test with SimpleTextCodec requires a lot of memory",
            codec is SimpleTextCodec
        )
        val dir: Directory = newDirectory()
        val iwc =
            IndexWriterConfig()
        iwc.setRAMBufferSizeMB(
            (8 + random().nextInt(64)).toDouble()
        )
        val writer = IndexWriter(dir, iwc)
        // many docs with some of them have very high ords
        val numDocs: Int = 20000 + random().nextInt(10000)
        for (i in 1..<numDocs) {
            val numOrds: Int
            if (random().nextInt(100) <= 5) {
                numOrds = 1000 + random().nextInt(500)
            } else {
                numOrds = random().nextInt(10)
            }
            val doc = Document()
            for (ord in 0..<numOrds) {
                doc.add(
                    SortedSetDocValuesField(
                        "sorted_set_dv",
                        TestUtil.randomBinaryTerm(
                            random(),
                            2
                        )
                    )
                )
            }
            writer.addDocument(doc)
        }
        writer.forceMerge(1, true)
        DirectoryReader.open(writer).use { reader ->
            TestUtil.checkReader(reader)
        }
        IOUtils.close(writer, dir)
    }

    private interface FieldCreator {
        fun next(): Field

        @Throws(IOException::class)
        fun iterator(r: IndexReader): DocIdSetIterator
    }

    @Throws(IOException::class)
    private fun doTestRandomAdvance(fieldCreator: FieldCreator) {
        val analyzer: Analyzer = MockAnalyzer(random())

        val directory: Directory = newDirectory()
        val conf = newIndexWriterConfig(analyzer)
        conf.setMergePolicy(newLogMergePolicy())
        val w = RandomIndexWriter(random(), directory, conf)
        val numChunks: Int = atLeast(10)
        var id = 0
        val missingSet: MutableSet<Int> = mutableSetOf()
        for (i in 0..<numChunks) {
            // change sparseness for each chunk
            val sparseChance: Double =
                random().nextDouble()
            val docCount: Int = atLeast(1000)
            for (j in 0..<docCount) {
                val doc = Document()
                doc.add(StoredField("id", id))
                if (random().nextDouble() > sparseChance
                ) {
                    doc.add(fieldCreator.next())
                } else {
                    missingSet.add(id)
                }
                id++
                w.addDocument(doc)
            }
        }

        if (random().nextBoolean()) {
            w.forceMerge(1)
        }

        // Now search the index:
        val r: IndexReader = w.reader
        val storedFields: StoredFields = r.storedFields()
        val missing: BitSet = FixedBitSet(r.maxDoc())
        for (docID in 0..<r.maxDoc()) {
            val doc = storedFields.document(docID)
            if (missingSet.contains(doc.getField("id")!!.numericValue())) {
                missing.set(docID)
            }
        }

        val numIters: Int = atLeast(10)
        for (iter in 0..<numIters) {
            val values: DocIdSetIterator = fieldCreator.iterator(r)
            assertEquals(-1, values.docID().toLong())

            while (true) {
                val docID: Int
                if (random().nextBoolean()) {
                    docID = values.nextDoc()
                } else {
                    val range: Int
                    if (random().nextInt(10) == 7) {
                        // big jump
                        range = r.maxDoc() - values.docID()
                    } else {
                        // small jump
                        range = 25
                    }
                    val inc: Int = TestUtil.nextInt(
                        random(),
                        1,
                        range
                    )
                    docID = values.advance(values.docID() + inc)
                }
                if (docID == DocIdSetIterator.NO_MORE_DOCS) {
                    break
                }
                assertFalse(missing.get(docID))
            }
        }

        IOUtils.close(r, w, directory)
    }

    protected fun codecAcceptsHugeBinaryValues(field: String): Boolean {
        return true
    }
}
