package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.toHexString
import org.gnit.lucenekmp.jdkport.Cloneable

/**
 * Represents long[], as a slice (offset + length) into an existing long[]. The [.longs]
 * member should never be null; use [.EMPTY_LONGS] if necessary.
 *
 * @lucene.internal
 */
class LongsRef : Comparable<LongsRef>, Cloneable<LongsRef> {
    /** The contents of the LongsRef. Should never be `null`.  */
    var longs: LongArray

    /** Offset of first valid long.  */
    var offset: Int = 0

    /** Length of used longs.  */
    var length: Int = 0

    /** Create a LongsRef with [.EMPTY_LONGS]  */
    constructor() {
        longs = EMPTY_LONGS
    }

    /**
     * Create a LongsRef pointing to a new array of size `capacity`. Offset and length will
     * both be zero.
     */
    constructor(capacity: Int) {
        longs = LongArray(capacity)
    }

    /** This instance will directly reference longs w/o making a copy. longs should not be null  */
    constructor(longs: LongArray, offset: Int, length: Int) {
        this.longs = longs
        this.offset = offset
        this.length = length
        require(this.isValid)
    }

    /**
     * Returns a shallow clone of this instance (the underlying longs are **not** copied and will
     * be shared by both the returned object and this object.
     *
     * @see .deepCopyOf
     */
    override fun clone(): LongsRef {
        return LongsRef(longs, offset, length)
    }

    override fun hashCode(): Int {
        val prime = 31
        var result = 0
        val end = offset + length
        for (i in offset..<end) {
            result = prime * result + (longs[i] xor (longs[i] ushr 32)).toInt()
        }
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) {
            return false
        }
        if (other is LongsRef) {
            return this.longsEquals(other)
        }
        return false
    }

    fun longsEquals(other: LongsRef): Boolean {
        return Arrays.equals(
            this.longs,
            this.offset,
            this.offset + this.length,
            other.longs,
            other.offset,
            other.offset + other.length
        )
    }

    /** Signed int order comparison  */
    override fun compareTo(other: LongsRef): Int {
        return Arrays.compare(
            this.longs,
            this.offset,
            this.offset + this.length,
            other.longs,
            other.offset,
            other.offset + other.length
        )
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append('[')
        val end = offset + length
        for (i in offset..<end) {
            if (i > offset) {
                sb.append(' ')
            }
            sb.append(Long.toHexString(longs[i].toLong()))
        }
        sb.append(']')
        return sb.toString()
    }

    val isValid: Boolean
        /** Performs internal consistency checks. Always returns true (or throws IllegalStateException)  */
        get() {
            checkNotNull(longs) { "longs is null" }
            check(length >= 0) { "length is negative: $length" }
            check(length <= longs.size) { "length is out of bounds: " + length + ",longs.length=" + longs.size }
            check(offset >= 0) { "offset is negative: $offset" }
            check(offset <= longs.size) { "offset out of bounds: " + offset + ",longs.length=" + longs.size }
            check(offset + length >= 0) { "offset+length is negative: offset=$offset,length=$length" }
            check(offset + length <= longs.size) {
                ("offset+length out of bounds: offset="
                        + offset
                        + ",length="
                        + length
                        + ",longs.length="
                        + longs.size)
            }
            return true
        }

    companion object {
        /** An empty long array for convenience  */
        val EMPTY_LONGS: LongArray = LongArray(0)

        /**
         * Creates a new LongsRef that points to a copy of the longs from `other`
         *
         *
         * The returned IntsRef will have a length of other.length and an offset of zero.
         */
        fun deepCopyOf(other: LongsRef): LongsRef {
            return LongsRef(
                ArrayUtil.copyOfSubArray(other.longs, other.offset, other.offset + other.length),
                0,
                other.length
            )
        }
    }
}
