package org.gnit.lucenekmp.queries.spans

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.ScoreMode
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.CheckHits
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TestSpanContainQuery : LuceneTestCase() {
    lateinit var searcher: IndexSearcher
    lateinit var reader: IndexReader
    lateinit var directory: Directory

    companion object {
        const val field: String = "field"
    }

    @BeforeTest
    fun setUpTest() {
        directory = newDirectory()
        val writer = RandomIndexWriter(
            random(),
            directory,
            newIndexWriterConfig(MockAnalyzer(random())).setMergePolicy(newLogMergePolicy()),
        )
        for (docField in docFields) {
            val doc = Document()
            doc.add(newTextField(field, docField, Field.Store.YES))
            writer.addDocument(doc)
        }
        writer.forceMerge(1)
        reader = writer.reader
        writer.close()
        searcher = newSearcher(getOnlyLeafReader(reader))
    }

    @AfterTest
    fun tearDownTest() {
        reader.close()
        directory.close()
    }

    val docFields = arrayOf(
        "w1 w2 w3 w4 w5", "w1 w3 w2 w3", "w1 xx w2 yy w3", "w1 w3 xx w2 yy w3",
    )

    fun checkHits(query: Query, results: IntArray) {
        CheckHits.checkHits(random(), query, field, searcher, results)
    }

    fun makeSpans(sq: SpanQuery): Spans? {
        return sq.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1f)
            .getSpans(searcher.indexReader.leaves()[0], SpanWeight.Postings.POSITIONS)
    }

    fun tstEqualSpans(mes: String, expectedQ: SpanQuery, actualQ: SpanQuery) {
        val expected = makeSpans(expectedQ)!!
        val actual = makeSpans(actualQ)!!
        tstEqualSpans(mes, expected, actual)
    }

    fun tstEqualSpans(mes: String, expected: Spans, actual: Spans) {
        while (expected.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
            assertEquals(expected.docID(), actual.nextDoc())
            assertEquals(expected.docID(), actual.docID())
            while (expected.nextStartPosition() != Spans.NO_MORE_POSITIONS) {
                assertEquals(expected.startPosition(), actual.nextStartPosition(), mes)
                assertEquals(expected.startPosition(), actual.startPosition(), "start")
                assertEquals(expected.endPosition(), actual.endPosition(), "end")
            }
        }
    }

    @Test
    fun testSpanContainTerm() {
        val stq = SpanTestUtil.spanTermQuery(field, "w3")
        val containingQ = SpanTestUtil.spanContainingQuery(stq, stq)
        val containedQ = SpanTestUtil.spanWithinQuery(stq, stq)
        tstEqualSpans("containing", stq, containingQ)
        tstEqualSpans("containing", stq, containedQ)
    }

    @Test
    fun testSpanContainPhraseBothWords() {
        val w2 = "w2"
        val w3 = "w3"
        val phraseQ = SpanTestUtil.spanNearOrderedQuery(field, 0, w2, w3)
        val w23 = SpanTestUtil.spanOrQuery(field, w2, w3)
        val containingPhraseOr = SpanTestUtil.spanContainingQuery(phraseQ, w23)
        val containedPhraseOr = SpanTestUtil.spanWithinQuery(phraseQ, w23)
        tstEqualSpans("containing phrase or", phraseQ, containingPhraseOr)
        val spans = makeSpans(containedPhraseOr)!!
        SpanTestUtil.assertNext(spans, 0, 1, 2)
        SpanTestUtil.assertNext(spans, 0, 2, 3)
        SpanTestUtil.assertNext(spans, 1, 2, 3)
        SpanTestUtil.assertNext(spans, 1, 3, 4)
        SpanTestUtil.assertFinished(spans)
    }

    @Test
    fun testSpanContainPhraseFirstWord() {
        val w2 = "w2"
        val w3 = "w3"
        val stqw2 = SpanTestUtil.spanTermQuery(field, w2)
        val phraseQ = SpanTestUtil.spanNearOrderedQuery(field, 0, w2, w3)
        val containingPhraseW2 = SpanTestUtil.spanContainingQuery(phraseQ, stqw2)
        val containedPhraseW2 = SpanTestUtil.spanWithinQuery(phraseQ, stqw2)
        tstEqualSpans("containing phrase w2", phraseQ, containingPhraseW2)
        val spans = makeSpans(containedPhraseW2)!!
        SpanTestUtil.assertNext(spans, 0, 1, 2)
        SpanTestUtil.assertNext(spans, 1, 2, 3)
        SpanTestUtil.assertFinished(spans)
    }

    @Test
    fun testSpanContainPhraseSecondWord() {
        val w2 = "w2"
        val w3 = "w3"
        val stqw3 = SpanTestUtil.spanTermQuery(field, w3)
        val phraseQ = SpanTestUtil.spanNearOrderedQuery(field, 0, w2, w3)
        val containingPhraseW3 = SpanTestUtil.spanContainingQuery(phraseQ, stqw3)
        val containedPhraseW3 = SpanTestUtil.spanWithinQuery(phraseQ, stqw3)
        tstEqualSpans("containing phrase w3", phraseQ, containingPhraseW3)
        val spans = makeSpans(containedPhraseW3)!!
        SpanTestUtil.assertNext(spans, 0, 2, 3)
        SpanTestUtil.assertNext(spans, 1, 3, 4)
        SpanTestUtil.assertFinished(spans)
    }
}
