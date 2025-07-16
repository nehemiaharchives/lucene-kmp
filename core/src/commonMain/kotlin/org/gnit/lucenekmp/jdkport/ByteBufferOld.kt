package org.gnit.lucenekmp.jdkport

import okio.IOException
import kotlin.math.min

// A simple re-implementation of java.nio.ByteBufferOld using a ByteArray as the backing store.
@Ported(from = "java.nio.ByteBuffer")
open class ByteBufferOld private constructor(
    private val hb: ByteArray,
    /** The fixed capacity of this ByteBufferOld. */
    val capacity: Int
) : Comparable<ByteBufferOld> {

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

    /**
     * Returns this buffer’s current byte order.
     */
    fun order(): ByteOrder =
        if (bigEndian) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN


    // ------------------------------------------------------------
    // READ-ONLY SUPPORT
    // ------------------------------------------------------------
    /** true once this buffer is a read-only view */
    private var _readOnly: Boolean = false

    /** true if mutation is disallowed */
    fun isReadOnly(): Boolean = _readOnly

    /** throws if this is read-only */
    private fun checkWritable() {
        if (_readOnly) throw ReadOnlyBufferException("buffer is read-only")
    }

    /**
     * port of java.nio.ByteBufferOld.array()
     * Returns byte array that backs this buffer.
     */
    fun array(): ByteArray {
        return hb
    }

    /**
     *
     * Tells whether or not this buffer is backed by an accessible byte array.
     * For this implementation, we always return true since we can always
     * create and return a byte array copy of the buffer contents.
     *
     * @return `true` since we can always create a byte array representation
     */
    fun hasArray(): Boolean {
        return true
    }

    /**
     * Returns the offset within this buffer's backing array of the first
     * element of the buffer&nbsp;&nbsp;*(optional operation)*.
     *
     * If this buffer is backed by an array then buffer position *p*
     * corresponds to array index *p*&nbsp;+&nbsp;`arrayOffset()`.
     *
     * Invoke the [hasArray][.hasArray] method before invoking this
     * method in order to ensure that this buffer has an accessible backing
     * array.
     *
     * @return The offset within this buffer's array (always 0 for this implementation)
     *
     * @throws UnsupportedOperationException
     * If this buffer is not backed by an accessible array
     */
    fun arrayOffset(): Int {
        if (!hasArray()) {
            throw UnsupportedOperationException("Buffer does not have a backing array")
        }
        return 0
    }

    /**
     * Sets this buffer's limit.
     *
     * If the current position is greater than the new limit, the position is set to the new limit.
     * If the mark is defined (i.e. mark != -1) and is greater than the new limit, it is discarded.
     *
     * @param newLimit the new limit (in int); must be non-negative and no larger than capacity.
     * @return this ByteBufferOld.
     * @throws IllegalArgumentException if newLimit is negative or greater than capacity.
     */
    fun limit(newLimit: Int): ByteBufferOld {
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
    fun position(newPosition: Int): ByteBufferOld {
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
    fun order(bo: ByteOrder): ByteBufferOld {
        bigEndian = (bo == ByteOrder.BIG_ENDIAN)
        nativeByteOrder =
            (bigEndian == (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN))
        return this
    }

    /** Relative get: reads the byte at the current position then increments it. */
    fun get(): Byte {
        if (position >= limit) throw BufferUnderflowException("Not enough bytes to read at position $position with limit $limit")
        val b = hb[position]
        position++
        return b
    }

    /** Absolute get: returns the byte at the given index (without modifying position). */
    fun get(index: Int): Byte {
        if (index !in 0 until limit)
            throw IndexOutOfBoundsException("Index ($index) out of bounds (0..${limit - 1})")
        return hb[index]
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
    open fun get(index: Int, dst: ByteArray, offset: Int, length: Int): ByteBufferOld {
        Objects.checkFromIndexSize(index, length, limit)
        Objects.checkFromIndexSize(offset, length, dst.size)

        getArray(index, dst, offset, length)

        return this
    }

    private fun getArray(index: Int, dst: ByteArray, offset: Int, length: Int): ByteBufferOld {
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
        if (index < 0 || limit - index < 4) {
            throw IndexOutOfBoundsException(
                "Index $index out of bounds: need 4 bytes from index (limit: $limit)"
            )
        }
        return if (bigEndian) {
            ((hb[index].toInt() and 0xFF) shl 24) or
            ((hb[index + 1].toInt() and 0xFF) shl 16) or
            ((hb[index + 2].toInt() and 0xFF) shl 8) or
            (hb[index + 3].toInt() and 0xFF)
        } else {
            (hb[index].toInt() and 0xFF) or
            ((hb[index + 1].toInt() and 0xFF) shl 8) or
            ((hb[index + 2].toInt() and 0xFF) shl 16) or
            ((hb[index + 3].toInt() and 0xFF) shl 24)
        }
    }

    /**
     * Relative *get* method for reading an int value.
     *
     * Reads the next four bytes at this buffer's current position,
     * composing them into an int value according to the current byte order,
     * and then increments the position by four.
     *
     * @return  The int value at the buffer's current position
     *
     * @throws  BufferUnderflowException
     * If there are fewer than four bytes
     * remaining in this buffer
     */
    fun getInt(): Int {
        if (remaining() < 4)
            throw BufferUnderflowException("Not enough bytes remaining to read an int (need 4, have ${remaining()})")
        val value = getInt(position)
        position += 4
        return value
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
        if (index < 0 || limit - index < 2) {
            throw IndexOutOfBoundsException(
                "Index $index out of bounds: need 2 bytes from index (limit: $limit)"
            )
        }
        return if (bigEndian) {
            val hi = hb[index].toInt() and 0xFF
            val lo = hb[index + 1].toInt() and 0xFF
            ((hi shl 8) or lo).toShort()
        } else {
            val lo = hb[index].toInt() and 0xFF
            val hi = hb[index + 1].toInt() and 0xFF
            ((hi shl 8) or lo).toShort()
        }
    }

    /**
     * Relative *get* method for reading a short value.
     *
     *
     *  Reads the next two bytes at this buffer's current position,
     * composing them into a short value according to the current byte order,
     * and then increments the position by two.
     *
     * @return  The short value at the buffer's current position
     *
     * @throws  BufferUnderflowException
     * If there are fewer than two bytes
     * remaining in this buffer
     */
    fun getShort(): Short {
        if (remaining() < 2)
            throw BufferUnderflowException("Not enough bytes remaining to read a short (need 2, have ${remaining()})")
        val value = if (bigEndian) {
            val hi = hb[position].toInt() and 0xFF
            val lo = hb[position + 1].toInt() and 0xFF
            ((hi shl 8) or lo).toShort()
        } else {
            val lo = hb[position].toInt() and 0xFF
            val hi = hb[position + 1].toInt() and 0xFF
            ((hi shl 8) or lo).toShort()
        }
        position += 2
        return value
    }

    /** Relative put: writes a byte at the current position then increments it. */
    fun put(b: Byte): ByteBufferOld {
        checkWritable()
        if (position >= limit) throw BufferOverflowException("Not enough space to write at position $position with limit $limit")
        hb[position] = b
        position++
        return this
    }

    /** Absolute put: writes a byte at the specified index. */
    fun put(index: Int, b: Byte): ByteBufferOld {
        checkWritable()
        if (index !in 0 until limit)
            throw IndexOutOfBoundsException("Index ($index) out of bounds (0..${limit - 1})")
        hb[index] = b
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
    fun put(src: ByteBufferOld): ByteBufferOld {
        checkWritable()
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
    fun putShort(value: Short): ByteBufferOld {
        checkWritable()
        if (remaining() < 2) {
            throw BufferOverflowException("Not enough space to write 2 bytes at position $position with limit $limit")
        }
        if (order() == ByteOrder.BIG_ENDIAN) {
            hb[position] = (value.toInt() shr 8).toByte()
            hb[position + 1] = value.toByte()
        } else {
            hb[position] = value.toByte()
            hb[position + 1] = (value.toInt() shr 8).toByte()
        }
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
    fun putInt(value: Int): ByteBufferOld {
        checkWritable()
        if (remaining() < 4) {
            throw BufferOverflowException("Not enough space to write 4 bytes at position $position with limit $limit")
        }
        if (order() == ByteOrder.BIG_ENDIAN) {
            hb[position] = (value shr 24).toByte()
            hb[position + 1] = (value shr 16).toByte()
            hb[position + 2] = (value shr 8).toByte()
            hb[position + 3] = value.toByte()
        } else {
            hb[position] = value.toByte()
            hb[position + 1] = (value shr 8).toByte()
            hb[position + 2] = (value shr 16).toByte()
            hb[position + 3] = (value shr 24).toByte()
        }
        position += 4
        return this
    }

    fun putBuffer(pos: Int, src: ByteBufferOld, srcPos: Int, n: Int) {
        checkWritable()
        for (i in 0 until n) {
            val b = src.get(srcPos + i)
            put(pos + i, b)
        }
    }

    /** Bulk get: transfers remaining bytes into the given destination array. */
    fun get(dst: ByteArray, offset: Int = 0, length: Int = dst.size - offset): ByteBufferOld {
        require(offset >= 0 && length >= 0 && offset + length <= dst.size)
        if (length > remaining())
            throw BufferUnderflowException("Not enough bytes remaining to read $length bytes (only ${remaining()} available)")
        for (i in 0 until length) {
            dst[offset + i] = get()
        }
        return this
    }

    /** Bulk put: transfers bytes from the source array into this buffer. */
    fun put(src: ByteArray, offset: Int = 0, length: Int = src.size - offset): ByteBufferOld {
        checkWritable()
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
        if (index < 0 || limit - index < 8) {
            throw IndexOutOfBoundsException("Index $index out of bounds: need 8 bytes from index (limit: $limit)")
        }
        return if (order() == ByteOrder.BIG_ENDIAN) {
            ((hb[index].toLong() and 0xFF) shl 56) or
            ((hb[index + 1].toLong() and 0xFF) shl 48) or
            ((hb[index + 2].toLong() and 0xFF) shl 40) or
            ((hb[index + 3].toLong() and 0xFF) shl 32) or
            ((hb[index + 4].toLong() and 0xFF) shl 24) or
            ((hb[index + 5].toLong() and 0xFF) shl 16) or
            ((hb[index + 6].toLong() and 0xFF) shl 8) or
            (hb[index + 7].toLong() and 0xFF)
        } else {
            (hb[index].toLong() and 0xFF) or
            ((hb[index + 1].toLong() and 0xFF) shl 8) or
            ((hb[index + 2].toLong() and 0xFF) shl 16) or
            ((hb[index + 3].toLong() and 0xFF) shl 24) or
            ((hb[index + 4].toLong() and 0xFF) shl 32) or
            ((hb[index + 5].toLong() and 0xFF) shl 40) or
            ((hb[index + 6].toLong() and 0xFF) shl 48) or
            ((hb[index + 7].toLong() and 0xFF) shl 56)
        }
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
        val value = getLong(position)
        position += 8
        return value
    }

    fun putLong(value: Long): ByteBufferOld {
        checkWritable()
        if (remaining() < 8) {
            throw BufferOverflowException("Not enough space to write 8 bytes at position $position with limit $limit")
        }
        if (order() == ByteOrder.BIG_ENDIAN) {
            hb[position] = (value shr 56).toByte()
            hb[position + 1] = (value shr 48).toByte()
            hb[position + 2] = (value shr 40).toByte()
            hb[position + 3] = (value shr 32).toByte()
            hb[position + 4] = (value shr 24).toByte()
            hb[position + 5] = (value shr 16).toByte()
            hb[position + 6] = (value shr 8).toByte()
            hb[position + 7] = value.toByte()
        } else {
            hb[position] = value.toByte()
            hb[position + 1] = (value shr 8).toByte()
            hb[position + 2] = (value shr 16).toByte()
            hb[position + 3] = (value shr 24).toByte()
            hb[position + 4] = (value shr 32).toByte()
            hb[position + 5] = (value shr 40).toByte()
            hb[position + 6] = (value shr 48).toByte()
            hb[position + 7] = (value shr 56).toByte()
        }
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
    fun mark(): ByteBufferOld {
        mark = position
        return this
    }

    /** Resets the position to the previously set mark. */
    fun reset(): ByteBufferOld {
        if (mark == -1) throw IOException("Mark has not been set")
        position = mark
        return this
    }

    /** Clears the buffer: sets position to 0, limit to capacity, and mark to -1. */
    fun clear(): ByteBufferOld {
        position = 0
        limit = capacity
        mark = -1
        return this
    }

    /** Flips the buffer: sets limit to current position and resets position to 0. */
    fun flip(): ByteBufferOld {
        limit = position
        position = 0
        mark = -1
        return this
    }

    /** Rewinds the buffer: resets position to 0 without changing limit. */
    fun rewind(): ByteBufferOld {
        position = 0
        mark = -1
        return this
    }

    /** Creates a new buffer that shares this buffer’s content but has independent position, limit, and mark. */
    fun duplicate(): ByteBufferOld {
        val dup = ByteBufferOld(hb, capacity)
        dup.position = this.position
        dup.limit = this.limit
        dup.bigEndian = this.bigEndian
        dup.nativeByteOrder = this.nativeByteOrder
        dup._readOnly = this._readOnly
        return dup
    }

    /** Creates a new buffer that is a view of this buffer's content between position and limit. */
    fun slice(): ByteBufferOld {
        val remaining = remaining()
        val sliceArray = hb.copyOfRange(position, limit)
        val bb = ByteBufferOld(sliceArray, capacity = remaining)
        bb.clear()
        bb.limit = remaining
        return bb
    }

    /** Compares the remaining bytes lexicographically. */
    override fun compareTo(other: ByteBufferOld): Int {
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
    fun asReadOnlyBuffer(): ByteBufferOld {
        val dup = duplicate()
        dup._readOnly = true
        dup.order(this.order())
        return dup
    }

    fun asIntBuffer(): IntBuffer {
        val intCapacity = remaining() / 4
        if (intCapacity <= 0) {
            return IntBuffer.allocate(0).apply { order = this@ByteBufferOld.order() }
        }
        val slice = duplicate()
        slice.position(position)
        slice.limit(position + (intCapacity * 4))
        val intBuffer = IntBuffer.allocate(intCapacity)
        intBuffer.clear()
        intBuffer.order = this.order()
        for (i in 0 until intCapacity) {
            val value = slice.getInt()
            intBuffer.put(value)
        }
        intBuffer.flip()
        return intBuffer
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
        val longCapacity = remaining() / 8
        if (longCapacity <= 0) {
            return LongBuffer.allocate(0).apply { order = this@ByteBufferOld.order() }
        }
        val slice = duplicate()
        slice.position(position)
        slice.limit(position + (longCapacity * 8))
        val longBuffer = LongBuffer.allocate(longCapacity)
        longBuffer.clear()
        longBuffer.order = this.order()
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
        val floatCapacity = remaining() / 4
        if (floatCapacity <= 0) {
            return FloatBuffer.allocate(0).apply { order = this@ByteBufferOld.order() }
        }
        val slice = duplicate()
        slice.position(position)
        slice.limit(position + (floatCapacity * 4))
        val floatBuffer = FloatBuffer.allocate(floatCapacity)
        floatBuffer.clear()
        floatBuffer.order = this.order()
        val floatArray = FloatArray(floatCapacity)
        for (i in 0 until floatCapacity) {
            val bits = when (order()) {
                ByteOrder.BIG_ENDIAN -> {
                    ((slice.get().toInt() and 0xFF) shl 24) or
                            ((slice.get().toInt() and 0xFF) shl 16) or
                            ((slice.get().toInt() and 0xFF) shl 8) or
                            (slice.get().toInt() and 0xFF)
                }
                else -> {
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

    /**
     * Compacts this buffer&nbsp;&nbsp;*(optional operation)*.
     *
     *
     *  The bytes between the buffer's current position and its limit,
     * if any, are copied to the beginning of the buffer.  That is, the
     * byte at index *p*&nbsp;=&nbsp;`position()` is copied
     * to index zero, the byte at index *p*&nbsp;+&nbsp;1 is copied
     * to index one, and so forth until the byte at index
     * `limit()`&nbsp;-&nbsp;1 is copied to index
     * *n*&nbsp;=&nbsp;`limit()`&nbsp;-&nbsp;`1`&nbsp;-&nbsp;*p*.
     * The buffer's position is then set to *n+1* and its limit is set to
     * its capacity.  The mark, if defined, is discarded.
     *
     *
     *  The buffer's position is set to the number of bytes copied,
     * rather than to zero, so that an invocation of this method can be
     * followed immediately by an invocation of another relative *put*
     * method.
     *
     *
     *
     *
     *  Invoke this method after writing data from a buffer in case the
     * write was incomplete.  The following loop, for example, copies bytes
     * from one channel to another via the buffer `buf`:
     *
     * {@snippet lang=java :
     * *     buf.clear();          // Prepare buffer for use
     * *     while (in.read(buf) >= 0 || buf.position != 0) {
     * *         buf.flip();
     * *         out.write(buf);
     * *         buf.compact();    // In case of partial write
     * *     }
     * * }
     *
     *
     *
     * @return  This buffer
     *
     * @throws  ReadOnlyBufferException
     * If this buffer is read-only
     */
    fun compact(): ByteBufferOld {
        checkWritable()
        val rem = remaining()
        if (rem > 0) {
            for (i in 0 until rem) {
                hb[i] = hb[position + i]
            }
        }
        position = rem
        limit = capacity
        mark = -1
        return this
    }

    override fun toString(): String {
        return "ByteBuffer[pos=${position} lim=${limit} cap=${capacity}]"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ByteBufferOld) return false
        val thisRemaining = remaining()
        if (thisRemaining != other.remaining()) return false
        for (i in 0 until thisRemaining) {
            if (get(position + i) != other.get(other.position + i)) return false
        }
        return true
    }

    override fun hashCode(): Int {
        var result = 1
        val end = position + remaining()
        for (i in position until end) {
            result = 31 * result + get(i)
        }
        return result
    }

    companion object {
        fun allocate(capacity: Int): ByteBufferOld {
            require(capacity >= 0) { "Capacity must be non-negative" }
            val backing = ByteArray(capacity)
            return ByteBufferOld(backing, capacity).clear()
        }

        fun wrap(array: ByteArray, offset: Int = 0, length: Int = array.size - offset): ByteBufferOld {
            require(offset in 0..array.size)
            require(length in 0..(array.size - offset))

            val bb = ByteBufferOld(array, array.size)
            bb.position = offset
            bb.limit = offset + length
            return bb
        }
    }
}
