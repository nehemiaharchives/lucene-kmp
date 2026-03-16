package org.gnit.lucenekmp.queries.spans

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.Explanation
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.ScoreMode
import org.gnit.lucenekmp.search.Scorer
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.search.Weight
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.CheckHits
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestNearSpansOrdered : LuceneTestCase() {
    protected lateinit var searcher: IndexSearcher
    protected lateinit var directory: Directory
    protected lateinit var reader: IndexReader

    companion object {
        const val FIELD: String = "field"
    }

    @AfterTest
    fun tearDownTest() {
        reader.close()
        directory.close()
    }

    @BeforeTest
    fun setUpTest() {
        directory = newDirectory()
        val writer = RandomIndexWriter(
            random(),
            directory,
            newIndexWriterConfig(MockAnalyzer(random())).setMergePolicy(newLogMergePolicy())
        )
        for (fieldValue in docFields) {
            val doc = Document()
            doc.add(newTextField(FIELD, fieldValue, Field.Store.NO))
            writer.addDocument(doc)
        }
        writer.forceMerge(1)
        reader = writer.reader
        writer.close()
        searcher = newSearcher(getOnlyLeafReader(reader))
    }

    protected val docFields = arrayOf(
        "w1 w2 w3 w4 w5",
        "w1 w3 w2 w3 zz",
        "w1 xx w2 yy w3",
        "w1 w3 xx w2 yy w3 zz",
        "t1 t2 t2 t1",
        "g x x g g x x x g g x x g",
        "go to webpage",
    )

    protected fun makeQuery(s1: String, s2: String, s3: String, slop: Int, inOrder: Boolean): SpanNearQuery {
        return SpanNearQuery(
            arrayOf(
                SpanTermQuery(Term(FIELD, s1)),
                SpanTermQuery(Term(FIELD, s2)),
                SpanTermQuery(Term(FIELD, s3)),
            ),
            slop,
            inOrder,
        )
    }

    protected fun makeQuery(): SpanNearQuery {
        return makeQuery("w1", "w2", "w3", 1, true)
    }

    protected fun makeOverlappedQuery(
        sqt1: String,
        sqt2: String,
        sqOrdered: Boolean,
        t3: String,
        ordered: Boolean,
    ): SpanNearQuery {
        return SpanNearQuery(
            arrayOf(
                SpanNearQuery(
                    arrayOf(
                        SpanTermQuery(Term(FIELD, sqt1)),
                        SpanTermQuery(Term(FIELD, sqt2)),
                    ),
                    1,
                    sqOrdered,
                ),
                SpanTermQuery(Term(FIELD, t3)),
            ),
            0,
            ordered,
        )
    }

    @Test
    fun testSpanNearQuery() {
        val q = makeQuery()
        CheckHits.checkHits(random(), q, FIELD, searcher, intArrayOf(0, 1))
    }

    fun s(span: Spans): String {
        return s(span.docID(), span.startPosition(), span.endPosition())
    }

    fun s(doc: Int, start: Int, end: Int): String {
        return "s($doc,$start,$end)"
    }

    @Test
    fun testNearSpansNext() {
        val q = makeQuery()
        val span = q.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1f)
            .getSpans(searcher.indexReader.leaves()[0], SpanWeight.Postings.POSITIONS)!!
        SpanTestUtil.assertNext(span, 0, 0, 3)
        SpanTestUtil.assertNext(span, 1, 0, 4)
        SpanTestUtil.assertFinished(span)
    }

    /**
     * test does not imply that skipTo(doc+1) should work exactly the same as next -- it's only
     * applicable in this case since we know doc does not contain more than one span
     */
    @Test
    fun testNearSpansAdvanceLikeNext() {
        val q = makeQuery()
        val span = q.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1f)
            .getSpans(searcher.indexReader.leaves()[0], SpanWeight.Postings.POSITIONS)!!
        assertEquals(0, span.advance(0))
        assertEquals(0, span.nextStartPosition())
        assertEquals(s(0, 0, 3), s(span))
        assertEquals(1, span.advance(1))
        assertEquals(0, span.nextStartPosition())
        assertEquals(s(1, 0, 4), s(span))
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, span.advance(2))
    }

    @Test
    fun testNearSpansNextThenAdvance() {
        val q = makeQuery()
        val span = q.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1f)
            .getSpans(searcher.indexReader.leaves()[0], SpanWeight.Postings.POSITIONS)!!
        assertTrue(span.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(0, span.nextStartPosition())
        assertEquals(s(0, 0, 3), s(span))
        assertTrue(span.advance(1) != DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(0, span.nextStartPosition())
        assertEquals(s(1, 0, 4), s(span))
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, span.nextDoc())
    }

    @Test
    fun testNearSpansNextThenAdvancePast() {
        val q = makeQuery()
        val span = q.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1f)
            .getSpans(searcher.indexReader.leaves()[0], SpanWeight.Postings.POSITIONS)!!
        assertTrue(span.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(0, span.nextStartPosition())
        assertEquals(s(0, 0, 3), s(span))
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, span.advance(2))
    }

    @Test
    fun testNearSpansAdvancePast() {
        val q = makeQuery()
        val span = q.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1f)
            .getSpans(searcher.indexReader.leaves()[0], SpanWeight.Postings.POSITIONS)!!
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, span.advance(2))
    }

    @Test
    fun testNearSpansAdvanceTo0() {
        val q = makeQuery()
        val span = q.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1f)
            .getSpans(searcher.indexReader.leaves()[0], SpanWeight.Postings.POSITIONS)!!
        assertEquals(0, span.advance(0))
        assertEquals(0, span.nextStartPosition())
        assertEquals(s(0, 0, 3), s(span))
    }

    @Test
    fun testNearSpansAdvanceTo1() {
        val q = makeQuery()
        val span = q.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1f)
            .getSpans(searcher.indexReader.leaves()[0], SpanWeight.Postings.POSITIONS)!!
        assertEquals(1, span.advance(1))
        assertEquals(0, span.nextStartPosition())
        assertEquals(s(1, 0, 4), s(span))
    }

    /** not a direct test of NearSpans, but a demonstration of how/when this causes problems */
    @Test
    fun testSpanNearScorerSkipTo1() {
        val q = makeQuery()
        val w: Weight = searcher.createWeight(searcher.rewrite(q), ScoreMode.COMPLETE, 1f)
        val leave = searcher.topReaderContext.leaves()[0]
        val s: Scorer = w.scorer(leave)!!
        assertEquals(1, s.iterator().advance(1))
    }

    @Test
    fun testOverlappedOrderedSpan() {
        val q = makeOverlappedQuery("w5", "w3", false, "w4", true)
        CheckHits.checkHits(random(), q, FIELD, searcher, intArrayOf())
    }

    @Test
    fun testOverlappedNonOrderedSpan() {
        val q = makeOverlappedQuery("w3", "w5", true, "w4", false)
        CheckHits.checkHits(random(), q, FIELD, searcher, intArrayOf(0))
    }

    @Test
    fun testNonOverlappedOrderedSpan() {
        val q = makeOverlappedQuery("w3", "w4", true, "w5", true)
        CheckHits.checkHits(random(), q, FIELD, searcher, intArrayOf(0))
    }

    @Test
    fun testOrderedSpanIteration() {
        val q = SpanNearQuery(
            arrayOf(
                SpanOrQuery(
                    SpanTermQuery(Term(FIELD, "w1")),
                    SpanTermQuery(Term(FIELD, "w2")),
                ),
                SpanTermQuery(Term(FIELD, "w4")),
            ),
            10,
            true,
        )
        val spans = q.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1f)
            .getSpans(searcher.indexReader.leaves()[0], SpanWeight.Postings.POSITIONS)!!
        SpanTestUtil.assertNext(spans, 0, 0, 4)
        SpanTestUtil.assertNext(spans, 0, 1, 4)
        SpanTestUtil.assertFinished(spans)
    }

    @Test
    fun testOrderedSpanIterationSameTerms1() {
        val q = SpanNearQuery(
            arrayOf(
                SpanTermQuery(Term(FIELD, "t1")),
                SpanTermQuery(Term(FIELD, "t2")),
            ),
            1,
            true,
        )
        val spans = q.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1f)
            .getSpans(searcher.indexReader.leaves()[0], SpanWeight.Postings.POSITIONS)!!
        SpanTestUtil.assertNext(spans, 4, 0, 2)
        SpanTestUtil.assertFinished(spans)
    }

    @Test
    fun testOrderedSpanIterationSameTerms2() {
        val q = SpanNearQuery(
            arrayOf(
                SpanTermQuery(Term(FIELD, "t2")),
                SpanTermQuery(Term(FIELD, "t1")),
            ),
            1,
            true,
        )
        val spans = q.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1f)
            .getSpans(searcher.indexReader.leaves()[0], SpanWeight.Postings.POSITIONS)!!
        SpanTestUtil.assertNext(spans, 4, 1, 4)
        SpanTestUtil.assertNext(spans, 4, 2, 4)
        SpanTestUtil.assertFinished(spans)
    }

    /** not a direct test of NearSpans, but a demonstration of how/when this causes problems */
    @Test
    fun testSpanNearScorerExplain() {
        val q = makeQuery()
        val e: Explanation = searcher.explain(q, 1)
        assertTrue(0.0f <= e.value.toDouble(), "Scorer explanation value for doc#1 isn't positive: $e")
    }

    @Test
    fun testGaps() {
        var q = SpanNearQuery.newOrderedNearQuery(FIELD)
            .addClause(SpanTermQuery(Term(FIELD, "w1")))
            .addGap(1)
            .addClause(SpanTermQuery(Term(FIELD, "w2")))
            .build()
        var spans = q.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1f)
            .getSpans(searcher.indexReader.leaves()[0], SpanWeight.Postings.POSITIONS)!!
        SpanTestUtil.assertNext(spans, 1, 0, 3)
        SpanTestUtil.assertNext(spans, 2, 0, 3)
        SpanTestUtil.assertFinished(spans)

        q = SpanNearQuery.newOrderedNearQuery(FIELD)
            .addClause(SpanTermQuery(Term(FIELD, "w1")))
            .addGap(1)
            .addClause(SpanTermQuery(Term(FIELD, "w2")))
            .addGap(1)
            .addClause(SpanTermQuery(Term(FIELD, "w3")))
            .setSlop(1)
            .build()
        spans = q.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1f)
            .getSpans(searcher.indexReader.leaves()[0], SpanWeight.Postings.POSITIONS)!!
        SpanTestUtil.assertNext(spans, 2, 0, 5)
        SpanTestUtil.assertNext(spans, 3, 0, 6)
        SpanTestUtil.assertFinished(spans)
    }

    @Test
    fun testMultipleGaps() {
        val q: SpanQuery = SpanNearQuery.newOrderedNearQuery(FIELD)
            .addClause(SpanTermQuery(Term(FIELD, "g")))
            .addGap(2)
            .addClause(SpanTermQuery(Term(FIELD, "g")))
            .build()
        val spans = q.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1f)
            .getSpans(searcher.indexReader.leaves()[0], SpanWeight.Postings.POSITIONS)!!
        SpanTestUtil.assertNext(spans, 5, 0, 4)
        SpanTestUtil.assertNext(spans, 5, 9, 13)
        SpanTestUtil.assertFinished(spans)
    }

    @Test
    fun testNestedGaps() {
        val q: SpanQuery = SpanNearQuery.newOrderedNearQuery(FIELD)
            .addClause(
                SpanOrQuery(
                    SpanTermQuery(Term(FIELD, "open")),
                    SpanNearQuery.newOrderedNearQuery(FIELD)
                        .addClause(SpanTermQuery(Term(FIELD, "go")))
                        .addGap(1)
                        .build(),
                )
            )
            .addClause(SpanTermQuery(Term(FIELD, "webpage")))
            .build()
        val topDocs: TopDocs = searcher.search(q, 1)
        assertEquals(6, topDocs.scoreDocs[0].doc)
    }

    /*
      protected String[] docFields = {
      "w1 w2 w3 w4 w5",
      "w1 w3 w2 w3 zz",
      "w1 xx w2 yy w3",
      "w1 w3 xx w2 yy w3 zz",
      "t1 t2 t2 t1",
      "g x x g g x x x g g x x g",
      "go to webpage"
    };
     */
}
