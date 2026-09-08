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
package org.gnit.lucenekmp.facet.sortedset

import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.facet.FacetField
import org.gnit.lucenekmp.facet.FacetsConfig
import org.gnit.lucenekmp.index.IndexOptions

/**
 * Add an instance of this to your Document for every facet label to be indexed via
 * SortedSetDocValues.
 */
open class SortedSetDocValuesFacetField : Field {
    companion object {
        /** Indexed [FieldType]. */
        val TYPE = FieldType().apply {
            // NOTE: we don't actually use these index options, because this field is "processed" by
            // FacetsConfig.build()
            setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS)
            freeze()
        }
    }
    /** Dimension. */
    val dim: String
    /** Path. */
    val path: Array<out String>
    /** Sole constructor. */
    constructor(dim: String?, vararg path: String?) : super("dummy", TYPE) {
        for (label in path) {
            FacetField.verifyLabel(label)
        }
        FacetField.verifyLabel(dim)
        if (path.isEmpty()) {
            throw IllegalArgumentException("path must have at least one element")
        }
        this.dim = dim!!
        @Suppress("UNCHECKED_CAST")
        this.path = path as Array<out String>
    }
    override fun toString(): String = "SortedSetDocValuesFacetField(dim=$dim path=${FacetsConfig.pathToString(path)})"
}
