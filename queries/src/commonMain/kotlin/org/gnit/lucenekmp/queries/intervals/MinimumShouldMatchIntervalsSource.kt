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
import org.gnit.lucenekmp.search.MatchesUtils
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.QueryVisitor
import org.gnit.lucenekmp.util.PriorityQueue
import kotlin.math.max
import kotlin.math.min

internal class MinimumShouldMatchIntervalsSource(
    private val sources: Array<out IntervalsSource>,
    private val minShouldMatch: Int
) : IntervalsSource() {

    init {
        assert(minShouldMatch < sources.size)
    }

    @Throws(IOException::class)
    override fun intervals(field: String, ctx: LeafReaderContext): IntervalIterator? {
        val iterators = mutableListOf<IntervalIterator>()
        for (source in sources) {
            val it = source.intervals(field, ctx)
            if (it != null) {
                iterators.add(it)
            }
        }
        if (iterators.size < minShouldMatch) {
            return null
        }
        return MinimumShouldMatchIntervalIterator(
            iterators,
            minShouldMatch,
            MinimizingConjunctionIntervalsSource.MatchCallback {}
        )
    }

    @Throws(IOException::class)
    override fun matches(
        field: String,
        ctx: LeafReaderContext,
        doc: Int
    ): IntervalMatchesIterator? {
        val lookup = mutableMapOf<IntervalIterator, CachingMatchesIterator>()
        for (source in sources) {
            val mi = source.matches(field, ctx, doc)
            if (mi != null) {
                val cmi = CachingMatchesIterator(mi)
                lookup[IntervalMatches.wrapMatches(cmi, doc)] = cmi
            }
        }
        if (lookup.size < minShouldMatch) {
            return null
        }
        val it =
            MinimumShouldMatchIntervalIterator(
                lookup.keys,
                minShouldMatch,
                MinimizingConjunctionIntervalsSource.cacheIterators(lookup.values)
            )
        if (it.advance(doc) != doc) {
            return null
        }
        if (it.nextInterval() == IntervalIterator.NO_MORE_INTERVALS) {
            return null
        }
        return MinimumMatchesIterator(it, lookup)
    }

    override fun visit(field: String, visitor: QueryVisitor) {
        val parent: Query = IntervalQuery(field, this)
        val v = visitor.getSubVisitor(BooleanClause.Occur.SHOULD, parent)
        for (source in sources) {
            source.visit(field, v)
        }
    }

    override fun minExtent(): Int {
        val subExtents = IntArray(sources.size)
        for (i in subExtents.indices) {
            subExtents[i] = sources[i].minExtent()
        }
        subExtents.sort()
        var minExtent = 0
        for (i in 0 until minShouldMatch) {
            minExtent += subExtents[i]
        }
        return minExtent
    }

    override fun pullUpDisjunctions(): Collection<IntervalsSource> {
        return setOf(this)
    }

    override fun toString(): String {
        return "AtLeast(" + sources.joinToString(",") + "~" + minShouldMatch + ")"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MinimumShouldMatchIntervalsSource) return false
        return minShouldMatch == other.minShouldMatch && sources.contentEquals(other.sources)
    }

    override fun hashCode(): Int {
        var result = 31 + minShouldMatch
        result = 31 * result + sources.contentHashCode()
        return result
    }

    // This works as a combination of unordered-AND and OR
    // First of all, iterators are advanced using a DisjunctionDISIApproximation
    // Once positioned on a document, nextInterval() is called on each interval, and
    // those that have intervals are added to an OR-based priority queue (the background queue)
    // The top-n iterators (where n = minimumShouldMatch) are popped from this queue
    // and added to an AND-based priority queue (the proximity queue)
    // Iteration over intervals then proceeds according to the algorithm used by
    // UnorderedIntervalIterator based on intervals in the proximity queue, with
    // the one change that when an iterator is popped from the proximity queue, it
    // is inserted back into the background queue, and replaced by the top iterator
    // from the background queue.
    internal class MinimumShouldMatchIntervalIterator(
        subs: Collection<IntervalIterator>,
        private val minShouldMatch: Int,
        private val onMatch: MinimizingConjunctionIntervalsSource.MatchCallback
    ) : IntervalIterator() {

        private val approximation: DocIdSetIterator
        private val disiQueue: DisiPriorityQueue
        private val proximityQueue: PriorityQueue<IntervalIterator>
        private val backgroundQueue: PriorityQueue<IntervalIterator>
        private val matchCost: Float
        private val currentIterators = mutableListOf<IntervalIterator>()

        private var start = 0
        private var end = 0
        private var queueEnd = 0
        private var slop = 0
        private var lead: IntervalIterator? = null

        init {
            this.disiQueue = DisiPriorityQueue(subs.size)
            var mc = 0f
            for (it in subs) {
                this.disiQueue.add(DisiWrapper(it))
                mc += it.matchCost()
            }
            this.approximation = DisjunctionDISIApproximation(disiQueue)
            this.matchCost = mc

            this.proximityQueue =
                object : PriorityQueue<IntervalIterator>(minShouldMatch) {
                    override fun lessThan(a: IntervalIterator, b: IntervalIterator): Boolean {
                        return a.start() < b.start() ||
                            (a.start() == b.start() && a.end() >= b.end())
                    }
                }
            this.backgroundQueue =
                object : PriorityQueue<IntervalIterator>(subs.size) {
                    override fun lessThan(a: IntervalIterator, b: IntervalIterator): Boolean {
                        return a.end() < b.end() ||
                            (a.end() == b.end() && a.start() >= b.start())
                    }
                }
        }

        override fun start(): Int {
            return start
        }

        override fun end(): Int {
            return end
        }

        override fun gaps(): Int {
            return slop
        }

        @Throws(IOException::class)
        override fun nextInterval(): Int {
            lead = null
            // first, find a matching interval beyond the current start
            while (
                this.proximityQueue.size() == minShouldMatch &&
                proximityQueue.top().start() == start
            ) {
                val it = proximityQueue.pop()
                if (it != null && it.nextInterval() != IntervalIterator.NO_MORE_INTERVALS) {
                    backgroundQueue.add(it)
                    val next = backgroundQueue.pop()
                    assert(next != null) // it's just been added!
                    proximityQueue.add(next!!)
                    updateRightExtreme(next)
                }
            }
            if (this.proximityQueue.size() < minShouldMatch) {
                end = IntervalIterator.NO_MORE_INTERVALS
                start = end
                return start
            }
            // then, minimize it
            do {
                onMatch.onMatch()
                start = proximityQueue.top().start()
                end = queueEnd
                slop = width()
                for (it in proximityQueue) {
                    slop -= it.width()
                }
                if (proximityQueue.top().end() == end) {
                    return start
                }
                lead = proximityQueue.pop()
                val lead = lead
                if (lead != null) {
                    if (lead.nextInterval() != NO_MORE_INTERVALS) {
                        backgroundQueue.add(lead)
                    }
                    val next = backgroundQueue.pop()
                    if (next != null) {
                        proximityQueue.add(next)
                        updateRightExtreme(next)
                    }
                }
            } while (this.proximityQueue.size() == minShouldMatch && end == queueEnd)
            return start
        }

        fun getCurrentIterators(): Collection<IntervalIterator> {
            currentIterators.clear()
            val lead = lead
            if (lead != null) {
                currentIterators.add(lead)
            }
            for (it in this.proximityQueue) {
                if (it.end() <= end) {
                    currentIterators.add(it)
                }
            }
            return currentIterators
        }

        @Throws(IOException::class)
        private fun reset() {
            this.proximityQueue.clear()
            this.backgroundQueue.clear()
            // First we populate the background queue
            var dw: DisiWrapper? = disiQueue.topList()
            while (dw != null) {
                if (dw.intervals.nextInterval() != NO_MORE_INTERVALS) {
                    this.backgroundQueue.add(dw.intervals)
                }
                dw = dw.next
            }
            // Then we pop the first minShouldMatch entries and add them to the proximity queue
            this.queueEnd = -1
            for (i in 0 until minShouldMatch) {
                val it = this.backgroundQueue.pop()
                if (it == null) {
                    break
                }
                this.proximityQueue.add(it)
                updateRightExtreme(it)
            }
            end = -1
            start = end
        }

        private fun updateRightExtreme(it: IntervalIterator) {
            val itEnd = it.end()
            if (itEnd > queueEnd) {
                queueEnd = itEnd
            }
        }

        override fun matchCost(): Float {
            return matchCost
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
    }

    internal class MinimumMatchesIterator(
        val iterator: MinimumShouldMatchIntervalIterator,
        val lookup: Map<IntervalIterator, CachingMatchesIterator>
    ) : IntervalMatchesIterator {

        var cached = true

        @Throws(IOException::class)
        override fun next(): Boolean {
            if (cached) {
                cached = false
                return true
            }
            return iterator.nextInterval() != IntervalIterator.NO_MORE_INTERVALS
        }

        override fun startPosition(): Int {
            return iterator.start()
        }

        override fun endPosition(): Int {
            return iterator.end()
        }

        @Throws(IOException::class)
        override fun startOffset(): Int {
            var start = Int.MAX_VALUE
            for (it in iterator.getCurrentIterators()) {
                val cms = lookup[it]!!
                start = min(start, cms.startOffset())
            }
            return start
        }

        @Throws(IOException::class)
        override fun endOffset(): Int {
            var end = 0
            for (it in iterator.getCurrentIterators()) {
                val cms = lookup[it]!!
                end = max(end, cms.endOffset())
            }
            return end
        }

        override fun gaps(): Int {
            return iterator.gaps()
        }

        override fun width(): Int {
            return iterator.width()
        }

        override val subMatches: MatchesIterator?
            get() {
                val mis = mutableListOf<MatchesIterator>()
                for (it in iterator.getCurrentIterators()) {
                    val cms = lookup[it]!!
                    val mi = cms.subMatches
                    mis.add(mi ?: cms)
                }
                return MatchesUtils.disjunction(mis)
            }

        override val query: Query?
            get() = null
    }
}
