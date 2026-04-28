package org.gnit.lucenekmp.jdkport

import okio.Buffer
import okio.BufferedSource
import okio.EOFException
import okio.IOException

/**
 * A InputStream implementation which use okio.Source
 */
class OkioSourceInputStream(val source: BufferedSource) : InputStream() {

    /**
     * Reads the next byte of data from the input stream. The value byte is
     * returned as an `int` in the range `0` to
     * `255`. If no byte is available because the end of the stream
     * has been reached, the value `-1` is returned. This method
     * blocks until input data is available, the end of the stream is detected,
     * or an exception is thrown.
     *
     * @return     the next byte of data, or `-1` if the end of the
     * stream is reached.
     * @throws     kotlinx.io.IOException  if an I/O error occurs.
     */
    override fun read(): Int {
        return try {
            // readByte() blocks until a byte is available or the source is exhausted.
            // We must convert the signed Byte to an unsigned Int in the range 0-255.
            source.readByte().toInt() and 0xFF
        } catch (e: EOFException) {
            -1
        }
    }

    /**
     * Reads up to `len` bytes of data from the input stream into
     * an array of bytes. An attempt is made to read as many as
     * `len` bytes, but a smaller number may be read.
     * The number of bytes actually read is returned as an integer.
     *
     * @param      b     the buffer into which the data is read.
     * @param      off   the start offset in array `b`
     *                   at which the data is written.
     * @param      len   the maximum number of bytes to read.
     * @return     the total number of bytes read into the buffer, or
     *             `-1` if there is no more data because the end of
     *             the stream has been reached.
     * @throws     IOException  If the first byte cannot be read for any reason
     *             other than end of file, or if the input stream has been closed,
     *             or if some other I/O error occurs.
     */
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0

        val bytesRead = try {
            source.read(b, off, len)
        } catch (e: EOFException) {
            -1
        }

        // If source.read returns -1, it could be a premature EOF from BufferedSource.
        // Fall back to a single-byte read, which correctly blocks until a true EOF.

        if (bytesRead != -1) {
            return bytesRead
        }

        // bytesRead is -1. This could be a premature EOF from BufferedSource if its
        // internal buffer was empty and the underlying source returned 0 bytes.
        // To distinguish a real EOF from a temporary lack of data, we check if the
        // source is truly exhausted. Note that `exhausted()` may block and try to
        // read from the underlying source, which is the desired behavior here.
        return if (source.exhausted()) {
            -1 // Real EOF
        } else {
            0 // Not a real EOF. No data available right now.
        }
    }

    /**
     * Report how many bytes are still available without blocking.
     * If the underlying Source is a Buffer, we can peek at its .size.
     * Otherwise we return 0.
     */
    override fun available(): Int {
        return (source as? Buffer)?.size?.toInt() ?: 0
    }

}
