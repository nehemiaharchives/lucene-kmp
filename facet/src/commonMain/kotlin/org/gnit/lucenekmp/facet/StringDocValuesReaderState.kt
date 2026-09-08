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

import org.gnit.lucenekmp.index.DocValues
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.OrdinalMap
import org.gnit.lucenekmp.util.packed.PackedInts

/**
 * Stores an [OrdinalMap] created for a specific [IndexReader] (`reader`) + `* field`. Enables re-use of the `ordinalMap` once created since creation is costly.
 *
 * Note: It's important that callers confirm the ordinal map is still valid for their cases.
 * Specifically, callers should confirm that the reader used to create the map (`reader`)
 * matches their use-case.
 */
class StringDocValuesReaderState
/**
   * Construct state specific to a reader + field. This builds an [OrdinalMap] that can be
   * reused for mapping segment-specific ordinals to global ordinals for the given field. Keep in
   * mind that the state is only valid for the specified [IndexReader], so opening new readers
   * (e.g., to pickup NRT updates) requires constructing a new state instance.
   */
constructor(internal val reader: IndexReader, internal val field: String) {
    internal val ordinalMap: OrdinalMap? = buildOrdinalMap(reader, field)

    companion object {
        private fun buildOrdinalMap(reader: IndexReader, field: String): OrdinalMap? {
            val leaves = reader.leaves()
            val leafCount = leaves.size

            if (leafCount <= 1) {
                return null
            }

            val docValues = Array(leafCount) { i ->
                val context = reader.leaves()[i]
                DocValues.getSortedSet(context.reader(), field)
            }

            val cacheHelper = reader.readerCacheHelper
            val owner = cacheHelper?.key

            return OrdinalMap.build(owner, docValues, PackedInts.DEFAULT)
        }
    }

    override fun toString(): String {
        return "StringDocValuesReaderState(field=$field reader=$reader)"
    }
}
