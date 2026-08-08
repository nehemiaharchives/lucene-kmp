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
import org.gnit.lucenekmp.search.BooleanClause
import org.gnit.lucenekmp.search.QueryVisitor
import kotlin.math.max

/**
 * Tracks a reference intervals source, and produces a pseudo-interval that appears either one
 * position before or one position after each interval from the reference
 */
internal class OffsetIntervalsSource(
    private val `in`: IntervalsSource,
    private val before: Boolean
) : IntervalsSource() {

    @Throws(IOException::class)
    override fun intervals(field: String, ctx: LeafReaderContext): IntervalIterator? {
        val it = `in`.intervals(field, ctx)
        if (it == null) {
            return null
        }
        return offset(it)
    }

    private fun offset(it: IntervalIterator): IntervalIterator {
        if (before) {
            return object : OffsetIntervalIterator(it) {
                override fun start(): Int {
                    val pos = `in`.start()
                    if (pos == -1) {
                        return -1
                    }
                    if (pos == NO_MORE_INTERVALS) {
                        return NO_MORE_INTERVALS
                    }
                    return max(0, pos - 1)
                }
            }
        } else {
            return object : OffsetIntervalIterator(it) {
                override fun start(): Int {
                    val pos = `in`.end() + 1
                    if (pos == 0) {
                        return -1
                    }
                    if (pos < 0) { // overflow
                        return Int.MAX_VALUE
                    }
                    if (pos == Int.MAX_VALUE) {
                        return Int.MAX_VALUE - 1
                    }
                    return pos
                }
            }
        }
    }

    private abstract class OffsetIntervalIterator(
        val `in`: IntervalIterator
    ) : IntervalIterator() {

        override fun end(): Int {
            return start()
        }

        override fun gaps(): Int {
            return 0
        }

        @Throws(IOException::class)
        override fun nextInterval(): Int {
            `in`.nextInterval()
            return start()
        }

        override fun matchCost(): Float {
            return `in`.matchCost()
        }

        override fun docID(): Int {
            return `in`.docID()
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            return `in`.nextDoc()
        }

        @Throws(IOException::class)
        override fun advance(target: Int): Int {
            return `in`.advance(target)
        }

        override fun cost(): Long {
            return `in`.cost()
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
        return IntervalMatches.asMatches(
            offset(IntervalMatches.wrapMatches(mi, doc)),
            mi,
            doc
        )
    }

    override fun visit(field: String, visitor: QueryVisitor) {
        `in`.visit(
            field,
            visitor.getSubVisitor(BooleanClause.Occur.MUST, IntervalQuery(field, this))
        )
    }

    override fun minExtent(): Int {
        return 1
    }

    override fun pullUpDisjunctions(): Collection<IntervalsSource> {
        return setOf(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OffsetIntervalsSource) return false
        return before == other.before && `in` == other.`in`
    }

    override fun hashCode(): Int {
        var result = 1
        result = 31 * result + `in`.hashCode()
        result = 31 * result + before.hashCode()
        return result
    }

    override fun toString(): String {
        if (before) {
            return "PRECEDING($`in`)"
        }
        return "FOLLOWING($`in`)"
    }
}
