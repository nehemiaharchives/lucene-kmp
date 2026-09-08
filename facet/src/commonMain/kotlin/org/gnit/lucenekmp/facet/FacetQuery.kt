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

import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.TermQuery

/**
 * A term [Query] over a [FacetField].
 *
 * **NOTE:**This helper class is an alternative to [DrillDownQuery] especially in cases
 * where you don't intend to use [DrillSideways]
 *
 * @lucene.experimental
 */
open class FacetQuery : TermQuery {
    /** Creates a new `FacetQuery` filtering the query on the given dimension. */
    constructor(facetsConfig: FacetsConfig, dimension: String, vararg path: String) :
        super(toTerm(facetsConfig.getDimConfig(dimension), dimension, *path))

    /**
   * Creates a new `FacetQuery` filtering the query on the given dimension.
   *
   * **NOTE:**Uses FacetsConfig.DEFAULT_DIM_CONFIG.
   */
    constructor(dimension: String, vararg path: String) : super(toTerm(FacetsConfig.DEFAULT_DIM_CONFIG, dimension, *path))

    companion object {
        internal fun toTerm(dimConfig: FacetsConfig.DimConfig, dimension: String, vararg path: String): Term =
            Term(dimConfig.indexFieldName, FacetsConfig.pathToString(dimension, *path))
    }
}
