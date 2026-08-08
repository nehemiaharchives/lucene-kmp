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
import org.gnit.lucenekmp.search.QueryVisitor

/**
 * An IntervalsSource that filters the intervals from another IntervalsSource
 *
 * Create a new FilteredIntervalsSource
 *
 * @param name the name of the filter
 * @param `in` the source to filter
 */
abstract class FilteredIntervalsSource(
    private val name: String,
    protected val `in`: IntervalsSource
) : IntervalsSource() {

    companion object {
        fun maxGaps(`in`: IntervalsSource, maxGaps: Int): IntervalsSource {
            return Intervals.or(
                `in`.pullUpDisjunctions().map { s -> MaxGaps(s, maxGaps) }
            )
        }

        private class MaxGaps(
            `in`: IntervalsSource,
            private val maxGaps: Int
        ) : FilteredIntervalsSource("MAXGAPS/$maxGaps", `in`) {

            override fun accept(it: IntervalIterator): Boolean {
                return it.gaps() <= maxGaps
            }
        }

        fun maxWidth(`in`: IntervalsSource, maxWidth: Int): IntervalsSource {
            return MaxWidth(`in`, maxWidth)
        }

        private class MaxWidth(
            `in`: IntervalsSource,
            private val maxWidth: Int
        ) : FilteredIntervalsSource("MAXWIDTH/$maxWidth", `in`) {

            override fun accept(it: IntervalIterator): Boolean {
                return (it.end() - it.start()) + 1 <= maxWidth
            }

            override fun pullUpDisjunctions(): Collection<IntervalsSource> {
                return Disjunctions.pullUp(`in`) { s -> MaxWidth(s, maxWidth) }
            }
        }
    }

    /**
     * @return `false` if the current interval should be filtered out
     */
    protected abstract fun accept(it: IntervalIterator): Boolean

    @Throws(IOException::class)
    override fun intervals(field: String, ctx: LeafReaderContext): IntervalIterator? {
        val i = `in`.intervals(field, ctx)
        if (i == null) {
            return null
        }
        return object : IntervalFilter(i) {
            override fun accept(): Boolean {
                return this@FilteredIntervalsSource.accept(i)
            }
        }
    }

    @Throws(IOException::class)
    override fun matches(
        field: String,
        ctx: LeafReaderContext,
        doc: Int
    ): IntervalMatchesIterator? {
        val mi = `in`.matches(field, ctx, doc)
        if (mi == null) {
            return null
        }
        val wrappedInput = IntervalMatches.wrapMatches(mi, doc)
        val filtered =
            object : IntervalFilter(wrappedInput) {
                override fun accept(): Boolean {
                    return this@FilteredIntervalsSource.accept(wrappedInput)
                }
            }
        return IntervalMatches.asMatches(filtered, mi, doc)
    }

    override fun minExtent(): Int {
        return `in`.minExtent()
    }

    override fun pullUpDisjunctions(): Collection<IntervalsSource> {
        return listOf(this)
    }

    override fun visit(field: String, visitor: QueryVisitor) {
        `in`.visit(field, visitor)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FilteredIntervalsSource) return false
        return name == other.name && `in` == other.`in`
    }

    override fun hashCode(): Int {
        var result = 1
        result = 31 * result + name.hashCode()
        result = 31 * result + `in`.hashCode()
        return result
    }

    override fun toString(): String {
        return "$name($`in`)"
    }
}
