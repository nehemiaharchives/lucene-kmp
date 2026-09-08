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

import org.gnit.lucenekmp.document.IntPoint

/**
 * A [FacetSet] which encodes integer dimension values.
 *
 * @lucene.experimental
 */
open class IntFacetSet : FacetSet {
    /** The raw dimension values of this facet set. */
    val values: IntArray

    /** Constructs a new instance of a facet set which stores `int` dimension values. */
    constructor(vararg values: Int) : super(validateValuesAndGetNumDims(*values)) {
        this.values = values
    }

    override fun getComparableValues(): LongArray = LongArray(values.size) { values[it].toLong() }

    override fun packValues(buf: ByteArray, start: Int): Int {
        var i = 0
        var offset = start
        while (i < values.size) {
            IntPoint.encodeDimension(values[i], buf, offset)
            i++
            offset += Int.SIZE_BYTES
        }
        return values.size * Int.SIZE_BYTES
    }

    override fun sizePackedBytes(): Int = dims * Int.SIZE_BYTES

    companion object {
        private fun validateValuesAndGetNumDims(vararg values: Int): Int {
            if (values.isEmpty()) {
                throw IllegalArgumentException("values cannot be null or empty")
            }
            return values.size
        }
    }
}
