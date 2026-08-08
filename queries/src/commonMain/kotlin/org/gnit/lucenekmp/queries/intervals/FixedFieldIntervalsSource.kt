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

internal class FixedFieldIntervalsSource(
    private val field: String,
    private val source: IntervalsSource
) : IntervalsSource() {

    @Throws(IOException::class)
    override fun intervals(field: String, ctx: LeafReaderContext): IntervalIterator? {
        return source.intervals(this.field, ctx)
    }

    @Throws(IOException::class)
    override fun matches(
        field: String,
        ctx: LeafReaderContext,
        doc: Int
    ): IntervalMatchesIterator? {
        return source.matches(this.field, ctx, doc)
    }

    override fun visit(field: String, visitor: QueryVisitor) {
        source.visit(this.field, visitor)
    }

    override fun minExtent(): Int {
        return source.minExtent()
    }

    override fun pullUpDisjunctions(): Collection<IntervalsSource> {
        val inner = source.pullUpDisjunctions()
        if (inner.size == 1) {
            return setOf(this)
        }
        return inner.map { s -> FixedFieldIntervalsSource(field, s) }.toSet()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FixedFieldIntervalsSource) return false
        return field == other.field && source == other.source
    }

    override fun hashCode(): Int {
        var result = 1
        result = 31 * result + field.hashCode()
        result = 31 * result + source.hashCode()
        return result
    }

    override fun toString(): String {
        return "FIELD($field,$source)"
    }
}
