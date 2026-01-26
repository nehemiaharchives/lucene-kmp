package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.assert

/**
 * A builder for [CharsRef] instances.
 *
 * @lucene.internal
 */
class CharsRefBuilder : Appendable {
    private val ref: CharsRef = CharsRef()

    /** Return a reference to the chars of this builder.  */
    fun chars(): CharArray {
        return ref.chars
    }

    /** Return the number of chars in this buffer.  */
    fun length(): Int {
        return ref.length
    }

    /** Set the length.  */
    fun setLength(length: Int) {
        this.ref.length = length
    }

    /** Return the char at the given offset.  */
    fun charAt(offset: Int): Char {
        return ref.chars[offset]
    }

    /** Set a char.  */
    fun setCharAt(offset: Int, b: Char) {
        ref.chars[offset] = b
    }

    /** Reset this builder to the empty state.  */
    fun clear() {
        ref.length = 0
    }

    override fun append(csq: CharSequence?): CharsRefBuilder {
        if (csq == null) {
            return append(NULL_STRING)
        }
        return append(csq, 0, csq.length)
    }

    override fun append(csq: CharSequence?, start: Int, end: Int): CharsRefBuilder {
        if (csq == null) {
            return append(NULL_STRING)
        }
        grow(ref.length + end - start)
        for (i in start..<end) {
            setCharAt(ref.length++, csq.get(i))
        }
        return this
    }

    override fun append(c: Char): CharsRefBuilder {
        grow(ref.length + 1)
        setCharAt(ref.length++, c)
        return this
    }

    /** Copies the given [CharsRef] referenced content into this instance.  */
    fun copyChars(other: CharsRef) {
        copyChars(other.chars, other.offset, other.length)
    }

    /** Used to grow the reference array.  */
    fun grow(newLength: Int) {
        ref.chars = ArrayUtil.grow(ref.chars, newLength)
    }

    /** Copy the provided bytes, interpreted as UTF-8 bytes.  */
    fun copyUTF8Bytes(bytes: ByteArray, offset: Int, length: Int) {
        grow(length)
        ref.length = UnicodeUtil.UTF8toUTF16(bytes, offset, length, ref.chars)
    }

    /** Copy the provided bytes, interpreted as UTF-8 bytes.  */
    fun copyUTF8Bytes(bytes: BytesRef) {
        copyUTF8Bytes(bytes.bytes, bytes.offset, bytes.length)
    }

    /** Copies the given array into this instance.  */
    fun copyChars(otherChars: CharArray, otherOffset: Int, otherLength: Int) {
        grow(otherLength)
        System.arraycopy(otherChars, otherOffset, ref.chars, 0, otherLength)
        ref.length = otherLength
    }

    /** Appends the given array to this CharsRef  */
    fun append(otherChars: CharArray, otherOffset: Int, otherLength: Int) {
        val newLen: Int = ref.length + otherLength
        grow(newLen)
        System.arraycopy(otherChars, otherOffset, ref.chars, ref.length, otherLength)
        ref.length = newLen
    }

    /**
     * Return a [CharsRef] that points to the internal content of this builder. Any update to
     * the content of this builder might invalidate the provided `ref` and vice-versa.
     */
    fun get(): CharsRef {
        assert(ref.offset == 0) { "Modifying the offset of the returned ref is illegal" }
        return ref
    }

    /** Build a new [CharsRef] that has the same content as this builder.  */
    fun toCharsRef(): CharsRef {
        return CharsRef(
            ArrayUtil.copyOfSubArray(
                ref.chars,
                0,
                ref.length
            ), 0, ref.length
        )
    }

    override fun toString(): String {
        return get().toString()
    }

    override fun equals(obj: Any?): Boolean {
        throw UnsupportedOperationException()
    }

    override fun hashCode(): Int {
        throw UnsupportedOperationException()
    }

    companion object {
        private const val NULL_STRING = "null"
    }
}
