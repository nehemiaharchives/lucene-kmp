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

/**
 * Matches the encoded [FacetSet] that was indexed in [FacetSetsField].
 *
 * @lucene.experimental
 */
abstract class FacetSetMatcher {
    /** The label to associate to this matcher's aggregated value. */
    val label: String

    /** The number of dimensions that are matched by this matcher. */
    val dims: Int

    /**
   * Constructs a new matcher.
   *
   * @param label the label to associate to this matcher's aggregated value
   * @param dims the number of dimensions that are matched by this matcher
   */
    protected constructor(label: String?, dims: Int) {
        if (label == null) {
            throw IllegalArgumentException("label cannot be null")
        }
        if (dims <= 0) {
            throw IllegalArgumentException("dims must be greater than 0: $dims")
        }
        this.label = label
        this.dims = dims
    }

    /**
   * Returns true if the facet set encoded in the given `long[]` is matched by this matcher.
   *
   * @param dimValues the encoded dimension values of a single facet set in this field
   */
    abstract fun matches(dimValues: LongArray): Boolean
}
