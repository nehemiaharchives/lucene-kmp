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
package org.gnit.lucenekmp.facet

/** Maps specified dims to provided Facets impls; else, uses the default Facets impl. */
open class MultiFacets : Facets {
    private val dimToFacets: Map<String, Facets>
    private val defaultFacets: Facets?

    /** Create this, with no default [Facets]. */
    constructor(dimToFacets: Map<String, Facets>) : this(dimToFacets, null)

    /**
   * Create this, with the specified default [Facets] for fields not included in `* dimToFacets`.
   */
    constructor(dimToFacets: Map<String, Facets>, defaultFacets: Facets?) {
        this.dimToFacets = dimToFacets
        this.defaultFacets = defaultFacets
    }
    override fun getAllChildren(dim: String, vararg path: String): FacetResult? {
        var facets = dimToFacets[dim]
        if (facets == null) {
            if (defaultFacets == null) {
                throw IllegalArgumentException("invalid dim \"$dim\"")
            }
            facets = defaultFacets
        }
        return facets.getAllChildren(dim, *path)
    }

    override fun getTopChildren(topN: Int, dim: String, vararg path: String): FacetResult? {
        validateTopN(topN)
        var facets = dimToFacets[dim]
        if (facets == null) {
            if (defaultFacets == null) {
                throw IllegalArgumentException("invalid dim \"$dim\"")
            }
            facets = defaultFacets
        }
        return facets.getTopChildren(topN, dim, *path)
    }

    override fun getSpecificValue(dim: String, vararg path: String): Number {
        var facets = dimToFacets[dim]
        if (facets == null) {
            if (defaultFacets == null) {
                throw IllegalArgumentException("invalid dim \"$dim\"")
            }
            facets = defaultFacets
        }
        return facets.getSpecificValue(dim, *path)
    }

    override fun getAllDims(topN: Int): List<FacetResult> {
        validateTopN(topN)
        val results = mutableListOf<FacetResult>()
        // First add the specific dim's facets:
        for (ent in dimToFacets.entries) {
            results.add(ent.value.getTopChildren(topN, ent.key)!!)
        }
        if (defaultFacets != null) {
            // Then add all default facets as long as we didn't
            // already add that dim:
            for (result in defaultFacets.getAllDims(topN)) {
                if (!dimToFacets.containsKey(result.dim)) {
                    results.add(result)
                }
            }
        }
        return results
    }
}
