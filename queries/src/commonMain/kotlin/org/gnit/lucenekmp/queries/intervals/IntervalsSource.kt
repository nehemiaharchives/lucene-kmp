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
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.search.QueryVisitor

/**
 * A helper class for [IntervalQuery] that provides an [IntervalIterator] for a given
 * field and segment
 *
 * <p>Static constructor functions for various different sources can be found in the
 * [Intervals] class
 */
abstract class IntervalsSource {

    /**
     * Create an [IntervalIterator] exposing the minimum intervals defined by this
     * [IntervalsSource]
     *
     * <p>Returns `null` if no intervals for this field exist in this segment
     *
     * @param field the field to read positions from
     * @param ctx the context for which to return the iterator
     */
    @Throws(IOException::class)
    abstract fun intervals(field: String, ctx: LeafReaderContext): IntervalIterator?

    /**
     * Return a [IntervalMatchesIterator] over the intervals defined by this [IntervalsSource] for
     * a given document and field
     *
     * <p>Returns `null` if no intervals exist in the given document and field
     *
     * @param field the field to read positions from
     * @param ctx the document's context
     * @param doc the document to return matches for
     */
    @Throws(IOException::class)
    abstract fun matches(field: String, ctx: LeafReaderContext, doc: Int): IntervalMatchesIterator?

    /** Expert: visit the tree of sources */
    abstract fun visit(field: String, visitor: QueryVisitor)

    /** Return the minimum possible width of an interval returned by this source */
    abstract fun minExtent(): Int

    /**
     * Expert: return the set of disjunctions that make up this IntervalsSource
     *
     * <p>Most implementations can return `Collections.singleton(this)`
     */
    abstract fun pullUpDisjunctions(): Collection<IntervalsSource>

    abstract override fun hashCode(): Int

    abstract override fun equals(other: Any?): Boolean

    abstract override fun toString(): String
}
