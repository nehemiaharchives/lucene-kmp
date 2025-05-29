package org.gnit.lucenekmp.jdkport

import okio.IOException


/**
 * port of java.io.BufferedWriter
 *
 * Writes text to a character-output stream, buffering characters so as to
 * provide for the efficient writing of single characters, arrays, and strings.
 *
 *
 *  The buffer size may be specified, or the default size may be accepted.
 * The default is large enough for most purposes.
 *
 *
 *  A `newLine()` method is provided, which uses the platform's own
 * notion of line separator as defined by the system property
 * [line.separator][System.lineSeparator]. Not all platforms use the newline character ('\n')
 * to terminate lines. Calling this method to terminate each output line is
 * therefore preferred to writing a newline character directly.
 *
 *
 *  In general, a `Writer` sends its output immediately to the
 * underlying character or byte stream.  Unless prompt output is required, it
 * is advisable to wrap a `BufferedWriter` around any `Writer` whose
 * `write()` operations may be costly, such as `FileWriter`s and
 * `OutputStreamWriter`s.  For example,
 *
 * {@snippet lang=java :
 * *     PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("foo.out")));
 * * }
 *
 * will buffer the `PrintWriter`'s output to the file.  Without buffering,
 * each invocation of a `print()` method would cause characters to be
 * converted into bytes that would then be written immediately to the file,
 * which can be very inefficient.
 *
 * @apiNote
 * Once wrapped in a `BufferedWriter`, the underlying
 * `Writer` should not be used directly nor wrapped with
 * another writer.
 *
 * @see PrintWriter
 *
 * @see FileWriter
 *
 * @see OutputStreamWriter
 *
 * @see java.nio.file.Files.newBufferedWriter
 *
 *
 * @author      Mark Reinhold
 * @since       1.1
 */
open class BufferedWriter private constructor(out: Writer, initialSize: Int, maxSize: Int) : Writer() {
    private var out: Writer?

    private var cb: CharArray?
    private var nChars: Int
    private var nextChar = 0
    private val maxChars: Int // maximum number of buffers chars

    /**
     * Creates a buffered character-output stream.
     */
    init {
        require(initialSize > 0) { "Buffer size <= 0" }

        this.out = out
        this.cb = CharArray(initialSize)
        this.nChars = initialSize
        this.maxChars = maxSize
    }

    /**
     * Creates a buffered character-output stream that uses a default-sized
     * output buffer.
     *
     * @param  out  A Writer
     */
    constructor(out: Writer) : this(out, initialBufferSize(), DEFAULT_MAX_BUFFER_SIZE)

    /**
     * Creates a new buffered character-output stream that uses an output
     * buffer of the given size.
     *
     * @param  out  A Writer
     * @param  sz   Output-buffer size, a positive integer
     *
     * @throws     IllegalArgumentException  If `sz <= 0`
     */
    constructor(out: Writer, sz: Int) : this(out, sz, sz)

    /** Checks to make sure that the stream has not been closed  */
    @Throws(IOException::class)
    private fun ensureOpen() {
        if (out == null) throw IOException("Stream closed")
    }

    /**
     * Grow char array to fit an additional len characters if needed.
     * If possible, it grows by len+1 to avoid flushing when len chars
     * are added.
     *
     * This method should only be called while holding the lock.
     */
    private fun growIfNeeded(len: Int) {
        var neededSize = nextChar + len + 1
        if (neededSize < 0) neededSize = Int.Companion.MAX_VALUE
        if (neededSize > nChars && nChars < maxChars) {
            val newSize = min(neededSize, maxChars)
            cb = cb!!.copyOf(newSize)
            nChars = newSize
        }
    }

    /**
     * Flushes the output buffer to the underlying character stream, without
     * flushing the stream itself.  This method is non-private only so that it
     * may be invoked by PrintStream.
     */
    @Throws(IOException::class)
    fun flushBuffer() {
        //synchronized(lock) {
            ensureOpen()
            if (nextChar == 0) return
            out!!.write(cb!!, 0, nextChar)
            nextChar = 0
        //}
    }

    /**
     * Writes a single character.
     *
     * @throws     IOException  If an I/O error occurs
     */
    @Throws(IOException::class)
    override fun write(c: Int) {
        //synchronized(lock) {
            ensureOpen()
            growIfNeeded(1)
            if (nextChar >= nChars) flushBuffer()
            cb!![nextChar++] = c.toChar()
        //}
    }

    /**
     * Our own little min method, to avoid loading java.lang.Math if we've run
     * out of file descriptors and we're trying to print a stack trace.
     */
    private fun min(a: Int, b: Int): Int {
        if (a < b) return a
        return b
    }

    /**
     * Writes a portion of an array of characters.
     *
     *
     *  Ordinarily this method stores characters from the given array into
     * this stream's buffer, flushing the buffer to the underlying stream as
     * needed.  If the requested length is at least as large as the buffer,
     * however, then this method will flush the buffer and write the characters
     * directly to the underlying stream.  Thus redundant
     * `BufferedWriter`s will not copy data unnecessarily.
     *
     * @param  cbuf  A character array
     * @param  off   Offset from which to start reading characters
     * @param  len   Number of characters to write
     *
     * @throws  IndexOutOfBoundsException
     * If `off` is negative, or `len` is negative,
     * or `off + len` is negative or greater than the length
     * of the given array
     *
     * @throws  IOException  If an I/O error occurs
     */
    @Throws(IOException::class)
    override fun write(cbuf: CharArray, off: Int, len: Int) {
        //synchronized(lock) {
            ensureOpen()
            Objects.checkFromIndexSize(off, len, cbuf.size)
            if (len == 0) {
                return
            }

            if (len >= maxChars) {
                /* If the request length exceeds the max size of the output buffer,
                   flush the buffer and then write the data directly.  In this
                   way buffered streams will cascade harmlessly. */
                flushBuffer()
                out!!.write(cbuf, off, len)
                return
            }

            growIfNeeded(len)
            var b = off
            val t = off + len
            while (b < t) {
                val d = min(nChars - nextChar, t - b)
                System.arraycopy(cbuf, b, cb!!, nextChar, d)
                b += d
                nextChar += d
                if (nextChar >= nChars) {
                    flushBuffer()
                }
            }
        //}
    }

    /**
     * Writes a portion of a String.
     *
     * @implSpec
     * While the specification of this method in the
     * [superclass][Writer.write]
     * recommends that an [IndexOutOfBoundsException] be thrown
     * if `len` is negative or `off + len` is negative,
     * the implementation in this class does not throw such an exception in
     * these cases but instead simply writes no characters.
     *
     * @param  s     String to be written
     * @param  off   Offset from which to start reading characters
     * @param  len   Number of characters to be written
     *
     * @throws  IndexOutOfBoundsException
     * If `off` is negative,
     * or `off + len` is greater than the length
     * of the given string
     *
     * @throws  IOException  If an I/O error occurs
     */
    @Throws(IOException::class)
    override fun write(s: String, off: Int, len: Int) {
        //synchronized(lock) {
            ensureOpen()
            growIfNeeded(len)
            var b = off
            val t = off + len
            while (b < t) {
                val d = min(nChars - nextChar, t - b)
                s.toCharArray(cb!!, nextChar, b, b + d)
                b += d
                nextChar += d
                if (nextChar >= nChars) flushBuffer()
            }
        //}
    }

    /**
     * Writes a line separator.  The line separator string is defined by the
     * system property `line.separator`, and is not necessarily a single
     * newline ('\n') character.
     *
     * @throws     IOException  If an I/O error occurs
     */
    @Throws(IOException::class)
    fun newLine() {
        write(System.lineSeparator())
    }

    /**
     * Flushes the stream.
     *
     * @throws     IOException  If an I/O error occurs
     */
    @Throws(IOException::class)
    override fun flush() {
        //synchronized(lock) {
            flushBuffer()
            out!!.flush()
        //}
    }

    override fun close() {
        //synchronized(lock) {
            if (out == null) {
                return
            }
            try {
                out.use { w ->
                    flushBuffer()
                }
            } finally {
                out = null
                cb = null
            }
        //}
    }

    companion object {
        private const val DEFAULT_INITIAL_BUFFER_SIZE = 512
        private const val DEFAULT_MAX_BUFFER_SIZE = 8192

        /**
         * Returns the buffer size to use when no output buffer size specified
         */
        private fun initialBufferSize(): Int {
            /*if (jdk.internal.misc.VM.isBooted() && java.lang.Thread.currentThread().isVirtual()) {
                return DEFAULT_INITIAL_BUFFER_SIZE
            } else {
                return DEFAULT_MAX_BUFFER_SIZE
            }*/

            return DEFAULT_MAX_BUFFER_SIZE
        }
    }
}
