package org.gnit.lucenekmp.jdkport

import kotlinx.io.EOFException
import kotlinx.io.IOException
import kotlin.concurrent.Volatile
import kotlin.math.min


/**
 * This abstract class is the superclass of all classes representing
 * an input stream of bytes.
 *
 *
 *  Applications that need to define a subclass of `InputStream`
 * must always provide a method that returns the next byte of input.
 *
 * @author  Arthur van Hoff
 * @see java.io.BufferedInputStream
 *
 * @see java.io.ByteArrayInputStream
 *
 * @see java.io.DataInputStream
 *
 * @see java.io.FilterInputStream
 *
 * @see java.io.InputStream.read
 * @see java.io.OutputStream
 *
 * @see java.io.PushbackInputStream
 *
 * @since   1.0
 */
abstract class InputStream
/**
 * Constructor for subclasses to call.
 */
    : AutoCloseable {
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
     * @throws     IOException  if an I/O error occurs.
     */
    @Throws(IOException::class)
    abstract fun read(): Int

    /**
     * Reads some number of bytes from the input stream and stores them into
     * the buffer array `b`. The number of bytes actually read is
     * returned as an integer.  This method blocks until input data is
     * available, end of file is detected, or an exception is thrown.
     *
     *
     *  If the length of `b` is zero, then no bytes are read and
     * `0` is returned; otherwise, there is an attempt to read at
     * least one byte. If no byte is available because the stream is at the
     * end of the file, the value `-1` is returned; otherwise, at
     * least one byte is read and stored into `b`.
     *
     *
     *  The first byte read is stored into element `b[0]`, the
     * next one into `b[1]`, and so on. The number of bytes read is,
     * at most, equal to the length of `b`. Let *k* be the
     * number of bytes actually read; these bytes will be stored in elements
     * `b[0]` through `b[`*k*`-1]`,
     * leaving elements `b[`*k*`]` through
     * `b[b.length-1]` unaffected.
     *
     * @implSpec
     * The `read(b)` method for class `InputStream`
     * has the same effect as:
     * {@snippet lang=java :
     * *     read(b, 0, b.length)
     * * }
     *
     * @param      b   the buffer into which the data is read.
     * @return     the total number of bytes read into the buffer, or
     * `-1` if there is no more data because the end of
     * the stream has been reached.
     * @throws     IOException  If the first byte cannot be read for any reason
     * other than the end of the file, if the input stream has been
     * closed, or if some other I/O error occurs.
     * @throws     NullPointerException  if `b` is `null`.
     * @see java.io.InputStream.read
     */
    @Throws(IOException::class)
    open fun read(b: ByteArray): Int {
        return read(b, 0, b.size)
    }

    /**
     * Reads up to `len` bytes of data from the input stream into
     * an array of bytes.  An attempt is made to read as many as
     * `len` bytes, but a smaller number may be read.
     * The number of bytes actually read is returned as an integer.
     *
     *
     *  This method blocks until input data is available, end of file is
     * detected, or an exception is thrown.
     *
     *
     *  If `len` is zero, then no bytes are read and
     * `0` is returned; otherwise, there is an attempt to read at
     * least one byte. If no byte is available because the stream is at end of
     * file, the value `-1` is returned; otherwise, at least one
     * byte is read and stored into `b`.
     *
     *
     *  The first byte read is stored into element `b[off]`, the
     * next one into `b[off+1]`, and so on. The number of bytes read
     * is, at most, equal to `len`. Let *k* be the number of
     * bytes actually read; these bytes will be stored in elements
     * `b[off]` through `b[off+`*k*`-1]`,
     * leaving elements `b[off+`*k*`]` through
     * `b[off+len-1]` unaffected.
     *
     *
     *  In every case, elements `b[0]` through
     * `b[off-1]` and elements `b[off+len]` through
     * `b[b.length-1]` are unaffected.
     *
     * @implSpec
     * The `read(b, off, len)` method
     * for class `InputStream` simply calls the method
     * `read()` repeatedly. If the first such call results in an
     * `IOException`, that exception is returned from the call to
     * the `read(b,` `off,` `len)` method.  If
     * any subsequent call to `read()` results in a
     * `IOException`, the exception is caught and treated as if it
     * were end of file; the bytes read up to that point are stored into
     * `b` and the number of bytes read before the exception
     * occurred is returned. The default implementation of this method blocks
     * until the requested amount of input data `len` has been read,
     * end of file is detected, or an exception is thrown. Subclasses are
     * encouraged to provide a more efficient implementation of this method.
     *
     * @param      b     the buffer into which the data is read.
     * @param      off   the start offset in array `b`
     * at which the data is written.
     * @param      len   the maximum number of bytes to read.
     * @return     the total number of bytes read into the buffer, or
     * `-1` if there is no more data because the end of
     * the stream has been reached.
     * @throws     IOException If the first byte cannot be read for any reason
     * other than end of file, or if the input stream has been closed,
     * or if some other I/O error occurs.
     * @throws     NullPointerException If `b` is `null`.
     * @throws     IndexOutOfBoundsException If `off` is negative,
     * `len` is negative, or `len` is greater than
     * `b.length - off`
     * @see java.io.InputStream.read
     */
    @Throws(IOException::class)
    open fun read(b: ByteArray, off: Int, len: Int): Int {
        Objects.checkFromIndexSize(off, len, b.size)
        if (len == 0) {
            return 0
        }

        var c = read()
        if (c == -1) {
            return -1
        }
        b[off] = c.toByte()

        var i = 1
        try {
            while (i < len) {
                c = read()
                if (c == -1) {
                    break
                }
                b[off + i] = c.toByte()
                i++
            }
        } catch (ee: IOException) {
        }
        return i
    }

    /**
     * Reads all remaining bytes from the input stream. This method blocks until
     * all remaining bytes have been read and end of stream is detected, or an
     * exception is thrown. This method does not close the input stream.
     *
     *
     *  When this stream reaches end of stream, further invocations of this
     * method will return an empty byte array.
     *
     *
     *  Note that this method is intended for simple cases where it is
     * convenient to read all bytes into a byte array. It is not intended for
     * reading input streams with large amounts of data.
     *
     *
     *  The behavior for the case where the input stream is *asynchronously
     * closed*, or the thread interrupted during the read, is highly input
     * stream specific, and therefore not specified.
     *
     *
     *  If an I/O error occurs reading from the input stream, then it may do
     * so after some, but not all, bytes have been read. Consequently the input
     * stream may not be at end of stream and may be in an inconsistent state.
     * It is strongly recommended that the stream be promptly closed if an I/O
     * error occurs.
     *
     * @implSpec
     * This method invokes [.readNBytes] with a length of
     * [Integer.MAX_VALUE].
     *
     * @return a byte array containing the bytes read from this input stream
     * @throws IOException if an I/O error occurs
     * @throws OutOfMemoryError if an array of the required size cannot be
     * allocated.
     *
     * @since 9
     */
    @Throws(IOException::class)
    open fun readAllBytes(): ByteArray? {
        return readNBytes(Int.Companion.MAX_VALUE)
    }

    /**
     * Reads up to a specified number of bytes from the input stream. This
     * method blocks until the requested number of bytes has been read, end
     * of stream is detected, or an exception is thrown. This method does not
     * close the input stream.
     *
     *
     *  The length of the returned array equals the number of bytes read
     * from the stream. If `len` is zero, then no bytes are read and
     * an empty byte array is returned. Otherwise, up to `len` bytes
     * are read from the stream. Fewer than `len` bytes may be read if
     * end of stream is encountered.
     *
     *
     *  When this stream reaches end of stream, further invocations of this
     * method will return an empty byte array.
     *
     *
     *  Note that this method is intended for simple cases where it is
     * convenient to read the specified number of bytes into a byte array. The
     * total amount of memory allocated by this method is proportional to the
     * number of bytes read from the stream which is bounded by `len`.
     * Therefore, the method may be safely called with very large values of
     * `len` provided sufficient memory is available.
     *
     *
     *  The behavior for the case where the input stream is *asynchronously
     * closed*, or the thread interrupted during the read, is highly input
     * stream specific, and therefore not specified.
     *
     *
     *  If an I/O error occurs reading from the input stream, then it may do
     * so after some, but not all, bytes have been read. Consequently the input
     * stream may not be at end of stream and may be in an inconsistent state.
     * It is strongly recommended that the stream be promptly closed if an I/O
     * error occurs.
     *
     * @implNote
     * The number of bytes allocated to read data from this stream and return
     * the result is bounded by `2*(long)len`, inclusive.
     *
     * @param len the maximum number of bytes to read
     * @return a byte array containing the bytes read from this input stream
     * @throws IllegalArgumentException if `length` is negative
     * @throws IOException if an I/O error occurs
     * @throws OutOfMemoryError if an array of the required size cannot be
     * allocated.
     *
     * @since 11
     */
    @Throws(IOException::class)
    open fun readNBytes(len: Int): ByteArray? {
        require(len >= 0) { "len < 0" }

        var bufs: MutableList<ByteArray>? = null
        var result: ByteArray? = null
        var total = 0
        var remaining = len
        var n: Int
        do {
            var buf = ByteArray(min(remaining, DEFAULT_BUFFER_SIZE))
            var nread = 0

            // read to EOF which may read more or less than buffer size
            while ((read(
                    buf, nread,
                    min(buf.size - nread, remaining)
                ).also { n = it }) > 0
            ) {
                nread += n
                remaining -= n
            }

            if (nread > 0) {
                if (MAX_BUFFER_SIZE - total < nread) {
                    throw /*java.lang.OutOfMemory*/Error("Required array size too large")
                }
                if (nread < buf.size) {
                    buf = Arrays.copyOfRange(buf, 0, nread)
                }
                total += nread
                if (result == null) {
                    result = buf
                } else {
                    if (bufs == null) {
                        bufs = ArrayList()
                        bufs.add(result)
                    }
                    bufs.add(buf)
                }
            }
            // if the last call to read returned -1 or the number of bytes
            // requested have been read then break
        } while (n >= 0 && remaining > 0)

        if (bufs == null) {
            if (result == null) {
                return ByteArray(0)
            }
            return if (result.size == total) result else result.copyOf(total)
        }

        result = ByteArray(total)
        var offset = 0
        remaining = total
        for (b in bufs) {
            val count = min(b.size, remaining)
            System.arraycopy(b, 0, result, offset, count)
            offset += count
            remaining -= count
        }

        return result
    }

    /**
     * Reads the requested number of bytes from the input stream into the given
     * byte array. This method blocks until `len` bytes of input data have
     * been read, end of stream is detected, or an exception is thrown. The
     * number of bytes actually read, possibly zero, is returned. This method
     * does not close the input stream.
     *
     *
     *  In the case where end of stream is reached before `len` bytes
     * have been read, then the actual number of bytes read will be returned.
     * When this stream reaches end of stream, further invocations of this
     * method will return zero.
     *
     *
     *  If `len` is zero, then no bytes are read and `0` is
     * returned; otherwise, there is an attempt to read up to `len` bytes.
     *
     *
     *  The first byte read is stored into element `b[off]`, the next
     * one in to `b[off+1]`, and so on. The number of bytes read is, at
     * most, equal to `len`. Let *k* be the number of bytes actually
     * read; these bytes will be stored in elements `b[off]` through
     * `b[off+`*k*`-1]`, leaving elements `b[off+`*k*
     * `]` through `b[off+len-1]` unaffected.
     *
     *
     *  The behavior for the case where the input stream is *asynchronously
     * closed*, or the thread interrupted during the read, is highly input
     * stream specific, and therefore not specified.
     *
     *
     *  If an I/O error occurs reading from the input stream, then it may do
     * so after some, but not all, bytes of `b` have been updated with
     * data from the input stream. Consequently the input stream and `b`
     * may be in an inconsistent state. It is strongly recommended that the
     * stream be promptly closed if an I/O error occurs.
     *
     * @param  b the byte array into which the data is read
     * @param  off the start offset in `b` at which the data is written
     * @param  len the maximum number of bytes to read
     * @return the actual number of bytes read into the buffer
     * @throws IOException if an I/O error occurs
     * @throws NullPointerException if `b` is `null`
     * @throws IndexOutOfBoundsException If `off` is negative, `len`
     * is negative, or `len` is greater than `b.length - off`
     *
     * @since 9
     */
    @Throws(IOException::class)
    open fun readNBytes(b: ByteArray, off: Int, len: Int): Int {
        Objects.checkFromIndexSize(off, len, b.size)

        var n = 0
        while (n < len) {
            val count = read(b, off + n, len - n)
            if (count < 0) break
            n += count
        }
        return n
    }

    /**
     * Skips over and discards `n` bytes of data from this input
     * stream. The `skip` method may, for a variety of reasons, end
     * up skipping over some smaller number of bytes, possibly `0`.
     * This may result from any of a number of conditions; reaching end of file
     * before `n` bytes have been skipped is only one possibility.
     * The actual number of bytes skipped is returned. If `n` is
     * negative, the `skip` method for class `InputStream` always
     * returns 0, and no bytes are skipped. Subclasses may handle the negative
     * value differently.
     *
     * @implSpec
     * The `skip` method implementation of this class creates a
     * byte array and then repeatedly reads into it until `n` bytes
     * have been read or the end of the stream has been reached. Subclasses are
     * encouraged to provide a more efficient implementation of this method.
     * For instance, the implementation may depend on the ability to seek.
     *
     * @param      n   the number of bytes to be skipped.
     * @return     the actual number of bytes skipped which might be zero.
     * @throws     IOException  if an I/O error occurs.
     * @see java.io.InputStream.skipNBytes
     */
    @Throws(IOException::class)
    open fun skip(n: Long): Long {
        var remaining = n
        var nr: Int

        if (n <= 0) {
            return 0
        }

        val size = min(MAX_SKIP_BUFFER_SIZE.toLong(), remaining).toInt()
        val skipBuffer = ByteArray(size)
        while (remaining > 0) {
            nr = read(skipBuffer, 0, min(size.toLong(), remaining).toInt())
            if (nr < 0) {
                break
            }
            remaining -= nr.toLong()
        }

        return n - remaining
    }

    /**
     * Skips over and discards exactly `n` bytes of data from this input
     * stream.  If `n` is zero, then no bytes are skipped.
     * If `n` is negative, then no bytes are skipped.
     * Subclasses may handle the negative value differently.
     *
     *
     *  This method blocks until the requested number of bytes has been
     * skipped, end of file is reached, or an exception is thrown.
     *
     *
     *  If end of stream is reached before the stream is at the desired
     * position, then an `EOFException` is thrown.
     *
     *
     *  If an I/O error occurs, then the input stream may be
     * in an inconsistent state. It is strongly recommended that the
     * stream be promptly closed if an I/O error occurs.
     *
     * @implNote
     * Subclasses are encouraged to provide a more efficient implementation
     * of this method.
     *
     * @implSpec
     * If `n` is zero or negative, then no bytes are skipped.
     * If `n` is positive, the default implementation of this method
     * invokes [skip()][.skip] repeatedly with its parameter equal
     * to the remaining number of bytes to skip until the requested number
     * of bytes has been skipped or an error condition occurs.  If at any
     * point the return value of `skip()` is negative or greater than the
     * remaining number of bytes to be skipped, then an `IOException` is
     * thrown.  If `skip()` ever returns zero, then [.read] is
     * invoked to read a single byte, and if it returns `-1`, then an
     * `EOFException` is thrown.  Any exception thrown by `skip()`
     * or `read()` will be propagated.
     *
     * @param      n   the number of bytes to be skipped.
     * @throws     EOFException if end of stream is encountered before the
     * stream can be positioned `n` bytes beyond its position
     * when this method was invoked.
     * @throws     IOException  if the stream cannot be positioned properly or
     * if an I/O error occurs.
     * @see java.io.InputStream.skip
     * @since 12
     */
    @Throws(IOException::class)
    open fun skipNBytes(n: Long) {
        var n = n
        while (n > 0) {
            val ns = skip(n)
            if (ns > 0 && ns <= n) {
                // adjust number to skip
                n -= ns
            } else if (ns == 0L) { // no bytes skipped
                // read one byte to check for EOS
                if (read() == -1) {
                    throw EOFException()
                }
                // one byte read so decrement number to skip
                n--
            } else { // skipped negative or too many bytes
                throw IOException("Unable to skip exactly")
            }
        }
    }

    /**
     * Returns an estimate of the number of bytes that can be read (or skipped
     * over) from this input stream without blocking, which may be 0, or 0 when
     * end of stream is detected.  The read might be on the same thread or
     * another thread.  A single read or skip of this many bytes will not block,
     * but may read or skip fewer bytes.
     *
     *
     *  Note that while some implementations of `InputStream` will
     * return the total number of bytes in the stream, many will not.  It is
     * never correct to use the return value of this method to allocate
     * a buffer intended to hold all data in this stream.
     *
     *
     *  A subclass's implementation of this method may choose to throw an
     * [IOException] if this input stream has been closed by invoking the
     * [.close] method.
     *
     * @implSpec
     * The `available` method of `InputStream` always returns
     * `0`.
     *
     * @apiNote
     * This method should be overridden by subclasses.
     *
     * @return     an estimate of the number of bytes that can be read (or
     * skipped over) from this input stream without blocking or
     * `0` when it reaches the end of the input stream.
     * @throws     IOException if an I/O error occurs.
     */
    @Throws(IOException::class)
    open fun available(): Int {
        return 0
    }

    /**
     * Closes this input stream and releases any system resources associated
     * with the stream.
     *
     * @implSpec
     * The `close` method of `InputStream` does
     * nothing.
     *
     * @throws     IOException  if an I/O error occurs.
     */
    override fun close() {
    }

    /**
     * Marks the current position in this input stream. A subsequent call to
     * the `reset` method repositions this stream at the last marked
     * position so that subsequent reads re-read the same bytes.
     *
     *
     *  The `readlimit` arguments tells this input stream to
     * allow that many bytes to be read before the mark position gets
     * invalidated.
     *
     *
     *  The general contract of `mark` is that, if the method
     * `markSupported` returns `true`, the stream somehow
     * remembers all the bytes read after the call to `mark` and
     * stands ready to supply those same bytes again if and whenever the method
     * `reset` is called.  However, the stream is not required to
     * remember any data at all if more than `readlimit` bytes are
     * read from the stream before `reset` is called.
     *
     *
     *  Marking a closed stream should not have any effect on the stream.
     *
     * @implSpec
     * The `mark` method of `InputStream` does nothing.
     *
     * @param   readlimit   the maximum limit of bytes that can be read before
     * the mark position becomes invalid.
     * @see java.io.InputStream.reset
     */
    open fun mark(readlimit: Int) {}

    /**
     * Repositions this stream to the position at the time the
     * `mark` method was last called on this input stream.
     *
     *
     *  The general contract of `reset` is:
     *
     *
     *  *  If the method `markSupported` returns
     * `true`, then:
     *
     *  *  If the method `mark` has not been called since
     * the stream was created, or the number of bytes read from the stream
     * since `mark` was last called is larger than the argument
     * to `mark` at that last call, then an
     * `IOException` might be thrown.
     *
     *  *  If such an `IOException` is not thrown, then the
     * stream is reset to a state such that all the bytes read since the
     * most recent call to `mark` (or since the start of the
     * file, if `mark` has not been called) will be resupplied
     * to subsequent callers of the `read` method, followed by
     * any bytes that otherwise would have been the next input data as of
     * the time of the call to `reset`.
     *
     *  *  If the method `markSupported` returns
     * `false`, then:
     *
     *  *  The call to `reset` may throw an
     * `IOException`.
     *
     *  *  If an `IOException` is not thrown, then the stream
     * is reset to a fixed state that depends on the particular type of the
     * input stream and how it was created. The bytes that will be supplied
     * to subsequent callers of the `read` method depend on the
     * particular type of the input stream.
     *
     * @implSpec
     * The method `reset` for class `InputStream`
     * does nothing except throw an `IOException`.
     *
     * @throws  IOException  if this stream has not been marked or if the
     * mark has been invalidated.
     * @see java.io.InputStream.mark
     * @see java.io.IOException
     */
    @Throws(IOException::class)
    open fun reset() {
        throw IOException("mark/reset not supported")
    }

    /**
     * Tests if this input stream supports the `mark` and
     * `reset` methods. Whether or not `mark` and
     * `reset` are supported is an invariant property of a
     * particular input stream instance.
     *
     * @implSpec
     * The `markSupported` method
     * of `InputStream` returns `false`.
     *
     * @return  `true` if this stream instance supports the mark
     * and reset methods; `false` otherwise.
     * @see java.io.InputStream.mark
     * @see java.io.InputStream.reset
     */
    open fun markSupported(): Boolean {
        return false
    }

    /**
     * Reads all bytes from this input stream and writes the bytes to the
     * given output stream in the order that they are read. On return, this
     * input stream will be at end of stream. This method does not close either
     * stream.
     *
     *
     * This method may block indefinitely reading from the input stream, or
     * writing to the output stream. The behavior for the case where the input
     * and/or output stream is *asynchronously closed*, or the thread
     * interrupted during the transfer, is highly input and output stream
     * specific, and therefore not specified.
     *
     *
     * If the total number of bytes transferred is greater than [ ][Long.MAX_VALUE], then `Long.MAX_VALUE` will be returned.
     *
     *
     * If an I/O error occurs reading from the input stream or writing to the
     * output stream, then it may do so after some bytes have been read or
     * written. Consequently the input stream may not be at end of stream and
     * one, or both, streams may be in an inconsistent state. It is strongly
     * recommended that both streams be promptly closed if an I/O error occurs.
     *
     * @param  out the output stream, non-null
     * @return the number of bytes transferred
     * @throws IOException if an I/O error occurs when reading or writing
     * @throws NullPointerException if `out` is `null`
     *
     * @since 9
     */
    @Throws(IOException::class)
    open fun transferTo(out: OutputStream): Long {
        var transferred: Long = 0
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var read: Int
        while ((this.read(buffer, 0, DEFAULT_BUFFER_SIZE).also { read = it }) >= 0) {
            out.write(buffer, 0, read)
            if (transferred < Long.Companion.MAX_VALUE) {
                try {
                    transferred = Math.addExact(transferred, read.toLong())
                } catch (ignore: ArithmeticException) {
                    transferred = Long.Companion.MAX_VALUE
                }
            }
        }
        return transferred
    }

    companion object {
        // MAX_SKIP_BUFFER_SIZE is used to determine the maximum buffer size to
        // use when skipping.
        private const val MAX_SKIP_BUFFER_SIZE = 2048

        private const val DEFAULT_BUFFER_SIZE = 16384

        /**
         * Returns a new `InputStream` that reads no bytes. The returned
         * stream is initially open.  The stream is closed by calling the
         * `close()` method.  Subsequent calls to `close()` have no
         * effect.
         *
         *
         *  While the stream is open, the `available()`, `read()`,
         * `read(byte[])`, `read(byte[], int, int)`,
         * `readAllBytes()`, `readNBytes(byte[], int, int)`,
         * `readNBytes(int)`, `skip(long)`, `skipNBytes(long)`,
         * and `transferTo()` methods all behave as if end of stream has been
         * reached.  After the stream has been closed, these methods all throw
         * `IOException`.
         *
         *
         *  The `markSupported()` method returns `false`.  The
         * `mark()` method does nothing, and the `reset()` method
         * throws `IOException`.
         *
         * @return an `InputStream` which contains no bytes
         *
         * @since 11
         */
        fun nullInputStream(): InputStream {
            return object : InputStream() {
                @Volatile
                private var closed = false

                @Throws(IOException::class)
                fun ensureOpen() {
                    if (closed) {
                        throw IOException("Stream closed")
                    }
                }

                @Throws(IOException::class)
                override fun available(): Int {
                    ensureOpen()
                    return 0
                }

                @Throws(IOException::class)
                override fun read(): Int {
                    ensureOpen()
                    return -1
                }

                @Throws(IOException::class)
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    Objects.checkFromIndexSize(off, len, b.size)
                    if (len == 0) {
                        return 0
                    }
                    ensureOpen()
                    return -1
                }

                @Throws(IOException::class)
                override fun readAllBytes(): ByteArray? {
                    ensureOpen()
                    return ByteArray(0)
                }

                @Throws(IOException::class)
                override fun readNBytes(b: ByteArray, off: Int, len: Int): Int {
                    Objects.checkFromIndexSize(off, len, b.size)
                    ensureOpen()
                    return 0
                }

                @Throws(IOException::class)
                override fun readNBytes(len: Int): ByteArray? {
                    require(len >= 0) { "len < 0" }
                    ensureOpen()
                    return ByteArray(0)
                }

                @Throws(IOException::class)
                override fun skip(n: Long): Long {
                    ensureOpen()
                    return 0L
                }

                @Throws(IOException::class)
                override fun skipNBytes(n: Long) {
                    ensureOpen()
                    if (n > 0) {
                        throw EOFException()
                    }
                }

                @Throws(IOException::class)
                override fun transferTo(out: OutputStream): Long {
                    ensureOpen()
                    return 0L
                }

                @Throws(IOException::class)
                override fun close() {
                    closed = true
                }
            }
        }

        /**
         * The maximum size of array to allocate.
         * Some VMs reserve some header words in an array.
         * Attempts to allocate larger arrays may result in
         * OutOfMemoryError: Requested array size exceeds VM limit
         */
        private const val MAX_BUFFER_SIZE = Int.Companion.MAX_VALUE - 8
    }
}
