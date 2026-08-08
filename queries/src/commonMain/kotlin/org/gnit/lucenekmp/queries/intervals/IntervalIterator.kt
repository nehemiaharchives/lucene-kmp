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
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.TwoPhaseIterator

/**
 * A [DocIdSetIterator] that also allows iteration over matching intervals in a document.
 *
 * <p>Once the iterator is positioned on a document by calling [advance] or
 * [nextDoc], intervals may be retrieved by calling [nextInterval] until
 * [NO_MORE_INTERVALS] is returned.
 *
 * <p>The limits of the current interval are returned by [start] and [end]. When
 * the iterator has been moved to a new document, but before [nextInterval] has been
 * called, both these methods return `-1`.
 *
 * <p>Note that it is possible for a document to return [NO_MORE_INTERVALS] on the first call
 * to [nextInterval]
 */
abstract class IntervalIterator : DocIdSetIterator() {

    /**
     * The start of the current interval
     *
     * <p>Returns -1 if [nextInterval] has not yet been called and [NO_MORE_INTERVALS]
     * once the iterator is exhausted.
     */
    abstract fun start(): Int

    /**
     * The end of the current interval
     *
     * <p>Returns -1 if [nextInterval] has not yet been called and [NO_MORE_INTERVALS]
     * once the iterator is exhausted.
     */
    abstract fun end(): Int

    /**
     * The number of gaps within the current interval
     *
     * <p>Note that this returns the number of gaps between the immediate sub-intervals of this
     * interval, and does not include the gaps inside those sub-intervals.
     *
     * <p>Should not be called before [nextInterval], or after it has returned
     * [NO_MORE_INTERVALS]
     */
    abstract fun gaps(): Int

    /** The width of the current interval */
    open fun width(): Int {
        return end() - start() + 1
    }

    /**
     * Advance the iterator to the next interval
     *
     * <p>Should not be called after [DocIdSetIterator.NO_MORE_DOCS] is returned by
     * [DocIdSetIterator.nextDoc] or [DocIdSetIterator.advance]. If that's the case in
     * some existing code, please consider opening an issue. However, after
     * [IntervalIterator.NO_MORE_INTERVALS] is returned by this method, it might be called again.
     *
     * @return the start of the next interval, or [IntervalIterator.NO_MORE_INTERVALS] if there
     *     are no more intervals on the current document
     */
    @Throws(IOException::class)
    abstract fun nextInterval(): Int

    /**
     * An indication of the average cost of iterating over all intervals in a document
     *
     * @see TwoPhaseIterator.matchCost
     */
    abstract fun matchCost(): Float

    override fun toString(): String {
        return docID().toString() + ":[" + start() + "->" + end() + "]"
    }

    companion object {
        /**
         * When returned from [nextInterval], indicates that there are no more matching intervals
         * on the current document
         */
        const val NO_MORE_INTERVALS: Int = Int.MAX_VALUE
    }
}
