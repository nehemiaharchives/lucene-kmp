package org.gnit.lucenekmp.queries.spans

import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.tests.search.MatchesTestBase
import kotlin.test.Test

class TestSpanMatches : MatchesTestBase() {
    override fun getDocuments(): Array<String> {
        return arrayOf(
            "w1 w2 w3 w4 w5",
            "w1 w3 w2 w3 zz",
            "w1 xx w2 yy w4",
            "w1 w2 w1 w4 w2 w3",
            "a phrase sentence with many phrase sentence iterations of a phrase sentence",
            "nothing matches this document",
        )
    }

    @Test
    fun testSpanQuery() {
        val subq =
            SpanNearQuery.newOrderedNearQuery(FIELD_WITH_OFFSETS)
                .addClause(SpanTermQuery(Term(FIELD_WITH_OFFSETS, "with")))
                .addClause(SpanTermQuery(Term(FIELD_WITH_OFFSETS, "many")))
                .build()
        val q: Query =
            SpanNearQuery.newOrderedNearQuery(FIELD_WITH_OFFSETS)
                .addClause(SpanTermQuery(Term(FIELD_WITH_OFFSETS, "sentence")))
                .addClause(
                    SpanOrQuery(
                        subq,
                        SpanTermQuery(Term(FIELD_WITH_OFFSETS, "iterations")),
                    ),
                ).build()
        checkMatches(
            q,
            FIELD_WITH_OFFSETS,
            arrayOf(
                intArrayOf(0),
                intArrayOf(1),
                intArrayOf(2),
                intArrayOf(3),
                intArrayOf(4, 2, 4, 9, 27, 6, 7, 35, 54),
            ),
        )
        checkLabelCount(q, FIELD_WITH_OFFSETS, intArrayOf(0, 0, 0, 0, 1))
        checkTermMatches(
            q,
            FIELD_WITH_OFFSETS,
            arrayOf(
                emptyArray(),
                emptyArray(),
                emptyArray(),
                emptyArray(),
                arrayOf(
                    arrayOf(TermMatch(2, 9, 17), TermMatch(3, 18, 22), TermMatch(4, 23, 27)),
                    arrayOf(TermMatch(6, 35, 43), TermMatch(7, 44, 54)),
                ),
            ),
        )
    }
}
