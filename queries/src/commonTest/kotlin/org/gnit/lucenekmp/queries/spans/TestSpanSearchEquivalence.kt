package org.gnit.lucenekmp.queries.spans

import org.gnit.lucenekmp.queries.spans.SpanTestUtil.spanQuery
import org.gnit.lucenekmp.search.BooleanClause
import org.gnit.lucenekmp.search.BooleanQuery
import org.gnit.lucenekmp.search.PhraseQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.tests.search.SearchEquivalenceTestBase
import kotlin.test.Test

/** Basic equivalence tests for span queries */
class TestSpanSearchEquivalence : SearchEquivalenceTestBase() {
    // TODO: we could go a little crazy for a lot of these,
    // but these are just simple minimal cases in case something
    // goes horribly wrong. Put more intense tests elsewhere.

    /** SpanTermQuery(A) = TermQuery(A) */
    @Test
    fun testSpanTermVersusTerm() {
        val t1 = randomTerm()
        assertSameScores(TermQuery(t1), spanQuery(SpanTermQuery(t1)))
    }

    /** SpanOrQuery(A) = SpanTermQuery(A) */
    @Test
    fun testSpanOrVersusTerm() {
        val t1 = randomTerm()
        val term = spanQuery(SpanTermQuery(t1))
        assertSameSet(spanQuery(SpanOrQuery(term)), term)
    }

    /** SpanOrQuery(A, A) = SpanTermQuery(A) */
    @Test
    fun testSpanOrDoubleVersusTerm() {
        val t1 = randomTerm()
        val term = spanQuery(SpanTermQuery(t1))
        assertSameSet(spanQuery(SpanOrQuery(term, term)), term)
    }

    /** SpanOrQuery(A, B) = (A B) */
    @Test
    fun testSpanOrVersusBooleanTerm() {
        val t1 = randomTerm()
        val t2 = randomTerm()
        val q1 = BooleanQuery.Builder()
        q1.add(TermQuery(t1), BooleanClause.Occur.SHOULD)
        q1.add(TermQuery(t2), BooleanClause.Occur.SHOULD)
        val q2 = spanQuery(SpanOrQuery(spanQuery(SpanTermQuery(t1)), spanQuery(SpanTermQuery(t2))))
        assertSameSet(q1.build(), q2)
    }

    /**
     * SpanOrQuery(SpanNearQuery[A B], SpanNearQuery[C D]) = (SpanNearQuery[A B], SpanNearQuery[C D])
     */
    @Test
    fun testSpanOrVersusBooleanNear() {
        val t1 = randomTerm()
        val t2 = randomTerm()
        val t3 = randomTerm()
        val t4 = randomTerm()
        val near1 =
            spanQuery(
                SpanNearQuery(
                    arrayOf(
                        spanQuery(SpanTermQuery(t1)),
                        spanQuery(SpanTermQuery(t2)),
                    ),
                    10,
                    random().nextBoolean(),
                ),
            )
        val near2 =
            spanQuery(
                SpanNearQuery(
                    arrayOf(
                        spanQuery(SpanTermQuery(t3)),
                        spanQuery(SpanTermQuery(t4)),
                    ),
                    10,
                    random().nextBoolean(),
                ),
            )
        val q1 = BooleanQuery.Builder()
        q1.add(near1, BooleanClause.Occur.SHOULD)
        q1.add(near2, BooleanClause.Occur.SHOULD)
        val q2 = spanQuery(SpanOrQuery(near1, near2))
        assertSameSet(q1.build(), q2)
    }

    /** SpanNotQuery(A, B) ⊆ SpanTermQuery(A) */
    @Test
    fun testSpanNotVersusSpanTerm() {
        val t1 = randomTerm()
        val t2 = randomTerm()
        assertSubsetOf(
            spanQuery(SpanNotQuery(spanQuery(SpanTermQuery(t1)), spanQuery(SpanTermQuery(t2)))),
            spanQuery(SpanTermQuery(t1)),
        )
    }

    /** SpanNotQuery(A, [B C]) ⊆ SpanTermQuery(A) */
    @Test
    fun testSpanNotNearVersusSpanTerm() {
        val t1 = randomTerm()
        val t2 = randomTerm()
        val t3 = randomTerm()
        val near =
            spanQuery(
                SpanNearQuery(
                    arrayOf(
                        spanQuery(SpanTermQuery(t2)),
                        spanQuery(SpanTermQuery(t3)),
                    ),
                    10,
                    random().nextBoolean(),
                ),
            )
        assertSubsetOf(
            spanQuery(SpanNotQuery(spanQuery(SpanTermQuery(t1)), near)),
            spanQuery(SpanTermQuery(t1)),
        )
    }

    /** SpanNotQuery([A B], C) ⊆ SpanNearQuery([A B]) */
    @Test
    fun testSpanNotVersusSpanNear() {
        val t1 = randomTerm()
        val t2 = randomTerm()
        val t3 = randomTerm()
        val near =
            spanQuery(
                SpanNearQuery(
                    arrayOf(
                        spanQuery(SpanTermQuery(t1)),
                        spanQuery(SpanTermQuery(t2)),
                    ),
                    10,
                    random().nextBoolean(),
                ),
            )
        assertSubsetOf(spanQuery(SpanNotQuery(near, spanQuery(SpanTermQuery(t3)))), near)
    }

    /** SpanNotQuery([A B], [C D]) ⊆ SpanNearQuery([A B]) */
    @Test
    fun testSpanNotNearVersusSpanNear() {
        val t1 = randomTerm()
        val t2 = randomTerm()
        val t3 = randomTerm()
        val t4 = randomTerm()
        val near1 =
            spanQuery(
                SpanNearQuery(
                    arrayOf(
                        spanQuery(SpanTermQuery(t1)),
                        spanQuery(SpanTermQuery(t2)),
                    ),
                    10,
                    random().nextBoolean(),
                ),
            )
        val near2 =
            spanQuery(
                SpanNearQuery(
                    arrayOf(
                        spanQuery(SpanTermQuery(t3)),
                        spanQuery(SpanTermQuery(t4)),
                    ),
                    10,
                    random().nextBoolean(),
                ),
            )
        assertSubsetOf(spanQuery(SpanNotQuery(near1, near2)), near1)
    }

    /** SpanFirstQuery(A, 10) ⊆ SpanTermQuery(A) */
    @Test
    fun testSpanFirstVersusSpanTerm() {
        val t1 = randomTerm()
        assertSubsetOf(
            spanQuery(SpanFirstQuery(spanQuery(SpanTermQuery(t1)), 10)),
            spanQuery(SpanTermQuery(t1)),
        )
    }

    /** SpanNearQuery([A, B], 0, true) = "A B" */
    @Test
    fun testSpanNearVersusPhrase() {
        val t1 = randomTerm()
        val t2 = randomTerm()
        val subquery =
            arrayOf(
                spanQuery(SpanTermQuery(t1)),
                spanQuery(SpanTermQuery(t2)),
            )
        val q1 = spanQuery(SpanNearQuery(subquery, 0, true))
        val q2 = PhraseQuery(t1.field(), t1.bytes(), t2.bytes())
        if (t1 == t2) {
            assertSameSet(q1, q2)
        } else {
            assertSameScores(q1, q2)
        }
    }

    /** SpanNearQuery([A, B], ∞, false) = +A +B */
    @Test
    fun testSpanNearVersusBooleanAnd() {
        val t1 = randomTerm()
        val t2 = randomTerm()
        val subquery =
            arrayOf(
                spanQuery(SpanTermQuery(t1)),
                spanQuery(SpanTermQuery(t2)),
            )
        val q1 = spanQuery(SpanNearQuery(subquery, Int.MAX_VALUE, false))
        val q2 = BooleanQuery.Builder()
        q2.add(TermQuery(t1), BooleanClause.Occur.MUST)
        q2.add(TermQuery(t2), BooleanClause.Occur.MUST)
        assertSameSet(q1, q2.build())
    }

    /** SpanNearQuery([A B], 0, false) ⊆ SpanNearQuery([A B], 1, false) */
    @Test
    fun testSpanNearVersusSloppySpanNear() {
        val t1 = randomTerm()
        val t2 = randomTerm()
        val subquery =
            arrayOf(
                spanQuery(SpanTermQuery(t1)),
                spanQuery(SpanTermQuery(t2)),
            )
        val q1 = spanQuery(SpanNearQuery(subquery, 0, false))
        val q2 = spanQuery(SpanNearQuery(subquery, 1, false))
        assertSubsetOf(q1, q2)
    }

    /** SpanNearQuery([A B], 3, true) ⊆ SpanNearQuery([A B], 3, false) */
    @Test
    fun testSpanNearInOrderVersusOutOfOrder() {
        val t1 = randomTerm()
        val t2 = randomTerm()
        val subquery =
            arrayOf(
                spanQuery(SpanTermQuery(t1)),
                spanQuery(SpanTermQuery(t2)),
            )
        val q1 = spanQuery(SpanNearQuery(subquery, 3, true))
        val q2 = spanQuery(SpanNearQuery(subquery, 3, false))
        assertSubsetOf(q1, q2)
    }

    /** SpanNearQuery([A B], N, false) ⊆ SpanNearQuery([A B], N+1, false) */
    @Test
    fun testSpanNearIncreasingSloppiness() {
        val t1 = randomTerm()
        val t2 = randomTerm()
        val subquery =
            arrayOf(
                spanQuery(SpanTermQuery(t1)),
                spanQuery(SpanTermQuery(t2)),
            )
        for (i in 0..<10) {
            val q1 = spanQuery(SpanNearQuery(subquery, i, false))
            val q2 = spanQuery(SpanNearQuery(subquery, i + 1, false))
            assertSubsetOf(q1, q2)
        }
    }

    /** SpanNearQuery([A B C], N, false) ⊆ SpanNearQuery([A B C], N+1, false) */
    @Test
    fun testSpanNearIncreasingSloppiness3() {
        val t1 = randomTerm()
        val t2 = randomTerm()
        val t3 = randomTerm()
        val subquery =
            arrayOf(
                spanQuery(SpanTermQuery(t1)),
                spanQuery(SpanTermQuery(t2)),
                spanQuery(SpanTermQuery(t3)),
            )
        for (i in 0..<10) {
            val q1 = spanQuery(SpanNearQuery(subquery, i, false))
            val q2 = spanQuery(SpanNearQuery(subquery, i + 1, false))
            assertSubsetOf(q1, q2)
        }
    }

    /** SpanNearQuery([A B], N, true) ⊆ SpanNearQuery([A B], N+1, true) */
    @Test
    fun testSpanNearIncreasingOrderedSloppiness() {
        val t1 = randomTerm()
        val t2 = randomTerm()
        val subquery =
            arrayOf(
                spanQuery(SpanTermQuery(t1)),
                spanQuery(SpanTermQuery(t2)),
            )
        for (i in 0..<10) {
            val q1 = spanQuery(SpanNearQuery(subquery, i, false))
            val q2 = spanQuery(SpanNearQuery(subquery, i + 1, false))
            assertSubsetOf(q1, q2)
        }
    }

    /** SpanNearQuery([A B C], N, true) ⊆ SpanNearQuery([A B C], N+1, true) */
    @Test
    fun testSpanNearIncreasingOrderedSloppiness3() {
        val t1 = randomTerm()
        val t2 = randomTerm()
        val t3 = randomTerm()
        val subquery =
            arrayOf(
                spanQuery(SpanTermQuery(t1)),
                spanQuery(SpanTermQuery(t2)),
                spanQuery(SpanTermQuery(t3)),
            )
        for (i in 0..<10) {
            val q1 = spanQuery(SpanNearQuery(subquery, i, true))
            val q2 = spanQuery(SpanNearQuery(subquery, i + 1, true))
            assertSubsetOf(q1, q2)
        }
    }

    /** SpanPositionRangeQuery(A, M, N) ⊆ TermQuery(A) */
    @Test
    fun testSpanRangeTerm() {
        val t1 = randomTerm()
        for (i in 0..<5) {
            for (j in 0..<5) {
                val q1: Query = spanQuery(SpanPositionRangeQuery(spanQuery(SpanTermQuery(t1)), i, i + j))
                val q2: Query = TermQuery(t1)
                assertSubsetOf(q1, q2)
            }
        }
    }

    /** SpanPositionRangeQuery(A, M, N) ⊆ SpanFirstQuery(A, M, N+1) */
    @Test
    fun testSpanRangeTermIncreasingEnd() {
        val t1 = randomTerm()
        for (i in 0..<5) {
            for (j in 0..<5) {
                val q1: Query = spanQuery(SpanPositionRangeQuery(spanQuery(SpanTermQuery(t1)), i, i + j))
                val q2: Query = spanQuery(SpanPositionRangeQuery(spanQuery(SpanTermQuery(t1)), i, i + j + 1))
                assertSubsetOf(q1, q2)
            }
        }
    }

    /** SpanPositionRangeQuery(A, 0, ∞) = TermQuery(A) */
    @Test
    fun testSpanRangeTermEverything() {
        val t1 = randomTerm()
        val q1: Query = spanQuery(SpanPositionRangeQuery(spanQuery(SpanTermQuery(t1)), 0, Int.MAX_VALUE))
        val q2: Query = TermQuery(t1)
        assertSameSet(q1, q2)
    }

    /** SpanPositionRangeQuery([A B], M, N) ⊆ SpanNearQuery([A B]) */
    @Test
    fun testSpanRangeNear() {
        val t1 = randomTerm()
        val t2 = randomTerm()
        val subquery =
            arrayOf(
                spanQuery(SpanTermQuery(t1)),
                spanQuery(SpanTermQuery(t2)),
            )
        val nearQuery = spanQuery(SpanNearQuery(subquery, 10, true))
        for (i in 0..<5) {
            for (j in 0..<5) {
                val q1: Query = spanQuery(SpanPositionRangeQuery(nearQuery, i, i + j))
                val q2: Query = nearQuery
                assertSubsetOf(q1, q2)
            }
        }
    }

    /** SpanPositionRangeQuery([A B], M, N) ⊆ SpanFirstQuery([A B], M, N+1) */
    @Test
    fun testSpanRangeNearIncreasingEnd() {
        val t1 = randomTerm()
        val t2 = randomTerm()
        val subquery =
            arrayOf(
                spanQuery(SpanTermQuery(t1)),
                spanQuery(SpanTermQuery(t2)),
            )
        val nearQuery = spanQuery(SpanNearQuery(subquery, 10, true))
        for (i in 0..<5) {
            for (j in 0..<5) {
                val q1: Query = spanQuery(SpanPositionRangeQuery(nearQuery, i, i + j))
                val q2: Query = spanQuery(SpanPositionRangeQuery(nearQuery, i, i + j + 1))
                assertSubsetOf(q1, q2)
            }
        }
    }

    /** SpanPositionRangeQuery([A B], ∞) = SpanNearQuery([A B]) */
    @Test
    fun testSpanRangeNearEverything() {
        val t1 = randomTerm()
        val t2 = randomTerm()
        val subquery =
            arrayOf(
                spanQuery(SpanTermQuery(t1)),
                spanQuery(SpanTermQuery(t2)),
            )
        val nearQuery = spanQuery(SpanNearQuery(subquery, 10, true))
        val q1: Query = spanQuery(SpanPositionRangeQuery(nearQuery, 0, Int.MAX_VALUE))
        val q2: Query = nearQuery
        assertSameSet(q1, q2)
    }

    /** SpanFirstQuery(A, N) ⊆ TermQuery(A) */
    @Test
    fun testSpanFirstTerm() {
        val t1 = randomTerm()
        for (i in 0..<10) {
            val q1: Query = spanQuery(SpanFirstQuery(spanQuery(SpanTermQuery(t1)), i))
            val q2: Query = TermQuery(t1)
            assertSubsetOf(q1, q2)
        }
    }

    /** SpanFirstQuery(A, N) ⊆ SpanFirstQuery(A, N+1) */
    @Test
    fun testSpanFirstTermIncreasing() {
        val t1 = randomTerm()
        for (i in 0..<10) {
            val q1: Query = spanQuery(SpanFirstQuery(spanQuery(SpanTermQuery(t1)), i))
            val q2: Query = spanQuery(SpanFirstQuery(spanQuery(SpanTermQuery(t1)), i + 1))
            assertSubsetOf(q1, q2)
        }
    }

    /** SpanFirstQuery(A, ∞) = TermQuery(A) */
    @Test
    fun testSpanFirstTermEverything() {
        val t1 = randomTerm()
        val q1: Query = spanQuery(SpanFirstQuery(spanQuery(SpanTermQuery(t1)), Int.MAX_VALUE))
        val q2: Query = TermQuery(t1)
        assertSameSet(q1, q2)
    }

    /** SpanFirstQuery([A B], N) ⊆ SpanNearQuery([A B]) */
    @Test
    fun testSpanFirstNear() {
        val t1 = randomTerm()
        val t2 = randomTerm()
        val subquery =
            arrayOf(
                spanQuery(SpanTermQuery(t1)),
                spanQuery(SpanTermQuery(t2)),
            )
        val nearQuery = spanQuery(SpanNearQuery(subquery, 10, true))
        for (i in 0..<10) {
            val q1: Query = spanQuery(SpanFirstQuery(nearQuery, i))
            val q2: Query = nearQuery
            assertSubsetOf(q1, q2)
        }
    }

    /** SpanFirstQuery([A B], N) ⊆ SpanFirstQuery([A B], N+1) */
    @Test
    fun testSpanFirstNearIncreasing() {
        val t1 = randomTerm()
        val t2 = randomTerm()
        val subquery =
            arrayOf(
                spanQuery(SpanTermQuery(t1)),
                spanQuery(SpanTermQuery(t2)),
            )
        val nearQuery = spanQuery(SpanNearQuery(subquery, 10, true))
        for (i in 0..<10) {
            val q1: Query = spanQuery(SpanFirstQuery(nearQuery, i))
            val q2: Query = spanQuery(SpanFirstQuery(nearQuery, i + 1))
            assertSubsetOf(q1, q2)
        }
    }

    /** SpanFirstQuery([A B], ∞) = SpanNearQuery([A B]) */
    @Test
    fun testSpanFirstNearEverything() {
        val t1 = randomTerm()
        val t2 = randomTerm()
        val subquery =
            arrayOf(
                spanQuery(SpanTermQuery(t1)),
                spanQuery(SpanTermQuery(t2)),
            )
        val nearQuery = spanQuery(SpanNearQuery(subquery, 10, true))
        val q1: Query = spanQuery(SpanFirstQuery(nearQuery, Int.MAX_VALUE))
        val q2: Query = nearQuery
        assertSameSet(q1, q2)
    }

    /** SpanWithinQuery(A, B) ⊆ SpanNearQuery(A) */
    @Test
    fun testSpanWithinVsNear() {
        val t1 = randomTerm()
        val t2 = randomTerm()
        val subquery =
            arrayOf(
                spanQuery(SpanTermQuery(t1)),
                spanQuery(SpanTermQuery(t2)),
            )
        val nearQuery = spanQuery(SpanNearQuery(subquery, 10, true))

        val t3 = randomTerm()
        val termQuery = spanQuery(SpanTermQuery(t3))
        val q1 = spanQuery(SpanWithinQuery(nearQuery, termQuery))
        assertSubsetOf(q1, termQuery)
    }

    /** SpanWithinQuery(A, B) = SpanContainingQuery(A, B) */
    @Test
    fun testSpanWithinVsContaining() {
        val t1 = randomTerm()
        val t2 = randomTerm()
        val subquery =
            arrayOf(
                spanQuery(SpanTermQuery(t1)),
                spanQuery(SpanTermQuery(t2)),
            )
        val nearQuery = spanQuery(SpanNearQuery(subquery, 10, true))

        val t3 = randomTerm()
        val termQuery = spanQuery(SpanTermQuery(t3))
        val q1 = spanQuery(SpanWithinQuery(nearQuery, termQuery))
        val q2 = spanQuery(SpanContainingQuery(nearQuery, termQuery))
        assertSameSet(q1, q2)
    }
}
