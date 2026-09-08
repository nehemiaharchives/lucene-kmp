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
import org.gnit.lucenekmp.util.BytesRef

/**
 * Add an instance of this to your [Document] to add a facet label associated with a float.
 * Use [TaxonomyFacetFloatAssociations] to aggregate float values per facet label at search
 * time.
 *
 * @lucene.experimental
 */
open class FloatAssociationFacetField : AssociationFacetField {
    /** Creates this from `dim` and `path` and a float association */
    constructor(assoc: Float, dim: String?, vararg path: String?) : super(floatToBytesRef(assoc), dim, *path)

    companion object {
        /** Encodes a `float` as a 4-byte [BytesRef]. */
        fun floatToBytesRef(v: Float): BytesRef = IntAssociationFacetField.intToBytesRef(v.toBits())

        /** Decodes a previously encoded `float`. */
        fun bytesRefToFloat(b: BytesRef): Float = Float.fromBits(IntAssociationFacetField.bytesRefToInt(b))
    }

    override fun toString(): String = "FloatAssociationFacetField(dim=$dim path=${path.contentToString()} value=${bytesRefToFloat(assoc)})"
}
