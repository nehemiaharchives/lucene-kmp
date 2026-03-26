/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestQueryVisitor : LuceneTestCase() {
    companion object {
        private val query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("field1", "t1")), BooleanClause.Occur.MUST)
                .add(
                    BooleanQuery.Builder()
                        .add(TermQuery(Term("field1", "tm2")), BooleanClause.Occur.SHOULD)
                        .add(
                            BoostQuery(TermQuery(Term("field1", "tm3")), 2f),
                            BooleanClause.Occur.SHOULD
                        )
                        .build(),
                    BooleanClause.Occur.MUST
                )
                .add(
                    BoostQuery(
                        PhraseQuery.Builder()
                            .add(Term("field1", "term4"))
                            .add(Term("field1", "term5"))
                            .build(),
                        3f
                    ),
                    BooleanClause.Occur.MUST
                )
                .add(TermQuery(Term("field1", "term8")), BooleanClause.Occur.MUST_NOT)
                .add(PrefixQuery(Term("field1", "term9")), BooleanClause.Occur.SHOULD)
                .add(
                    BoostQuery(
                        BooleanQuery.Builder()
                            .add(
                                BoostQuery(TermQuery(Term("field2", "term10")), 3f),
                                BooleanClause.Occur.MUST
                            )
                            .build(),
                        2f
                    ),
                    BooleanClause.Occur.SHOULD
                )
                .build()
    }

    @Test
    fun testExtractTermsEquivalent() {
        val terms = mutableSetOf<Term>()
        val expected =
            mutableSetOf(
                Term("field1", "t1"),
                Term("field1", "tm2"),
                Term("field1", "tm3"),
                Term("field1", "term4"),
                Term("field1", "term5"),
                Term("field2", "term10")
            )
        query.visit(QueryVisitor.termCollector(terms))
        assertEquals(expected, terms)
    }

    fun extractAllTerms() {
        val allTerms = mutableSetOf<Term>()
        val visitor =
            object : QueryVisitor() {
                override fun consumeTerms(query: Query, vararg terms: Term) {
                    allTerms.addAll(terms)
                }

                override fun getSubVisitor(occur: BooleanClause.Occur, parent: Query): QueryVisitor {
                    return this
                }
            }
        val expected =
            mutableSetOf(
                Term("field1", "t1"),
                Term("field1", "tm2"),
                Term("field1", "tm3"),
                Term("field1", "term4"),
                Term("field1", "term5"),
                Term("field1", "term8"),
                Term("field2", "term10")
        )
        query.visit(visitor)
        assertEquals(expected, allTerms)
    }

    fun extractTermsFromField() {
        val actual = mutableSetOf<Term>()
        val expected = mutableSetOf(Term("field2", "term10"))
        query.visit(
            object : QueryVisitor() {
                override fun acceptField(field: String?): Boolean {
                    return "field2" == field
                }

                override fun consumeTerms(query: Query, vararg terms: Term) {
                    actual.addAll(terms)
                }
            }
        )
        assertEquals(expected, actual)
    }

    class BoostedTermExtractor(
        val boost: Float,
        val termsToBoosts: MutableMap<Term, Float>
    ) : QueryVisitor() {
        override fun consumeTerms(query: Query, vararg terms: Term) {
            for (term in terms) {
                termsToBoosts[term] = boost
            }
        }

        override fun getSubVisitor(occur: BooleanClause.Occur, parent: Query): QueryVisitor {
            if (parent is BoostQuery) {
                return BoostedTermExtractor(boost * parent.boost, termsToBoosts)
            }
            return super.getSubVisitor(occur, parent)
        }
    }

    @Test
    fun testExtractTermsAndBoosts() {
        val termsToBoosts = mutableMapOf<Term, Float>()
        query.visit(BoostedTermExtractor(1f, termsToBoosts))
        val expected = mutableMapOf<Term, Float>()
        expected[Term("field1", "t1")] = 1f
        expected[Term("field1", "tm2")] = 1f
        expected[Term("field1", "tm3")] = 2f
        expected[Term("field1", "term4")] = 3f
        expected[Term("field1", "term5")] = 3f
        expected[Term("field2", "term10")] = 6f
        assertEquals(expected, termsToBoosts)
    }

    @Test
    fun testLeafQueryTypeCounts() {
        val queryCounts = mutableMapOf<KClass<out Query>, Int>()
        query.visit(
            object : QueryVisitor() {
                private fun countQuery(q: Query) {
                    queryCounts[q::class] = (queryCounts[q::class] ?: 0) + 1
                }

                override fun consumeTerms(query: Query, vararg terms: Term) {
                    countQuery(query)
                }

                override fun visitLeaf(query: Query?) {
                    requireNotNull(query)
                    countQuery(query)
                }
            }
        )
        assertEquals(4, queryCounts[TermQuery::class])
        assertEquals(1, queryCounts[PhraseQuery::class])
    }

    abstract class QueryNode : QueryVisitor() {
        val children = mutableListOf<QueryNode>()

        abstract fun getWeight(): Int

        abstract fun collectTerms(terms: MutableSet<Term>)

        abstract fun nextTermSet(): Boolean

        override fun getSubVisitor(occur: BooleanClause.Occur, parent: Query): QueryVisitor {
            if (occur == BooleanClause.Occur.MUST || occur == BooleanClause.Occur.FILTER) {
                val n = ConjunctionNode()
                children.add(n)
                return n
            }
            if (occur == BooleanClause.Occur.MUST_NOT) {
                return QueryVisitor.EMPTY_VISITOR
            }
            if (parent is BooleanQuery) {
                if (parent.getClauses(BooleanClause.Occur.MUST).size > 0 ||
                    parent.getClauses(BooleanClause.Occur.FILTER).size > 0
                ) {
                    return QueryVisitor.EMPTY_VISITOR
                }
            }
            val n = DisjunctionNode()
            children.add(n)
            return n
        }
    }

    class TermNode(val term: Term) : QueryNode() {
        override fun getWeight(): Int {
            return term.text().length
        }

        override fun collectTerms(terms: MutableSet<Term>) {
            terms.add(term)
        }

        override fun nextTermSet(): Boolean {
            return false
        }

        override fun toString(): String {
            return "TERM($term)"
        }
    }

    class ConjunctionNode : QueryNode() {
        override fun getWeight(): Int {
            children.sortBy { it.getWeight() }
            return children[0].getWeight()
        }

        override fun collectTerms(terms: MutableSet<Term>) {
            children.sortBy { it.getWeight() }
            children[0].collectTerms(terms)
        }

        override fun nextTermSet(): Boolean {
            children.sortBy { it.getWeight() }
            if (children[0].nextTermSet()) {
                return true
            }
            if (children.size == 1) {
                return false
            }
            children.removeAt(0)
            return true
        }

        override fun consumeTerms(query: Query, vararg terms: Term) {
            for (term in terms) {
                children.add(TermNode(term))
            }
        }

        override fun toString(): String {
            return children.joinToString(separator = ",", prefix = "AND(", postfix = ")")
        }
    }

    class DisjunctionNode : QueryNode() {
        override fun getWeight(): Int {
            children.sortByDescending { it.getWeight() }
            return children[0].getWeight()
        }

        override fun collectTerms(terms: MutableSet<Term>) {
            for (child in children) {
                child.collectTerms(terms)
            }
        }

        override fun nextTermSet(): Boolean {
            var next = false
            for (child in children) {
                next = next or child.nextTermSet()
            }
            return next
        }

        override fun consumeTerms(query: Query, vararg terms: Term) {
            for (term in terms) {
                children.add(TermNode(term))
            }
        }

        override fun toString(): String {
            return children.joinToString(separator = ",", prefix = "OR(", postfix = ")")
        }
    }

    @Test
    fun testExtractMatchingTermSet() {
        val extractor = ConjunctionNode()
        query.visit(extractor)
        val minimumTermSet = mutableSetOf<Term>()
        extractor.collectTerms(minimumTermSet)

        val expected1 = mutableSetOf(Term("field1", "t1"))
        assertEquals(expected1, minimumTermSet)
        assertTrue(extractor.nextTermSet())
        val expected2 = mutableSetOf(Term("field1", "tm2"), Term("field1", "tm3"))
        minimumTermSet.clear()
        extractor.collectTerms(minimumTermSet)
        assertEquals(expected2, minimumTermSet)

        val bq =
            BooleanQuery.Builder()
                .add(
                    BooleanQuery.Builder()
                        .add(TermQuery(Term("f", "1")), BooleanClause.Occur.MUST)
                        .add(TermQuery(Term("f", "61")), BooleanClause.Occur.MUST)
                        .add(TermQuery(Term("f", "211")), BooleanClause.Occur.FILTER)
                        .add(TermQuery(Term("f", "5")), BooleanClause.Occur.SHOULD)
                        .build(),
                    BooleanClause.Occur.SHOULD
                )
                .add(PhraseQuery("f", "3333", "44444"), BooleanClause.Occur.SHOULD)
                .build()
        val ex2 = ConjunctionNode()
        bq.visit(ex2)
        val expected3 = mutableSetOf(Term("f", "1"), Term("f", "3333"))
        minimumTermSet.clear()
        ex2.collectTerms(minimumTermSet)
        assertEquals(expected3, minimumTermSet)
        ex2.getWeight() // force sort order
        assertEquals(
            "AND(AND(OR(AND(TERM(f:3333),TERM(f:44444)),AND(TERM(f:1),TERM(f:61),AND(TERM(f:211))))))",
            ex2.toString()
        )
    }
}
