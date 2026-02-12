package org.gnit.lucenekmp.jdkport

import okio.IOException

/**
 * This class implements an output stream in which the data is
 * written into a byte array. The buffer automatically grows as data
 * is written to it.
 * The data can be retrieved using `toByteArray()` and
 * `toString()`.
 *
 *
 * Closing a `ByteArrayOutputStream` has no effect. The methods in
 * this class can be called after the stream has been closed without
 * generating an `IOException`.
 *
 * @author  Arthur van Hoff
 * @since   1.0
 */
class ByteArrayOutputStream (size: Int = 32) : OutputStream() {
    /**
     * The buffer where data is stored.
     */
    private var buf: ByteArray

    /**
     * The number of valid bytes in the buffer.
     */
    private var count: Int = 0

    /**
     * Creates a new `ByteArrayOutputStream`, with a buffer capacity of
     * the specified size, in bytes.
     *
     * @param  size   the initial size.
     * @throws IllegalArgumentException if size is negative.
     */
    /**
     * Creates a new `ByteArrayOutputStream`. The buffer capacity is
     * initially 32 bytes, though its size increases if necessary.
     */
    init {
        require(size >= 0) {
            ("Negative initial size: "
                    + size)
        }
        buf = ByteArray(size)
    }

    /**
     * Increases the capacity if necessary to ensure that it can hold
     * at least the number of elements specified by the minimum
     * capacity argument.
     *
     * @param  minCapacity the desired minimum capacity.
     * @throws OutOfMemoryError if `minCapacity < 0` and
     * `minCapacity - buf.length > 0`.  This is interpreted as a
     * request for the unsatisfiably large capacity.
     * `(long) Integer.MAX_VALUE + (minCapacity - Integer.MAX_VALUE)`.
     */
    private fun ensureCapacity(minCapacity: Int) {
        // overflow-conscious code
        val oldCapacity = buf.size
        val minGrowth = minCapacity - oldCapacity
        if (minGrowth > 0) {
            buf = buf.copyOf(
                ArraysSupport.newLength(
                    oldCapacity,
                    minGrowth, oldCapacity /* preferred growth */
                )
            )
        }
    }

    /**
     * Writes the specified byte to this `ByteArrayOutputStream`.
     *
     * @param   b   the byte to be written.
     */
    override fun write(b: Int) {
        ensureCapacity(count + 1)
        buf[count] = b.toByte()
        count += 1
    }

    /**
     * Writes `len` bytes from the specified byte array
     * starting at offset `off` to this `ByteArrayOutputStream`.
     *
     * @param   b     {@inheritDoc}
     * @param   off   {@inheritDoc}
     * @param   len   {@inheritDoc}
     * @throws  NullPointerException if `b` is `null`.
     * @throws  IndexOutOfBoundsException if `off` is negative,
     * `len` is negative, or `len` is greater than
     * `b.length - off`
     */
    override fun write(b: ByteArray, off: Int, len: Int) {
        Objects.checkFromIndexSize(off, len, b.size)
        ensureCapacity(count + len)
        System.arraycopy(b, off, buf, count, len)
        count += len
    }

    /**
     * Writes the complete contents of the specified byte array
     * to this `ByteArrayOutputStream`.
     *
     * @apiNote
     * This method is equivalent to [ write(b, 0, b.length)][.write].
     *
     * @param   b     the data.
     * @throws  NullPointerException if `b` is `null`.
     * @since   11
     */
    fun writeBytes(b: ByteArray) {
        write(b, 0, b.size)
    }

    /**
     * Writes the complete contents of this `ByteArrayOutputStream` to
     * the specified output stream argument, as if by calling the output
     * stream's write method using `out.write(buf, 0, count)`.
     *
     * @param   out   the output stream to which to write the data.
     * @throws  NullPointerException if `out` is `null`.
     * @throws  okio.IOException if an I/O error occurs.
     */
    @Throws(IOException::class)
    fun writeTo(out: OutputStream) {
        out.write(buf, 0, count)
    }

    /**
     * Resets the `count` field of this `ByteArrayOutputStream`
     * to zero, so that all currently accumulated output in the
     * output stream is discarded. The output stream can be used again,
     * reusing the already allocated buffer space.
     *
     * @see java.io.ByteArrayInputStream.count
     */
    fun reset() {
        count = 0
    }

    /**
     * Creates a newly allocated byte array. Its size is the current
     * size of this output stream and the valid contents of the buffer
     * have been copied into it.
     *
     * @return  the current contents of this output stream, as a byte array.
     * @see java.io.ByteArrayOutputStream.size
     */
    fun toByteArray(): ByteArray {
        return buf.copyOf(count)
    }

    /**
     * Returns the current size of the buffer.
     *
     * @return  the value of the `count` field, which is the number
     * of valid bytes in this output stream.
     * @see java.io.ByteArrayOutputStream.count
     */
    fun size(): Int {
        return count
    }

    /**
     * Converts the buffer's contents into a string decoding bytes using the
     * default charset. The length of the new `String`
     * is a function of the charset, and hence may not be equal to the
     * size of the buffer.
     *
     *
     *  This method always replaces malformed-input and unmappable-character
     * sequences with the default replacement string for the
     * default charset. The [CharsetDecoder]
     * class should be used when more control over the decoding process is
     * required.
     *
     * @see Charset.defaultCharset
     * @return String decoded from the buffer's contents.
     * @since  1.1
     */
    override fun toString(): String {
        return /*String(buf, 0, count)*/ String.fromByteArray(buf.copyOf(count), StandardCharsets.UTF_8)
    }

    /**
     * Converts the buffer's contents into a string by decoding the bytes using
     * the named [charset][Charset].
     *
     *
     *  This method is equivalent to `#toString(charset)` that takes a
     * [charset][Charset].
     *
     *
     *  An invocation of this method of the form
     *
     * {@snippet lang=java :
     * *     ByteArrayOutputStream b;
     * *     b.toString("UTF-8")
     * * }
     *
     * behaves in exactly the same way as the expression
     *
     * {@snippet lang=java :
     * *     ByteArrayOutputStream b;
     * *     b.toString(StandardCharsets.UTF_8)
     * * }
     *
     *
     * @param  charsetName  the name of a supported
     * [charset][Charset]
     * @return String decoded from the buffer's contents.
     * @throws UnsupportedEncodingException
     * If the named charset is not supported
     * @since  1.1
     */
    /*@Throws(UnsupportedEncodingException::class)*/
    fun toString(charsetName: String): String {
        return /*String(buf, 0, count, charset(charsetName))*/ String.fromByteArray(
            buf.copyOf(count), StandardCharsets.UTF_8
        )
    }

    /**
     * Converts the buffer's contents into a string by decoding the bytes using
     * the specified [charset][Charset]. The length of the new
     * `String` is a function of the charset, and hence may not be equal
     * to the length of the byte array.
     *
     *
     *  This method always replaces malformed-input and unmappable-character
     * sequences with the charset's default replacement string. The [ ] class should be used when more control
     * over the decoding process is required.
     *
     * @param      charset  the [charset][Charset]
     * to be used to decode the `bytes`
     * @return     String decoded from the buffer's contents.
     * @since      10
     */
    fun toString(charset: Charset): String {
        return /*String(buf, 0, count, charset)*/ String.fromByteArray(
            buf.copyOf(count), charset
        )
    }

    /**
     * Creates a newly allocated string. Its size is the current size of
     * the output stream and the valid contents of the buffer have been
     * copied into it. Each character *c* in the resulting string is
     * constructed from the corresponding element *b* in the byte
     * array such that:
     * {@snippet lang=java :
     * *     c == (char)(((hibyte & 0xff) << 8) | (b & 0xff))
     * * }
     *
     * @param      hibyte    the high byte of each resulting Unicode character.
     * @return     the current contents of the output stream, as a string.
     * @see java.io.ByteArrayOutputStream.size
     * @see java.io.ByteArrayOutputStream.toString
     * @see java.io.ByteArrayOutputStream.toString
     * @see Charset.defaultCharset
     */
    /*@Deprecated(
        """This method does not properly convert bytes into characters.
      As of JDK&nbsp;1.1, the preferred way to do this is via the
      {@link #toString(String charsetName)} or {@link #toString(Charset charset)}
      method, which takes an encoding-name or charset argument,
      or the {@code toString()} method, which uses the default charset.
     
      """
    )*/
    /*fun toString(hibyte: Int): String {
        return String(buf, hibyte, 0, count)
    }*/

    /**
     * Closing a `ByteArrayOutputStream` has no effect. The methods in
     * this class can be called after the stream has been closed without
     * generating an `IOException`.
     */
    override fun close() {
    }
}
