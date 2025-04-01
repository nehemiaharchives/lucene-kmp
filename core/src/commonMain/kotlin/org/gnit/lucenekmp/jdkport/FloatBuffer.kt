package org.gnit.lucenekmp.jdkport

import kotlinx.io.Buffer
import kotlinx.io.EOFException
import kotlin.math.min

// Assuming BufferOverflowException and BufferUnderflowException are defined
// in the same package or imported correctly. If not, uncomment these:
// class BufferOverflowException(message: String = "") : RuntimeException(message)
// class BufferUnderflowException(message: String = "") : RuntimeException(message)


/**
 * ported from java.nio.FloatBuffer
 *
 * A platform-agnostic FloatBuffer built on top of a kotlin‑io Buffer.
 *
 * The [capacity] is in numbers of floats (each float is 4 bytes). The
 * [position] and [limit] are expressed in floats. When reading/writing,
 * This version tracks an extra [baseOffset] (in bytes) so that view buffers (via slice())
 * share the same underlying Buffer while translating float indices appropriately.
 *
 * All indices (position, limit, capacity) are in units of floats (4 bytes).
 * we convert indices by multiplying by 4.
 */
class FloatBuffer(private val buffer: Buffer, val capacity: Int, private val baseOffset: Long = 0L) :
    Comparable<FloatBuffer> {

    var position: Int = 0
    var limit: Int = capacity
    var order: ByteOrder = ByteOrder.nativeOrder()

    companion object {
        private const val BYTES_PER_FLOAT = 4

        /**
         * Allocates a new FloatBuffer with the given capacity (in floats).
         * Internally, it creates a Buffer large enough to hold (capacity * 4) bytes.
         */
        fun allocate(capacity: Int): FloatBuffer {
            require(capacity >= 0) { "Capacity must be non-negative" }
            // Create a zero-filled ByteArray to back the Buffer.
            // Use a temporary buffer to easily write zeros.
            val buf = Buffer()
            buf.write(ByteArray(capacity * BYTES_PER_FLOAT)) // Allocate space
            buf.clear() // Reset buffer position/limit after writing zeros (important!)
            // Re-read the zeroed bytes into the actual buffer we'll use
            // This ensures the buffer size is correctly managed by kotlinx.io
            val finalBuf = Buffer().apply { transferFrom(buf) }

            // Alternatively, simpler approach if direct zero-filling is okay:
            // val byteArray = ByteArray(capacity * BYTES_PER_FLOAT)
            // val finalBuf = Buffer().apply { write(byteArray, 0, byteArray.size) }

            return FloatBuffer(finalBuf, capacity).clear()
        }

        /**
         * Wraps an existing FloatArray into a FloatBuffer.
         * The resulting buffer’s capacity and limit are set to array.size.
         * The buffer's byte order defaults to BIG_ENDIAN for wrapping,
         * similar to how the LongBuffer wrap was implemented.
         */
        fun wrap(array: FloatArray): FloatBuffer {
            val byteSize = array.size * BYTES_PER_FLOAT
            val buf = Buffer()
            // Default to BIG_ENDIAN for the wrapping process as in LongBuffer.wrap
            // The user can change the order later if needed.
            val tempOrder = ByteOrder.BIG_ENDIAN

            for (value in array) {
                val bits = value.toBits() // Get IEEE 754 representation
                when (tempOrder) {
                    ByteOrder.BIG_ENDIAN -> {
                        buf.writeByte((bits shr 24).toByte())
                        buf.writeByte((bits shr 16).toByte())
                        buf.writeByte((bits shr 8).toByte())
                        buf.writeByte(bits.toByte())
                    }

                    ByteOrder.LITTLE_ENDIAN -> {
                        buf.writeByte(bits.toByte())
                        buf.writeByte((bits shr 8).toByte())
                        buf.writeByte((bits shr 16).toByte())
                        buf.writeByte((bits shr 24).toByte())
                    }
                }
            }
            // Ensure the wrapped buffer uses the system's native order by default
            // or allow setting it explicitly. Here, we'll match LongBuffer's behavior.
            return FloatBuffer(buf, array.size).clear().apply {
                order = ByteOrder.nativeOrder() // Set default order after creation
            }
        }
    }

    /**
     * Returns the number of floats remaining between position and limit.
     */
    fun remaining(): Int = limit - position

    /**
     * Returns whether there are any elements remaining between position and limit.
     */
    fun hasRemaining(): Boolean = position < limit

    /**
     * Clears this buffer. Sets position to 0 and limit to capacity.
     */
    fun clear(): FloatBuffer {
        position = 0
        limit = capacity
        // mark = -1 // If implementing mark/reset
        return this
    }

    /**
     * Flips this buffer. Sets limit to current position and position to 0.
     */
    fun flip(): FloatBuffer {
        limit = position
        position = 0
        // mark = -1 // If implementing mark/reset
        return this
    }

    /**
     * Returns a duplicate FloatBuffer that shares the underlying data buffer reference
     * but has independent position, limit, and mark values.
     * Changes to the underlying data will be visible in both buffers.
     *
     * NOTE: This differs from the provided LongBuffer's `duplicate` which deep copies
     * the underlying buffer. This implementation aligns more closely with Java NIO's
     * `duplicate()` behavior for shared data. If a deep copy is needed, use `copy()`
     * or allocate+copy manually.
     */
    fun duplicate(): FloatBuffer {
        // Share the underlying buffer, create new state
        val dup = FloatBuffer(this.buffer, this.capacity, this.baseOffset)
        dup.position = this.position
        dup.limit = this.limit
        dup.order = this.order
        // dup.mark = this.mark // If implementing mark/reset
        return dup
    }

    /**
     * Relative get method.
     * Reads the float at the current position and then increments the position.
     *
     * @return the float value at the current position.
     * @throws BufferUnderflowException if there are no floats remaining.
     */
    fun get(): Float {
        if (position >= limit) throw BufferUnderflowException("position=$position, limit=$limit")
        val result = get(position) // Use absolute get
        position++
        return result
    }

    /**
     * Absolute get method.
     * Reads the float at the specified index (in floats) without changing the current position.
     * Incorporates the `baseOffset` for sliced buffers.
     *
     * @param index the index (in floats) from which to read.
     * @return the float value at the given index.
     * @throws IndexOutOfBoundsException if index is out of bounds [0, limit).
     */
    fun get(index: Int): Float {
        if (index < 0 || index >= limit) {
            throw IndexOutOfBoundsException("index=$index, limit=$limit")
        }
        // Calculate the actual byte offset in the underlying buffer
        val byteOffset = baseOffset + index.toLong() * BYTES_PER_FLOAT
        var bits = 0
        try {
            when (order) {
                ByteOrder.BIG_ENDIAN -> {
                    bits = (buffer[byteOffset].toInt() and 0xFF) shl 24 or
                            ((buffer[byteOffset + 1].toInt() and 0xFF) shl 16) or
                            ((buffer[byteOffset + 2].toInt() and 0xFF) shl 8) or
                            (buffer[byteOffset + 3].toInt() and 0xFF)
                }

                ByteOrder.LITTLE_ENDIAN -> {
                    bits = (buffer[byteOffset].toInt() and 0xFF) or
                            ((buffer[byteOffset + 1].toInt() and 0xFF) shl 8) or
                            ((buffer[byteOffset + 2].toInt() and 0xFF) shl 16) or
                            ((buffer[byteOffset + 3].toInt() and 0xFF) shl 24)
                }
            }
        } catch (e: IndexOutOfBoundsException) {
            // Catch potential IOOBE from buffer.getByte if calculation is wrong or buffer is smaller than expected
            throw IndexOutOfBoundsException("Calculated byteOffset $byteOffset or subsequent bytes out of bounds for underlying buffer. index=$index, limit=$limit, capacity=$capacity, baseOffset=$baseOffset, buffer.size=${buffer.size}")
        } catch (e: EOFException) {
            // Catch potential EOF if trying to read past the end of the kotlinx.io.Buffer
            throw BufferUnderflowException("Read attempt failed due to EOF at byteOffset $byteOffset. index=$index, limit=$limit, capacity=$capacity, baseOffset=$baseOffset, buffer.size=${buffer.size}")
        }
        return Float.fromBits(bits)
    }


    /**
     * Sets this buffer's position. If the mark is defined and larger than the new position then it is discarded.
     */
    fun position(newPosition: Int): FloatBuffer {
        this.position = newPosition
        return this
    }

    /**
     * Relative put method.
     * Writes the given float at the current position and then increments the position.
     *
     * @param value the float value to write.
     * @return this FloatBuffer.
     * @throws BufferOverflowException if there is no space remaining (position equals limit).
     */
    fun put(value: Float): FloatBuffer {
        if (position >= limit) throw BufferOverflowException("position=$position, limit=$limit")
        put(position, value) // Use absolute put
        position++
        return this
    }

    /**
     * Absolute put method.
     * Writes the given float into the buffer at the specified index (in floats).
     * Incorporates the `baseOffset` for sliced buffers.
     *
     * @param index the index at which to write.
     * @param value the float value to write.
     * @return this FloatBuffer.
     * @throws IndexOutOfBoundsException if index is out of bounds [0, limit).
     * @throws kotlinx.io.IOException if the underlying buffer cannot be written to.
     */
    fun put(index: Int, value: Float): FloatBuffer {
        if (index < 0 || index >= limit) {
            throw IndexOutOfBoundsException("index=$index, limit=$limit")
        }
        // Calculate the actual byte offset in the underlying buffer
        val byteOffset = baseOffset + index.toLong() * BYTES_PER_FLOAT
        val bits = value.toBits() // Get IEEE 754 representation

        try {
            when (order) {
                ByteOrder.BIG_ENDIAN -> {
                    buffer.setByteAt(byteOffset, (bits shr 24).toByte())
                    buffer.setByteAt(byteOffset + 1, (bits shr 16).toByte())
                    buffer.setByteAt(byteOffset + 2, (bits shr 8).toByte())
                    buffer.setByteAt(byteOffset + 3, (bits).toByte())
                }

                ByteOrder.LITTLE_ENDIAN -> {
                    buffer.setByteAt(byteOffset, (bits).toByte())
                    buffer.setByteAt(byteOffset + 1, (bits shr 8).toByte())
                    buffer.setByteAt(byteOffset + 2, (bits shr 16).toByte())
                    buffer.setByteAt(byteOffset + 3, (bits shr 24).toByte())
                }
            }
        } catch (e: IndexOutOfBoundsException) {
            // Catch potential IOOBE from buffer.setByteAt
            throw IndexOutOfBoundsException("Calculated byteOffset $byteOffset or subsequent bytes out of bounds for underlying buffer. index=$index, limit=$limit, capacity=$capacity, baseOffset=$baseOffset, buffer.size=${buffer.size}")
        }
        // kotlinx.io setByteAt might throw IOException, let it propagate
        return this
    }

    /**
     * Relative bulk get method.
     *
     * Transfers [length] floats from this buffer into the destination [dst] array,
     * starting at the current position and at the specified [offset] in [dst].
     * After copying, the buffer's position is advanced by [length].
     *
     * @param dst The destination array.
     * @param offset The starting offset in the destination array.
     * @param length The number of floats to transfer.
     * @return This buffer.
     * @throws BufferUnderflowException if there are fewer than [length] floats remaining.
     * @throws IndexOutOfBoundsException if [offset] or [length] are invalid for [dst].
     */
    fun get(dst: FloatArray, offset: Int, length: Int): FloatBuffer {
        // Check bounds for the destination array
        if (offset < 0 || length < 0 || offset + length > dst.size) {
            throw IndexOutOfBoundsException("offset=$offset, length=$length, dst.size=${dst.size}")
        }
        // Check if there are enough elements remaining in the buffer
        if (length > remaining()) {
            throw BufferUnderflowException("length=$length, remaining=${remaining()}")
        }

        // Copy elements using absolute get
        val startPos = this.position
        for (i in 0 until length) {
            dst[offset + i] = this.get(startPos + i) // Use absolute get which handles baseOffset
        }

        // Advance the buffer's position
        this.position = startPos + length
        return this
    }

    /**
     * Relative bulk get method.
     *
     * Transfers floats from this buffer into the destination [dst] array.
     * This method behaves the same as `get(dst, 0, dst.size)`.
     *
     * @param dst The destination array, filled from the current position.
     * @return This buffer.
     * @throws BufferUnderflowException if there are fewer than `dst.size` floats remaining.
     */
    fun get(dst: FloatArray): FloatBuffer = get(dst, 0, dst.size)

    /**
     * Relative bulk put method.
     *
     * Transfers [length] floats from the source [src] array into this buffer,
     * starting at the specified [offset] in [src] and at the current position in this buffer.
     * After copying, the buffer's position is advanced by [length].
     *
     * @param src The source array.
     * @param offset The starting offset in the source array.
     * @param length The number of floats to transfer.
     * @return This buffer.
     * @throws BufferOverflowException if there is insufficient space in this buffer.
     * @throws IndexOutOfBoundsException if [offset] or [length] are invalid for [src].
     * @throws kotlinx.io.IOException if the underlying buffer cannot be written to.
     */
    fun put(src: FloatArray, offset: Int, length: Int): FloatBuffer {
        // Check bounds for the source array
        if (offset < 0 || length < 0 || offset + length > src.size) {
            throw IndexOutOfBoundsException("offset=$offset, length=$length, src.size=${src.size}")
        }
        // Check if there is enough space remaining in the buffer
        if (length > remaining()) {
            throw BufferOverflowException("length=$length, remaining=${remaining()}")
        }

        // Copy elements using absolute put
        val startPos = this.position
        for (i in 0 until length) {
            this.put(startPos + i, src[offset + i]) // Use absolute put which handles baseOffset
        }

        // Advance the buffer's position
        this.position = startPos + length
        return this
    }

    /**
     * Relative bulk put method.
     *
     * Transfers the entire content of the source [src] array into this buffer,
     * starting at the current position. This method behaves the same as `put(src, 0, src.size)`.
     *
     * @param src The source array.
     * @return This buffer.
     * @throws BufferOverflowException if there is insufficient space in this buffer.
     * @throws kotlinx.io.IOException if the underlying buffer cannot be written to.
     */
    fun put(src: FloatArray): FloatBuffer = put(src, 0, src.size)


    /**
     * Creates a new FloatBuffer slice that is a view of this buffer's content from
     * the current position to the limit.
     *
     * The new buffer’s capacity and limit will be set to the number of floats remaining (`remaining()`),
     * its position will be 0, and its `baseOffset` will be adjusted so that index 0 of the
     * slice corresponds to index `position` of the original buffer relative to its `baseOffset`.
     *
     * Changes to the underlying data in one buffer are visible in the other because they
     * share the same underlying `kotlinx.io.Buffer` instance.
     */
    fun slice(): FloatBuffer {
        val rem = remaining()
        if (rem < 0) throw IllegalStateException("position > limit") // Should not happen with proper checks

        // Calculate the new base offset for the slice relative to the start of the underlying buffer.
        val newBaseOffset = this.baseOffset + (this.position.toLong() * BYTES_PER_FLOAT)

        // Create a new FloatBuffer view sharing the same underlying kotlinx.io.Buffer.
        // The capacity of the slice is the number of remaining elements.
        val slicedBuffer = FloatBuffer(this.buffer, rem, newBaseOffset)
        // The slice starts at position 0, its limit is its capacity (the remaining elements).
        slicedBuffer.position = 0
        slicedBuffer.limit = rem
        // The slice inherits the byte order of the parent.
        slicedBuffer.order = this.order
        // slicedBuffer.mark = -1 // If implementing mark/reset

        return slicedBuffer
    }

    /**
     * Compares this buffer to another.
     *
     * Compares the remaining elements lexicographically.
     *
     * @param other The buffer to compare with.
     * @return A negative integer, zero, or a positive integer as this buffer
     * is less than, equal to, or greater than the specified buffer.
     */
    override fun compareTo(other: FloatBuffer): Int {
        val n = min(this.remaining(), other.remaining())
        val thisPos = this.position
        val otherPos = other.position
        for (i in 0 until n) {
            // Use absolute get which handles potential baseOffsets correctly
            val cmp = this.get(thisPos + i).compareTo(other.get(otherPos + i))
            if (cmp != 0) {
                return cmp
            }
        }
        // If all common elements are equal, the shorter buffer is "less"
        return this.remaining() - other.remaining()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FloatBuffer) return false

        if (this.remaining() != other.remaining()) return false

        val thisPos = this.position
        val otherPos = other.position
        for (i in 0 until remaining()) {
            // Use absolute get; compare bits for exact float equality check
            if (this.get(thisPos + i).toBits() != other.get(otherPos + i).toBits()) {
                return false
            }
        }
        return true
    }

    override fun hashCode(): Int {
        var result = 1
        val pos = this.position
        for (i in 0 until remaining()) {
            // Use absolute get
            result = 31 * result + get(pos + i).toBits()
        }
        return result
    }


    override fun toString(): String {
        return "${this::class.simpleName}(pos=$position lim=$limit cap=$capacity order=$order baseOffset=$baseOffset)"
    }
}

// Assume ByteOrder is defined elsewhere, e.g.:
// enum class ByteOrder { BIG_ENDIAN, LITTLE_ENDIAN; companion object { fun nativeOrder(): ByteOrder = TODO("Implement native order detection") } }
// You'll need to implement nativeOrder detection for your target platforms.