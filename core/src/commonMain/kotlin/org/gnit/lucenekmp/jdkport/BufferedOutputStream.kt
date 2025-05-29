package org.gnit.lucenekmp.jdkport

import okio.IOException
import kotlin.math.min


/**
 * port of java.io.BufferedOutputStream
 *
 * The class implements a buffered output stream. By setting up such
 * an output stream, an application can write bytes to the underlying
 * output stream without necessarily causing a call to the underlying
 * system for each byte written.
 *
 * @author  Arthur van Hoff
 * @since   1.0
 */
open class BufferedOutputStream private constructor(out: OutputStream, initialSize: Int, maxSize: Int) :
    FilterOutputStream(out) {

    /**
     * The internal buffer where data is stored.
     */
    protected var buf: ByteArray

    /**
     * The number of valid bytes in the buffer. This value is always
     * in the range `0` through `buf.length`; elements
     * `buf[0]` through `buf[count-1]` contain valid
     * byte data.
     */
    protected var count: Int = 0

    /**
     * Max size of the internal buffer.
     */
    private val maxBufSize: Int

    /**
     * Creates a new buffered output stream.
     */
    init {
        require(initialSize > 0) { "Buffer size <= 0" }
        this.buf = ByteArray(initialSize) // resizable
        this.maxBufSize = maxSize
    }

    /**
     * Creates a new buffered output stream to write data to the
     * specified underlying output stream.
     *
     * @param   out   the underlying output stream.
     */
    constructor(out: OutputStream) : this(out, initialBufferSize(), DEFAULT_MAX_BUFFER_SIZE)

    /**
     * Creates a new buffered output stream to write data to the
     * specified underlying output stream with the specified buffer
     * size.
     *
     * @param   out    the underlying output stream.
     * @param   size   the buffer size.
     * @throws  IllegalArgumentException if size &lt;= 0.
     */
    constructor(out: OutputStream, size: Int) : this(out, size, size)

    /** Flush the internal buffer  */
    @Throws(IOException::class)
    private fun flushBuffer() {
        if (count > 0) {
            out!!.write(buf, 0, count)
            count = 0
        }
    }

    /**
     * Grow buf to fit an additional len bytes if needed.
     * If possible, it grows by len+1 to avoid flushing when len bytes
     * are added. A no-op if the buffer is not resizable.
     *
     * This method should only be called while holding the lock.
     */
    private fun growIfNeeded(len: Int) {
        var neededSize = count + len + 1
        if (neededSize < 0) neededSize = Int.Companion.MAX_VALUE
        val bufSize = buf.size
        if (neededSize > bufSize && bufSize < maxBufSize) {
            val newSize = min(neededSize, maxBufSize)
            buf = buf.copyOf(newSize)
        }
    }

    /**
     * Writes the specified byte to this buffered output stream.
     *
     * @param      b   the byte to be written.
     * @throws     IOException  if an I/O error occurs.
     */
    @Throws(IOException::class)
    override fun write(b: Int) {
        growIfNeeded(1)
        if (count >= buf.size) {
            flushBuffer()
        }
        buf[count++] = b.toByte()
    }

    /**
     * Writes `len` bytes from the specified byte array
     * starting at offset `off` to this buffered output stream.
     *
     *
     *  Ordinarily this method stores bytes from the given array into this
     * stream's buffer, flushing the buffer to the underlying output stream as
     * needed.  If the requested length is at least as large as this stream's
     * buffer, however, then this method will flush the buffer and write the
     * bytes directly to the underlying output stream.  Thus redundant
     * `BufferedOutputStream`s will not copy data unnecessarily.
     *
     * @param      b     the data.
     * @param      off   the start offset in the data.
     * @param      len   the number of bytes to write.
     * @throws     IOException  if an I/O error occurs.
     * @throws     IndexOutOfBoundsException {@inheritDoc}
     */
    @Throws(IOException::class)
    override fun write(b: ByteArray, off: Int, len: Int) {
        if (len >= maxBufSize) {
            /* If the request length exceeds the max size of the output buffer,
               flush the output buffer and then write the data directly.
               In this way buffered streams will cascade harmlessly. */
            flushBuffer()
            out!!.write(b, off, len)
            return
        }
        growIfNeeded(len)
        if (len > buf.size - count) {
            flushBuffer()
        }
        System.arraycopy(b, off, buf, count, len)
        count += len
    }

    /**
     * Flushes this buffered output stream. This forces any buffered
     * output bytes to be written out to the underlying output stream.
     *
     * @throws     IOException  if an I/O error occurs.
     * @see java.io.FilterOutputStream.out
     */
    @Throws(IOException::class)
    override fun flush() {
        flushBuffer()
        out!!.flush()
    }

    companion object {
        //private const val DEFAULT_INITIAL_BUFFER_SIZE = 512
        private const val DEFAULT_MAX_BUFFER_SIZE = 8192

        /**
         * Returns the buffer size to use when no output buffer size specified.
         */
        private fun initialBufferSize(): Int {
            return DEFAULT_MAX_BUFFER_SIZE
        }
    }
}
