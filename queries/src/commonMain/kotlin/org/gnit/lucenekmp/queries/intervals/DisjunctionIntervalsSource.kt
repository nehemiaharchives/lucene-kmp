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
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.MatchesIterator
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.QueryVisitor
import org.gnit.lucenekmp.util.PriorityQueue
import kotlin.math.min

internal class DisjunctionIntervalsSource private constructor(
    subSources: Collection<IntervalsSource>,
    val pullUpDisjunctions: Boolean
) : IntervalsSource() {

    val subSources: Collection<IntervalsSource> = simplify(subSources)

    companion object {
        fun create(
            subSources: Collection<IntervalsSource>,
            pullUpDisjunctions: Boolean
        ): IntervalsSource {
            val subSources = simplify(subSources)
            if (subSources.size == 1) {
                return subSources.iterator().next()
            }
            return DisjunctionIntervalsSource(subSources, pullUpDisjunctions)
        }

        private fun simplify(sources: Collection<IntervalsSource>): Collection<IntervalsSource> {
            val simplified = HashSet<IntervalsSource>()
            for (source in sources) {
                if (source is DisjunctionIntervalsSource) {
                    simplified.addAll(source.pullUpDisjunctions())
                } else {
                    simplified.add(source)
                }
            }
            return simplified
        }
    }

    @Throws(IOException::class)
    override fun intervals(field: String, ctx: LeafReaderContext): IntervalIterator? {
        val subIterators = mutableListOf<IntervalIterator>()
        for (subSource in subSources) {
            val it = subSource.intervals(field, ctx)
            if (it != null) {
                subIterators.add(it)
            }
        }
        if (subIterators.isEmpty()) {
            return null
        }
        return DisjunctionIntervalIterator(subIterators)
    }

    @Throws(IOException::class)
    override fun matches(
        field: String,
        ctx: LeafReaderContext,
        doc: Int
    ): IntervalMatchesIterator? {
        val subMatches = mutableListOf<IntervalMatchesIterator>()
        for (subSource in subSources) {
            val mi = subSource.matches(field, ctx, doc)
            if (mi != null) {
                subMatches.add(mi)
            }
        }
        if (subMatches.isEmpty()) {
            return null
        }
        val it =
            DisjunctionIntervalIterator(
                subMatches.map { m -> IntervalMatches.wrapMatches(m, doc) }
            )
        if (it.advance(doc) != doc) {
            return null
        }
        return DisjunctionMatchesIterator(it, subMatches)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DisjunctionIntervalsSource) return false
        return subSources == other.subSources
    }

    override fun hashCode(): Int {
        return 31 + subSources.hashCode()
    }

    override fun toString(): String {
        return subSources.map { it.toString() }.sorted().joinToString(",", "or(", ")")
    }

    override fun visit(field: String, visitor: QueryVisitor) {
        val parent: Query = IntervalQuery(field, this)
        val v = visitor.getSubVisitor(BooleanClause.Occur.SHOULD, parent)
        for (source in subSources) {
            source.visit(field, v)
        }
    }

    override fun minExtent(): Int {
        var minExtent = Int.MAX_VALUE
        for (subSource in subSources) {
            minExtent = min(minExtent, subSource.minExtent())
        }
        return minExtent
    }

    override fun pullUpDisjunctions(): Collection<IntervalsSource> {
        if (pullUpDisjunctions) {
            return subSources
        }
        return listOf(this)
    }

    internal class DisjunctionIntervalIterator(
        val iterators: List<IntervalIterator>
    ) : IntervalIterator() {

        val approximation: DocIdSetIterator
        val intervalQueue: PriorityQueue<IntervalIterator>
        val disiQueue: DisiPriorityQueue
        val matchCost: Float

        var current: IntervalIterator = EMPTY

        init {
            this.disiQueue = DisiPriorityQueue(iterators.size)
            for (it in iterators) {
                disiQueue.add(DisiWrapper(it))
            }
            this.approximation = DisjunctionDISIApproximation(disiQueue)
            this.intervalQueue =
                object : PriorityQueue<IntervalIterator>(iterators.size) {
                    override fun lessThan(a: IntervalIterator, b: IntervalIterator): Boolean {
                        return a.end() < b.end() ||
                            (a.end() == b.end() && a.start() >= b.start())
                    }
                }
            var costsum = 0f
            for (it in iterators) {
                costsum += it.cost()
            }
            this.matchCost = costsum
        }

        override fun matchCost(): Float {
            return matchCost
        }

        override fun start(): Int {
            return current.start()
        }

        override fun end(): Int {
            return current.end()
        }

        override fun gaps(): Int {
            return current.gaps()
        }

        @Throws(IOException::class)
        private fun reset() {
            intervalQueue.clear()
            var dw: DisiWrapper? = disiQueue.topList()
            while (dw != null) {
                dw.intervals.nextInterval()
                intervalQueue.add(dw.intervals)
                dw = dw.next
            }
            current = EMPTY
        }

        fun currentOrd(): Int {
            assert(current !== EMPTY && current !== EXHAUSTED)
            for (i in iterators.indices) {
                if (iterators[i] === current) {
                    return i
                }
            }
            throw IllegalStateException()
        }

        @Throws(IOException::class)
        override fun nextInterval(): Int {
            if (current === EMPTY || current === EXHAUSTED) {
                if (intervalQueue.size() > 0) {
                    current = intervalQueue.top()
                }
                return current.start()
            }
            val start = current.start()
            val end = current.end()
            while (intervalQueue.size() > 0 && contains(intervalQueue.top(), start, end)) {
                val it = intervalQueue.pop()
                if (it != null && it.nextInterval() != NO_MORE_INTERVALS) {
                    intervalQueue.add(it)
                }
            }
            if (intervalQueue.size() == 0) {
                current = EXHAUSTED
                return NO_MORE_INTERVALS
            }
            current = intervalQueue.top()
            return current.start()
        }

        private fun contains(it: IntervalIterator, start: Int, end: Int): Boolean {
            return start >= it.start() &&
                start <= it.end() &&
                end >= it.start() &&
                end <= it.end()
        }

        override fun docID(): Int {
            return approximation.docID()
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            val doc = approximation.nextDoc()
            reset()
            return doc
        }

        @Throws(IOException::class)
        override fun advance(target: Int): Int {
            val doc = approximation.advance(target)
            reset()
            return doc
        }

        override fun cost(): Long {
            return approximation.cost()
        }

        companion object {
            private val EMPTY =
                object : IntervalIterator() {

                    override fun docID(): Int {
                        throw UnsupportedOperationException()
                    }

                    @Throws(IOException::class)
                    override fun nextDoc(): Int {
                        throw UnsupportedOperationException()
                    }

                    @Throws(IOException::class)
                    override fun advance(target: Int): Int {
                        throw UnsupportedOperationException()
                    }

                    override fun cost(): Long {
                        throw UnsupportedOperationException()
                    }

                    override fun start(): Int {
                        return -1
                    }

                    override fun end(): Int {
                        return -1
                    }

                    override fun gaps(): Int {
                        throw UnsupportedOperationException()
                    }

                    override fun nextInterval(): Int {
                        return NO_MORE_INTERVALS
                    }

                    override fun matchCost(): Float {
                        return 0f
                    }
                }

            private val EXHAUSTED =
                object : IntervalIterator() {

                    override fun docID(): Int {
                        throw UnsupportedOperationException()
                    }

                    @Throws(IOException::class)
                    override fun nextDoc(): Int {
                        throw UnsupportedOperationException()
                    }

                    @Throws(IOException::class)
                    override fun advance(target: Int): Int {
                        throw UnsupportedOperationException()
                    }

                    override fun cost(): Long {
                        throw UnsupportedOperationException()
                    }

                    override fun start(): Int {
                        return NO_MORE_INTERVALS
                    }

                    override fun end(): Int {
                        return NO_MORE_INTERVALS
                    }

                    override fun gaps(): Int {
                        throw UnsupportedOperationException()
                    }

                    override fun nextInterval(): Int {
                        return NO_MORE_INTERVALS
                    }

                    override fun matchCost(): Float {
                        return 0f
                    }
                }
        }
    }

    private class DisjunctionMatchesIterator(
        val it: DisjunctionIntervalIterator,
        val subs: List<IntervalMatchesIterator>
    ) : IntervalMatchesIterator {

        @Throws(IOException::class)
        override fun next(): Boolean {
            return it.nextInterval() != IntervalIterator.NO_MORE_INTERVALS
        }

        override fun startPosition(): Int {
            return it.start()
        }

        override fun endPosition(): Int {
            return it.end()
        }

        @Throws(IOException::class)
        override fun startOffset(): Int {
            val ord = it.currentOrd()
            return subs[ord].startOffset()
        }

        @Throws(IOException::class)
        override fun endOffset(): Int {
            val ord = it.currentOrd()
            return subs[ord].endOffset()
        }

        override val subMatches: MatchesIterator?
            get() {
                val ord = it.currentOrd()
                return subs[ord].subMatches
            }

        override val query: Query?
            get() {
                val ord = it.currentOrd()
                return subs[ord].query
            }

        override fun gaps(): Int {
            return it.gaps()
        }

        override fun width(): Int {
            return it.width()
        }
    }
}
