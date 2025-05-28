package org.gnit.lucenekmp.jdkport

import okio.IOException
import kotlin.concurrent.Volatile
import kotlin.jvm.JvmOverloads
import kotlin.math.max

/**
 * A `BufferedInputStream` adds
 * functionality to another input stream-namely,
 * the ability to buffer the input and to
 * support the `mark` and `reset`
 * methods. When  the `BufferedInputStream`
 * is created, an internal buffer array is
 * created. As bytes  from the stream are read
 * or skipped, the internal buffer is refilled
 * as necessary  from the contained input stream,
 * many bytes at a time. The `mark`
 * operation  remembers a point in the input
 * stream and the `reset` operation
 * causes all the  bytes read since the most
 * recent `mark` operation to be
 * reread before new bytes are  taken from
 * the contained input stream.
 *
 * @author  Arthur van Hoff
 * @since   1.0
 */
open class BufferedInputStream @JvmOverloads constructor(`in`: InputStream, size: Int = DEFAULT_BUFFER_SIZE) :
    FilterInputStream(`in`) {
    // initialized to null when BufferedInputStream is sub-classed

    // initial buffer size (DEFAULT_BUFFER_SIZE or size specified to constructor)
    private val initialSize: Int

    /**
     * The internal buffer array where the data is stored. When necessary,
     * it may be replaced by another array of
     * a different size.
     */
    /*
     * We null this out with a CAS on close(), which is necessary since
     * closes can be asynchronous. We use nullness of buf[] as primary
     * indicator that this stream is closed. (The "in" field is also
     * nulled out on close.)
     */
    @Volatile
    protected var buf: ByteArray

    /**
     * The index one greater than the index of the last valid byte in
     * the buffer.
     * This value is always
     * in the range `0` through `buf.length`;
     * elements `buf[0]` through `buf[count-1]`
     * contain buffered input data obtained
     * from the underlying  input stream.
     */
    protected var count: Int = 0

    /**
     * The current position in the buffer. This is the index of the next
     * byte to be read from the `buf` array.
     *
     *
     * This value is always in the range `0`
     * through `count`. If it is less
     * than `count`, then  `buf[pos]`
     * is the next byte to be supplied as input;
     * if it is equal to `count`, then
     * the  next `read` or `skip`
     * operation will require more bytes to be
     * read from the contained  input stream.
     *
     * @see java.io.BufferedInputStream.buf
     */
    protected var pos: Int = 0

    /**
     * The value of the `pos` field at the time the last
     * `mark` method was called.
     *
     *
     * This value is always
     * in the range `-1` through `pos`.
     * If there is no marked position in  the input
     * stream, this field is `-1`. If
     * there is a marked position in the input
     * stream,  then `buf[markpos]`
     * is the first byte to be supplied as input
     * after a `reset` operation. If
     * `markpos` is not `-1`,
     * then all bytes from positions `buf[markpos]`
     * through  `buf[pos-1]` must remain
     * in the buffer array (though they may be
     * moved to  another place in the buffer array,
     * with suitable adjustments to the values
     * of `count`,  `pos`,
     * and `markpos`); they may not
     * be discarded unless and until the difference
     * between `pos` and `markpos`
     * exceeds `marklimit`.
     *
     * @see java.io.BufferedInputStream.mark
     * @see java.io.BufferedInputStream.pos
     */
    protected var markpos: Int = -1

    /**
     * The maximum read ahead allowed after a call to the
     * `mark` method before subsequent calls to the
     * `reset` method fail.
     * Whenever the difference between `pos`
     * and `markpos` exceeds `marklimit`,
     * then the  mark may be dropped by setting
     * `markpos` to `-1`.
     *
     * @see java.io.BufferedInputStream.mark
     * @see java.io.BufferedInputStream.reset
     */
    protected var marklimit: Int = 0

    private val inIfOpen: InputStream
        /**
         * Check to make sure that underlying input stream has not been
         * nulled out due to close; if not return it;
         */
        get() {
            val input: InputStream? = `in`
            if (input == null) throw IOException("Stream closed")
            return input
        }

    /**
     * Returns the internal buffer, optionally allocating it if empty.
     * @param allocateIfEmpty true to allocate if empty
     * @throws IOException if the stream is closed (buf is null)
     */
    @Throws(IOException::class)
    private fun getBufIfOpen(allocateIfEmpty: Boolean): ByteArray {
        var buffer = buf
        if (allocateIfEmpty && buffer == EMPTY) {
            buffer = ByteArray(initialSize)
            buffer = buf
        }
        if (buffer == null) {
            throw IOException("Stream closed")
        }
        return buffer
    }

    private val bufIfOpen: ByteArray
        /**
         * Returns the internal buffer, allocating it if empty.
         * @throws IOException if the stream is closed (buf is null)
         */
        get() = getBufIfOpen(true)

    /**
     * Throws IOException if the stream is closed (buf is null).
     */
    @Throws(IOException::class)
    private fun ensureOpen() {
        if (buf == null) {
            throw IOException("Stream closed")
        }
    }

    /**
     * Creates a `BufferedInputStream`
     * with the specified buffer size,
     * and saves its  argument, the input stream
     * `in`, for later use.  An internal
     * buffer array of length  `size`
     * is created and stored in `buf`.
     *
     * @param   in     the underlying input stream.
     * @param   size   the buffer size.
     * @throws  IllegalArgumentException if `size <= 0`.
     */
    /**
     * Creates a `BufferedInputStream`
     * and saves its  argument, the input stream
     * `in`, for later use. An internal
     * buffer array is created and  stored in `buf`.
     *
     * @param   in   the underlying input stream.
     */
    init {
        require(size > 0) { "Buffer size <= 0" }
        initialSize = size
        buf = ByteArray(size)
    }

    /**
     * Fills the buffer with more data, taking into account
     * shuffling and other tricks for dealing with marks.
     * Assumes that it is being called by a locked method.
     * This method also assumes that all data has already been read in,
     * hence pos > count.
     */
    @Throws(IOException::class)
    private fun fill() {
        var buffer = this.bufIfOpen
        if (markpos == -1) pos = 0 /* no mark: throw away the buffer */
        else if (pos >= buffer.size) { /* no room left in buffer */
            if (markpos > 0) {  /* can throw away early part of the buffer */
                val sz = pos - markpos
                System.arraycopy(buffer, markpos, buffer, 0, sz)
                pos = sz
                markpos = 0
            } else if (buffer.size >= marklimit) {
                markpos = -1 /* buffer got too big, invalidate mark */
                pos = 0 /* drop buffer contents */
            } else {            /* grow buffer */
                var nsz: Int = ArraysSupport.newLength(
                    pos,
                    1,  /* minimum growth */
                    pos /* preferred growth */
                )
                if (nsz > marklimit) nsz = marklimit
                val nbuf = ByteArray(nsz)
                System.arraycopy(buffer, 0, nbuf, 0, pos)

                buffer = nbuf
            }
        }
        count = pos
        val n: Int = this.inIfOpen.read(buffer, pos, buffer.size - pos)
        if (n > 0) count = n + pos
    }

    /**
     * See
     * the general contract of the `read`
     * method of `InputStream`.
     *
     * @return     the next byte of data, or `-1` if the end of the
     * stream is reached.
     * @throws     IOException  if this input stream has been closed by
     * invoking its [.close] method,
     * or an I/O error occurs.
     * @see java.io.FilterInputStream in
     */
    @Throws(IOException::class)
    override fun read(): Int {
        if (pos >= count) {
            fill()
            if (pos >= count) return -1
        }
        return this.bufIfOpen[pos++].toInt() and 0xff
    }

    /**
     * Read bytes into a portion of an array, reading from the underlying
     * stream at most once if necessary.
     */
    @Throws(IOException::class)
    private fun read1(b: ByteArray, off: Int, len: Int): Int {
        var avail = count - pos
        if (avail <= 0) {
            /* If the requested length is at least as large as the buffer, and
               if there is no mark/reset activity, do not bother to copy the
               bytes into the local buffer.  In this way buffered streams will
               cascade harmlessly. */
            val size = max(getBufIfOpen(false).size, initialSize)
            if (len >= size && markpos == -1) {
                return this.inIfOpen.read(b, off, len)
            }
            fill()
            avail = count - pos
            if (avail <= 0) return -1
        }
        val cnt = if (avail < len) avail else len
        System.arraycopy(this.bufIfOpen, pos, b, off, cnt)
        pos += cnt
        return cnt
    }

    /**
     * Reads bytes from this byte-input stream into the specified byte array,
     * starting at the given offset.
     *
     *
     *  This method implements the general contract of the corresponding
     * [read][InputStream.read] method of
     * the [InputStream] class.  As an additional
     * convenience, it attempts to read as many bytes as possible by repeatedly
     * invoking the `read` method of the underlying stream.  This
     * iterated `read` continues until one of the following
     * conditions becomes true:
     *
     *  *  The specified number of bytes have been read,
     *
     *  *  The `read` method of the underlying stream returns
     * `-1`, indicating end-of-file, or
     *
     *  *  The `available` method of the underlying stream
     * returns zero, indicating that further input requests would block.
     *
     *  If the first `read` on the underlying stream returns
     * `-1` to indicate end-of-file then this method returns
     * `-1`.  Otherwise, this method returns the number of bytes
     * actually read.
     *
     *
     *  Subclasses of this class are encouraged, but not required, to
     * attempt to read as many bytes as possible in the same fashion.
     *
     * @param      b     destination buffer.
     * @param      off   offset at which to start storing bytes.
     * @param      len   maximum number of bytes to read.
     * @return     the number of bytes read, or `-1` if the end of
     * the stream has been reached.
     * @throws     IOException  if this input stream has been closed by
     * invoking its [.close] method,
     * or an I/O error occurs.
     * @throws     IndexOutOfBoundsException {@inheritDoc}
     */
    @Throws(IOException::class)
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        ensureOpen()
        if ((off or len or (off + len) or (b.size - (off + len))) < 0) {
            throw IndexOutOfBoundsException()
        } else if (len == 0) {
            return 0
        }

        var n = 0
        while (true) {
            val nread = read1(b, off + n, len - n)
            if (nread <= 0) return if (n == 0) nread else n
            n += nread
            if (n >= len) return n
            // if not closed but no bytes available, return
            val input: InputStream? = `in`
            if (input != null && input.available() <= 0) return n
        }
    }

    /**
     * See the general contract of the `skip`
     * method of `InputStream`.
     *
     * @throws IOException  if this input stream has been closed by
     * invoking its [.close] method,
     * `in.skip(n)` throws an IOException,
     * or an I/O error occurs.
     */
    @Throws(IOException::class)
    override fun skip(n: Long): Long {
        ensureOpen()
        if (n <= 0) return 0

        // 1) Skip whatever is left in the buffer
        val availInBuf = (count - pos).toLong()
        var skipped = if (availInBuf > 0) {
            val toSkipFromBuf = minOf(availInBuf, n)
            pos += toSkipFromBuf.toInt()
            toSkipFromBuf
        } else {
            0L
        }

        // 2) If there’s still more to skip, delegate to the wrapped stream
        if (skipped < n) {
            skipped += inIfOpen.skip(n - skipped)
        }
        return skipped
    }

    /**
     * Returns an estimate of the number of bytes that can be read (or
     * skipped over) from this input stream without blocking by the next
     * invocation of a method for this input stream. The next invocation might be
     * the same thread or another thread.  A single read or skip of this
     * many bytes will not block, but may read or skip fewer bytes.
     *
     *
     * This method returns the sum of the number of bytes remaining to be read in
     * the buffer (`count - pos`) and the result of calling the
     * [in][java.io.FilterInputStream. in]`.available()`.
     *
     * @return     an estimate of the number of bytes that can be read (or skipped
     * over) from this input stream without blocking.
     * @throws     IOException  if this input stream has been closed by
     * invoking its [.close] method,
     * or an I/O error occurs.
     */
    @Throws(IOException::class)
    override fun available(): Int {
        val n = count - pos
        val avail: Int = this.inIfOpen.available()
        return if (n > (Int.Companion.MAX_VALUE - avail)) Int.Companion.MAX_VALUE else
            n + avail
    }

    /**
     * See the general contract of the `mark`
     * method of `InputStream`.
     *
     * @param   readlimit   the maximum limit of bytes that can be read before
     * the mark position becomes invalid.
     * @see java.io.BufferedInputStream.reset
     */
    override fun mark(readlimit: Int) {
        marklimit = readlimit
        markpos = pos
    }

    /**
     * See the general contract of the `reset`
     * method of `InputStream`.
     *
     *
     * If `markpos` is `-1`
     * (no mark has been set or the mark has been
     * invalidated), an `IOException`
     * is thrown. Otherwise, `pos` is
     * set equal to `markpos`.
     *
     * @throws     IOException  if this stream has not been marked or,
     * if the mark has been invalidated, or the stream
     * has been closed by invoking its [.close]
     * method, or an I/O error occurs.
     * @see java.io.BufferedInputStream.mark
     */
    @Throws(IOException::class)
    override fun reset() {
        ensureOpen()
        if (markpos < 0) throw IOException("Resetting to invalid mark")
        pos = markpos
    }

    /**
     * Tests if this input stream supports the `mark`
     * and `reset` methods. The `markSupported`
     * method of `BufferedInputStream` returns
     * `true`.
     *
     * @return  a `boolean` indicating if this stream type supports
     * the `mark` and `reset` methods.
     * @see InputStream.mark
     * @see InputStream.reset
     */
    override fun markSupported(): Boolean {
        return true
    }

    /**
     * Closes this input stream and releases any system resources
     * associated with the stream.
     * Once the stream has been closed, further read(), available(), reset(),
     * or skip() invocations will throw an IOException.
     * Closing a previously closed stream has no effect.
     *
     * @throws     IOException  if an I/O error occurs.
     */
    override fun close() {
        var buffer: ByteArray
        while ((buf.also { buffer = it }) != null) {
            val input: InputStream? = `in`
            `in` = null
            if (input != null) input.close()
            return
            // Else retry in case a new buf was CASed in fill()
        }
    }

    @Throws(IOException::class)
    override fun transferTo(out: OutputStream): Long {
        if (this::class == BufferedInputStream::class && markpos == -1) {
            val avail = count - pos
            if (avail > 0) {
                if (/*isTrusted(out)*/ false) {
                    out.write(this.bufIfOpen, pos, avail)
                } else {
                    // Prevent poisoning and leaking of buf
                    val buffer: ByteArray = Arrays.copyOfRange(this.bufIfOpen, pos, count)
                    out.write(buffer)
                }
                pos = count
            }
            try {
                return Math.addExact(avail.toLong(), this.inIfOpen.transferTo(out))
            } catch (ignore: ArithmeticException) {
                return Long.Companion.MAX_VALUE
            }
        } else {
            return super.transferTo(out)
        }
    }

    companion object {
        private const val DEFAULT_BUFFER_SIZE = 8192

        private val EMPTY = ByteArray(0)

        /**
         * Returns true if this class satisfies the following conditions:
         *
         *  * does not retain a reference to the `byte[]`
         *  * does not leak a reference to the `byte[]` to non-trusted classes
         *  * does not modify the contents of the `byte[]`
         *  * `write()` method does not read the contents outside of the offset/length bounds
         *
         *
         * @return true if this class is trusted
         */
        // TODO implement if needed
        /*private fun isTrusted(os: OutputStream): Boolean {
            val clazz: KClass<out OutputStream> = os::class
            return clazz == ByteArrayOutputStream::class || clazz == FileOutputStream::class || clazz == PipedOutputStream::class
        }*/
    }
}
