package org.gnit.lucenekmp.jdkport

/**
 * port of [java.nio.CharBuffer](https://docs.oracle.com/en/java/javase/24/docs/api/java.base/java/nio/CharBuffer.html)
 *
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
) : CharSequence, Appendable, Comparable<CharBuffer> {

    var position: Int = 0
    var limit: Int = capacity
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

    // -- Bulk put operations --
    /**
     * Relative bulk *put* method&nbsp;&nbsp;*(optional operation)*.
     *
     *
     *  This method transfers the chars remaining in the given source
     * buffer into this buffer.  If there are more chars remaining in the
     * source buffer than in this buffer, that is, if
     * `src.remaining()`&nbsp;`>`&nbsp;`remaining()`,
     * then no chars are transferred and a [ ] is thrown.
     *
     *
     *  Otherwise, this method copies
     * *n*&nbsp;=&nbsp;`src.remaining()` chars from the given
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
     * The source buffer from which chars are to be read;
     * must not be this buffer
     *
     * @return  This buffer
     *
     * @throws  BufferOverflowException
     * If there is insufficient space in this buffer
     * for the remaining chars in the source buffer
     *
     * @throws  IllegalArgumentException
     * If the source buffer is this buffer
     *
     * @throws  ReadOnlyBufferException
     * If this buffer is read-only
     */
    fun put(src: CharBuffer): CharBuffer {
        if (src === this) throw Exception("createSameBuffer")
        if (isReadOnly()) throw ReadOnlyBufferException()

        val srcPos: Int = src.position()
        val srcLim: Int = src.limit
        val srcRem = (if (srcPos <= srcLim) srcLim - srcPos else 0)
        val pos = position()
        val lim: Int = limit
        val rem = (if (pos <= lim) lim - pos else 0)

        if (srcRem > rem) throw BufferOverflowException()

        putBuffer(pos, src, srcPos, srcRem)

        position = pos + srcRem

        src.position = srcPos + srcRem

        return this
    }

    fun putBuffer(pos: Int, src: CharBuffer, srcPos: Int, n: Int) {
        for (i in 0 until n) {
            this.putAbsolute(pos + i, src.getAbsolute(srcPos + i))
        }
    }

    /**
     * Relative bulk *put* method&nbsp;&nbsp;*(optional operation)*.
     *
     *
     *  This method transfers the entire content of the given source string
     * into this buffer.  An invocation of this method of the form
     * `dst.put(s)` behaves in exactly the same way as the invocation
     *
     * {@snippet lang=java :
     * *     dst.put(s, 0, s.length())
     * * }
     *
     * @param   src
     * The source string
     *
     * @return  This buffer
     *
     * @throws  BufferOverflowException
     * If there is insufficient space in this buffer
     *
     * @throws  ReadOnlyBufferException
     * If this buffer is read-only
     */
    fun put(src: String): CharBuffer {
        return put(src, 0, src.length)
    }

    /**
     * Relative bulk *put* method&nbsp;&nbsp;*(optional operation)*.
     *
     *
     *  This method transfers chars from the given string into this
     * buffer.  If there are more chars to be copied from the string than
     * remain in this buffer, that is, if
     * `end&nbsp;-&nbsp;start`&nbsp;`>`&nbsp;`remaining()`,
     * then no chars are transferred and a [ ] is thrown.
     *
     *
     *  Otherwise, this method copies
     * *n*&nbsp;=&nbsp;`end`&nbsp;-&nbsp;`start` chars
     * from the given string into this buffer, starting at the given
     * `start` index and at the current position of this buffer.  The
     * position of this buffer is then incremented by *n*.
     *
     *
     *  In other words, an invocation of this method of the form
     * `dst.put(src,&nbsp;start,&nbsp;end)` has exactly the same effect
     * as the loop
     *
     * {@snippet lang=java :
     * *     for (int i = start; i < end; i++)
     * *         dst.put(src.charAt(i));
     * * }
     *
     * except that it first checks that there is sufficient space in this
     * buffer and it is potentially much more efficient.
     *
     * @param  src
     * The string from which chars are to be read
     *
     * @param  start
     * The offset within the string of the first char to be read;
     * must be non-negative and no larger than
     * `string.length()`
     *
     * @param  end
     * The offset within the string of the last char to be read,
     * plus one; must be non-negative and no larger than
     * `string.length()`
     *
     * @return  This buffer
     *
     * @throws  BufferOverflowException
     * If there is insufficient space in this buffer
     *
     * @throws  IndexOutOfBoundsException
     * If the preconditions on the `start` and `end`
     * parameters do not hold
     *
     * @throws  ReadOnlyBufferException
     * If this buffer is read-only
     */
    fun put(src: String, start: Int, end: Int): CharBuffer {
        Objects.checkFromIndexSize(start, end - start, src.length)
        if (isReadOnly()) throw ReadOnlyBufferException()
        if (end - start > remaining()) throw BufferOverflowException()
        for (i in start..<end) this.put(src[i])
        return this
    }


    // --- Buffer state methods ---

    /** Returns the number of characters between position and limit. */
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

    /**
     * Sets the mark at the current position.
     * After this call, calling reset() will return the position to here.
     */
    fun mark(): CharBuffer {
        mark = position
        return this
    }

    /**
     * Resets the position to the last mark.
     * Throws IllegalStateException if no mark has been set.
     */
    fun reset(): CharBuffer {
        if (mark == -1) throw IllegalStateException("Mark has not been set")
        position = mark
        return this
    }

    /**
     * Lexicographically compares the remaining characters of this buffer
     * to another buffer's remaining characters.
     *
     * Returns negative if this < other, 0 if equal, positive if this > other.
     */
    override fun compareTo(other: CharBuffer): Int {
        val n = minOf(this.remaining(), other.remaining())
        for (i in 0 until n) {
            val cmp = this.getAbsolute(this.position + i).compareTo(other.getAbsolute(other.position + i))
            if (cmp != 0) {
                return cmp
            }
        }
        return this.remaining() - other.remaining()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CharBuffer) return false
        if (this.remaining() != other.remaining()) return false

        val n = this.remaining()
        for (i in 0 until n) {
            if (this.getAbsolute(this.position + i) != other.getAbsolute(other.position + i)) {
                return false
            }
        }
        return true
    }

    override fun hashCode(): Int {
        var result = 1
        val rem = remaining()
        for (i in 0 until rem) {
            result = 31 * result + getAbsolute(position + i).hashCode()
        }
        return result
    }

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
