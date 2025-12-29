package org.gnit.lucenekmp.jdkport

import okio.IOException
import kotlin.jvm.JvmOverloads
import kotlin.math.min


/**
 * Reads text from a character-input stream, buffering characters so as to
 * provide for the efficient reading of characters, arrays, and lines.
 *
 *
 *  The buffer size may be specified, or the default size may be used.  The
 * default is large enough for most purposes.
 *
 *
 *  In general, each read request made of a Reader causes a corresponding
 * read request to be made of the underlying character or byte stream.  It is
 * therefore advisable to wrap a BufferedReader around any Reader whose read()
 * operations may be costly, such as FileReaders and InputStreamReaders.  For
 * example,
 *
 * {@snippet lang=java :
 * *     BufferedReader in = new BufferedReader(new FileReader("foo.in"));
 * * }
 *
 * will buffer the input from the specified file.  Without buffering, each
 * invocation of read() or readLine() could cause bytes to be read from the
 * file, converted into characters, and then returned, which can be very
 * inefficient.
 *
 *
 *  Programs that use DataInputStreams for textual input can be localized by
 * replacing each DataInputStream with an appropriate BufferedReader.
 *
 * @see FileReader
 *
 * @see InputStreamReader
 *
 * @see java.nio.file.Files.newBufferedReader
 *
 *
 * @author      Mark Reinhold
 * @since       1.1
 */
open class BufferedReader @JvmOverloads constructor(`in`: Reader, sz: Int = DEFAULT_CHAR_BUFFER_SIZE) :
    Reader(/*`in`*/) {
    private var `in`: Reader?

    private var cb: CharArray?
    private var nChars: Int
    private var nextChar: Int

    private var markedChar = UNMARKED
    private var readAheadLimit = 0 /* Valid only when markedChar > 0 */

    /** If the next character is a line feed, skip it  */
    private var skipLF = false

    /** The skipLF flag when the mark was set  */
    private var markedSkipLF = false

    /**
     * Creates a buffering character-input stream that uses an input buffer of
     * the specified size.
     *
     * @param  in   A Reader
     * @param  sz   Input-buffer size
     *
     * @throws IllegalArgumentException  If `sz <= 0`
     */
    /**
     * Creates a buffering character-input stream that uses a default-sized
     * input buffer.
     *
     * @param  in   A Reader
     */
    init {
        require(sz > 0) { "Buffer size <= 0" }
        this.`in` = `in`
        cb = CharArray(sz)
        nChars = 0
        nextChar = nChars
    }

    /** Checks to make sure that the stream has not been closed  */
    @Throws(IOException::class)
    private fun ensureOpen() {
        if (`in` == null) throw IOException("Stream closed")
    }

    /**
     * Fills the input buffer, taking the mark into account if it is valid.
     */
    @Throws(IOException::class)
    private fun fill() {
        val dst: Int
        if (markedChar <= UNMARKED) {
            /* No mark */
            dst = 0
        } else {
            /* Marked */
            val delta = nextChar - markedChar
            if (delta >= readAheadLimit) {
                /* Gone past read-ahead limit: Invalidate mark */
                markedChar = INVALIDATED
                readAheadLimit = 0
                dst = 0
            } else {
                if (readAheadLimit <= cb!!.size) {
                    /* Shuffle in the current buffer */
                    System.arraycopy(cb!!, markedChar, cb!!, 0, delta)
                    markedChar = 0
                    dst = delta
                } else {
                    /* Reallocate buffer to accommodate read-ahead limit */
                    val ncb = CharArray(readAheadLimit)
                    System.arraycopy(cb!!, markedChar, ncb, 0, delta)
                    cb = ncb
                    markedChar = 0
                    dst = delta
                }
                nChars = delta
                nextChar = nChars
            }
        }

        var n: Int
        do {
            n = `in`!!.read(cb!!, dst, cb!!.size - dst)
        } while (n == 0)
        if (n > 0) {
            nChars = dst + n
            nextChar = dst
        }
    }

    /**
     * Reads a single character.
     *
     * @return The character read, as an integer in the range
     * 0 to 65535 (`0x00-0xffff`), or -1 if the
     * end of the stream has been reached
     * @throws     IOException  If an I/O error occurs
     */
    override fun read(): Int {
        ensureOpen()
        while (true) {
            if (nextChar >= nChars) {
                fill()
                if (nextChar >= nChars) return -1
            }
            if (skipLF) {
                skipLF = false
                if (cb!![nextChar] == '\n') {
                    nextChar++
                    continue
                }
            }
            return cb!![nextChar++].code
        }
    }

    /**
     * Reads characters into a portion of an array, reading from the underlying
     * stream if necessary.
     */
    @Throws(IOException::class)
    private fun read1(cbuf: CharArray, off: Int, len: Int): Int {
        if (nextChar >= nChars) {
            /* If the requested length is at least as large as the buffer, and
               if there is no mark/reset activity, and if line feeds are not
               being skipped, do not bother to copy the characters into the
               local buffer.  In this way buffered streams will cascade
               harmlessly. */
            if (len >= cb!!.size && markedChar <= UNMARKED && !skipLF) {
                return `in`!!.read(cbuf, off, len)
            }
            fill()
        }
        if (nextChar >= nChars) return -1
        if (skipLF) {
            skipLF = false
            if (cb!![nextChar] == '\n') {
                nextChar++
                if (nextChar >= nChars) fill()
                if (nextChar >= nChars) return -1
            }
        }
        val n = min(len, nChars - nextChar)
        System.arraycopy(cb!!, nextChar, cbuf, off, n)
        nextChar += n
        return n
    }

    /**
     * Reads characters into a portion of an array.
     *
     *
     *  This method implements the general contract of the corresponding
     * [read][Reader.read] method of the
     * [Reader] class.  As an additional convenience, it
     * attempts to read as many characters as possible by repeatedly invoking
     * the `read` method of the underlying stream.  This iterated
     * `read` continues until one of the following conditions becomes
     * true:
     *
     *
     *  *  The specified number of characters have been read,
     *
     *  *  The `read` method of the underlying stream returns
     * `-1`, indicating end-of-file, or
     *
     *  *  The `ready` method of the underlying stream
     * returns `false`, indicating that further input requests
     * would block.
     *
     *
     * If the first `read` on the underlying stream returns
     * `-1` to indicate end-of-file then this method returns
     * `-1`.  Otherwise this method returns the number of characters
     * actually read.
     *
     *
     *  Subclasses of this class are encouraged, but not required, to
     * attempt to read as many characters as possible in the same fashion.
     *
     *
     *  Ordinarily this method takes characters from this stream's character
     * buffer, filling it from the underlying stream as necessary.  If,
     * however, the buffer is empty, the mark is not valid, and the requested
     * length is at least as large as the buffer, then this method will read
     * characters directly from the underlying stream into the given array.
     * Thus redundant `BufferedReader`s will not copy data
     * unnecessarily.
     *
     * @param      cbuf  {@inheritDoc}
     * @param      off   {@inheritDoc}
     * @param      len   {@inheritDoc}
     *
     * @return     {@inheritDoc}
     *
     * @throws     IndexOutOfBoundsException {@inheritDoc}
     * @throws     IOException  {@inheritDoc}
     */
    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        ensureOpen()
        Objects.checkFromIndexSize(off, len, cbuf.size)
        if (len == 0) {
            return 0
        }

        var n = read1(cbuf, off, len)
        if (n <= 0) return n
        while ((n < len) /*&& `in`.ready()*/) {
            val n1 = read1(cbuf, off + n, len - n)
            if (n1 <= 0) break
            n += n1
        }
        return n
    }

    /**
     * Reads a line of text.  A line is considered to be terminated by any one
     * of a line feed ('\n'), a carriage return ('\r'), a carriage return
     * followed immediately by a line feed, or by reaching the end-of-file
     * (EOF).
     *
     * @param      ignoreLF  If true, the next '\n' will be skipped
     * @param      term      Output: Whether a line terminator was encountered
     * while reading the line; may be `null`.
     *
     * @return     A String containing the contents of the line, not including
     * any line-termination characters, or null if the end of the
     * stream has been reached without reading any characters
     *
     * @see java.io.LineNumberReader.readLine
     * @throws     IOException  If an I/O error occurs
     */
    @Throws(IOException::class)
    fun readLine(ignoreLF: Boolean, term: BooleanArray?): String? {
        var s: StringBuilder? = null
        var startChar: Int

        ensureOpen()
        var omitLF = ignoreLF || skipLF
        if (term != null) term[0] = false

        bufferLoop@ while (true) {
            if (nextChar >= nChars) fill()
            if (nextChar >= nChars) { /* EOF */
                return if (s != null && s.isNotEmpty()) s.toString()
                else null
            }
            var eol = false
            var c = 0.toChar()

            /* Skip a leftover '\n', if necessary */
            if (omitLF && (cb!![nextChar] == '\n')) nextChar++
            skipLF = false
            omitLF = false

            var i: Int = nextChar
            charLoop@ while (i < nChars) {
                c = cb!![i]
                if ((c == '\n') || (c == '\r')) {
                    if (term != null) term[0] = true
                    eol = true
                    break@charLoop
                }
                i++
            }
            startChar = nextChar
            nextChar = i

            if (eol) {
                val str: String?
                if (s == null) {
                    str = String.fromCharArray(cb!!, startChar, i - startChar)
                } else {
                    s.appendRange(cb!!, startChar, startChar + (i - startChar))
                    str = s.toString()
                }
                nextChar++
                if (c == '\r') {
                    skipLF = true
                }
                return str
            }

            if (s == null) s = StringBuilder(DEFAULT_EXPECTED_LINE_LENGTH)
            s.appendRange(cb!!, startChar, startChar + (i - startChar))
        }
    }

    /**
     * Reads a line of text.  A line is considered to be terminated by any one
     * of a line feed ('\n'), a carriage return ('\r'), a carriage return
     * followed immediately by a line feed, or by reaching the end-of-file
     * (EOF).
     *
     * @return     A String containing the contents of the line, not including
     * any line-termination characters, or null if the end of the
     * stream has been reached without reading any characters
     *
     * @throws     IOException  If an I/O error occurs
     *
     * @see java.nio.file.Files.readAllLines
     */
    @Throws(IOException::class)
    open fun readLine(): String? {
        return readLine(false, null)
    }

    /**
     * {@inheritDoc}
     */
    @Throws(IOException::class)
    fun skip(n: Long): Long {
        require(n >= 0L) { "skip value is negative" }
        ensureOpen()
        var r = n
        while (r > 0) {
            if (nextChar >= nChars) fill()
            if (nextChar >= nChars)  /* EOF */
                break
            if (skipLF) {
                skipLF = false
                if (cb!![nextChar] == '\n') {
                    nextChar++
                }
            }
            val d = (nChars - nextChar).toLong()
            if (r <= d) {
                nextChar += r.toInt()
                r = 0
                break
            } else {
                r -= d
                nextChar = nChars
            }
        }
        return n - r
    }

    /**
     * Tells whether this stream is ready to be read.  A buffered character
     * stream is ready if the buffer is not empty, or if the underlying
     * character stream is ready.
     *
     * @throws     IOException  If an I/O error occurs
     */
    @Throws(IOException::class)
    override fun ready(): Boolean {
        ensureOpen()

        /*
         * If newline needs to be skipped and the next char to be read
         * is a newline character, then just skip it right away.
         */
        if (skipLF) {
            /* Note that in.ready() will return true if and only if the next
             * read on the stream will not block.
             */
            if (nextChar >= nChars && `in`!!.ready()) {
                fill()
            }
            if (nextChar < nChars) {
                if (cb!![nextChar] == '\n') nextChar++
                skipLF = false
            }
        }
        return (nextChar < nChars) || `in`!!.ready()
    }

    /**
     * Tells whether this stream supports the mark() operation, which it does.
     */
    fun markSupported(): Boolean {
        return true
    }

    /**
     * Marks the present position in the stream.  Subsequent calls to reset()
     * will attempt to reposition the stream to this point.
     *
     * @param readAheadLimit   Limit on the number of characters that may be
     * read while still preserving the mark. An attempt
     * to reset the stream after reading characters
     * up to this limit or beyond may fail.
     * A limit value larger than the size of the input
     * buffer will cause a new buffer to be allocated
     * whose size is no smaller than limit.
     * Therefore large values should be used with care.
     *
     * @throws     IllegalArgumentException  If `readAheadLimit < 0`
     * @throws     IOException  If an I/O error occurs
     */
    @Throws(IOException::class)
    fun mark(readAheadLimit: Int) {
        require(readAheadLimit >= 0) { "Read-ahead limit < 0" }
        ensureOpen()
        this.readAheadLimit = readAheadLimit
        markedChar = nextChar
        markedSkipLF = skipLF
    }

    /**
     * Resets the stream to the most recent mark.
     *
     * @throws     IOException  If the stream has never been marked,
     * or if the mark has been invalidated
     */
    @Throws(IOException::class)
    override fun reset() {
        ensureOpen()
        if (markedChar < 0) throw IOException(
            if (markedChar == INVALIDATED)
                "Mark invalid"
            else
                "Stream not marked"
        )
        nextChar = markedChar
        skipLF = markedSkipLF
    }

    override fun close() {
        if (`in` == null) return
        try {
            `in`!!.close()
        } finally {
            `in` = null
            cb = null
        }
    }

    /**
     * Returns a `Stream`, the elements of which are lines read from
     * this `BufferedReader`.  The [Stream] is lazily populated,
     * i.e., read only occurs during the
     * [terminal
 * stream operation](../util/stream/package-summary.html#StreamOps).
     *
     *
     *  The reader must not be operated on during the execution of the
     * terminal stream operation. Otherwise, the result of the terminal stream
     * operation is undefined.
     *
     *
     *  After execution of the terminal stream operation there are no
     * guarantees that the reader will be at a specific position from which to
     * read the next character or line.
     *
     *
     *  If an [IOException] is thrown when accessing the underlying
     * `BufferedReader`, it is wrapped in an [ ] which will be thrown from the `Stream`
     * method that caused the read to take place. This method will return a
     * Stream if invoked on a BufferedReader that is closed. Any operation on
     * that stream that requires reading from the BufferedReader after it is
     * closed, will cause an UncheckedIOException to be thrown.
     *
     * @return a `Stream<String>` providing the lines of text
     * described by this `BufferedReader`
     *
     * @since 1.8
     */
    /*fun lines(): Sequence<String> {
        val iter: MutableIterator<String?> = object : MutableIterator<String?> {
            var nextLine: String? = null

            override fun hasNext(): Boolean {
                if (nextLine != null) {
                    return true
                } else {
                    try {
                        nextLine = readLine()
                        return (nextLine != null)
                    } catch (e: IOException) {
                        throw UncheckedIOException(e)
                    }
                }
            }

            override fun next(): String? {
                if (nextLine != null || hasNext()) {
                    val line = nextLine
                    nextLine = null
                    return line
                } else {
                    throw NoSuchElementException()
                }
            }
        }
        return StreamSupport.stream<String?>(
            java.util.Spliterators.spliteratorUnknownSize<String?>(
                iter, java.util.Spliterator.ORDERED or java.util.Spliterator.NONNULL
            ), false
        )
    }*/

    companion object {
        private const val INVALIDATED = -2
        private const val UNMARKED = -1
        private const val DEFAULT_CHAR_BUFFER_SIZE = 8192
        private const val DEFAULT_EXPECTED_LINE_LENGTH = 80
    }
}
