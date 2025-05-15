package org.gnit.lucenekmp.jdkport

/**
 * A simplified, mutable sequence of characters similar in spirit to Java’s StringBuffer.
 *
 * Note: This implementation is not truly thread‑safe.
 */
class StringBuffer : Appendable, CharSequence, Comparable<StringBuffer> {

    // Internal storage – we use a Kotlin StringBuilder.
    private val builder = StringBuilder()

    /**
     * Constructs a StringBuffer with an initial capacity of 16 characters.
     * (The capacity is not actually enforced in this simple version.)
     */
    constructor() : this(16)

    /**
     * Constructs a StringBuffer with the specified initial capacity.
     * The capacity parameter is advisory only.
     */
    constructor(capacity: Int) {
        // In a full implementation, we might preallocate storage.
        // Here we simply ignore capacity.
    }

    /**
     * Constructs a StringBuffer initialized to the contents of the given string.
     */
    constructor(str: String) : this(str.length + 16) {
        builder.append(str)
    }

    /**
     * Constructs a StringBuffer that contains the same characters as the specified [CharSequence].
     */
    constructor(seq: CharSequence) : this(seq.toString())

    // --- CharSequence implementation ---

    override val length: Int get() = builder.length

    override fun get(index: Int): Char {
        if (index < 0 || index >= length)
            throw IndexOutOfBoundsException("Index $index out of bounds for length $length")
        return builder[index]
    }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        if (startIndex < 0 || endIndex > length || startIndex > endIndex)
            throw IndexOutOfBoundsException("Invalid subsequence range [$startIndex, $endIndex) for length $length")
        return builder.subSequence(startIndex, endIndex)
    }

    // --- Appendable implementation ---

    override fun append(csq: CharSequence?): Appendable {
        builder.append(csq)
        return this
    }

    override fun append(csq: CharSequence?, start: Int, end: Int): Appendable {
        builder.append(csq, start, end)
        return this
    }

    override fun append(c: Char): Appendable {
        builder.append(c)
        return this
    }

    // --- Additional API similar to StringBuffer ---

    /**
     * Appends the string representation of the [obj] to this buffer.
     */
    fun append(obj: Any?): StringBuffer {
        builder.append(obj)
        return this
    }

    /**
     * Inserts the string [str] at the specified [index].
     */
    fun insert(index: Int, str: String): StringBuffer {
        if (index < 0 || index > length)
            throw IndexOutOfBoundsException("Index $index out of bounds for length $length")
        builder.insert(index, str)
        return this
    }

    /**
     * Sets the character at the specified [index] to [ch].
     */
    fun setCharAt(index: Int, ch: Char) {
        if (index < 0 || index >= length)
            throw IndexOutOfBoundsException("Index $index out of bounds for length $length")
        builder.setCharAt(index, ch)
    }

    fun getChars(srcBegin: Int, srcEnd: Int, dst: CharArray, dstBegin: Int) {
        if (srcBegin < 0 || srcEnd > this.length || srcBegin > srcEnd)
            throw IndexOutOfBoundsException("Invalid source indices: srcBegin=$srcBegin, srcEnd=$srcEnd, length=${this.length}")
        val count = srcEnd - srcBegin
        if (dstBegin < 0 || dstBegin + count > dst.size)
            throw IndexOutOfBoundsException("Invalid destination indices: dstBegin=$dstBegin, count=$count, dst.size=${dst.size}")
        for (i in 0 until count) {
            dst[dstBegin + i] = builder[srcBegin + i]
        }
    }

    /**
     * Returns the current string representation.
     */
    override fun toString(): String = builder.toString()

    /**
     * Compares this StringBuffer with another lexicographically.
     */
    override fun compareTo(other: StringBuffer): Int {
        return toString().compareTo(other.toString())
    }
}

fun StringBuilder.setCharAt(index: Int, ch: Char) {
    if (index < 0 || index >= this.length) {
        throw IndexOutOfBoundsException("Index $index out of bounds for length ${this.length}")
    }
    this.deleteAt(index)
    this.insert(index, ch)
}

