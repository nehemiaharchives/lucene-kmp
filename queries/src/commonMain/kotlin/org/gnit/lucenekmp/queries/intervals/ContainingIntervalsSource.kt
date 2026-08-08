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

internal class ContainingIntervalsSource private constructor(
    private val big: IntervalsSource,
    private val small: IntervalsSource
) : ConjunctionIntervalsSource(listOf(big, small)) {

    companion object {
        fun build(big: IntervalsSource, small: IntervalsSource): IntervalsSource {
            return Intervals.or(
                Disjunctions.pullUp(big) { s -> ContainingIntervalsSource(s, small) }
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
                    while (b.start() < a.start() && b.end() < a.end()) {
                        if (b.nextInterval() == IntervalIterator.NO_MORE_INTERVALS) {
                            bpos = false
                            return IntervalIterator.NO_MORE_INTERVALS
                        }
                    }
                    if (a.start() <= b.start() && a.end() >= b.end()) {
                        return a.start()
                    }
                }
                return IntervalIterator.NO_MORE_INTERVALS
            }
        }
    }

    override fun minExtent(): Int {
        return big.minExtent()
    }

    override fun pullUpDisjunctions(): Collection<IntervalsSource> {
        return Disjunctions.pullUp(big) { s -> ContainingIntervalsSource(s, small) }
    }

    override fun hashCode(): Int {
        return 31 + this.subSources.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is ContainingIntervalsSource) return false
        return this.subSources == other.subSources
    }

    override fun toString(): String {
        return "CONTAINING($big,$small)"
    }
}
