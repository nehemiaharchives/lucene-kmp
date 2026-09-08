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
 * A [FacetSetMatcher] which considers a set as a match only if all dimension values are equal
 * to the given one.
 *
 * @lucene.experimental
 */
open class ExactFacetSetMatcher : FacetSetMatcher {
    private val values: LongArray

    /** Constructs an instance to match the given facet set. */
    constructor(label: String?, facetSet: FacetSet) : super(label, facetSet.dims) {
        this.values = facetSet.getComparableValues()
    }

    override fun matches(dimValues: LongArray): Boolean {
        assert(dimValues.size == dims) {
            "Encoded dimensions (dims=${dimValues.size}) is incompatible with FacetSet dimensions (dims=$dims)"
        }
        return dimValues.contentEquals(values)
    }
}
