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

internal class BlockIntervalsSource private constructor(
    sources: List<IntervalsSource>
) : ConjunctionIntervalsSource(flatten(sources)) {

    companion object {
        fun build(subSources: List<IntervalsSource>): IntervalsSource {
            if (subSources.size == 1) {
                return subSources[0]
            }
            return Intervals.or(Disjunctions.pullUp(subSources) { BlockIntervalsSource(it) })
        }

        private fun flatten(sources: List<IntervalsSource>): List<IntervalsSource> {
            val flattened = mutableListOf<IntervalsSource>()
            for (s in sources) {
                if (s is BlockIntervalsSource) {
                    // Block sources can be flattened because they do not increase the gap (gap = 0)
                    flattened.addAll(s.subSources)
                } else {
                    flattened.add(s)
                }
            }
            return flattened
        }
    }

    override fun combine(iterators: List<IntervalIterator>): IntervalIterator {
        return BlockIntervalIterator(iterators)
    }

    override fun minExtent(): Int {
        var minExtent = 0
        for (subSource in subSources) {
            minExtent += subSource.minExtent()
        }
        return minExtent
    }

    override fun pullUpDisjunctions(): Collection<IntervalsSource> {
        return listOf(this) // Disjunctions already pulled up in build()
    }

    override fun hashCode(): Int {
        return 31 + subSources.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is BlockIntervalsSource) return false
        return this.subSources == other.subSources
    }

    override fun toString(): String {
        return "BLOCK(" + subSources.joinToString(",") + ")"
    }

    private class BlockIntervalIterator(
        subIterators: List<IntervalIterator>
    ) : ConjunctionIntervalIterator(subIterators) {

        var start = -1
        var end = -1

        override fun start(): Int {
            return start
        }

        override fun end(): Int {
            return end
        }

        override fun gaps(): Int {
            return 0
        }

        @Throws(IOException::class)
        override fun nextInterval(): Int {
            if (subIterators[0].nextInterval() == IntervalIterator.NO_MORE_INTERVALS) {
                end = IntervalIterator.NO_MORE_INTERVALS
                start = end
                return start
            }
            var i = 1
            while (i < subIterators.size) {
                while (subIterators[i].start() <= subIterators[i - 1].end()) {
                    if (subIterators[i].nextInterval() == IntervalIterator.NO_MORE_INTERVALS) {
                        end = IntervalIterator.NO_MORE_INTERVALS
                        start = end
                        return start
                    }
                }
                if (subIterators[i].start() == subIterators[i - 1].end() + 1) {
                    i = i + 1
                } else {
                    if (subIterators[0].nextInterval() == IntervalIterator.NO_MORE_INTERVALS) {
                        end = IntervalIterator.NO_MORE_INTERVALS
                        start = end
                        return start
                    }
                    i = 1
                }
            }
            start = subIterators[0].start()
            end = subIterators[subIterators.size - 1].end()
            return start
        }

        override fun reset() {
            end = -1
            start = end
        }
    }
}
