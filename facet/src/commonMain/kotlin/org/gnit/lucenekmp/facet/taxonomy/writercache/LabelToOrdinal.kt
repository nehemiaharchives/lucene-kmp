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
package org.gnit.lucenekmp.facet.taxonomy.writercache

import org.gnit.lucenekmp.facet.taxonomy.FacetLabel

/**
 * Abstract class for storing Label->Ordinal mappings in a taxonomy.
 *
 * @lucene.experimental
 */
abstract class LabelToOrdinal {
    /** How many ordinals we've seen. */
    protected var counter = 0

    /** Default constructor. */
    constructor()

    /** return the maximal Ordinal assigned so far */
    fun getMaxOrdinal(): Int = counter

    /**
   * Returns the next unassigned ordinal. The default behavior of this method is to simply increment
   * a counter.
   */
    fun getNextOrdinal(): Int = counter++

    /**
   * Adds a new label if it is not yet in the table. Throws an [IllegalArgumentException] if
   * the same label with a different ordinal was previously added to this table.
   */
    abstract fun addLabel(label: FacetLabel, ordinal: Int)

    /**
   * Returns the ordinal assigned to the given label, or [INVALID_ORDINAL] if the label
   * cannot be found in this table.
   */
    abstract fun getOrdinal(label: FacetLabel): Int

    companion object {
        /** Returned by [getOrdinal] when the label isn't recognized. */
        const val INVALID_ORDINAL = -2
    }
}
