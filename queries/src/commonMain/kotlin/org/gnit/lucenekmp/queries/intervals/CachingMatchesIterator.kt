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
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.RamUsageEstimator

internal class CachingMatchesIterator(
    `in`: IntervalMatchesIterator
) : FilterMatchesIterator(`in`), IntervalMatchesIterator {

    private var posAndOffsets = IntArray(4 * 4)
    private var matchingQueries = arrayOfNulls<Query>(4)
    private var count = 0

    @Throws(IOException::class)
    fun cache() {
        count = 0
        val mi = `in`.subMatches
        if (mi == null) {
            count = 1
            posAndOffsets[0] = `in`.startPosition()
            posAndOffsets[1] = `in`.endPosition()
            posAndOffsets[2] = `in`.startOffset()
            posAndOffsets[3] = `in`.endOffset()
            matchingQueries[0] = `in`.query
        } else {
            while (mi.next()) {
                if (count * 4 >= posAndOffsets.size) {
                    posAndOffsets = ArrayUtil.grow(posAndOffsets, (count + 1) * 4)
                    matchingQueries =
                        matchingQueries.copyOf(
                            ArrayUtil.oversize(
                                count + 1,
                                RamUsageEstimator.NUM_BYTES_OBJECT_REF
                            )
                        )
                }
                posAndOffsets[count * 4] = mi.startPosition()
                posAndOffsets[count * 4 + 1] = mi.endPosition()
                posAndOffsets[count * 4 + 2] = mi.startOffset()
                posAndOffsets[count * 4 + 3] = mi.endOffset()
                matchingQueries[count] = mi.query
                count++
            }
        }
    }

    @Throws(IOException::class)
    override fun next(): Boolean {
        return `in`.next()
    }

    @Throws(IOException::class)
    override fun startOffset(): Int {
        return posAndOffsets[2]
    }

    @Throws(IOException::class)
    override fun endOffset(): Int {
        return posAndOffsets[(count - 1) * 4 + 3]
    }

    override val subMatches: MatchesIterator
        get() {
            // We always return a submatches, even if there's only a single
            // cached submatch, because this way we can return the correct
            // positions - the positions of the top-level match may have
            // moved on due to minimization
            return object : MatchesIterator {

                var upto = -1

                override fun next(): Boolean {
                    upto++
                    return upto < count
                }

                override fun startPosition(): Int {
                    return posAndOffsets[upto * 4]
                }

                override fun endPosition(): Int {
                    return posAndOffsets[upto * 4 + 1]
                }

                override fun startOffset(): Int {
                    return posAndOffsets[upto * 4 + 2]
                }

                override fun endOffset(): Int {
                    return posAndOffsets[upto * 4 + 3]
                }

                override val subMatches: MatchesIterator?
                    get() = null

                override val query: Query
                    get() = matchingQueries[upto]!!
            }
        }

    override val query: Query
        get() = matchingQueries[0]!!

    override fun gaps(): Int {
        return (`in` as IntervalMatchesIterator).gaps()
    }

    override fun width(): Int {
        return (`in` as IntervalMatchesIterator).width()
    }
}
