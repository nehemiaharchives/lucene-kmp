package org.gnit.lucenekmp.internal.hppc


import org.gnit.lucenekmp.internal.hppc.HashContainers.DEFAULT_EXPECTED_ELEMENTS
import org.gnit.lucenekmp.internal.hppc.HashContainers.DEFAULT_LOAD_FACTOR
import org.gnit.lucenekmp.internal.hppc.HashContainers.ITERATION_SEED
import org.gnit.lucenekmp.internal.hppc.HashContainers.MAX_LOAD_FACTOR
import org.gnit.lucenekmp.internal.hppc.HashContainers.MIN_LOAD_FACTOR
import org.gnit.lucenekmp.internal.hppc.HashContainers.checkLoadFactor
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
 * A hash set of `long`s, implemented using open addressing with linear probing for
 * collision resolution.
 *
 *
 * Mostly forked and trimmed from com.carrotsearch.hppc.LongHashSet
 *
 *
 * github: https://github.com/carrotsearch/hppc release 0.10.0
 *
 * @lucene.internal
 */
@OptIn(ExperimentalAtomicApi::class)
open class LongHashSet @JvmOverloads constructor(expectedElements: Int, loadFactor: Double = DEFAULT_LOAD_FACTOR.toDouble()) :
    Iterable<LongCursor>, Accountable, Cloneable<LongHashSet> {
    /** The hash array holding keys.  */
    var keys: LongArray? = null

    /**
     * The number of stored keys (assigned key slots), excluding the special "empty" key, if any.
     *
     * @see .size
     * @see .hasEmptyKey
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
     * @param expectedElements The expected number of elements guaranteed not to cause a rehash
     * (inclusive).
     */
    init {
        this.loadFactor = verifyLoadFactor(loadFactor)
        iterationSeed = ITERATION_SEED.incrementAndFetch()
        ensureCapacity(expectedElements)
    }

    /** New instance copying elements from another set.  */
    constructor(set: LongHashSet) : this(set.size()) {
        addAll(set)
    }

    fun add(key: Long): Boolean {
        if (((key) == 0L)) {
            require((keys!![mask + 1]) == 0L)
            val added = !hasEmptyKey
            hasEmptyKey = true
            return added
        } else {
            val keys = this.keys
            val mask = this.mask
            var slot = hashKey(key) and mask

            var existing: Long
            while ((keys!![slot].also { existing = it }) != 0L) {
                if (((key) == (existing))) {
                    return false
                }
                slot = (slot + 1) and mask
            }

            if (assigned == resizeAt) {
                allocateThenInsertThenRehash(slot, key)
            } else {
                keys[slot] = key
            }

            assigned++
            return true
        }
    }

    /**
     * Adds all elements from the given list (vararg) to this set.
     *
     * @return Returns the number of elements actually added as a result of this call (not previously
     * present in the set).
     */
    fun addAll(vararg elements: Long): Int {
        ensureCapacity(elements.size)
        var count = 0
        for (e in elements) {
            if (add(e)) {
                count++
            }
        }
        return count
    }

    /**
     * Adds all elements from the given set to this set.
     *
     * @return Returns the number of elements actually added as a result of this call (not previously
     * present in the set).
     */
    fun addAll(set: LongHashSet): Int {
        ensureCapacity(set.size())
        return addAll(set as Iterable<LongCursor>)
    }

    /**
     * Adds all elements from the given iterable to this set.
     *
     * @return Returns the number of elements actually added as a result of this call (not previously
     * present in the set).
     */
    fun addAll(iterable: Iterable<LongCursor>): Int {
        var count = 0
        for (cursor in iterable) {
            if (add(cursor.value)) {
                count++
            }
        }
        return count
    }

    fun toArray(): LongArray {
        val cloned = (LongArray(size()))
        var j = 0
        if (hasEmptyKey) {
            cloned[j++] = 0L
        }

        val keys = this.keys
        val seed = nextIterationSeed()
        val inc: Int = iterationIncrement(seed)
        run {
            var i = 0
            val mask = this.mask
            var slot = seed and mask
            while (i <= mask
            ) {
                val existing: Long
                if ((keys!![slot].also { existing = it }) != 0L) {
                    cloned[j++] = existing
                }
                i++
                slot = (slot + inc) and mask
            }
        }

        return cloned
    }

    /** An alias for the (preferred) [.removeAll].  */
    fun remove(key: Long): Boolean {
        if (((key) == 0L)) {
            val hadEmptyKey = hasEmptyKey
            hasEmptyKey = false
            return hadEmptyKey
        } else {
            val keys = this.keys
            val mask = this.mask
            var slot = hashKey(key) and mask

            var existing: Long
            while ((keys!![slot].also { existing = it }) != 0L) {
                if (((key) == (existing))) {
                    shiftConflictingKeys(slot)
                    return true
                }
                slot = (slot + 1) and mask
            }
            return false
        }
    }

    /**
     * Removes all keys present in a given container.
     *
     * @return Returns the number of elements actually removed as a result of this call.
     */
    fun removeAll(other: LongHashSet): Int {
        val before = size()

        // Try to iterate over the smaller set or over the container that isn't implementing
        // efficient contains() lookup.
        if (other.size() >= size()) {
            if (hasEmptyKey && other.contains(0L)) {
                hasEmptyKey = false
            }

            val keys = this.keys
            var slot = 0
            val max = this.mask
            while (slot <= max) {
                val existing: Long
                if ((keys!![slot].also { existing = it }) != 0L && other.contains(existing)) {
                    // Shift, do not increment slot.
                    shiftConflictingKeys(slot)
                } else {
                    slot++
                }
            }
        } else {
            for (c in other) {
                remove(c.value)
            }
        }

        return before - size()
    }

    fun contains(key: Long): Boolean {
        if (((key) == 0L)) {
            return hasEmptyKey
        } else {
            val keys = this.keys
            val mask = this.mask
            var slot = hashKey(key) and mask
            var existing: Long
            while ((keys!![slot].also { existing = it }) != 0L) {
                if (((key) == (existing))) {
                    return true
                }
                slot = (slot + 1) and mask
            }
            return false
        }
    }

    fun clear() {
        assigned = 0
        hasEmptyKey = false
        Arrays.fill(keys!!, 0L)
    }

    fun release() {
        assigned = 0
        hasEmptyKey = false
        keys = null
        ensureCapacity(DEFAULT_EXPECTED_ELEMENTS)
    }

    val isEmpty: Boolean
        get() = size() == 0

    /**
     * Ensure this container can hold at least the given number of elements without resizing its
     * buffers.
     *
     * @param expectedElements The total number of elements, inclusive.
     */
    fun ensureCapacity(expectedElements: Int) {
        if (expectedElements > resizeAt || keys == null) {
            val prevKeys = this.keys
            allocateBuffers(minBufferSize(expectedElements, loadFactor))
            if (prevKeys != null && !this.isEmpty) {
                rehash(prevKeys)
            }
        }
    }

    fun size(): Int {
        return assigned + (if (hasEmptyKey) 1 else 0)
    }

    override fun hashCode(): Int {
        var h = if (hasEmptyKey) -0x21524111 else 0
        val keys = this.keys
        for (slot in mask downTo 0) {
            val existing: Long
            if ((keys!![slot].also { existing = it }) != 0L) {
                h += BitMixer.mix(existing)
            }
        }
        return h
    }

    override fun equals(other: Any?): Boolean {
        return (this === other)
                || (other != null && this::class == other::class && sameKeys(this::class.cast(other)))
    }

    /** Return true if all keys of some other container exist in this container.  */
    private fun sameKeys(other: LongHashSet): Boolean {
        if (other.size() != size()) {
            return false
        }

        for (c in other) {
            if (!contains(c.value)) {
                return false
            }
        }

        return true
    }

    override fun clone(): LongHashSet {
        val cloned = LongHashSet()
        cloned.keys = keys?.copyOf()
        cloned.hasEmptyKey = hasEmptyKey
        cloned.iterationSeed = ITERATION_SEED.incrementAndFetch()
        // Copy any additional relevant fields if needed
        return cloned
    }

    override fun iterator(): Iterator<LongCursor> {
        return this.EntryIterator() as Iterator<LongCursor>
    }

    override fun ramBytesUsed(): Long {
        return BASE_RAM_BYTES_USED + RamUsageEstimator.sizeOf(keys!!)
    }

    /**
     * Provides the next iteration seed used to build the iteration starting slot and offset
     * increment. This method does not need to be synchronized, what matters is that each thread gets
     * a sequence of varying seeds.
     */
    protected fun nextIterationSeed(): Int {
        return BitMixer.mixPhi(iterationSeed).also { iterationSeed = it }
    }

    /** An iterator implementation for [.iterator].  */
    protected inner class EntryIterator : AbstractIterator<LongCursor>() {
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
            val mask = this@LongHashSet.mask
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
                cursor.value = 0L
                return cursor
            }

            return done()
        }
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
     * Returns a logical "index" of a given key that can be used to speed up follow-up logic in
     * certain scenarios (conditional logic).
     *
     *
     * The semantics of "indexes" are not strictly defined. Indexes may (and typically won't be)
     * contiguous.
     *
     *
     * The index is valid only between modifications (it will not be affected by read-only
     * operations).
     *
     * @see .indexExists
     *
     * @see .indexGet
     *
     * @see .indexInsert
     *
     * @see .indexReplace
     *
     * @param key The key to locate in the set.
     * @return A non-negative value of the logical "index" of the key in the set or a negative value
     * if the key did not exist.
     */
    fun indexOf(key: Long): Int {
        val mask = this.mask
        if (((key) == 0L)) {
            return if (hasEmptyKey) mask + 1 else (mask + 1).inv()
        } else {
            val keys = this.keys
            var slot = hashKey(key) and mask

            var existing: Long
            while ((keys!![slot].also { existing = it }) != 0L) {
                if (((key) == (existing))) {
                    return slot
                }
                slot = (slot + 1) and mask
            }

            return slot.inv()
        }
    }

    /**
     * @see .indexOf
     *
     * @param index The index of a given key, as returned from [.indexOf].
     * @return Returns `true` if the index corresponds to an existing key or false
     * otherwise. This is equivalent to checking whether the index is a positive value (existing
     * keys) or a negative value (non-existing keys).
     */
    fun indexExists(index: Int): Boolean {
        require(index < 0 || index <= mask || (index == mask + 1 && hasEmptyKey))

        return index >= 0
    }

    /**
     * Returns the exact value of the existing key. This method makes sense for sets of objects which
     * define custom key-equality relationship.
     *
     * @see .indexOf
     *
     * @param index The index of an existing key.
     * @return Returns the equivalent key currently stored in the set.
     * @throws AssertionError If assertions are enabled and the index does not correspond to an
     * existing key.
     */
    fun indexGet(index: Int): Long {
        require(index >= 0) { "The index must point at an existing key." }
        require(index <= mask || (index == mask + 1 && hasEmptyKey))

        return keys!![index]
    }

    /**
     * Replaces the existing equivalent key with the given one and returns any previous value stored
     * for that key.
     *
     * @see .indexOf
     *
     * @param index The index of an existing key.
     * @param equivalentKey The key to put in the set as a replacement. Must be equivalent to the key
     * currently stored at the provided index.
     * @return Returns the previous key stored in the set.
     * @throws AssertionError If assertions are enabled and the index does not correspond to an
     * existing key.
     */
    fun indexReplace(index: Int, equivalentKey: Long): Long {
        require(index >= 0) { "The index must point at an existing key." }
        require(index <= mask || (index == mask + 1 && hasEmptyKey))
        require((keys!![index]) == (equivalentKey))

        val previousValue = keys!![index]
        keys!![index] = equivalentKey
        return previousValue
    }

    /**
     * Inserts a key for an index that is not present in the set. This method may help in avoiding
     * double recalculation of the key's hash.
     *
     * @see .indexOf
     *
     * @param index The index of a previously non-existing key, as returned from [.indexOf].
     * @throws AssertionError If assertions are enabled and the index does not correspond to an
     * existing key.
     */
    fun indexInsert(index: Int, key: Long) {
        var index = index
        require(index < 0) { "The index must not point at an existing key." }

        index = index.inv()
        if (((key) == 0L)) {
            require(index == mask + 1)
            require((keys!![index]) == 0L)
            hasEmptyKey = true
        } else {
            require((keys!![index]) == 0L)

            if (assigned == resizeAt) {
                allocateThenInsertThenRehash(index, key)
            } else {
                keys!![index] = key
            }

            assigned++
        }
    }

    /**
     * Removes a key at an index previously acquired from [.indexOf].
     *
     * @see .indexOf
     *
     * @param index The index of the key to remove, as returned from [.indexOf].
     * @throws AssertionError If assertions are enabled and the index does not correspond to an
     * existing key.
     */
    fun indexRemove(index: Int) {
        require(index >= 0) { "The index must point at an existing key." }
        require(index <= mask || (index == mask + 1 && hasEmptyKey))

        if (index > mask) {
            hasEmptyKey = false
        } else {
            shiftConflictingKeys(index)
        }
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
    protected fun rehash(fromKeys: LongArray) {
        require(HashContainers.checkPowerOfTwo(fromKeys.size - 1))

        // Rehash all stored keys into the new buffers.
        val keys = this.keys
        val mask = this.mask
        var existing: Long
        var i = fromKeys.size - 1
        while (--i >= 0) {
            if ((fromKeys[i].also { existing = it }) != 0L) {
                var slot = hashKey(existing) and mask
                while ((keys!![slot]) != 0L) {
                    slot = (slot + 1) and mask
                }
                keys[slot] = existing
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
        try {
            val emptyElementSlot = 1
            this.keys = (LongArray(arraySize + emptyElementSlot))
        } catch (e: /*OutOfMemory*/Error) {
            this.keys = prevKeys
            throw BufferAllocationException(
                "Not enough memory to allocate buffers for rehashing: %,d -> %,d",
                e, if (this.keys == null) 0 else size(), arraySize
            )
        }

        this.resizeAt = expandAtCount(arraySize, loadFactor)
        this.mask = arraySize - 1
    }

    /**
     * This method is invoked when there is a new key to be inserted into the buffer but there is not
     * enough empty slots to do so.
     *
     *
     * New buffers are allocated. If this succeeds, we know we can proceed with rehashing so we
     * assign the pending element to the previous buffer (possibly violating the invariant of having
     * at least one empty slot) and rehash all keys, substituting new buffers at the end.
     */
    protected fun allocateThenInsertThenRehash(slot: Int, pendingKey: Long) {
        require(assigned == resizeAt && ((keys!![slot]) == 0L) && ((pendingKey) != 0L))

        // Try to allocate new buffers first. If we OOM, we leave in a consistent state.
        val prevKeys = this.keys
        allocateBuffers(nextBufferSize(mask + 1, size(), loadFactor))
        require(this.keys!!.size > prevKeys!!.size)

        // We have succeeded at allocating new data so insert the pending key/value at
        // the free slot in the old arrays before rehashing.
        prevKeys[slot] = pendingKey

        // Rehash old keys, including the pending key.
        rehash(prevKeys)
    }

    /** Shift all the slot-conflicting keys allocated to (and including) `slot`.  */
    protected fun shiftConflictingKeys(gapSlot: Int) {
        var gapSlot = gapSlot
        val keys = this.keys
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
                gapSlot = slot
                distance = 0
            }
        }

        // Mark the last found gap slot without a conflict as empty.
        keys[gapSlot] = 0L
        assigned--
    }

    companion object {
        private val BASE_RAM_BYTES_USED: Long = RamUsageEstimator.shallowSizeOfInstance(LongHashSet::class)

        /**
         * Create a set from a variable number of arguments or an array of `long`. The elements
         * are copied from the argument to the internal buffer.
         */
        /*  */
        fun from(vararg elements: Long): LongHashSet {
            val set = LongHashSet(elements.size)
            set.addAll(*elements)
            return set
        }
    }
}
