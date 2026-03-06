package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.tests.search.RandomApproximationQuery
import org.gnit.lucenekmp.tests.search.SearchEquivalenceTestBase
import kotlin.test.Test

/** Basic equivalence tests for approximations. */
class TestApproximationSearchEquivalence : SearchEquivalenceTestBase() {
    @Test
    @Throws(Exception::class)
    fun testConjunction() {
        val t1 = randomTerm()
        val t2 = randomTerm()
        val q1 = TermQuery(t1)
        val q2 = TermQuery(t2)

        val bq1 = BooleanQuery.Builder()
        bq1.add(q1, BooleanClause.Occur.MUST)
        bq1.add(q2, BooleanClause.Occur.MUST)

        val bq2 = BooleanQuery.Builder()
        bq2.add(RandomApproximationQuery(q1, random()), BooleanClause.Occur.MUST)
        bq2.add(RandomApproximationQuery(q2, random()), BooleanClause.Occur.MUST)

        assertSameScores(bq1.build(), bq2.build())
    }

    @Test
    @Throws(Exception::class)
    fun testNestedConjunction() {
        val t1 = randomTerm()
        var t2: Term
        do {
            t2 = randomTerm()
        } while (t1 == t2)
        val t3 = randomTerm()
        val q1 = TermQuery(t1)
        val q2 = TermQuery(t2)
        val q3 = TermQuery(t3)

        val bq1 = BooleanQuery.Builder()
        bq1.add(q1, BooleanClause.Occur.MUST)
        bq1.add(q2, BooleanClause.Occur.MUST)

        val bq2 = BooleanQuery.Builder()
        bq2.add(bq1.build(), BooleanClause.Occur.MUST)
        bq2.add(q3, BooleanClause.Occur.MUST)

        val bq3 = BooleanQuery.Builder()
        bq3.add(RandomApproximationQuery(q1, random()), BooleanClause.Occur.MUST)
        bq3.add(RandomApproximationQuery(q2, random()), BooleanClause.Occur.MUST)

        val bq4 = BooleanQuery.Builder()
        bq4.add(bq3.build(), BooleanClause.Occur.MUST)
        bq4.add(q3, BooleanClause.Occur.MUST)

        assertSameScores(bq2.build(), bq4.build())
    }

    @Test
    @Throws(Exception::class)
    fun testDisjunction() {
        val t1 = randomTerm()
        val t2 = randomTerm()
        val q1 = TermQuery(t1)
        val q2 = TermQuery(t2)

        val bq1 = BooleanQuery.Builder()
        bq1.add(q1, BooleanClause.Occur.SHOULD)
        bq1.add(q2, BooleanClause.Occur.SHOULD)

        val bq2 = BooleanQuery.Builder()
        bq2.add(RandomApproximationQuery(q1, random()), BooleanClause.Occur.SHOULD)
        bq2.add(RandomApproximationQuery(q2, random()), BooleanClause.Occur.SHOULD)

        assertSameScores(bq1.build(), bq2.build())
    }

    @Test
    @Throws(Exception::class)
    fun testNestedDisjunction() {
        val t1 = randomTerm()
        var t2: Term
        do {
            t2 = randomTerm()
        } while (t1 == t2)
        val t3 = randomTerm()
        val q1 = TermQuery(t1)
        val q2 = TermQuery(t2)
        val q3 = TermQuery(t3)

        val bq1 = BooleanQuery.Builder()
        bq1.add(q1, BooleanClause.Occur.SHOULD)
        bq1.add(q2, BooleanClause.Occur.SHOULD)

        val bq2 = BooleanQuery.Builder()
        bq2.add(bq1.build(), BooleanClause.Occur.SHOULD)
        bq2.add(q3, BooleanClause.Occur.SHOULD)

        val bq3 = BooleanQuery.Builder()
        bq3.add(RandomApproximationQuery(q1, random()), BooleanClause.Occur.SHOULD)
        bq3.add(RandomApproximationQuery(q2, random()), BooleanClause.Occur.SHOULD)

        val bq4 = BooleanQuery.Builder()
        bq4.add(bq3.build(), BooleanClause.Occur.SHOULD)
        bq4.add(q3, BooleanClause.Occur.SHOULD)

        assertSameScores(bq2.build(), bq4.build())
    }

    @Test
    @Throws(Exception::class)
    fun testDisjunctionInConjunction() {
        val t1 = randomTerm()
        var t2: Term
        do {
            t2 = randomTerm()
        } while (t1 == t2)
        val t3 = randomTerm()
        val q1 = TermQuery(t1)
        val q2 = TermQuery(t2)
        val q3 = TermQuery(t3)

        val bq1 = BooleanQuery.Builder()
        bq1.add(q1, BooleanClause.Occur.SHOULD)
        bq1.add(q2, BooleanClause.Occur.SHOULD)

        val bq2 = BooleanQuery.Builder()
        bq2.add(bq1.build(), BooleanClause.Occur.MUST)
        bq2.add(q3, BooleanClause.Occur.MUST)

        val bq3 = BooleanQuery.Builder()
        bq3.add(RandomApproximationQuery(q1, random()), BooleanClause.Occur.SHOULD)
        bq3.add(RandomApproximationQuery(q2, random()), BooleanClause.Occur.SHOULD)

        val bq4 = BooleanQuery.Builder()
        bq4.add(bq3.build(), BooleanClause.Occur.MUST)
        bq4.add(q3, BooleanClause.Occur.MUST)

        assertSameScores(bq2.build(), bq4.build())
    }

    @Test
    @Throws(Exception::class)
    fun testConjunctionInDisjunction() {
        val t1 = randomTerm()
        var t2: Term
        do {
            t2 = randomTerm()
        } while (t1 == t2)
        val t3 = randomTerm()
        val q1 = TermQuery(t1)
        val q2 = TermQuery(t2)
        val q3 = TermQuery(t3)

        val bq1 = BooleanQuery.Builder()
        bq1.add(q1, BooleanClause.Occur.MUST)
        bq1.add(q2, BooleanClause.Occur.MUST)

        val bq2 = BooleanQuery.Builder()
        bq2.add(bq1.build(), BooleanClause.Occur.SHOULD)
        bq2.add(q3, BooleanClause.Occur.SHOULD)

        val bq3 = BooleanQuery.Builder()
        bq3.add(RandomApproximationQuery(q1, random()), BooleanClause.Occur.MUST)
        bq3.add(RandomApproximationQuery(q2, random()), BooleanClause.Occur.MUST)

        val bq4 = BooleanQuery.Builder()
        bq4.add(bq3.build(), BooleanClause.Occur.SHOULD)
        bq4.add(q3, BooleanClause.Occur.SHOULD)

        assertSameScores(bq2.build(), bq4.build())
    }

    @Test
    @Throws(Exception::class)
    fun testConstantScore() {
        val t1 = randomTerm()
        val t2 = randomTerm()
        val q1 = TermQuery(t1)
        val q2 = TermQuery(t2)

        val bq1 = BooleanQuery.Builder()
        bq1.add(ConstantScoreQuery(q1), BooleanClause.Occur.MUST)
        bq1.add(ConstantScoreQuery(q2), BooleanClause.Occur.MUST)

        val bq2 = BooleanQuery.Builder()
        bq2.add(ConstantScoreQuery(RandomApproximationQuery(q1, random())), BooleanClause.Occur.MUST)
        bq2.add(ConstantScoreQuery(RandomApproximationQuery(q2, random())), BooleanClause.Occur.MUST)

        assertSameScores(bq1.build(), bq2.build())
    }

    @Test
    @Throws(Exception::class)
    fun testExclusion() {
        val t1 = randomTerm()
        val t2 = randomTerm()
        val q1 = TermQuery(t1)
        val q2 = TermQuery(t2)

        val bq1 = BooleanQuery.Builder()
        bq1.add(q1, BooleanClause.Occur.MUST)
        bq1.add(q2, BooleanClause.Occur.MUST_NOT)

        val bq2 = BooleanQuery.Builder()
        bq2.add(RandomApproximationQuery(q1, random()), BooleanClause.Occur.MUST)
        bq2.add(RandomApproximationQuery(q2, random()), BooleanClause.Occur.MUST_NOT)

        assertSameScores(bq1.build(), bq2.build())
    }

    @Test
    @Throws(Exception::class)
    fun testNestedExclusion() {
        val t1 = randomTerm()
        var t2: Term
        do {
            t2 = randomTerm()
        } while (t1 == t2)
        val t3 = randomTerm()
        val q1 = TermQuery(t1)
        val q2 = TermQuery(t2)
        val q3 = TermQuery(t3)

        val bq1 = BooleanQuery.Builder()
        bq1.add(q1, BooleanClause.Occur.MUST)
        bq1.add(q2, BooleanClause.Occur.MUST_NOT)

        val bq2 = BooleanQuery.Builder()
        bq2.add(bq1.build(), BooleanClause.Occur.MUST)
        bq2.add(q3, BooleanClause.Occur.MUST)

        // Both req and excl have approximations
        var bq3 = BooleanQuery.Builder()
        bq3.add(RandomApproximationQuery(q1, random()), BooleanClause.Occur.MUST)
        bq3.add(RandomApproximationQuery(q2, random()), BooleanClause.Occur.MUST_NOT)

        var bq4 = BooleanQuery.Builder()
        bq4.add(bq3.build(), BooleanClause.Occur.MUST)
        bq4.add(q3, BooleanClause.Occur.MUST)

        assertSameScores(bq2.build(), bq4.build())

        // Only req has an approximation
        bq3 = BooleanQuery.Builder()
        bq3.add(RandomApproximationQuery(q1, random()), BooleanClause.Occur.MUST)
        bq3.add(q2, BooleanClause.Occur.MUST_NOT)

        bq4 = BooleanQuery.Builder()
        bq4.add(bq3.build(), BooleanClause.Occur.MUST)
        bq4.add(q3, BooleanClause.Occur.MUST)

        assertSameScores(bq2.build(), bq4.build())

        // Only excl has an approximation
        bq3 = BooleanQuery.Builder()
        bq3.add(q1, BooleanClause.Occur.MUST)
        bq3.add(RandomApproximationQuery(q2, random()), BooleanClause.Occur.MUST_NOT)

        bq4 = BooleanQuery.Builder()
        bq4.add(bq3.build(), BooleanClause.Occur.MUST)
        bq4.add(q3, BooleanClause.Occur.MUST)

        assertSameScores(bq2.build(), bq4.build())
    }

    @Test
    @Throws(Exception::class)
    fun testReqOpt() {
        val t1 = randomTerm()
        var t2: Term
        do {
            t2 = randomTerm()
        } while (t1 == t2)
        val t3 = randomTerm()
        val q1 = TermQuery(t1)
        val q2 = TermQuery(t2)
        val q3 = TermQuery(t3)

        val bq1 = BooleanQuery.Builder()
        bq1.add(q1, BooleanClause.Occur.MUST)
        bq1.add(q2, BooleanClause.Occur.SHOULD)

        val bq2 = BooleanQuery.Builder()
        bq2.add(bq1.build(), BooleanClause.Occur.MUST)
        bq2.add(q3, BooleanClause.Occur.MUST)

        val bq3 = BooleanQuery.Builder()
        bq3.add(RandomApproximationQuery(q1, random()), BooleanClause.Occur.MUST)
        bq3.add(RandomApproximationQuery(q2, random()), BooleanClause.Occur.SHOULD)

        val bq4 = BooleanQuery.Builder()
        bq4.add(bq3.build(), BooleanClause.Occur.MUST)
        bq4.add(q3, BooleanClause.Occur.MUST)

        assertSameScores(bq2.build(), bq4.build())
    }
}
