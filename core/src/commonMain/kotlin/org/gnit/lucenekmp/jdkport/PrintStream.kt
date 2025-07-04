package org.gnit.lucenekmp.jdkport

import okio.IOException

/**
 * caution: only minimum functionality which is called by lucene is implemented
 *
 * A {@code PrintStream} adds functionality to another output stream,
 * namely the ability to print representations of various data values
 * conveniently.  Two other features are provided as well.  Unlike other output
 * streams, a {@code PrintStream} never throws an
 * {@code IOException}; instead, exceptional situations merely set an
 * internal flag that can be tested via the {@code checkError} method.
 * Optionally, a {@code PrintStream} can be created so as to flush
 * automatically; this means that the {@code flush} method of the underlying
 * output stream is automatically invoked after a byte array is written, one
 * of the {@code println} methods is invoked, or a newline character or byte
 * ({@code '\n'}) is written.
 *
 * <p> All characters printed by a {@code PrintStream} are converted into
 * bytes using the given encoding or charset, or the default charset if not
 * specified.
 * The {@link PrintWriter} class should be used in situations that require
 * writing characters rather than bytes.
 *
 * <p> This class always replaces malformed and unmappable character sequences
 * with the charset's default replacement string.
 * The {@linkplain java.nio.charset.CharsetEncoder} class should be used when more
 * control over the encoding process is required.
 *
 * @author     Frank Yellin
 * @author     Mark Reinhold
 * @since      1.0
 * @see Charset#defaultCharset()
 */
@Ported(from = "java.io.PrintStream")
open class PrintStream(private val autoFlush: Boolean = false, out: OutputStream) : FilterOutputStream(out) {

    private var trouble = false
    private val charset: Charset
    /**
     * Track both the text- and character-output streams, so that their buffers
     * can be flushed without flushing the entire stream.
     */
    private var textOut: BufferedWriter? = null
    private var charOut: OutputStreamWriter? = null

    init {
        this.charset = if (out is PrintStream) out.charset else Charset.defaultCharset()
        this.charOut = OutputStreamWriter(this, charset)
        this.textOut = BufferedWriter(charOut!!)
    }

    /**
     * Prints a String and then terminates the line.  This method behaves as
     * though it invokes [.print] and then
     * [.println].
     *
     * @param x  The `String` to be printed.
     */
    open fun println(x: String?) {
        if (this::class == PrintStream::class) {
            writeln(x.toString())
        } else {
            /*synchronized(this) {
                print(x)
                newLine()
            }*/

            print(x)
            newLine()
        }
    }

    // Used to optimize away back-to-back flushing and synchronization when
    // using println, but since subclasses could exist which depend on
    // observing a call to print followed by newLine we only use this if
    // getClass() == PrintStream.class to avoid compatibility issues.
    private fun writeln(s: String) {
        try {
            //synchronized(this) {
                ensureOpen()
                textOut!!.write(s)
                textOut!!.newLine()
                textOut!!.flushBuffer()
                charOut!!.flushBuffer()
                if (autoFlush) out!!.flush()
            //}
        } catch (x: /*java.io.Interrupted*/IOException) {/*
            java.lang.Thread.currentThread().interrupt()
        } catch (x: java.io.IOException) {*/
            trouble = true
        }
    }

    private fun newLine() {
        try {
            //synchronized(this) {
                ensureOpen()
                textOut!!.newLine()
                textOut!!.flushBuffer()
                charOut!!.flushBuffer()
                if (autoFlush) out!!.flush()
            //}
        } catch (x: /*Interrupted*/IOException){ /*
            java.lang.Thread.currentThread().interrupt()
        } catch (x: java.io.IOException) {*/
            trouble = true
        }
    }


    /** Check to make sure that the stream has not been closed  */
    @Throws(IOException::class)
    private fun ensureOpen() {
        if (out == null) throw IOException("Stream closed")
    }


    /**
     * Flushes the stream.  This is done by writing any buffered output bytes to
     * the underlying output stream and then flushing that stream.
     *
     * @see java.io.OutputStream.flush
     */
    override fun flush() {
        //synchronized(this) {
            try {
                ensureOpen()
                out!!.flush()
            } catch (x: IOException) {
                trouble = true
            }
        //}
    }

    private var closing = false /* To avoid recursive closing */

    /**
     * Closes the stream.  This is done by flushing the stream and then closing
     * the underlying output stream.
     *
     * @see java.io.OutputStream.close
     */
    override fun close() {
        //synchronized(this) {
            if (!closing) {
                closing = true
                try {
                    textOut!!.close()
                    out!!.close()
                } catch (x: IOException) {
                    trouble = true
                }
                textOut = null
                charOut = null
                out = null
            }
        //}
    }
}