package org.gnit.lucenekmp.jdkport

import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlin.math.min

// (If you do not have these exceptions in common code, you may define your own.)
class BufferUnderflowException(message: String = "") : RuntimeException(message)
class BufferOverflowException(message: String = "") : RuntimeException(message)

// A simple re-implementation of java.nio.ByteBuffer using a kotlin-io Buffer as the backing store.
class ByteBuffer private constructor(
    private val buffer: Buffer,
    /** The fixed capacity of this ByteBuffer. */
    val capacity: Int
) : Comparable<ByteBuffer> {

    /** The current position. Must be between 0 and limit. */
    var position: Int = 0
        set(value) {
            require(value in 0..limit) { "Position ($value) out of bounds (0..$limit)" }
            field = value
            // Invalidate mark if position becomes less than mark.
            if (mark != -1 && mark > field) mark = -1
        }

    /** The limit, i.e. the upper bound (exclusive) for read/writes. */
    var limit: Int = capacity
        set(value) {
            require(value in 0..capacity) { "Limit ($value) out of bounds (0..$capacity)" }
            field = value
            if (position > field) position = field
            if (mark != -1 && mark > field) mark = -1
        }

    /** The mark. When not set, equals -1. */
    private var mark: Int = -1

    /** The byte order. Defaults to BIG_ENDIAN. */

    // -- Other char stuff --
    // -- Other byte stuff: Access to binary data --
    var bigEndian // package-private
            : Boolean = true
    var nativeByteOrder // package-private
            : Boolean = (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN)

    var order: ByteOrder = ByteOrder.BIG_ENDIAN

    fun order(): ByteOrder? {
        return if (bigEndian) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN
    }

    /**
     * Sets this buffer's limit.
     *
     * If the current position is greater than the new limit, the position is set to the new limit.
     * If the mark is defined (i.e. mark != -1) and is greater than the new limit, it is discarded.
     *
     * @param newLimit the new limit (in int); must be non-negative and no larger than capacity.
     * @return this ByteBuffer.
     * @throws IllegalArgumentException if newLimit is negative or greater than capacity.
     */
    fun limit(newLimit: Int): ByteBuffer {
        if (newLimit < 0 || newLimit > capacity) {
            throw IllegalArgumentException("newLimit ($newLimit) out of bounds (0..$capacity)")
        }
        limit = newLimit
        if (position > newLimit) {
            position = newLimit
        }
        if (mark > newLimit) {
            mark = -1
        }
        return this
    }


    /**
     * Modifies this buffer's byte order.
     *
     * @param  bo
     * The new byte order,
     * either [BIG_ENDIAN][ByteOrder.BIG_ENDIAN]
     * or [LITTLE_ENDIAN][ByteOrder.LITTLE_ENDIAN]
     *
     * @return  This buffer
     */
    fun order(bo: ByteOrder): ByteBuffer {
        bigEndian = (bo == ByteOrder.BIG_ENDIAN)
        nativeByteOrder =
            (bigEndian == (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN))
        return this
    }

    // --- Public API methods mimicking java.nio.ByteBuffer ---

    /** Relative get: reads the byte at the current position then increments it. */
    fun get(): Byte {
        if (position >= limit) throw BufferUnderflowException("Not enough bytes to read at position $position with limit $limit")
        val b = buffer.get(position.toLong())
        position++
        return b
    }

    /** Absolute get: returns the byte at the given index (without modifying position). */
    fun get(index: Int): Byte {
        if (index !in 0 until limit)
            throw IndexOutOfBoundsException("Index ($index) out of bounds (0..${limit - 1})")
        return buffer.get(index.toLong())
    }

    /** Relative put: writes a byte at the current position then increments it. */
    fun put(b: Byte): ByteBuffer {
        if (position >= limit) throw BufferOverflowException("Not enough space to write at position $position with limit $limit")
        buffer.setByteAt(position.toLong(), b)
        position++
        return this
    }

    /** Absolute put: writes a byte at the specified index. */
    fun put(index: Int, b: Byte): ByteBuffer {
        if (index !in 0 until limit)
            throw IndexOutOfBoundsException("Index ($index) out of bounds (0..${limit - 1})")
        buffer.setByteAt(index.toLong(), b)
        return this
    }

    /** Bulk get: transfers remaining bytes into the given destination array. */
    fun get(dst: ByteArray, offset: Int = 0, length: Int = dst.size - offset): ByteBuffer {
        require(offset >= 0 && length >= 0 && offset + length <= dst.size)
        if (length > remaining())
            throw BufferUnderflowException("Not enough bytes remaining to read $length bytes (only ${remaining()} available)")
        for (i in 0 until length) {
            dst[offset + i] = get()
        }
        return this
    }

    /** Bulk put: transfers bytes from the source array into this buffer. */
    fun put(src: ByteArray, offset: Int = 0, length: Int = src.size - offset): ByteBuffer {
        require(offset >= 0 && length >= 0 && offset + length <= src.size)
        if (length > remaining())
            throw BufferOverflowException("Not enough space remaining to write $length bytes (only ${remaining()} available)")
        for (i in 0 until length) {
            put(src[offset + i])
        }
        return this
    }

    /**
     * Absolute get method for reading a long value.
     *
     * Reads eight bytes starting at [index] and composes them into a long value according
     * to the current byte order. The index is in byte units.
     *
     * @param index The index from which the long will be read.
     * @return The long value at the given index.
     * @throws IndexOutOfBoundsException if there are fewer than 8 bytes available starting at [index].
     */
    fun getLong(index: Int): Long {
        if (index < 0 || (limit - index) < 8) {
            throw IndexOutOfBoundsException("Index $index out of bounds: need 8 bytes from index (limit: $limit)")
        }
        val offset = index.toLong()
        var result = 0L
        if (order == ByteOrder.BIG_ENDIAN) {
            for (i in 0 until 8) {
                result = (result shl 8) or ((buffer.get(offset + i).toInt() and 0xFF).toLong())
            }
        } else { // LITTLE_ENDIAN
            for (i in 7 downTo 0) {
                result = (result shl 8) or ((buffer.get(offset + i).toInt() and 0xFF).toLong())
            }
        }
        return result
    }

    /**
     * Relative get method for reading a long value.
     *
     * Reads the next eight bytes at this buffer's current position, composing them into a
     * long value according to the current byte order, and then increments the position by eight.
     *
     * @return The long value at the current position.
     * @throws BufferUnderflowException If there are fewer than eight bytes remaining in this buffer.
     */
    fun getLong(): Long {
        if (remaining() < 8)
            throw BufferUnderflowException("Not enough bytes remaining to read a long (need 8, have ${remaining()})")
        val offset = position.toLong()
        var result = 0L
        if (order == ByteOrder.BIG_ENDIAN) {
            for (i in 0 until 8) {
                result = (result shl 8) or ((buffer.get(offset + i).toInt() and 0xFF).toLong())
            }
        } else {
            for (i in 7 downTo 0) {
                result = (result shl 8) or ((buffer.get(offset + i).toInt() and 0xFF).toLong())
            }
        }
        position += 8
        return result
    }


    fun putLong(value: Long): ByteBuffer {
        // Ensure there are at least 8 bytes left between position and limit
        if (remaining() < 8) {
            throw BufferOverflowException("Not enough space to write 8 bytes at position $position with limit $limit")
        }

        // Write according to the current byte order
        if (order == ByteOrder.BIG_ENDIAN) {
            buffer.setByteAt(position.toLong(),     (value shr 56).toByte())
            buffer.setByteAt(position.toLong() + 1, (value shr 48).toByte())
            buffer.setByteAt(position.toLong() + 2, (value shr 40).toByte())
            buffer.setByteAt(position.toLong() + 3, (value shr 32).toByte())
            buffer.setByteAt(position.toLong() + 4, (value shr 24).toByte())
            buffer.setByteAt(position.toLong() + 5, (value shr 16).toByte())
            buffer.setByteAt(position.toLong() + 6, (value shr  8).toByte())
            buffer.setByteAt(position.toLong() + 7, (value        ).toByte())
        } else {
            // LITTLE_ENDIAN
            buffer.setByteAt(position.toLong(),     (value        ).toByte())
            buffer.setByteAt(position.toLong() + 1, (value shr  8).toByte())
            buffer.setByteAt(position.toLong() + 2, (value shr 16).toByte())
            buffer.setByteAt(position.toLong() + 3, (value shr 24).toByte())
            buffer.setByteAt(position.toLong() + 4, (value shr 32).toByte())
            buffer.setByteAt(position.toLong() + 5, (value shr 40).toByte())
            buffer.setByteAt(position.toLong() + 6, (value shr 48).toByte())
            buffer.setByteAt(position.toLong() + 7, (value shr 56).toByte())
        }

        // Advance the position by 8 bytes
        position += 8
        return this
    }


    /** Returns the number of bytes remaining between position and limit. */
    fun remaining(): Int = limit - position

    /** Sets the mark at the current position. */
    fun mark(): ByteBuffer {
        mark = position
        return this
    }

    /** Resets the position to the previously set mark. */
    fun reset(): ByteBuffer {
        if (mark == -1) throw IOException("Mark has not been set")
        position = mark
        return this
    }

    /** Clears the buffer: sets position to 0, limit to capacity, and mark to -1. */
    fun clear(): ByteBuffer {
        position = 0
        limit = capacity
        mark = -1
        return this
    }

    /** Flips the buffer: sets limit to current position and resets position to 0. */
    fun flip(): ByteBuffer {
        limit = position
        position = 0
        mark = -1
        return this
    }

    /** Rewinds the buffer: resets position to 0 without changing limit. */
    fun rewind(): ByteBuffer {
        position = 0
        mark = -1
        return this
    }

    /** Creates a new buffer that shares this buffer’s content but has independent position, limit, and mark. */
    fun duplicate(): ByteBuffer {
        // For simplicity we assume the underlying Buffer supports copy (deep copy of segments)
        val copy = buffer.copy()
        val dup = ByteBuffer(copy, capacity)
        dup.position = this.position
        dup.limit = this.limit
        dup.order = this.order
        // mark is not copied
        return dup
    }

    /** Creates a new buffer that is a view of this buffer's content between position and limit. */
    fun slice(): ByteBuffer {
        val remaining = remaining()
        val sliceBuffer = Buffer()
        buffer.copyTo(sliceBuffer, position.toLong(), position.toLong() + remaining)

        val bb = ByteBuffer(sliceBuffer, capacity = remaining)
        bb.clear()
        bb.limit = remaining
        sliceBuffer.clear()

        return bb
    }

    /** Compares the remaining bytes lexicographically. */
    override fun compareTo(other: ByteBuffer): Int {
        val n = min(this.remaining(), other.remaining())
        for (i in 0 until n) {
            val cmp = (this.get(this.position + i).toInt() and 0xff) - (other.get(other.position + i).toInt() and 0xff)
            if (cmp != 0) return cmp
        }
        return this.remaining() - other.remaining()
    }

    // --- Companion object with factory methods ---
    companion object {
        /**
         * Allocates a new byte buffer with the given capacity.
         * The buffer’s initial position is 0, its limit is its capacity, and its order is BIG_ENDIAN.
         */
        fun allocate(capacity: Int): ByteBuffer {
            require(capacity >= 0) { "Capacity must be non-negative" }
            // Create a backing Buffer and fill it with zeros up to capacity.
            val backing = Buffer()
            backing.write(ByteArray(capacity)) // writes capacity zeros
            return ByteBuffer(backing, capacity).clear()
        }

        /**
         * Wraps the given byte array into a byte buffer.
         * The new buffer’s capacity will be array.size, its position will be 0, and its limit will be array.size.
         */
        fun wrap(array: ByteArray, offset: Int = 0, length: Int = array.size - offset): ByteBuffer {
            require(offset in 0..array.size)
            require(length in 0..(array.size - offset))
            val backing = Buffer()
            // Write only the specified portion.
            backing.write(array, offset, offset + length)
            val bb = ByteBuffer(backing, array.size)
            bb.position = offset
            bb.limit = offset + length
            return bb
        }
    }
}

/**
 * Extension function for Buffer to set a byte at an absolute position.
 *
 * This is a simpler implementation using Buffer's existing API that doesn't require
 * direct access to internal segments.
 */
fun Buffer.setByteAt(position: Long, value: Byte) {
    require(position >= 0) { "Position must be non-negative" }
    require(position < size) { "Position $position is beyond buffer size $size" }

    // Create a temporary copy to preserve the buffer state
    val copy = copy()

    // Clear this buffer
    clear()

    // Write the first portion unchanged
    if (position > 0) {
        copy.copyTo(this, 0, position)
    }

    // Write the modified byte
    writeByte(value)

    // Skip the byte in the original buffer
    copy.skip(position + 1)

    // Write the remainder of the original buffer
    copy.copyTo(this)

    // Cleanup
    copy.close()
}