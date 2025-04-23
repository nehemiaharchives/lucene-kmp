package org.gnit.lucenekmp.jdkport

import kotlinx.io.EOFException
import kotlinx.io.IOException
import kotlinx.io.Source
import kotlinx.io.Sink
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/**
 * port of java.nio.file.Files
 */
object Files {
    fun newInputStream(path: Path): InputStream {
        SystemFileSystem.source(path).use { source ->
            return KIOSourceInputStream(source.buffered())
        }
    }

    fun newOutputStream(path: Path): OutputStream {
        SystemFileSystem.sink(path).use { sink ->
            return KIOSinkOutputStream(sink.buffered())
        }
    }
}

/**
 * A InputStream implementation which use kotlinx.io.Source
 */
class KIOSourceInputStream(val source: Source) : InputStream() {

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
    override fun read(): Int{
        return try {
            source.readByte().toInt()
        }catch (e: EOFException) {
            -1
        }
    }
}

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
     * @throws     IOException  if an I/O error occurs.
     */
    @Throws(IOException::class)
    override fun write(b: Int) {
        sink.writeByte(b.toByte())
    }
}
