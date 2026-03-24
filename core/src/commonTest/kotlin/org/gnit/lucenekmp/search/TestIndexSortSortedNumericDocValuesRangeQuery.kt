package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.LongPoint
import org.gnit.lucenekmp.document.SortedNumericDocValuesField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.DummyTotalHitCountCollector
import org.gnit.lucenekmp.tests.search.QueryUtils
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion.SuppressCodecs
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SuppressCodecs("SimpleText")
class TestIndexSortSortedNumericDocValuesRangeQuery : LuceneTestCase() {
    @Test
    @Throws(IOException::class)
    fun testSameHitsAsPointRangeQuery() {
        val iters = atLeast(10)
        for (iter in 0..<iters) {
            val dir: Directory = newDirectory()

            val iwc = IndexWriterConfig(MockAnalyzer(random()))
            val reverse = random().nextBoolean()
            val sortField = SortedNumericSortField("dv", SortField.Type.LONG, reverse)
            val enableMissingValue = random().nextBoolean()
            if (enableMissingValue) {
                val missingValue =
                    if (random().nextBoolean()) {
                        TestUtil.nextLong(random(), -100, 10000)
                    } else {
                        if (random().nextBoolean()) Long.MIN_VALUE else Long.MAX_VALUE
                    }
                sortField.missingValue = missingValue
            }
            iwc.setIndexSort(Sort(sortField))

            val iw = RandomIndexWriter(random(), dir, iwc)

            val numDocs = atLeast(100)
            for (i in 0..<numDocs) {
                val doc = Document()
                val numValues = TestUtil.nextInt(random(), 0, 1)
                for (j in 0..<numValues) {
                    val value = TestUtil.nextLong(random(), -100, 10000)
                    doc.add(SortedNumericDocValuesField("dv", value))
                    doc.add(LongPoint("idx", value))
                }
                iw.addDocument(doc)
            }
            if (random().nextBoolean()) {
                iw.deleteDocuments(LongPoint.newRangeQuery("idx", 0L, 10L))
            }
            val reader: IndexReader = iw.reader
            val searcher = newSearcher(reader)
            iw.close()

            for (i in 0..<100) {
                val min =
                    if (random().nextBoolean()) Long.MIN_VALUE else TestUtil.nextLong(random(), -100, 10000)
                val max =
                    if (random().nextBoolean()) Long.MAX_VALUE else TestUtil.nextLong(random(), -100, 10000)
                val q1: Query = LongPoint.newRangeQuery("idx", min, max)
                val q2: Query = createQuery("dv", min, max)
                assertSameHits(searcher, q1, q2, false)
            }

            reader.close()
            dir.close()
        }
    }

    @Throws(IOException::class)
    private fun assertSameHits(searcher: IndexSearcher, q1: Query, q2: Query, scores: Boolean) {
        val maxDoc = searcher.indexReader.maxDoc()
        val td1 = searcher.search(q1, maxDoc, if (scores) Sort.RELEVANCE else Sort.INDEXORDER)
        val td2 = searcher.search(q2, maxDoc, if (scores) Sort.RELEVANCE else Sort.INDEXORDER)
        assertEquals(td1.totalHits.value, td2.totalHits.value)
        for (i in td1.scoreDocs.indices) {
            assertEquals(td1.scoreDocs[i].doc, td2.scoreDocs[i].doc)
            if (scores) {
                assertEquals(td1.scoreDocs[i].score, td2.scoreDocs[i].score, 10e-7f)
            }
        }
    }

    @Test
    fun testEquals() {
        val q1 = createQuery("foo", 3, 5)
        QueryUtils.checkEqual(q1, createQuery("foo", 3, 5))
        QueryUtils.checkUnequal(q1, createQuery("foo", 3, 6))
        QueryUtils.checkUnequal(q1, createQuery("foo", 4, 5))
        QueryUtils.checkUnequal(q1, createQuery("bar", 3, 5))
    }

    @Test
    fun testToString() {
        val q1 = createQuery("foo", 3, 5)
        assertEquals("foo:[3 TO 5]", q1.toString())
        assertEquals("[3 TO 5]", q1.toString("foo"))
        assertEquals("foo:[3 TO 5]", q1.toString("bar"))
    }

    @Test
    @Throws(Exception::class)
    fun testIndexSortDocValuesWithEvenLength() {
        for (type in arrayOf(SortField.Type.INT, SortField.Type.LONG)) {
            testIndexSortDocValuesWithEvenLength(true, type)
            testIndexSortDocValuesWithEvenLength(false, type)
        }
    }

    @Throws(Exception::class)
    fun testIndexSortDocValuesWithEvenLength(reverse: Boolean, type: SortField.Type) {
        val dir: Directory = newDirectory()

        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val indexSort = Sort(SortedNumericSortField("field", type, reverse))
        iwc.setIndexSort(indexSort)
        val writer = RandomIndexWriter(random(), dir, iwc)

        writer.addDocument(createDocument("field", -80))
        writer.addDocument(createDocument("field", -5))
        writer.addDocument(createDocument("field", 0))
        writer.addDocument(createDocument("field", 0))
        writer.addDocument(createDocument("field", 30))
        writer.addDocument(createDocument("field", 35))

        val reader: DirectoryReader = writer.reader
        val searcher = newSearcher(reader)

        assertNumberOfHits(searcher, createQuery("field", -80, -80), 1)
        assertNumberOfHits(searcher, createQuery("field", -5, -5), 1)
        assertNumberOfHits(searcher, createQuery("field", 0, 0), 2)
        assertNumberOfHits(searcher, createQuery("field", 30, 30), 1)
        assertNumberOfHits(searcher, createQuery("field", 35, 35), 1)

        assertNumberOfHits(searcher, createQuery("field", -90, -90), 0)
        assertNumberOfHits(searcher, createQuery("field", 5, 5), 0)
        assertNumberOfHits(searcher, createQuery("field", 40, 40), 0)

        assertNumberOfHits(searcher, createQuery("field", -90, -4), 2)
        assertNumberOfHits(searcher, createQuery("field", -80, -4), 2)
        assertNumberOfHits(searcher, createQuery("field", -70, -4), 1)
        assertNumberOfHits(searcher, createQuery("field", -80, -5), 2)

        assertNumberOfHits(searcher, createQuery("field", 25, 34), 1)
        assertNumberOfHits(searcher, createQuery("field", 25, 35), 2)
        assertNumberOfHits(searcher, createQuery("field", 25, 36), 2)
        assertNumberOfHits(searcher, createQuery("field", 30, 35), 2)

        assertNumberOfHits(searcher, createQuery("field", -4, 4), 2)
        assertNumberOfHits(searcher, createQuery("field", -4, 0), 2)
        assertNumberOfHits(searcher, createQuery("field", 0, 4), 2)
        assertNumberOfHits(searcher, createQuery("field", 0, 30), 3)

        assertNumberOfHits(searcher, createQuery("field", -80, 35), 6)
        assertNumberOfHits(searcher, createQuery("field", -90, 40), 6)

        writer.close()
        reader.close()
        dir.close()
    }

    @Throws(IOException::class)
    private fun assertNumberOfHits(searcher: IndexSearcher, query: Query, numberOfHits: Int) {
        assertEquals(numberOfHits, searcher.search(query, DummyTotalHitCountCollector.createManager()))
        assertEquals(numberOfHits, searcher.count(query))
    }

    @Test
    @Throws(Exception::class)
    fun testIndexSortDocValuesWithOddLength() {
        testIndexSortDocValuesWithOddLength(false)
        testIndexSortDocValuesWithOddLength(true)
    }

    @Throws(Exception::class)
    fun testIndexSortDocValuesWithOddLength(reverse: Boolean) {
        val dir: Directory = newDirectory()

        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val indexSort = Sort(SortedNumericSortField("field", SortField.Type.LONG, reverse))
        iwc.setIndexSort(indexSort)
        val writer = RandomIndexWriter(random(), dir, iwc)

        writer.addDocument(createDocument("field", -80))
        writer.addDocument(createDocument("field", -5))
        writer.addDocument(createDocument("field", 0))
        writer.addDocument(createDocument("field", 0))
        writer.addDocument(createDocument("field", 5))
        writer.addDocument(createDocument("field", 30))
        writer.addDocument(createDocument("field", 35))

        val reader: DirectoryReader = writer.reader
        val searcher = newSearcher(reader)

        assertNumberOfHits(searcher, createQuery("field", -80, -80), 1)
        assertNumberOfHits(searcher, createQuery("field", -5, -5), 1)
        assertNumberOfHits(searcher, createQuery("field", 0, 0), 2)
        assertNumberOfHits(searcher, createQuery("field", 5, 5), 1)
        assertNumberOfHits(searcher, createQuery("field", 30, 30), 1)
        assertNumberOfHits(searcher, createQuery("field", 35, 35), 1)

        assertNumberOfHits(searcher, createQuery("field", -90, -90), 0)
        assertNumberOfHits(searcher, createQuery("field", 6, 6), 0)
        assertNumberOfHits(searcher, createQuery("field", 40, 40), 0)

        assertNumberOfHits(searcher, createQuery("field", -90, -4), 2)
        assertNumberOfHits(searcher, createQuery("field", -80, -4), 2)
        assertNumberOfHits(searcher, createQuery("field", -70, -4), 1)
        assertNumberOfHits(searcher, createQuery("field", -80, -5), 2)

        assertNumberOfHits(searcher, createQuery("field", 25, 34), 1)
        assertNumberOfHits(searcher, createQuery("field", 25, 35), 2)
        assertNumberOfHits(searcher, createQuery("field", 25, 36), 2)
        assertNumberOfHits(searcher, createQuery("field", 30, 35), 2)

        assertNumberOfHits(searcher, createQuery("field", -4, 4), 2)
        assertNumberOfHits(searcher, createQuery("field", -4, 0), 2)
        assertNumberOfHits(searcher, createQuery("field", 0, 4), 2)
        assertNumberOfHits(searcher, createQuery("field", 0, 30), 4)

        assertNumberOfHits(searcher, createQuery("field", -80, 35), 7)
        assertNumberOfHits(searcher, createQuery("field", -90, 40), 7)

        writer.close()
        reader.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testIndexSortDocValuesWithSingleValue() {
        testIndexSortDocValuesWithSingleValue(false)
        testIndexSortDocValuesWithSingleValue(true)
    }

    @Throws(IOException::class)
    private fun testIndexSortDocValuesWithSingleValue(reverse: Boolean) {
        val dir: Directory = newDirectory()

        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val indexSort = Sort(SortedNumericSortField("field", SortField.Type.LONG, reverse))
        iwc.setIndexSort(indexSort)
        val writer = RandomIndexWriter(random(), dir, iwc)

        writer.addDocument(createDocument("field", 42))

        val reader: DirectoryReader = writer.reader
        val searcher = newSearcher(reader)

        assertNumberOfHits(searcher, createQuery("field", 42, 43), 1)
        assertNumberOfHits(searcher, createQuery("field", 42, 42), 1)
        assertNumberOfHits(searcher, createQuery("field", 41, 41), 0)
        assertNumberOfHits(searcher, createQuery("field", 43, 43), 0)

        writer.close()
        reader.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testIndexSortMissingValues() {
        val dir: Directory = newDirectory()

        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val sortField = SortedNumericSortField("field", SortField.Type.LONG)
        sortField.missingValue = random().nextLong()
        iwc.setIndexSort(Sort(sortField))
        val writer = RandomIndexWriter(random(), dir, iwc)

        writer.addDocument(createDocument("field", -80))
        writer.addDocument(createDocument("field", -5))
        writer.addDocument(createDocument("field", 0))
        writer.addDocument(createDocument("field", 35))

        writer.addDocument(createDocument("other-field", 0))
        writer.addDocument(createDocument("other-field", 10))
        writer.addDocument(createDocument("other-field", 20))

        val reader: DirectoryReader = writer.reader
        val searcher = newSearcher(reader)

        assertNumberOfHits(searcher, createQuery("field", -70, 0), 2)
        assertNumberOfHits(searcher, createQuery("field", -2, 35), 2)

        assertNumberOfHits(searcher, createQuery("field", -80, 35), 4)
        assertNumberOfHits(searcher, createQuery("field", Long.MIN_VALUE, Long.MAX_VALUE), 4)

        writer.close()
        reader.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testNoDocuments() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        writer.addDocument(Document())
        val reader: IndexReader = writer.reader
        val searcher = newSearcher(reader)
        val query = createQuery("foo", 2, 4)
        val w = searcher.createWeight(searcher.rewrite(query), ScoreMode.COMPLETE, 1f)
        assertNull(w.scorer(searcher.indexReader.leaves()[0]))

        writer.close()
        reader.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testRewriteExhaustiveRange() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        writer.addDocument(Document())
        val reader: IndexReader = writer.reader

        val query = createQuery("field", Long.MIN_VALUE, Long.MAX_VALUE)
        val rewrittenQuery = query.rewrite(newSearcher(reader))
        assertEquals(FieldExistsQuery("field"), rewrittenQuery)

        writer.close()
        reader.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testRewriteFallbackQuery() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        writer.addDocument(Document())
        val reader: IndexReader = writer.reader

        val fallbackQuery: Query = BooleanQuery.Builder().build()
        val query: Query = IndexSortSortedNumericDocValuesRangeQuery("field", 1, 42, fallbackQuery)

        val rewrittenQuery = query.rewrite(newSearcher(reader))
        assertNotEquals(query, rewrittenQuery)
        assertTrue(rewrittenQuery is IndexSortSortedNumericDocValuesRangeQuery)

        val rangeQuery = rewrittenQuery as IndexSortSortedNumericDocValuesRangeQuery
        assertEquals(MatchNoDocsQuery(), rangeQuery.fallbackQuery)

        writer.close()
        reader.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testNoIndexSort() {
        val dir: Directory = newDirectory()

        val writer = RandomIndexWriter(random(), dir)
        writer.addDocument(createDocument("field", 0))

        testIndexSortOptimizationDeactivated(writer)

        writer.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testIndexSortOnWrongField() {
        val dir: Directory = newDirectory()

        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val indexSort = Sort(SortedNumericSortField("other-field", SortField.Type.LONG))
        iwc.setIndexSort(indexSort)

        val writer = RandomIndexWriter(random(), dir, iwc)
        writer.addDocument(createDocument("field", 0))

        testIndexSortOptimizationDeactivated(writer)

        writer.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testOtherSortTypes() {
        for (type in arrayOf(SortField.Type.FLOAT, SortField.Type.DOUBLE)) {
            val dir: Directory = newDirectory()

            val iwc = IndexWriterConfig(MockAnalyzer(random()))
            val indexSort = Sort(SortedNumericSortField("field", type))
            iwc.setIndexSort(indexSort)

            val writer = RandomIndexWriter(random(), dir, iwc)
            writer.addDocument(createDocument("field", 0))

            testIndexSortOptimizationDeactivated(writer)

            writer.close()
            dir.close()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testMultiDocValues() {
        val dir: Directory = newDirectory()

        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val indexSort = Sort(SortedNumericSortField("field", SortField.Type.LONG))
        iwc.setIndexSort(indexSort)
        val writer = RandomIndexWriter(random(), dir, iwc)

        val doc = Document()
        doc.add(SortedNumericDocValuesField("field", 0))
        doc.add(SortedNumericDocValuesField("field", 10))
        writer.addDocument(doc)

        testIndexSortOptimizationDeactivated(writer)

        writer.close()
        dir.close()
    }

    @Throws(IOException::class)
    fun testIndexSortOptimizationDeactivated(writer: RandomIndexWriter) {
        val reader: DirectoryReader = writer.reader
        val searcher = newSearcher(reader)

        val query = createQuery("field", 0, 0)
        val weight = query.createWeight(searcher, ScoreMode.TOP_SCORES, 1.0f)

        for (context: LeafReaderContext in searcher.indexReader.leaves()) {
            val scorer = weight.scorer(context)
            assertNotNull(scorer!!.twoPhaseIterator())
        }

        reader.close()
    }

    @Test
    @Throws(IOException::class)
    fun testFallbackCount() {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val indexSort = Sort(SortedNumericSortField("field", SortField.Type.LONG))
        iwc.setIndexSort(indexSort)
        val writer = RandomIndexWriter(random(), dir, iwc)
        val doc = Document()
        doc.add(SortedNumericDocValuesField("field", 10))
        writer.addDocument(doc)
        val reader: IndexReader = writer.reader
        val searcher = newSearcher(reader)

        val fallbackQuery: Query = MatchNoDocsQuery()
        val query: Query = IndexSortSortedNumericDocValuesRangeQuery("another", 1, 42, fallbackQuery)
        val weight = query.createWeight(searcher, ScoreMode.COMPLETE, 1.0f)
        for (context: LeafReaderContext in searcher.leafContexts) {
            assertEquals(0, weight.count(context))
        }

        writer.close()
        reader.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testCompareCount() {
        val iters = atLeast(10)
        for (iter in 0..<iters) {
            val dir: Directory = newDirectory()
            val iwc = IndexWriterConfig(MockAnalyzer(random()))
            val sortField = SortedNumericSortField("field", SortField.Type.LONG)
            val enableMissingValue = random().nextBoolean()
            if (enableMissingValue) {
                val missingValue =
                    if (random().nextBoolean()) {
                        TestUtil.nextLong(random(), -100, 10000)
                    } else {
                        if (random().nextBoolean()) Long.MIN_VALUE else Long.MAX_VALUE
                    }
                sortField.missingValue = missingValue
            }
            iwc.setIndexSort(Sort(sortField))

            val writer = RandomIndexWriter(random(), dir, iwc)

            val numDocs = atLeast(100)
            for (i in 0..<numDocs) {
                var doc = Document()
                val numValues = TestUtil.nextInt(random(), 0, 1)
                for (j in 0..<numValues) {
                    val value = TestUtil.nextLong(random(), -100, 10000)
                    doc = createSNDVAndPointDocument("field", value)
                }
                writer.addDocument(doc)
            }

            if (random().nextBoolean()) {
                writer.deleteDocuments(LongPoint.newRangeQuery("field", 0L, 10L))
            }

            val reader: IndexReader = writer.reader
            val searcher = newSearcher(reader)
            writer.close()

            for (i in 0..<100) {
                val min =
                    if (random().nextBoolean()) Long.MIN_VALUE else TestUtil.nextLong(random(), -100, 10000)
                val max =
                    if (random().nextBoolean()) Long.MAX_VALUE else TestUtil.nextLong(random(), -100, 10000)
                val q1: Query = LongPoint.newRangeQuery("field", min, max)

                val fallbackQuery: Query = LongPoint.newRangeQuery("field", min, max)
                val q2: Query =
                    IndexSortSortedNumericDocValuesRangeQuery("field", min, max, fallbackQuery)
                val weight1 = q1.createWeight(searcher, ScoreMode.COMPLETE, 1.0f)
                val weight2 = q2.createWeight(searcher, ScoreMode.COMPLETE, 1.0f)
                assertSameCount(weight1, weight2, searcher)
            }

            reader.close()
            dir.close()
        }
    }

    @Throws(IOException::class)
    private fun assertSameCount(weight1: Weight, weight2: Weight, searcher: IndexSearcher) {
        for (context: LeafReaderContext in searcher.leafContexts) {
            assertEquals(weight1.count(context), weight2.count(context))
        }
    }

    @Test
    @Throws(IOException::class)
    fun testCountBoundary() {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val sortField = SortedNumericSortField("field", SortField.Type.LONG)
        val useLower = random().nextBoolean()
        val lowerValue = 1L
        val upperValue = 100L
        sortField.missingValue = if (useLower) lowerValue else upperValue
        val indexSort = Sort(sortField)
        iwc.setIndexSort(indexSort)
        val writer = RandomIndexWriter(random(), dir, iwc)

        writer.addDocument(createSNDVAndPointDocument("field", random().nextLong(lowerValue, upperValue)))
        writer.addDocument(createSNDVAndPointDocument("field", random().nextLong(lowerValue, upperValue)))
        writer.addDocument(createMissingValueDocument())

        val reader: IndexReader = writer.reader
        val searcher = newSearcher(reader)

        val fallbackQuery: Query = LongPoint.newRangeQuery("field", lowerValue, upperValue)
        val query: Query =
            IndexSortSortedNumericDocValuesRangeQuery("field", lowerValue, upperValue, fallbackQuery)
        val weight = query.createWeight(searcher, ScoreMode.COMPLETE, 1.0f)
        var count = 0
        for (context: LeafReaderContext in searcher.leafContexts) {
            count += weight.count(context)
        }
        assertEquals(2, count)

        writer.close()
        reader.close()
        dir.close()
    }

    private fun createMissingValueDocument(): Document {
        val doc = Document()
        doc.add(StringField("foo", "fox", Field.Store.YES))
        return doc
    }

    private fun createSNDVAndPointDocument(field: String, value: Long): Document {
        val doc = Document()
        doc.add(SortedNumericDocValuesField(field, value))
        doc.add(LongPoint(field, value))
        return doc
    }

    private fun createDocument(field: String, value: Long): Document {
        val doc = Document()
        doc.add(SortedNumericDocValuesField(field, value))
        return doc
    }

    private fun createQuery(field: String, lowerValue: Long, upperValue: Long): Query {
        val fallbackQuery: Query =
            SortedNumericDocValuesField.newSlowRangeQuery(field, lowerValue, upperValue)
        return IndexSortSortedNumericDocValuesRangeQuery(field, lowerValue, upperValue, fallbackQuery)
    }

    @Test
    @Throws(Exception::class)
    fun testCountWithBkdAsc() {
        doTestCountWithBkd(false)
    }

    @Test
    @Throws(Exception::class)
    fun testCountWithBkdDesc() {
        doTestCountWithBkd(true)
    }

    @Throws(Exception::class)
    fun doTestCountWithBkd(reverse: Boolean) {
        val filedName = "field"
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val indexSort = Sort(SortedNumericSortField(filedName, SortField.Type.LONG, reverse))
        iwc.setIndexSort(indexSort)
        val writer = RandomIndexWriter(random(), dir, iwc)
        addDocWithBkd(writer, filedName, 7, 500)
        addDocWithBkd(writer, filedName, 5, 600)
        addDocWithBkd(writer, filedName, 11, 700)
        addDocWithBkd(writer, filedName, 13, 800)
        addDocWithBkd(writer, filedName, 9, 900)
        writer.flush()
        writer.forceMerge(1)
        val reader: IndexReader = writer.reader
        val searcher = newSearcher(reader)

        var fallbackQuery: Query = LongPoint.newRangeQuery(filedName, 7, 9)
        var query: Query = IndexSortSortedNumericDocValuesRangeQuery(filedName, 7, 9, fallbackQuery)
        var weight = query.createWeight(searcher, ScoreMode.COMPLETE, 1.0f)
        for (context: LeafReaderContext in searcher.leafContexts) {
            assertEquals(1400, weight.count(context))
        }

        fallbackQuery = LongPoint.newRangeQuery(filedName, 6, 10)
        query = IndexSortSortedNumericDocValuesRangeQuery(filedName, 6, 10, fallbackQuery)
        weight = query.createWeight(searcher, ScoreMode.COMPLETE, 1.0f)
        for (context: LeafReaderContext in searcher.leafContexts) {
            assertEquals(1400, weight.count(context))
        }

        fallbackQuery = LongPoint.newRangeQuery(filedName, 7, 10)
        query = IndexSortSortedNumericDocValuesRangeQuery(filedName, 7, 10, fallbackQuery)
        weight = query.createWeight(searcher, ScoreMode.COMPLETE, 1.0f)
        for (context: LeafReaderContext in searcher.leafContexts) {
            assertEquals(1400, weight.count(context))
        }

        fallbackQuery = LongPoint.newRangeQuery(filedName, 6, 9)
        query = IndexSortSortedNumericDocValuesRangeQuery(filedName, 6, 9, fallbackQuery)
        weight = query.createWeight(searcher, ScoreMode.COMPLETE, 1.0f)
        for (context: LeafReaderContext in searcher.leafContexts) {
            assertEquals(1400, weight.count(context))
        }

        fallbackQuery = LongPoint.newRangeQuery(filedName, 5, 8)
        query = IndexSortSortedNumericDocValuesRangeQuery(filedName, 5, 8, fallbackQuery)
        weight = query.createWeight(searcher, ScoreMode.COMPLETE, 1.0f)
        for (context: LeafReaderContext in searcher.leafContexts) {
            assertEquals(1100, weight.count(context))
        }

        fallbackQuery = LongPoint.newRangeQuery(filedName, 4, 8)
        query = IndexSortSortedNumericDocValuesRangeQuery(filedName, 4, 8, fallbackQuery)
        weight = query.createWeight(searcher, ScoreMode.COMPLETE, 1.0f)
        for (context: LeafReaderContext in searcher.leafContexts) {
            assertEquals(1100, weight.count(context))
        }

        fallbackQuery = LongPoint.newRangeQuery(filedName, 10, 13)
        query = IndexSortSortedNumericDocValuesRangeQuery(filedName, 10, 13, fallbackQuery)
        weight = query.createWeight(searcher, ScoreMode.COMPLETE, 1.0f)
        for (context: LeafReaderContext in searcher.leafContexts) {
            assertEquals(1500, weight.count(context))
        }

        fallbackQuery = LongPoint.newRangeQuery(filedName, 10, 14)
        query = IndexSortSortedNumericDocValuesRangeQuery(filedName, 10, 14, fallbackQuery)
        weight = query.createWeight(searcher, ScoreMode.COMPLETE, 1.0f)
        for (context: LeafReaderContext in searcher.leafContexts) {
            assertEquals(1500, weight.count(context))
        }

        fallbackQuery = LongPoint.newRangeQuery(filedName, 2, 14)
        query = IndexSortSortedNumericDocValuesRangeQuery(filedName, 2, 14, fallbackQuery)
        weight = query.createWeight(searcher, ScoreMode.COMPLETE, 1.0f)
        for (context: LeafReaderContext in searcher.leafContexts) {
            assertEquals(3500, weight.count(context))
        }

        fallbackQuery = LongPoint.newRangeQuery(filedName, 2, 14)
        query = IndexSortSortedNumericDocValuesRangeQuery(filedName, 2, 14, fallbackQuery)
        weight = query.createWeight(searcher, ScoreMode.COMPLETE, 1.0f)
        for (context: LeafReaderContext in searcher.leafContexts) {
            assertEquals(3500, weight.count(context))
        }

        fallbackQuery = LongPoint.newRangeQuery(filedName, 2, 3)
        query = IndexSortSortedNumericDocValuesRangeQuery(filedName, 2, 3, fallbackQuery)
        weight = query.createWeight(searcher, ScoreMode.COMPLETE, 1.0f)
        for (context: LeafReaderContext in searcher.leafContexts) {
            assertEquals(0, weight.count(context))
        }

        fallbackQuery = LongPoint.newRangeQuery(filedName, 14, 15)
        query = IndexSortSortedNumericDocValuesRangeQuery(filedName, 14, 15, fallbackQuery)
        weight = query.createWeight(searcher, ScoreMode.COMPLETE, 1.0f)
        for (context: LeafReaderContext in searcher.leafContexts) {
            assertEquals(0, weight.count(context))
        }

        writer.close()
        reader.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testRandomCountWithBkdAsc() {
        doTestRandomCountWithBkd(false)
    }

    @Test
    @Throws(Exception::class)
    fun testRandomCountWithBkdDesc() {
        doTestRandomCountWithBkd(true)
    }

    @Throws(Exception::class)
    private fun doTestRandomCountWithBkd(reverse: Boolean) {
        val filedName = "field"
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val indexSort = Sort(SortedNumericSortField(filedName, SortField.Type.LONG, reverse))
        iwc.setIndexSort(indexSort)
        val writer = RandomIndexWriter(random(), dir, iwc)
        val random: Random = random()
        for (i in 0..<100) {
            addDocWithBkd(writer, filedName, random.nextInt(1000).toLong(), random.nextInt(1000))
        }
        writer.flush()
        writer.forceMerge(1)
        val reader: IndexReader = writer.reader
        val searcher = newSearcher(reader)

        for (i in 0..<100) {
            val random1 = random.nextInt(1100)
            val random2 = random.nextInt(1100)
            val low = min(random1, random2)
            val upper = max(random1, random2)
            val rangeQuery: Query = LongPoint.newRangeQuery(filedName, low.toLong(), upper.toLong())
            val indexSortRangeQuery: Query =
                IndexSortSortedNumericDocValuesRangeQuery(filedName, low.toLong(), upper.toLong(), rangeQuery)
            val indexSortRangeQueryWeight =
                indexSortRangeQuery.createWeight(searcher, ScoreMode.COMPLETE, 1.0f)
            val rangeQueryWeight = rangeQuery.createWeight(searcher, ScoreMode.COMPLETE, 1.0f)
            for (context: LeafReaderContext in searcher.leafContexts) {
                assertEquals(rangeQueryWeight.count(context), indexSortRangeQueryWeight.count(context))
            }
        }

        writer.close()
        reader.close()
        dir.close()
    }

    @Throws(IOException::class)
    private fun addDocWithBkd(indexWriter: RandomIndexWriter, field: String, value: Long, repeat: Int) {
        for (i in 0..<repeat) {
            val doc = Document()
            doc.add(SortedNumericDocValuesField(field, value))
            doc.add(LongPoint(field, value))
            indexWriter.addDocument(doc)
        }
    }
}
