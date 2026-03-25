package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.IOUtils
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TestNeedsScores : LuceneTestCase() {
    private lateinit var dir: Directory
    private lateinit var reader: IndexReader
    private lateinit var searcher: IndexSearcher

    @BeforeTest
    fun setUp() {
        dir = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        for (i in 0..<5) {
            val doc = Document()
            doc.add(TextField("field", "this is document $i", Field.Store.NO))
            iw.addDocument(doc)
        }
        reader = iw.reader
        searcher = newSearcher(reader)
        // Needed so that the cache doesn't consume weights with ScoreMode.COMPLETE_NO_SCORES for the
        // purpose of populating the cache.
        searcher.queryCache = null
        iw.close()
    }

    @AfterTest
    fun tearDown() {
        IOUtils.close(reader, dir)
    }

    /** prohibited clauses in booleanquery don't need scoring */
    @Test
    fun testProhibitedClause() {
        val required: Query = TermQuery(Term("field", "this"))
        val prohibited: Query = TermQuery(Term("field", "3"))
        val bq = BooleanQuery.Builder()
        bq.add(AssertNeedsScores(required, ScoreMode.TOP_SCORES), BooleanClause.Occur.MUST)
        bq.add(
            AssertNeedsScores(prohibited, ScoreMode.COMPLETE_NO_SCORES),
            BooleanClause.Occur.MUST_NOT
        )
        assertEquals(4L, searcher.search(bq.build(), 5).totalHits.value) // we exclude 3
    }

    /** nested inside constant score query */
    @Test
    fun testConstantScoreQuery() {
        val term: Query = TermQuery(Term("field", "this"))

        // Counting queries and top-score queries that compute the hit count should use
        // COMPLETE_NO_SCORES
        var constantScore: Query =
            ConstantScoreQuery(AssertNeedsScores(term, ScoreMode.COMPLETE_NO_SCORES))
        assertEquals(5, searcher.count(constantScore))

        val hits =
            searcher.search(
                constantScore,
                TopScoreDocCollectorManager(5, null, Int.MAX_VALUE)
            )
        assertEquals(5L, hits.totalHits.value)

        // Queries that support dynamic pruning like top-score or top-doc queries that do not compute
        // the hit count should use TOP_DOCS
        constantScore = ConstantScoreQuery(AssertNeedsScores(term, ScoreMode.TOP_DOCS))
        assertEquals(5L, searcher.search(constantScore, 5).totalHits.value)

        assertEquals(
            5L,
            searcher.search(constantScore, 5, Sort(SortField.FIELD_DOC)).totalHits.value
        )

        assertEquals(
            5L,
            searcher
                .search(constantScore, 5, Sort(SortField.FIELD_DOC, SortField.FIELD_SCORE))
                .totalHits
                .value
        )
    }

    /** when not sorting by score */
    @Test
    fun testSortByField() {
        val query: Query = AssertNeedsScores(MatchAllDocsQuery(), ScoreMode.TOP_DOCS)
        assertEquals(5L, searcher.search(query, 5, Sort.INDEXORDER).totalHits.value)
    }

    /** when sorting by score */
    @Test
    fun testSortByScore() {
        val query: Query = AssertNeedsScores(MatchAllDocsQuery(), ScoreMode.TOP_SCORES)
        assertEquals(5L, searcher.search(query, 5, Sort.RELEVANCE).totalHits.value)
    }

    /**
     * Wraps a query, checking that the needsScores param passed to Weight.scorer is the expected
     * value.
     */
    internal class AssertNeedsScores(
        val `in`: Query,
        val value: ScoreMode
    ) : Query() {
        @Throws(Exception::class)
        override fun createWeight(
            searcher: IndexSearcher,
            scoreMode: ScoreMode,
            boost: Float
        ): Weight {
            val w = `in`.createWeight(searcher, scoreMode, boost)
            return object : FilterWeight(w) {
                @Throws(IOException::class)
                override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
                    val scorerSupplier = w.scorerSupplier(context) ?: return null
                    val scorer = scorerSupplier.get(Long.MAX_VALUE)!!
                    return object : ScorerSupplier() {
                        @Throws(IOException::class)
                        override fun get(leadCost: Long): Scorer {
                            assertEquals(value, scoreMode, "query=${`in`}")
                            return scorer
                        }

                        override fun cost(): Long {
                            return scorer.iterator().cost()
                        }
                    }
                }
            }
        }

        @Throws(Exception::class)
        override fun rewrite(indexSearcher: IndexSearcher): Query {
            val in2 = `in`.rewrite(indexSearcher)
            return if (in2 === `in`) {
                super.rewrite(indexSearcher)
            } else {
                AssertNeedsScores(in2, value)
            }
        }

        override fun visit(visitor: QueryVisitor) {
            `in`.visit(visitor)
        }

        override fun hashCode(): Int {
            val prime = 31
            var result = classHash()
            result = prime * result + `in`.hashCode()
            result = prime * result + value.hashCode()
            return result
        }

        override fun equals(other: Any?): Boolean {
            return sameClassAs(other) && equalsTo(other as AssertNeedsScores)
        }

        private fun equalsTo(other: AssertNeedsScores): Boolean {
            return `in` == other.`in` && value == other.value
        }

        override fun toString(field: String?): String {
            return "asserting(${`in`.toString(field)})"
        }
    }
}
