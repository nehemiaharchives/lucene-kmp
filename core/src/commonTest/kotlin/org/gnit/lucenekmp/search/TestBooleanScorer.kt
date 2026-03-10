package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.BooleanClause.Occur
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.Weight.DefaultBulkScorer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.QueryUtils
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.Bits
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestBooleanScorer : LuceneTestCase() {
    companion object {
        private const val FIELD = "category"
    }

    @Test
    @Throws(Exception::class)
    fun testMethod() {
        val directory = newDirectory()

        val values = arrayOf("1", "2", "3", "4")

        val writer = RandomIndexWriter(random(), directory)
        for (i in values.indices) {
            val doc = Document()
            doc.add(newStringField(FIELD, values[i], Field.Store.YES))
            writer.addDocument(doc)
        }
        val ir = writer.getReader(true, false)
        writer.close()

        val booleanQuery1 = BooleanQuery.Builder()
        booleanQuery1.add(TermQuery(Term(FIELD, "1")), BooleanClause.Occur.SHOULD)
        booleanQuery1.add(TermQuery(Term(FIELD, "2")), BooleanClause.Occur.SHOULD)

        val query = BooleanQuery.Builder()
        query.add(booleanQuery1.build(), BooleanClause.Occur.MUST)
        query.add(TermQuery(Term(FIELD, "9")), BooleanClause.Occur.MUST_NOT)

        val indexSearcher = newSearcher(ir)
        val hits = indexSearcher.search(query.build(), 1000).scoreDocs
        assertEquals(2, hits.size, "Number of matched documents")
        ir.close()
        directory.close()
    }

    /** Throws UOE if Weight.scorer is called */
    private class CrazyMustUseBulkScorerQuery : Query() {
        override fun toString(field: String?): String {
            return "MustUseBulkScorerQuery"
        }

        override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
            return object : Weight(this@CrazyMustUseBulkScorerQuery) {
                override fun explain(context: LeafReaderContext, doc: Int): Explanation {
                    throw UnsupportedOperationException()
                }

                @Throws(IOException::class)
                override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier {
                    return object : ScorerSupplier() {
                        @Throws(IOException::class)
                        override fun get(leadCost: Long): Scorer {
                            throw UnsupportedOperationException()
                        }

                        override fun cost(): Long {
                            throw UnsupportedOperationException()
                        }

                        @Throws(IOException::class)
                        override fun bulkScorer(): BulkScorer {
                            return object : BulkScorer() {
                                @Throws(IOException::class)
                                override fun score(collector: LeafCollector, acceptDocs: Bits?, min: Int, max: Int): Int {
                                    assert(min == 0)
                                    collector.scorer = Score()
                                    collector.collect(0)
                                    return DocIdSetIterator.NO_MORE_DOCS
                                }

                                override fun cost(): Long {
                                    return 1
                                }
                            }
                        }
                    }
                }

                override fun isCacheable(ctx: LeafReaderContext): Boolean {
                    return false
                }
            }
        }

        override fun visit(visitor: QueryVisitor) {}

        override fun equals(other: Any?): Boolean {
            return this === other
        }

        override fun hashCode(): Int {
            return this::class.hashCode()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testEmbeddedBooleanScorer() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        val doc = Document()
        doc.add(
            newTextField(
                "field",
                "doctors are people who prescribe medicines of which they know little, to cure diseases of which they know less, in human beings of whom they know nothing",
                Field.Store.NO
            )
        )
        w.addDocument(doc)
        val r = w.getReader(true, false)
        w.close()

        val s = IndexSearcher(r)
        val q1 = BooleanQuery.Builder()
        q1.add(TermQuery(Term("field", "little")), BooleanClause.Occur.SHOULD)
        q1.add(TermQuery(Term("field", "diseases")), BooleanClause.Occur.SHOULD)

        val q2 = BooleanQuery.Builder()
        q2.add(q1.build(), BooleanClause.Occur.SHOULD)
        q2.add(CrazyMustUseBulkScorerQuery(), BooleanClause.Occur.SHOULD)

        assertEquals(1, s.count(q2.build()))
        r.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testOptimizeTopLevelClauseOrNull() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        val doc = Document()
        doc.add(StringField("foo", "bar", Field.Store.NO))
        w.addDocument(doc)
        val reader = w.getReader(true, false)
        val searcher = IndexSearcher(reader)
        searcher.queryCache = null
        val ctx = reader.leaves()[0]
        var query: Query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "bar")), Occur.SHOULD)
                .add(TermQuery(Term("missing_field", "baz")), Occur.SHOULD)
                .build()

        var weight = searcher.createWeight(searcher.rewrite(query), ScoreMode.COMPLETE_NO_SCORES, 1f)
        var ss = weight.scorerSupplier(ctx)
        var scorer = (ss as BooleanScorerSupplier).booleanScorer()
        assertTrue(scorer is DefaultBulkScorer)

        query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "bar")), Occur.SHOULD)
                .add(TermQuery(Term("foo", "baz")), Occur.SHOULD)
                .build()
        weight = searcher.createWeight(searcher.rewrite(query), ScoreMode.COMPLETE, 1f)
        ss = weight.scorerSupplier(ctx)
        scorer = (ss as BooleanScorerSupplier).booleanScorer()
        assertTrue(scorer is DefaultBulkScorer)

        w.close()
        reader.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testOptimizeProhibitedClauses() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        var doc = Document()
        doc.add(StringField("foo", "bar", Field.Store.NO))
        doc.add(StringField("foo", "baz", Field.Store.NO))
        w.addDocument(doc)
        doc = Document()
        doc.add(StringField("foo", "baz", Field.Store.NO))
        w.addDocument(doc)
        w.forceMerge(1)
        val reader = w.getReader(true, false)
        val searcher = IndexSearcher(reader)
        searcher.queryCache = null
        val ctx = reader.leaves()[0]

        var query: Query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "baz")), Occur.SHOULD)
                .add(TermQuery(Term("foo", "bar")), Occur.MUST_NOT)
                .build()
        var weight = searcher.createWeight(searcher.rewrite(query), ScoreMode.COMPLETE, 1f)
        var ss = weight.scorerSupplier(ctx)
        var scorer = (ss as BooleanScorerSupplier).booleanScorer()
        assertTrue(scorer is ReqExclBulkScorer)

        query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "baz")), Occur.SHOULD)
                .add(MatchAllDocsQuery(), Occur.SHOULD)
                .add(TermQuery(Term("foo", "bar")), Occur.MUST_NOT)
                .build()
        weight = searcher.createWeight(searcher.rewrite(query), ScoreMode.COMPLETE, 1f)
        ss = weight.scorerSupplier(ctx)
        scorer = (ss as BooleanScorerSupplier).booleanScorer()
        assertTrue(scorer is ReqExclBulkScorer)

        query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "baz")), Occur.MUST)
                .add(TermQuery(Term("foo", "bar")), Occur.MUST_NOT)
                .build()
        weight = searcher.createWeight(searcher.rewrite(query), ScoreMode.COMPLETE, 1f)
        ss = weight.scorerSupplier(ctx)
        scorer = (ss as BooleanScorerSupplier).booleanScorer()
        assertTrue(scorer is ReqExclBulkScorer)

        query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("foo", "baz")), Occur.FILTER)
                .add(TermQuery(Term("foo", "bar")), Occur.MUST_NOT)
                .build()
        weight = searcher.createWeight(searcher.rewrite(query), ScoreMode.COMPLETE, 1f)
        ss = weight.scorerSupplier(ctx)
        scorer = (ss as BooleanScorerSupplier).booleanScorer()
        assertTrue(scorer is ReqExclBulkScorer)

        w.close()
        reader.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testSparseClauseOptimization() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        val emptyDoc = Document()
        val numDocs = atLeast(10)
        var numEmptyDocs = atLeast(200)
        for (d in 0 until numDocs) {
            for (i in numEmptyDocs downTo 0) {
                w.addDocument(emptyDoc)
            }
            val doc = Document()
            for (value in arrayOf("foo", "bar", "baz")) {
                if (random().nextBoolean()) {
                    doc.add(StringField("field", value, Field.Store.NO))
                }
            }
            w.addDocument(doc)
        }
        numEmptyDocs = atLeast(200)
        for (i in numEmptyDocs downTo 0) {
            w.addDocument(emptyDoc)
        }
        if (random().nextBoolean()) {
            w.forceMerge(1)
        }
        val reader = w.getReader(true, false)
        val searcher = newSearcher(reader)

        val query =
            BooleanQuery.Builder()
                .add(BoostQuery(TermQuery(Term("field", "foo")), 3f), Occur.SHOULD)
                .add(BoostQuery(TermQuery(Term("field", "bar")), 3f), Occur.SHOULD)
                .add(BoostQuery(TermQuery(Term("field", "baz")), 3f), Occur.SHOULD)
                .build()

        QueryUtils.check(random(), query, searcher)

        reader.close()
        w.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testFilterConstantScore() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        val doc = Document()
        doc.add(StringField("foo", "bar", Field.Store.NO))
        doc.add(StringField("foo", "bat", Field.Store.NO))
        doc.add(StringField("foo", "baz", Field.Store.NO))
        w.addDocument(doc)
        val reader = w.getReader(true, false)
        val searcher = IndexSearcher(reader)
        searcher.queryCache = null

        run {
            val query =
                BooleanQuery.Builder()
                    .add(TermQuery(Term("foo", "bar")), Occur.FILTER)
                    .build()
            val rewrite = searcher.rewrite(query)
            assertTrue(rewrite is BoostQuery)
            assertTrue((rewrite as BoostQuery).query is ConstantScoreQuery)
        }

        var queries =
            arrayOf<Query>(
                BooleanQuery.Builder()
                    .add(TermQuery(Term("foo", "bar")), Occur.FILTER)
                    .add(TermQuery(Term("foo", "baz")), Occur.FILTER)
                    .build(),
                BooleanQuery.Builder()
                    .add(TermQuery(Term("foo", "baz")), Occur.FILTER)
                    .add(TermQuery(Term("foo", "arf")), Occur.SHOULD)
                    .build(),
                BooleanQuery.Builder()
                    .add(TermQuery(Term("foo", "baz")), Occur.FILTER)
                    .add(TermQuery(Term("foo", "baz")), Occur.FILTER)
                    .add(TermQuery(Term("foo", "arf")), Occur.SHOULD)
                    .add(TermQuery(Term("foo", "arw")), Occur.SHOULD)
                    .build()
            )
        for (query in queries) {
            val rewrite = searcher.rewrite(query)
            for (scoreMode in ScoreMode.entries) {
                val weight = searcher.createWeight(rewrite, scoreMode, 1f)
                val scorer = weight.scorer(reader.leaves()[0])!!
                if (scoreMode == ScoreMode.TOP_SCORES) {
                    assertTrue(scorer is ConstantScoreScorer)
                } else {
                    assertFalse(scorer is ConstantScoreScorer)
                }
            }
        }

        queries =
            arrayOf(
                BooleanQuery.Builder()
                    .add(TermQuery(Term("foo", "bar")), Occur.FILTER)
                    .add(TermQuery(Term("foo", "baz")), Occur.SHOULD)
                    .build(),
                BooleanQuery.Builder()
                    .add(TermQuery(Term("foo", "bar")), Occur.FILTER)
                    .add(TermQuery(Term("foo", "baz")), Occur.MUST)
                    .add(TermQuery(Term("foo", "arf")), Occur.SHOULD)
                    .build(),
                BooleanQuery.Builder()
                    .add(TermQuery(Term("foo", "bar")), Occur.FILTER)
                    .add(TermQuery(Term("foo", "baz")), Occur.SHOULD)
                    .add(TermQuery(Term("foo", "arf")), Occur.MUST)
                    .build()
            )
        for (query in queries) {
            val rewrite = searcher.rewrite(query)
            for (scoreMode in ScoreMode.entries) {
                val weight = searcher.createWeight(rewrite, scoreMode, 1f)
                val scorer = weight.scorer(reader.leaves()[0])
                assertFalse(scorer is ConstantScoreScorer)
            }
        }

        reader.close()
        w.close()
        dir.close()
    }

}
