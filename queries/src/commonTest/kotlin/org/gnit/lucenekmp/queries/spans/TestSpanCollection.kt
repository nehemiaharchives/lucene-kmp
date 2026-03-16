package org.gnit.lucenekmp.queries.spans

import okio.IOException
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.ScoreMode
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestSpanCollection : LuceneTestCase() {
    protected lateinit var searcher: IndexSearcher
    protected lateinit var directory: Directory
    protected lateinit var reader: IndexReader

    companion object {
        const val FIELD: String = "field"

        val OFFSETS: FieldType = FieldType(TextField.TYPE_STORED)

        init {
            OFFSETS.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS)
        }
    }

    @AfterTest
    fun tearDown() {
        reader.close()
        directory.close()
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
            val doc = org.gnit.lucenekmp.document.Document()
            doc.add(newField(FIELD, docFields[i], OFFSETS))
            writer.addDocument(doc)
        }
        writer.forceMerge(1)
        reader = writer.reader
        writer.close()
        searcher = newSearcher(getOnlyLeafReader(reader))
    }

    private class TermCollector : SpanCollector {
        val terms: MutableSet<Term> = HashSet()

        @Throws(IOException::class)
        override fun collectLeaf(postings: PostingsEnum, position: Int, term: Term) {
            terms.add(term)
        }

        override fun reset() {
            terms.clear()
        }
    }

    protected val docFields =
        arrayOf(
            "w1 w2 w3 w4 w5",
            "w1 w3 w2 w3 zz",
            "w1 xx w2 yy w4",
            "w1 w2 w1 w4 w2 w3",
        )

    @Throws(IOException::class)
    private fun checkCollectedTerms(spans: Spans, collector: TermCollector, vararg expectedTerms: Term) {
        collector.reset()
        spans.collect(collector)
        for (t in expectedTerms) {
            assertTrue(collector.terms.contains(t), "Missing term $t")
        }
        assertEquals(expectedTerms.size, collector.terms.size, "Unexpected terms found")
    }

    @Test
    fun testNestedNearQuery() {
        // near(w1, near(w2, or(w3, w4)))

        val q1 = SpanTermQuery(Term(FIELD, "w1"))
        val q2 = SpanTermQuery(Term(FIELD, "w2"))
        val q3 = SpanTermQuery(Term(FIELD, "w3"))
        val q4 = SpanTermQuery(Term(FIELD, "w4"))

        val q5 = SpanOrQuery(q4, q3)
        val q6 = SpanNearQuery(arrayOf(q2, q5), 1, true)
        val q7 = SpanNearQuery(arrayOf(q1, q6), 1, true)

        val collector = TermCollector()
        val spans =
            q7.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1f)
                .getSpans(searcher.indexReader.leaves()[0], SpanWeight.Postings.POSITIONS)!!
        assertEquals(0, spans.advance(0))
        spans.nextStartPosition()
        checkCollectedTerms(spans, collector, Term(FIELD, "w1"), Term(FIELD, "w2"), Term(FIELD, "w3"))

        assertEquals(3, spans.advance(3))
        spans.nextStartPosition()
        checkCollectedTerms(spans, collector, Term(FIELD, "w1"), Term(FIELD, "w2"), Term(FIELD, "w4"))
        spans.nextStartPosition()
        checkCollectedTerms(spans, collector, Term(FIELD, "w1"), Term(FIELD, "w2"), Term(FIELD, "w3"))
    }

    @Test
    fun testOrQuery() {
        val q2 = SpanTermQuery(Term(FIELD, "w2"))
        val q3 = SpanTermQuery(Term(FIELD, "w3"))
        val orQuery = SpanOrQuery(q2, q3)

        val collector = TermCollector()
        val spans =
            orQuery.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1f)
                .getSpans(searcher.indexReader.leaves()[0], SpanWeight.Postings.POSITIONS)!!

        assertEquals(1, spans.advance(1))
        spans.nextStartPosition()
        checkCollectedTerms(spans, collector, Term(FIELD, "w3"))
        spans.nextStartPosition()
        checkCollectedTerms(spans, collector, Term(FIELD, "w2"))
        spans.nextStartPosition()
        checkCollectedTerms(spans, collector, Term(FIELD, "w3"))

        assertEquals(3, spans.advance(3))
        spans.nextStartPosition()
        checkCollectedTerms(spans, collector, Term(FIELD, "w2"))
        spans.nextStartPosition()
        checkCollectedTerms(spans, collector, Term(FIELD, "w2"))
        spans.nextStartPosition()
        checkCollectedTerms(spans, collector, Term(FIELD, "w3"))
    }

    @Test
    fun testSpanNotQuery() {
        val q1 = SpanTermQuery(Term(FIELD, "w1"))
        val q2 = SpanTermQuery(Term(FIELD, "w2"))
        val q3 = SpanTermQuery(Term(FIELD, "w3"))

        val nq = SpanNearQuery(arrayOf(q1, q2), 2, true)
        val notq = SpanNotQuery(nq, q3)

        val collector = TermCollector()
        val spans =
            notq.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1f)
                .getSpans(searcher.indexReader.leaves()[0], SpanWeight.Postings.POSITIONS)!!

        assertEquals(2, spans.advance(2))
        spans.nextStartPosition()
        checkCollectedTerms(spans, collector, Term(FIELD, "w1"), Term(FIELD, "w2"))
    }
}
