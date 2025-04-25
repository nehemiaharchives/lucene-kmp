package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.Objects
import org.gnit.lucenekmp.jdkport.fromCharArray
import kotlin.jvm.JvmOverloads
import kotlin.math.min

/**
 * Represents char[], as a slice (offset + length) into an existing char[]. The [.chars]
 * member should never be null; use [.EMPTY_CHARS] if necessary.
 *
 * @lucene.internal
 */
class CharsRef : Comparable<CharsRef>, CharSequence, Cloneable {
    /** The contents of the CharsRef. Should never be `null`.  */
    var chars: CharArray

    /** Offset of first valid character.  */
    var offset: Int = 0

    /** Length of used characters.  */
    override var length: Int = 0

    override fun get(index: Int): Char {
        throw UnsupportedOperationException("get() is not supported. Use charAt() instead.")
    }

    /** Creates a new [CharsRef] initialized with an array of the given capacity  */
    constructor(capacity: Int) {
        chars = CharArray(capacity)
    }

    /** Creates a new [CharsRef] initialized with the given array, offset and length  */
    /** Creates a new [CharsRef] initialized an empty array zero-length  */
    @JvmOverloads
    constructor(chars: CharArray = EMPTY_CHARS, offset: Int = 0, length: Int = 0) {
        this.chars = chars
        this.offset = offset
        this.length = length
        require(this.isValid)
    }

    /** Creates a new [CharsRef] initialized with the given Strings character array  */
    constructor(string: String) {
        this.chars = string.toCharArray()
        this.offset = 0
        this.length = chars.size
    }

    /**
     * Returns a shallow clone of this instance (the underlying characters are **not** copied and
     * will be shared by both the returned object and this object.
     *
     * @see .deepCopyOf
     */
    public override fun clone(): CharsRef {
        return CharsRef(chars, offset, length)
    }

    override fun hashCode(): Int {
        return stringHashCode(chars, offset, length)
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) {
            return false
        }
        if (other is CharsRef) {
            return this.charsEquals(other)
        }
        return false
    }

    fun charsEquals(other: CharsRef): Boolean {
        return Arrays.equals(
            this.chars,
            this.offset,
            this.offset + this.length,
            other.chars,
            other.offset,
            other.offset + other.length
        )
    }

    /** Signed int order comparison  */
    override fun compareTo(other: CharsRef): Int {
        return Arrays.compare(
            this.chars,
            this.offset,
            this.offset + this.length,
            other.chars,
            other.offset,
            other.offset + other.length
        )
    }

    override fun toString(): String {
        return String.fromCharArray(chars, offset, length)
    }

    fun length(): Int {
        return length
    }

    fun charAt(index: Int): Char {
        // NOTE: must do a real check here to meet the specs of CharSequence
        Objects.checkIndex(index, length)
        return chars[offset + index]
    }

    override fun subSequence(start: Int, end: Int): CharSequence {
        // NOTE: must do a real check here to meet the specs of CharSequence
        Objects.checkFromToIndex(start, end, length)
        return CharsRef(chars, offset + start, end - start)
    }

    @Deprecated("This comparator is only a transition mechanism")
    private class UTF16SortedAsUTF8Comparator  // Only singleton
        : Comparator<CharsRef> {
        override fun compare(a: CharsRef, b: CharsRef): Int {
            val aEnd = a.offset + a.length
            val bEnd = b.offset + b.length
            val i: Int = Arrays.mismatch(a.chars, a.offset, aEnd, b.chars, b.offset, bEnd)

            if (i >= 0 && i < min(a.length, b.length)) {
                // http://icu-project.org/docs/papers/utf16_code_point_order.html

                var aChar = a.chars[a.offset + i]
                var bChar = b.chars[b.offset + i]
                /* aChar != bChar, fix up each one if they're both in or above the surrogate range, then compare them */
                if (aChar.code >= 0xd800 && bChar.code >= 0xd800) {
                    if (aChar.code >= 0xe000) {
                        aChar -= 0x800.toChar().code
                    } else {
                        aChar += 0x2000.toChar().code
                    }

                    if (bChar.code >= 0xe000) {
                        bChar -= 0x800.toChar().code
                    } else {
                        bChar += 0x2000.toChar().code
                    }
                }

                /* now aChar and bChar are in code point order */
                return aChar.code - bChar.code /* int must be 32 bits wide */
            }

            // One is a prefix of the other, or, they are equal:
            return a.length - b.length
        }
    }

    val isValid: Boolean
        /** Performs internal consistency checks. Always returns true (or throws IllegalStateException)  */
        get() {
            checkNotNull(chars) { "chars is null" }
            check(length >= 0) { "length is negative: $length" }
            check(length <= chars.size) { "length is out of bounds: " + length + ",chars.length=" + chars.size }
            check(offset >= 0) { "offset is negative: $offset" }
            check(offset <= chars.size) { "offset out of bounds: " + offset + ",chars.length=" + chars.size }
            check(offset + length >= 0) { "offset+length is negative: offset=$offset,length=$length" }
            check(offset + length <= chars.size) {
                ("offset+length out of bounds: offset="
                        + offset
                        + ",length="
                        + length
                        + ",chars.length="
                        + chars.size)
            }
            return true
        }

    companion object {
        /** An empty character array for convenience  */
        val EMPTY_CHARS: CharArray = CharArray(0)

        /**
         * @return the hash code of the given char sub-array, calculated by [String.hashCode]
         * specification
         */
        fun stringHashCode(chars: CharArray, offset: Int, length: Int): Int {
            val end = offset + length
            var result = 0
            for (i in offset..<end) {
                result = 31 * result + chars[i].code
            }
            return result
        }

        @Deprecated("This comparator is only a transition mechanism")
        private val utf16SortedAsUTF8SortOrder: Comparator<CharsRef> = UTF16SortedAsUTF8Comparator()

        @get:Deprecated("This comparator is only a transition mechanism")
        val uTF16SortedAsUTF8Comparator: Comparator<CharsRef>
            get() = utf16SortedAsUTF8SortOrder

        /**
         * Creates a new CharsRef that points to a copy of the chars from `other`
         *
         *
         * The returned CharsRef will have a length of other.length and an offset of zero.
         */
        fun deepCopyOf(other: CharsRef): CharsRef {
            return CharsRef(
                ArrayUtil.copyOfSubArray(other.chars, other.offset, other.offset + other.length),
                0,
                other.length
            )
        }
    }
}
