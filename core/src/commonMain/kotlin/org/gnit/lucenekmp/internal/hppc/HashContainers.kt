package org.gnit.lucenekmp.internal.hppc

import org.gnit.lucenekmp.util.BitUtil.nextHighestPowerOfTwo
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.ceil
import kotlin.math.min

/**
 * Constants for primitive maps.
 *
 * @lucene.internal
 */
object HashContainers {


    const val DEFAULT_EXPECTED_ELEMENTS: Int = 4

    const val DEFAULT_LOAD_FACTOR: Float = 0.75f

    /** Minimal sane load factor (99 empty slots per 100).  */
    val MIN_LOAD_FACTOR: Float = 1 / 100.0f

    /** Maximum sane load factor (1 empty slot per 100).  */
    val MAX_LOAD_FACTOR: Float = 99 / 100.0f

    /** Minimum hash buffer size.  */
    const val MIN_HASH_ARRAY_LENGTH: Int = 4

    /**
     * Maximum array size for hash containers (power-of-two and still allocable in Java, not a
     * negative int).
     */
    val MAX_HASH_ARRAY_LENGTH: Int = -0x80000000 ushr 1

    @OptIn(ExperimentalAtomicApi::class)
    val ITERATION_SEED: AtomicInt = AtomicInt(0)

    fun iterationIncrement(seed: Int): Int {
        return 29 + ((seed and 7) shl 1) // Small odd integer.
    }

    fun nextBufferSize(arraySize: Int, elements: Int, loadFactor: Double): Int {
        require(checkPowerOfTwo(arraySize))
        if (arraySize == MAX_HASH_ARRAY_LENGTH) {
            throw BufferAllocationException(
                "Maximum array size exceeded for this load factor (elements: %d, load factor: %f)",
                elements, loadFactor
            )
        }

        return arraySize shl 1
    }

    fun expandAtCount(arraySize: Int, loadFactor: Double): Int {
        require(checkPowerOfTwo(arraySize))
        // Take care of hash container invariant (there has to be at least one empty slot to ensure
        // the lookup loop finds either the element or an empty slot).
        return min(arraySize - 1, ceil(arraySize * loadFactor).toInt())
    }

    fun checkPowerOfTwo(arraySize: Int): Boolean {
        // These are internals, we can just assert without retrying.
        require(arraySize > 1)
        require(nextHighestPowerOfTwo(arraySize) == arraySize)
        return true
    }

    fun minBufferSize(elements: Int, loadFactor: Double): Int {
        require(elements >= 0) { "Number of elements must be >= 0: $elements" }

        var length = ceil(elements / loadFactor).toLong()
        if (length == elements.toLong()) {
            length++
        }
        length = kotlin.math.max(MIN_HASH_ARRAY_LENGTH.toLong(), nextHighestPowerOfTwo(length))

        if (length > MAX_HASH_ARRAY_LENGTH) {
            throw BufferAllocationException(
                "Maximum array size exceeded for this load factor (elements: %d, load factor: %f)",
                elements, loadFactor
            )
        }

        return length.toInt()
    }

    fun checkLoadFactor(
        loadFactor: Double, minAllowedInclusive: Double, maxAllowedInclusive: Double
    ) {
        if (loadFactor < minAllowedInclusive || loadFactor > maxAllowedInclusive) {
            throw BufferAllocationException(
                "The load factor should be in range [%.2f, %.2f]: %f",
                minAllowedInclusive, maxAllowedInclusive, loadFactor
            )
        }
    }
}