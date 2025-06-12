package org.gnit.lucenekmp.util

/**
 * A builder for [CharsRef] instances.
 *
 * @lucene.internal
 */
class CharsRefBuilder : Appendable {
    companion object {
        private const val NULL_STRING = "null"
    }

    private val ref: CharsRef = CharsRef()

    /** Return a reference to the chars of this builder. */
    fun chars(): CharArray = ref.chars

    /** Return the number of chars in this buffer. */
    fun length(): Int = ref.length

    /** Set the length. */
    fun setLength(length: Int) {
        ref.length = length
    }

    /** Return the char at the given offset. */
    fun charAt(offset: Int): Char = ref.chars[offset]

    /** Set a char. */
    fun setCharAt(offset: Int, c: Char) {
        ref.chars[offset] = c
    }

    /** Reset this builder to the empty state. */
    fun clear() {
        ref.length = 0
    }

    override fun append(csq: CharSequence?): CharsRefBuilder {
        val s = csq ?: NULL_STRING
        return append(s, 0, s.length)
    }

    override fun append(csq: CharSequence?, start: Int, end: Int): CharsRefBuilder {
        val s = csq ?: NULL_STRING
        grow(ref.length + end - start)
        for (i in start until end) {
            setCharAt(ref.length++, s[i])
        }
        return this
    }

    override fun append(c: Char): CharsRefBuilder {
        grow(ref.length + 1)
        setCharAt(ref.length++, c)
        return this
    }

    /** Copies the given [CharsRef] referenced content into this instance. */
    fun copyChars(other: CharsRef) {
        copyChars(other.chars, other.offset, other.length)
    }

    /** Used to grow the reference array. */
    fun grow(newLength: Int) {
        ref.chars = ArrayUtil.grow(ref.chars, newLength)
    }

    /** Copy the provided bytes, interpreted as UTF-8 bytes. */
    fun copyUTF8Bytes(bytes: ByteArray, offset: Int, length: Int) {
        grow(length)
        ref.length = UnicodeUtil.UTF8toUTF16(bytes, offset, length, ref.chars)
    }

    /** Copy the provided bytes, interpreted as UTF-8 bytes. */
    fun copyUTF8Bytes(bytes: BytesRef) {
        copyUTF8Bytes(bytes.bytes, bytes.offset, bytes.length)
    }

    /** Copies the given array into this instance. */
    fun copyChars(otherChars: CharArray, otherOffset: Int, otherLength: Int) {
        grow(otherLength)
        otherChars.copyInto(ref.chars, 0, otherOffset, otherOffset + otherLength)
        ref.length = otherLength
    }

    /** Appends the given array to this [CharsRef]. */
    fun append(otherChars: CharArray, otherOffset: Int, otherLength: Int) {
        val newLen = ref.length + otherLength
        grow(newLen)
        otherChars.copyInto(ref.chars, ref.length, otherOffset, otherOffset + otherLength)
        ref.length = newLen
    }

    /**
     * Return a [CharsRef] that points to the internal content of this builder.
     * Any update to the content of this builder might invalidate the provided
     * `ref` and vice-versa.
     */
    fun get(): CharsRef {
        require(ref.offset == 0) { "Modifying the offset of the returned ref is illegal" }
        return ref
    }

    /** Build a new [CharsRef] that has the same content as this builder. */
    fun toCharsRef(): CharsRef {
        return CharsRef(ArrayUtil.copyOfSubArray(ref.chars, 0, ref.length), 0, ref.length)
    }

    override fun toString(): String = get().toString()

    override fun hashCode(): Int {
        throw UnsupportedOperationException()
    }

    override fun equals(other: Any?): Boolean {
        throw UnsupportedOperationException()
    }
}
