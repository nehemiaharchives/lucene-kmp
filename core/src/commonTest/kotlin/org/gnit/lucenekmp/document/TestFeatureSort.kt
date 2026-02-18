package org.gnit.lucenekmp.document

import okio.IOException
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.jdkport.MIN_NORMAL
import org.gnit.lucenekmp.jdkport.floatToIntBits
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.MatchAllDocsQuery
import org.gnit.lucenekmp.search.Sort
import org.gnit.lucenekmp.search.SortField
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.CheckHits
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/*
 * Test for sorting using a feature from a FeatureField.
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
class TestFeatureSort : LuceneTestCase() {

    @Test
    @Throws(IOException::class)
    fun testFeature() {
        val dir: Directory = newDirectory()
        val config: IndexWriterConfig =
            newIndexWriterConfig().setMergePolicy(newLogMergePolicy(random().nextBoolean()))
        val writer = RandomIndexWriter(random(), dir, config)
        var doc = Document()
        doc.add(FeatureField("field", "name", 30.1f))
        doc.add(newStringField("value", "30.1", Field.Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(FeatureField("field", "name", 1.3f))
        doc.add(newStringField("value", "1.3", Field.Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(FeatureField("field", "name", 4.2f))
        doc.add(newStringField("value", "4.2", Field.Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        val searcher: IndexSearcher = newSearcher(ir)
        val storedFields = searcher.storedFields()
        val sort = Sort(FeatureField.newFeatureSort("field", "name"))

        val td: TopDocs = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(3, td.totalHits.value)
        // numeric order
        assertEquals("30.1", storedFields.document(td.scoreDocs[0].doc).get("value"))
        assertEquals("4.2", storedFields.document(td.scoreDocs[1].doc).get("value"))
        assertEquals("1.3", storedFields.document(td.scoreDocs[2].doc).get("value"))

        ir.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testFeatureMissing() {
        val dir: Directory = newDirectory()
        val config: IndexWriterConfig =
            newIndexWriterConfig().setMergePolicy(newLogMergePolicy(random().nextBoolean()))
        val writer = RandomIndexWriter(random(), dir, config)
        var doc = Document()
        writer.addDocument(doc)
        doc = Document()
        doc.add(FeatureField("field", "name", 1.3f))
        doc.add(newStringField("value", "1.3", Field.Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(FeatureField("field", "name", 4.2f))
        doc.add(newStringField("value", "4.2", Field.Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        val searcher: IndexSearcher = newSearcher(ir)
        val sort = Sort(FeatureField.newFeatureSort("field", "name"))
        val storedFields = searcher.storedFields()

        val td: TopDocs = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(3, td.totalHits.value)
        // null is treated as 0
        assertEquals("4.2", storedFields.document(td.scoreDocs[0].doc).get("value"))
        assertEquals("1.3", storedFields.document(td.scoreDocs[1].doc).get("value"))
        assertNull(storedFields.document(td.scoreDocs[2].doc).get("value"))

        ir.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testFeatureMissingFieldInSegment() {
        val dir: Directory = newDirectory()
        val config: IndexWriterConfig =
            newIndexWriterConfig().setMergePolicy(newLogMergePolicy(random().nextBoolean()))
        val writer = RandomIndexWriter(random(), dir, config)
        var doc = Document()
        writer.addDocument(doc)
        writer.commit()
        doc = Document()
        doc.add(FeatureField("field", "name", 1.3f))
        doc.add(newStringField("value", "1.3", Field.Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(FeatureField("field", "name", 4.2f))
        doc.add(newStringField("value", "4.2", Field.Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        val searcher: IndexSearcher = newSearcher(ir)
        val sort = Sort(FeatureField.newFeatureSort("field", "name"))
        val storedFields = searcher.storedFields()

        val td: TopDocs = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(3, td.totalHits.value)
        // null is treated as 0
        assertEquals("4.2", storedFields.document(td.scoreDocs[0].doc).get("value"))
        assertEquals("1.3", storedFields.document(td.scoreDocs[1].doc).get("value"))
        assertNull(storedFields.document(td.scoreDocs[2].doc).get("value"))

        ir.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testFeatureMissingFeatureNameInSegment() {
        val dir: Directory = newDirectory()
        val config: IndexWriterConfig =
            newIndexWriterConfig().setMergePolicy(newLogMergePolicy(random().nextBoolean()))
        val writer = RandomIndexWriter(random(), dir, config)
        var doc = Document()
        doc.add(FeatureField("field", "different_name", 0.5f))
        writer.addDocument(doc)
        writer.commit()
        doc = Document()
        doc.add(FeatureField("field", "name", 1.3f))
        doc.add(newStringField("value", "1.3", Field.Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(FeatureField("field", "name", 4.2f))
        doc.add(newStringField("value", "4.2", Field.Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        val searcher: IndexSearcher = newSearcher(ir)
        val sort = Sort(FeatureField.newFeatureSort("field", "name"))
        val storedFields = searcher.storedFields()

        val td: TopDocs = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(3, td.totalHits.value)
        // null is treated as 0
        assertEquals("4.2", storedFields.document(td.scoreDocs[0].doc).get("value"))
        assertEquals("1.3", storedFields.document(td.scoreDocs[1].doc).get("value"))
        assertNull(storedFields.document(td.scoreDocs[2].doc).get("value"))

        ir.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testFeatureMultipleMissing() {
        val dir: Directory = newDirectory()
        val config: IndexWriterConfig =
            newIndexWriterConfig().setMergePolicy(newLogMergePolicy(random().nextBoolean()))
        val writer = RandomIndexWriter(random(), dir, config)
        var doc = Document()
        writer.addDocument(doc)
        doc = Document()
        writer.addDocument(doc)
        doc = Document()
        writer.addDocument(doc)
        doc = Document()
        writer.addDocument(doc)
        doc = Document()
        writer.addDocument(doc)
        doc = Document()
        doc.add(FeatureField("field", "name", 1.3f))
        doc.add(newStringField("value", "1.3", Field.Store.YES))
        writer.addDocument(doc)
        doc = Document()
        doc.add(FeatureField("field", "name", 4.2f))
        doc.add(newStringField("value", "4.2", Field.Store.YES))
        writer.addDocument(doc)
        val ir: IndexReader = writer.reader
        writer.close()

        val searcher: IndexSearcher = newSearcher(ir)
        val sort = Sort(FeatureField.newFeatureSort("field", "name"))
        val storedFields = searcher.storedFields()

        val td: TopDocs = searcher.search(MatchAllDocsQuery(), 10, sort)
        assertEquals(7, td.totalHits.value)
        // null is treated as 0
        assertEquals("4.2", storedFields.document(td.scoreDocs[0].doc).get("value"))
        assertEquals("1.3", storedFields.document(td.scoreDocs[1].doc).get("value"))
        assertNull(storedFields.document(td.scoreDocs[2].doc).get("value"))
        assertNull(storedFields.document(td.scoreDocs[3].doc).get("value"))
        assertNull(storedFields.document(td.scoreDocs[4].doc).get("value"))
        assertNull(storedFields.document(td.scoreDocs[5].doc).get("value"))
        assertNull(storedFields.document(td.scoreDocs[6].doc).get("value"))

        ir.close()
        dir.close()
    }

    // This duel gives compareBottom and compareTop some coverage
    @Test
    @Throws(IOException::class)
    fun testDuelFloat() {
        val dir: Directory = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        val numDocs = atLeast(10) // TODO reduced from 100 to 10 for dev speed
        for (d in 0..<numDocs) {
            val doc = Document()
            if (random().nextBoolean()) {
                var f: Float
                do {
                    val freq = TestUtil.nextInt(random(), 1, FeatureField.MAX_FREQ)
                    f = FeatureField.decodeFeatureValue(freq.toFloat())
                } while (f < Float.MIN_NORMAL)
                doc.add(NumericDocValuesField("float", Float.floatToIntBits(f).toLong()))
                doc.add(FeatureField("feature", "foo", f))
            }
            w.addDocument(doc)
        }

        val r: IndexReader = w.reader
        w.close()
        val searcher = newSearcher(r)

        var topDocs: TopDocs? = null
        var featureTopDocs: TopDocs? = null
        do {
            if (topDocs == null) {
                topDocs = searcher.search(
                    MatchAllDocsQuery(),
                    10,
                    Sort(SortField("float", SortField.Type.FLOAT, true))
                )
                featureTopDocs = searcher.search(
                    MatchAllDocsQuery(),
                    10,
                    Sort(FeatureField.newFeatureSort("feature", "foo"))
                )
            } else {
                topDocs = searcher.searchAfter(
                    topDocs.scoreDocs[topDocs.scoreDocs.size - 1],
                    MatchAllDocsQuery(),
                    10,
                    Sort(SortField("float", SortField.Type.FLOAT, true))
                )
                featureTopDocs = searcher.searchAfter(
                    featureTopDocs!!.scoreDocs[featureTopDocs.scoreDocs.size - 1],
                    MatchAllDocsQuery(),
                    10,
                    Sort(FeatureField.newFeatureSort("feature", "foo"))
                )
            }

            CheckHits.checkEqual(MatchAllDocsQuery(), topDocs.scoreDocs, featureTopDocs!!.scoreDocs)
        } while (topDocs.scoreDocs.isNotEmpty())

        r.close()
        dir.close()
    }
}
