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
import org.gnit.lucenekmp.search.MatchesIterator
import org.gnit.lucenekmp.search.Query

internal object IntervalMatches {

    @Throws(IOException::class)
    fun asMatches(
        iterator: IntervalIterator,
        source: IntervalMatchesIterator?,
        doc: Int
    ): IntervalMatchesIterator? {
        if (source == null) {
            return null
        }
        if (iterator.advance(doc) != doc) {
            return null
        }
        if (iterator.nextInterval() == IntervalIterator.NO_MORE_INTERVALS) {
            return null
        }
        return object : IntervalMatchesIterator {

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
                return source.startOffset()
            }

            @Throws(IOException::class)
            override fun endOffset(): Int {
                return source.endOffset()
            }

            override fun gaps(): Int {
                return iterator.gaps()
            }

            override fun width(): Int {
                return iterator.width()
            }

            override val subMatches: MatchesIterator?
                get() = source.subMatches

            override val query: Query?
                get() = source.query
        }
    }

    internal enum class State {
        UNPOSITIONED,
        ITERATING,
        NO_MORE_INTERVALS,
        EXHAUSTED
    }

    fun wrapMatches(mi: IntervalMatchesIterator, doc: Int): IntervalIterator {
        return object : IntervalIterator() {

            var state = State.UNPOSITIONED

            override fun start(): Int {
                if (state == State.NO_MORE_INTERVALS) {
                    return NO_MORE_INTERVALS
                }
                assert(state == State.ITERATING)
                return mi.startPosition()
            }

            override fun end(): Int {
                if (state == State.NO_MORE_INTERVALS) {
                    return NO_MORE_INTERVALS
                }
                assert(state == State.ITERATING)
                return mi.endPosition()
            }

            override fun gaps(): Int {
                assert(state == State.ITERATING)
                return mi.gaps()
            }

            override fun width(): Int {
                assert(state == State.ITERATING)
                return mi.width()
            }

            @Throws(IOException::class)
            override fun nextInterval(): Int {
                assert(state == State.ITERATING)
                if (mi.next()) {
                    return mi.startPosition()
                }
                state = State.NO_MORE_INTERVALS
                return NO_MORE_INTERVALS
            }

            override fun matchCost(): Float {
                return 1f
            }

            override fun docID(): Int {
                when (state) {
                    State.UNPOSITIONED -> return -1
                    State.ITERATING,
                    State.NO_MORE_INTERVALS -> return doc
                    State.EXHAUSTED -> {}
                }
                return NO_MORE_DOCS
            }

            override fun nextDoc(): Int {
                when (state) {
                    State.UNPOSITIONED -> {
                        state = State.ITERATING
                        return doc
                    }
                    State.ITERATING,
                    State.NO_MORE_INTERVALS -> {
                        state = State.EXHAUSTED
                    }
                    State.EXHAUSTED -> {}
                }
                return NO_MORE_DOCS
            }

            override fun advance(target: Int): Int {
                if (target == doc) {
                    state = State.ITERATING
                    return doc
                }
                state = State.EXHAUSTED
                return NO_MORE_DOCS
            }

            override fun cost(): Long {
                return 1
            }
        }
    }
}
