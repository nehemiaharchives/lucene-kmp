package org.gnit.lucenekmp.util


/**
 * A builder for [IntsRef] instances.
 *
 * @lucene.internal
 */
class IntsRefBuilder {
    private val ref: IntsRef

    /** Sole constructor.  */
    init {
        ref = IntsRef()
    }

    /** Return a reference to the ints of this builder.  */
    fun ints(): IntArray {
        return ref.ints
    }

    /** Return the number of ints in this buffer.  */
    fun length(): Int {
        return ref.length
    }

    /** Set the length.  */
    fun setLength(length: Int) {
        this.ref.length = length
    }

    /** Empty this builder.  */
    fun clear() {
        setLength(0)
    }

    /** Return the int at the given offset.  */
    fun intAt(offset: Int): Int {
        return ref.ints[offset]
    }

    /** Set an int.  */
    fun setIntAt(offset: Int, b: Int) {
        ref.ints[offset] = b
    }

    /** Append the provided int to this buffer.  */
    fun append(i: Int) {
        grow(ref.length + 1)
        ref.ints[ref.length++] = i
    }

    /**
     * Used to grow the reference array.
     *
     *
     * In general this should not be used as it does not take the offset into account.
     *
     * @lucene.internal
     */
    fun grow(newLength: Int) {
        ref.ints = ArrayUtil.grow(ref.ints, newLength)
    }

    /** Grow the reference array without copying the origin data to the new array.  */
    fun growNoCopy(newLength: Int) {
        ref.ints = ArrayUtil.growNoCopy(ref.ints, newLength)
    }

    /** Copies the given array into this instance.  */
    fun copyInts(otherInts: IntArray, otherOffset: Int, otherLength: Int) {
        growNoCopy(otherLength)
        /*java.lang.System.arraycopy(otherInts, otherOffset, ref.ints, 0, otherLength)*/
        otherInts.copyInto(
            destination = ref.ints,
            destinationOffset = 0,
            startIndex = otherOffset,
            endIndex = otherOffset + otherLength
        )
        ref.length = otherLength
    }

    /** Copies the given array into this instance.  */
    fun copyInts(ints: IntsRef) {
        copyInts(ints.ints, ints.offset, ints.length)
    }

    /**
     * Copy the given UTF-8 bytes into this builder. Works as if the bytes were first converted from
     * UTF-8 to UTF-32 and then copied into this builder.
     */
    fun copyUTF8Bytes(bytes: BytesRef) {
        growNoCopy(bytes.length)
        ref.length = UnicodeUtil.UTF8toUTF32(bytes, ref.ints)
    }

    /**
     * Return a [IntsRef] that points to the internal content of this builder. Any update to the
     * content of this builder might invalidate the provided `ref` and vice-versa.
     */
    fun get(): IntsRef {
        require(ref.offset === 0) { "Modifying the offset of the returned ref is illegal" }
        return ref
    }

    /** Build a new [CharsRef] that has the same content as this builder.  */
    fun toIntsRef(): IntsRef {
        return IntsRef.deepCopyOf(get())
    }

    override fun equals(obj: Any?): Boolean {
        throw UnsupportedOperationException()
    }

    override fun hashCode(): Int {
        throw UnsupportedOperationException()
    }
}
