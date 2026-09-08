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

import org.gnit.lucenekmp.jdkport.Cloneable

/**
 * LRUHashMap is an extension of Java's HashMap, which has a bounded size(); When it reaches that
 * size, each time a new element is added, the least recently used (LRU) entry is removed.
 *
 * Java makes it very easy to implement LRUHashMap - all its functionality is already available
 * from [java.util.LinkedHashMap], and we just need to configure that properly.
 *
 * Note that like HashMap, LRUHashMap is unsynchronized, and the user MUST synchronize the access
 * to it if used from several threads. Moreover, while with HashMap this is only a concern if one of
 * the threads is modifies the map, with LURHashMap every read is a modification (because the LRU
 * order needs to be remembered) so proper synchronization is always necessary.
 *
 * With the usual synchronization mechanisms available to the user, this unfortunately means that
 * LRUHashMap will probably perform sub-optimally under heavy contention: while one thread uses the
 * hash table (reads or writes), any other thread will be blocked from using it - or even just
 * starting to use it (e.g., calculating the hash function). A more efficient approach would be not
 * to use LinkedHashMap at all, but rather to use a non-locking (as much as possible) thread-safe
 * solution, something along the lines of java.util.concurrent.ConcurrentHashMap (though that
 * particular class does not support the additional LRU semantics, which will need to be added
 * separately using a concurrent linked list or additional storage of timestamps (in an array or
 * inside the entry objects), or whatever).
 *
 * @lucene.experimental
 */
open class LRUHashMap<K, V>
/**
   * Create a new hash map with a bounded size and with least recently used entries removed.
   *
   * @param maxSize the maximum size (in number of entries) to which the map can grow before the
   *     least recently used entries start being removed.<br>
   *     Setting maxSize to a very large value, like [Integer.MAX_VALUE] is allowed, but is
   *     less efficient than using [java.util.HashMap] because our class needs to keep track
   *     of the use order (via an additional doubly-linked list) which is not used when the map's
   *     size is always below the maximum size.
   */
constructor(private var maxSize: Int) : AbstractMutableMap<K, V>(), Cloneable<LRUHashMap<K, V>> {
    // Kotlin common LinkedHashMap has insertion order only; implement Java access order here.
    private val map = LinkedHashMap<K, V>(16, 0.75f)
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> get() = map.entries

    override fun get(key: K): V? {
        if (!map.containsKey(key)) return null
        @Suppress("UNCHECKED_CAST")
        val value = map.remove(key) as V
        map[key] = value
        return value
    }

    override fun containsKey(key: K): Boolean = map.containsKey(key)

    override fun put(key: K, value: V): V? {
        val present = map.containsKey(key)
        val previous = map.remove(key)
        map[key] = value
        if (!present && removeEldestEntry(map.entries.first())) {
            val iterator = map.entries.iterator()
            iterator.next()
            iterator.remove()
        }
        return previous
    }

    /** Return the max size */
    fun getMaxSize(): Int = maxSize

    /**
   * setMaxSize() allows changing the map's maximal number of elements which was defined at
   * construction time.
   *
   * Note that if the map is already larger than maxSize, the current implementation does not
   * shrink it (by removing the oldest elements); Rather, the map remains in its current size as new
   * elements are added, and will only start shrinking (until settling again on the give maxSize) if
   * existing elements are explicitly deleted.
   */
    fun setMaxSize(maxSize: Int) { this.maxSize = maxSize }

    // We override LinkedHashMap's removeEldestEntry() method. This method
    // is called every time a new entry is added, and if we return true
    // here, the eldest element will be deleted automatically. In our case,
    // we return true if the size of the map grew beyond our limit - ignoring
    // what is that eldest element that we'll be deleting.
    protected open fun removeEldestEntry(eldest: Map.Entry<K, V>): Boolean = size > maxSize

    public override fun clone(): LRUHashMap<K, V> {
        val clone = LRUHashMap<K, V>(maxSize)
        clone.map.putAll(map)
        return clone
    }
}
