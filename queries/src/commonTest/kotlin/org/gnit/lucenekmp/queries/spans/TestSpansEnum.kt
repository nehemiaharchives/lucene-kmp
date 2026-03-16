package org.gnit.lucenekmp.queries.spans

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.CheckHits
import org.gnit.lucenekmp.tests.util.English
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/** Tests Spans (v2) */
class TestSpansEnum : LuceneTestCase() {
    private lateinit var searcher: IndexSearcher
    private lateinit var reader: IndexReader
    private lateinit var directory: Directory

    @BeforeTest
    fun beforeClass() {
        directory = newDirectory()
        val writer =
            RandomIndexWriter(
                random(),
                directory,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setMaxBufferedDocs(TestUtil.nextInt(random(), 100, 1000))
                    .setMergePolicy(newLogMergePolicy()),
            )
        for (i in 0..<10) {
            val doc = Document()
            doc.add(newTextField("field", English.intToEnglish(i), Field.Store.YES))
            writer.addDocument(doc)
        }
        for (i in 100..<110) {
            val doc = Document()
            doc.add(newTextField("field", English.intToEnglish(i), Field.Store.YES))
            writer.addDocument(doc)
        }
        reader = writer.reader
        searcher = newSearcher(reader)
        writer.close()
    }

    @AfterTest
    fun afterClass() {
        reader.close()
        directory.close()
    }

    @Throws(IOException::class)
    private fun checkHits(query: Query, results: IntArray) {
        CheckHits.checkHits(random(), query, "field", searcher, results)
    }

    @Test
    fun testSpansEnumOr1() {
        checkHits(
            SpanTestUtil.spanOrQuery("field", "one", "two"),
            intArrayOf(1, 2, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19),
        )
    }

    @Test
    fun testSpansEnumOr2() {
        checkHits(
            SpanTestUtil.spanOrQuery("field", "one", "eleven"),
            intArrayOf(1, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19),
        )
    }

    @Test
    fun testSpansEnumOr3() {
        checkHits(SpanTestUtil.spanOrQuery("field", "twelve", "eleven"), intArrayOf())
    }

    fun spanTQ(s: String): SpanQuery {
        return SpanTestUtil.spanTermQuery("field", s)
    }

    @Test
    fun testSpansEnumOrNot1() {
        checkHits(
            SpanTestUtil.spanNotQuery(
                SpanTestUtil.spanOrQuery("field", "one", "two"),
                SpanTestUtil.spanTermQuery("field", "one"),
            ),
            intArrayOf(2, 12),
        )
    }

    @Test
    fun testSpansEnumNotBeforeAfter1() {
        checkHits(
            SpanTestUtil.spanNotQuery(
                SpanTestUtil.spanTermQuery("field", "hundred"),
                SpanTestUtil.spanTermQuery("field", "one"),
            ),
            intArrayOf(10, 11, 12, 13, 14, 15, 16, 17, 18, 19),
        )
    }

    @Test
    fun testSpansEnumNotBeforeAfter2() {
        checkHits(
            SpanTestUtil.spanNotQuery(
                SpanTestUtil.spanTermQuery("field", "hundred"),
                SpanTestUtil.spanTermQuery("field", "one"),
                1,
                0,
            ),
            intArrayOf(),
        )
    }

    @Test
    fun testSpansEnumNotBeforeAfter3() {
        checkHits(
            SpanTestUtil.spanNotQuery(
                SpanTestUtil.spanTermQuery("field", "hundred"),
                SpanTestUtil.spanTermQuery("field", "one"),
                0,
                1,
            ),
            intArrayOf(10, 12, 13, 14, 15, 16, 17, 18, 19),
        )
    }
}
