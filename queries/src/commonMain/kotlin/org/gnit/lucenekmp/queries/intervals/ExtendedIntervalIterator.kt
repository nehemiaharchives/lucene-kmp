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
import kotlin.math.max

/**
 * Wraps an IntervalIterator and extends the bounds of its intervals
 *
 * <p>Useful for specifying gaps in an ordered iterator; if you want to match `a b [2 spaces] c`,
 * you can search for phrase(a, extended(b, 0, 2), c)
 *
 * <p>An interval with prefix bounds extended by n will skip over matches that appear in positions
 * lower than n
 */
internal class ExtendedIntervalIterator(
    private val `in`: IntervalIterator,
    private val before: Int,
    private val after: Int
) : IntervalIterator() {

    private var positioned: Boolean = false

    override fun start(): Int {
        if (positioned == false) {
            return -1
        }
        val start = `in`.start()
        if (start == NO_MORE_INTERVALS) {
            return NO_MORE_INTERVALS
        }
        return max(0, start - before)
    }

    override fun end(): Int {
        if (positioned == false) {
            return -1
        }
        var end = `in`.end()
        if (end == NO_MORE_INTERVALS) {
            return NO_MORE_INTERVALS
        }
        end += after
        if (end < 0 || end == NO_MORE_INTERVALS) {
            // overflow
            end = NO_MORE_INTERVALS - 1
        }
        return end
    }

    override fun gaps(): Int {
        return `in`.gaps()
    }

    @Throws(IOException::class)
    override fun nextInterval(): Int {
        positioned = true
        `in`.nextInterval()
        return start()
    }

    override fun matchCost(): Float {
        return `in`.matchCost()
    }

    override fun docID(): Int {
        return `in`.docID()
    }

    @Throws(IOException::class)
    override fun nextDoc(): Int {
        positioned = false
        return `in`.nextDoc()
    }

    @Throws(IOException::class)
    override fun advance(target: Int): Int {
        positioned = false
        return `in`.advance(target)
    }

    override fun cost(): Long {
        return `in`.cost()
    }
}
