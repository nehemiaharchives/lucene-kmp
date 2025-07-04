package org.gnit.lucenekmp.jdkport

import okio.IOException
import kotlin.concurrent.Volatile


/**
 * Abstract class for writing to character streams.  The only methods that a
 * subclass must implement are write(char[], int, int), flush(), and close().
 * Most subclasses, however, will override some of the methods defined here in
 * order to provide higher efficiency, additional functionality, or both.
 *
 * @see BufferedWriter
 *
 * @see CharArrayWriter
 *
 * @see FilterWriter
 *
 * @see OutputStreamWriter
 *
 * @see FileWriter
 *
 * @see PipedWriter
 *
 * @see PrintWriter
 *
 * @see StringWriter
 *
 * @see Reader
 *
 *
 * @author      Mark Reinhold
 * @since       1.1
 */
@Ported(from = "java.io.Writer")
abstract class Writer : Appendable, AutoCloseable, Flushable {
    /**
     * Temporary buffer used to hold writes of strings and single characters
     */
    private var writeBuffer: CharArray? = null

    /**
     * Writes a single character.  The character to be written is contained in
     * the 16 low-order bits of the given integer value; the 16 high-order bits
     * are ignored.
     *
     *
     *  Subclasses that intend to support efficient single-character output
     * should override this method.
     *
     * @param  c
     * int specifying a character to be written
     *
     * @throws  IOException
     * If an I/O error occurs
     */
    @Throws(IOException::class)
    open fun write(c: Int) {
        if (writeBuffer == null) {
            writeBuffer = CharArray(WRITE_BUFFER_SIZE)
        }
        writeBuffer!![0] = c.toChar()
        write(writeBuffer!!, 0, 1)
    }

    /**
     * Writes an array of characters.
     *
     * @param  cbuf
     * Array of characters to be written
     *
     * @throws  IOException
     * If an I/O error occurs
     */
    @Throws(IOException::class)
    open fun write(cbuf: CharArray) {
        write(cbuf, 0, cbuf.size)
    }

    /**
     * Writes a portion of an array of characters.
     *
     * @param  cbuf
     * Array of characters
     *
     * @param  off
     * Offset from which to start writing characters
     *
     * @param  len
     * Number of characters to write
     *
     * @throws  IndexOutOfBoundsException
     * Implementations should throw this exception
     * if `off` is negative, or `len` is negative,
     * or `off + len` is negative or greater than the length
     * of the given array
     *
     * @throws  IOException
     * If an I/O error occurs
     */
    @Throws(IOException::class)
    abstract fun write(cbuf: CharArray, off: Int, len: Int)

    /**
     * Writes a string.
     *
     * @param  str
     * String to be written
     *
     * @throws  IOException
     * If an I/O error occurs
     */
    @Throws(IOException::class)
    open fun write(str: String) {
        write(str, 0, str.length)
    }

    /**
     * Writes a portion of a string.
     *
     * @implSpec
     * The implementation in this class throws an
     * `IndexOutOfBoundsException` for the indicated conditions;
     * overriding methods may choose to do otherwise.
     *
     * @param  str
     * A String
     *
     * @param  off
     * Offset from which to start writing characters
     *
     * @param  len
     * Number of characters to write
     *
     * @throws  IndexOutOfBoundsException
     * Implementations should throw this exception
     * if `off` is negative, or `len` is negative,
     * or `off + len` is negative or greater than the length
     * of the given string
     *
     * @throws  IOException
     * If an I/O error occurs
     */
    @Throws(IOException::class)
    open fun write(str: String, off: Int, len: Int) {
        val cbuf: CharArray?
        if (len <= WRITE_BUFFER_SIZE) {
            if (writeBuffer == null) {
                writeBuffer = CharArray(WRITE_BUFFER_SIZE)
            }
            cbuf = writeBuffer
        } else {    // Don't permanently allocate very large buffers.
            cbuf = CharArray(len)
        }
        str.toCharArray(cbuf!!, 0, off, (off + len))
        write(cbuf, 0, len)
    }

    /**
     * Appends the specified character sequence to this writer.
     *
     *
     *  An invocation of this method of the form `out.append(csq)`
     * when `csq` is not `null`, behaves in exactly the same way
     * as the invocation
     *
     * {@snippet lang=java :
     * *     out.write(csq.toString())
     * * }
     *
     *
     *  Depending on the specification of `toString` for the
     * character sequence `csq`, the entire sequence may not be
     * appended. For instance, invoking the `toString` method of a
     * character buffer will return a subsequence whose content depends upon
     * the buffer's position and limit.
     *
     * @param  csq
     * The character sequence to append.  If `csq` is
     * `null`, then the four characters `"null"` are
     * appended to this writer.
     *
     * @return  This writer
     *
     * @throws  IOException
     * If an I/O error occurs
     *
     * @since  1.5
     */
    override fun append(csq: CharSequence?): Writer {
        write(csq.toString())
        return this
    }

    /**
     * Appends a subsequence of the specified character sequence to this writer.
     *
     *
     *  An invocation of this method of the form
     * `out.append(csq, start, end)` when `csq`
     * is not `null` behaves in exactly the
     * same way as the invocation
     *
     * {@snippet lang=java :
     * *     out.write(csq.subSequence(start, end).toString())
     * * }
     *
     * @param  csq
     * The character sequence from which a subsequence will be
     * appended.  If `csq` is `null`, then characters
     * will be appended as if `csq` contained the four
     * characters `"null"`.
     *
     * @param  start
     * The index of the first character in the subsequence
     *
     * @param  end
     * The index of the character following the last character in the
     * subsequence
     *
     * @return  This writer
     *
     * @throws  IndexOutOfBoundsException
     * If `start` or `end` are negative, `start`
     * is greater than `end`, or `end` is greater than
     * `csq.length()`
     *
     * @throws  IOException
     * If an I/O error occurs
     *
     * @since  1.5
     */
    override fun append(csq: CharSequence?, start: Int, end: Int): Writer {
        var csq = csq
        if (csq == null) csq = "null"
        return append(csq.subSequence(start, end))
    }

    /**
     * Appends the specified character to this writer.
     *
     *
     *  An invocation of this method of the form `out.append(c)`
     * behaves in exactly the same way as the invocation
     *
     * {@snippet lang=java :
     * *     out.write(c)
     * * }
     *
     * @param  c
     * The 16-bit character to append
     *
     * @return  This writer
     *
     * @throws  IOException
     * If an I/O error occurs
     *
     * @since 1.5
     */
    override fun append(c: Char): Writer {
        write(c.code)
        return this
    }

    /**
     * Flushes the stream.  If the stream has saved any characters from the
     * various write() methods in a buffer, write them immediately to their
     * intended destination.  Then, if that destination is another character or
     * byte stream, flush it.  Thus one flush() invocation will flush all the
     * buffers in a chain of Writers and OutputStreams.
     *
     *
     *  If the intended destination of this stream is an abstraction provided
     * by the underlying operating system, for example a file, then flushing the
     * stream guarantees only that bytes previously written to the stream are
     * passed to the operating system for writing; it does not guarantee that
     * they are actually written to a physical device such as a disk drive.
     *
     * @throws  IOException
     * If an I/O error occurs
     */
    @Throws(IOException::class)
    abstract override fun flush()

    /**
     * Closes the stream, flushing it first. Once the stream has been closed,
     * further write() or flush() invocations will cause an IOException to be
     * thrown. Closing a previously closed stream has no effect.
     *
     * @throws  IOException
     * If an I/O error occurs
     */
    abstract override fun close()

    companion object {
        /**
         * Size of writeBuffer, must be >= 1
         */
        private const val WRITE_BUFFER_SIZE = 1024

        /**
         * Returns a new `Writer` which discards all characters.  The
         * returned stream is initially open.  The stream is closed by calling
         * the `close()` method.  Subsequent calls to `close()` have
         * no effect.
         *
         *
         *  While the stream is open, the `append(char)`, `append(CharSequence)`, `append(CharSequence, int, int)`,
         * `flush()`, `write(int)`, `write(char[])`, and
         * `write(char[], int, int)` methods do nothing. After the stream
         * has been closed, these methods all throw `IOException`.
         *
         *
         *  The [object][.lock] used to synchronize operations on the
         * returned `Writer` is not specified.
         *
         * @return a `Writer` which discards all characters
         *
         * @since 11
         */
        fun nullWriter(): Writer {
            return object : Writer() {
                @Volatile
                private var closed = false

                @Throws(IOException::class)
                fun ensureOpen() {
                    if (closed) {
                        throw IOException("Stream closed")
                    }
                }

                @Throws(IOException::class)
                override fun append(c: Char): Writer {
                    ensureOpen()
                    return this
                }

                @Throws(IOException::class)
                override fun append(csq: CharSequence?): Writer {
                    ensureOpen()
                    return this
                }

                @Throws(IOException::class)
                override fun append(csq: CharSequence?, start: Int, end: Int): Writer {
                    ensureOpen()
                    if (csq != null) {
                        Objects.checkFromToIndex(start, end, csq.length)
                    }
                    return this
                }

                @Throws(IOException::class)
                override fun write(c: Int) {
                    ensureOpen()
                }

                @Throws(IOException::class)
                override fun write(cbuf: CharArray, off: Int, len: Int) {
                    Objects.checkFromIndexSize(off, len, cbuf.size)
                    ensureOpen()
                }

                @Throws(IOException::class)
                override fun write(str: String) {
                    requireNotNull(str)
                    ensureOpen()
                }

                @Throws(IOException::class)
                override fun write(str: String, off: Int, len: Int) {
                    Objects.checkFromIndexSize(off, len, str.length)
                    ensureOpen()
                }

                @Throws(IOException::class)
                override fun flush() {
                    ensureOpen()
                }

                @Throws(IOException::class)
                override fun close() {
                    closed = true
                }
            }
        }
    }
}
