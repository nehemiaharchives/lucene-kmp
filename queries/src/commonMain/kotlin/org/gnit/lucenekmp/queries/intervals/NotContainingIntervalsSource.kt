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

internal class NotContainingIntervalsSource private constructor(
    minuend: IntervalsSource,
    subtrahend: IntervalsSource
) : DifferenceIntervalsSource(minuend, subtrahend) {

    companion object {
        fun build(minuend: IntervalsSource, subtrahend: IntervalsSource): IntervalsSource {
            return Intervals.or(
                Disjunctions.pullUp(minuend) { s ->
                    NotContainingIntervalsSource(s, subtrahend)
                }
            )
        }
    }

    override fun combine(
        minuend: IntervalIterator,
        subtrahend: IntervalIterator
    ): IntervalIterator {
        return NotContainingIterator(minuend, subtrahend)
    }

    override fun pullUpDisjunctions(): Collection<IntervalsSource> {
        return listOf(this)
    }

    override fun hashCode(): Int {
        var result = 1
        result = 31 * result + minuend.hashCode()
        result = 31 * result + subtrahend.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (other !is NotContainingIntervalsSource) return false
        return this.minuend == other.minuend && this.subtrahend == other.subtrahend
    }

    override fun toString(): String {
        return "NOT_CONTAINING($minuend,$subtrahend)"
    }

    private class NotContainingIterator(
        minuend: IntervalIterator,
        subtrahend: IntervalIterator
    ) : RelativeIterator(minuend, subtrahend) {

        @Throws(IOException::class)
        override fun nextInterval(): Int {
            if (bpos == false) {
                return a.nextInterval()
            }
            while (a.nextInterval() != NO_MORE_INTERVALS) {
                while (b.start() < a.start() && b.end() < a.end()) {
                    if (b.nextInterval() == NO_MORE_INTERVALS) {
                        bpos = false
                        return a.start()
                    }
                }
                if (b.start() > a.end()) {
                    return a.start()
                }
            }
            return NO_MORE_INTERVALS
        }
    }
}
