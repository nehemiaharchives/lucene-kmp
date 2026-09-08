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
import org.gnit.lucenekmp.util.BitUtil
import org.gnit.lucenekmp.util.BytesRef

/**
 * Add an instance of this to your [Document] to add a facet label associated with an int. Use
 * [TaxonomyFacetIntAssociations] to aggregate int values per facet label at search time.
 *
 * @lucene.experimental
 */
open class IntAssociationFacetField : AssociationFacetField {
    /** Creates this from `dim` and `path` and an int association */
    constructor(assoc: Int, dim: String?, vararg path: String?) : super(intToBytesRef(assoc), dim, *path)

    companion object {
        /** Encodes an `int` as a 4-byte [BytesRef], big-endian. */
        fun intToBytesRef(v: Int): BytesRef {
            val bytes = ByteArray(4)
            // big-endian:
            BitUtil.VH_BE_INT.set(bytes, 0, v)
            return BytesRef(bytes)
        }

        /** Decodes a previously encoded `int`. */
        fun bytesRefToInt(b: BytesRef): Int = BitUtil.VH_BE_INT.get(b.bytes, b.offset)
    }

    override fun toString(): String = "IntAssociationFacetField(dim=$dim path=${path.contentToString()} value=${bytesRefToInt(assoc)})"
}
