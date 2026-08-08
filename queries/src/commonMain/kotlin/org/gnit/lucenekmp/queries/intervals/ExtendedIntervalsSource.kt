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
import org.gnit.lucenekmp.search.MatchesIterator
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.QueryVisitor

internal class ExtendedIntervalsSource(
    val source: IntervalsSource,
    private val before: Int,
    private val after: Int
) : IntervalsSource() {

    @Throws(IOException::class)
    override fun intervals(field: String, ctx: LeafReaderContext): IntervalIterator? {
        val `in` = source.intervals(field, ctx)
        if (`in` == null) {
            return null
        }
        return ExtendedIntervalIterator(`in`, before, after)
    }

    @Throws(IOException::class)
    override fun matches(
        field: String,
        ctx: LeafReaderContext,
        doc: Int
    ): IntervalMatchesIterator? {
        val `in` = source.matches(field, ctx, doc)
        if (`in` == null) {
            return null
        }

        val inNoOffsets =
            object : IntervalMatchesIterator {
                val delegate: IntervalMatchesIterator = `in`

                override fun gaps(): Int {
                    return delegate.gaps()
                }

                override fun width(): Int {
                    return delegate.width()
                }

                @Throws(IOException::class)
                override fun next(): Boolean {
                    return delegate.next()
                }

                override fun startPosition(): Int {
                    return delegate.startPosition()
                }

                override fun endPosition(): Int {
                    return delegate.endPosition()
                }

                @Throws(IOException::class)
                override fun startOffset(): Int {
                    // We could return this:
                    // before > 0 ? -1 : in.startOffset();
                    // but keep it consistent for start/end offset:
                    return -1
                }

                @Throws(IOException::class)
                override fun endOffset(): Int {
                    // We could return this:
                    // after > 0 ? -1 : in.startOffset();
                    // but keep it consistent for start/end offset:
                    return -1
                }

                override val subMatches: MatchesIterator?
                    get() = delegate.subMatches

                override val query: Query?
                    get() = delegate.query
            }

        val wrapped =
            ExtendedIntervalIterator(
                IntervalMatches.wrapMatches(inNoOffsets, doc),
                before,
                after
            )
        return IntervalMatches.asMatches(wrapped, inNoOffsets, doc)
    }

    override fun visit(field: String, visitor: QueryVisitor) {
        source.visit(field, visitor)
    }

    override fun minExtent(): Int {
        val minExtent = before + source.minExtent() + after
        if (minExtent < 0) {
            return Int.MAX_VALUE
        }
        return minExtent
    }

    override fun pullUpDisjunctions(): Collection<IntervalsSource> {
        val inner = source.pullUpDisjunctions()
        if (inner.size == 0) {
            return setOf(this)
        }
        return inner.map { s -> ExtendedIntervalsSource(s, before, after) }.toSet()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ExtendedIntervalsSource) return false
        return before == other.before && after == other.after && source == other.source
    }

    override fun hashCode(): Int {
        var result = 1
        result = 31 * result + source.hashCode()
        result = 31 * result + before
        result = 31 * result + after
        return result
    }

    override fun toString(): String {
        return "EXTEND($source,$before,$after)"
    }
}
