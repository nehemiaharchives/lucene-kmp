package org.gnit.lucenekmp.queries.spans

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.FuzzyQuery
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.PrefixQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.ScoreMode
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.CheckHits
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TestSpans : LuceneTestCase() {
    private lateinit var searcher: IndexSearcher
    private lateinit var reader: IndexReader
    private lateinit var directory: Directory

    companion object {
        const val field: String = "field"
    }

    @BeforeTest
    fun setUp() {
        directory = newDirectory()
        val writer =
            RandomIndexWriter(
                random(),
                directory,
                newIndexWriterConfig(MockAnalyzer(random())).setMergePolicy(newLogMergePolicy()),
            )
        for (i in docFields.indices) {
            val doc = Document()
            doc.add(newTextField(field, docFields[i], Field.Store.YES))
            writer.addDocument(doc)
        }
        writer.forceMerge(1)
        reader = writer.reader
        writer.close()
        searcher = newSearcher(getOnlyLeafReader(reader))
    }

    @AfterTest
    fun tearDown() {
        reader.close()
        directory.close()
    }

    private val docFields =
        arrayOf(
            "w1 w2 w3 w4 w5",
            "w1 w3 w2 w3",
            "w1 xx w2 yy w3",
            "w1 w3 xx w2 yy w3",
            "u2 u2 u1",
            "u2 xx u2 u1",
            "u2 u2 xx u1",
            "u2 xx u2 yy u1",
            "u2 xx u1 u2",
            "u2 u1 xx u2",
            "u1 u2 xx u2",
            "t1 t2 t1 t3 t2 t3",
            "s2 s1 s1 xx xx s2 xx s2 xx s1 xx xx xx xx xx s2 xx",
            "r1 s11",
            "r1 s21",
        )

    @Throws(IOException::class)
    private fun checkHits(query: Query, results: IntArray) {
        CheckHits.checkHits(random(), query, field, searcher, results)
    }

    @Throws(IOException::class)
    private fun orderedSlopTest3SQ(
        q1: SpanQuery,
        q2: SpanQuery,
        q3: SpanQuery,
        slop: Int,
        expectedDocs: IntArray,
    ) {
        val query = SpanTestUtil.spanNearOrderedQuery(slop, q1, q2, q3)
        checkHits(query, expectedDocs)
    }

    @Throws(IOException::class)
    fun orderedSlopTest3(slop: Int, expectedDocs: IntArray) {
        orderedSlopTest3SQ(
            SpanTestUtil.spanTermQuery(field, "w1"),
            SpanTestUtil.spanTermQuery(field, "w2"),
            SpanTestUtil.spanTermQuery(field, "w3"),
            slop,
            expectedDocs,
        )
    }

    @Throws(IOException::class)
    fun orderedSlopTest3Equal(slop: Int, expectedDocs: IntArray) {
        orderedSlopTest3SQ(
            SpanTestUtil.spanTermQuery(field, "w1"),
            SpanTestUtil.spanTermQuery(field, "w3"),
            SpanTestUtil.spanTermQuery(field, "w3"),
            slop,
            expectedDocs,
        )
    }

    @Throws(IOException::class)
    fun orderedSlopTest1Equal(slop: Int, expectedDocs: IntArray) {
        orderedSlopTest3SQ(
            SpanTestUtil.spanTermQuery(field, "u2"),
            SpanTestUtil.spanTermQuery(field, "u2"),
            SpanTestUtil.spanTermQuery(field, "u1"),
            slop,
            expectedDocs,
        )
    }

    @Test
    fun testSpanNearOrdered01() {
        orderedSlopTest3(0, intArrayOf(0))
    }

    @Test
    fun testSpanNearOrdered02() {
        orderedSlopTest3(1, intArrayOf(0, 1))
    }

    @Test
    fun testSpanNearOrdered03() {
        orderedSlopTest3(2, intArrayOf(0, 1, 2))
    }

    @Test
    fun testSpanNearOrdered04() {
        orderedSlopTest3(3, intArrayOf(0, 1, 2, 3))
    }

    @Test
    fun testSpanNearOrdered05() {
        orderedSlopTest3(4, intArrayOf(0, 1, 2, 3))
    }

    @Test
    fun testSpanNearOrderedEqual01() {
        orderedSlopTest3Equal(0, intArrayOf())
    }

    @Test
    fun testSpanNearOrderedEqual02() {
        orderedSlopTest3Equal(1, intArrayOf(1))
    }

    @Test
    fun testSpanNearOrderedEqual03() {
        orderedSlopTest3Equal(2, intArrayOf(1))
    }

    @Test
    fun testSpanNearOrderedEqual04() {
        orderedSlopTest3Equal(3, intArrayOf(1, 3))
    }

    @Test
    fun testSpanNearOrderedEqual11() {
        orderedSlopTest1Equal(0, intArrayOf(4))
    }

    @Test
    fun testSpanNearOrderedEqual12() {
        orderedSlopTest1Equal(0, intArrayOf(4))
    }

    @Test
    fun testSpanNearOrderedEqual13() {
        orderedSlopTest1Equal(1, intArrayOf(4, 5, 6))
    }

    @Test
    fun testSpanNearOrderedEqual14() {
        orderedSlopTest1Equal(2, intArrayOf(4, 5, 6, 7))
    }

    @Test
    fun testSpanNearOrderedEqual15() {
        orderedSlopTest1Equal(3, intArrayOf(4, 5, 6, 7))
    }

    @Test
    fun testSpanNearOrderedOverlap() {
        val query = SpanTestUtil.spanNearOrderedQuery(field, 1, "t1", "t2", "t3")

        val spans =
            query
                .createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1f)
                .getSpans(searcher.indexReader.leaves()[0], SpanWeight.Postings.POSITIONS)!!

        assertEquals(11, spans.nextDoc(), "first doc")
        assertEquals(0, spans.nextStartPosition(), "first start")
        assertEquals(4, spans.endPosition(), "first end")

        assertEquals(2, spans.nextStartPosition(), "second start")
        assertEquals(6, spans.endPosition(), "second end")

        SpanTestUtil.assertFinished(spans)
    }

    @Test
    fun testSpanNearUnOrdered() {
        // See http://www.gossamer-threads.com/lists/lucene/java-dev/52270 for discussion about this
        // test
        var senq = SpanTestUtil.spanNearUnorderedQuery(field, 0, "u1", "u2")
        var spans =
            senq.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1f)
                .getSpans(searcher.indexReader.leaves()[0], SpanWeight.Postings.POSITIONS)!!
        SpanTestUtil.assertNext(spans, 4, 1, 3)
        SpanTestUtil.assertNext(spans, 5, 2, 4)
        SpanTestUtil.assertNext(spans, 8, 2, 4)
        SpanTestUtil.assertNext(spans, 9, 0, 2)
        SpanTestUtil.assertNext(spans, 10, 0, 2)
        SpanTestUtil.assertFinished(spans)

        senq = SpanTestUtil.spanNearUnorderedQuery(1, senq, SpanTestUtil.spanTermQuery(field, "u2"))
        spans =
            senq.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1f)
                .getSpans(searcher.indexReader.leaves()[0], SpanWeight.Postings.POSITIONS)!!
        SpanTestUtil.assertNext(spans, 4, 0, 3)
        SpanTestUtil.assertNext(spans, 4, 1, 3)
        SpanTestUtil.assertNext(spans, 5, 0, 4)
        SpanTestUtil.assertNext(spans, 5, 2, 4)
        SpanTestUtil.assertNext(spans, 8, 0, 4)
        SpanTestUtil.assertNext(spans, 8, 2, 4)
        SpanTestUtil.assertNext(spans, 9, 0, 2)
        SpanTestUtil.assertNext(spans, 9, 0, 4)
        SpanTestUtil.assertNext(spans, 10, 0, 2)
        SpanTestUtil.assertFinished(spans)
    }

    @Throws(Exception::class)
    private fun orSpans(terms: Array<String>): Spans? {
        return SpanTestUtil.spanOrQuery(field, *terms)
            .createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1f)
            .getSpans(searcher.indexReader.leaves()[0], SpanWeight.Postings.POSITIONS)
    }

    @Test
    fun testSpanOrEmpty() {
        val spans = orSpans(arrayOf())
        SpanTestUtil.assertFinished(spans)
    }

    @Test
    fun testSpanOrSingle() {
        val spans = orSpans(arrayOf("w5"))!!
        SpanTestUtil.assertNext(spans, 0, 4, 5)
        SpanTestUtil.assertFinished(spans)
    }

    @Test
    fun testSpanOrDouble() {
        val spans = orSpans(arrayOf("w5", "yy"))!!
        SpanTestUtil.assertNext(spans, 0, 4, 5)
        SpanTestUtil.assertNext(spans, 2, 3, 4)
        SpanTestUtil.assertNext(spans, 3, 4, 5)
        SpanTestUtil.assertNext(spans, 7, 3, 4)
        SpanTestUtil.assertFinished(spans)
    }

    @Test
    fun testSpanOrDoubleAdvance() {
        val spans = orSpans(arrayOf("w5", "yy"))!!
        assertEquals(3, spans.advance(3), "initial advance")
        SpanTestUtil.assertNext(spans, 3, 4, 5)
        SpanTestUtil.assertNext(spans, 7, 3, 4)
        SpanTestUtil.assertFinished(spans)
    }

    @Test
    fun testSpanOrUnused() {
        val spans = orSpans(arrayOf("w5", "unusedTerm", "yy"))!!
        SpanTestUtil.assertNext(spans, 0, 4, 5)
        SpanTestUtil.assertNext(spans, 2, 3, 4)
        SpanTestUtil.assertNext(spans, 3, 4, 5)
        SpanTestUtil.assertNext(spans, 7, 3, 4)
        SpanTestUtil.assertFinished(spans)
    }

    @Test
    fun testSpanOrTripleSameDoc() {
        val spans = orSpans(arrayOf("t1", "t2", "t3"))!!
        SpanTestUtil.assertNext(spans, 11, 0, 1)
        SpanTestUtil.assertNext(spans, 11, 1, 2)
        SpanTestUtil.assertNext(spans, 11, 2, 3)
        SpanTestUtil.assertNext(spans, 11, 3, 4)
        SpanTestUtil.assertNext(spans, 11, 4, 5)
        SpanTestUtil.assertNext(spans, 11, 5, 6)
        SpanTestUtil.assertFinished(spans)
    }

    // LUCENE-1404
    @Throws(IOException::class)
    private fun addDoc(writer: IndexWriter, id: String, text: String) {
        val doc = Document()
        doc.add(newStringField("id", id, Field.Store.YES))
        doc.add(newTextField("text", text, Field.Store.YES))
        writer.addDocument(doc)
    }

    // LUCENE-1404
    @Throws(Throwable::class)
    private fun hitCount(searcher: IndexSearcher, word: String): Long {
        return searcher.count(TermQuery(Term("text", word))).toLong()
    }

    // LUCENE-1404
    private fun createSpan(value: String): SpanQuery {
        return SpanTestUtil.spanTermQuery("text", value)
    }

    // LUCENE-1404
    private fun createSpan(slop: Int, ordered: Boolean, clauses: Array<SpanQuery>): SpanQuery {
        return if (ordered) {
            SpanTestUtil.spanNearOrderedQuery(slop, *clauses)
        } else {
            SpanTestUtil.spanNearUnorderedQuery(slop, *clauses)
        }
    }

    // LUCENE-1404
    private fun createSpan(slop: Int, ordered: Boolean, term1: String, term2: String): SpanQuery {
        return createSpan(slop, ordered, arrayOf(createSpan(term1), createSpan(term2)))
    }

    // LUCENE-1404
    @Test
    fun testNPESpanQuery() {
        val dir = newDirectory()
        val writer = IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random())))

        addDoc(writer, "1", "the big dogs went running to the market")
        addDoc(writer, "2", "the cat chased the mouse, then the cat ate the mouse quickly")

        writer.close()

        val reader = DirectoryReader.open(dir)
        val searcher = newSearcher(reader)

        assertEquals(2, hitCount(searcher, "the"))
        assertEquals(1, hitCount(searcher, "cat"))
        assertEquals(1, hitCount(searcher, "dogs"))
        assertEquals(0, hitCount(searcher, "rabbit"))

        assertEquals(
            1L,
            searcher.search(
                createSpan(
                    0,
                    true,
                    arrayOf(createSpan(4, false, "chased", "cat"), createSpan("ate")),
                ),
                10,
            ).totalHits.value,
        )
        reader.close()
        dir.close()
    }

    @Test
    fun testSpanNotWithMultiterm() {
        var q =
            SpanTestUtil.spanNotQuery(
                SpanTestUtil.spanTermQuery(field, "r1"),
                SpanMultiTermQueryWrapper(PrefixQuery(Term(field, "s1"))),
                3,
                3,
            )
        checkHits(q, intArrayOf(14))

        q =
            SpanTestUtil.spanNotQuery(
                SpanTestUtil.spanTermQuery(field, "r1"),
                SpanMultiTermQueryWrapper(FuzzyQuery(Term(field, "s12"), 1, 2)),
                3,
                3,
            )
        checkHits(q, intArrayOf(14))

        q =
            SpanTestUtil.spanNotQuery(
                SpanMultiTermQueryWrapper(PrefixQuery(Term(field, "r"))),
                SpanTestUtil.spanTermQuery(field, "s21"),
                3,
                3,
            )
        checkHits(q, intArrayOf(13))
    }

    @Test
    fun testSpanNots() {
        assertEquals(0, spanCount("s2", 0, "s2", 0, 0), "SpanNotIncludeExcludeSame1")
        assertEquals(0, spanCount("s2", 0, "s2", 10, 10), "SpanNotIncludeExcludeSame2")

        assertEquals(1, spanCount("s2", 0, "s1", 6, 0), "SpanNotS2NotS1_6_0")
        assertEquals(2, spanCount("s2", 0, "s1", 5, 0), "SpanNotS2NotS1_5_0")
        assertEquals(3, spanCount("s2", 0, "s1", 3, 0), "SpanNotS2NotS1_3_0")
        assertEquals(4, spanCount("s2", 0, "s1", 2, 0), "SpanNotS2NotS1_2_0")
        assertEquals(4, spanCount("s2", 0, "s1", 0, 0), "SpanNotS2NotS1_0_0")

        assertEquals(2, spanCount("s2", 0, "s1", 3, 1), "SpanNotS2NotS1_3_1")
        assertEquals(3, spanCount("s2", 0, "s1", 2, 1), "SpanNotS2NotS1_2_1")
        assertEquals(3, spanCount("s2", 0, "s1", 1, 1), "SpanNotS2NotS1_1_1")
        assertEquals(0, spanCount("s2", 0, "s1", 10, 10), "SpanNotS2NotS1_10_10")

        assertEquals(0, spanCount("s1", 0, "s2", 10, 10), "SpanNotS1NotS2_10_10")
        assertEquals(3, spanCount("s1", 0, "s2", 0, 1), "SpanNotS1NotS2_0_1")
        assertEquals(3, spanCount("s1", 0, "s2", 0, 2), "SpanNotS1NotS2_0_2")
        assertEquals(2, spanCount("s1", 0, "s2", 0, 3), "SpanNotS1NotS2_0_3")
        assertEquals(1, spanCount("s1", 0, "s2", 0, 4), "SpanNotS1NotS2_0_4")
        assertEquals(0, spanCount("s1", 0, "s2", 0, 8), "SpanNotS1NotS2_0_8")

        assertEquals(3, spanCount("s1", 0, "s3", 8, 8), "SpanNotS1NotS3_8_8")
        assertEquals(0, spanCount("s3", 0, "s1", 8, 8), "SpanNotS3NotS1_8_8")

        assertEquals(1, spanCount("s2 s1", 10, "xx", 0, 0), "SpanNotS2S1NotXXNeg_0_0")
        assertEquals(1, spanCount("s2 s1", 10, "xx", -1, -1), "SpanNotS2S1NotXXNeg_1_1")
        assertEquals(2, spanCount("s2 s1", 10, "xx", 0, -2), "SpanNotS2S1NotXXNeg_0_2")
        assertEquals(2, spanCount("s2 s1", 10, "xx", -1, -2), "SpanNotS2S1NotXXNeg_1_2")
        assertEquals(2, spanCount("s2 s1", 10, "xx", -2, -1), "SpanNotS2S1NotXXNeg_2_1")
        assertEquals(2, spanCount("s2 s1", 10, "xx", -3, -1), "SpanNotS2S1NotXXNeg_3_1")
        assertEquals(2, spanCount("s2 s1", 10, "xx", -1, -3), "SpanNotS2S1NotXXNeg_1_3")
        assertEquals(3, spanCount("s2 s1", 10, "xx", -2, -2), "SpanNotS2S1NotXXNeg_2_2")
    }

    @Throws(IOException::class)
    private fun spanCount(include: String, slop: Int, exclude: String, pre: Int, post: Int): Int {
        val includeTerms = include.split(" +".toRegex()).toTypedArray()
        val iq =
            if (includeTerms.size == 1) {
                SpanTestUtil.spanTermQuery(field, include)
            } else {
                SpanTestUtil.spanNearOrderedQuery(field, slop, *includeTerms)
            }
        val eq = SpanTestUtil.spanTermQuery(field, exclude)
        val snq = SpanTestUtil.spanNotQuery(iq, eq, pre, post)
        val spans =
            snq.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1f)
                .getSpans(searcher.indexReader.leaves()[0], SpanWeight.Postings.POSITIONS)

        var i = 0
        if (spans != null) {
            while (spans.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                while (spans.nextStartPosition() != Spans.NO_MORE_POSITIONS) {
                    i++
                }
            }
        }
        return i
    }
}
