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
import org.gnit.lucenekmp.search.BooleanClause
import org.gnit.lucenekmp.search.QueryVisitor

internal abstract class DifferenceIntervalsSource(
    val minuend: IntervalsSource,
    val subtrahend: IntervalsSource
) : IntervalsSource() {

    protected abstract fun combine(
        minuend: IntervalIterator,
        subtrahend: IntervalIterator
    ): IntervalIterator

    @Throws(IOException::class)
    final override fun intervals(field: String, ctx: LeafReaderContext): IntervalIterator? {
        val minIt = minuend.intervals(field, ctx)
        if (minIt == null) {
            return null
        }
        val subIt = subtrahend.intervals(field, ctx)
        if (subIt == null) {
            return minIt
        }
        return combine(minIt, subIt)
    }

    @Throws(IOException::class)
    final override fun matches(
        field: String,
        ctx: LeafReaderContext,
        doc: Int
    ): IntervalMatchesIterator? {
        val minIt = minuend.matches(field, ctx, doc)
        if (minIt == null) {
            return null
        }
        val subIt = subtrahend.matches(field, ctx, doc)
        if (subIt == null) {
            return minIt
        }
        val difference =
            combine(
                IntervalMatches.wrapMatches(minIt, doc),
                IntervalMatches.wrapMatches(subIt, doc)
            )
        return IntervalMatches.asMatches(difference, minIt, doc)
    }

    override fun visit(field: String, visitor: QueryVisitor) {
        val q = IntervalQuery(field, this)
        minuend.visit(field, visitor.getSubVisitor(BooleanClause.Occur.MUST, q))
        subtrahend.visit(field, visitor.getSubVisitor(BooleanClause.Occur.MUST_NOT, q))
    }

    override fun minExtent(): Int {
        return minuend.minExtent()
    }
}
