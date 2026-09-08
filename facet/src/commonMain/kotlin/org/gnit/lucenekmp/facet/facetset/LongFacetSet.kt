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
 * A [FacetSet] which encodes long dimension values.
 *
 * @lucene.experimental
 */
open class LongFacetSet : FacetSet {
    /** The raw dimension values of this facet set. */
    val values: LongArray

    /** Constructs a new instance of a facet set which stores `long` dimension values. */
    constructor(vararg values: Long) : super(validateValuesAndGetNumDims(*values)) {
        this.values = values
    }

    override fun getComparableValues(): LongArray = values

    companion object {
        private fun validateValuesAndGetNumDims(vararg values: Long): Int {
            if (values.isEmpty()) {
                throw IllegalArgumentException("values cannot be null or empty")
            }
            return values.size
        }
    }
}
