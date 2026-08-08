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

/**
 * Wraps an [IntervalIterator] and passes through those intervals that match the
 * [accept] function
 */
abstract class IntervalFilter(
    /** Create a new filter */
    protected val `in`: IntervalIterator
) : IntervalIterator() {

    override fun docID(): Int {
        return `in`.docID()
    }

    @Throws(IOException::class)
    override fun nextDoc(): Int {
        return `in`.nextDoc()
    }

    @Throws(IOException::class)
    override fun advance(target: Int): Int {
        return `in`.advance(target)
    }

    override fun cost(): Long {
        return `in`.cost()
    }

    override fun start(): Int {
        return `in`.start()
    }

    override fun end(): Int {
        return `in`.end()
    }

    override fun gaps(): Int {
        return `in`.gaps()
    }

    override fun matchCost(): Float {
        return `in`.matchCost()
    }

    /**
     * @return `true` if the wrapped iterator's interval should be passed on
     */
    protected abstract fun accept(): Boolean

    @Throws(IOException::class)
    final override fun nextInterval(): Int {
        var next: Int
        do {
            next = `in`.nextInterval()
        } while (next != IntervalIterator.NO_MORE_INTERVALS && accept() == false)
        return next
    }
}
