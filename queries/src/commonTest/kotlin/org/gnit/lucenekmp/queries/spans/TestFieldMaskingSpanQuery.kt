package org.gnit.lucenekmp.queries.spans

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.QueryVisitor
import org.gnit.lucenekmp.search.ScoreMode
import org.gnit.lucenekmp.search.similarities.TFIDFSimilarity
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.CheckHits
import org.gnit.lucenekmp.tests.search.QueryUtils
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

class TestFieldMaskingSpanQuery : LuceneTestCase() {
    companion object {
        fun doc(fields: Array<Field>): Document {
            val doc = Document()
            for (field in fields) {
                doc.add(field)
            }
            return doc
        }

        fun field(name: String, value: String): Field {
            return newTextField(name, value, Field.Store.NO)
        }
    }

    protected lateinit var searcher: IndexSearcher
    protected lateinit var directory: Directory
    protected lateinit var reader: IndexReader

    @BeforeTest
    fun beforeTest() {
        directory = newDirectory()
        val writer = RandomIndexWriter(
            random(),
            directory,
            newIndexWriterConfig(MockAnalyzer(random())).setMergePolicy(newLogMergePolicy()),
        )

        writer.addDocument(
            doc(
                arrayOf(
                    field("id", "0"),
                    field("gender", "male"),
                    field("first", "james"),
                    field("last", "jones"),
                ),
            ),
        )

        writer.addDocument(
            doc(
                arrayOf(
                    field("id", "1"),
                    field("gender", "male"),
                    field("first", "james"),
                    field("last", "smith"),
                    field("gender", "female"),
                    field("first", "sally"),
                    field("last", "jones"),
                ),
            ),
        )

        writer.addDocument(
            doc(
                arrayOf(
                    field("id", "2"),
                    field("gender", "female"),
                    field("first", "greta"),
                    field("last", "jones"),
                    field("gender", "female"),
                    field("first", "sally"),
                    field("last", "smith"),
                    field("gender", "male"),
                    field("first", "james"),
                    field("last", "jones"),
                ),
            ),
        )

        writer.addDocument(
            doc(
                arrayOf(
                    field("id", "3"),
                    field("gender", "female"),
                    field("first", "lisa"),
                    field("last", "jones"),
                    field("gender", "male"),
                    field("first", "bob"),
                    field("last", "costas"),
                ),
            ),
        )

        writer.addDocument(
            doc(
                arrayOf(
                    field("id", "4"),
                    field("gender", "female"),
                    field("first", "sally"),
                    field("last", "smith"),
                    field("gender", "female"),
                    field("first", "linda"),
                    field("last", "dixit"),
                    field("gender", "male"),
                    field("first", "bubba"),
                    field("last", "jones"),
                ),
            ),
        )
        writer.forceMerge(1)
        reader = writer.reader
        writer.close()
        searcher = IndexSearcher(getOnlyLeafReader(reader))
    }

    @AfterTest
    fun afterTest() {
        reader.close()
        directory.close()
    }

    protected fun check(q: SpanQuery, docs: IntArray) {
        CheckHits.checkHitCollector(random(), q, "", searcher, docs)
    }

    @Test
    fun testRewrite0() {
        val q = FieldMaskingSpanQuery(SpanTermQuery(Term("last", "sally")), "first")
        val qr = searcher.rewrite(q) as SpanQuery

        QueryUtils.checkEqual(q, qr)

        val terms = HashSet<Term?>()
        qr.visit(QueryVisitor.termCollector(terms))
        assertEquals(1, terms.size)
    }

    @Test
    fun testRewrite1() {
        val q =
            FieldMaskingSpanQuery(
                object : SpanQuery() {
                    private val q = SpanTermQuery(Term("last", "sally"))

                    override fun getField(): String {
                        return q.getField()
                    }

                    override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): SpanWeight {
                        return q.createWeight(searcher, scoreMode, boost)
                    }

                    override fun visit(visitor: QueryVisitor) {
                        q.visit(visitor)
                    }

                    override fun toString(field: String?): String {
                        return q.toString(field)
                    }

                    override fun rewrite(indexSearcher: IndexSearcher): Query {
                        return SpanOrQuery(
                            SpanTermQuery(Term("first", "sally")),
                            SpanTermQuery(Term("first", "james")),
                        )
                    }

                    override fun equals(other: Any?): Boolean {
                        return other === this
                    }

                    override fun hashCode(): Int {
                        return q.hashCode()
                    }
                },
                "first",
            )

        val qr = searcher.rewrite(q) as SpanQuery

        QueryUtils.checkUnequal(q, qr)

        val terms = HashSet<Term?>()
        qr.visit(QueryVisitor.termCollector(terms))
        assertEquals(2, terms.size)
    }

    @Test
    fun testRewrite2() {
        val q1 = SpanTermQuery(Term("last", "smith"))
        val q2 = SpanTermQuery(Term("last", "jones"))
        val q = SpanNearQuery(arrayOf(q1, FieldMaskingSpanQuery(q2, "last")), 1, true)
        val qr = searcher.rewrite(q)

        QueryUtils.checkEqual(q, qr)

        val set = HashSet<Term?>()
        qr.visit(QueryVisitor.termCollector(set))
        assertEquals(2, set.size)
    }

    @Test
    fun testEquality1() {
        val q1 = FieldMaskingSpanQuery(SpanTermQuery(Term("last", "sally")), "first")
        val q2 = FieldMaskingSpanQuery(SpanTermQuery(Term("last", "sally")), "first")
        val q3 = FieldMaskingSpanQuery(SpanTermQuery(Term("last", "sally")), "XXXXX")
        val q4 = FieldMaskingSpanQuery(SpanTermQuery(Term("last", "XXXXX")), "first")
        val q5 = FieldMaskingSpanQuery(SpanTermQuery(Term("xXXX", "sally")), "first")
        QueryUtils.checkEqual(q1, q2)
        QueryUtils.checkUnequal(q1, q3)
        QueryUtils.checkUnequal(q1, q4)
        QueryUtils.checkUnequal(q1, q5)
    }

    @Test
    fun testNoop0() {
        val q1 = SpanTermQuery(Term("last", "sally"))
        val q = FieldMaskingSpanQuery(q1, "first")
        check(q, intArrayOf())
    }

    @Test
    fun testNoop1() {
        val q1 = SpanTermQuery(Term("last", "smith"))
        val q2 = SpanTermQuery(Term("last", "jones"))
        var q = SpanNearQuery(arrayOf(q1, FieldMaskingSpanQuery(q2, "last")), 0, true)
        check(q, intArrayOf(1, 2))
        q = SpanNearQuery(
            arrayOf(FieldMaskingSpanQuery(q1, "last"), FieldMaskingSpanQuery(q2, "last")),
            0,
            true,
        )
        check(q, intArrayOf(1, 2))
    }

    @Test
    fun testSimple1() {
        val q1 = SpanTermQuery(Term("first", "james"))
        val q2 = SpanTermQuery(Term("last", "jones"))
        var q = SpanNearQuery(arrayOf(q1, FieldMaskingSpanQuery(q2, "first")), -1, false)
        check(q, intArrayOf(0, 2))
        q = SpanNearQuery(arrayOf(FieldMaskingSpanQuery(q2, "first"), q1), -1, false)
        check(q, intArrayOf(0, 2))
        q = SpanNearQuery(arrayOf(q2, FieldMaskingSpanQuery(q1, "last")), -1, false)
        check(q, intArrayOf(0, 2))
        q = SpanNearQuery(arrayOf(FieldMaskingSpanQuery(q1, "last"), q2), -1, false)
        check(q, intArrayOf(0, 2))
    }

    @Test
    fun testSimple2() {
        if (searcher.similarity !is TFIDFSimilarity) {
            return
        }
        val q1 = SpanTermQuery(Term("gender", "female"))
        val q2 = SpanTermQuery(Term("last", "smith"))
        var q = SpanNearQuery(arrayOf(q1, FieldMaskingSpanQuery(q2, "gender")), -1, false)
        check(q, intArrayOf(2, 4))
        q = SpanNearQuery(
            arrayOf(FieldMaskingSpanQuery(q1, "id"), FieldMaskingSpanQuery(q2, "id")),
            -1,
            false,
        )
        check(q, intArrayOf(2, 4))
    }

    @Test
    fun testSpans0() {
        val q1 = SpanTermQuery(Term("gender", "female"))
        val q2 = SpanTermQuery(Term("first", "james"))
        val q = SpanOrQuery(q1, FieldMaskingSpanQuery(q2, "gender"))
        check(q, intArrayOf(0, 1, 2, 3, 4))

        val span = q.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1f)
            .getSpans(searcher.indexReader.leaves()[0], SpanWeight.Postings.POSITIONS)!!
        SpanTestUtil.assertNext(span, 0, 0, 1)
        SpanTestUtil.assertNext(span, 1, 0, 1)
        SpanTestUtil.assertNext(span, 1, 1, 2)
        SpanTestUtil.assertNext(span, 2, 0, 1)
        SpanTestUtil.assertNext(span, 2, 1, 2)
        SpanTestUtil.assertNext(span, 2, 2, 3)
        SpanTestUtil.assertNext(span, 3, 0, 1)
        SpanTestUtil.assertNext(span, 4, 0, 1)
        SpanTestUtil.assertNext(span, 4, 1, 2)
        SpanTestUtil.assertFinished(span)
    }

    @Test
    fun testSpans1() {
        val q1 = SpanTermQuery(Term("first", "sally"))
        val q2 = SpanTermQuery(Term("first", "james"))
        val qA = SpanOrQuery(q1, q2)
        val qB = FieldMaskingSpanQuery(qA, "id")

        check(qA, intArrayOf(0, 1, 2, 4))
        check(qB, intArrayOf(0, 1, 2, 4))

        val spanA = qA.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1f)
            .getSpans(searcher.indexReader.leaves()[0], SpanWeight.Postings.POSITIONS)!!
        val spanB = qB.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1f)
            .getSpans(searcher.indexReader.leaves()[0], SpanWeight.Postings.POSITIONS)!!

        while (spanA.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
            assertNotSame(DocIdSetIterator.NO_MORE_DOCS, spanB.nextDoc(), "spanB not still going")
            while (spanA.nextStartPosition() != Spans.NO_MORE_POSITIONS) {
                assertEquals(spanA.startPosition(), spanB.nextStartPosition(), "spanB start position")
                assertEquals(spanA.endPosition(), spanB.endPosition(), "spanB end position")
            }
            assertEquals(Spans.NO_MORE_POSITIONS, spanB.nextStartPosition(), "spanB start position")
        }
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, spanB.nextDoc(), "spanB end doc")
    }

    @Test
    fun testSpans2() {
        if (searcher.similarity !is TFIDFSimilarity) {
            return
        }
        val qA1 = SpanTermQuery(Term("gender", "female"))
        val qA2 = SpanTermQuery(Term("first", "james"))
        val qA = SpanOrQuery(qA1, FieldMaskingSpanQuery(qA2, "gender"))
        val qB = SpanTermQuery(Term("last", "jones"))
        val q = SpanNearQuery(
            arrayOf(
                FieldMaskingSpanQuery(qA, "id"),
                FieldMaskingSpanQuery(qB, "id"),
            ),
            -1,
            false,
        )
        check(q, intArrayOf(0, 1, 2, 3))

        val span = q.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1f)
            .getSpans(searcher.indexReader.leaves()[0], SpanWeight.Postings.POSITIONS)!!
        SpanTestUtil.assertNext(span, 0, 0, 1)
        SpanTestUtil.assertNext(span, 1, 1, 2)
        SpanTestUtil.assertNext(span, 2, 0, 1)
        SpanTestUtil.assertNext(span, 2, 2, 3)
        SpanTestUtil.assertNext(span, 3, 0, 1)
        SpanTestUtil.assertFinished(span)
    }

    fun s(doc: Int, start: Int, end: Int): String {
        return "s($doc,$start,$end)"
    }
}
