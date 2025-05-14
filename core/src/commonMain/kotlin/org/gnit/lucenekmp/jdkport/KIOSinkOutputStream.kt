package org.gnit.lucenekmp.jdkport

import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.Sink

/**
 * A OutputStream implementation which uses either kotlinx.io.Sink or kotlinx.io.Buffer
 */
class KIOSinkOutputStream : OutputStream {
    private val sink: Sink?
    private val buffer: Buffer?
    private var closed = false

    /**
     * Constructs a KIOSinkOutputStream that writes to the given Sink.
     *
     * @param sink the Sink to write to
     */
    constructor(sink: Sink) {
        this.sink = sink
        this.buffer = null
    }

    /**
     * Constructs a KIOSinkOutputStream that writes to the given Buffer.
     *
     * @param buffer the Buffer to write to
     */
    constructor(buffer: Buffer) {
        this.sink = null
        this.buffer = buffer
    }

    /**
     * Writes the specified byte to this output stream. The general contract for
     * `write` is that one byte is written to the output stream. The
     * byte to be written is the low eight bits of the argument `b`. The
     * 24 high-order bits of `b` are ignored.
     *
     * @param      b   the `byte`.
     * @throws     kotlinx.io.IOException  if an I/O error occurs or if this stream has been closed.
     */
    @Throws(IOException::class)
    override fun write(b: Int) {
        if (closed) {
            throw IOException("Stream closed")
        }

        if (sink != null) {
            sink.writeByte(b.toByte())
        } else if (buffer != null) {
            buffer.writeByte(b.toByte())
        }
    }

    /**
     * Flushes this output stream and forces any buffered output bytes
     * to be written out.
     *
     * @throws     kotlinx.io.IOException  if an I/O error occurs or if this stream has been closed.
     */
    @Throws(IOException::class)
    override fun flush() {
        if (closed) {
            throw IOException("Stream closed")
        }

        sink?.flush()
        // Buffer doesn't need to be flushed
    }

    /**
     * Closes this output stream and releases any system resources
     * associated with this stream.
     *
     * @throws     kotlinx.io.IOException  if an I/O error occurs.
     */
    override fun close() {
        if (!closed) {
            closed = true
            sink?.close()
            // Buffer doesn't need to be closed
        }
    }
}
