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
import org.gnit.lucenekmp.jdkport.assert

internal class ContainedByIntervalsSource private constructor(
    private val small: IntervalsSource,
    private val big: IntervalsSource
) : ConjunctionIntervalsSource(listOf(small, big)) {

    companion object {
        fun build(small: IntervalsSource, big: IntervalsSource): IntervalsSource {
            return Intervals.or(
                Disjunctions.pullUp(big) { s -> ContainedByIntervalsSource(small, s) }
            )
        }
    }

    override fun combine(iterators: List<IntervalIterator>): IntervalIterator {
        assert(iterators.size == 2)
        val a = iterators[0]
        val b = iterators[1]
        return object : FilteringIntervalIterator(a, b) {
            @Throws(IOException::class)
            override fun nextInterval(): Int {
                if (bpos == false) {
                    return IntervalIterator.NO_MORE_INTERVALS
                }
                while (a.nextInterval() != IntervalIterator.NO_MORE_INTERVALS) {
                    while (b.end() < a.end()) {
                        if (b.nextInterval() == IntervalIterator.NO_MORE_INTERVALS) {
                            bpos = false
                            return IntervalIterator.NO_MORE_INTERVALS
                        }
                    }
                    if (b.start() <= a.start()) {
                        return a.start()
                    }
                }
                bpos = false
                return IntervalIterator.NO_MORE_INTERVALS
            }
        }
    }

    override fun createMatchesIterator(
        it: IntervalIterator,
        subs: List<IntervalMatchesIterator>
    ): IntervalMatchesIterator {
        assert(subs.size == 2)
        // the only sub source we care is the "small" source
        return ConjunctionMatchesIterator(it, listOf(subs[0]))
    }

    override fun minExtent(): Int {
        return small.minExtent()
    }

    override fun pullUpDisjunctions(): Collection<IntervalsSource> {
        return Disjunctions.pullUp(big) { s -> ContainedByIntervalsSource(small, s) }
    }

    override fun hashCode(): Int {
        return subSources.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is ContainedByIntervalsSource) return false
        return this.subSources == other.subSources
    }

    override fun toString(): String {
        return "CONTAINED_BY($small,$big)"
    }
}
