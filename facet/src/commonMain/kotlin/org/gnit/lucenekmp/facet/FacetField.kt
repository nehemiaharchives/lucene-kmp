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

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType

/**
 * Add an instance of this to your [Document] for every facet label.
 *
 * **NOTE:** you must call [FacetsConfig.build] before you add the document to
 * IndexWriter.
 */
open class FacetField : Field {
    companion object {
        /**
   * Field type used for storing facet values. Actual field type used for indexing is determined in
   * [FacetsConfig.build]
   */
        val TYPE = FieldType()

        /**
   * Verifies the label is not null or empty string.
   *
   * @lucene.internal
   */
        fun verifyLabel(label: String?) {
            if (label.isNullOrEmpty()) {
                throw IllegalArgumentException("empty or null components not allowed; got: $label")
            }
        }
    }

    /** Dimension for this field. */
    val dim: String

    /** Path for this field. */
    val path: Array<out String>

    /** Creates this from `dim` and `path`. */
    constructor(dim: String?, vararg path: String?) : super("dummy", TYPE) {
        verifyLabel(dim)
        for (label in path) {
            verifyLabel(label)
        }
        this.dim = dim!!
        if (path.isEmpty()) {
            throw IllegalArgumentException("path must have at least one element")
        }
        @Suppress("UNCHECKED_CAST")
        this.path = path as Array<out String>
    }

    override fun toString(): String = "FacetField(dim=$dim path=${path.contentToString()})"
}
