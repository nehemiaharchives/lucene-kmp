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

import org.gnit.lucenekmp.search.Query

/**
 * DocValues field for IntRange. This is a single valued field per document due to being an
 * extension of BinaryDocValuesField.
 */
class IntRangeDocValuesField(
    field: String,
    val min: IntArray,
    val max: IntArray,
) : BinaryRangeDocValuesField(field, IntRange.encode(min, max), min.size, IntRange.BYTES) {
    /** Sole constructor. */
    init {
        checkArgs(min, max)
    }

    /** Get the minimum value for the given dimension. */
    fun getMin(dimension: Int): Int {
        if (dimension > 4 || dimension > min.size) {
            throw IllegalArgumentException("Dimension out of valid range")
        }
        return min[dimension]
    }

    /** Get the maximum value for the given dimension. */
    fun getMax(dimension: Int): Int {
        if (dimension > 4 || dimension > min.size) {
            throw IllegalArgumentException("Dimension out of valid range")
        }
        return max[dimension]
    }

    companion object {
        private fun newSlowRangeQuery(
            field: String,
            min: IntArray,
            max: IntArray,
            queryType: RangeFieldQuery.QueryType,
        ): Query {
            checkArgs(min, max)
            return IntRangeSlowRangeQuery(field, min, max, queryType)
        }

        /**
         * Create a new range query that finds all ranges that intersect using doc values. NOTE: This
         * doesn't leverage indexing and may be slow.
         *
         * @see IntRange.newIntersectsQuery
         */
        fun newSlowIntersectsQuery(field: String, min: IntArray, max: IntArray): Query {
            return newSlowRangeQuery(field, min, max, RangeFieldQuery.QueryType.INTERSECTS)
        }

        /** validate the arguments */
        private fun checkArgs(min: IntArray, max: IntArray) {
            if (min.isEmpty() || max.isEmpty()) {
                throw IllegalArgumentException("min/max range values cannot be null or empty")
            }
            if (min.size != max.size) {
                throw IllegalArgumentException("min/max ranges must agree")
            }
            for (i in min.indices) {
                if (min[i] > max[i]) {
                    throw IllegalArgumentException(
                        "min should be less than max but min = ${min[i]} and max = ${max[i]}",
                    )
                }
            }
        }
    }
}
