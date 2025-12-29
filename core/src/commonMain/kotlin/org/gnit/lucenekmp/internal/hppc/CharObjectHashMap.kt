package org.gnit.lucenekmp.internal.hppc

import org.gnit.lucenekmp.jdkport.Cloneable
import org.gnit.lucenekmp.util.Accountable

/**
 * A hash map of `char` to `Object`. Minimal port for analysis usage.
 * Uses Kotlin's HashMap internally.
 */
class CharObjectHashMap<VType> : Iterable<CharObjectHashMap.CharObjectCursor<VType>>, Accountable,
    Cloneable<CharObjectHashMap<VType>> {

    private val map = HashMap<Char, VType?>()

    fun put(key: Char, value: VType?): VType? = map.put(key, value)

    fun putIfAbsent(key: Char, value: VType?): Boolean {
        return if (!map.containsKey(key)) {
            map[key] = value
            true
        } else {
            false
        }
    }

    fun get(key: Char): VType? = map[key]

    fun remove(key: Char): VType? = map.remove(key)

    fun containsKey(key: Char): Boolean = map.containsKey(key)

    fun size(): Int = map.size

    fun isEmpty(): Boolean = map.isEmpty()

    fun clear() {
        map.clear()
    }

    override fun iterator(): MutableIterator<CharObjectCursor<VType>> {
        val it = map.entries.iterator()
        return object : MutableIterator<CharObjectCursor<VType>> {
            override fun hasNext(): Boolean = it.hasNext()

            override fun next(): CharObjectCursor<VType> {
                val entry = it.next()
                return CharObjectCursor(entry.key, entry.value)
            }

            override fun remove() {
                it.remove()
            }
        }
    }

    override fun clone(): CharObjectHashMap<VType> {
        val copy = CharObjectHashMap<VType>()
        copy.map.putAll(map)
        return copy
    }

    override fun ramBytesUsed(): Long = 0

    data class CharObjectCursor<VType>(var key: Char, var value: VType?)
}
