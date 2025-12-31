package org.gnit.lucenekmp.jdkport

import okio.IOException
import kotlin.math.min

/**
 * A buffered character-input stream that keeps track of line numbers.  This
 * class defines methods [.setLineNumber] and [ ][.getLineNumber] for setting and getting the current line number
 * respectively.
 *
 *
 *  By default, line numbering begins at 0. This number increments at every
 * [line terminator](#lt) as the data is read, and at the end of the
 * stream if the last character in the stream is not a line terminator.  This
 * number can be changed with a call to `setLineNumber(int)`.  Note
 * however, that `setLineNumber(int)` does not actually change the current
 * position in the stream; it only changes the value that will be returned by
 * `getLineNumber()`.
 *
 *
 *  A line is considered to be <a id="lt">terminated</a> by any one of a
 * line feed ('\n'), a carriage return ('\r'), or a carriage return followed
 * immediately by a linefeed, or any of the previous terminators followed by
 * end of stream, or end of stream not preceded by another terminator.
 *
 * @author      Mark Reinhold
 * @since       1.1
 */
class LineNumberReader : BufferedReader {
    /** The previous character type  */
    private var prevChar = NONE

    /**
     * Get the current line number.
     *
     * @return  The current line number
     *
     * @see .setLineNumber
     */
    /**
     * Set the current line number.
     *
     * @param  lineNumber
     * An int specifying the line number
     *
     * @see .getLineNumber
     */
    /** The current line number  */
    var lineNumber: Int = 0

    /** The line number of the mark, if any  */
    private var markedLineNumber = 0 // Defaults to 0

    /** If the next character is a line feed, skip it  */
    private var skipLF = false

    /** The skipLF flag when the mark was set  */
    private var markedSkipLF = false

    /**
     * Create a new line-numbering reader, using the default input-buffer
     * size.
     *
     * @param  in
     * A Reader object to provide the underlying stream
     */
    constructor(`in`: Reader) : super(`in`)

    /**
     * Create a new line-numbering reader, reading characters into a buffer of
     * the given size.
     *
     * @param  in
     * A Reader object to provide the underlying stream
     *
     * @param  sz
     * An int specifying the size of the buffer
     */
    constructor(`in`: Reader, sz: Int) : super(`in`, sz)

    /**
     * Read a single character.  [Line terminators](#lt) are
     * compressed into single newline ('\n') characters.  The current line
     * number is incremented whenever a line terminator is read, or when the
     * end of the stream is reached and the last character in the stream is
     * not a line terminator.
     *
     * @return  The character read, or -1 if the end of the stream has been
     * reached
     *
     * @throws  IOException
     * If an I/O error occurs
     */
    override fun read(): Int {
        //synchronized(lock) {
            var c: Int = super.read()
            if (skipLF) {
                if (c == '\n'.code) c = super.read()
                skipLF = false
            }
            when (c.toChar()) {
                '\r' -> {
                    skipLF = true
                    lineNumber++
                    prevChar = EOL
                    return '\n'.code
                }

                '\n' -> {
                    lineNumber++
                    prevChar = EOL
                    return '\n'.code
                }

                (-1).toChar() -> {
                    if (prevChar == CHAR) lineNumber++
                    prevChar = EOF
                }

                else -> prevChar = CHAR
            }
            return c
        //}
    }

    /**
     * Reads characters into a portion of an array.  This method will block
     * until some input is available, an I/O error occurs, or the end of the
     * stream is reached.
     *
     *
     *  If `len` is zero, then no characters are read and `0` is
     * returned; otherwise, there is an attempt to read at least one character.
     * If no character is available because the stream is at its end, the value
     * `-1` is returned; otherwise, at least one character is read and
     * stored into `cbuf`.
     *
     *
     * [Line terminators](#lt) are compressed into single newline
     * ('\n') characters.  The current line number is incremented whenever a
     * line terminator is read, or when the end of the stream is reached and
     * the last character in the stream is not a line terminator.
     *
     * @param  cbuf  {@inheritDoc}
     * @param  off   {@inheritDoc}
     * @param  len   {@inheritDoc}
     *
     * @return  {@inheritDoc}
     *
     * @throws  IndexOutOfBoundsException {@inheritDoc}
     * @throws  IOException {@inheritDoc}
     */
    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        //synchronized(lock) {
            val n: Int = super.read(cbuf, off, len)
            if (n == -1) {
                if (prevChar == CHAR) lineNumber++
                prevChar = EOF
                return -1
            }

            for (i in off..<off + n) {
                val c = cbuf[i].code
                if (skipLF) {
                    skipLF = false
                    if (c == '\n'.code) continue
                }
                when (c.toChar()) {
                    '\r' -> {
                        skipLF = true
                        lineNumber++
                    }

                    '\n' -> lineNumber++
                }
            }

            if (n > 0) {
                when (cbuf[off + n - 1].code) {
                    '\r'.code, '\n'.code -> prevChar = EOL
                    else -> prevChar = CHAR
                }
            }
            return n
        //}
    }

    /**
     * Read a line of text.  [Line terminators](#lt) are compressed
     * into single newline ('\n') characters. The current line number is
     * incremented whenever a line terminator is read, or when the end of the
     * stream is reached and the last character in the stream is not a line
     * terminator.
     *
     * @return  A String containing the contents of the line, not including
     * any [line termination characters](#lt), or
     * `null` if the end of the stream has been reached
     *
     * @throws  IOException
     * If an I/O error occurs
     */
    @Throws(IOException::class)
    override fun readLine(): String? {
        //synchronized(lock) {
            val term = BooleanArray(1)
            val l: String? = super.readLine(skipLF, term)
            skipLF = false
            if (l != null) {
                lineNumber++
                prevChar = if (term[0]) EOL else EOF
            } else { // l == null
                if (prevChar == CHAR) lineNumber++
                prevChar = EOF
            }
            return l
        //}
    }

    /** Skip buffer, null until allocated  */
    private var skipBuffer: CharArray? = null

    /**
     * {@inheritDoc}
     */
    @Throws(IOException::class)
    override fun skip(n: Long): Long {
        require(n >= 0) { "skip() value is negative" }
        val nn = min(n, maxSkipBufferSize.toLong()).toInt()
        //synchronized(lock) {
            if ((skipBuffer == null) || (skipBuffer!!.size < nn)) skipBuffer = CharArray(nn)
            var r = n
            while (r > 0) {
                val nc = read(skipBuffer!!, 0, min(r, nn.toLong()).toInt())
                if (nc == -1) break
                r -= nc.toLong()
            }
            if (n - r > 0) {
                prevChar = NONE
            }
            return n - r
        //}
    }

    /**
     * Mark the present position in the stream.  Subsequent calls to reset()
     * will attempt to reposition the stream to this point, and will also reset
     * the line number appropriately.
     *
     * @param  readAheadLimit
     * Limit on the number of characters that may be read while still
     * preserving the mark.  After reading this many characters,
     * attempting to reset the stream may fail.
     *
     * @throws  IOException
     * If an I/O error occurs
     */
    @Throws(IOException::class)
    override fun mark(readAheadLimit: Int) {
        var readAheadLimit = readAheadLimit
        //synchronized(lock) {
            // If the most recently read character is '\r', then increment the
            // read ahead limit as in this case if the next character is '\n',
            // two characters would actually be read by the next read().
            if (skipLF) readAheadLimit++
            super.mark(readAheadLimit)
            markedLineNumber = lineNumber
            markedSkipLF = skipLF
        //}
    }

    /**
     * Reset the stream to the most recent mark.
     *
     * @throws  IOException
     * If the stream has not been marked, or if the mark has been
     * invalidated
     */
    @Throws(IOException::class)
    override fun reset() {
        //synchronized(lock) {
            super.reset()
            lineNumber = markedLineNumber
            skipLF = markedSkipLF
        //}
    }

    companion object {
        /** Previous character types  */
        private const val NONE = 0 // no previous character
        private const val CHAR = 1 // non-line terminator
        private const val EOL = 2 // line terminator
        private const val EOF = 3 // end-of-file

        /** Maximum skip-buffer size  */
        private const val maxSkipBufferSize = 8192
    }
}
