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
import org.gnit.lucenekmp.search.MatchesIterator
import org.gnit.lucenekmp.search.MatchesUtils
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.QueryVisitor

/**
 * Generates an iterator that spans repeating instances of a sub-iterator, avoiding minimization.
 * This is useful for repeated terms within an unordered interval, for example, ensuring that
 * multiple iterators do not match on a single term.
 *
 * <p>The generated iterators have a specialized [IntervalIterator.width] implementation
 * that sums up the widths of the individual sub-iterators, rather than just returning the full span
 * of the iterator.
 */
internal class RepeatingIntervalsSource private constructor(
    val `in`: IntervalsSource,
    val childCount: Int
) : IntervalsSource() {

    companion object {
        fun build(`in`: IntervalsSource, childCount: Int): IntervalsSource {
            if (childCount == 1) {
                return `in`
            }
            assert(childCount > 0)
            return RepeatingIntervalsSource(`in`, childCount)
        }
    }

    var name: String? = null

    @Throws(IOException::class)
    override fun intervals(field: String, ctx: LeafReaderContext): IntervalIterator? {
        val it = `in`.intervals(field, ctx)
        if (it == null) {
            return null
        }
        return DuplicateIntervalIterator(it, childCount)
    }

    @Throws(IOException::class)
    override fun matches(
        field: String,
        ctx: LeafReaderContext,
        doc: Int
    ): IntervalMatchesIterator? {
        val subs = mutableListOf<IntervalMatchesIterator>()
        for (i in 0 until childCount) {
            val mi = `in`.matches(field, ctx, doc)
            if (mi == null) {
                return null
            }
            subs.add(mi)
        }
        return DuplicateMatchesIterator.build(subs)
    }

    override fun visit(field: String, visitor: QueryVisitor) {
        `in`.visit(field, visitor)
    }

    override fun minExtent(): Int {
        return `in`.minExtent()
    }

    override fun pullUpDisjunctions(): Collection<IntervalsSource> {
        return setOf(this)
    }

    override fun hashCode(): Int {
        var result = 1
        result = 31 * result + `in`.hashCode()
        result = 31 * result + childCount
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (other !is RepeatingIntervalsSource) return false
        return this.`in` == other.`in` && this.childCount == other.childCount
    }

    override fun toString(): String {
        val s = `in`.toString()
        val out = StringBuilder(s)
        for (i in 1 until childCount) {
            out.append(",").append(s)
        }
        if (name != null) {
            return name + "(" + out.toString() + ")"
        }
        return out.toString()
    }

    private class DuplicateIntervalIterator(
        private val `in`: IntervalIterator,
        copies: Int
    ) : IntervalIterator() {

        val cacheLength: Int = copies
        val cache: IntArray = IntArray(this.cacheLength * 2)
        var cacheBase: Int = 0
        var started = false
        var exhausted = false

        override fun start(): Int {
            return if (exhausted) {
                NO_MORE_INTERVALS
            } else {
                cache[(cacheBase % cacheLength) * 2]
            }
        }

        override fun end(): Int {
            return if (exhausted) {
                NO_MORE_INTERVALS
            } else {
                cache[(((cacheBase + cacheLength - 1) % cacheLength) * 2) + 1]
            }
        }

        override fun width(): Int {
            var width = 0
            for (i in 0 until cacheLength) {
                val pos = (cacheBase + i) % cacheLength
                width += cache[pos * 2] - cache[pos * 2 + 1] + 1
            }
            return width
        }

        override fun gaps(): Int {
            return super.width() - width()
        }

        @Throws(IOException::class)
        override fun nextInterval(): Int {
            if (exhausted) {
                return NO_MORE_INTERVALS
            }
            if (started == false) {
                for (i in 0 until cacheLength) {
                    if (cacheNextInterval(i) == NO_MORE_INTERVALS) {
                        return NO_MORE_INTERVALS
                    }
                }
                cacheBase = 0
                started = true
                return start()
            } else {
                val insert = (cacheBase + cacheLength) % cacheLength
                cacheBase = (cacheBase + 1) % cacheLength
                return cacheNextInterval(insert)
            }
        }

        @Throws(IOException::class)
        private fun cacheNextInterval(linePos: Int): Int {
            if (`in`.nextInterval() == NO_MORE_INTERVALS) {
                exhausted = true
                return NO_MORE_INTERVALS
            }
            cache[linePos * 2] = `in`.start()
            cache[linePos * 2 + 1] = `in`.end()
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
            exhausted = false
            started = exhausted
            cache.fill(-1)
            return `in`.nextDoc()
        }

        @Throws(IOException::class)
        override fun advance(target: Int): Int {
            exhausted = false
            started = exhausted
            cache.fill(-1)
            return `in`.advance(target)
        }

        override fun cost(): Long {
            return `in`.cost()
        }
    }

    private class DuplicateMatchesIterator private constructor(
        var subs: List<IntervalMatchesIterator>
    ) : IntervalMatchesIterator {

        var cached = false

        companion object {
            @Throws(IOException::class)
            fun build(subs: List<IntervalMatchesIterator>): IntervalMatchesIterator? {
                var count = subs.size
                while (count > 0) {
                    for (i in 0 until count) {
                        if (subs[count - 1].next() == false) {
                            return null
                        }
                    }
                    count--
                }
                return DuplicateMatchesIterator(subs)
            }
        }

        @Throws(IOException::class)
        override fun next(): Boolean {
            if (cached == false) {
                cached = true
                return true
            }
            if (subs[subs.size - 1].next() == false) {
                return false
            }
            for (i in 0 until subs.size - 1) {
                subs[i].next()
            }
            return true
        }

        override fun startPosition(): Int {
            return subs[0].startPosition()
        }

        override fun endPosition(): Int {
            return subs[subs.size - 1].endPosition()
        }

        @Throws(IOException::class)
        override fun startOffset(): Int {
            return subs[0].startOffset()
        }

        @Throws(IOException::class)
        override fun endOffset(): Int {
            return subs[subs.size - 1].endOffset()
        }

        override val subMatches: MatchesIterator?
            get() {
                val subMatches = mutableListOf<MatchesIterator>()
                for (mi in subs) {
                    var sub = mi.subMatches
                    if (sub == null) {
                        sub = ConjunctionMatchesIterator.SingletonMatchesIterator(mi)
                    }
                    subMatches.add(sub)
                }
                return MatchesUtils.disjunction(subMatches)
            }

        override val query: Query
            get() = throw UnsupportedOperationException()

        override fun gaps(): Int {
            var width = endPosition() - startPosition() + 1
            for (mi in subs) {
                width -= mi.endPosition() - mi.startPosition() + 1
            }
            return width
        }

        override fun width(): Int {
            var width = 0
            for (mi in subs) {
                width += mi.endPosition() - mi.startPosition() + 1
            }
            return width
        }
    }
}
