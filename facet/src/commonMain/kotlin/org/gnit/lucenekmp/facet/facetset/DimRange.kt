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

import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.util.NumericUtils

/**
 * Defines a single range in a [FacetSet] dimension.
 *
 * @param min Inclusive min
 * @param max Inclusive max
 * @lucene.experimental
 */
data class DimRange(val min: Long, val max: Long) {
    companion object {
        /**
   * Creates a [DimRange] for the given min and max long values. This method is also suitable
   * for int values.
   */
        fun fromLongs(min: Long, minInclusive: Boolean, max: Long, maxInclusive: Boolean): DimRange {
            var min = min
            var max = max
            if (!minInclusive) {
                if (min != Long.MAX_VALUE) {
                    min++
                } else {
                    throw IllegalArgumentException("Invalid min input: $min")
                }
            }
            if (!maxInclusive) {
                if (max != Long.MIN_VALUE) {
                    max--
                } else {
                    throw IllegalArgumentException("Invalid max input: $max")
                }
            }
            if (min > max) {
                throw IllegalArgumentException("Minimum cannot be greater than maximum, max=$max, min=$min")
            }
            return DimRange(min, max)
        }

        /** Creates a [DimRange] for the given min and max double values. */
        fun fromDoubles(min: Double, minInclusive: Boolean, max: Double, maxInclusive: Boolean): DimRange {
            var min = min
            var max = max
            if (min.isNaN()) {
                throw IllegalArgumentException("min cannot be NaN")
            }
            if (!minInclusive) {
                min = Math.nextUp(min)
            }
            if (max.isNaN()) {
                throw IllegalArgumentException("max cannot be NaN")
            }
            if (!maxInclusive) {
                max = Math.nextDown(max)
            }
            if (min > max) {
                throw IllegalArgumentException("Minimum cannot be greater than maximum")
            }
            return DimRange(NumericUtils.doubleToSortableLong(min), NumericUtils.doubleToSortableLong(max))
        }

        /** Creates a [DimRange] for the given min and max float values. */
        fun fromFloats(min: Float, minInclusive: Boolean, max: Float, maxInclusive: Boolean): DimRange {
            var min = min
            var max = max
            if (min.isNaN()) {
                throw IllegalArgumentException("min cannot be NaN")
            }
            if (!minInclusive) {
                min = Math.nextUp(min)
            }
            if (max.isNaN()) {
                throw IllegalArgumentException("max cannot be NaN")
            }
            if (!maxInclusive) {
                max = Math.nextDown(max)
            }
            if (min > max) {
                throw IllegalArgumentException("Minimum cannot be greater than maximum")
            }
            return DimRange(NumericUtils.floatToSortableInt(min).toLong(), NumericUtils.floatToSortableInt(max).toLong())
        }
    }
}
