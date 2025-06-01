package org.gnit.lucenekmp.jdkport

import okio.IOException

/**
 * port of java.io.OutputStreamWriter
 *
 * An OutputStreamWriter is a bridge from character streams to byte streams:
 * Characters written to it are encoded into bytes using a specified [ ].  The charset that it uses
 * may be specified by name or may be given explicitly, or the
 * default charset may be accepted.
 *
 *
 *  Each invocation of a write() method causes the encoding converter to be
 * invoked on the given character(s).  The resulting bytes are accumulated in a
 * buffer before being written to the underlying output stream.  Note that the
 * characters passed to the write() methods are not buffered.
 *
 *
 *  For top efficiency, consider wrapping an OutputStreamWriter within a
 * BufferedWriter so as to avoid frequent converter invocations.  For example:
 *
 * {@snippet lang=java :
 * *     Writer out = new BufferedWriter(new OutputStreamWriter(anOutputStream));
 * * }
 *
 *
 *  A *surrogate pair* is a character represented by a sequence of two
 * `char` values: A *high* surrogate in the range '&#92;uD800' to
 * '&#92;uDBFF' followed by a *low* surrogate in the range '&#92;uDC00' to
 * '&#92;uDFFF'.
 *
 *
 *  A *malformed surrogate element* is a high surrogate that is not
 * followed by a low surrogate or a low surrogate that is not preceded by a
 * high surrogate.
 *
 *
 *  This class always replaces malformed surrogate elements and unmappable
 * character sequences with the charset's default *substitution sequence*.
 * The [CharsetEncoder] class should be used when more
 * control over the encoding process is required.
 *
 * @see BufferedWriter
 *
 * @see OutputStream
 *
 * @see Charset
 *
 *
 * @author      Mark Reinhold
 * @since       1.1
 */
open class OutputStreamWriter : Writer {
    private val se: StreamEncoder

    /**
     * Creates an OutputStreamWriter that uses the named charset.
     *
     * @param  out
     * An OutputStream
     *
     * @param  charsetName
     * The name of a supported [charset][Charset]
     *
     * @throws     UnsupportedEncodingException
     * If the named encoding is not supported
     */
    /*constructor(out: OutputStream, charsetName: String) : super(out) {
        se = StreamEncoder.forOutputStreamWriter(out, this, charsetName)
    }*/

    /**
     * Creates an OutputStreamWriter that uses the default character encoding, or
     * where `out` is a `PrintStream`, the charset used by the print
     * stream.
     *
     * @param  out  An OutputStream
     * @see Charset.defaultCharset
     */
    /*constructor(out: OutputStream) : super(out) {
        se = StreamEncoder.forOutputStreamWriter(
            out, this,
            if (out is PrintStream) out.charset() else Charset.defaultCharset()
        )
    }*/

    /**
     * Creates an OutputStreamWriter that uses the given charset.
     *
     * @param  out
     * An OutputStream
     *
     * @param  cs
     * A charset
     *
     * @since 1.4
     */
    constructor(out: OutputStream, cs: Charset) : super() {
        se = StreamEncoder.forOutputStreamWriter(out, cs)
    }

    /**
     * Creates an OutputStreamWriter that uses the given charset encoder.
     *
     * @param  out
     * An OutputStream
     *
     * @param  enc
     * A charset encoder
     *
     * @since 1.4
     */
    constructor(out: OutputStream, enc: CharsetEncoder) : super() {
        se = StreamEncoder.forOutputStreamWriter(out, enc)
    }

    val encoding: String?
        /**
         * Returns the name of the character encoding being used by this stream.
         *
         *
         *  If the encoding has an historical name then that name is returned;
         * otherwise the encoding's canonical name is returned.
         *
         *
         *  If this instance was created with the [ ][.OutputStreamWriter] constructor then the returned
         * name, being unique for the encoding, may differ from the name passed to
         * the constructor.  This method may return `null` if the stream has
         * been closed.
         *
         * @return The historical name of this encoding, or possibly
         * `null` if the stream has been closed
         *
         * @see Charset
         */
        get() = se.encoding

    /**
     * Flushes the output buffer to the underlying byte stream, without flushing
     * the byte stream itself.  This method is non-private only so that it may
     * be invoked by PrintStream.
     */
    @Throws(IOException::class)
    fun flushBuffer() {
        se.flushBuffer()
    }

    /**
     * Writes a single character.
     *
     * @throws     IOException  If an I/O error occurs
     */
    @Throws(IOException::class)
    override fun write(c: Int) {
        se.write(c)
    }

    /**
     * Writes a portion of an array of characters.
     *
     * @param  cbuf  Buffer of characters
     * @param  off   Offset from which to start writing characters
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
        se.write(cbuf, off, len)
    }

    /**
     * Writes a portion of a string.
     *
     * @param  str  A String
     * @param  off  Offset from which to start writing characters
     * @param  len  Number of characters to write
     *
     * @throws  IndexOutOfBoundsException
     * If `off` is negative, or `len` is negative,
     * or `off + len` is negative or greater than the length
     * of the given string
     *
     * @throws  IOException  If an I/O error occurs
     */
    @Throws(IOException::class)
    override fun write(str: String, off: Int, len: Int) {
        se.write(str, off, len)
    }

    override fun append(csq: CharSequence?, start: Int, end: Int): Writer {
        var csq = csq
        if (csq == null) csq = "null"
        return append(csq.subSequence(start, end))
    }

    override fun append(csq: CharSequence?): Writer {
        if (csq is CharBuffer) {
            se.write(csq as CharBuffer)
        } else {
            se.write(csq.toString())
        }
        return this
    }

    /**
     * Flushes the stream.
     *
     * @throws     IOException  If an I/O error occurs
     */
    @Throws(IOException::class)
    override fun flush() {
        se.flush()
    }

    override fun close() {
        se.close()
    }
}
