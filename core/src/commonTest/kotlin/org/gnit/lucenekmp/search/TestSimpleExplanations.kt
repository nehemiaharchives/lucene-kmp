package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.tests.search.BaseExplanationTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** TestExplanations subclass focusing on basic query types */
open class TestSimpleExplanations : BaseExplanationTestCase() {
    // we focus on queries that don't rewrite to other queries.
    // if we get those covered well, then the ones that rewrite should
    // also be covered.

    /* simple term tests */

    @Test
    @Throws(Exception::class)
    open fun testT1() {
        qtest(TermQuery(Term(FIELD, "w1")), intArrayOf(0, 1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testT2() {
        val termQuery = TermQuery(Term(FIELD, "w1"))
        qtest(BoostQuery(termQuery, 100f), intArrayOf(0, 1, 2, 3))
    }

    /* MatchAllDocs */

    @Test
    @Throws(Exception::class)
    open fun testMA1() {
        qtest(MatchAllDocsQuery(), intArrayOf(0, 1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testMA2() {
        val q: Query = MatchAllDocsQuery()
        qtest(BoostQuery(q, 1000f), intArrayOf(0, 1, 2, 3))
    }

    /* some simple phrase tests */

    @Test
    @Throws(Exception::class)
    open fun testP1() {
        val phraseQuery = PhraseQuery(FIELD, "w1", "w2")
        qtest(phraseQuery, intArrayOf(0))
    }

    @Test
    @Throws(Exception::class)
    open fun testP2() {
        val phraseQuery = PhraseQuery(FIELD, "w1", "w3")
        qtest(phraseQuery, intArrayOf(1, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testP3() {
        val phraseQuery = PhraseQuery(1, FIELD, "w1", "w2")
        qtest(phraseQuery, intArrayOf(0, 1, 2))
    }

    @Test
    @Throws(Exception::class)
    open fun testP4() {
        val phraseQuery = PhraseQuery(1, FIELD, "w2", "w3")
        qtest(phraseQuery, intArrayOf(0, 1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testP5() {
        val phraseQuery = PhraseQuery(1, FIELD, "w3", "w2")
        qtest(phraseQuery, intArrayOf(1, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testP6() {
        val phraseQuery = PhraseQuery(2, FIELD, "w3", "w2")
        qtest(phraseQuery, intArrayOf(0, 1, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testP7() {
        val phraseQuery = PhraseQuery(3, FIELD, "w3", "w2")
        qtest(phraseQuery, intArrayOf(0, 1, 2, 3))
    }

    /* ConstantScoreQueries */

    @Test
    @Throws(Exception::class)
    open fun testCSQ1() {
        val q: Query = ConstantScoreQuery(matchTheseItems(intArrayOf(0, 1, 2, 3)))
        qtest(q, intArrayOf(0, 1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testCSQ2() {
        val q: Query = ConstantScoreQuery(matchTheseItems(intArrayOf(1, 3)))
        qtest(q, intArrayOf(1, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testCSQ3() {
        val q: Query = ConstantScoreQuery(matchTheseItems(intArrayOf(0, 2)))
        qtest(BoostQuery(q, 1000f), intArrayOf(0, 2))
    }

    /* DisjunctionMaxQuery */

    @Test
    @Throws(Exception::class)
    open fun testDMQ1() {
        val q =
            DisjunctionMaxQuery(
                mutableListOf(
                    TermQuery(Term(FIELD, "w1")),
                    TermQuery(Term(FIELD, "w5")),
                ),
                0.0f,
            )
        qtest(q, intArrayOf(0, 1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testDMQ2() {
        val q =
            DisjunctionMaxQuery(
                mutableListOf(
                    TermQuery(Term(FIELD, "w1")),
                    TermQuery(Term(FIELD, "w5")),
                ),
                0.5f,
            )
        qtest(q, intArrayOf(0, 1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testDMQ3() {
        val q =
            DisjunctionMaxQuery(
                mutableListOf(
                    TermQuery(Term(FIELD, "QQ")),
                    TermQuery(Term(FIELD, "w5")),
                ),
                0.5f,
            )
        qtest(q, intArrayOf(0))
    }

    @Test
    @Throws(Exception::class)
    open fun testDMQ4() {
        val q =
            DisjunctionMaxQuery(
                mutableListOf(
                    TermQuery(Term(FIELD, "QQ")),
                    TermQuery(Term(FIELD, "xx")),
                ),
                0.5f,
            )
        qtest(q, intArrayOf(2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testDMQ5() {
        val booleanQuery = BooleanQuery.Builder()
        booleanQuery.add(TermQuery(Term(FIELD, "yy")), BooleanClause.Occur.SHOULD)
        booleanQuery.add(TermQuery(Term(FIELD, "QQ")), BooleanClause.Occur.MUST_NOT)

        val q =
            DisjunctionMaxQuery(
                mutableListOf(booleanQuery.build(), TermQuery(Term(FIELD, "xx"))),
                0.5f,
            )
        qtest(q, intArrayOf(2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testDMQ6() {
        val booleanQuery = BooleanQuery.Builder()
        booleanQuery.add(TermQuery(Term(FIELD, "yy")), BooleanClause.Occur.MUST_NOT)
        booleanQuery.add(TermQuery(Term(FIELD, "w3")), BooleanClause.Occur.SHOULD)

        val q =
            DisjunctionMaxQuery(
                mutableListOf(booleanQuery.build(), TermQuery(Term(FIELD, "xx"))),
                0.5f,
            )
        qtest(q, intArrayOf(0, 1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testDMQ7() {
        val booleanQuery = BooleanQuery.Builder()
        booleanQuery.add(TermQuery(Term(FIELD, "yy")), BooleanClause.Occur.MUST_NOT)
        booleanQuery.add(TermQuery(Term(FIELD, "w3")), BooleanClause.Occur.SHOULD)

        val q =
            DisjunctionMaxQuery(
                mutableListOf(booleanQuery.build(), TermQuery(Term(FIELD, "w2"))),
                0.5f,
            )
        qtest(q, intArrayOf(0, 1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testDMQ8() {
        val booleanQuery = BooleanQuery.Builder()
        booleanQuery.add(TermQuery(Term(FIELD, "yy")), BooleanClause.Occur.SHOULD)

        val boostedQuery = TermQuery(Term(FIELD, "w5"))
        booleanQuery.add(BoostQuery(boostedQuery, 100f), BooleanClause.Occur.SHOULD)

        val xxBoostedQuery = TermQuery(Term(FIELD, "xx"))

        val q =
            DisjunctionMaxQuery(
                mutableListOf(booleanQuery.build(), BoostQuery(xxBoostedQuery, 100000f)),
                0.5f,
            )
        qtest(q, intArrayOf(0, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testDMQ9() {
        val booleanQuery = BooleanQuery.Builder()
        booleanQuery.add(TermQuery(Term(FIELD, "yy")), BooleanClause.Occur.SHOULD)

        val boostedQuery = TermQuery(Term(FIELD, "w5"))
        booleanQuery.add(BoostQuery(boostedQuery, 100f), BooleanClause.Occur.SHOULD)

        val xxBoostedQuery = TermQuery(Term(FIELD, "xx"))

        val q =
            DisjunctionMaxQuery(
                mutableListOf(booleanQuery.build(), BoostQuery(xxBoostedQuery, 0f)),
                0.5f,
            )

        qtest(q, intArrayOf(0, 2, 3))
    }

    /* MultiPhraseQuery */

    @Test
    @Throws(Exception::class)
    open fun testMPQ1() {
        val qb = MultiPhraseQuery.Builder()
        qb.add(ta(arrayOf("w1")))
        qb.add(ta(arrayOf("w2", "w3", "xx")))
        qtest(qb.build(), intArrayOf(0, 1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testMPQ2() {
        val qb = MultiPhraseQuery.Builder()
        qb.add(ta(arrayOf("w1")))
        qb.add(ta(arrayOf("w2", "w3")))
        qtest(qb.build(), intArrayOf(0, 1, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testMPQ3() {
        val qb = MultiPhraseQuery.Builder()
        qb.add(ta(arrayOf("w1", "xx")))
        qb.add(ta(arrayOf("w2", "w3")))
        qtest(qb.build(), intArrayOf(0, 1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testMPQ4() {
        val qb = MultiPhraseQuery.Builder()
        qb.add(ta(arrayOf("w1")))
        qb.add(ta(arrayOf("w2")))
        qtest(qb.build(), intArrayOf(0))
    }

    @Test
    @Throws(Exception::class)
    open fun testMPQ5() {
        val qb = MultiPhraseQuery.Builder()
        qb.add(ta(arrayOf("w1")))
        qb.add(ta(arrayOf("w2")))
        qb.setSlop(1)
        qtest(qb.build(), intArrayOf(0, 1, 2))
    }

    @Test
    @Throws(Exception::class)
    open fun testMPQ6() {
        val qb = MultiPhraseQuery.Builder()
        qb.add(ta(arrayOf("w1", "w3")))
        qb.add(ta(arrayOf("w2")))
        qb.setSlop(1)
        qtest(qb.build(), intArrayOf(0, 1, 2, 3))
    }

    /* some simple tests of boolean queries containing term queries */

    @Test
    @Throws(Exception::class)
    open fun testBQ1() {
        val query = BooleanQuery.Builder()
        query.add(TermQuery(Term(FIELD, "w1")), BooleanClause.Occur.MUST)
        query.add(TermQuery(Term(FIELD, "w2")), BooleanClause.Occur.MUST)
        qtest(query.build(), intArrayOf(0, 1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testBQ2() {
        val query = BooleanQuery.Builder()
        query.add(TermQuery(Term(FIELD, "yy")), BooleanClause.Occur.MUST)
        query.add(TermQuery(Term(FIELD, "w3")), BooleanClause.Occur.MUST)
        qtest(query.build(), intArrayOf(2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testBQ3() {
        val query = BooleanQuery.Builder()
        query.add(TermQuery(Term(FIELD, "yy")), BooleanClause.Occur.SHOULD)
        query.add(TermQuery(Term(FIELD, "w3")), BooleanClause.Occur.MUST)
        qtest(query.build(), intArrayOf(0, 1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testBQ4() {
        val outerQuery = BooleanQuery.Builder()
        outerQuery.add(TermQuery(Term(FIELD, "w1")), BooleanClause.Occur.SHOULD)

        val innerQuery = BooleanQuery.Builder()
        innerQuery.add(TermQuery(Term(FIELD, "xx")), BooleanClause.Occur.MUST_NOT)
        innerQuery.add(TermQuery(Term(FIELD, "w2")), BooleanClause.Occur.SHOULD)
        outerQuery.add(innerQuery.build(), BooleanClause.Occur.SHOULD)

        qtest(outerQuery.build(), intArrayOf(0, 1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testBQ5() {
        val outerQuery = BooleanQuery.Builder()
        outerQuery.add(TermQuery(Term(FIELD, "w1")), BooleanClause.Occur.SHOULD)

        val innerQuery = BooleanQuery.Builder()
        innerQuery.add(TermQuery(Term(FIELD, "qq")), BooleanClause.Occur.MUST)
        innerQuery.add(TermQuery(Term(FIELD, "w2")), BooleanClause.Occur.SHOULD)
        outerQuery.add(innerQuery.build(), BooleanClause.Occur.SHOULD)

        qtest(outerQuery.build(), intArrayOf(0, 1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testBQ6() {
        val outerQuery = BooleanQuery.Builder()
        outerQuery.add(TermQuery(Term(FIELD, "w1")), BooleanClause.Occur.SHOULD)

        val innerQuery = BooleanQuery.Builder()
        innerQuery.add(TermQuery(Term(FIELD, "qq")), BooleanClause.Occur.MUST_NOT)
        innerQuery.add(TermQuery(Term(FIELD, "w5")), BooleanClause.Occur.SHOULD)
        outerQuery.add(innerQuery.build(), BooleanClause.Occur.MUST_NOT)

        qtest(outerQuery.build(), intArrayOf(1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testBQ7() {
        val outerQuery = BooleanQuery.Builder()
        outerQuery.add(TermQuery(Term(FIELD, "w1")), BooleanClause.Occur.MUST)

        val innerQuery = BooleanQuery.Builder()
        innerQuery.add(TermQuery(Term(FIELD, "qq")), BooleanClause.Occur.SHOULD)

        val childLeft = BooleanQuery.Builder()
        childLeft.add(TermQuery(Term(FIELD, "xx")), BooleanClause.Occur.SHOULD)
        childLeft.add(TermQuery(Term(FIELD, "w2")), BooleanClause.Occur.MUST_NOT)
        innerQuery.add(childLeft.build(), BooleanClause.Occur.SHOULD)

        val childRight = BooleanQuery.Builder()
        childRight.add(TermQuery(Term(FIELD, "w3")), BooleanClause.Occur.MUST)
        childRight.add(TermQuery(Term(FIELD, "w4")), BooleanClause.Occur.MUST)
        innerQuery.add(childRight.build(), BooleanClause.Occur.SHOULD)

        outerQuery.add(innerQuery.build(), BooleanClause.Occur.MUST)

        qtest(outerQuery.build(), intArrayOf(0))
    }

    @Test
    @Throws(Exception::class)
    open fun testBQ8() {
        val outerQuery = BooleanQuery.Builder()
        outerQuery.add(TermQuery(Term(FIELD, "w1")), BooleanClause.Occur.MUST)

        val innerQuery = BooleanQuery.Builder()
        innerQuery.add(TermQuery(Term(FIELD, "qq")), BooleanClause.Occur.SHOULD)

        val childLeft = BooleanQuery.Builder()
        childLeft.add(TermQuery(Term(FIELD, "xx")), BooleanClause.Occur.SHOULD)
        childLeft.add(TermQuery(Term(FIELD, "w2")), BooleanClause.Occur.MUST_NOT)
        innerQuery.add(childLeft.build(), BooleanClause.Occur.SHOULD)

        val childRight = BooleanQuery.Builder()
        childRight.add(TermQuery(Term(FIELD, "w3")), BooleanClause.Occur.MUST)
        childRight.add(TermQuery(Term(FIELD, "w4")), BooleanClause.Occur.MUST)
        innerQuery.add(childRight.build(), BooleanClause.Occur.SHOULD)

        outerQuery.add(innerQuery.build(), BooleanClause.Occur.SHOULD)

        qtest(outerQuery.build(), intArrayOf(0, 1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testBQ9() {
        val outerQuery = BooleanQuery.Builder()
        outerQuery.add(TermQuery(Term(FIELD, "w1")), BooleanClause.Occur.MUST)

        val innerQuery = BooleanQuery.Builder()
        innerQuery.add(TermQuery(Term(FIELD, "qq")), BooleanClause.Occur.SHOULD)

        val childLeft = BooleanQuery.Builder()
        childLeft.add(TermQuery(Term(FIELD, "xx")), BooleanClause.Occur.MUST_NOT)
        childLeft.add(TermQuery(Term(FIELD, "w2")), BooleanClause.Occur.SHOULD)
        innerQuery.add(childLeft.build(), BooleanClause.Occur.SHOULD)

        val childRight = BooleanQuery.Builder()
        childRight.add(TermQuery(Term(FIELD, "w3")), BooleanClause.Occur.MUST)
        childRight.add(TermQuery(Term(FIELD, "w4")), BooleanClause.Occur.MUST)
        innerQuery.add(childRight.build(), BooleanClause.Occur.MUST_NOT)

        outerQuery.add(innerQuery.build(), BooleanClause.Occur.SHOULD)

        qtest(outerQuery.build(), intArrayOf(0, 1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testBQ10() {
        val outerQuery = BooleanQuery.Builder()
        outerQuery.add(TermQuery(Term(FIELD, "w1")), BooleanClause.Occur.MUST)

        val innerQuery = BooleanQuery.Builder()
        innerQuery.add(TermQuery(Term(FIELD, "qq")), BooleanClause.Occur.SHOULD)

        val childLeft = BooleanQuery.Builder()
        childLeft.add(TermQuery(Term(FIELD, "xx")), BooleanClause.Occur.MUST_NOT)
        childLeft.add(TermQuery(Term(FIELD, "w2")), BooleanClause.Occur.SHOULD)
        innerQuery.add(childLeft.build(), BooleanClause.Occur.SHOULD)

        val childRight = BooleanQuery.Builder()
        childRight.add(TermQuery(Term(FIELD, "w3")), BooleanClause.Occur.MUST)
        childRight.add(TermQuery(Term(FIELD, "w4")), BooleanClause.Occur.MUST)
        innerQuery.add(childRight.build(), BooleanClause.Occur.MUST_NOT)

        outerQuery.add(innerQuery.build(), BooleanClause.Occur.MUST)

        qtest(outerQuery.build(), intArrayOf(1))
    }

    @Test
    @Throws(Exception::class)
    open fun testBQ11() {
        val query = BooleanQuery.Builder()
        query.add(TermQuery(Term(FIELD, "w1")), BooleanClause.Occur.SHOULD)
        val boostedQuery = TermQuery(Term(FIELD, "w1"))
        query.add(BoostQuery(boostedQuery, 1000f), BooleanClause.Occur.SHOULD)

        qtest(query.build(), intArrayOf(0, 1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testBQ14() {
        val q = BooleanQuery.Builder()
        q.add(TermQuery(Term(FIELD, "QQQQQ")), BooleanClause.Occur.SHOULD)
        q.add(TermQuery(Term(FIELD, "w1")), BooleanClause.Occur.SHOULD)
        qtest(q.build(), intArrayOf(0, 1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testBQ15() {
        val q = BooleanQuery.Builder()
        q.add(TermQuery(Term(FIELD, "QQQQQ")), BooleanClause.Occur.MUST_NOT)
        q.add(TermQuery(Term(FIELD, "w1")), BooleanClause.Occur.SHOULD)
        qtest(q.build(), intArrayOf(0, 1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testBQ16() {
        val q = BooleanQuery.Builder()
        q.add(TermQuery(Term(FIELD, "QQQQQ")), BooleanClause.Occur.SHOULD)

        val booleanQuery = BooleanQuery.Builder()
        booleanQuery.add(TermQuery(Term(FIELD, "w1")), BooleanClause.Occur.SHOULD)
        booleanQuery.add(TermQuery(Term(FIELD, "xx")), BooleanClause.Occur.MUST_NOT)

        q.add(booleanQuery.build(), BooleanClause.Occur.SHOULD)
        qtest(q.build(), intArrayOf(0, 1))
    }

    @Test
    @Throws(Exception::class)
    open fun testBQ17() {
        val q = BooleanQuery.Builder()
        q.add(TermQuery(Term(FIELD, "w2")), BooleanClause.Occur.SHOULD)

        val booleanQuery = BooleanQuery.Builder()
        booleanQuery.add(TermQuery(Term(FIELD, "w1")), BooleanClause.Occur.SHOULD)
        booleanQuery.add(TermQuery(Term(FIELD, "xx")), BooleanClause.Occur.MUST_NOT)

        q.add(booleanQuery.build(), BooleanClause.Occur.SHOULD)
        qtest(q.build(), intArrayOf(0, 1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testBQ19() {
        val query = BooleanQuery.Builder()
        query.add(TermQuery(Term(FIELD, "yy")), BooleanClause.Occur.MUST_NOT)
        query.add(TermQuery(Term(FIELD, "w3")), BooleanClause.Occur.SHOULD)

        qtest(query.build(), intArrayOf(0, 1))
    }

    @Test
    @Throws(Exception::class)
    open fun testBQ20() {
        val q = BooleanQuery.Builder()
        q.setMinimumNumberShouldMatch(2)
        q.add(TermQuery(Term(FIELD, "QQQQQ")), BooleanClause.Occur.SHOULD)
        q.add(TermQuery(Term(FIELD, "yy")), BooleanClause.Occur.SHOULD)
        q.add(TermQuery(Term(FIELD, "zz")), BooleanClause.Occur.SHOULD)
        q.add(TermQuery(Term(FIELD, "w5")), BooleanClause.Occur.SHOULD)
        q.add(TermQuery(Term(FIELD, "w4")), BooleanClause.Occur.SHOULD)

        qtest(q.build(), intArrayOf(0, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testBQ21() {
        val q = BooleanQuery.Builder()
        q.add(TermQuery(Term(FIELD, "yy")), BooleanClause.Occur.SHOULD)
        q.add(TermQuery(Term(FIELD, "zz")), BooleanClause.Occur.SHOULD)

        qtest(q.build(), intArrayOf(1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testBQ23() {
        val query = BooleanQuery.Builder()
        query.add(TermQuery(Term(FIELD, "w1")), BooleanClause.Occur.FILTER)
        query.add(TermQuery(Term(FIELD, "w2")), BooleanClause.Occur.FILTER)
        qtest(query.build(), intArrayOf(0, 1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testBQ24() {
        val query = BooleanQuery.Builder()
        query.add(TermQuery(Term(FIELD, "w1")), BooleanClause.Occur.FILTER)
        query.add(TermQuery(Term(FIELD, "w2")), BooleanClause.Occur.SHOULD)
        qtest(query.build(), intArrayOf(0, 1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testBQ25() {
        val query = BooleanQuery.Builder()
        query.add(TermQuery(Term(FIELD, "w1")), BooleanClause.Occur.FILTER)
        query.add(TermQuery(Term(FIELD, "w2")), BooleanClause.Occur.MUST)
        qtest(query.build(), intArrayOf(0, 1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testBQ26() {
        val query = BooleanQuery.Builder()
        query.add(TermQuery(Term(FIELD, "w1")), BooleanClause.Occur.FILTER)
        query.add(TermQuery(Term(FIELD, "xx")), BooleanClause.Occur.MUST_NOT)
        qtest(query.build(), intArrayOf(0, 1))
    }

    /* BQ of TQ: using alt so some fields have zero boost and some don't */

    @Test
    @Throws(Exception::class)
    open fun testMultiFieldBQ1() {
        val query = BooleanQuery.Builder()
        query.add(TermQuery(Term(FIELD, "w1")), BooleanClause.Occur.MUST)
        query.add(TermQuery(Term(ALTFIELD, "w2")), BooleanClause.Occur.MUST)

        qtest(query.build(), intArrayOf(0, 1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testMultiFieldBQ2() {
        val query = BooleanQuery.Builder()
        query.add(TermQuery(Term(FIELD, "yy")), BooleanClause.Occur.MUST)
        query.add(TermQuery(Term(ALTFIELD, "w3")), BooleanClause.Occur.MUST)

        qtest(query.build(), intArrayOf(2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testMultiFieldBQ3() {
        val query = BooleanQuery.Builder()
        query.add(TermQuery(Term(FIELD, "yy")), BooleanClause.Occur.SHOULD)
        query.add(TermQuery(Term(ALTFIELD, "w3")), BooleanClause.Occur.MUST)

        qtest(query.build(), intArrayOf(0, 1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testMultiFieldBQ4() {
        val outerQuery = BooleanQuery.Builder()
        outerQuery.add(TermQuery(Term(FIELD, "w1")), BooleanClause.Occur.SHOULD)

        val innerQuery = BooleanQuery.Builder()
        innerQuery.add(TermQuery(Term(FIELD, "xx")), BooleanClause.Occur.MUST_NOT)
        innerQuery.add(TermQuery(Term(ALTFIELD, "w2")), BooleanClause.Occur.SHOULD)
        outerQuery.add(innerQuery.build(), BooleanClause.Occur.SHOULD)

        qtest(outerQuery.build(), intArrayOf(0, 1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testMultiFieldBQ5() {
        val outerQuery = BooleanQuery.Builder()
        outerQuery.add(TermQuery(Term(FIELD, "w1")), BooleanClause.Occur.SHOULD)

        val innerQuery = BooleanQuery.Builder()
        innerQuery.add(TermQuery(Term(ALTFIELD, "qq")), BooleanClause.Occur.MUST)
        innerQuery.add(TermQuery(Term(ALTFIELD, "w2")), BooleanClause.Occur.SHOULD)
        outerQuery.add(innerQuery.build(), BooleanClause.Occur.SHOULD)

        qtest(outerQuery.build(), intArrayOf(0, 1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testMultiFieldBQ6() {
        val outerQuery = BooleanQuery.Builder()
        outerQuery.add(TermQuery(Term(FIELD, "w1")), BooleanClause.Occur.SHOULD)

        val innerQuery = BooleanQuery.Builder()
        innerQuery.add(TermQuery(Term(ALTFIELD, "qq")), BooleanClause.Occur.MUST_NOT)
        innerQuery.add(TermQuery(Term(ALTFIELD, "w5")), BooleanClause.Occur.SHOULD)
        outerQuery.add(innerQuery.build(), BooleanClause.Occur.MUST_NOT)

        qtest(outerQuery.build(), intArrayOf(1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testMultiFieldBQ7() {
        val outerQuery = BooleanQuery.Builder()
        outerQuery.add(TermQuery(Term(FIELD, "w1")), BooleanClause.Occur.MUST)

        val innerQuery = BooleanQuery.Builder()
        innerQuery.add(TermQuery(Term(ALTFIELD, "qq")), BooleanClause.Occur.SHOULD)

        val childLeft = BooleanQuery.Builder()
        childLeft.add(TermQuery(Term(ALTFIELD, "xx")), BooleanClause.Occur.SHOULD)
        childLeft.add(TermQuery(Term(ALTFIELD, "w2")), BooleanClause.Occur.MUST_NOT)
        innerQuery.add(childLeft.build(), BooleanClause.Occur.SHOULD)

        val childRight = BooleanQuery.Builder()
        childRight.add(TermQuery(Term(ALTFIELD, "w3")), BooleanClause.Occur.MUST)
        childRight.add(TermQuery(Term(ALTFIELD, "w4")), BooleanClause.Occur.MUST)
        innerQuery.add(childRight.build(), BooleanClause.Occur.SHOULD)

        outerQuery.add(innerQuery.build(), BooleanClause.Occur.MUST)

        qtest(outerQuery.build(), intArrayOf(0))
    }

    @Test
    @Throws(Exception::class)
    open fun testMultiFieldBQ8() {
        val outerQuery = BooleanQuery.Builder()
        outerQuery.add(TermQuery(Term(ALTFIELD, "w1")), BooleanClause.Occur.MUST)

        val innerQuery = BooleanQuery.Builder()
        innerQuery.add(TermQuery(Term(FIELD, "qq")), BooleanClause.Occur.SHOULD)

        val childLeft = BooleanQuery.Builder()
        childLeft.add(TermQuery(Term(ALTFIELD, "xx")), BooleanClause.Occur.SHOULD)
        childLeft.add(TermQuery(Term(FIELD, "w2")), BooleanClause.Occur.MUST_NOT)
        innerQuery.add(childLeft.build(), BooleanClause.Occur.SHOULD)

        val childRight = BooleanQuery.Builder()
        childRight.add(TermQuery(Term(ALTFIELD, "w3")), BooleanClause.Occur.MUST)
        childRight.add(TermQuery(Term(FIELD, "w4")), BooleanClause.Occur.MUST)
        innerQuery.add(childRight.build(), BooleanClause.Occur.SHOULD)

        outerQuery.add(innerQuery.build(), BooleanClause.Occur.SHOULD)

        qtest(outerQuery.build(), intArrayOf(0, 1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testMultiFieldBQ9() {
        val outerQuery = BooleanQuery.Builder()
        outerQuery.add(TermQuery(Term(FIELD, "w1")), BooleanClause.Occur.MUST)

        val innerQuery = BooleanQuery.Builder()
        innerQuery.add(TermQuery(Term(ALTFIELD, "qq")), BooleanClause.Occur.SHOULD)

        val childLeft = BooleanQuery.Builder()
        childLeft.add(TermQuery(Term(FIELD, "xx")), BooleanClause.Occur.MUST_NOT)
        childLeft.add(TermQuery(Term(FIELD, "w2")), BooleanClause.Occur.SHOULD)
        innerQuery.add(childLeft.build(), BooleanClause.Occur.SHOULD)

        val childRight = BooleanQuery.Builder()
        childRight.add(TermQuery(Term(ALTFIELD, "w3")), BooleanClause.Occur.MUST)
        childRight.add(TermQuery(Term(FIELD, "w4")), BooleanClause.Occur.MUST)
        innerQuery.add(childRight.build(), BooleanClause.Occur.MUST_NOT)

        outerQuery.add(innerQuery.build(), BooleanClause.Occur.SHOULD)

        qtest(outerQuery.build(), intArrayOf(0, 1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testMultiFieldBQ10() {
        val outerQuery = BooleanQuery.Builder()
        outerQuery.add(TermQuery(Term(FIELD, "w1")), BooleanClause.Occur.MUST)

        val innerQuery = BooleanQuery.Builder()
        innerQuery.add(TermQuery(Term(ALTFIELD, "qq")), BooleanClause.Occur.SHOULD)

        val childLeft = BooleanQuery.Builder()
        childLeft.add(TermQuery(Term(FIELD, "xx")), BooleanClause.Occur.MUST_NOT)
        childLeft.add(TermQuery(Term(ALTFIELD, "w2")), BooleanClause.Occur.SHOULD)
        innerQuery.add(childLeft.build(), BooleanClause.Occur.SHOULD)

        val childRight = BooleanQuery.Builder()
        childRight.add(TermQuery(Term(ALTFIELD, "w3")), BooleanClause.Occur.MUST)
        childRight.add(TermQuery(Term(FIELD, "w4")), BooleanClause.Occur.MUST)
        innerQuery.add(childRight.build(), BooleanClause.Occur.MUST_NOT)

        outerQuery.add(innerQuery.build(), BooleanClause.Occur.MUST)

        qtest(outerQuery.build(), intArrayOf(1))
    }

    /* BQ of PQ: using alt so some fields have zero boost and some don't */

    @Test
    @Throws(Exception::class)
    open fun testMultiFieldBQofPQ1() {
        val query = BooleanQuery.Builder()

        val leftChild = PhraseQuery(FIELD, "w1", "w2")
        query.add(leftChild, BooleanClause.Occur.SHOULD)

        val rightChild = PhraseQuery(ALTFIELD, "w1", "w2")
        query.add(rightChild, BooleanClause.Occur.SHOULD)

        qtest(query.build(), intArrayOf(0))
    }

    @Test
    @Throws(Exception::class)
    open fun testMultiFieldBQofPQ2() {
        val query = BooleanQuery.Builder()

        val leftChild = PhraseQuery(FIELD, "w1", "w3")
        query.add(leftChild, BooleanClause.Occur.SHOULD)

        val rightChild = PhraseQuery(ALTFIELD, "w1", "w3")
        query.add(rightChild, BooleanClause.Occur.SHOULD)

        qtest(query.build(), intArrayOf(1, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testMultiFieldBQofPQ3() {
        val query = BooleanQuery.Builder()

        val leftChild = PhraseQuery(1, FIELD, "w1", "w2")
        query.add(leftChild, BooleanClause.Occur.SHOULD)

        val rightChild = PhraseQuery(1, ALTFIELD, "w1", "w2")
        query.add(rightChild, BooleanClause.Occur.SHOULD)

        qtest(query.build(), intArrayOf(0, 1, 2))
    }

    @Test
    @Throws(Exception::class)
    open fun testMultiFieldBQofPQ4() {
        val query = BooleanQuery.Builder()

        val leftChild = PhraseQuery(1, FIELD, "w2", "w3")
        query.add(leftChild, BooleanClause.Occur.SHOULD)

        val rightChild = PhraseQuery(1, ALTFIELD, "w2", "w3")
        query.add(rightChild, BooleanClause.Occur.SHOULD)

        qtest(query.build(), intArrayOf(0, 1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testMultiFieldBQofPQ5() {
        val query = BooleanQuery.Builder()

        val leftChild = PhraseQuery(1, FIELD, "w3", "w2")
        query.add(leftChild, BooleanClause.Occur.SHOULD)

        val rightChild = PhraseQuery(1, ALTFIELD, "w3", "w2")
        query.add(rightChild, BooleanClause.Occur.SHOULD)

        qtest(query.build(), intArrayOf(1, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testMultiFieldBQofPQ6() {
        val query = BooleanQuery.Builder()

        val leftChild = PhraseQuery(2, FIELD, "w3", "w2")
        query.add(leftChild, BooleanClause.Occur.SHOULD)

        val rightChild = PhraseQuery(2, ALTFIELD, "w3", "w2")
        query.add(rightChild, BooleanClause.Occur.SHOULD)

        qtest(query.build(), intArrayOf(0, 1, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testMultiFieldBQofPQ7() {
        val query = BooleanQuery.Builder()

        val leftChild = PhraseQuery(3, FIELD, "w3", "w2")
        query.add(leftChild, BooleanClause.Occur.SHOULD)

        val rightChild = PhraseQuery(1, ALTFIELD, "w3", "w2")
        query.add(rightChild, BooleanClause.Occur.SHOULD)

        qtest(query.build(), intArrayOf(0, 1, 2, 3))
    }

    @Test
    @Throws(Exception::class)
    open fun testSynonymQuery() {
        val query =
            SynonymQuery.Builder(FIELD)
                .addTerm(Term(FIELD, "w1"))
                .addTerm(Term(FIELD, "w2"))
                .build()
        qtest(query, intArrayOf(0, 1, 2, 3))
    }

    @Test
    open fun testEquality() {
        val e1 = Explanation.match(1f, "an explanation")
        val e2 =
            Explanation.match(1f, "an explanation", Explanation.match(1f, "a subexplanation"))
        val e25 =
            Explanation.match(
                1f,
                "an explanation",
                Explanation.match(
                    1f,
                    "a subexplanation",
                    Explanation.match(1f, "a subsubexplanation"),
                ),
            )
        val e3 = Explanation.match(1f, "an explanation")
        val e4 = Explanation.match(2f, "an explanation")
        val e5 = Explanation.noMatch("an explanation")
        val e6 =
            Explanation.noMatch("an explanation", Explanation.match(1f, "a subexplanation"))
        val e7 = Explanation.noMatch("an explanation")
        val e8 = Explanation.match(1f, "another explanation")

        assertEquals(e1, e3)
        assertFalse(e1.equals(e2))
        assertFalse(e2.equals(e25))
        assertFalse(e1.equals(e4))
        assertFalse(e1.equals(e5))
        assertEquals(e5, e7)
        assertFalse(e5.equals(e6))
        assertFalse(e1.equals(e8))

        assertEquals(e1.hashCode(), e3.hashCode())
        assertEquals(e5.hashCode(), e7.hashCode())
    }
}
