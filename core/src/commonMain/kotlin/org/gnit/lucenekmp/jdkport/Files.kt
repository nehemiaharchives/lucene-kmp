package org.gnit.lucenekmp.jdkport

import kotlinx.io.EOFException
import kotlinx.io.IOException
import kotlinx.io.Source
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
