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
package org.gnit.lucenekmp.facet.facetset

import org.gnit.lucenekmp.jdkport.assert

/**
 * A [FacetSetMatcher] which considers a set as a match if all dimensions fall within the
 * given corresponding range.
 *
 * @lucene.experimental
 */
open class RangeFacetSetMatcher : FacetSetMatcher {
    private val lowerRanges: LongArray
    private val upperRanges: LongArray

    /**
   * Constructs an instance to match facet sets with dimensions that fall within the given ranges.
   */
    constructor(label: String?, vararg dimRanges: DimRange) : super(label, getDims(*dimRanges)) {
        this.lowerRanges = LongArray(dimRanges.size) { dimRanges[it].min }
        this.upperRanges = LongArray(dimRanges.size) { dimRanges[it].max }
    }

    override fun matches(dimValues: LongArray): Boolean {
        assert(dimValues.size == dims) {
            "Encoded dimensions (dims=${dimValues.size}) is incompatible with range dimensions (dims=$dims)"
        }
        for (i in dimValues.indices) {
            if (dimValues[i] < lowerRanges[i]) {
                // Doc's value is too low in this dimension
                return false
            }
            if (dimValues[i] > upperRanges[i]) {
                // Doc's value is too high in this dimension
                return false
            }
        }
        return true
    }

    companion object {
        private fun getDims(vararg dimRanges: DimRange): Int {
            if (dimRanges.isEmpty()) {
                throw IllegalArgumentException("dimRanges cannot be null or empty")
            }
            return dimRanges.size
        }
    }
}
