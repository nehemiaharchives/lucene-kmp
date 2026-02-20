package org.gnit.lucenekmp.internal.hppc

import org.gnit.lucenekmp.internal.hppc.HashContainers.DEFAULT_EXPECTED_ELEMENTS
import org.gnit.lucenekmp.internal.hppc.HashContainers.DEFAULT_LOAD_FACTOR
import org.gnit.lucenekmp.internal.hppc.HashContainers.ITERATION_SEED
import org.gnit.lucenekmp.internal.hppc.HashContainers.MAX_LOAD_FACTOR
import org.gnit.lucenekmp.internal.hppc.HashContainers.MIN_LOAD_FACTOR
import org.gnit.lucenekmp.internal.hppc.HashContainers.checkLoadFactor
import org.gnit.lucenekmp.internal.hppc.HashContainers.checkPowerOfTwo
import org.gnit.lucenekmp.internal.hppc.HashContainers.expandAtCount
import org.gnit.lucenekmp.internal.hppc.HashContainers.iterationIncrement
import org.gnit.lucenekmp.internal.hppc.HashContainers.minBufferSize
import org.gnit.lucenekmp.internal.hppc.HashContainers.nextBufferSize
import org.gnit.lucenekmp.jdkport.Cloneable
import org.gnit.lucenekmp.jdkport.bitCount
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.RamUsageEstimator
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.jvm.JvmOverloads
import kotlin.reflect.cast

/**
 * A hash map of `char` to `Object`, implemented using open addressing with
 * linear probing for collision resolution. Supports null values.
 *
 *
 * Mostly forked and trimmed from com.carrotsearch.hppc.CharObjectHashMap
 *
 *
 * github: https://github.com/carrotsearch/hppc release 0.10.0
 *
 * @lucene.internal
 */
@OptIn(ExperimentalAtomicApi::class)
open class CharObjectHashMap<VType>
@JvmOverloads constructor(expectedElements: Int, loadFactor: Double = DEFAULT_LOAD_FACTOR.toDouble()) :
    Iterable<CharObjectHashMap.CharObjectCursor<VType>>, Accountable, Cloneable<CharObjectHashMap<VType>> {
    /** The array holding keys.  */
    var keys: CharArray? = null

    /** The array holding values.  */
    var values: Array<Any?>? = null

    /**
     * The number of stored keys (assigned key slots), excluding the special "empty" key, if any (use
     * [.size] instead).
     *
     * @see .size
     */
    var assigned: Int = 0

    /** Mask for slot scans in [.keys].  */
    var mask: Int = 0

    /** Expand (rehash) [.keys] when [.assigned] hits this value.  */
    protected var resizeAt: Int = 0

    /** Special treatment for the "empty slot" key marker.  */
    var hasEmptyKey: Boolean = false

    /** The load factor for [.keys].  */
    protected var loadFactor: Double = 0.0

    /** Seed used to ensure the hash iteration order is different from an iteration to another.  */
    protected var iterationSeed: Int = 0

    /** New instance with sane defaults.  */
    constructor() : this(DEFAULT_EXPECTED_ELEMENTS)

    init {
        this.loadFactor = verifyLoadFactor(loadFactor)
        iterationSeed = ITERATION_SEED.addAndFetch(1)
        ensureCapacity(expectedElements)
    }

    /** Create a hash map from all key-value pairs of another map.  */
    constructor(map: CharObjectHashMap<VType?>) : this(map.size()) {
        putAll(map)
    }

    fun put(key: Char, value: VType?): VType? {
        require(assigned < mask + 1)

        val mask = this.mask
        if (key == EMPTY_KEY) {
            val previousValue = if (hasEmptyKey) values!![mask + 1] as VType? else null
            hasEmptyKey = true
            values!![mask + 1] = value
            return previousValue
        } else {
            val keys = this.keys
            var slot = hashKey(key) and mask

            var existing: Char
            while ((keys!![slot].also { existing = it }) != EMPTY_KEY) {
                if (existing == key) {
                    val previousValue = values!![slot] as VType?
                    values!![slot] = value
                    return previousValue
                }
                slot = (slot + 1) and mask
            }

            if (assigned == resizeAt) {
                allocateThenInsertThenRehash(slot, key, value)
            } else {
                keys[slot] = key
                values!![slot] = value
            }

            assigned++
            return null
        }
    }

    fun putAll(iterable: Iterable<out CharObjectCursor<out VType?>>): Int {
        val count = size()
        for (c in iterable) {
            put(c.key, c.value)
        }
        return size() - count
    }

    fun putIfAbsent(key: Char, value: VType?): Boolean {
        val keyIndex = indexOf(key)
        if (!indexExists(keyIndex)) {
            indexInsert(keyIndex, key, value)
            return true
        }
        return false
    }

    fun remove(key: Char): VType? {
        val mask = this.mask
        if (key == EMPTY_KEY) {
            if (!hasEmptyKey) {
                return null
            }
            hasEmptyKey = false
            val previousValue = values!![mask + 1] as VType?
            values!![mask + 1] = 0
            return previousValue
        } else {
            val keys = this.keys
            var slot = hashKey(key) and mask

            var existing: Char
            while ((keys!![slot].also { existing = it }) != EMPTY_KEY) {
                if (existing == key) {
                    val previousValue = values!![slot] as VType?
                    shiftConflictingKeys(slot)
                    return previousValue
                }
                slot = (slot + 1) and mask
            }

            return null
        }
    }

    operator fun get(key: Char): VType? {
        if (key == EMPTY_KEY) {
            return if (hasEmptyKey) values!![mask + 1] as VType? else null
        } else {
            val keys = this.keys
            val mask = this.mask
            var slot = hashKey(key) and mask

            var existing: Char
            while ((keys!![slot].also { existing = it }) != EMPTY_KEY) {
                if (existing == key) {
                    return values!![slot] as VType?
                }
                slot = (slot + 1) and mask
            }

            return null
        }
    }

    fun getOrDefault(key: Char, defaultValue: VType?): VType? {
        if (key == EMPTY_KEY) {
            return if (hasEmptyKey) values!![mask + 1] as VType? else defaultValue
        } else {
            val keys = this.keys
            val mask = this.mask
            var slot = hashKey(key) and mask

            var existing: Char
            while ((keys!![slot].also { existing = it }) != EMPTY_KEY) {
                if (existing == key) {
                    return values!![slot] as VType?
                }
                slot = (slot + 1) and mask
            }

            return defaultValue
        }
    }

    fun containsKey(key: Char): Boolean {
        if (key == EMPTY_KEY) {
            return hasEmptyKey
        } else {
            val keys = this.keys
            val mask = this.mask
            var slot = hashKey(key) and mask

            var existing: Char
            while ((keys!![slot].also { existing = it }) != EMPTY_KEY) {
                if (existing == key) {
                    return true
                }
                slot = (slot + 1) and mask
            }

            return false
        }
    }

    fun indexOf(key: Char): Int {
        val mask = this.mask
        if (key == EMPTY_KEY) {
            return if (hasEmptyKey) mask + 1 else (mask + 1).inv()
        } else {
            val keys = this.keys
            var slot = hashKey(key) and mask

            var existing: Char
            while ((keys!![slot].also { existing = it }) != EMPTY_KEY) {
                if (existing == key) {
                    return slot
                }
                slot = (slot + 1) and mask
            }

            return slot.inv()
        }
    }

    fun indexExists(index: Int): Boolean {
        require(index < 0 || index <= mask || (index == mask + 1 && hasEmptyKey))
        return index >= 0
    }

    fun indexGet(index: Int): VType? {
        require(index >= 0) { "The index must point at an existing key." }
        require(index <= mask || (index == mask + 1 && hasEmptyKey))
        return values!![index] as VType?
    }

    fun indexReplace(index: Int, newValue: VType?): VType? {
        require(index >= 0) { "The index must point at an existing key." }
        require(index <= mask || (index == mask + 1 && hasEmptyKey))

        val previousValue = values!![index] as VType?
        values!![index] = newValue
        return previousValue
    }

    fun indexInsert(index: Int, key: Char, value: VType?) {
        var mutableIndex = index
        require(mutableIndex < 0) { "The index must not point at an existing key." }

        mutableIndex = mutableIndex.inv()
        if (key == EMPTY_KEY) {
            require(mutableIndex == mask + 1)
            values!![mutableIndex] = value
            hasEmptyKey = true
        } else {
            require(keys!![mutableIndex] == EMPTY_KEY)

            if (assigned == resizeAt) {
                allocateThenInsertThenRehash(mutableIndex, key, value)
            } else {
                keys!![mutableIndex] = key
                values!![mutableIndex] = value
            }
            assigned++
        }
    }

    fun indexRemove(index: Int): VType? {
        require(index >= 0) { "The index must point at an existing key." }
        require(index <= mask || (index == mask + 1 && hasEmptyKey))

        val previousValue = values!![index] as VType?
        if (index > mask) {
            require(index == mask + 1)
            hasEmptyKey = false
            values!![index] = 0
        } else {
            shiftConflictingKeys(index)
        }
        return previousValue
    }

    fun clear() {
        assigned = 0
        hasEmptyKey = false
        keys?.fill(EMPTY_KEY)
    }

    fun release() {
        assigned = 0
        hasEmptyKey = false

        keys = null
        values = null
        ensureCapacity(DEFAULT_EXPECTED_ELEMENTS)
    }

    fun size(): Int {
        return assigned + if (hasEmptyKey) 1 else 0
    }

    val isEmpty: Boolean
        get() = size() == 0

    override fun hashCode(): Int {
        var h = if (hasEmptyKey) -0x21524111 else 0
        for (c in this) {
            h += BitMixer.mix(c.key.code) + BitMixer.mix(c.value)
        }
        return h
    }

    override fun equals(obj: Any?): Boolean {
        return (this === obj)
                || (obj != null && this::class == obj::class && equalElements(this::class.cast(obj)))
    }

    protected fun equalElements(other: CharObjectHashMap<*>): Boolean {
        if (other.size() != size()) {
            return false
        }

        for (c in other) {
            val key = c.key
            if (!containsKey(key) || c.value != get(key)) {
                return false
            }
        }
        return true
    }

    fun ensureCapacity(expectedElements: Int) {
        if (expectedElements > resizeAt || keys == null) {
            val prevKeys = this.keys
            val prevValues = this.values
            allocateBuffers(minBufferSize(expectedElements, loadFactor))
            if (prevKeys != null && !this.isEmpty) {
                rehash(prevKeys, prevValues as Array<VType?>)
            }
        }
    }

    protected fun nextIterationSeed(): Int {
        return BitMixer.mixPhi(iterationSeed).also { iterationSeed = it }
    }

    override fun iterator(): Iterator<CharObjectCursor<VType>> {
        return EntryIterator() as Iterator<CharObjectCursor<VType>>
    }

    override fun ramBytesUsed(): Long {
        return BASE_RAM_BYTES_USED + RamUsageEstimator.sizeOf(keys!!) + sizeOfValues()
    }

    private fun sizeOfValues(): Long {
        var size = RamUsageEstimator.shallowSizeOf(values)
        for (value in values()) {
            size += RamUsageEstimator.sizeOfObject(value)
        }
        return size
    }

    private inner class EntryIterator : AbstractIterator<CharObjectCursor<VType?>?>() {
        private val cursor: CharObjectCursor<VType?> = CharObjectCursor()
        private val increment: Int
        private var index = 0
        private var slot: Int

        init {
            val seed = nextIterationSeed()
            increment = iterationIncrement(seed)
            slot = seed and mask
        }

        override fun fetch(): CharObjectCursor<VType?>? {
            val mask = this@CharObjectHashMap.mask
            while (index <= mask) {
                val existing: Char
                index++
                slot = (slot + increment) and mask
                if ((keys!![slot].also { existing = it }) != EMPTY_KEY) {
                    cursor.index = slot
                    cursor.key = existing
                    cursor.value = values!![slot] as VType?
                    return cursor
                }
            }

            if (index == mask + 1 && hasEmptyKey) {
                cursor.index = index
                cursor.key = EMPTY_KEY
                cursor.value = values!![index++] as VType?
                return cursor
            }
            return done()
        }
    }

    fun keys(): KeysContainer {
        return KeysContainer()
    }

    inner class KeysContainer : Iterable<CharCursor> {
        override fun iterator(): Iterator<CharCursor> {
            return KeysIterator() as Iterator<CharCursor>
        }

        fun size(): Int {
            return this@CharObjectHashMap.size()
        }

        fun toArray(): CharArray {
            val array = CharArray(size())
            var i = 0
            for (cursor in this) {
                array[i++] = cursor.value
            }
            return array
        }
    }

    private inner class KeysIterator : AbstractIterator<CharCursor?>() {
        private val cursor: CharCursor = CharCursor()
        private val increment: Int
        private var index = 0
        private var slot: Int

        init {
            val seed = nextIterationSeed()
            increment = iterationIncrement(seed)
            slot = seed and mask
        }

        override fun fetch(): CharCursor? {
            val mask = this@CharObjectHashMap.mask
            while (index <= mask) {
                val existing: Char
                index++
                slot = (slot + increment) and mask
                if ((keys!![slot].also { existing = it }) != EMPTY_KEY) {
                    cursor.index = slot
                    cursor.value = existing
                    return cursor
                }
            }

            if (index == mask + 1 && hasEmptyKey) {
                cursor.index = index++
                cursor.value = EMPTY_KEY
                return cursor
            }
            return done()
        }
    }

    fun values(): ValuesContainer {
        return ValuesContainer()
    }

    inner class ValuesContainer : Iterable<ObjectCursor<VType?>?> {
        override fun iterator(): MutableIterator<ObjectCursor<VType?>?> {
            return ValuesIterator()
        }

        fun size(): Int {
            return this@CharObjectHashMap.size()
        }
    }

    private inner class ValuesIterator : AbstractIterator<ObjectCursor<VType?>?>() {
        private val cursor: ObjectCursor<VType?> = ObjectCursor()
        private val increment: Int
        private var index = 0
        private var slot: Int

        init {
            val seed = nextIterationSeed()
            increment = iterationIncrement(seed)
            slot = seed and mask
        }

        override fun fetch(): ObjectCursor<VType?>? {
            val mask = this@CharObjectHashMap.mask
            while (index <= mask) {
                index++
                slot = (slot + increment) and mask
                if (keys!![slot] != EMPTY_KEY) {
                    cursor.index = slot
                    cursor.value = values!![slot] as VType?
                    return cursor
                }
            }

            if (index == mask + 1 && hasEmptyKey) {
                cursor.index = index
                cursor.value = values!![index++] as VType?
                return cursor
            }
            return done()
        }
    }

    override fun clone(): CharObjectHashMap<VType> {
        val cloned = CharObjectHashMap<VType>()
        cloned.keys = keys?.copyOf()
        cloned.values = values?.copyOf()
        cloned.mask = mask
        cloned.assigned = assigned
        cloned.resizeAt = resizeAt
        cloned.hasEmptyKey = hasEmptyKey
        cloned.loadFactor = loadFactor
        cloned.iterationSeed = ITERATION_SEED.addAndFetch(1)
        return cloned
    }

    override fun toString(): String {
        val buffer = StringBuilder()
        buffer.append("[")

        var first = true
        for (cursor in this) {
            if (!first) {
                buffer.append(", ")
            }
            buffer.append(cursor.key)
            buffer.append("=>")
            buffer.append(cursor.value)
            first = false
        }
        buffer.append("]")
        return buffer.toString()
    }

    protected fun hashKey(key: Char): Int {
        require(key != EMPTY_KEY)
        return BitMixer.mixPhi(key.code)
    }

    protected open fun verifyLoadFactor(loadFactor: Double): Double {
        checkLoadFactor(loadFactor, MIN_LOAD_FACTOR.toDouble(), MAX_LOAD_FACTOR.toDouble())
        return loadFactor
    }

    protected fun rehash(fromKeys: CharArray, fromValues: Array<VType?>) {
        require(fromKeys.size == fromValues.size && checkPowerOfTwo(fromKeys.size - 1))

        val keys = this.keys
        val values = this.values as Array<VType?>
        val mask = this.mask
        var existing: Char

        var from = fromKeys.size - 1
        keys!![keys.size - 1] = fromKeys[from]
        values[values.size - 1] = fromValues[from]
        while (--from >= 0) {
            if ((fromKeys[from].also { existing = it }) != EMPTY_KEY) {
                var slot = hashKey(existing) and mask
                while (keys[slot] != EMPTY_KEY) {
                    slot = (slot + 1) and mask
                }
                keys[slot] = existing
                values[slot] = fromValues[from]
            }
        }
    }

    protected open fun allocateBuffers(arraySize: Int) {
        require(Int.bitCount(arraySize) == 1)

        val prevKeys = this.keys
        val prevValues = this.values
        try {
            val emptyElementSlot = 1
            this.keys = CharArray(arraySize + emptyElementSlot)
            this.values = kotlin.arrayOfNulls<Any>(arraySize + emptyElementSlot)
        } catch (e: Error) {
            this.keys = prevKeys
            this.values = prevValues
            throw BufferAllocationException(
                "Not enough memory to allocate buffers for rehashing: %,d -> %,d",
                e,
                this.mask + 1,
                arraySize
            )
        }

        this.resizeAt = expandAtCount(arraySize, loadFactor)
        this.mask = arraySize - 1
    }

    protected fun allocateThenInsertThenRehash(slot: Int, pendingKey: Char, pendingValue: VType?) {
        require(assigned == resizeAt && keys!![slot] == EMPTY_KEY && pendingKey != EMPTY_KEY)

        val prevKeys = this.keys
        val prevValues = this.values as Array<VType?>
        allocateBuffers(nextBufferSize(mask + 1, size(), loadFactor))
        require(this.keys!!.size > prevKeys!!.size)

        prevKeys[slot] = pendingKey
        prevValues[slot] = pendingValue

        rehash(prevKeys, prevValues)
    }

    protected fun shiftConflictingKeys(gapSlot: Int) {
        var mutableGapSlot = gapSlot
        val keys = this.keys
        val values = this.values as Array<VType?>
        val mask = this.mask

        var distance = 0
        while (true) {
            val slot = (mutableGapSlot + (++distance)) and mask
            val existing = keys!![slot]
            if (existing == EMPTY_KEY) {
                break
            }

            val idealSlot = hashKey(existing)
            val shift = (slot - idealSlot) and mask
            if (shift >= distance) {
                keys[mutableGapSlot] = existing
                values[mutableGapSlot] = values[slot]
                mutableGapSlot = slot
                distance = 0
            }
        }

        keys[mutableGapSlot] = EMPTY_KEY
        values[mutableGapSlot] = null
        assigned--
    }

    /** Forked from HPPC, holding int index,key and value  */
    class CharObjectCursor<VType> {
        var index: Int = 0
        var key: Char = EMPTY_KEY
        var value: VType? = null

        override fun toString(): String {
            return "[cursor, index: $index, key: $key, value: $value]"
        }
    }

    companion object {
        private val BASE_RAM_BYTES_USED: Long = RamUsageEstimator.shallowSizeOfInstance(CharObjectHashMap::class)
        private const val EMPTY_KEY: Char = '\u0000'

        /** Creates a hash map from two index-aligned arrays of key-value pairs.  */
        fun <VType> from(keys: CharArray, values: Array<VType?>): CharObjectHashMap<VType?> {
            require(keys.size == values.size) { "Arrays of keys and values must have an identical length." }

            val map: CharObjectHashMap<VType?> = CharObjectHashMap<Any?>(keys.size) as CharObjectHashMap<VType?>
            for (i in keys.indices) {
                map.put(keys[i], values[i])
            }

            return map
        }
    }
}
