package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.document.BinaryDocValuesField
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.DoubleDocValuesField
import org.gnit.lucenekmp.document.DoublePoint
import org.gnit.lucenekmp.document.Field.Store
import org.gnit.lucenekmp.document.FloatDocValuesField
import org.gnit.lucenekmp.document.FloatPoint
import org.gnit.lucenekmp.document.IntPoint
import org.gnit.lucenekmp.document.LongPoint
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.SortedDocValuesField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/*
 * Very simple tests of sorting.
 *
 * THE RULES:
 * 1. keywords like 'abstract' and 'static' should not appear in this file.
 * 2. each test method should be self-contained and understandable.
 * 3. no test methods should share code with other test methods.
 * 4. no testing of things unrelated to sorting.
 * 5. no tracers.
 * 6. keyword 'class' should appear only once in this file, here ----
 *                                                                  |
 *        -----------------------------------------------------------
 *        |
 *       \./
 */
class TestSort : LuceneTestCase() {
    private val logger = io.github.oshai.kotlinlogging.KotlinLogging.logger {}
    private fun assertEqualsInTestSort(a: Sort, b: Sort) {
        /*LuceneTestCase.*/assertEquals(a, b)
        /*LuceneTestCase.*/assertEquals(b, a)
        /*LuceneTestCase.*/assertEquals(
            a.hashCode().toLong(),
            b.hashCode().toLong()
        )
    }

    private fun assertDifferent(a: Sort, b: Sort) {
        assertNotEquals(a, b)
        assertNotEquals(b, a)
        assertNotEquals(a.hashCode().toLong(), b.hashCode().toLong())
    }

    @Test
    fun testEquals() {
        val sortField1 = SortField("foo", SortField.Type.STRING)
        var sortField2 = SortField("foo", SortField.Type.STRING)
        assertEqualsInTestSort(Sort(sortField1), Sort(sortField2))

        sortField2 = SortField("bar", SortField.Type.STRING)
        assertDifferent(Sort(sortField1), Sort(sortField2))

        sortField2 = SortField("foo", SortField.Type.LONG)
        assertDifferent(Sort(sortField1), Sort(sortField2))

        sortField2 = SortField("foo", SortField.Type.STRING)
        sortField2.missingValue = SortField.STRING_FIRST
        assertDifferent(Sort(sortField1), Sort(sortField2))

        sortField2 = SortField("foo", SortField.Type.STRING, false)
        assertEqualsInTestSort(Sort(sortField1), Sort(sortField2))

        sortField2 = SortField("foo", SortField.Type.STRING, true)
        assertDifferent(Sort(sortField1), Sort(sortField2))
    }

    /** Tests sorting on type string  */
    @Test
    @Throws(IOException::class)
    fun testString() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        var doc = Document()
        doc.add(SortedDocValuesField("value", newBytesRef("foo")))
        doc.add(newStringField("value", "foo", Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(SortedDocValuesField("value", newBytesRef("bar")))
        doc.add(newStringField("value", "bar", Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        val searcher: IndexSearcher = newSearcher(ir)
        val sort = Sort(SortField("value", SortField.Type.STRING))

        val td: TopDocs = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(2, td.totalHits.value)
         // 'bar' comes before 'foo'
        assertEquals("bar", searcher.storedFields().document(td.scoreDocs[0].doc).get("value"))
        assertEquals("foo", searcher.storedFields().document(td.scoreDocs[1].doc).get("value"))
        ir.close()
        dir.close()
    }

    /** Tests reverse sorting on type string  */
    @Test
    @Throws(IOException::class)
    fun testStringReverse() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        var doc = Document()
        doc.add(SortedDocValuesField("value", newBytesRef("bar")))
        doc.add(newStringField("value", "bar", Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(SortedDocValuesField("value", newBytesRef("foo")))
        doc.add(newStringField("value", "foo", Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        val searcher: IndexSearcher = newSearcher(ir)
        val sort = Sort(SortField("value", SortField.Type.STRING, true))

        val td: TopDocs = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(2, td.totalHits.value)
        // 'foo' comes after 'bar' in reverse order
        assertEquals("foo", searcher.storedFields().document(td.scoreDocs[0].doc).get("value"))
        assertEquals("bar", searcher.storedFields().document(td.scoreDocs[1].doc).get("value"))

        ir.close()
        dir.close()
    }

    /** Tests sorting on type string_val  */
    @Test
    @Throws(IOException::class)
    fun testStringVal() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        var doc = Document()
        doc.add(BinaryDocValuesField("value", newBytesRef("foo")))
        doc.add(newStringField("value", "foo", Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(BinaryDocValuesField("value", newBytesRef("bar")))
        doc.add(newStringField("value", "bar", Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        val searcher: IndexSearcher = newSearcher(ir)
        val sort = Sort(SortField("value", SortField.Type.STRING_VAL))

        val td: TopDocs = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(2, td.totalHits.value)
        // 'bar' comes before 'foo'
        assertEquals("bar", searcher.storedFields().document(td.scoreDocs[0].doc).get("value"))
        assertEquals("foo", searcher.storedFields().document(td.scoreDocs[1].doc).get("value"))

        ir.close()
        dir.close()
    }

    /** Tests reverse sorting on type string_val  */
    @Test
    @Throws(IOException::class)
    fun testStringValReverse() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        var doc = Document()
        doc.add(BinaryDocValuesField("value", newBytesRef("bar")))
        doc.add(newStringField("value", "bar", Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(BinaryDocValuesField("value", newBytesRef("foo")))
        doc.add(newStringField("value", "foo", Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        val searcher: IndexSearcher = newSearcher(ir)
        val sort = Sort(SortField("value", SortField.Type.STRING_VAL, true))

        val td: TopDocs = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(2, td.totalHits.value)
        // 'foo' comes after 'bar' in reverse order
        assertEquals("foo", searcher.storedFields().document(td.scoreDocs[0].doc).get("value"))
        assertEquals("bar", searcher.storedFields().document(td.scoreDocs[1].doc).get("value"))

        ir.close()
        dir.close()
    }

    /** Tests sorting on type int  */
    @Test
    @Throws(IOException::class)
    fun testInt() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        var doc = Document()
        doc.add(NumericDocValuesField("value", 300000))
        doc.add(newStringField("value", "300000", Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(NumericDocValuesField("value", -1))
        doc.add(newStringField("value", "-1", Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(NumericDocValuesField("value", 4))
        doc.add(newStringField("value", "4", Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        val searcher: IndexSearcher = newSearcher(ir)
        val sort = Sort(SortField("value", SortField.Type.INT))

        val td: TopDocs = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(3, td.totalHits.value)
        // numeric order
        assertEquals("-1", searcher.storedFields().document(td.scoreDocs[0].doc).get("value"))
        assertEquals("4", searcher.storedFields().document(td.scoreDocs[1].doc).get("value"))
        assertEquals("300000", searcher.storedFields().document(td.scoreDocs[2].doc).get("value"))

        ir.close()
        dir.close()
    }

    /** Tests sorting on type int in reverse  */
    @Test
    @Throws(IOException::class)
    fun testIntReverse() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        var doc = Document()
        doc.add(NumericDocValuesField("value", 300000))
        doc.add(newStringField("value", "300000", Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(NumericDocValuesField("value", -1))
        doc.add(newStringField("value", "-1", Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(NumericDocValuesField("value", 4))
        doc.add(newStringField("value", "4", Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        val searcher: IndexSearcher = newSearcher(ir)
        val sort = Sort(SortField("value", SortField.Type.INT, true))

        val td: TopDocs = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(3, td.totalHits.value)
        // reverse numeric order
        assertEquals("300000", searcher.storedFields().document(td.scoreDocs[0].doc).get("value"))
        assertEquals("4", searcher.storedFields().document(td.scoreDocs[1].doc).get("value"))
        assertEquals("-1", searcher.storedFields().document(td.scoreDocs[2].doc).get("value"))

        ir.close()
        dir.close()
    }

    /** Tests sorting on type int with a missing value  */
    @Test
    @Throws(IOException::class)
    fun testIntMissing() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        var doc = Document()
        writer.addDocument(doc)
        doc = Document()
        doc.add(NumericDocValuesField("value", -1))
        doc.add(newStringField("value", "-1", Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(NumericDocValuesField("value", 4))
        doc.add(newStringField("value", "4", Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        val searcher: IndexSearcher = newSearcher(ir)
        val sort = Sort(SortField("value", SortField.Type.INT))

        val td: TopDocs = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(3, td.totalHits.value)
        // null is treated as a 0
        assertEquals("-1", searcher.storedFields().document(td.scoreDocs[0].doc).get("value"))
        assertNull(searcher.storedFields().document(td.scoreDocs[1].doc).get("value"))
        assertEquals("4", searcher.storedFields().document(td.scoreDocs[2].doc).get("value"))

        ir.close()
        dir.close()
    }

    /**
     * Tests sorting on type int, specifying the missing value should be treated as Integer.MAX_VALUE
     */
    @Test
    @Throws(IOException::class)
    fun testIntMissingLast() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        var doc = Document()
        writer.addDocument(doc)
        doc = Document()
        doc.add(NumericDocValuesField("value", -1))
        doc.add(newStringField("value", "-1", Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(NumericDocValuesField("value", 4))
        doc.add(newStringField("value", "4", Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        val searcher: IndexSearcher = newSearcher(ir)
        val sortField = SortField("value", SortField.Type.INT)
        sortField.missingValue = Int.MAX_VALUE
        val sort = Sort(sortField)

        val td: TopDocs = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(3, td.totalHits.value)
        // null is treated as a Integer.MAX_VALUE
        assertEquals("-1", searcher.storedFields().document(td.scoreDocs[0].doc).get("value"))
        assertEquals("4", searcher.storedFields().document(td.scoreDocs[1].doc).get("value"))
        assertNull(searcher.storedFields().document(td.scoreDocs[2].doc).get("value"))

        ir.close()
        dir.close()
    }

    /** Tests sorting on type long  */
    @Test
    @Throws(IOException::class)
    fun testLong() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        var doc = Document()
        doc.add(NumericDocValuesField("value", 3000000000L))
        doc.add(newStringField("value", "3000000000", Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(NumericDocValuesField("value", -1))
        doc.add(newStringField("value", "-1", Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(NumericDocValuesField("value", 4))
        doc.add(newStringField("value", "4", Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        val searcher: IndexSearcher = newSearcher(ir)
        val sort = Sort(SortField("value", SortField.Type.LONG))

        val td: TopDocs = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(3, td.totalHits.value)
        // numeric order
        assertEquals("-1", searcher.storedFields().document(td.scoreDocs[0].doc).get("value"))
        assertEquals("4", searcher.storedFields().document(td.scoreDocs[1].doc).get("value"))
        assertEquals("3000000000", searcher.storedFields().document(td.scoreDocs[2].doc).get("value"))

        ir.close()
        dir.close()
    }

    /** Tests sorting on type long in reverse  */
    @Test
    @Throws(IOException::class)
    fun testLongReverse() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        var doc = Document()
        doc.add(NumericDocValuesField("value", 3000000000L))
        doc.add(newStringField("value", "3000000000", Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(NumericDocValuesField("value", -1))
        doc.add(newStringField("value", "-1", Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(NumericDocValuesField("value", 4))
        doc.add(newStringField("value", "4", Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        val searcher: IndexSearcher = newSearcher(ir)
        val sort = Sort(SortField("value", SortField.Type.LONG, true))

        val td: TopDocs = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(3, td.totalHits.value)
        // reverse numeric order
        assertEquals("3000000000", searcher.storedFields().document(td.scoreDocs[0].doc).get("value"))
        assertEquals("4", searcher.storedFields().document(td.scoreDocs[1].doc).get("value"))
        assertEquals("-1", searcher.storedFields().document(td.scoreDocs[2].doc).get("value"))

        ir.close()
        dir.close()
    }

    /** Tests sorting on type long with a missing value  */
    @Test
    @Throws(IOException::class)
    fun testLongMissing() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        var doc = Document()
        writer.addDocument(doc)
        doc = Document()
        doc.add(NumericDocValuesField("value", -1))
        doc.add(newStringField("value", "-1", Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(NumericDocValuesField("value", 4))
        doc.add(newStringField("value", "4", Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        val searcher: IndexSearcher = newSearcher(ir)
        val sort = Sort(SortField("value", SortField.Type.LONG))

        val td: TopDocs = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(3, td.totalHits.value)
        // null is treated as 0
        assertEquals("-1", searcher.storedFields().document(td.scoreDocs[0].doc).get("value"))
        assertNull(searcher.storedFields().document(td.scoreDocs[1].doc).get("value"))
        assertEquals("4", searcher.storedFields().document(td.scoreDocs[2].doc).get("value"))

        ir.close()
        dir.close()
    }

    /**
     * Tests sorting on type long, specifying the missing value should be treated as Long.MAX_VALUE
     */
    @Test
    @Throws(IOException::class)
    fun testLongMissingLast() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        var doc = Document()
        writer.addDocument(doc)
        doc = Document()
        doc.add(NumericDocValuesField("value", -1))
        doc.add(newStringField("value", "-1", Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(NumericDocValuesField("value", 4))
        doc.add(newStringField("value", "4", Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        val searcher: IndexSearcher = newSearcher(ir)
        val sortField = SortField("value", SortField.Type.LONG)
        sortField.missingValue = Long.MAX_VALUE
        val sort = Sort(sortField)

        val td: TopDocs = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(3, td.totalHits.value)
        // null is treated as Long.MAX_VALUE
        assertEquals("-1", searcher.storedFields().document(td.scoreDocs[0].doc).get("value"))
        assertEquals("4", searcher.storedFields().document(td.scoreDocs[1].doc).get("value"))
        assertNull(searcher.storedFields().document(td.scoreDocs[2].doc).get("value"))

        ir.close()
        dir.close()
    }

    /** Tests sorting on type float  */
    @Test
    @Throws(IOException::class)
    fun testFloat() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        var doc = Document()
        doc.add(FloatDocValuesField("value", 30.1f))
        doc.add(newStringField("value", "30.1", Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(FloatDocValuesField("value", -1.3f))
        doc.add(newStringField("value", "-1.3", Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(FloatDocValuesField("value", 4.2f))
        doc.add(newStringField("value", "4.2", Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        val searcher: IndexSearcher = newSearcher(ir)
        val sort = Sort(SortField("value", SortField.Type.FLOAT))

        val td: TopDocs = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(3, td.totalHits.value)
        // numeric order
        assertEquals("-1.3", searcher.storedFields().document(td.scoreDocs[0].doc).get("value"))
        assertEquals("4.2", searcher.storedFields().document(td.scoreDocs[1].doc).get("value"))
        assertEquals("30.1", searcher.storedFields().document(td.scoreDocs[2].doc).get("value"))

        ir.close()
        dir.close()
    }

    /** Tests sorting on type float in reverse  */
    @Test
    @Throws(IOException::class)
    fun testFloatReverse() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        var doc = Document()
        doc.add(FloatDocValuesField("value", 30.1f))
        doc.add(newStringField("value", "30.1", Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(FloatDocValuesField("value", -1.3f))
        doc.add(newStringField("value", "-1.3", Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(FloatDocValuesField("value", 4.2f))
        doc.add(newStringField("value", "4.2", Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        val searcher: IndexSearcher = newSearcher(ir)
        val sort = Sort(SortField("value", SortField.Type.FLOAT, true))

        val td: TopDocs = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(3, td.totalHits.value)
        // reverse numeric order
        assertEquals("30.1", searcher.storedFields().document(td.scoreDocs[0].doc).get("value"))
        assertEquals("4.2", searcher.storedFields().document(td.scoreDocs[1].doc).get("value"))
        assertEquals("-1.3", searcher.storedFields().document(td.scoreDocs[2].doc).get("value"))

        ir.close()
        dir.close()
    }

    /** Tests sorting on type float with a missing value  */
    @Test
    @Throws(IOException::class)
    fun testFloatMissing() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        var doc = Document()
        writer.addDocument(doc)
        doc = Document()
        doc.add(FloatDocValuesField("value", -1.3f))
        doc.add(newStringField("value", "-1.3", Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(FloatDocValuesField("value", 4.2f))
        doc.add(newStringField("value", "4.2", Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        val searcher: IndexSearcher = newSearcher(ir)
        val sort = Sort(SortField("value", SortField.Type.FLOAT))

        val td: TopDocs = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(3, td.totalHits.value)
        // null is treated as 0
        assertEquals("-1.3", searcher.storedFields().document(td.scoreDocs[0].doc).get("value"))
        assertNull(searcher.storedFields().document(td.scoreDocs[1].doc).get("value"))
        assertEquals("4.2", searcher.storedFields().document(td.scoreDocs[2].doc).get("value"))

        ir.close()
        dir.close()
    }

    /**
     * Tests sorting on type float, specifying the missing value should be treated as Float.MAX_VALUE
     */
    @Test
    @Throws(IOException::class)
    fun testFloatMissingLast() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        var doc = Document()
        writer.addDocument(doc)
        doc = Document()
        doc.add(FloatDocValuesField("value", -1.3f))
        doc.add(newStringField("value", "-1.3", Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(FloatDocValuesField("value", 4.2f))
        doc.add(newStringField("value", "4.2", Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        val searcher: IndexSearcher = newSearcher(ir)
        val sortField = SortField("value", SortField.Type.FLOAT)
        sortField.missingValue = Float.MAX_VALUE
        val sort = Sort(sortField)

        val td: TopDocs = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(3, td.totalHits.value)
        // null is treated as Float.MAX_VALUE
        assertEquals("-1.3", searcher.storedFields().document(td.scoreDocs[0].doc).get("value"))
        assertEquals("4.2", searcher.storedFields().document(td.scoreDocs[1].doc).get("value"))
        assertNull(searcher.storedFields().document(td.scoreDocs[2].doc).get("value"))

        ir.close()
        dir.close()
    }

    /** Tests sorting on type double  */
    @Test
    @Throws(IOException::class)
    fun testDouble() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        var doc = Document()
        doc.add(DoubleDocValuesField("value", 30.1))
        doc.add(newStringField("value", "30.1", Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(DoubleDocValuesField("value", -1.3))
        doc.add(newStringField("value", "-1.3", Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(DoubleDocValuesField("value", 4.2333333333333))
        doc.add(newStringField("value", "4.2333333333333", Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(DoubleDocValuesField("value", 4.2333333333332))
        doc.add(newStringField("value", "4.2333333333332", Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        val searcher: IndexSearcher = newSearcher(ir)
        val sort = Sort(SortField("value", SortField.Type.DOUBLE))

        val td: TopDocs = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(4, td.totalHits.value)
        // numeric order
        assertEquals("-1.3", searcher.storedFields().document(td.scoreDocs[0].doc).get("value"))
        assertEquals("4.2333333333332", searcher.storedFields().document(td.scoreDocs[1].doc).get("value"))
        assertEquals("4.2333333333333", searcher.storedFields().document(td.scoreDocs[2].doc).get("value"))
        assertEquals("30.1", searcher.storedFields().document(td.scoreDocs[3].doc).get("value"))

        ir.close()
        dir.close()
    }

    /** Tests sorting on type double with +/- zero  */
    @Test
    @Throws(IOException::class)
    fun testDoubleSignedZero() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        var doc = Document()
        doc.add(DoubleDocValuesField("value", +0.0))
        doc.add(newStringField("value", "+0", Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(DoubleDocValuesField("value", -0.0))
        doc.add(newStringField("value", "-0", Store.YES))
        writer.addDocument(doc)
        doc = Document()
        val ir: IndexReader = writer.reader
        writer.close()

        val searcher: IndexSearcher = newSearcher(ir)
        val sort = Sort(SortField("value", SortField.Type.DOUBLE))

        val td: TopDocs = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(2, td.totalHits.value)
        // numeric order
        assertEquals("-0", searcher.storedFields().document(td.scoreDocs[0].doc).get("value"))
        assertEquals("+0", searcher.storedFields().document(td.scoreDocs[1].doc).get("value"))

        ir.close()
        dir.close()
    }

    /** Tests sorting on type double in reverse  */
    @Throws(IOException::class)
    @Test
    fun testDoubleReverse() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        var doc = Document()
        doc.add(DoubleDocValuesField("value", 30.1))
        doc.add(newStringField("value", "30.1", Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(DoubleDocValuesField("value", -1.3))
        doc.add(newStringField("value", "-1.3", Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(DoubleDocValuesField("value", 4.2333333333333))
        doc.add(newStringField("value", "4.2333333333333", Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(DoubleDocValuesField("value", 4.2333333333332))
        doc.add(newStringField("value", "4.2333333333332", Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        val searcher: IndexSearcher = newSearcher(ir)
        val sort = Sort(SortField("value", SortField.Type.DOUBLE, true))

        val td: TopDocs = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(4, td.totalHits.value)
        // numeric order
        assertEquals("30.1", searcher.storedFields().document(td.scoreDocs[0].doc).get("value"))
        assertEquals("4.2333333333333", searcher.storedFields().document(td.scoreDocs[1].doc).get("value"))
        assertEquals("4.2333333333332", searcher.storedFields().document(td.scoreDocs[2].doc).get("value"))
        assertEquals("-1.3", searcher.storedFields().document(td.scoreDocs[3].doc).get("value"))

        ir.close()
        dir.close()
    }

    /** Tests sorting on type double with a missing value  */
    @Test
    @Throws(IOException::class)
    fun testDoubleMissing() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        var doc = Document()
        writer.addDocument(doc)
        doc = Document()
        doc.add(DoubleDocValuesField("value", -1.3))
        doc.add(newStringField("value", "-1.3", Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(DoubleDocValuesField("value", 4.2333333333333))
        doc.add(newStringField("value", "4.2333333333333", Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(DoubleDocValuesField("value", 4.2333333333332))
        doc.add(newStringField("value", "4.2333333333332", Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        val searcher: IndexSearcher = newSearcher(ir)
        val sort = Sort(SortField("value", SortField.Type.DOUBLE))

        val td: TopDocs = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(4, td.totalHits.value)
        // null treated as a 0
        assertEquals("-1.3", searcher.storedFields().document(td.scoreDocs[0].doc).get("value"))
        assertNull(searcher.storedFields().document(td.scoreDocs[1].doc).get("value"))
        assertEquals("4.2333333333332", searcher.storedFields().document(td.scoreDocs[2].doc).get("value"))
        assertEquals("4.2333333333333", searcher.storedFields().document(td.scoreDocs[3].doc).get("value"))

        ir.close()
        dir.close()
    }

    /**
     * Tests sorting on type double, specifying the missing value should be treated as
     * Double.MAX_VALUE
     */
    @Test
    @Throws(IOException::class)
    fun testDoubleMissingLast() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        var doc = Document()
        writer.addDocument(doc)
        doc = Document()
        doc.add(DoubleDocValuesField("value", -1.3))
        doc.add(newStringField("value", "-1.3", Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(DoubleDocValuesField("value", 4.2333333333333))
        doc.add(newStringField("value", "4.2333333333333", Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(DoubleDocValuesField("value", 4.2333333333332))
        doc.add(newStringField("value", "4.2333333333332", Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        val searcher: IndexSearcher = newSearcher(ir)
        val sortField = SortField("value", SortField.Type.DOUBLE)
        sortField.missingValue = Double.MAX_VALUE
        val sort = Sort(sortField)

        val td: TopDocs = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(4, td.totalHits.value)
        // null treated as Double.MAX_VALUE
        assertEquals("-1.3", searcher.storedFields().document(td.scoreDocs[0].doc).get("value"))
        assertEquals("4.2333333333332", searcher.storedFields().document(td.scoreDocs[1].doc).get("value"))
        assertEquals("4.2333333333333", searcher.storedFields().document(td.scoreDocs[2].doc).get("value"))
        assertNull(searcher.storedFields().document(td.scoreDocs[3].doc).get("value"))

        ir.close()
        dir.close()
    }

    /** Tests sorting on multiple sort fields  */
    @Test
    @Throws(IOException::class)
    fun testMultiSort() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        var doc = Document()
        doc.add(SortedDocValuesField("value1", newBytesRef("foo")))
        doc.add(NumericDocValuesField("value2", 0))
        doc.add(newStringField("value1", "foo", Store.YES))
        doc.add(newStringField("value2", "0", Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(SortedDocValuesField("value1", newBytesRef("bar")))
        doc.add(NumericDocValuesField("value2", 1))
        doc.add(newStringField("value1", "bar", Store.YES))
        doc.add(newStringField("value2", "1", Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(SortedDocValuesField("value1", newBytesRef("bar")))
        doc.add(NumericDocValuesField("value2", 0))
        doc.add(newStringField("value1", "bar", Store.YES))
        doc.add(newStringField("value2", "0", Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(SortedDocValuesField("value1", newBytesRef("foo")))
        doc.add(NumericDocValuesField("value2", 1))
        doc.add(newStringField("value1", "foo", Store.YES))
        doc.add(newStringField("value2", "1", Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        val searcher: IndexSearcher = newSearcher(ir)
        val sort = Sort(SortField("value1", SortField.Type.STRING), SortField("value2", SortField.Type.LONG))

        var td: TopDocs = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(4, td.totalHits.value)
        // 'bar' comes before 'foo'
        assertEquals("bar", searcher.storedFields().document(td.scoreDocs[0].doc).get("value1"))
        assertEquals("bar", searcher.storedFields().document(td.scoreDocs[1].doc).get("value1"))
        assertEquals("foo", searcher.storedFields().document(td.scoreDocs[2].doc).get("value1"))
        assertEquals("foo", searcher.storedFields().document(td.scoreDocs[3].doc).get("value1"))
        // 0 comes before 1
        assertEquals("0", searcher.storedFields().document(td.scoreDocs[0].doc).get("value2"))
        assertEquals("1", searcher.storedFields().document(td.scoreDocs[1].doc).get("value2"))
        assertEquals("0", searcher.storedFields().document(td.scoreDocs[2].doc).get("value2"))
        assertEquals("1", searcher.storedFields().document(td.scoreDocs[3].doc).get("value2"))

        // Now with overflow
        td = searcher.search(MatchAllDocsQuery(), 1, sort)
        assertEquals(4, td.totalHits.value)
        assertEquals("bar", searcher.storedFields().document(td.scoreDocs[0].doc).get("value1"))
        assertEquals("0", searcher.storedFields().document(td.scoreDocs[0].doc).get("value2"))

        ir.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testRewrite() { newDirectory().use { dir ->
            RandomIndexWriter(random(), dir).use { writer ->
                writer.reader.use { reader ->
                    val searcher: IndexSearcher = newSearcher(reader)
                    val longSource: LongValuesSource = LongValuesSource.constant(1L)
                    var sort = Sort(longSource.getSortField(false))

                    assertSame(sort, sort.rewrite(searcher))

                    val doubleSource: DoubleValuesSource = DoubleValuesSource.constant(1.0)
                    sort = Sort(doubleSource.getSortField(false))
                    assertSame(sort, sort.rewrite(searcher))
                }
            }
        }
    }

    // Ghost tests make sure that sorting can cope with segments that are missing values while their
    // FieldInfo reports that the field exists.
    @Test
    @Throws(IOException::class)
    fun testStringGhost() {
        doTestStringGhost(true)
        doTestStringGhost(false)
    }

    @Throws(IOException::class)
    fun doTestStringGhost(indexed: Boolean) {
        val dir: Directory = newDirectory()
        val writer = IndexWriter(dir, IndexWriterConfig())
        val doc = Document()
        doc.add(SortedDocValuesField("value", newBytesRef("foo")))
        if (indexed) {
            doc.add(newStringField("value", "foo", Store.YES))
        }
        doc.add(
            StringField("id", "0", Store.NO)
        )
        writer.addDocument(doc)
        writer.addDocument(Document())
        writer.flush()
        writer.addDocument(Document())
        writer.flush()
        writer.deleteDocuments(Term("id", "0"))
        writer.forceMerge(1)
        val ir: IndexReader = DirectoryReader.open(writer)
        writer.close()

        val searcher: IndexSearcher = newSearcher(ir)
        val sort = Sort(SortField("value", SortField.Type.STRING))

        val td: TopFieldDocs = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(2, td.totalHits.value)
        assertNull((td.scoreDocs[0] as FieldDoc).fields!![0])
        assertNull((td.scoreDocs[1] as FieldDoc).fields!![0])

        ir.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testIntGhost() {
        doTestIntGhost(true)
        doTestIntGhost(false)
    }

    @Throws(IOException::class)
    private fun doTestIntGhost(indexed: Boolean) {
        val dir: Directory = newDirectory()
        val writer = IndexWriter(dir, IndexWriterConfig())
        val doc = Document()
        doc.add(NumericDocValuesField("value", 3))
        if (indexed) {
            doc.add(IntPoint("value", 3))
        }
        doc.add(
            StringField("id", "0", Store.NO)
        )
        writer.addDocument(doc)
        writer.addDocument(Document())
        writer.flush()
        writer.addDocument(Document())
        writer.flush()
        writer.deleteDocuments(Term("id", "0"))
        writer.forceMerge(1)
        val ir: IndexReader = DirectoryReader.open(writer)
        writer.close()

        val searcher: IndexSearcher = newSearcher(ir)
        val sort = Sort(SortField("value", SortField.Type.INT))

        val td: TopFieldDocs = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(2, td.totalHits.value)
        assertEquals(0, (td.scoreDocs[0] as FieldDoc).fields!![0])
        assertEquals(0, (td.scoreDocs[1] as FieldDoc).fields!![0])

        ir.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testLongGhost() {
        doTestLongGhost(true)
        doTestLongGhost(false)
    }

    @Throws(IOException::class)
    private fun doTestLongGhost(indexed: Boolean) {
        val dir: Directory = newDirectory()
        val writer = IndexWriter(dir, IndexWriterConfig())
        val doc = Document()
        doc.add(NumericDocValuesField("value", 3L))
        if (indexed) {
            doc.add(LongPoint("value", 3L))
        }
        doc.add(
            StringField("id", "0", Store.NO)
        )
        writer.addDocument(doc)
        writer.addDocument(Document())
        writer.flush()
        writer.addDocument(Document())
        writer.flush()
        writer.deleteDocuments(Term("id", "0"))
        writer.forceMerge(1)
        val ir: IndexReader = DirectoryReader.open(writer)
        writer.close()

        val searcher: IndexSearcher = newSearcher(ir)
        val sort = Sort(SortField("value", SortField.Type.LONG))

        val td: TopFieldDocs = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(2, td.totalHits.value)
        assertEquals(0L, (td.scoreDocs[0] as FieldDoc).fields!![0])
        assertEquals(0L, (td.scoreDocs[1] as FieldDoc).fields!![0])

        ir.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testDoubleGhost() {
        doTestDoubleGhost(true)
        doTestDoubleGhost(false)
    }

    @Throws(IOException::class)
    private fun doTestDoubleGhost(indexed: Boolean) {
        val dir: Directory = newDirectory()
        val writer = IndexWriter(dir, IndexWriterConfig())
        val doc = Document()
        doc.add(DoubleDocValuesField("value", 1.25))
        if (indexed) {
            doc.add(DoublePoint("value", 1.25))
        }
        doc.add(StringField("id", "0", Store.NO))
        writer.addDocument(doc)
        writer.addDocument(Document())
        writer.flush()
        writer.addDocument(Document())
        writer.flush()
        writer.deleteDocuments(Term("id", "0"))
        writer.forceMerge(1)
        val ir: IndexReader = DirectoryReader.open(writer)
        writer.close()

        val searcher: IndexSearcher = newSearcher(ir)
        val sort = Sort(SortField("value", SortField.Type.DOUBLE))

        val td: TopFieldDocs = searcher.search(MatchAllDocsQuery(), 10, sort)
       assertEquals(2, td.totalHits.value)
       assertEquals(0.0, (td.scoreDocs[0] as FieldDoc).fields!![0])
       assertEquals(0.0, (td.scoreDocs[1] as FieldDoc).fields!![0])

        ir.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testFloatGhost() {
        doTestFloatGhost(true)
        doTestFloatGhost(false)
    }

    @Throws(IOException::class)
    private fun doTestFloatGhost(indexed: Boolean) {
        logger.debug { "testFloatGhost: start indexed=$indexed" }
        val dir: Directory = newDirectory()
        val writer = IndexWriter(dir, IndexWriterConfig())
        val doc = Document()
        doc.add(FloatDocValuesField("value", 1.25f))
        if (indexed) {
            doc.add(FloatPoint("value", 1.25f))
        }
        doc.add(StringField("id", "0", Store.NO))
        logger.debug { "testFloatGhost: add docs indexed=$indexed" }
        writer.addDocument(doc)
        writer.addDocument(Document())
        writer.flush()
        writer.addDocument(Document())
        writer.flush()
        logger.debug { "testFloatGhost: delete id=0 indexed=$indexed" }
        writer.deleteDocuments(Term("id", "0"))
        logger.debug { "testFloatGhost: forceMerge start indexed=$indexed" }
        writer.forceMerge(1)
        logger.debug { "testFloatGhost: forceMerge done indexed=$indexed" }
        val ir: IndexReader = DirectoryReader.open(writer)
        logger.debug { "testFloatGhost: opened reader indexed=$indexed maxDoc=${ir.maxDoc()}" }
        writer.close()

        val searcher: IndexSearcher = newSearcher(ir)
        val sort = Sort(SortField("value", SortField.Type.FLOAT))

        logger.debug { "testFloatGhost: search start indexed=$indexed" }
        val td: TopFieldDocs = searcher.search(MatchAllDocsQuery(), 10, sort)
        logger.debug { "testFloatGhost: search done indexed=$indexed totalHits=${td.totalHits.value}" }
        assertEquals(2, td.totalHits.value)
        assertEquals(0.0f, (td.scoreDocs[0] as FieldDoc).fields!![0])
        assertEquals(0.0f, (td.scoreDocs[1] as FieldDoc).fields!![0])

        ir.close()
        dir.close()
        logger.debug { "testFloatGhost: end indexed=$indexed" }
    }
}
