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
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.bitCount
import org.gnit.lucenekmp.jdkport.Cloneable
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.RamUsageEstimator
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.jvm.JvmOverloads
import kotlin.reflect.cast

/**
 * A hash map of `long` to `int`, implemented using open addressing with
 * linear probing for collision resolution.
 *
 *
 * Mostly forked and trimmed from com.carrotsearch.hppc.LongIntHashMap
 *
 *
 * github: https://github.com/carrotsearch/hppc release 0.10.0
 *
 * @lucene.internal
 */
@OptIn(ExperimentalAtomicApi::class)
open class LongIntHashMap
@JvmOverloads constructor(expectedElements: Int, loadFactor: Double = DEFAULT_LOAD_FACTOR.toDouble()) :
    Iterable<LongIntHashMap.LongIntCursor>, Accountable, Cloneable<LongIntHashMap> {
    /** The array holding keys.  */
    var keys: LongArray? = null

    /** The array holding values.  */
    var values: IntArray? = null

    /**
     * The number of stored keys (assigned key slots), excluding the special "empty" key, if any (use
     * [.size] instead).
     *
     * @see .size
     */
    protected var assigned: Int = 0

    /** Mask for slot scans in [.keys].  */
    protected var mask: Int = 0

    /** Expand (rehash) [.keys] when [.assigned] hits this value.  */
    protected var resizeAt: Int = 0

    /** Special treatment for the "empty slot" key marker.  */
    protected var hasEmptyKey: Boolean = false

    /** The load factor for [.keys].  */
    protected var loadFactor: Double = 0.0

    /** Seed used to ensure the hash iteration order is different from an iteration to another.  */
    protected var iterationSeed: Int = 0

    /** New instance with sane defaults.  */
    constructor() : this(DEFAULT_EXPECTED_ELEMENTS)

    /**
     * New instance with the provided defaults.
     *
     * @param expectedElements The expected number of elements guaranteed not to cause a rehash
     * (inclusive).
     * @param loadFactor The load factor for internal buffers. Insane load factors (zero, full
     * capacity) are rejected by [.verifyLoadFactor].
     */
    /**
     * New instance with sane defaults.
     *
     * @param expectedElements The expected number of elements guaranteed not to cause buffer
     * expansion (inclusive).
     */
    init {
        this.loadFactor = verifyLoadFactor(loadFactor)
        iterationSeed = ITERATION_SEED.incrementAndFetch()
        ensureCapacity(expectedElements)
    }

    /** Create a hash map from all key-value pairs of another map.  */
    constructor(map: LongIntHashMap) : this(map.size()) {
        putAll(map)
    }

    fun put(key: Long, value: Int): Int {
        require(assigned < mask + 1)

        val mask = this.mask
        if (((key) == 0L)) {
            val previousValue = if (hasEmptyKey) values!![mask + 1] else 0
            hasEmptyKey = true
            values!![mask + 1] = value
            return previousValue
        } else {
            val keys = this.keys
            var slot = hashKey(key) and mask

            var existing: Long
            while ((keys!![slot].also { existing = it }) != 0L) {
                if (((existing) == (key))) {
                    val previousValue = values!![slot]
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
            return 0
        }
    }

    fun putAll(iterable: Iterable<LongIntCursor>): Int {
        val count = size()
        for (c in iterable) {
            put(c.key, c.value)
        }
        return size() - count
    }

    /**
     * [Trove](http://trove4j.sourceforge.net)-inspired API method. An equivalent of the
     * following code:
     *
     * <pre>
     * if (!map.containsKey(key)) map.put(value);
    </pre> *
     *
     * @param key The key of the value to check.
     * @param value The value to put if `key` does not exist.
     * @return `true` if `key` did not exist and `value` was placed
     * in the map.
     */
    fun putIfAbsent(key: Long, value: Int): Boolean {
        val keyIndex = indexOf(key)
        if (!indexExists(keyIndex)) {
            indexInsert(keyIndex, key, value)
            return true
        } else {
            return false
        }
    }

    /**
     * If `key` exists, `putValue` is inserted into the map, otherwise any
     * existing value is incremented by `additionValue`.
     *
     * @param key The key of the value to adjust.
     * @param putValue The value to put if `key` does not exist.
     * @param incrementValue The value to add to the existing value if `key` exists.
     * @return Returns the current value associated with `key` (after changes).
     */
    fun putOrAdd(key: Long, putValue: Int, incrementValue: Int): Int {
        var putValue = putValue
        require(assigned < mask + 1)

        val keyIndex = indexOf(key)
        if (indexExists(keyIndex)) {
            putValue = values!![keyIndex] + incrementValue
            indexReplace(keyIndex, putValue)
        } else {
            indexInsert(keyIndex, key, putValue)
        }
        return putValue
    }

    /**
     * Adds `incrementValue` to any existing value for the given `key` or
     * inserts `incrementValue` if `key` did not previously exist.
     *
     * @param key The key of the value to adjust.
     * @param incrementValue The value to put or add to the existing value if `key` exists.
     * @return Returns the current value associated with `key` (after changes).
     */
    fun addTo(key: Long, incrementValue: Int): Int {
        return putOrAdd(key, incrementValue, incrementValue)
    }

    fun remove(key: Long): Int {
        val mask = this.mask
        if (((key) == 0L)) {
            if (!hasEmptyKey) {
                return 0
            }
            hasEmptyKey = false
            val previousValue = values!![mask + 1]
            values!![mask + 1] = 0
            return previousValue
        } else {
            val keys = this.keys
            var slot = hashKey(key) and mask

            var existing: Long
            while ((keys!![slot].also { existing = it }) != 0L) {
                if (((existing) == (key))) {
                    val previousValue = values!![slot]
                    shiftConflictingKeys(slot)
                    return previousValue
                }
                slot = (slot + 1) and mask
            }

            return 0
        }
    }

    fun get(key: Long): Int {
        if (((key) == 0L)) {
            return if (hasEmptyKey) values!![mask + 1] else 0
        } else {
            val keys = this.keys
            val mask = this.mask
            var slot = hashKey(key) and mask

            var existing: Long
            while ((keys!![slot].also { existing = it }) != 0L) {
                if (((existing) == (key))) {
                    return values!![slot]
                }
                slot = (slot + 1) and mask
            }

            return 0
        }
    }

    fun getOrDefault(key: Long, defaultValue: Int): Int {
        if (((key) == 0L)) {
            return if (hasEmptyKey) values!![mask + 1] else defaultValue
        } else {
            val keys = this.keys
            val mask = this.mask
            var slot = hashKey(key) and mask

            var existing: Long
            while ((keys!![slot].also { existing = it }) != 0L) {
                if (((existing) == (key))) {
                    return values!![slot]
                }
                slot = (slot + 1) and mask
            }

            return defaultValue
        }
    }

    fun containsKey(key: Long): Boolean {
        if (((key) == 0L)) {
            return hasEmptyKey
        } else {
            val keys = this.keys
            val mask = this.mask
            var slot = hashKey(key) and mask

            var existing: Long
            while ((keys!![slot].also { existing = it }) != 0L) {
                if (((existing) == (key))) {
                    return true
                }
                slot = (slot + 1) and mask
            }

            return false
        }
    }

    fun indexOf(key: Long): Int {
        val mask = this.mask
        if (((key) == 0L)) {
            return if (hasEmptyKey) mask + 1 else (mask + 1).inv()
        } else {
            val keys = this.keys
            var slot = hashKey(key) and mask

            var existing: Long
            while ((keys!![slot].also { existing = it }) != 0L) {
                if (((existing) == (key))) {
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

    fun indexGet(index: Int): Int {
        require(index >= 0) { "The index must point at an existing key." }
        require(index <= mask || (index == mask + 1 && hasEmptyKey))

        return values!![index]
    }

    fun indexReplace(index: Int, newValue: Int): Int {
        require(index >= 0) { "The index must point at an existing key." }
        require(index <= mask || (index == mask + 1 && hasEmptyKey))

        val previousValue = values!![index]
        values!![index] = newValue
        return previousValue
    }

    fun indexInsert(index: Int, key: Long, value: Int) {
        var index = index
        require(index < 0) { "The index must not point at an existing key." }

        index = index.inv()
        if (((key) == 0L)) {
            require(index == mask + 1)
            values!![index] = value
            hasEmptyKey = true
        } else {
            require((keys!![index]) == 0L)

            if (assigned == resizeAt) {
                allocateThenInsertThenRehash(index, key, value)
            } else {
                keys!![index] = key
                values!![index] = value
            }

            assigned++
        }
    }

    fun indexRemove(index: Int): Int {
        require(index >= 0) { "The index must point at an existing key." }
        require(index <= mask || (index == mask + 1 && hasEmptyKey))

        val previousValue = values!![index]
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

        Arrays.fill(keys!!, 0L)

        /*  */
    }

    fun release() {
        assigned = 0
        hasEmptyKey = false

        keys = null
        values = null
        ensureCapacity(DEFAULT_EXPECTED_ELEMENTS)
    }

    fun size(): Int {
        return assigned + (if (hasEmptyKey) 1 else 0)
    }

    val isEmpty: Boolean
        get() = size() == 0

    override fun hashCode(): Int {
        var h = if (hasEmptyKey) -0x21524111 else 0
        for (c in this) {
            h += BitMixer.mix(c.key) + BitMixer.mix(c.value)
        }
        return h
    }

    override fun equals(other: Any?): Boolean {
        return (this === other)
                || (other != null && this::class == other::class && equalElements(this::class.cast(other)))
    }

    /** Return true if all keys of some other container exist in this container.  */
    protected fun equalElements(other: LongIntHashMap): Boolean {
        if (other.size() != size()) {
            return false
        }

        for (c in other) {
            val key = c.key
            if (!containsKey(key) || (get(key)) != (c.value)) {
                return false
            }
        }

        return true
    }

    /**
     * Ensure this container can hold at least the given number of keys (entries) without resizing its
     * buffers.
     *
     * @param expectedElements The total number of keys, inclusive.
     */
    fun ensureCapacity(expectedElements: Int) {
        if (expectedElements > resizeAt || keys == null) {
            val prevKeys = this.keys
            val prevValues = this.values
            allocateBuffers(minBufferSize(expectedElements, loadFactor))
            if (prevKeys != null && !this.isEmpty) {
                rehash(prevKeys, prevValues!!)
            }
        }
    }

    /**
     * Provides the next iteration seed used to build the iteration starting slot and offset
     * increment. This method does not need to be synchronized, what matters is that each thread gets
     * a sequence of varying seeds.
     */
    protected fun nextIterationSeed(): Int {
        return BitMixer.mixPhi(iterationSeed).also { iterationSeed = it }
    }

    override fun ramBytesUsed(): Long {
        return BASE_RAM_BYTES_USED + RamUsageEstimator.sizeOf(keys!!) + RamUsageEstimator.sizeOf(values!!)
    }

    /** An iterator implementation for [.iterator].  */
    private inner class EntryIterator : AbstractIterator<LongIntCursor>() {
        private val cursor: LongIntCursor = LongIntCursor()
        private val increment: Int
        private var index = 0
        private var slot: Int

        init {
            val seed = nextIterationSeed()
            increment = iterationIncrement(seed)
            slot = seed and mask
        }

        override fun fetch(): LongIntCursor? {
            val mask = this@LongIntHashMap.mask
            while (index <= mask) {
                val existing: Long
                index++
                slot = (slot + increment) and mask
                if ((keys!![slot].also { existing = it }) != 0L) {
                    cursor.index = slot
                    cursor.key = existing
                    cursor.value = values!![slot]
                    return cursor
                }
            }

            if (index == mask + 1 && hasEmptyKey) {
                cursor.index = index
                cursor.key = 0
                cursor.value = values!![index++]
                return cursor
            }

            return done()
        }
    }

    override fun iterator(): Iterator<LongIntCursor> {
        return this@LongIntHashMap.EntryIterator() as Iterator<LongIntCursor>
    }

    /** Returns a specialized view of the keys of this associated container.  */
    fun keys(): KeysContainer {
        return this.KeysContainer()
    }

    /** A view of the keys inside this hash map.  */
    inner class KeysContainer : Iterable<LongCursor> {
        override fun iterator(): Iterator<LongCursor> {
            return this@LongIntHashMap.KeysIterator() as Iterator<LongCursor>
        }

        fun size(): Int {
            return this@LongIntHashMap.size()
        }

        fun toArray(): LongArray {
            val array = LongArray(size())
            var i = 0
            for (cursor in this) {
                array[i++] = cursor.value
            }
            return array
        }
    }

    /** An iterator over the set of assigned keys.  */
    private inner class KeysIterator : AbstractIterator<LongCursor>() {
        private val cursor: LongCursor = LongCursor()
        private val increment: Int
        private var index = 0
        private var slot: Int

        init {
            val seed = nextIterationSeed()
            increment = iterationIncrement(seed)
            slot = seed and mask
        }

        override fun fetch(): LongCursor? {
            val mask = this@LongIntHashMap.mask
            while (index <= mask) {
                val existing: Long
                index++
                slot = (slot + increment) and mask
                if ((keys!![slot].also { existing = it }) != 0L) {
                    cursor.index = slot
                    cursor.value = existing
                    return cursor
                }
            }

            if (index == mask + 1 && hasEmptyKey) {
                cursor.index = index++
                cursor.value = 0
                return cursor
            }

            return done()
        }
    }

    /**
     * @return Returns a container with all values stored in this map.
     */
    fun values(): ValuesContainer {
        return this.ValuesContainer()
    }

    /** A view over the set of values of this map.  */
    inner class ValuesContainer : Iterable<IntCursor> {
        override fun iterator(): Iterator<IntCursor> {
            return this@LongIntHashMap.ValuesIterator() as Iterator<IntCursor>
        }

        fun size(): Int {
            return this@LongIntHashMap.size()
        }

        fun toArray(): IntArray {
            val array = IntArray(size())
            var i = 0
            for (cursor in this) {
                array[i++] = cursor.value
            }
            return array
        }
    }

    /** An iterator over the set of assigned values.  */
    private inner class ValuesIterator : AbstractIterator<IntCursor>() {
        private val cursor: IntCursor = IntCursor()
        private val increment: Int
        private var index = 0
        private var slot: Int

        init {
            val seed = nextIterationSeed()
            increment = iterationIncrement(seed)
            slot = seed and mask
        }

        override fun fetch(): IntCursor? {
            val mask = this@LongIntHashMap.mask
            while (index <= mask) {
                index++
                slot = (slot + increment) and mask
                if ((keys!![slot]) != 0L) {
                    cursor.index = slot
                    cursor.value = values!![slot]
                    return cursor
                }
            }

            if (index == mask + 1 && hasEmptyKey) {
                cursor.index = index
                cursor.value = values!![index++]
                return cursor
            }

            return done()
        }
    }

    override fun clone(): LongIntHashMap {
        // Use the copy constructor to ensure all internal state is correctly
        // transferred to the cloned instance.
        return LongIntHashMap(this)
    }

    /** Convert the contents of this map to a human-friendly string.  */
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

    /**
     * Returns a hash code for the given key.
     *
     *
     * The output from this function should evenly distribute keys across the entire integer range.
     */
    protected fun hashKey(key: Long): Int {
        require(
            (key) != 0L // Handled as a special case (empty slot marker).
        )
        return BitMixer.mixPhi(key)
    }

    /**
     * Validate load factor range and return it. Override and suppress if you need insane load
     * factors.
     */
    protected open fun verifyLoadFactor(loadFactor: Double): Double {
        checkLoadFactor(
            loadFactor, MIN_LOAD_FACTOR.toDouble(), MAX_LOAD_FACTOR.toDouble()
        )
        return loadFactor
    }

    /** Rehash from old buffers to new buffers.  */
    protected fun rehash(fromKeys: LongArray, fromValues: IntArray) {
        require(
            fromKeys.size == fromValues.size
                    && checkPowerOfTwo(fromKeys.size - 1)
        )

        // Rehash all stored key/value pairs into the new buffers.
        val keys = this.keys
        val values = this.values
        val mask = this.mask
        var existing: Long

        // Copy the zero element's slot, then rehash everything else.
        var from = fromKeys.size - 1
        keys!![keys.size - 1] = fromKeys[from]
        values!![values.size - 1] = fromValues[from]
        while (--from >= 0) {
            if ((fromKeys[from].also { existing = it }) != 0L) {
                var slot = hashKey(existing) and mask
                while ((keys[slot]) != 0L) {
                    slot = (slot + 1) and mask
                }
                keys[slot] = existing
                values[slot] = fromValues[from]
            }
        }
    }

    /**
     * Allocate new internal buffers. This method attempts to allocate and assign internal buffers
     * atomically (either allocations succeed or not).
     */
    protected open fun allocateBuffers(arraySize: Int) {
        require(Int.bitCount(arraySize) == 1)

        // Ensure no change is done if we hit an OOM.
        val prevKeys = this.keys
        val prevValues = this.values
        try {
            val emptyElementSlot = 1
            this.keys = (LongArray(arraySize + emptyElementSlot))
            this.values = (IntArray(arraySize + emptyElementSlot))
        } catch (e: /*OutOfMemory*/Error) {
            this.keys = prevKeys
            this.values = prevValues
            throw BufferAllocationException(
                "Not enough memory to allocate buffers for rehashing: %,d -> %,d",
                e, this.mask + 1, arraySize
            )
        }

        this.resizeAt = expandAtCount(arraySize, loadFactor)
        this.mask = arraySize - 1
    }

    /**
     * This method is invoked when there is a new key/ value pair to be inserted into the buffers but
     * there is not enough empty slots to do so.
     *
     *
     * New buffers are allocated. If this succeeds, we know we can proceed with rehashing so we
     * assign the pending element to the previous buffer (possibly violating the invariant of having
     * at least one empty slot) and rehash all keys, substituting new buffers at the end.
     */
    protected fun allocateThenInsertThenRehash(slot: Int, pendingKey: Long, pendingValue: Int) {
        require(assigned == resizeAt && ((keys!![slot]) == 0L) && ((pendingKey) != 0L))

        // Try to allocate new buffers first. If we OOM, we leave in a consistent state.
        val prevKeys = this.keys
        val prevValues = this.values
        allocateBuffers(nextBufferSize(mask + 1, size(), loadFactor))
        require(this.keys!!.size > prevKeys!!.size)

        // We have succeeded at allocating new data so insert the pending key/value at
        // the free slot in the old arrays before rehashing.
        prevKeys[slot] = pendingKey
        prevValues!![slot] = pendingValue

        // Rehash old keys, including the pending key.
        rehash(prevKeys, prevValues)
    }

    /**
     * Shift all the slot-conflicting keys and values allocated to (and including) `slot`.
     */
    protected fun shiftConflictingKeys(gapSlot: Int) {
        var gapSlot = gapSlot
        val keys = this.keys
        val values = this.values
        val mask = this.mask

        // Perform shifts of conflicting keys to fill in the gap.
        var distance = 0
        while (true) {
            val slot = (gapSlot + (++distance)) and mask
            val existing = keys!![slot]
            if (((existing) == 0L)) {
                break
            }

            val idealSlot = hashKey(existing)
            val shift = (slot - idealSlot) and mask
            if (shift >= distance) {
                // Entry at this position was originally at or before the gap slot.
                // Move the conflict-shifted entry to the gap's position and repeat the procedure
                // for any entries to the right of the current position, treating it
                // as the new gap.
                keys[gapSlot] = existing
                values!![gapSlot] = values[slot]
                gapSlot = slot
                distance = 0
            }
        }

        // Mark the last found gap slot without a conflict as empty.
        keys[gapSlot] = 0
        values!![gapSlot] = 0
        assigned--
    }

    /** Forked from HPPC, holding int index,key and value  */
    class LongIntCursor {
        /**
         * The current key and value's index in the container this cursor belongs to. The meaning of
         * this index is defined by the container (usually it will be an index in the underlying storage
         * buffer).
         */
        var index: Int = 0

        /** The current key.  */
        var key: Long = 0

        /** The current value.  */
        var value: Int = 0

        override fun toString(): String {
            return "[cursor, index: $index, key: $key, value: $value]"
        }
    }

    companion object {
        private val BASE_RAM_BYTES_USED: Long = RamUsageEstimator.shallowSizeOfInstance(LongIntHashMap::class)

        /** Creates a hash map from two index-aligned arrays of key-value pairs.  */
        fun from(keys: LongArray, values: IntArray): LongIntHashMap {
            require(keys.size == values.size) { "Arrays of keys and values must have an identical length." }

            val map = LongIntHashMap(keys.size)
            for (i in keys.indices) {
                map.put(keys[i], values[i])
            }

            return map
        }
    }
}
