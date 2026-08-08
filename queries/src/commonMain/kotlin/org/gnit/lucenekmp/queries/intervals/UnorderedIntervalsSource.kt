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
import org.gnit.lucenekmp.util.PriorityQueue

internal class UnorderedIntervalsSource private constructor(
    sources: List<IntervalsSource>
) : MinimizingConjunctionIntervalsSource(sources) {

    companion object {
        fun build(sources: List<IntervalsSource>): IntervalsSource {
            if (sources.size == 1) {
                return sources[0]
            }
            val rewritten = deduplicate(sources)
            if (rewritten.size == 1) {
                return rewritten[0]
            }
            return UnorderedIntervalsSource(rewritten)
        }

        private fun deduplicate(sources: List<IntervalsSource>): List<IntervalsSource> {
            val counts = mutableMapOf<IntervalsSource, Int>() // preserve order for testing
            for (source in sources) {
                counts[source] = (counts[source] ?: 0) + 1
            }
            val deduplicated = mutableListOf<IntervalsSource>()
            for (source in counts.keys) {
                deduplicated.add(RepeatingIntervalsSource.build(source, counts[source]!!))
            }
            if (deduplicated.size == 1 && deduplicated[0] is RepeatingIntervalsSource) {
                (deduplicated[0] as RepeatingIntervalsSource).name = "UNORDERED"
            }
            return deduplicated
        }
    }

    override fun combine(
        iterators: List<IntervalIterator>,
        onMatch: MatchCallback
    ): IntervalIterator {
        return UnorderedIntervalIterator(iterators, onMatch)
    }

    override fun minExtent(): Int {
        var minExtent = 0
        for (subSource in subSources) {
            minExtent += subSource.minExtent()
        }
        return minExtent
    }

    override fun pullUpDisjunctions(): Collection<IntervalsSource> {
        return Disjunctions.pullUp(subSources) { UnorderedIntervalsSource(it) }
    }

    override fun hashCode(): Int {
        return 31 + this.subSources.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is UnorderedIntervalsSource) return false
        return this.subSources == other.subSources
    }

    override fun toString(): String {
        return "UNORDERED(" + subSources.joinToString(",") + ")"
    }

    private class UnorderedIntervalIterator(
        subIterators: List<IntervalIterator>,
        private val onMatch: MatchCallback
    ) : ConjunctionIntervalIterator(subIterators) {

        private val queue =
            object : PriorityQueue<IntervalIterator>(subIterators.size) {
                override fun lessThan(a: IntervalIterator, b: IntervalIterator): Boolean {
                    return a.start() < b.start() ||
                        (a.start() == b.start() && a.end() >= b.end())
                }
            }
        private val subIteratorsArray: Array<IntervalIterator> = subIterators.toTypedArray()

        var start = -1
        var end = -1
        var slop: Int = 0
        var queueEnd: Int = 0

        override fun start(): Int {
            return start
        }

        override fun end(): Int {
            return end
        }

        fun updateRightExtreme(it: IntervalIterator) {
            val itEnd = it.end()
            if (itEnd > queueEnd) {
                queueEnd = itEnd
            }
        }

        @Throws(IOException::class)
        override fun nextInterval(): Int {
            // first, find a matching interval
            while (this.queue.size() == subIteratorsArray.size && queue.top().start() == start) {
                val it = queue.pop()
                if (it != null && it.nextInterval() != IntervalIterator.NO_MORE_INTERVALS) {
                    queue.add(it)
                    updateRightExtreme(it)
                }
            }
            if (this.queue.size() < subIteratorsArray.size) {
                end = IntervalIterator.NO_MORE_INTERVALS
                start = end
                return start
            }
            // then, minimize it
            do {
                start = queue.top().start()
                end = queueEnd
                slop = width()
                for (it in subIteratorsArray) {
                    slop -= it.width()
                }
                onMatch.onMatch()
                if (queue.top().end() == end) {
                    return start
                }
                val it = queue.pop()
                if (it != null && it.nextInterval() != IntervalIterator.NO_MORE_INTERVALS) {
                    queue.add(it)
                    updateRightExtreme(it)
                }
            } while (this.queue.size() == subIteratorsArray.size && end == queueEnd)
            return start
        }

        override fun gaps(): Int {
            return slop
        }

        @Throws(IOException::class)
        override fun reset() {
            end = -1
            start = end
            queueEnd = start
            this.queue.clear()
            for (it in subIteratorsArray) {
                if (it.nextInterval() == NO_MORE_INTERVALS) {
                    break
                }
                queue.add(it)
                updateRightExtreme(it)
            }
        }
    }
}
