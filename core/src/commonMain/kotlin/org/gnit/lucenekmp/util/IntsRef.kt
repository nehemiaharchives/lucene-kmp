package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.jdkport.Arrays


/**
 * Represents int[], as a slice (offset + length) into an existing int[]. The [.ints] member
 * should never be null; use [.EMPTY_INTS] if necessary.
 *
 * @lucene.internal
 */
class IntsRef : Comparable<IntsRef>, Cloneable {
    /** The contents of the IntsRef. Should never be `null`.  */
    var ints: IntArray

    /** Offset of first valid integer.  */
    var offset: Int = 0

    /** Length of used ints.  */
    var length: Int = 0

    /** Create a IntsRef with [.EMPTY_INTS]  */
    constructor() {
        ints = EMPTY_INTS
    }

    /**
     * Create a IntsRef pointing to a new array of size `capacity`. Offset and length will
     * both be zero.
     */
    constructor(capacity: Int) {
        ints = IntArray(capacity)
    }

    /** This instance will directly reference ints w/o making a copy. ints should not be null.  */
    constructor(ints: IntArray, offset: Int, length: Int) {
        this.ints = ints
        this.offset = offset
        this.length = length
        require(this.isValid)
    }

    /**
     * Returns a shallow clone of this instance (the underlying ints are **not** copied and will be
     * shared by both the returned object and this object.
     *
     * @see .deepCopyOf
     */
    public override fun clone(): IntsRef {
        return IntsRef(ints, offset, length)
    }

    override fun hashCode(): Int {
        val prime = 31
        var result = 0
        val end = offset + length
        for (i in offset..<end) {
            result = prime * result + ints[i]
        }
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) {
            return false
        }
        if (other is IntsRef) {
            return this.intsEquals(other)
        }
        return false
    }

    fun intsEquals(other: IntsRef): Boolean {
        return Arrays.equals(
            this.ints,
            this.offset,
            this.offset + this.length,
            other.ints,
            other.offset,
            other.offset + other.length
        )
    }

    /** Signed int order comparison  */
    override fun compareTo(other: IntsRef): Int {
        return Arrays.compare(
            this.ints,
            this.offset,
            this.offset + this.length,
            other.ints,
            other.offset,
            other.offset + other.length
        )
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun toString(): String {
        val sb = StringBuilder()
        sb.append('[')
        val end = offset + length
        for (i in offset..<end) {
            if (i > offset) {
                sb.append(' ')
            }
            sb.append(ints[i].toHexString())
        }
        sb.append(']')
        return sb.toString()
    }

    val isValid: Boolean
        /** Performs internal consistency checks. Always returns true (or throws IllegalStateException)  */
        get() {
            checkNotNull(ints) { "ints is null" }
            check(length >= 0) { "length is negative: $length" }
            check(length <= ints.size) { "length is out of bounds: " + length + ",ints.length=" + ints.size }
            check(offset >= 0) { "offset is negative: $offset" }
            check(offset <= ints.size) { "offset out of bounds: " + offset + ",ints.length=" + ints.size }
            check(offset + length >= 0) { "offset+length is negative: offset=$offset,length=$length" }
            check(offset + length <= ints.size) {
                ("offset+length out of bounds: offset="
                        + offset
                        + ",length="
                        + length
                        + ",ints.length="
                        + ints.size)
            }
            return true
        }

    companion object {
        /** An empty integer array for convenience  */
        val EMPTY_INTS: IntArray = IntArray(0)

        /**
         * Creates a new IntsRef that points to a copy of the ints from `other`
         *
         *
         * The returned IntsRef will have a length of other.length and an offset of zero.
         */
        fun deepCopyOf(other: IntsRef): IntsRef {
            return IntsRef(
                ArrayUtil.copyOfSubArray(other.ints, other.offset, other.offset + other.length),
                0,
                other.length
            )
        }
    }
}
