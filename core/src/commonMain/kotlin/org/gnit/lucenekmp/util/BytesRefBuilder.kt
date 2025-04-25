package org.gnit.lucenekmp.util

import kotlin.jvm.JvmOverloads


/**
 * A builder for [BytesRef] instances.
 *
 * @lucene.internal
 */
class BytesRefBuilder {
    private val ref: BytesRef

    /** Sole constructor.  */
    init {
        ref = BytesRef()
    }

    /** Return a reference to the bytes of this builder.  */
    fun bytes(): ByteArray {
        return ref.bytes
    }

    /** Return the number of bytes in this buffer.  */
    fun length(): Int {
        return ref.length
    }

    /** Set the length.  */
    fun setLength(length: Int) {
        this.ref.length = length
    }

    /** Return the byte at the given offset.  */
    fun byteAt(offset: Int): Byte {
        return ref.bytes[offset]
    }

    /** Set a byte.  */
    fun setByteAt(offset: Int, b: Byte) {
        ref.bytes[offset] = b
    }

    /** Ensure that this builder can hold at least `capacity` bytes without resizing.  */
    fun grow(capacity: Int) {
        ref.bytes = ArrayUtil.grow(ref.bytes, capacity)
    }

    /**
     * Used to grow the builder without copying bytes. see [ArrayUtil.growNoCopy].
     */
    fun growNoCopy(capacity: Int) {
        ref.bytes = ArrayUtil.growNoCopy(ref.bytes, capacity)
    }

    /** Append a single byte to this builder.  */
    fun append(b: Byte) {
        grow(ref.length + 1)
        ref.bytes[ref.length++] = b
    }

    /** Append the provided bytes to this builder.  */
    fun append(b: ByteArray, off: Int, len: Int) {
        grow(ref.length + len)
        b.copyInto(ref.bytes, ref.length, off, off + len)
        ref.length += len
    }

    /** Append the provided bytes to this builder.  */
    fun append(ref: BytesRef) {
        append(ref.bytes, ref.offset, ref.length)
    }

    /** Append the provided bytes to this builder.  */
    fun append(builder: BytesRefBuilder) {
        append(builder.get())
    }

    /** Reset this builder to the empty state.  */
    fun clear() {
        setLength(0)
    }

    /**
     * Replace the content of this builder with the provided bytes. Equivalent to calling [ ][.clear] and then [.append].
     */
    fun copyBytes(b: ByteArray, off: Int, len: Int) {
        require(0 == ref.offset)
        ref.length = len
        growNoCopy(len)
        b.copyInto(ref.bytes, 0, off, off + len)
    }

    /**
     * Replace the content of this builder with the provided bytes. Equivalent to calling [ ][.clear] and then [.append].
     */
    fun copyBytes(ref: BytesRef) {
        copyBytes(ref.bytes, ref.offset, ref.length)
    }

    /**
     * Replace the content of this builder with the provided bytes. Equivalent to calling [ ][.clear] and then [.append].
     */
    fun copyBytes(builder: BytesRefBuilder) {
        copyBytes(builder.get())
    }

    /**
     * Replace the content of this buffer with UTF-8 encoded bytes that would represent the provided
     * text.
     */
    /**
     * Replace the content of this buffer with UTF-8 encoded bytes that would represent the provided
     * text.
     */
    @JvmOverloads
    fun copyChars(text: CharSequence?, off: Int = 0, len: Int = text!!.length) {
        growNoCopy(UnicodeUtil.maxUTF8Length(len))
        ref.length = UnicodeUtil.UTF16toUTF8(text!!, off, len, ref.bytes)
    }

    /**
     * Replace the content of this buffer with UTF-8 encoded bytes that would represent the provided
     * text.
     */
    fun copyChars(text: CharArray?, off: Int, len: Int) {
        growNoCopy(UnicodeUtil.maxUTF8Length(len))
        ref.length = UnicodeUtil.UTF16toUTF8(text!!, off, len, ref.bytes)
    }

    /**
     * Return a [BytesRef] that points to the internal content of this builder. Any update to
     * the content of this builder might invalidate the provided `ref` and vice-versa.
     */
    fun get(): BytesRef {
        require(0 === ref.offset) { "Modifying the offset of the returned ref is illegal" }
        return ref
    }

    /** Build a new [BytesRef] that has the same content as this buffer.  */
    fun toBytesRef(): BytesRef {
        return BytesRef(ArrayUtil.copyOfSubArray(ref.bytes, 0, ref.length))
    }

    override fun equals(obj: Any?): Boolean {
        throw UnsupportedOperationException()
    }

    override fun hashCode(): Int {
        throw UnsupportedOperationException()
    }
}
