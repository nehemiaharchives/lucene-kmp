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

internal class OverlappingIntervalsSource(
    private val source: IntervalsSource,
    private val reference: IntervalsSource
) : ConjunctionIntervalsSource(listOf(source, reference)) {

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
                    while (b.end() < a.start()) {
                        if (b.nextInterval() == IntervalIterator.NO_MORE_INTERVALS) {
                            bpos = false
                            return IntervalIterator.NO_MORE_INTERVALS
                        }
                    }
                    if (b.start() <= a.end()) {
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
        // the only sub source we care is the "real" source
        return ConjunctionMatchesIterator(it, listOf(subs[0]))
    }

    override fun minExtent(): Int {
        return source.minExtent()
    }

    override fun pullUpDisjunctions(): Collection<IntervalsSource> {
        return Disjunctions.pullUp(listOf(source, reference)) { ss ->
            OverlappingIntervalsSource(ss[0], ss[1])
        }
    }

    override fun hashCode(): Int {
        return 31 + this.subSources.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is OverlappingIntervalsSource) return false
        return this.subSources == other.subSources
    }

    override fun toString(): String {
        return "OVERLAPPING($source,$reference)"
    }
}
