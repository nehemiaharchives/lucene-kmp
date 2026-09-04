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
import org.gnit.lucenekmp.search.FilterMatchesIterator
import org.gnit.lucenekmp.search.MatchesIterator
import org.gnit.lucenekmp.search.MatchesUtils
import org.gnit.lucenekmp.search.Query
import kotlin.math.max
import kotlin.math.min

internal class ConjunctionMatchesIterator(
    val iterator: IntervalIterator,
    val subs: List<IntervalMatchesIterator>
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
        for (s in subs) {
            val v = s.startOffset()
            if (v == -1) {
                return -1
            }
            start = min(start, v)
        }
        return start
    }

    @Throws(IOException::class)
    override fun endOffset(): Int {
        var end = -1
        for (s in subs) {
            val v = s.endOffset()
            if (v == -1) {
                return -1
            }
            end = max(end, v)
        }
        return end
    }

    override val subMatches: MatchesIterator?
        get() {
            val subMatches = mutableListOf<MatchesIterator>()
            for (mi in subs) {
                var sub = mi.subMatches
                if (sub == null) {
                    sub = SingletonMatchesIterator(mi)
                }
                subMatches.add(sub)
            }
            return MatchesUtils.disjunction(subMatches)
        }

    override val query: Query
        get() = throw UnsupportedOperationException()

    override fun gaps(): Int {
        return iterator.gaps()
    }

    override fun width(): Int {
        return iterator.width()
    }

    internal class SingletonMatchesIterator(
        `in`: MatchesIterator
    ) : FilterMatchesIterator(`in`) {

        var exhausted = false

        override fun next(): Boolean {
            if (exhausted) {
                return false
            }
            exhausted = true
            return true
        }
    }
}
