package org.gnit.lucenekmp.jdkport

import kotlinx.io.IOException
import kotlin.math.max
import kotlin.math.min


class StringReader(s: String) : Reader() {
    private val length = s.length
    private var str: String?
    private var next = 0
    private var mark = 0

    /**
     * Creates a new string reader.
     *
     * @param s  String providing the character stream.
     */
    init {
        this.str = s
    }

    /** Check to make sure that the stream has not been closed  */
    @Throws(IOException::class)
    private fun ensureOpen() {
        if (str == null) throw IOException("Stream closed")
    }

    /**
     * Reads a single character.
     *
     * @return     The character read, or -1 if the end of the stream has been
     * reached
     *
     * @throws     IOException  If an I/O error occurs
     */
    override fun read(): Int {
        ensureOpen()
        if (next >= length) return -1
        return str!![next++].code
    }

    /**
     * Reads characters into a portion of an array.
     *
     *
     *  If `len` is zero, then no characters are read and `0` is
     * returned; otherwise, there is an attempt to read at least one character.
     * If no character is available because the stream is at its end, the value
     * `-1` is returned; otherwise, at least one character is read and
     * stored into `cbuf`.
     *
     * @param      cbuf  {@inheritDoc}
     * @param      off   {@inheritDoc}
     * @param      len   {@inheritDoc}
     *
     * @return     {@inheritDoc}
     *
     * @throws     IndexOutOfBoundsException  {@inheritDoc}
     * @throws     IOException  {@inheritDoc}
     */
    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        ensureOpen()
        Objects.checkFromIndexSize(off, len, cbuf.size)
        if (len == 0) {
            return 0
        }
        if (next >= length) return -1
        val n = min(length - next, len)
        str!!.toCharArray(cbuf, off, next, next + n)
        next += n
        return n
    }

    /**
     * Skips characters. If the stream is already at its end before this method
     * is invoked, then no characters are skipped and zero is returned.
     *
     *
     * The `n` parameter may be negative, even though the
     * `skip` method of the [Reader] superclass throws
     * an exception in this case. Negative values of `n` cause the
     * stream to skip backwards. Negative return values indicate a skip
     * backwards. It is not possible to skip backwards past the beginning of
     * the string.
     *
     *
     * If the entire string has been read or skipped, then this method has
     * no effect and always returns `0`.
     *
     * @param n {@inheritDoc}
     *
     * @return {@inheritDoc}
     *
     * @throws IOException {@inheritDoc}
     */
    fun skip(n: Long): Long {
        ensureOpen()
        if (next >= length) return 0
        // Bound skip by beginning and end of the source
        var r = min((length - next).toUInt(), n.toUInt()).toLong()
        r = max((-next).toUInt(), r.toUInt()).toLong()
        next += r.toInt()
        return r
    }

    /**
     * Tells whether this stream is ready to be read.
     *
     * @return True if the next read() is guaranteed not to block for input
     *
     * @throws     IOException  If the stream is closed
     */
    @Throws(IOException::class)
    fun ready(): Boolean {
        ensureOpen()
        return true
    }

    /**
     * Tells whether this stream supports the mark() operation, which it does.
     */
    fun markSupported(): Boolean {
        return true
    }

    /**
     * Marks the present position in the stream.  Subsequent calls to reset()
     * will reposition the stream to this point.
     *
     * @param  readAheadLimit  Limit on the number of characters that may be
     * read while still preserving the mark.  Because
     * the stream's input comes from a string, there
     * is no actual limit, so this argument must not
     * be negative, but is otherwise ignored.
     *
     * @throws     IllegalArgumentException  If `readAheadLimit < 0`
     * @throws     IOException  If an I/O error occurs
     */
    @Throws(IOException::class)
    fun mark(readAheadLimit: Int) {
        require(readAheadLimit >= 0) { "Read-ahead limit < 0" }
        ensureOpen()
        mark = next
    }

    /**
     * Resets the stream to the most recent mark, or to the beginning of the
     * string if it has never been marked.
     *
     * @throws     IOException  If an I/O error occurs
     */
    fun reset() {
        ensureOpen()
        next = mark
    }

    /**
     * Closes the stream and releases any system resources associated with
     * it. Once the stream has been closed, further read(),
     * ready(), mark(), or reset() invocations will throw an IOException.
     * Closing a previously closed stream has no effect. This method will block
     * while there is another thread blocking on the reader.
     */
    override fun close() {
        str = null
    }
}
