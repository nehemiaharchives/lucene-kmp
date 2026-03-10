package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.BooleanClause.Occur
import org.gnit.lucenekmp.search.similarities.ClassicSimilarity
import org.gnit.lucenekmp.tests.search.BaseExplanationTestCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * TestExplanations subclass that builds up super crazy complex queries on the assumption that if
 * the explanations work out right for them, they should work for anything.
 */
open class TestComplexExplanations : BaseExplanationTestCase() {
    @BeforeTest
    @Throws(Exception::class)
    fun setUp() {
        // TODO: switch to BM25?
        searcher?.similarity = ClassicSimilarity()
    }

    @AfterTest
    @Throws(Exception::class)
    fun tearDown() {
        searcher?.similarity = IndexSearcher.defaultSimilarity
    }

    // :TODO: we really need more crazy complex cases.

    // //////////////////////////////////////////////////////////////////

    // The rest of these aren't that complex, but they are <i>somewhat</i>
    // complex, and they expose weakness in dealing with queries that match
    // with scores of 0 wrapped in other queries

    @Test
    @Throws(Exception::class)
    open fun testT3() {
        val query = TermQuery(Term(FIELD, "w1"))
        bqtest(BoostQuery(query, 0f), intArrayOf(0, 1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testMA3() {
        val q: Query = MatchAllDocsQuery()
        bqtest(BoostQuery(q, 0f), intArrayOf(0, 1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testFQ5() {
        val query = TermQuery(Term(FIELD, "xx"))
        val filtered =
            BooleanQuery.Builder()
                .add(BoostQuery(query, 0f), Occur.MUST)
                .add(matchTheseItems(intArrayOf(1, 3)), Occur.FILTER)
                .build()
        bqtest(filtered, intArrayOf(3))
    }

    @Test
    @Throws(Exception::class)
    open fun testCSQ4() {
        val q: Query = ConstantScoreQuery(matchTheseItems(intArrayOf(3)))
        bqtest(BoostQuery(q, 0f), intArrayOf(3))
    }

    @Test
    @Throws(Exception::class)
    open fun testDMQ10() {
        val query = BooleanQuery.Builder()
        query.add(TermQuery(Term(FIELD, "yy")), Occur.SHOULD)
        val boostedQuery = TermQuery(Term(FIELD, "w5"))
        query.add(BoostQuery(boostedQuery, 100f), Occur.SHOULD)

        val xxBoostedQuery = TermQuery(Term(FIELD, "xx"))

        val q =
            DisjunctionMaxQuery(
                mutableListOf(query.build(), BoostQuery(xxBoostedQuery, 0f)),
                0.5f,
            )
        bqtest(BoostQuery(q, 0f), intArrayOf(0, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testMPQ7() {
        val qb = MultiPhraseQuery.Builder()
        qb.add(ta(arrayOf("w1")))
        qb.add(ta(arrayOf("w2")))
        qb.setSlop(1)
        bqtest(BoostQuery(qb.build(), 0f), intArrayOf(0, 1, 2))
    }

    @Test
    @Throws(Exception::class)
    open fun testBQ12() {
        // NOTE: using qtest not bqtest
        val query = BooleanQuery.Builder()
        query.add(TermQuery(Term(FIELD, "w1")), Occur.SHOULD)
        val boostedQuery = TermQuery(Term(FIELD, "w2"))
        query.add(BoostQuery(boostedQuery, 0f), Occur.SHOULD)

        qtest(query.build(), intArrayOf(0, 1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testBQ13() {
        // NOTE: using qtest not bqtest
        val query = BooleanQuery.Builder()
        query.add(TermQuery(Term(FIELD, "w1")), Occur.SHOULD)
        val boostedQuery = TermQuery(Term(FIELD, "w5"))
        query.add(BoostQuery(boostedQuery, 0f), Occur.MUST_NOT)

        qtest(query.build(), intArrayOf(1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testBQ18() {
        // NOTE: using qtest not bqtest
        val query = BooleanQuery.Builder()
        val boostedQuery = TermQuery(Term(FIELD, "w1"))
        query.add(BoostQuery(boostedQuery, 0f), Occur.MUST)
        query.add(TermQuery(Term(FIELD, "w2")), Occur.SHOULD)

        qtest(query.build(), intArrayOf(0, 1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testBQ21() {
        val builder = BooleanQuery.Builder()
        builder.add(TermQuery(Term(FIELD, "w1")), Occur.MUST)
        builder.add(TermQuery(Term(FIELD, "w2")), Occur.SHOULD)

        val query = builder.build()

        bqtest(BoostQuery(query, 0f), intArrayOf(0, 1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testBQ22() {
        val builder = BooleanQuery.Builder()
        val boostedQuery = TermQuery(Term(FIELD, "w1"))
        builder.add(BoostQuery(boostedQuery, 0f), Occur.MUST)
        builder.add(TermQuery(Term(FIELD, "w2")), Occur.SHOULD)
        val query = builder.build()

        bqtest(BoostQuery(query, 0f), intArrayOf(0, 1, 2, 3))
    }
}
