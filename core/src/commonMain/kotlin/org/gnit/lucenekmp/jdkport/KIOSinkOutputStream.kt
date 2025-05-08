package org.gnit.lucenekmp.jdkport

import kotlinx.io.IOException
import kotlinx.io.Sink

/**
 * A OutputStream implementation which use kotlinx.io.Sink
 */
class KIOSinkOutputStream(val sink: Sink) : OutputStream() {

    /**
     * Writes the specified byte to this output stream. The general contract for
     * `write` is that one byte is written to the output stream. The
     * byte to be written is the low eight bits of the argument `b`. The
     * 24 high-order bits of `b` are ignored.
     *
     * @param      b   the `byte`.
     * @throws     kotlinx.io.IOException  if an I/O error occurs.
     */
    @Throws(IOException::class)
    override fun write(b: Int) {
        sink.writeByte(b.toByte())
    }
}