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

import org.gnit.lucenekmp.util.ByteBlockPool.Companion.BYTE_BLOCK_SIZE
import org.gnit.lucenekmp.jdkport.assert

/**
 * Holds a sequence of string components, specifying the hierarchical name of a category.
 *
 * @lucene.internal
 */
open class FacetLabel : Comparable<FacetLabel> {
    companion object {
        /*
   * copied from DocumentWriterPerThread -- if a FacetLabel is resolved to a
   * drill-down term which is encoded to a larger term than that length, it is
   * silently dropped! Therefore, we limit the number of characters to MAX/4 to
   * be on the safe side.
   */
        /** The maximum number of characters a [FacetLabel] can have. */
        const val MAX_CATEGORY_PATH_LENGTH = (BYTE_BLOCK_SIZE - 2) / 4
    }

    /**
   * The components of this [FacetLabel]. Note that this array may be shared with other [FacetLabel] instances, e.g. as a result of [subpath], therefore you should traverse
   * the array up to [length] for this path's components.
   */
    val components: Array<out String>

    /** The number of components of this [FacetLabel]. */
    val length: Int

    // Used by sub-path
    private constructor(copyFrom: FacetLabel, prefixLen: Int) {
        // While the code which calls this method is safe, at some point a test
        // tripped on AIOOBE in toString, but we failed to reproduce. Adding
        // assert as a safety check.
        assert(prefixLen in 0..copyFrom.components.size) {
            "prefixLen cannot be negative nor larger than the given components' length: prefixLen=$prefixLen components.length=${copyFrom.components.size}"
        }
        this.components = copyFrom.components
        length = prefixLen
    }

    /** Construct from the given path components. */
    constructor(vararg components: String?) {
        // Keep the shared array; checkComponents rejects null elements before the constructor returns.
        @Suppress("UNCHECKED_CAST")
        this.components = components as Array<out String>
        length = components.size
        checkComponents()
    }

    /** Construct from the dimension plus the given path components. */
    constructor(dim: String?, path: Array<out String?>) {
        @Suppress("UNCHECKED_CAST")
        val components = arrayOfNulls<String>(1 + path.size) as Array<String>
        @Suppress("UNCHECKED_CAST")
        (components as Array<String?>)[0] = dim
        path.copyInto(components, 1, 0, path.size)
        this.components = components
        length = components.size
        checkComponents()
    }

    private fun checkComponents() {
        var len = 0L
        for (comp: String? in components) {
            if (comp.isNullOrEmpty()) {
                throw IllegalArgumentException("empty or null components not allowed: ${components.contentToString()}")
            }
            len += comp.length
        }
        len += components.size - 1 // add separators
        if (len > MAX_CATEGORY_PATH_LENGTH) {
            throw IllegalArgumentException("category path exceeds maximum allowed path length: max=$MAX_CATEGORY_PATH_LENGTH len=$len path=${components.contentToString().substring(0, 30)}...")
        }
    }

    /** Compares this path with another [FacetLabel] for lexicographic order. */
    override fun compareTo(other: FacetLabel): Int {
        val len = minOf(length, other.length)
        var i = 0
        var j = 0
        while (i < len) {
            val cmp = components[i].compareTo(other.components[j])
            if (cmp < 0) {
                return -1 // this is 'before'
            }
            if (cmp > 0) {
                return 1 // this is 'after'
            }
            i++
            j++
        }
        // one is a prefix of the other
        return length - other.length
    }

    override fun equals(other: Any?): Boolean {
        if (other !is FacetLabel) {
            return false
        }
        if (length != other.length) {
            return false // not same length, cannot be equal
        }
        // CategoryPaths are more likely to differ at the last components, so start
        // from last-first
        for (i in length - 1 downTo 0) {
            if (components[i] != other.components[i]) {
                return false
            }
        }
        return true
    }

    override fun hashCode(): Int {
        if (length == 0) {
            return 0
        }
        var hash = length
        for (i in 0 until length) {
            hash = hash * 31 + components[i].hashCode()
        }
        return hash
    }

    /**
   * Calculate a 64-bit hash function for this path. This is necessary for [NameHashIntCacheLRU] (the default cache impl for [LruTaxonomyWriterCache]) to reduce the
   * chance of "silent but deadly" collisions.
   */
    fun longHashCode(): Long {
        if (length == 0) {
            return 0
        }
        var hash = length.toLong()
        for (i in 0 until length) {
            hash = hash * 65599 + components[i].hashCode()
        }
        return hash
    }

    /** Returns a sub-path of this path up to `length` components. */
    fun subpath(length: Int): FacetLabel {
        if (length >= this.length || length < 0) {
            return this
        } else {
            return FacetLabel(this, length)
        }
    }

    /** Get the last component. */
    fun lastComponent(): String {
        if (components.isEmpty()) {
            throw UnsupportedOperationException("components is empty")
        }
        return components[components.size - 1]
    }

    /** Returns a string representation of the path. */
    override fun toString(): String {
        if (length == 0) {
            return "FacetLabel: []"
        }
        val parts = arrayOfNulls<String>(length)
        components.copyInto(parts, 0, 0, length)
        return "FacetLabel: ${parts.contentToString()}"
    }
}
