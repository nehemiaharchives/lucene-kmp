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

import org.gnit.lucenekmp.document.LongPoint

/**
 * Holds a set of facet dimension values.
 *
 * @lucene.experimental
 */
abstract class FacetSet /** Constructs a new instance of a facet set with the given number of dimensions. */ protected constructor(
    /** The number of dimension values in this set. */
    val dims: Int
) {
    /** Returns the dimension values in this facet set as comparable longs. */
    abstract fun getComparableValues(): LongArray

    /**
   * Packs the dimension values into the given `byte[]` and returns the number of
   * packed-values bytes. The default implementation packs the [getComparableValues
   * comparable values], and you can override to implement your own scheme.
   */
    open fun packValues(buf: ByteArray, start: Int): Int {
        val comparableValues = getComparableValues()
        var i = 0
        var offset = start
        while (i < comparableValues.size) {
            LongPoint.encodeDimension(comparableValues[i], buf, offset)
            i++
            offset += Long.SIZE_BYTES
        }
        return comparableValues.size * Long.SIZE_BYTES
    }

    /**
   * Returns the size of the packed values in this facet set. If the value is unknown in advance
   * (e.g. if the values are compressed), this method can return an upper limit. The default
   * implementations returns `dims * Long.BYTES` per the default implementation of [packValues]. You should override if you implement [packValues] differently.
   */
    open fun sizePackedBytes(): Int = dims * Long.SIZE_BYTES
}
