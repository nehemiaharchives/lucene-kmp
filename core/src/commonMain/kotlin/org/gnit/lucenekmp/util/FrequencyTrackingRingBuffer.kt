package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.highestOneBit
import kotlin.math.max

/**
 * A ring buffer that tracks the frequency of the integers that it contains. This is typically
 * useful to track the hash codes of popular recently-used items.
 *
 *
 * This data-structure requires 22 bytes per entry on average (between 16 and 28).
 *
 * @lucene.internal
 */
class FrequencyTrackingRingBuffer(maxSize: Int, sentinel: Int) : Accountable {
    private val logger = io.github.oshai.kotlinlogging.KotlinLogging.logger {}
    private val maxSize: Int
    private val buffer: IntArray
    private var position: Int
    private val frequencies: IntBag

    /**
     * Create a new ring buffer that will contain at most `maxSize` items. This buffer will
     * initially contain `maxSize` times the `sentinel` value.
     */
    init {
        require(maxSize >= 2) { "maxSize must be at least 2" }
        this.maxSize = maxSize
        buffer = IntArray(maxSize)
        position = 0
        frequencies = IntBag(maxSize)

        Arrays.fill(buffer, sentinel)
        for (i in 0..<maxSize) {
            frequencies.add(sentinel)
        }
        assert(frequencies.frequency(sentinel) == maxSize)
    }

    override fun ramBytesUsed(): Long {
        return BASE_RAM_BYTES_USED + frequencies.ramBytesUsed() + RamUsageEstimator.sizeOf(buffer)
    }

    /**
     * Add a new item to this ring buffer, potentially removing the oldest entry from this buffer if
     * it is already full.
     */
    fun add(i: Int) {
        // remove the previous value
        val removed = buffer[position]
        //logger.debug { "[FrequencyTrackingRingBuffer.add] remove start removed=$removed pos=$position" }
        val removedFromBag = frequencies.remove(removed)
        //logger.debug { "[FrequencyTrackingRingBuffer.add] remove done removed=$removed ok=$removedFromBag" }
        assert(removedFromBag)
        // add the new value
        buffer[position] = i
        //logger.debug { "[FrequencyTrackingRingBuffer.add] add start value=$i pos=$position" }
        frequencies.add(i)
        //logger.debug { "[FrequencyTrackingRingBuffer.add] add done value=$i pos=$position" }
        // increment the position
        position += 1
        if (position == maxSize) {
            position = 0
        }
    }

    /** Returns the frequency of the provided key in the ring buffer.  */
    fun frequency(key: Int): Int {
        return frequencies.frequency(key)
    }

    // pkg-private for testing
    fun asFrequencyMap(): MutableMap<Int, Int> {
        return frequencies.asMap()
    }

    /**
     * A bag of integers. Since in the context of the ring buffer the maximum size is known up-front
     * there is no need to worry about resizing the underlying storage.
     */
    private class IntBag(maxSize: Int) : Accountable {
        private val keys: IntArray
        private val freqs: IntArray
        private val mask: Int

        init {
            // load factor of 2/3
            var capacity = max(2, maxSize * 3 / 2)
            // round up to the next power of two
            capacity = Int.highestOneBit(capacity - 1) shl 1
            assert(capacity > maxSize)
            keys = IntArray(capacity)
            freqs = IntArray(capacity)
            mask = capacity - 1
        }

        override fun ramBytesUsed(): Long {
            return BASE_RAM_BYTES_USED + RamUsageEstimator.sizeOf(keys) + RamUsageEstimator.sizeOf(
                freqs
            )
        }

        /** Return the frequency of the give key in the bag.  */
        fun frequency(key: Int): Int {
            var slot = key and mask
            while (true) {
                if (keys[slot] == key) {
                    return freqs[slot]
                } else if (freqs[slot] == 0) {
                    return 0
                }
                slot = (slot + 1) and mask
            }
        }

        /** Increment the frequency of the given key by 1 and return its new frequency.  */
        fun add(key: Int): Int {
            var slot = key and mask
            while (true) {
                if (freqs[slot] == 0) {
                    keys[slot] = key
                    return 1.also { freqs[slot] = it }
                } else if (keys[slot] == key) {
                    return ++freqs[slot]
                }
                slot = (slot + 1) and mask
            }
        }

        /**
         * Decrement the frequency of the given key by one, or do nothing if the key is not present in
         * the bag. Returns true iff the key was contained in the bag.
         */
        fun remove(key: Int): Boolean {
            var slot = key and mask
            while (true) {
                if (freqs[slot] == 0) {
                    // no such key in the bag
                    return false
                } else if (keys[slot] == key) {
                    val newFreq: Int = --freqs[slot]
                    if (newFreq == 0) { // removed
                        relocateAdjacentKeys(slot)
                    }
                    return true
                }
                slot = (slot + 1) and mask
            }
        }

        fun relocateAdjacentKeys(freeSlot: Int) {
            var freeSlot = freeSlot
            var slot = (freeSlot + 1) and mask
            while (true) {
                val freq = freqs[slot]
                if (freq == 0) {
                    // end of the collision chain, we're done
                    break
                }
                val key = keys[slot]
                // the slot where <code>key</code> should be if there were no collisions
                val expectedSlot = key and mask
                // if the free slot is between the expected slot and the slot where the
                // key is, then we can relocate there
                if (between(expectedSlot, slot, freeSlot)) {
                    keys[freeSlot] = key
                    freqs[freeSlot] = freq
                    // slot is the new free slot
                    freqs[slot] = 0
                    freeSlot = slot
                }
                slot = (slot + 1) and mask
            }
        }

        fun asMap(): MutableMap<Int, Int> {
            val map: MutableMap<Int, Int> = HashMap()
            for (i in keys.indices) {
                if (freqs[i] > 0) {
                    map.put(keys[i], freqs[i])
                }
            }
            return map
        }

        companion object {
            private val BASE_RAM_BYTES_USED: Long =
                RamUsageEstimator.shallowSizeOfInstance(IntBag::class)

            /**
             * Given a chain of occupied slots between `chainStart` and `chainEnd`,
             * return whether `slot` is between the start and end of the chain.
             */
            private fun between(chainStart: Int, chainEnd: Int, slot: Int): Boolean {
                return if (chainStart <= chainEnd) {
                    chainStart <= slot && slot <= chainEnd
                } else {
                    // the chain is across the end of the array
                    slot >= chainStart || slot <= chainEnd
                }
            }
        }
    }

    companion object {
        private val BASE_RAM_BYTES_USED: Long =
            RamUsageEstimator.shallowSizeOfInstance(FrequencyTrackingRingBuffer::class)
    }
}
