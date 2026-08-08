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

package org.gnit.lucenekmp.queries.intervals

import okio.IOException
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.MatchesIterator
import org.gnit.lucenekmp.search.MatchesUtils
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.QueryVisitor
import org.gnit.lucenekmp.util.automaton.CompiledAutomaton

internal class MultiTermIntervalsSource(
    private val automaton: CompiledAutomaton,
    private val maxExpansions: Int,
    private val pattern: String
) : IntervalsSource() {

    init {
        if (maxExpansions > IndexSearcher.maxClauseCount) {
            throw IllegalArgumentException(
                "maxExpansions [" +
                    maxExpansions +
                    "] cannot be greater than BooleanQuery.getMaxClauseCount [" +
                    IndexSearcher.maxClauseCount +
                    "]"
            )
        }
    }

    @Throws(IOException::class)
    override fun intervals(field: String, ctx: LeafReaderContext): IntervalIterator? {
        val terms = ctx.reader().terms(field)
        if (terms == null) {
            return null
        }
        val subSources = mutableListOf<IntervalIterator>()
        val te = automaton.getTermsEnum(terms)
        var count = 0
        while (true) {
            val term = te.next() ?: break
            subSources.add(TermIntervalsSource.intervals(term, te))
            count++
            if (count > maxExpansions) {
                throw IllegalStateException(
                    "Automaton [" +
                        this.pattern +
                        "] expanded to too many terms (limit " +
                        maxExpansions +
                        ")"
                )
            }
        }
        if (subSources.size == 0) {
            return null
        }
        return DisjunctionIntervalsSource.DisjunctionIntervalIterator(subSources)
    }

    @Throws(IOException::class)
    override fun matches(
        field: String,
        ctx: LeafReaderContext,
        doc: Int
    ): IntervalMatchesIterator? {
        val terms = ctx.reader().terms(field)
        if (terms == null) {
            return null
        }
        val subMatches = mutableListOf<MatchesIterator>()
        val te = automaton.getTermsEnum(terms)
        var count = 0
        while (true) {
            val term = te.next() ?: break
            val match = TermIntervalsSource.matches(te, doc, field)
            if (match != null) {
                subMatches.add(match)
                val previousCount = count
                count++
                if (previousCount > maxExpansions) {
                    throw IllegalStateException(
                        "Automaton $term expanded to too many terms (limit $maxExpansions)"
                    )
                }
            }
        }
        val mi = MatchesUtils.disjunction(subMatches)
        if (mi == null) {
            return null
        }
        return object : IntervalMatchesIterator {
            override fun gaps(): Int {
                return 0
            }

            override fun width(): Int {
                return 1
            }

            @Throws(IOException::class)
            override fun next(): Boolean {
                return mi.next()
            }

            override fun startPosition(): Int {
                return mi.startPosition()
            }

            override fun endPosition(): Int {
                return mi.endPosition()
            }

            @Throws(IOException::class)
            override fun startOffset(): Int {
                return mi.startOffset()
            }

            @Throws(IOException::class)
            override fun endOffset(): Int {
                return mi.endOffset()
            }

            override val subMatches: MatchesIterator?
                get() = mi.subMatches

            override val query: Query?
                get() = mi.query
        }
    }

    override fun visit(field: String, visitor: QueryVisitor) {
        automaton.visit(visitor, IntervalQuery(field, this), field)
    }

    override fun minExtent(): Int {
        return 1
    }

    override fun pullUpDisjunctions(): Collection<IntervalsSource> {
        return setOf(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MultiTermIntervalsSource) return false
        return maxExpansions == other.maxExpansions &&
            automaton == other.automaton &&
            pattern == other.pattern
    }

    override fun hashCode(): Int {
        var result = 1
        result = 31 * result + automaton.hashCode()
        result = 31 * result + maxExpansions
        result = 31 * result + pattern.hashCode()
        return result
    }

    override fun toString(): String {
        return "MultiTerm($pattern)"
    }
}
