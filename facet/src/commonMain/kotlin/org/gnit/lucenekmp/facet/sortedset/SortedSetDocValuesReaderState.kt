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

import org.gnit.lucenekmp.facet.FacetsConfig
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.SortedSetDocValues
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.jdkport.PrimitiveIterator
import org.gnit.lucenekmp.jdkport.assert

/**
 * Wraps a [IndexReader] and resolves ords using existing [SortedSetDocValues] APIs
 * without a separate taxonomy index. This makes faceting a bit slower, adds some cost at reopen
 * time, but avoids managing the separate taxonomy index. In addition, the tie-break during faceting
 * is now meaningful (in label sorted order).
 *
 * **NOTE**: creating an instance of this class is somewhat costly, as it computes per-segment
 * ordinal maps, so you should create it once and re-use that one instance for a given [IndexReader].
 */
abstract class SortedSetDocValuesReaderState /** Sole constructor. */ protected constructor() : Accountable {
    /**
   * Holds start/end range of ords, which maps to one dimension. Only used for flat hierarchies.
   *
   * @param start Start of range, inclusive:
   * @param end End of range, inclusive:
   */
    data class OrdRange(val start: Int, val end: Int) {
        /** Iterates from start to end ord (inclusive) */
        fun iterator(): PrimitiveIterator.OfInt = object : PrimitiveIterator.OfInt {
            override fun remove(): Unit = throw UnsupportedOperationException()
            var current = start

            override fun nextInt(): Int {
                if (current > end) {
                    return INVALID_ORDINAL
                }
                return current++
            }

            override fun hasNext(): Boolean = current <= end
        }
    }

    /**
   * Holds children and sibling information for a single dimension. Only used with hierarchical
   * dimensions.
   */
    class DimTree(dimStartOrd: Int, sibling: List<Int>, hasChildren: List<Boolean>) {
        private val hasChildren: FixedBitSet
        // TODO: This array can take up a lot of space. Change type based on input size maybe?
        private val siblings: IntArray

        /** The first ord of the dimension */
        val dimStartOrd: Int

        /** Sibling and children must be of same length */
        init {
            if (sibling.size != hasChildren.size) {
                throw IllegalArgumentException(
                    "Sibling list and children list must have the same size. Got sibling list size of " +
                        sibling.size + " and child list size of " + hasChildren.size)
            }
            this.hasChildren = FixedBitSet(hasChildren.size)
            this.siblings = IntArray(sibling.size)
            for (i in sibling.indices) {
                if (hasChildren[i]) {
                    assert(i < sibling.size - 1)
                    this.hasChildren.set(i)
                }
                assert(this.siblings[i] < sibling.size)
                this.siblings[i] = sibling[i]
            }
            this.dimStartOrd = dimStartOrd
        }

        /** Iterates through all first level children of dimension */
        fun iterator(): PrimitiveIterator.OfInt = iterator(dimStartOrd)

        /** Iterates through all children of given pathOrd */
        fun iterator(pathOrd: Int): PrimitiveIterator.OfInt = object : PrimitiveIterator.OfInt {
            override fun remove(): Unit = throw UnsupportedOperationException()
            var atStart = true
            var currentOrd = pathOrd - dimStartOrd

            override fun nextInt(): Int {
                if (atStart) {
                    if (currentOrd < 0 || currentOrd >= hasChildren.length()) {
                        return INVALID_ORDINAL
                    }
                    atStart = false
                    if (hasChildren.get(currentOrd)) {
                        currentOrd++
                        return currentOrd + dimStartOrd
                    } else {
                        return INVALID_ORDINAL
                    }
                } else {
                    currentOrd = siblings[currentOrd]
                    return currentOrd + dimStartOrd
                }
            }

            override fun hasNext(): Boolean {
                if (atStart) {
                    if (currentOrd < 0 || currentOrd >= hasChildren.length()) {
                        return false
                    }
                    return hasChildren.get(currentOrd)
                } else {
                    return siblings[currentOrd] != INVALID_ORDINAL
                }
            }
        }
    }

    companion object {
        /** Invalid ordinal const */
        const val INVALID_ORDINAL = -1
    }

    /** Return top-level doc values. */
    abstract fun getDocValues(): SortedSetDocValues?

    /** Indexed field we are reading. */
    abstract val field: String

    /** Returns top-level index reader. */
    abstract val reader: IndexReader

    /** Number of unique labels. */
    abstract val size: Int

    /** Returns the associated facet config. */
    abstract val facetsConfig: FacetsConfig

    /* Only used for flat facets (dim/value) */

    /** Returns the [OrdRange] for this dimension. */
    abstract fun getOrdRange(dim: String): OrdRange?

    /** Returns mapping from prefix to [OrdRange]. */
    abstract fun getPrefixToOrdRange(): Map<String, OrdRange>

    /* Only used for hierarchical facets */

    /** Returns mapping from prefix to [DimTree] */
    abstract fun getDimTree(dim: String): DimTree?

    /** Returns a list of all dimensions */
    abstract fun getDims(): Iterable<String>
}
