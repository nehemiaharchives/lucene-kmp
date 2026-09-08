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

import org.gnit.lucenekmp.document.BinaryDocValuesField
import org.gnit.lucenekmp.document.IntPoint
import org.gnit.lucenekmp.util.BytesRef

/**
 * A [BinaryDocValuesField] which encodes a list of [FacetSet facet sets]. The encoding
 * scheme consists of a packed `byte[]` where the first value denotes the number of dimensions
 * in all the sets, followed by each set's values.
 *
 * @lucene.experimental
 */
open class FacetSetsField private constructor(name: String, value: BytesRef) : BinaryDocValuesField(name, value) {
    companion object {
        /**
   * Create a new FacetSets field.
   *
   * @param name field name
   * @param facetSets the [FacetSet facet sets] to index in that field. All must have the same
   *     number of dimensions
   * @throws IllegalArgumentException if the field name is null or the given facet sets are invalid
   */
        fun create(name: String, vararg facetSets: FacetSet): FacetSetsField {
            if (facetSets.isEmpty()) {
                throw IllegalArgumentException("FacetSets cannot be null or empty!")
            }
            return FacetSetsField(name, toPackedValues(*facetSets))
        }

        private fun toPackedValues(vararg facetSets: FacetSet): BytesRef {
            val numDims = facetSets[0].dims
            val expectedClass = facetSets[0]::class
            val buf = ByteArray(Int.SIZE_BYTES + facetSets[0].sizePackedBytes() * facetSets.size)
            IntPoint.encodeDimension(numDims, buf, 0)
            var offset = Int.SIZE_BYTES
            for (facetSet in facetSets) {
                if (facetSet.dims != numDims) {
                    throw IllegalArgumentException("All FacetSets must have the same number of dimensions. Expected $numDims found ${facetSet.dims}")
                }
                // It doesn't make sense to index facet sets of different types in the same field
                if (facetSet::class != expectedClass) {
                    throw IllegalArgumentException("All FacetSets must be the same type. Expected $expectedClass found ${facetSet::class}")
                }
                offset += facetSet.packValues(buf, offset)
            }
            return BytesRef(buf, 0, offset)
        }
    }
}
