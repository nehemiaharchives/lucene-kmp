package org.gnit.lucenekmp.queries.spans

import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.BooleanClause
import org.gnit.lucenekmp.search.BooleanQuery
import org.gnit.lucenekmp.search.BoostQuery
import org.gnit.lucenekmp.search.PhraseQuery
import org.gnit.lucenekmp.search.PrefixQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.QueryVisitor
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals

class TestSpanQueryVisitor : LuceneTestCase() {
    companion object {
        private val query: Query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("field1", "t1")), BooleanClause.Occur.MUST)
                .add(
                    BooleanQuery.Builder()
                        .add(TermQuery(Term("field1", "tm2")), BooleanClause.Occur.SHOULD)
                        .add(
                            BoostQuery(TermQuery(Term("field1", "tm3")), 2f),
                            BooleanClause.Occur.SHOULD,
                        )
                        .build(),
                    BooleanClause.Occur.MUST,
                )
                .add(
                    BoostQuery(
                        PhraseQuery.Builder()
                            .add(Term("field1", "term4"))
                            .add(Term("field1", "term5"))
                            .build(),
                        3f,
                    ),
                    BooleanClause.Occur.MUST,
                )
                .add(
                    SpanNearQuery(
                        arrayOf(
                            SpanTermQuery(Term("field1", "term6")),
                            SpanTermQuery(Term("field1", "term7")),
                        ),
                        2,
                        true,
                    ),
                    BooleanClause.Occur.MUST,
                )
                .add(TermQuery(Term("field1", "term8")), BooleanClause.Occur.MUST_NOT)
                .add(PrefixQuery(Term("field1", "term9")), BooleanClause.Occur.SHOULD)
                .add(
                    BoostQuery(
                        BooleanQuery.Builder()
                            .add(
                                BoostQuery(TermQuery(Term("field2", "term10")), 3f),
                                BooleanClause.Occur.MUST,
                            )
                            .build(),
                        2f,
                    ),
                    BooleanClause.Occur.SHOULD,
                )
                .build()
    }

    @Test
    fun testExtractTermsEquivalent() {
        val terms: MutableSet<Term> = mutableSetOf()
        val expected: Set<Term?> = hashSetOf(
            Term("field1", "t1"),
            Term("field1", "tm2"),
            Term("field1", "tm3"),
            Term("field1", "term4"),
            Term("field1", "term5"),
            Term("field1", "term6"),
            Term("field1", "term7"),
            Term("field2", "term10"),
        )
        query.visit(QueryVisitor.termCollector(terms))
        assertEquals(expected, terms)
    }
}
