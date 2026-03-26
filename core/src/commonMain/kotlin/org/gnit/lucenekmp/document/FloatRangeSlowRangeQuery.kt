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

package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.QueryVisitor

class FloatRangeSlowRangeQuery(
    private val field: String,
    private val min: FloatArray,
    private val max: FloatArray,
    queryType: RangeFieldQuery.QueryType,
) : BinaryRangeFieldRangeQuery(field, encodeRanges(min, max), FloatRange.BYTES, min.size, queryType) {
    override fun equals(other: Any?): Boolean {
        if (sameClassAs(other).not()) {
            return false
        }
        other as FloatRangeSlowRangeQuery
        return field == other.field && min.contentEquals(other.min) && max.contentEquals(other.max)
    }

    override fun hashCode(): Int {
        var h = classHash()
        h = 31 * h + field.hashCode()
        h = 31 * h + min.contentHashCode()
        h = 31 * h + max.contentHashCode()
        return h
    }

    override fun visit(visitor: QueryVisitor) {
        if (visitor.acceptField(field)) {
            visitor.visitLeaf(this)
        }
    }

    override fun toString(field: String?): String {
        val b = StringBuilder()
        if (this.field != field) {
            b.append(this.field).append(":")
        }
        return b.append("[")
            .append(min.contentToString())
            .append(" TO ")
            .append(max.contentToString())
            .append("]")
            .toString()
    }

    @Throws(Exception::class)
    override fun rewrite(indexSearcher: IndexSearcher): Query {
        return super.rewrite(indexSearcher)
    }

    companion object {
        private fun encodeRanges(min: FloatArray, max: FloatArray): ByteArray {
            val result = ByteArray(2 * FloatRange.BYTES * min.size)
            FloatRange.verifyAndEncode(min, max, result)
            return result
        }
    }
}
