package org.gnit.lucenekmp.jdkport

import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlin.math.min

// (If you do not have these exceptions in common code, you may define your own.)
class BufferUnderflowException(message: String = "") : RuntimeException(message)
class BufferOverflowException(message: String = "") : RuntimeException(message)

// A simple re-implementation of java.nio.ByteBuffer using a kotlin-io Buffer as the backing store.
open class ByteBuffer private constructor(
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
     * Sets this buffer's position. If the mark is defined and larger than the new position then it is discarded.
     */
    fun position(newPosition: Int): ByteBuffer {
        position = newPosition
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

    /**
     * Absolute bulk *get* method.
     *
     *
     *  This method transfers `length` bytes from this
     * buffer into the given array, starting at the given index in this
     * buffer and at the given offset in the array.  The position of this
     * buffer is unchanged.
     *
     *
     *  An invocation of this method of the form
     * `src.get(index,&nbsp;dst,&nbsp;offset,&nbsp;length)`
     * has exactly the same effect as the following loop except that it first
     * checks the consistency of the supplied parameters and it is potentially
     * much more efficient:
     *
     * {@snippet lang=java :
     * *     for (int i = offset, j = index; i < offset + length; i++, j++)
     * *         dst[i] = src.get(j);
     * * }
     *
     * @param  index
     * The index in this buffer from which the first byte will be
     * read; must be non-negative and less than `limit()`
     *
     * @param  dst
     * The destination array
     *
     * @param  offset
     * The offset within the array of the first byte to be
     * written; must be non-negative and less than
     * `dst.length`
     *
     * @param  length
     * The number of bytes to be written to the given array;
     * must be non-negative and no larger than the smaller of
     * `limit() - index` and `dst.length - offset`
     *
     * @return  This buffer
     *
     * @throws  IndexOutOfBoundsException
     * If the preconditions on the `index`, `offset`, and
     * `length` parameters do not hold
     *
     * @since 13
     */
    open fun get(index: Int, dst: ByteArray, offset: Int, length: Int): ByteBuffer {
        Objects.checkFromIndexSize(index, length, limit)
        Objects.checkFromIndexSize(offset, length, dst.size)

        getArray(index, dst, offset, length)

        return this
    }

    // -- Other stuff --
    /**
     * Tells whether or not this buffer is backed by an accessible byte
     * array.
     *
     *
     *  If this method returns `true` then the [array][.array]
     * and [arrayOffset][.arrayOffset] methods may safely be invoked.
     *
     *
     * @return  `true` if, and only if, this buffer
     * is backed by an array and is not read-only
     */
    /*
    NOT_TODO will not implement this for now
    fun hasArray(): Boolean {
        return (hb != null) && !isReadOnly
    }*/

    private fun getArray(index: Int, dst: ByteArray, offset: Int, length: Int): ByteBuffer {
        // Simple implementation: copy bytes one by one using the get() method
        // We're not implementing the JNI direct memory copy optimization from the Java version
        // since we don't have equivalent functionality in kotlinx.io.Buffer

        val end = offset + length
        for (i in offset until end) {
            dst[i] = get(index + (i - offset))
        }
        return this
    }

    /**
     * Absolute *get* method for reading an int value.
     *
     *
     *  Reads four bytes at the given index, composing them into a
     * int value according to the current byte order.
     *
     * @param  index
     * The index from which the bytes will be read
     *
     * @return  The int value at the given index
     *
     * @throws  IndexOutOfBoundsException
     * If `index` is negative
     * or not smaller than the buffer's limit,
     * minus three
     */
    fun getInt(index: Int): Int {
        if (index < 0 || (limit - index) < 4) {
            throw IndexOutOfBoundsException("Index $index out of bounds: need 4 bytes from index (limit: $limit)")
        }
        val offset = index.toLong()
        var result = 0
        if (order == ByteOrder.BIG_ENDIAN) {
            for (i in 0 until 4) {
                result = (result shl 8) or (buffer.get(offset + i).toInt() and 0xFF)
            }
        } else { // LITTLE_ENDIAN
            for (i in 3 downTo 0) {
                result = (result shl 8) or (buffer.get(offset + i).toInt() and 0xFF)
            }
        }
        return result
    }

    /**
     * Absolute *get* method for reading a short value.
     *
     *
     *  Reads two bytes at the given index, composing them into a
     * short value according to the current byte order.
     *
     * @param  index
     * The index from which the bytes will be read
     *
     * @return  The short value at the given index
     *
     * @throws  IndexOutOfBoundsException
     * If `index` is negative
     * or not smaller than the buffer's limit,
     * minus one
     */
    fun getShort(index: Int): Short {
        if (index < 0 || (limit - index) < 2) {
            throw IndexOutOfBoundsException("Index $index out of bounds: need 2 bytes from index (limit: $limit)")
        }
        val offset = index.toLong()
        var result = 0
        if (order == ByteOrder.BIG_ENDIAN) {
            for (i in 0 until 2) {
                result = (result shl 8) or (buffer.get(offset + i).toInt() and 0xFF)
            }
        } else { // LITTLE_ENDIAN
            for (i in 1 downTo 0) {
                result = (result shl 8) or (buffer.get(offset + i).toInt() and 0xFF)
            }
        }
        return result.toShort()
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

    // -- Bulk put operations --
    /**
     * Relative bulk *put* method&nbsp;&nbsp;*(optional operation)*.
     *
     *
     *  This method transfers the bytes remaining in the given source
     * buffer into this buffer.  If there are more bytes remaining in the
     * source buffer than in this buffer, that is, if
     * `src.remaining()`&nbsp;`>`&nbsp;`remaining()`,
     * then no bytes are transferred and a [ ] is thrown.
     *
     *
     *  Otherwise, this method copies
     * *n*&nbsp;=&nbsp;`src.remaining()` bytes from the given
     * buffer into this buffer, starting at each buffer's current position.
     * The positions of both buffers are then incremented by *n*.
     *
     *
     *  In other words, an invocation of this method of the form
     * `dst.put(src)` has exactly the same effect as the loop
     *
     * {@snippet lang=java :
     * *     while (src.hasRemaining())
     * *         dst.put(src.get());
     * * }
     *
     * except that it first checks that there is sufficient space in this
     * buffer and it is potentially much more efficient.  If this buffer and
     * the source buffer share the same backing array or memory, then the
     * result will be as if the source elements were first copied to an
     * intermediate location before being written into this buffer.
     *
     * @param  src
     * The source buffer from which bytes are to be read;
     * must not be this buffer
     *
     * @return  This buffer
     *
     * @throws  BufferOverflowException
     * If there is insufficient space in this buffer
     * for the remaining bytes in the source buffer
     *
     * @throws  IllegalArgumentException
     * If the source buffer is this buffer
     *
     * @throws  ReadOnlyBufferException
     * If this buffer is read-only
     */
    fun put(src: ByteBuffer): ByteBuffer {

        val srcPos: Int = src.position
        val srcLim: Int = src.limit
        val srcRem = (if (srcPos <= srcLim) srcLim - srcPos else 0)
        val pos: Int = position
        val lim: Int = limit
        val rem = (if (pos <= lim) lim - pos else 0)

        if (srcRem > rem) throw BufferOverflowException()

        putBuffer(pos, src, srcPos, srcRem)

        position(pos + srcRem)
        src.position(srcPos + srcRem)

        return this
    }

    /**
     * Relative *put* method for writing a short
     * value&nbsp;&nbsp;*(optional operation)*.
     *
     *
     *  Writes two bytes containing the given short value, in the
     * current byte order, into this buffer at the current position, and then
     * increments the position by two.
     *
     * @param  value
     * The short value to be written
     *
     * @return  This buffer
     *
     * @throws  BufferOverflowException
     * If there are fewer than two bytes
     * remaining in this buffer
     *
     * @throws  ReadOnlyBufferException
     * If this buffer is read-only
     */
    fun putShort(value: Short): ByteBuffer {
        // Ensure there are at least 2 bytes left between position and limit
        if (remaining() < 2) {
            throw BufferOverflowException("Not enough space to write 2 bytes at position $position with limit $limit")
        }

        // Write according to the current byte order
        if (order == ByteOrder.BIG_ENDIAN) {
            buffer.setByteAt(position.toLong(), (value.toInt() shr 8).toByte())
            buffer.setByteAt(position.toLong() + 1, value.toByte())
        } else {
            // LITTLE_ENDIAN
            buffer.setByteAt(position.toLong(), value.toByte())
            buffer.setByteAt(position.toLong() + 1, (value.toInt() shr 8).toByte())
        }

        // Advance the position by 2 bytes
        position += 2
        return this
    }

    /**
     * Relative *put* method for writing an int
     * value&nbsp;&nbsp;*(optional operation)*.
     *
     *
     *  Writes four bytes containing the given int value, in the
     * current byte order, into this buffer at the current position, and then
     * increments the position by four.
     *
     * @param  value
     * The int value to be written
     *
     * @return  This buffer
     *
     * @throws  BufferOverflowException
     * If there are fewer than four bytes
     * remaining in this buffer
     *
     * @throws  ReadOnlyBufferException
     * If this buffer is read-only
     */
    fun putInt(value: Int): ByteBuffer {
        // Ensure there are at least 4 bytes left between position and limit
        if (remaining() < 4) {
            throw BufferOverflowException("Not enough space to write 4 bytes at position $position with limit $limit")
        }

        // Write according to the current byte order
        if (order == ByteOrder.BIG_ENDIAN) {
            buffer.setByteAt(position.toLong(), (value shr 24).toByte())
            buffer.setByteAt(position.toLong() + 1, (value shr 16).toByte())
            buffer.setByteAt(position.toLong() + 2, (value shr 8).toByte())
            buffer.setByteAt(position.toLong() + 3, value.toByte())
        } else {
            // LITTLE_ENDIAN
            buffer.setByteAt(position.toLong(), value.toByte())
            buffer.setByteAt(position.toLong() + 1, (value shr 8).toByte())
            buffer.setByteAt(position.toLong() + 2, (value shr 16).toByte())
            buffer.setByteAt(position.toLong() + 3, (value shr 24).toByte())
        }

        // Advance the position by 4 bytes
        position += 4
        return this
    }

    fun putBuffer(pos: Int, src: ByteBuffer, srcPos: Int, n: Int) {
        // Simple buffer-to-buffer copy implementation without direct memory access
        // Copy bytes one by one from src to this buffer
        for (i in 0 until n) {
            val b = src.get(srcPos + i)
            put(pos + i, b)
        }
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
            buffer.setByteAt(position.toLong(), (value shr 56).toByte())
            buffer.setByteAt(position.toLong() + 1, (value shr 48).toByte())
            buffer.setByteAt(position.toLong() + 2, (value shr 40).toByte())
            buffer.setByteAt(position.toLong() + 3, (value shr 32).toByte())
            buffer.setByteAt(position.toLong() + 4, (value shr 24).toByte())
            buffer.setByteAt(position.toLong() + 5, (value shr 16).toByte())
            buffer.setByteAt(position.toLong() + 6, (value shr 8).toByte())
            buffer.setByteAt(position.toLong() + 7, (value).toByte())
        } else {
            // LITTLE_ENDIAN
            buffer.setByteAt(position.toLong(), (value).toByte())
            buffer.setByteAt(position.toLong() + 1, (value shr 8).toByte())
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

    /**
     * Tells whether there are any elements between the current position and
     * the limit.
     *
     * @return  `true` if, and only if, there is at least one element
     * remaining in this buffer
     */
    fun hasRemaining(): Boolean {
        return position < limit
    }

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

    /**
     * Creates a new, read-only byte buffer that shares this buffer's
     * content.
     *
     *
     *  The content of the new buffer will be that of this buffer.  Changes
     * to this buffer's content will be visible in the new buffer; the new
     * buffer itself, however, will be read-only and will not allow the shared
     * content to be modified.  The two buffers' position, limit, and mark
     * values will be independent.
     *
     *
     *  The new buffer's capacity, limit, position,
     *
     * and mark values will be identical to those of this buffer, and its byte
     * order will be [BIG_ENDIAN][ByteOrder.BIG_ENDIAN].
     *
     *
     *
     *
     *
     *  If this buffer is itself read-only then this method behaves in
     * exactly the same way as the [duplicate][.duplicate] method.
     *
     * @return  The new, read-only byte buffer
     */
    fun asReadOnlyBuffer(): ByteBuffer {
        // For simplicity, we'll just duplicate the buffer
        // In a real implementation, this would return a read-only wrapper
        // but for now we're just creating a duplicate with the same properties
        val dup = duplicate()
        // In a full implementation, you would set a read-only flag and enforce it
        // during mutation operations like put(), but that's not implemented yet
        dup.order = this.order // Ensure we copy the byte order
        return dup
    }

    /**
     * Creates a view of this byte buffer as a long buffer.
     *
     * The content of the new buffer will start at this buffer's current position.
     * Changes to this buffer's content will be visible in the new buffer, and vice versa.
     * The new buffer's position will be zero, its capacity and limit will be the number of longs
     * remaining in this buffer divided by eight, and its byte order will be that of this buffer.
     *
     * @return A new long buffer that shares this buffer's content
     */
    fun asLongBuffer(): LongBuffer {
        // Calculate capacity in longs (bytes/8)
        val longCapacity = remaining() / 8
        if (longCapacity <= 0) {
            return LongBuffer.allocate(0).apply { order = this@ByteBuffer.order }
        }

        // Create a slice of this buffer from current position
        val slice = duplicate()
        slice.position(position)
        slice.limit(position + (longCapacity * 8))

        // Create a LongBuffer with the same byte order
        val longBuffer = LongBuffer.allocate(longCapacity)
        longBuffer.clear()
        longBuffer.order = this.order

        // Copy data from this buffer to the longBuffer
        for (i in 0 until longCapacity) {
            val value = slice.getLong()
            longBuffer.put(value)
        }
        longBuffer.flip()

        return longBuffer
    }

    /**
     * Creates a view of this byte buffer as a float buffer.
     *
     * The content of the new buffer will start at this buffer's current position.
     * Changes to this buffer's content will be visible in the new buffer, and vice versa.
     * The new buffer's position will be zero, its capacity and limit will be the number of floats
     * remaining in this buffer divided by four, and its byte order will be that of this buffer.
     *
     * @return A new float buffer that shares this buffer's content
     */
    fun asFloatBuffer(): FloatBuffer {
        // Calculate capacity in floats (bytes/4)
        val floatCapacity = remaining() / 4
        if (floatCapacity <= 0) {
            return FloatBuffer.allocate(0).apply { order = this@ByteBuffer.order }
        }

        // Create a slice of this buffer from current position
        val slice = duplicate()
        slice.position(position)
        slice.limit(position + (floatCapacity * 4))

        // Create a FloatBuffer with the same byte order
        val floatBuffer = FloatBuffer.allocate(floatCapacity)
        floatBuffer.clear()
        floatBuffer.order = this.order

        // Copy data from this buffer to the floatBuffer
        val floatArray = FloatArray(floatCapacity)
        for (i in 0 until floatCapacity) {
            // Read from the ByteBuffer as bytes and convert to float
            val bits = when (order) {
                ByteOrder.BIG_ENDIAN -> {
                    ((slice.get().toInt() and 0xFF) shl 24) or
                            ((slice.get().toInt() and 0xFF) shl 16) or
                            ((slice.get().toInt() and 0xFF) shl 8) or
                            (slice.get().toInt() and 0xFF)
                }

                else -> { // LITTLE_ENDIAN
                    (slice.get().toInt() and 0xFF) or
                            ((slice.get().toInt() and 0xFF) shl 8) or
                            ((slice.get().toInt() and 0xFF) shl 16) or
                            ((slice.get().toInt() and 0xFF) shl 24)
                }
            }
            floatArray[i] = Float.fromBits(bits)
        }
        floatBuffer.put(floatArray)
        floatBuffer.flip()

        return floatBuffer
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