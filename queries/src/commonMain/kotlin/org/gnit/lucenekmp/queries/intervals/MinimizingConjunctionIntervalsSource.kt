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
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.BooleanClause
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.QueryVisitor

internal abstract class MinimizingConjunctionIntervalsSource(
    protected val subSources: List<IntervalsSource>
) : IntervalsSource() {

    init {
        assert(subSources.size > 1)
    }

    protected abstract fun combine(
        iterators: List<IntervalIterator>,
        onMatch: MatchCallback
    ): IntervalIterator

    @Throws(IOException::class)
    override fun intervals(field: String, ctx: LeafReaderContext): IntervalIterator? {
        val subIntervals = mutableListOf<IntervalIterator>()
        for (source in subSources) {
            val it = source.intervals(field, ctx)
            if (it == null) {
                return null
            }
            subIntervals.add(it)
        }
        return combine(subIntervals, MatchCallback.NO_OP)
    }

    @Throws(IOException::class)
    override fun matches(
        field: String,
        ctx: LeafReaderContext,
        doc: Int
    ): IntervalMatchesIterator? {
        val subs = mutableListOf<CachingMatchesIterator>()
        for (source in subSources) {
            val mi = source.matches(field, ctx, doc)
            if (mi == null) {
                return null
            }
            subs.add(CachingMatchesIterator(mi))
        }
        val it =
            combine(
                subs.map { m -> IntervalMatches.wrapMatches(m, doc) },
                cacheIterators(subs)
            )
        if (it.advance(doc) != doc) {
            return null
        }
        if (it.nextInterval() == IntervalIterator.NO_MORE_INTERVALS) {
            return null
        }
        return ConjunctionMatchesIterator(it, subs)
    }

    override fun visit(field: String, visitor: QueryVisitor) {
        val parent: Query = IntervalQuery(field, this)
        val v = visitor.getSubVisitor(BooleanClause.Occur.MUST, parent)
        for (source in subSources) {
            source.visit(field, v)
        }
    }

    fun interface MatchCallback {

        /** Called when the parent iterator has found a match */
        @Throws(IOException::class)
        fun onMatch()

        companion object {
            val NO_OP = MatchCallback {}
        }
    }

    companion object {
        fun cacheIterators(its: Collection<CachingMatchesIterator>): MatchCallback {
            return MatchCallback {
                for (it in its) {
                    it.cache()
                }
            }
        }
    }
}
