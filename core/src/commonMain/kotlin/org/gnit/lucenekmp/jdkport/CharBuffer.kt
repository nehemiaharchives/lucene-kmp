package org.gnit.lucenekmp.jdkport

// Custom exceptions similar to the Java NIO ones.
class ReadOnlyBufferException(message: String? = null) : RuntimeException(message)

/**
 * A very simple, mutable (or optionally read-only) character buffer.
 *
 * This class mimics basic behaviors of a CharBuffer:
 * - Relative get() and put() operations that update the current position.
 * - Absolute get/put via helper methods.
 * - Bulk get/put.
 * - Slicing, duplicating, compacting, and creating a read-only view.
 * - Implements CharSequence (the sequence being the “remaining” characters,
 *   i.e. from the current position up to the limit) and Appendable.
 */
class CharBuffer private constructor(
    private val array: CharArray,
    private val arrayOffset: Int,
    val capacity: Int,
    private var readOnly: Boolean = false
) : CharSequence, Appendable {

    var position: Int = 0
        private set
    var limit: Int = capacity
        private set
    var mark: Int = -1
        private set

    // --- Relative operations ---

    /** Relative get: reads the char at current position and increments position. */
    fun get(): Char {
        if (position >= limit) throw BufferUnderflowException("No more characters")
        return array[arrayOffset + position++]
    }

    /** Relative put: writes the char at current position and increments position. */
    fun put(c: Char): CharBuffer {
        if (readOnly) throw ReadOnlyBufferException("Buffer is read-only")
        if (position >= limit) throw BufferOverflowException("No space remaining")
        array[arrayOffset + position++] = c
        return this
    }

    // --- Absolute operations ---

    /** Absolute get: returns the char at the given index (0 ≤ index < limit) without changing position. */
    fun getAbsolute(index: Int): Char {
        if (index < 0 || index >= limit)
            throw IndexOutOfBoundsException("Index $index out of bounds (limit: $limit)")
        return array[arrayOffset + index]
    }

    /** Absolute put: writes the char at the given index (0 ≤ index < limit) without changing position. */
    fun putAbsolute(index: Int, c: Char): CharBuffer {
        if (readOnly) throw ReadOnlyBufferException("Buffer is read-only")
        if (index < 0 || index >= limit)
            throw IndexOutOfBoundsException("Index $index out of bounds (limit: $limit)")
        array[arrayOffset + index] = c
        return this
    }

    // --- Bulk operations ---

    /** Reads [length] characters into [dst] starting at [dstOffset], advancing the buffer’s position. */
    fun get(dst: CharArray, dstOffset: Int, length: Int): CharBuffer {
        if (length > remaining()) throw BufferUnderflowException("Not enough characters remaining")
        if (dstOffset < 0 || dstOffset + length > dst.size)
            throw IndexOutOfBoundsException("Destination out of bounds")
        for (i in 0 until length) {
            dst[dstOffset + i] = get()
        }
        return this
    }

    /** Writes [length] characters from [src] (starting at [srcOffset]) into this buffer, advancing the position. */
    fun put(src: CharArray, srcOffset: Int, length: Int): CharBuffer {
        if (readOnly) throw ReadOnlyBufferException("Buffer is read-only")
        if (length > remaining()) throw BufferOverflowException("Not enough space remaining")
        if (srcOffset < 0 || srcOffset + length > src.size)
            throw IndexOutOfBoundsException("Source out of bounds")
        for (i in 0 until length) {
            put(src[srcOffset + i])
        }
        return this
    }

    // --- Buffer state methods ---

    /** Returns the number of characters between position and limit. */
    fun remaining(): Int = limit - position

    /** Clears the buffer: position ← 0, limit ← capacity, mark undefined. */
    fun clear(): CharBuffer {
        position = 0
        limit = capacity
        mark = -1
        return this
    }

    /** Flips the buffer: limit ← current position, position ← 0, mark undefined. */
    fun flip(): CharBuffer {
        limit = position
        position = 0
        mark = -1
        return this
    }

    /**
     * Slices the buffer, creating a new buffer that shares the underlying array
     * and represents the remaining characters (from current position to limit).
     */
    fun slice(): CharBuffer {
        val rem = remaining()
        return CharBuffer(array, arrayOffset + position, rem, readOnly).apply {
            position = 0
            limit = rem
        }
    }

    /** Duplicates the buffer, sharing the underlying array and preserving the state. */
    fun duplicate(): CharBuffer {
        return CharBuffer(array, arrayOffset, capacity, readOnly).apply {
            position = this@CharBuffer.position
            limit = this@CharBuffer.limit
            mark = this@CharBuffer.mark
        }
    }

    /** Compacts the buffer by moving the remaining characters to the beginning. */
    fun compact(): CharBuffer {
        if (readOnly) throw ReadOnlyBufferException("Buffer is read-only")
        val rem = remaining()
        for (i in 0 until rem) {
            array[arrayOffset + i] = array[arrayOffset + position + i]
        }
        position = rem
        limit = capacity
        mark = -1
        return this
    }

    /** Returns a read-only view of this buffer. */
    fun asReadOnlyBuffer(): CharBuffer = duplicate().apply { readOnly = true }

    // --- CharSequence implementation ---
    // The character sequence represents the buffer’s "remaining" characters.

    override val length: Int
        get() = remaining()

    override fun get(index: Int): Char {
        // index here is relative to the current position.
        if (index < 0 || index >= remaining())
            throw IndexOutOfBoundsException("Index $index out of bounds (remaining: ${remaining()})")
        return array[arrayOffset + position + index]
    }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        if (startIndex < 0 || endIndex > remaining() || startIndex > endIndex)
            throw IndexOutOfBoundsException("Invalid subsequence range")
        val sb = StringBuilder()
        for (i in startIndex until endIndex) {
            sb.append(get(i))
        }
        return sb.toString()
    }

    override fun toString(): String {
        return subSequence(0, remaining()).toString()
    }

    // --- Appendable implementation ---
    override fun append(csq: CharSequence?): Appendable {
        val s = csq?.toString() ?: "null"
        for (c in s) {
            put(c)
        }
        return this
    }

    override fun append(csq: CharSequence?, start: Int, end: Int): Appendable {
        val s = csq?.subSequence(start, end)?.toString() ?: "null".substring(start, end)
        return append(s)
    }

    override fun append(c: Char): Appendable = put(c)

    fun isReadOnly(): Boolean = readOnly

    fun hasArray(): Boolean {
        return array.isNotEmpty() && !readOnly
    }

    fun arrayOffset(): Int {
        return arrayOffset
    }

    fun array(): CharArray {
        if (readOnly) throw ReadOnlyBufferException("Buffer is read-only")
        return array
    }

    fun position() = position

    companion object {
        /** Allocates a new buffer with the given capacity. */
        fun allocate(capacity: Int): CharBuffer {
            if (capacity < 0) throw IllegalArgumentException("Capacity must be non-negative")
            return CharBuffer(CharArray(capacity), 0, capacity)
        }

        /** Wraps an existing char array. */
        fun wrap(array: CharArray, offset: Int = 0, length: Int = array.size - offset): CharBuffer {
            if (offset < 0 || length < 0 || offset + length > array.size)
                throw IndexOutOfBoundsException("Invalid offset or length")
            return CharBuffer(array, offset, length)
        }

        /** Wraps a CharSequence into a read-only buffer. */
        fun wrap(csq: CharSequence, start: Int = 0, end: Int = csq.length): CharBuffer {
            if (start < 0 || end < start || end > csq.length)
                throw IndexOutOfBoundsException("Invalid start or end")
            val s = csq.subSequence(start, end).toString()
            return CharBuffer(s.toCharArray(), 0, s.length, readOnly = true)
        }
    }
}
