package org.gnit.lucenekmp.jdkport

import kotlinx.io.IOException
import kotlin.concurrent.Volatile


/**
 * This class is the superclass of all classes that filter output
 * streams. These streams sit on top of an already existing output
 * stream (the *underlying* output stream) which it uses as its
 * basic sink of data, but possibly transforming the data along the
 * way or providing additional functionality.
 *
 *
 * The class `FilterOutputStream` itself simply overrides
 * all methods of `OutputStream` with versions that pass
 * all requests to the underlying output stream. Subclasses of
 * `FilterOutputStream` may further override some of these
 * methods as well as provide additional methods and fields.
 *
 * @author  Jonathan Payne
 * @since   1.0
 */
open class FilterOutputStream(
    /**
     * The underlying output stream to be filtered.
     */
    protected var out: OutputStream
) : OutputStream() {

    /**
     * Whether the stream is closed; implicitly initialized to false.
     */
    @Volatile
    private var closed = false

    /**
     * Writes the specified `byte` to this output stream.
     *
     *
     * The `write` method of `FilterOutputStream`
     * calls the `write` method of its underlying output stream,
     * that is, it performs `out.write(b)`.
     *
     *
     * Implements the abstract `write` method of `OutputStream`.
     *
     * @param      b   {@inheritDoc}
     * @throws     IOException  if an I/O error occurs.
     */
    @Throws(IOException::class)
    override fun write(b: Int) {
        out.write(b)
    }

    /**
     * Writes `b.length` bytes to this output stream.
     * @implSpec
     * The `write` method of `FilterOutputStream`
     * calls its `write` method of three arguments with the
     * arguments `b`, `0`, and
     * `b.length`.
     * @implNote
     * Note that this method does *not* call the one-argument
     * `write` method of its underlying output stream with
     * the single argument `b`.
     *
     * @param      b   the data to be written.
     * @throws     IOException  {@inheritDoc}
     * @see java.io.FilterOutputStream.write
     */
    @Throws(IOException::class)
    override fun write(b: ByteArray) {
        write(b, 0, b.size)
    }

    /**
     * Writes `len` bytes from the specified
     * `byte` array starting at offset `off` to
     * this output stream.
     * @implSpec
     * The `write` method of `FilterOutputStream`
     * calls the `write` method of one argument on each
     * `byte` to output.
     * @implNote
     * Note that this method does not call the `write` method
     * of its underlying output stream with the same arguments. Subclasses
     * of `FilterOutputStream` should provide a more efficient
     * implementation of this method.
     *
     * @param      b     {@inheritDoc}
     * @param      off   {@inheritDoc}
     * @param      len   {@inheritDoc}
     * @throws     IOException  if an I/O error occurs.
     * @throws     IndexOutOfBoundsException {@inheritDoc}
     * @see java.io.FilterOutputStream.write
     */
    @Throws(IOException::class)
    override fun write(b: ByteArray, off: Int, len: Int) {
        Objects.checkFromIndexSize(off, len, b.size)

        for (i in 0..<len) {
            write(b[off + i].toInt())
        }
    }

    /**
     * Flushes this output stream and forces any buffered output bytes
     * to be written out to the stream.
     * @implSpec
     * The `flush` method of `FilterOutputStream`
     * calls the `flush` method of its underlying output stream.
     *
     * @throws     IOException  {@inheritDoc}
     * @see java.io.FilterOutputStream.out
     */
    @Throws(IOException::class)
    override fun flush() {
        out.flush()
    }

    /**
     * Closes this output stream and releases any system resources
     * associated with the stream.
     * @implSpec
     * When not already closed, the `close` method of `FilterOutputStream` calls its `flush` method, and then
     * calls the `close` method of its underlying output stream.
     *
     * @throws     IOException  if an I/O error occurs.
     * @see java.io.FilterOutputStream.flush
     * @see java.io.FilterOutputStream.out
     */
    override fun close() {
        if (closed) {
            return
        }
        closed = true

        var flushException: Throwable? = null
        try {
            flush()
        } catch (e: Throwable) {
            flushException = e
            throw e
        } finally {
            if (flushException == null) {
                out.close()
            } else {
                try {
                    out.close()
                } catch (closeException: Throwable) {
                    if (flushException !== closeException) {
                        closeException.addSuppressed(flushException)
                    }
                    throw closeException
                }
            }
        }
    }
}
