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

import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.TermInSetQuery
import org.gnit.lucenekmp.util.BytesRef

/**
 * A multi-terms [Query] over a [FacetField].
 *
 * **NOTE:**This helper class is an alternative to [DrillDownQuery] especially in cases
 * where you don't intend to use [DrillSideways]
 *
 * @lucene.experimental
 * @see org.gnit.lucenekmp.search.TermInSetQuery
 */
open class MultiFacetQuery : TermInSetQuery {
    /** Creates a new `MultiFacetQuery` filtering the query on the given dimension. */
    constructor(facetsConfig: FacetsConfig, dimension: String, vararg paths: Array<String>) :
        super(facetsConfig.getDimConfig(dimension).indexFieldName, toTerms(dimension, *paths))

    /**
   * Creates a new `MultiFacetQuery` filtering the query on the given dimension.
   *
   * **NOTE:**Uses FacetsConfig.DEFAULT_DIM_CONFIG.
   */
    constructor(dimension: String, vararg paths: Array<String>) : super(FacetsConfig.DEFAULT_DIM_CONFIG.indexFieldName, toTerms(dimension, *paths))

    companion object {
        internal fun toTerms(dimension: String, vararg paths: Array<String>): MutableCollection<BytesRef> {
            val terms = ArrayList<BytesRef>(paths.size)
            for (path in paths) {
                terms.add(BytesRef(FacetsConfig.pathToString(dimension, *path)))
            }
            return terms
        }
    }
}
