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
import org.gnit.lucenekmp.jdkport.Cloneable
import org.gnit.lucenekmp.jdkport.bitCount
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.RamUsageEstimator
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.jvm.JvmOverloads
import kotlin.reflect.cast

/**
 * A hash set of `char`s, implemented using open addressing with linear probing for
 * collision resolution.
 *
 *
 * Mostly forked and trimmed from com.carrotsearch.hppc.CharHashSet
 *
 *
 * github: https://github.com/carrotsearch/hppc release 0.10.0
 *
 * @lucene.internal
 */
@OptIn(ExperimentalAtomicApi::class)
open class CharHashSet @JvmOverloads constructor(expectedElements: Int, loadFactor: Double = DEFAULT_LOAD_FACTOR.toDouble()) :
    Iterable<CharCursor>, Accountable, Cloneable<CharHashSet> {
    /** The hash array holding keys.  */
    var keys: CharArray? = null

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

    init {
        this.loadFactor = verifyLoadFactor(loadFactor)
        iterationSeed = ITERATION_SEED.incrementAndFetch()
        ensureCapacity(expectedElements)
    }

    /** New instance copying elements from another set.  */
    constructor(set: CharHashSet) : this(set.size()) {
        addAll(set)
    }

    fun add(key: Char): Boolean {
        if (key == EMPTY_KEY) {
            require(keys!![mask + 1] == EMPTY_KEY)
            val added = !hasEmptyKey
            hasEmptyKey = true
            return added
        } else {
            val keys = this.keys
            val mask = this.mask
            var slot = hashKey(key) and mask

            var existing: Char
            while ((keys!![slot].also { existing = it }) != EMPTY_KEY) {
                if (key == existing) {
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

    fun addAll(vararg elements: Char): Int {
        ensureCapacity(elements.size)
        var count = 0
        for (e in elements) {
            if (add(e)) {
                count++
            }
        }
        return count
    }

    fun addAll(set: CharHashSet): Int {
        ensureCapacity(set.size())
        return addAll(set as Iterable<CharCursor>)
    }

    fun addAll(iterable: Iterable<CharCursor>): Int {
        var count = 0
        for (cursor in iterable) {
            if (add(cursor.value)) {
                count++
            }
        }
        return count
    }

    fun toArray(): CharArray {
        val cloned = CharArray(size())
        var j = 0
        if (hasEmptyKey) {
            cloned[j++] = EMPTY_KEY
        }

        val keys = this.keys
        val seed = nextIterationSeed()
        val inc = iterationIncrement(seed)
        var i = 0
        val mask = this.mask
        var slot = seed and mask
        while (i <= mask) {
            val existing: Char
            if ((keys!![slot].also { existing = it }) != EMPTY_KEY) {
                cloned[j++] = existing
            }
            i++
            slot = (slot + inc) and mask
        }

        return cloned
    }

    fun remove(key: Char): Boolean {
        if (key == EMPTY_KEY) {
            val hadEmptyKey = hasEmptyKey
            hasEmptyKey = false
            return hadEmptyKey
        } else {
            val keys = this.keys
            val mask = this.mask
            var slot = hashKey(key) and mask

            var existing: Char
            while ((keys!![slot].also { existing = it }) != EMPTY_KEY) {
                if (key == existing) {
                    shiftConflictingKeys(slot)
                    return true
                }
                slot = (slot + 1) and mask
            }
            return false
        }
    }

    fun removeAll(other: CharHashSet): Int {
        val before = size()

        if (other.size() >= size()) {
            if (hasEmptyKey && other.contains(EMPTY_KEY)) {
                hasEmptyKey = false
            }

            val keys = this.keys
            var slot = 0
            val max = this.mask
            while (slot <= max) {
                val existing: Char
                if ((keys!![slot].also { existing = it }) != EMPTY_KEY && other.contains(existing)) {
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

    fun contains(key: Char): Boolean {
        if (key == EMPTY_KEY) {
            return hasEmptyKey
        } else {
            val keys = this.keys
            val mask = this.mask
            var slot = hashKey(key) and mask
            var existing: Char
            while ((keys!![slot].also { existing = it }) != EMPTY_KEY) {
                if (key == existing) {
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
        Arrays.fill(keys!!, EMPTY_KEY)
    }

    fun release() {
        assigned = 0
        hasEmptyKey = false
        keys = null
        ensureCapacity(DEFAULT_EXPECTED_ELEMENTS)
    }

    val isEmpty: Boolean
        get() = size() == 0

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
        return assigned + if (hasEmptyKey) 1 else 0
    }

    override fun hashCode(): Int {
        var h = if (hasEmptyKey) -0x21524111 else 0
        val keys = this.keys
        for (slot in mask downTo 0) {
            val existing: Char
            if ((keys!![slot].also { existing = it }) != EMPTY_KEY) {
                h += BitMixer.mix(existing.code)
            }
        }
        return h
    }

    override fun equals(other: Any?): Boolean {
        return (this === other)
                || (other != null && this::class == other::class && sameKeys(this::class.cast(other)))
    }

    private fun sameKeys(other: CharHashSet): Boolean {
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

    override fun clone(): CharHashSet {
        return CharHashSet(this)
    }

    override fun iterator(): Iterator<CharCursor> {
        return EntryIterator() as Iterator<CharCursor>
    }

    override fun ramBytesUsed(): Long {
        return BASE_RAM_BYTES_USED + RamUsageEstimator.sizeOf(keys!!)
    }

    protected fun nextIterationSeed(): Int {
        return BitMixer.mixPhi(iterationSeed).also { iterationSeed = it }
    }

    protected inner class EntryIterator : AbstractIterator<CharCursor>() {
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
            val mask = this@CharHashSet.mask
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

    protected fun hashKey(key: Char): Int {
        require(key != EMPTY_KEY)
        return BitMixer.mixPhi(key.code)
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
                if (key == existing) {
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

    fun indexGet(index: Int): Char {
        require(index >= 0) { "The index must point at an existing key." }
        require(index <= mask || (index == mask + 1 && hasEmptyKey))

        return keys!![index]
    }

    fun indexReplace(index: Int, equivalentKey: Char): Char {
        require(index >= 0) { "The index must point at an existing key." }
        require(index <= mask || (index == mask + 1 && hasEmptyKey))
        require(keys!![index] == equivalentKey)

        val previousValue = keys!![index]
        keys!![index] = equivalentKey
        return previousValue
    }

    fun indexInsert(index: Int, key: Char) {
        var mutableIndex = index
        require(mutableIndex < 0) { "The index must not point at an existing key." }

        mutableIndex = mutableIndex.inv()
        if (key == EMPTY_KEY) {
            require(mutableIndex == mask + 1)
            require(keys!![mutableIndex] == EMPTY_KEY)
            hasEmptyKey = true
        } else {
            require(keys!![mutableIndex] == EMPTY_KEY)

            if (assigned == resizeAt) {
                allocateThenInsertThenRehash(mutableIndex, key)
            } else {
                keys!![mutableIndex] = key
            }

            assigned++
        }
    }

    fun indexRemove(index: Int) {
        require(index >= 0) { "The index must point at an existing key." }
        require(index <= mask || (index == mask + 1 && hasEmptyKey))

        if (index > mask) {
            hasEmptyKey = false
        } else {
            shiftConflictingKeys(index)
        }
    }

    protected open fun verifyLoadFactor(loadFactor: Double): Double {
        checkLoadFactor(loadFactor, MIN_LOAD_FACTOR.toDouble(), MAX_LOAD_FACTOR.toDouble())
        return loadFactor
    }

    protected fun rehash(fromKeys: CharArray) {
        require(HashContainers.checkPowerOfTwo(fromKeys.size - 1))

        val keys = this.keys
        val mask = this.mask
        var existing: Char
        var i = fromKeys.size - 1
        while (--i >= 0) {
            if ((fromKeys[i].also { existing = it }) != EMPTY_KEY) {
                var slot = hashKey(existing) and mask
                while (keys!![slot] != EMPTY_KEY) {
                    slot = (slot + 1) and mask
                }
                keys[slot] = existing
            }
        }
    }

    protected open fun allocateBuffers(arraySize: Int) {
        require(Int.bitCount(arraySize) == 1)

        val prevKeys = this.keys
        try {
            val emptyElementSlot = 1
            this.keys = CharArray(arraySize + emptyElementSlot)
        } catch (e: Error) {
            this.keys = prevKeys
            throw BufferAllocationException(
                "Not enough memory to allocate buffers for rehashing: %,d -> %,d",
                e,
                if (this.keys == null) 0 else size(),
                arraySize
            )
        }

        this.resizeAt = expandAtCount(arraySize, loadFactor)
        this.mask = arraySize - 1
    }

    protected fun allocateThenInsertThenRehash(slot: Int, pendingKey: Char) {
        require(assigned == resizeAt && keys!![slot] == EMPTY_KEY && pendingKey != EMPTY_KEY)

        val prevKeys = this.keys
        allocateBuffers(nextBufferSize(mask + 1, size(), loadFactor))
        require(this.keys!!.size > prevKeys!!.size)

        prevKeys[slot] = pendingKey
        rehash(prevKeys)
    }

    protected fun shiftConflictingKeys(gapSlot: Int) {
        var mutableGapSlot = gapSlot
        val keys = this.keys
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
                mutableGapSlot = slot
                distance = 0
            }
        }

        keys[mutableGapSlot] = EMPTY_KEY
        assigned--
    }

    companion object {
        private val BASE_RAM_BYTES_USED: Long = RamUsageEstimator.shallowSizeOfInstance(CharHashSet::class)
        private const val EMPTY_KEY: Char = '\u0000'

        fun from(vararg elements: Char): CharHashSet {
            val set = CharHashSet(elements.size)
            set.addAll(*elements)
            return set
        }
    }
}
