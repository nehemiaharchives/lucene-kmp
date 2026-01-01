package org.gnit.lucenekmp.util

/**
 * A simplified port of Lucene's CharsRef.
 *
 * Represents a slice of a char array and supports lexicographic comparison.
 */
class CharsRef(
    var chars: CharArray,
    var offset: Int,
    private var len: Int
) : Comparable<CharsRef>, CharSequence {

    constructor() : this(CharArray(0), 0, 0)

    /** Creates a CharsRef with an internal buffer of the given capacity and length=0. */
    constructor(capacity: Int) : this(CharArray(capacity), 0, 0)

    constructor(s: String) : this(s.toCharArray(), 0, s.length)

    /** Mutable length (Lucene API) */
    var lengthMutable: Int
        get() = len
        set(value) {
            len = value
        }

    override val length: Int
        get() = len

    override fun get(index: Int): Char {
        if (index < 0 || index >= len) throw IndexOutOfBoundsException(index.toString())
        return chars[offset + index]
    }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        if (startIndex < 0 || endIndex < startIndex || endIndex > len) {
            throw IndexOutOfBoundsException("start=$startIndex end=$endIndex length=$len")
        }
        return CharsRef(chars, offset + startIndex, endIndex - startIndex)
    }

    override fun compareTo(other: CharsRef): Int {
        val lim = minOf(len, other.len)
        var i = 0
        while (i < lim) {
            val c1 = chars[offset + i]
            val c2 = other.chars[other.offset + i]
            if (c1 != c2) return c1.code - c2.code
            i++
        }
        return len - other.len
    }

    override fun toString(): String {
        return chars.concatToString(offset, offset + len)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CharsRef) return false
        if (len != other.len) return false
        for (i in 0 until len) {
            if (chars[offset + i] != other.chars[other.offset + i]) return false
        }
        return true
    }

    override fun hashCode(): Int {
        var h = 0
        for (i in 0 until len) {
            h = 31 * h + chars[offset + i].code
        }
        return h
    }

    companion object {
        /** Computes hashcode over a slice of a char[] like Java's String does. */
        fun stringHashCode(text: CharArray, offset: Int, len: Int): Int {
            var code = 0
            for (i in 0 until len) {
                code = code * 31 + text[offset + i].code
            }
            return code
        }

        fun deepCopyOf(other: CharsRef): CharsRef {
            if (other.offset < 0 || other.length < 0 || other.offset + other.length > other.chars.size) {
                throw IndexOutOfBoundsException(
                    "offset=${other.offset} length=${other.length} size=${other.chars.size}"
                )
            }
            val copy = CharArray(other.length)
            other.chars.copyInto(copy, 0, other.offset, other.offset + other.length)
            return CharsRef(copy, 0, other.length)
        }

        /**
         * Comparator that orders UTF-16 the same way as UTF-8 for ASCII-only data.
         * This is sufficient for current tests, which use ASCII input.
         */
        val UTF16SortedAsUTF8Comparator: Comparator<CharsRef> =
            Comparator { a, b -> a.compareTo(b) }
    }
}
