package org.gnit.lucenekmp.jdkport

import okio.Buffer
import kotlin.math.min

/**
 * A platform-agnostic LongBuffer built on top of a kotlin‑io Buffer.
 *
 * The [capacity] is in numbers of longs (each long is 8 bytes). The
 * [position] and [limit] are expressed in longs. When reading/writing,
 * This version tracks an extra [baseOffset] (in bytes) so that view buffers (via slice())
 * share the same underlying Buffer while translating long indices appropriately.
 *
 * All indices (position, limit, capacity) are in units of longs (8 bytes).
 * we convert indices by multiplying by 8.
 */
class LongBuffer(private val buffer: Buffer, val capacity: Int, private val baseOffset: Long = 0L) : Comparable<LongBuffer> {

    var position: Int = 0
    var limit: Int = capacity
    var order: ByteOrder = ByteOrder.nativeOrder()

    companion object {
        /**
         * Allocates a new LongBuffer with the given capacity (in longs).
         * Internally, it creates a Buffer large enough to hold (capacity * 8) bytes.
         */
        fun allocate(capacity: Int): LongBuffer {
            require(capacity >= 0) { "Capacity must be non-negative" }
            // Create a zero-filled ByteArray to back the Buffer.
            val byteArray = ByteArray(capacity * 8)
            val buf = Buffer().apply { write(byteArray, 0, byteArray.size) }
            return LongBuffer(buf, capacity).clear()
        }

        /**
         * Wraps an existing LongArray into a LongBuffer.
         * The resulting buffer’s capacity and limit are set to array.size.
         */
        fun wrap(array: LongArray): LongBuffer {
            // Convert the long array to a ByteArray.
            val byteArray = ByteArray(array.size * 8)
            val nativeOrder = ByteOrder.nativeOrder()
            for (i in array.indices) {
                val offset = i * 8
                val value = array[i]
                when (nativeOrder) {
                    ByteOrder.BIG_ENDIAN -> {
                        byteArray[offset]     = (value shr 56).toByte()
                        byteArray[offset + 1] = (value shr 48).toByte()
                        byteArray[offset + 2] = (value shr 40).toByte()
                        byteArray[offset + 3] = (value shr 32).toByte()
                        byteArray[offset + 4] = (value shr 24).toByte()
                        byteArray[offset + 5] = (value shr 16).toByte()
                        byteArray[offset + 6] = (value shr 8).toByte()
                        byteArray[offset + 7] = value.toByte()
                    }
                    ByteOrder.LITTLE_ENDIAN -> {
                        byteArray[offset]     = value.toByte()
                        byteArray[offset + 1] = (value shr 8).toByte()
                        byteArray[offset + 2] = (value shr 16).toByte()
                        byteArray[offset + 3] = (value shr 24).toByte()
                        byteArray[offset + 4] = (value shr 32).toByte()
                        byteArray[offset + 5] = (value shr 40).toByte()
                        byteArray[offset + 6] = (value shr 48).toByte()
                        byteArray[offset + 7] = (value shr 56).toByte()
                    }
                }
            }
            val buf = Buffer().apply { write(byteArray, 0, byteArray.size) }
            return LongBuffer(buf, array.size).clear()
        }
    }

    /**
     * Returns the number of longs remaining between position and limit.
     */
    fun remaining(): Int = limit - position

    /**
     * Returns whether there are any elements remaining between position and limit.
     */
    fun hasRemaining(): Boolean = position < limit

    /**
     * Clears this buffer. Sets position to 0 and limit to capacity.
     */
    fun clear(): LongBuffer {
        position = 0
        limit = capacity
        return this
    }

    /**
     * Flips this buffer. Sets limit to current position and position to 0.
     */
    fun flip(): LongBuffer {
        limit = position
        position = 0
        return this
    }

    /**
     * Returns a duplicate LongBuffer that shares the underlying data.
     * The duplicate’s position, limit, and byte order are set to the same values.
     */
    fun duplicate(): LongBuffer {
        val newBuffer = buffer.copy() // Deep copy of the underlying Buffer.
        val dup = LongBuffer(newBuffer, capacity)
        dup.position = position
        dup.limit = limit
        dup.order = order
        return dup
    }

    /**
     * Relative get method.
     * Reads the long at the current position and then increments the position.
     *
     * @return the long value at the current position.
     * @throws BufferUnderflowException if there are no longs remaining.
     */
    fun get(): Long {
        if (position >= limit) throw BufferUnderflowException("No more longs to read")
        val result = get(position)
        position++
        return result
    }

    /**
     * Absolute get method.
     * Reads the long at the specified index (in longs) without changing the current position.
     *
     * @param index the index (in longs) from which to read.
     * @return the long value at the given index.
     * @throws IndexOutOfBoundsException if index is out of bounds.
     */
    fun get(index: Int): Long {
        if (index < 0 || index >= limit)
            throw IndexOutOfBoundsException("Index $index out of bounds (limit: $limit)")
        val byteOffset = baseOffset + (index.toLong() * 8L)
        var result = 0L
        when (order) {
            ByteOrder.BIG_ENDIAN -> {
                for (i in 0 until 8) {
                    result = (result shl 8) or ((buffer.get(byteOffset + i).toInt() and 0xFF).toLong())
                }
            }
            ByteOrder.LITTLE_ENDIAN -> {
                for (i in 0 until 8) {
                    result = result or ((buffer.get(byteOffset + i).toInt() and 0xFF).toLong() shl (i * 8))
                }
            }
        }
        return result
    }

    /**
     * Sets this buffer's position. If the mark is defined and larger than the new position then it is discarded.
     */
    fun position(newPosition: Int): LongBuffer {
        this.position = newPosition
        return this
    }

    /**
     * Sets this buffer's limit. If the position is larger than the new limit then it is set to the new limit.
     * If the mark is defined and larger than the new limit then it is discarded.
     */
    fun limit(newLimit: Int): LongBuffer {
        this.limit = newLimit
        return this
    }

    /**
     * Relative put method.
     * Writes the given long at the current position and then increments the position.
     *
     * @param value the long value to write.
     * @return this LongBuffer.
     * @throws BufferOverflowException if there is no space remaining.
     */
    fun put(value: Long): LongBuffer {
        if (position >= limit) throw BufferOverflowException("No space to write")
        put(position, value)
        position++
        return this
    }

    /**
     * Relative bulk get method.
     *
     * Transfers [length] longs from this buffer into the destination [dst] array,
     * starting at the current position and at the specified [offset] in [dst].
     * After copying, the buffer's position is advanced by [length].
     *
     * @throws BufferUnderflowException if there are fewer than [length] longs remaining.
     * @throws IndexOutOfBoundsException if [offset] or [length] are invalid for [dst].
     */
    fun get(dst: LongArray, offset: Int, length: Int): LongBuffer {
        // Check that offset and length are valid for the dst array.
        if (offset < 0 || length < 0 || offset + length > dst.size)
            throw IndexOutOfBoundsException("offset: $offset, length: $length, array size: ${dst.size}")

        val pos = this.position
        if (length > this.limit - pos)
            throw BufferUnderflowException("Not enough longs remaining: requested $length, available ${this.limit - pos}")

        // Copy each long from the buffer using absolute get.
        for (i in 0 until length) {
            dst[offset + i] = this.get(pos + i)
        }

        // Advance the buffer's position by the number of longs copied.
        this.position = pos + length
        return this
    }

    /**
     * Relative bulk get method.
     *
     * Transfers all longs from this buffer into the destination [dst] array.
     * This method behaves the same as get(dst, 0, dst.size).
     *
     * @throws BufferUnderflowException if there are fewer than dst.size longs remaining.
     */
    fun get(dst: LongArray): LongBuffer = get(dst, 0, dst.size)

    /**
     * Absolute put method.
     * Writes the given long into the buffer at the specified index (in longs).
     *
     * @param index the index at which to write.
     * @param value the long value to write.
     * @return this LongBuffer.
     * @throws IndexOutOfBoundsException if index is out of bounds.
     */
    fun put(index: Int, value: Long): LongBuffer {
        if (index < 0 || index >= limit)
            throw IndexOutOfBoundsException("Index $index out of bounds (limit: $limit)")
        val byteOffset = baseOffset + (index.toLong() * 8L)
        when (order) {
            ByteOrder.BIG_ENDIAN -> {
                buffer.setByteAt(byteOffset,     (value shr 56).toByte())
                buffer.setByteAt(byteOffset + 1, (value shr 48).toByte())
                buffer.setByteAt(byteOffset + 2, (value shr 40).toByte())
                buffer.setByteAt(byteOffset + 3, (value shr 32).toByte())
                buffer.setByteAt(byteOffset + 4, (value shr 24).toByte())
                buffer.setByteAt(byteOffset + 5, (value shr 16).toByte())
                buffer.setByteAt(byteOffset + 6, (value shr 8).toByte())
                buffer.setByteAt(byteOffset + 7, (value).toByte())
            }
            ByteOrder.LITTLE_ENDIAN -> {
                buffer.setByteAt(byteOffset,     (value).toByte())
                buffer.setByteAt(byteOffset + 1, (value shr 8).toByte())
                buffer.setByteAt(byteOffset + 2, (value shr 16).toByte())
                buffer.setByteAt(byteOffset + 3, (value shr 24).toByte())
                buffer.setByteAt(byteOffset + 4, (value shr 32).toByte())
                buffer.setByteAt(byteOffset + 5, (value shr 40).toByte())
                buffer.setByteAt(byteOffset + 6, (value shr 48).toByte())
                buffer.setByteAt(byteOffset + 7, (value shr 56).toByte())
            }
        }
        return this
    }

    /**
     * Creates a new LongBuffer slice that is a view of this buffer's content from
     * the current position to the limit.
     *
     * The new buffer’s capacity and limit will be set to the number of longs remaining,
     * its position will be 0, and its base offset will be adjusted so that index 0 of the
     * slice corresponds to index [position] of the original buffer.
     *
     * Changes to one buffer are visible in the other.
     */
    fun slice(): LongBuffer {
        val remainingLongs = remaining()
        // New base offset is the original base plus (current position * 8).
        val newBaseOffset = baseOffset + (position.toLong() * 8L)
        // Create a new LongBuffer view sharing the same underlying buffer.
        return LongBuffer(buffer, remainingLongs, newBaseOffset).clear().also {
            // The slice's order is the same as the original.
            it.order = this.order
        }
    }

    override fun compareTo(other: LongBuffer): Int {
        val n = min(remaining(), other.remaining())
        for (i in 0 until n) {
            val cmp = get(this.position + i).compareTo(other.get(other.position + i))
            if (cmp != 0) return cmp
        }
        return remaining() - other.remaining()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LongBuffer) return false

        if (this.remaining() != other.remaining()) return false

        val thisPos = this.position
        val otherPos = other.position
        for (i in 0 until remaining()) {
            if (this.get(thisPos + i) != other.get(otherPos + i)) {
                return false
            }
        }
        return true
    }

    override fun hashCode(): Int {
        var result = 1
        val pos = this.position
        for (i in 0 until remaining()) {
            result = 31 * result + get(pos + i).hashCode()
        }
        return result
    }

    override fun toString(): String {
        return "LongBuffer(pos=$position, lim=$limit, cap=$capacity, order=$order)"
    }
}
