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

internal class OrderedIntervalsSource private constructor(
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
            return OrderedIntervalsSource(rewritten)
        }

        private fun deduplicate(sources: List<IntervalsSource>): List<IntervalsSource> {
            val deduplicated = mutableListOf<IntervalsSource>()
            val current = mutableListOf<IntervalsSource>()
            for (source in sources) {
                if (current.size == 0 || current[0] == source) {
                    current.add(source)
                } else {
                    deduplicated.add(RepeatingIntervalsSource.build(current[0], current.size))
                    current.clear()
                    current.add(source)
                }
            }
            deduplicated.add(RepeatingIntervalsSource.build(current[0], current.size))
            if (deduplicated.size == 1 && deduplicated[0] is RepeatingIntervalsSource) {
                (deduplicated[0] as RepeatingIntervalsSource).name = "ORDERED"
            }
            return deduplicated
        }
    }

    override fun combine(
        iterators: List<IntervalIterator>,
        onMatch: MatchCallback
    ): IntervalIterator {
        return OrderedIntervalIterator(iterators, onMatch)
    }

    override fun minExtent(): Int {
        var minExtent = 0
        for (subSource in subSources) {
            minExtent += subSource.minExtent()
        }
        return minExtent
    }

    override fun pullUpDisjunctions(): Collection<IntervalsSource> {
        return Disjunctions.pullUp(subSources) { OrderedIntervalsSource(it) }
    }

    override fun hashCode(): Int {
        return subSources.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is OrderedIntervalsSource) return false
        return subSources == other.subSources
    }

    override fun toString(): String {
        return "ORDERED(" + subSources.joinToString(",") + ")"
    }

    private class OrderedIntervalIterator(
        subIntervals: List<IntervalIterator>,
        val onMatch: MatchCallback
    ) : ConjunctionIntervalIterator(subIntervals) {

        var start = -1
        var end = -1
        var i = 1
        var slop: Int = 0

        override fun start(): Int {
            return start
        }

        override fun end(): Int {
            return end
        }

        @Throws(IOException::class)
        override fun nextInterval(): Int {
            slop = IntervalIterator.NO_MORE_INTERVALS
            end = slop
            start = end
            var lastStart = Int.MAX_VALUE
            var minimizing = false
            val subIterators = this.subIterators
            var currentIndex = i
            while (true) {
                var prevEnd = subIterators[currentIndex - 1].end()
                while (true) {
                    if (prevEnd >= lastStart) {
                        i = currentIndex
                        return start
                    }
                    if (currentIndex == subIterators.size) {
                        break
                    }
                    val current = subIterators[currentIndex]
                    if (minimizing && current.start() > prevEnd) {
                        break
                    }
                    var currentStart: Int
                    do {
                        if (current.end() >= lastStart) {
                            i = currentIndex
                            return start
                        }
                        currentStart = current.nextInterval()
                        if (currentStart == IntervalIterator.NO_MORE_INTERVALS) {
                            i = currentIndex
                            return start
                        }
                    } while (currentStart <= prevEnd)
                    currentIndex++
                    prevEnd = current.end()
                }
                val first = subIterators[0]
                val start = first.start()
                this.start = start
                if (start == NO_MORE_INTERVALS) {
                    i = currentIndex
                    end = NO_MORE_INTERVALS
                    return end
                }
                val last = subIterators.last()

                val end = last.end()
                this.end = end
                var slop = end - start + 1
                // use indexed loop since this is always a random access capable list to avoid allocations
                // in a hot nested loop
                for (j in subIterators.indices) {
                    slop -= subIterators[j].width()
                }
                this.slop = slop
                onMatch.onMatch()
                currentIndex = 1
                if (first.nextInterval() == IntervalIterator.NO_MORE_INTERVALS) {
                    i = currentIndex
                    return start
                }
                lastStart = last.start()
                minimizing = true
            }
        }

        override fun gaps(): Int {
            return slop
        }

        @Throws(IOException::class)
        override fun reset() {
            subIterators[0].nextInterval()
            i = 1
            slop = -1
            end = slop
            start = end
        }
    }
}
