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
package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.jdkport.assert

/**
 * A native int hash-based set where one value is reserved to mean "EMPTY" internally. The space
 * overhead is fairly low as there is only one power-of-two sized int[] to hold the values. The set
 * is re-hashed when adding a value that would make it >= 75% full. Consider extending and
 * over-riding [hash] if the values might be poor hash keys; Lucene docids should be
 * fine. The internal fields are exposed publicly to enable more efficient use at the expense of
 * better O-O principles.
 *
 *
 * To iterate over the integers held in this set, simply use code like this:
 *
 * ```
 * SentinelIntSet set = ...
 * for (int v : set.keys) {
 *   if (v == set.emptyVal)
 *     continue;
 *   //use v...
 * }
 * ```
 *
 * @lucene.internal
 */
open class SentinelIntSet(size: Int, val emptyVal: Int) {
    /** A power-of-2 over-sized array holding the integers in the set along with empty values. */
    var keys: IntArray

    var count: Int = 0

    /** the count at which a rehash should be done */
    var rehashCount: Int

    /**
     * @param size The minimum number of elements this set should be able to hold without rehashing
     *     (i.e. the slots are guaranteed not to change)
     * @param emptyVal The integer value to use for EMPTY
     */
    init {
        var tsize = kotlin.math.max(BitUtil.nextHighestPowerOfTwo(size), 1)
        rehashCount = tsize - (tsize shr 2)
        if (size >= rehashCount) { // should be able to hold "size" w/o re-hashing
            tsize = tsize shl 1
            rehashCount = tsize - (tsize shr 2)
        }
        keys = IntArray(tsize)
        if (emptyVal != 0) clear()
    }

    fun clear() {
        keys.fill(emptyVal)
        count = 0
    }

    /**
     * (internal) Return the hash for the key. The default implementation just returns the key, which
     * is not appropriate for general purpose use.
     */
    open fun hash(key: Int): Int {
        return key
    }

    /** The number of integers in this set. */
    fun size(): Int {
        return count
    }

    /** (internal) Returns the slot for this key */
    fun getSlot(key: Int): Int {
        assert(key != emptyVal)
        val h = hash(key)
        var s = h and (keys.size - 1)
        if (keys[s] == key || keys[s] == emptyVal) return s

        val increment = (h shr 7) or 1
        do {
            s = (s + increment) and (keys.size - 1)
        } while (keys[s] != key && keys[s] != emptyVal)
        return s
    }

    /** (internal) Returns the slot for this key, or -slot-1 if not found */
    fun find(key: Int): Int {
        assert(key != emptyVal)
        val h = hash(key)
        var s = h and (keys.size - 1)
        if (keys[s] == key) return s
        if (keys[s] == emptyVal) return -s - 1

        val increment = (h shr 7) or 1
        while (true) {
            s = (s + increment) and (keys.size - 1)
            if (keys[s] == key) return s
            if (keys[s] == emptyVal) return -s - 1
        }
    }

    /** Does this set contain the specified integer? */
    fun exists(key: Int): Boolean {
        return find(key) >= 0
    }

    /**
     * Puts this integer (key) in the set, and returns the slot index it was added to. It rehashes if
     * adding it would make the set more than 75% full.
     */
    fun put(key: Int): Int {
        var s = find(key)
        if (s < 0) {
            count++
            if (count >= rehashCount) {
                rehash()
                s = getSlot(key)
            } else {
                s = -s - 1
            }
            keys[s] = key
        }
        return s
    }

    /** (internal) Rehashes by doubling `int[] key` and filling with the old values. */
    fun rehash() {
        val newSize = keys.size shl 1
        val oldKeys = keys
        keys = IntArray(newSize)
        if (emptyVal != 0) {
            keys.fill(emptyVal)
        }

        for (key in oldKeys) {
            if (key == emptyVal) continue
            val newSlot = getSlot(key)
            keys[newSlot] = key
        }
        rehashCount = newSize - (newSize shr 2)
    }

    /** Return the memory footprint of this class in bytes. */
    fun ramBytesUsed(): Long {
        return RamUsageEstimator.alignObjectSize(
            Int.SIZE_BYTES * 3L + RamUsageEstimator.NUM_BYTES_OBJECT_REF
        ) + RamUsageEstimator.sizeOf(keys)
    }
}
