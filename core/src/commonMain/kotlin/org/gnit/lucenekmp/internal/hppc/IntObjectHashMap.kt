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
import org.gnit.lucenekmp.jdkport.bitCount
import org.gnit.lucenekmp.jdkport.Cloneable
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.RamUsageEstimator
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.jvm.JvmOverloads
import kotlin.reflect.cast


/**
 * A hash map of `int` to `Object`, implemented using open addressing with
 * linear probing for collision resolution. Supports null values.
 *
 *
 * Mostly forked and trimmed from com.carrotsearch.hppc.IntObjectHashMap
 *
 *
 * github: https://github.com/carrotsearch/hppc release 0.10.0
 *
 * @lucene.internal
 */
@OptIn(ExperimentalAtomicApi::class)
class IntObjectHashMap<VType>
@JvmOverloads constructor(expectedElements: Int, loadFactor: Double = DEFAULT_LOAD_FACTOR.toDouble()) :
    Iterable<IntObjectHashMap.IntObjectCursor<VType?>>, Accountable, Cloneable<IntObjectHashMap<VType>> {
    /** The array holding keys.  */
    var keys: IntArray? = null

    /** The array holding values.  */
    var values: Array<Any?>?  = null

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
        iterationSeed = ITERATION_SEED.addAndFetch(1)
        ensureCapacity(expectedElements)
    }

    /** Create a hash map from all key-value pairs of another map.  */
    constructor(map: IntObjectHashMap<VType?>) : this(map.size()) {
        putAll(map)
    }

    fun put(key: Int, value: VType?): VType? {
        require(assigned < mask + 1)

        val mask = this.mask
        if (((key) == 0)) {
            val previousValue = if (hasEmptyKey) values!![mask + 1] as VType? else null
            hasEmptyKey = true
            values!![mask + 1] = value
            return previousValue
        } else {
            val keys = this.keys
            var slot = hashKey(key) and mask

            var existing: Int
            while ((keys!![slot].also { existing = it }) != 0) {
                if (((existing) == (key))) {
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

    fun putAll(iterable: Iterable<out IntObjectCursor<out VType?>>): Int {
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
    fun putIfAbsent(key: Int, value: VType?): Boolean {
        val keyIndex = indexOf(key)
        if (!indexExists(keyIndex)) {
            indexInsert(keyIndex, key, value)
            return true
        } else {
            return false
        }
    }

    fun remove(key: Int): VType? {
        val mask = this.mask
        if (((key) == 0)) {
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

            var existing: Int
            while ((keys!![slot].also { existing = it }) != 0) {
                if (((existing) == (key))) {
                    val previousValue = values!![slot] as VType?
                    shiftConflictingKeys(slot)
                    return previousValue
                }
                slot = (slot + 1) and mask
            }

            return null
        }
    }

    operator fun get(key: Int): VType? {
        if (((key) == 0)) {
            return if (hasEmptyKey) values!![mask + 1] as VType? else null
        } else {
            val keys = this.keys
            val mask = this.mask
            var slot = hashKey(key) and mask

            var existing: Int
            while ((keys!![slot].also { existing = it }) != 0) {
                if (((existing) == (key))) {
                    return values!![slot] as VType?
                }
                slot = (slot + 1) and mask
            }

            return null
        }
    }

    fun getOrDefault(key: Int, defaultValue: VType?): VType? {
        if (((key) == 0)) {
            return if (hasEmptyKey) values!![mask + 1] as VType? else defaultValue
        } else {
            val keys = this.keys
            val mask = this.mask
            var slot = hashKey(key) and mask

            var existing: Int
            while ((keys!![slot].also { existing = it }) != 0) {
                if (((existing) == (key))) {
                    return values!![slot] as VType?
                }
                slot = (slot + 1) and mask
            }

            return defaultValue
        }
    }

    fun containsKey(key: Int): Boolean {
        if (((key) == 0)) {
            return hasEmptyKey
        } else {
            val keys = this.keys
            val mask = this.mask
            var slot = hashKey(key) and mask

            var existing: Int
            while ((keys!![slot].also { existing = it }) != 0) {
                if (((existing) == (key))) {
                    return true
                }
                slot = (slot + 1) and mask
            }

            return false
        }
    }

    fun indexOf(key: Int): Int {
        val mask = this.mask
        if (((key) == 0)) {
            return if (hasEmptyKey) mask + 1 else (mask + 1).inv()
        } else {
            val keys = this.keys
            var slot = hashKey(key) and mask

            var existing: Int
            while ((keys!![slot].also { existing = it }) != 0) {
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

    fun indexInsert(index: Int, key: Int, value: VType?) {
        var index = index
        require(index < 0) { "The index must not point at an existing key." }

        index = index.inv()
        if (((key) == 0)) {
            require(index == mask + 1)
            values!![index] = value
            hasEmptyKey = true
        } else {
            require((keys!![index]) == 0)

            if (assigned == resizeAt) {
                allocateThenInsertThenRehash(index, key, value)
            } else {
                keys!![index] = key
                values!![index] = value
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

        /*java.util.Arrays.fill(keys, 0)*/
        keys?.fill(0)
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
            h += BitMixer.mix(c!!.key) + BitMixer.mix(c.value)
        }
        return h
    }

    override fun equals(obj: Any?): Boolean {
        return (this === obj)
                || (obj != null && this::class == obj::class && equalElements(this::class.cast(obj)))
    }

    /** Return true if all keys of some other container exist in this container.  */
    protected fun equalElements(other: IntObjectHashMap<*>): Boolean {
        if (other.size() != size()) {
            return false
        }

        for (c in other) {
            val key = c!!.key
            if (!containsKey(key) || c.value != get(key)) {
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
            val prevValues = this.values as Array<VType?>
            allocateBuffers(minBufferSize(expectedElements, loadFactor))
            if (prevKeys != null && !this.isEmpty) {
                rehash(prevKeys, prevValues)
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

    override fun iterator(): Iterator<IntObjectCursor<VType?>> {
        return this.EntryIterator() as Iterator<IntObjectCursor<VType?>>
    }

    public override fun ramBytesUsed(): Long {
        return BASE_RAM_BYTES_USED + RamUsageEstimator.sizeOf(keys!!) + sizeOfValues()
    }

    private fun sizeOfValues(): Long {
        var size: Long = RamUsageEstimator.shallowSizeOf(values)
        for (value in values()) {
            size += RamUsageEstimator.sizeOfObject(value)
        }
        return size
    }

    /** An iterator implementation for [.iterator].  */
    private inner class EntryIterator : AbstractIterator<IntObjectCursor<VType?>?>() {
        private val cursor: IntObjectCursor<VType?>
        private val increment: Int
        private var index = 0
        private var slot: Int

        init {
            cursor = IntObjectCursor<VType?>()
            val seed = nextIterationSeed()
            increment = iterationIncrement(seed)
            slot = seed and mask
        }

        protected override fun fetch(): IntObjectCursor<VType?> {
            val mask = this@IntObjectHashMap.mask
            while (index <= mask) {
                val existing: Int
                index++
                slot = (slot + increment) and mask
                if ((keys!![slot].also { existing = it }) != 0) {
                    cursor.index = slot
                    cursor.key = existing
                    cursor.value = values!![slot] as VType?
                    return cursor
                }
            }

            if (index == mask + 1 && hasEmptyKey) {
                cursor.index = index
                cursor.key = 0
                cursor.value = values!![index++] as VType?
                return cursor
            }

            return done()!!
        }
    }

    /** Returns a specialized view of the keys of this associated container.  */
    fun keys(): KeysContainer {
        return this.KeysContainer()
    }

    /** A view of the keys inside this hash map.  */
    inner class KeysContainer : Iterable<IntCursor> {
        override fun iterator(): Iterator<IntCursor> {
            return KeysIterator() as Iterator<IntCursor>
        }

        fun size(): Int {
            return this@IntObjectHashMap.size()
        }

        fun toArray(): IntArray {
            val array = IntArray(size())
            var i = 0
            for (cursor in this) {
                array[i++] = cursor!!.value
            }
            return array
        }
    }

    /** An iterator over the set of assigned keys.  */
    private inner class KeysIterator : AbstractIterator<IntCursor?>() {
        private val cursor: IntCursor
        private val increment: Int
        private var index = 0
        private var slot: Int

        init {
            cursor = IntCursor()
            val seed = nextIterationSeed()
            increment = iterationIncrement(seed)
            slot = seed and mask
        }

        protected override fun fetch(): IntCursor {
            val mask = this@IntObjectHashMap.mask
            while (index <= mask) {
                val existing: Int
                index++
                slot = (slot + increment) and mask
                if ((keys!![slot].also { existing = it }) != 0) {
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

            return done()!!
        }
    }

    /**
     * @return Returns a container with all values stored in this map.
     */
    fun values(): ValuesContainer {
        return ValuesContainer()
    }

    /** A view over the set of values of this map.  */
    inner class ValuesContainer : Iterable<ObjectCursor<VType?>?> {
        override fun iterator(): MutableIterator<ObjectCursor<VType?>?> {
            return ValuesIterator()
        }

        fun size(): Int {
            return this@IntObjectHashMap.size()
        }
    }

    /** An iterator over the set of assigned values.  */
    private inner class ValuesIterator : AbstractIterator<ObjectCursor<VType?>?>() {
        private val cursor: ObjectCursor<VType?>
        private val increment: Int
        private var index = 0
        private var slot: Int

        init {
            cursor = ObjectCursor()
            val seed = nextIterationSeed()
            increment = iterationIncrement(seed)
            slot = seed and mask
        }

        override fun fetch(): ObjectCursor<VType?>? {
            val mask = this@IntObjectHashMap.mask
            while (index <= mask) {
                index++
                slot = (slot + increment) and mask
                if ((keys!![slot]) != 0) {
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

    override fun clone(): IntObjectHashMap<VType> {
        val cloned = IntObjectHashMap<VType>()
        // Deep copy keys array
        cloned.keys = keys?.copyOf()
        // Shallow copy values array (elements themselves aren't cloned—if you need deep clone, handle accordingly)
        cloned.values = values?.copyOf()

        cloned.mask = mask
        cloned.assigned = assigned
        cloned.resizeAt = resizeAt
        cloned.hasEmptyKey = hasEmptyKey
        cloned.loadFactor = loadFactor
        cloned.iterationSeed = ITERATION_SEED.addAndFetch(1)
        return cloned
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
            buffer.append(cursor!!.key)
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
    protected fun hashKey(key: Int): Int {
        require(
            (key) != 0 // Handled as a special case (empty slot marker).
        )
        return BitMixer.mixPhi(key)
    }

    /**
     * Validate load factor range and return it. Override and suppress if you need insane load
     * factors.
     */
    protected fun verifyLoadFactor(loadFactor: Double): Double {
        checkLoadFactor(loadFactor, MIN_LOAD_FACTOR.toDouble(), MAX_LOAD_FACTOR.toDouble())
        return loadFactor
    }

    /** Rehash from old buffers to new buffers.  */
    protected fun rehash(fromKeys: IntArray, fromValues: Array<VType?>) {
        require(fromKeys.size == fromValues.size && checkPowerOfTwo(fromKeys.size - 1))

        // Rehash all stored key/value pairs into the new buffers.
        val keys = this.keys
        val values = this.values as Array<VType?>
        val mask = this.mask
        var existing: Int

        // Copy the zero element's slot, then rehash everything else.
        var from = fromKeys.size - 1
        keys!![keys.size - 1] = fromKeys[from]
        values[values.size - 1] = fromValues[from]
        while (--from >= 0) {
            if ((fromKeys[from].also { existing = it }) != 0) {
                var slot = hashKey(existing) and mask
                while ((keys[slot]) != 0) {
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
    protected fun allocateBuffers(arraySize: Int) {
        require(Int.bitCount(arraySize) == 1)

        // Ensure no change is done if we hit an OOM.
        val prevKeys = this.keys
        val prevValues = this.values as Array<VType?>
        try {
            val emptyElementSlot = 1
            this.keys = (IntArray(arraySize + emptyElementSlot))
            this.values = kotlin.arrayOfNulls<Any>(arraySize + emptyElementSlot)
        } catch (e: Error) {
            this.keys = prevKeys
            this.values = prevValues as Array<Any?>?
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
    protected fun allocateThenInsertThenRehash(slot: Int, pendingKey: Int, pendingValue: VType?) {
        require(assigned == resizeAt && ((keys!![slot]) == 0) && ((pendingKey) != 0))

        // Try to allocate new buffers first. If we OOM, we leave in a consistent state.
        val prevKeys = this.keys
        val prevValues = this.values as Array<VType?>
        allocateBuffers(nextBufferSize(mask + 1, size(), loadFactor))
        require(this.keys!!.size > prevKeys!!.size)

        // We have succeeded at allocating new data so insert the pending key/value at
        // the free slot in the old arrays before rehashing.
        prevKeys[slot] = pendingKey
        prevValues[slot] = pendingValue

        // Rehash old keys, including the pending key.
        rehash(prevKeys, prevValues)
    }

    /**
     * Shift all the slot-conflicting keys and values allocated to (and including) `slot`.
     */
    protected fun shiftConflictingKeys(gapSlot: Int) {
        var gapSlot = gapSlot
        val keys = this.keys
        val values = this.values as Array<VType?>
        val mask = this.mask

        // Perform shifts of conflicting keys to fill in the gap.
        var distance = 0
        while (true) {
            val slot = (gapSlot + (++distance)) and mask
            val existing = keys!![slot]
            if (((existing) == 0)) {
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
                values[gapSlot] = values[slot]
                gapSlot = slot
                distance = 0
            }
        }

        // Mark the last found gap slot without a conflict as empty.
        keys[gapSlot] = 0
        values[gapSlot] = null
        assigned--
    }

    /** Forked from HPPC, holding int index,key and value  */
    class IntObjectCursor<VType> {
        /**
         * The current key and value's index in the container this cursor belongs to. The meaning of
         * this index is defined by the container (usually it will be an index in the underlying storage
         * buffer).
         */
        var index: Int = 0

        /** The current key.  */
        var key: Int = 0

        /** The current value.  */
        var value: VType? = null

        override fun toString(): String {
            return "[cursor, index: $index, key: $key, value: $value]"
        }
    }

    companion object {
        private val BASE_RAM_BYTES_USED: Long = RamUsageEstimator.shallowSizeOfInstance(IntObjectHashMap::class)

        /** Creates a hash map from two index-aligned arrays of key-value pairs.  */
        fun <VType> from(keys: IntArray, values: Array<VType?>): IntObjectHashMap<VType?> {
            require(keys.size == values.size) { "Arrays of keys and values must have an identical length." }

            val map: IntObjectHashMap<VType?> = IntObjectHashMap<Any?>(keys.size) as IntObjectHashMap<VType?>
            for (i in keys.indices) {
                map.put(keys[i], values[i])
            }

            return map
        }
    }
}
