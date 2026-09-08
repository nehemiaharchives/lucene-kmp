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
package org.gnit.lucenekmp.facet.taxonomy

/**
 * Specify aggregation logic used in [TaxonomyFacetIntAssociations] and [TaxonomyFacetFloatAssociations].
 */
abstract class AssociationAggregationFunction /** Sole constructor. */ protected constructor() {
    // TODO: Would be nice to add support for MIN as well here, but there are a number of places
    // in our facet implementations where we attribute special meaning to 0 and assume that valid
    // values are always positive. I think we'd want to break that assumption for MIN to really
    // make sense.

    /** Implement aggregation logic for integers */
    abstract fun aggregate(existingVal: Int, newVal: Int): Int
    /** Implement aggregation logic for floats */
    abstract fun aggregate(existingVal: Float, newVal: Float): Float

    companion object {
        /** Aggregation that computes the maximum value */
        val MAX = object : AssociationAggregationFunction() {
            override fun aggregate(existingVal: Int, newVal: Int): Int = maxOf(existingVal, newVal)
            override fun aggregate(existingVal: Float, newVal: Float): Float = maxOf(existingVal, newVal)
        }
        /** Aggregation that computes the sum */
        val SUM = object : AssociationAggregationFunction() {
            override fun aggregate(existingVal: Int, newVal: Int): Int = existingVal + newVal
            override fun aggregate(existingVal: Float, newVal: Float): Float = existingVal + newVal
        }
    }
}
