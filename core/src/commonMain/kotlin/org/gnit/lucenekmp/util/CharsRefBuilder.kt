package org.gnit.lucenekmp.util

/** A minimal port of Lucene's CharsRefBuilder. */
class CharsRefBuilder {
    private var chars: CharArray = CharArray(0)
    var len: Int = 0

    fun clear() {
        len = 0
    }

    fun length(): Int = len

    /** Set the length.  */
    fun setLength(length: Int) {
        len = length
    }

    fun chars(): CharArray = chars

    fun get(): CharsRef = CharsRef(chars, 0, len)

    fun append(seq: CharSequence) {
        grow(len + seq.length)
        for (i in 0 until seq.length) {
            chars[len + i] = seq[i]
        }
        len += seq.length
    }

    fun append(buffer: CharArray, offset: Int, length: Int) {
        if (length == 0) return
        grow(len + length)
        buffer.copyInto(chars, destinationOffset = len, startIndex = offset, endIndex = offset + length)
        len += length
    }

    fun copyChars(seq: CharSequence) {
        clear()
        append(seq)
    }

    fun copyChars(buffer: CharArray, offset: Int, length: Int) {
        clear()
        append(buffer, offset, length)
    }

    override fun toString(): String = chars.concatToString(0, len)

    /** Ensures internal buffer can hold at least newLength characters. */
    fun grow(newLength: Int) {
        if (chars.size < newLength) {
            val newSize = maxOf(newLength, chars.size * 2 + 1)
            chars = chars.copyOf(newSize)
        }
    }

    private fun growInternal(newLength: Int) {
        grow(newLength)
    }
}
