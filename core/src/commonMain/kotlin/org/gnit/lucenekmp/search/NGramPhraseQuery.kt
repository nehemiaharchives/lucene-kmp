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

/**
 * This is a [PhraseQuery] which is optimized for n-gram phrase query. For example, when you
 * query "ABCD" on a 2-gram field, you may want to use NGramPhraseQuery rather than [PhraseQuery],
 * because NGramPhraseQuery will [Query.rewrite] the query to "AB/0 CD/2", while [PhraseQuery]
 * will query "AB/0 BC/1 CD/2" (where term/position).
 */
class NGramPhraseQuery(
    /** Return the n in n-gram */
    val n: Int,
    private val phraseQuery: PhraseQuery
) : Query() {

    init {
        requireNotNull(phraseQuery)
    }

    override fun rewrite(indexSearcher: IndexSearcher): Query {
        val terms = phraseQuery.terms
        val positions = phraseQuery.positions

        var isOptimizable =
            phraseQuery.slop == 0 &&
                n >= 2 && // non-overlap n-gram cannot be optimized
                terms.size >= 3 // short ones can't be optimized

        if (isOptimizable) {
            for (i in 1 until positions.size) {
                if (positions[i] != positions[i - 1] + 1) {
                    isOptimizable = false
                    break
                }
            }
        }

        if (isOptimizable == false) {
            return phraseQuery.rewrite(indexSearcher)
        }

        val builder = PhraseQuery.Builder()
        for (i in terms.indices) {
            if (i % n == 0 || i == terms.size - 1) {
                builder.add(terms[i], i)
            }
        }
        return builder.build()
    }

    override fun visit(visitor: QueryVisitor) {
        phraseQuery.visit(visitor.getSubVisitor(BooleanClause.Occur.MUST, this))
    }

    override fun equals(other: Any?): Boolean {
        return sameClassAs(other) && equalsTo(other as NGramPhraseQuery)
    }

    private fun equalsTo(other: NGramPhraseQuery): Boolean {
        return n == other.n && phraseQuery == other.phraseQuery
    }

    override fun hashCode(): Int {
        var h = classHash()
        h = 31 * h + phraseQuery.hashCode()
        h = 31 * h + n
        return h
    }

    /** Return the list of terms. */
    val terms: Array<Term>
        get() = phraseQuery.terms

    /** Return the list of relative positions that each term should appear at. */
    val positions: IntArray
        get() = phraseQuery.positions

    override fun toString(field: String?): String {
        return phraseQuery.toString(field)
    }
}
