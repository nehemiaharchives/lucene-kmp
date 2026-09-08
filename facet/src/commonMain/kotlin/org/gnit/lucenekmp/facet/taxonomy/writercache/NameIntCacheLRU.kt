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
 * An LRU cache of mapping from name to int. Used to cache Ordinals of category paths.
 *
 * @lucene.experimental
 */
// Note: Nothing in this class is synchronized. The caller is assumed to be
// synchronized so that no two methods of this class are called concurrently.
open class NameIntCacheLRU internal constructor(private val maxCacheSize: Int) {
    private lateinit var cache: MutableMap<Any, Int>
    internal var nMisses = 0L // for debug
    internal var nHits = 0L // for debug
    init { createCache(maxCacheSize) }

    /** Maximum number of cache entries before eviction. */
    fun getMaxSize(): Int = maxCacheSize
    /** Number of entries currently in the cache. */
    fun getSize(): Int = cache.size

    private fun createCache(maxSize: Int) {
        if (maxSize < Int.MAX_VALUE) {
            cache = LinkedHashMap(1000, 0.7f) // for LRU
        } else {
            cache = HashMap(1000, 0.7f) // no need for LRU
        }
    }

    internal fun get(name: FacetLabel): Int? {
        val res = cache[key(name)]
        if (res == null) {
            nMisses++
        } else {
            // Kotlin common LinkedHashMap lacks access-order mode; move the accessed entry to the end.
            if (maxCacheSize < Int.MAX_VALUE) {
                cache.remove(key(name))
                cache[key(name)] = res
            }
            nHits++
        }
        return res
    }

    /** Subclasses can override this to provide caching by e.g. hash of the string. */
    internal open fun key(name: FacetLabel): Any = name
    internal open fun key(name: FacetLabel, prefixLen: Int): Any = name.subpath(prefixLen)

    /** Add a new value to cache. Return true if cache became full and some room need to be made. */
    internal fun put(name: FacetLabel, `val`: Int): Boolean {
        if (maxCacheSize < Int.MAX_VALUE) cache.remove(key(name))
        cache[key(name)] = `val`
        return isCacheFull()
    }

    internal fun put(name: FacetLabel, prefixLen: Int, `val`: Int): Boolean {
        if (maxCacheSize < Int.MAX_VALUE) cache.remove(key(name, prefixLen))
        cache[key(name, prefixLen)] = `val`
        return isCacheFull()
    }

    private fun isCacheFull(): Boolean = cache.size > maxCacheSize
    internal fun clear() { cache.clear() }
    internal fun stats(): String = "#miss=$nMisses #hit=$nHits"

    /**
   * If cache is full remove least recently used entries from cache. Return true if anything was
   * removed, false otherwise.
   *
   * See comment in DirectoryTaxonomyWriter.addToCache(CategoryPath, int) for an explanation why
   * we clean 1/3rd of the cache, and not just one entry.
   */
    internal fun makeRoomLRU(): Boolean {
        if (!isCacheFull()) return false
        val n = cache.size - ((2L * maxCacheSize) / 3).toInt()
        if (n <= 0) return false
        val it = cache.keys.iterator()
        var i = 0
        while (i < n && it.hasNext()) {
            it.next()
            it.remove()
            i++
        }
        return true
    }
}
