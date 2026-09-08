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

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.facet.FacetField
import org.gnit.lucenekmp.facet.Facets
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.util.BytesRef

/**
 * Add an instance of this to your [Document] to add a facet label associated with an
 * arbitrary byte[]. This will require a custom [Facets] implementation at search time; see
 * [IntAssociationFacetField] and [FloatAssociationFacetField] to use existing [Facets] implementations.
 *
 * @lucene.experimental
 */
open class AssociationFacetField : Field {
    companion object {
        /** Indexed [FieldType]. */
        val TYPE = FieldType().apply {
            setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS)
            freeze()
        }
    }

    /** Dimension for this field. */
    val dim: String

    /** Facet path for this field. */
    val path: Array<out String>

    /** Associated value. */
    val assoc: BytesRef

    /** Creates this from `dim` and `path` and an association */
    constructor(assoc: BytesRef, dim: String?, vararg path: String?) : super("dummy", TYPE) {
        FacetField.verifyLabel(dim)
        for (label in path) {
            FacetField.verifyLabel(label)
        }
        this.dim = dim!!
        this.assoc = assoc
        if (path.isEmpty()) {
            throw IllegalArgumentException("path must have at least one element")
        }
        @Suppress("UNCHECKED_CAST")
        this.path = path as Array<out String>
    }

    override fun toString(): String = "AssociationFacetField(dim=$dim path=${path.contentToString()} bytes=$assoc)"
}
