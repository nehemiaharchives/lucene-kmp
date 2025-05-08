package org.gnit.lucenekmp.jdkport

import kotlinx.io.Buffer
import kotlinx.io.EOFException
import kotlinx.io.IOException
import kotlinx.io.Source

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
     * @throws     kotlinx.io.IOException  if an I/O error occurs.
     */
    @Throws(IOException::class)
    override fun read(): Int{
        return try {
            source.readByte().toInt()
        }catch (e: EOFException) {
            -1
        }
    }

    /**
     * Report how many bytes are still available without blocking.
     * If the underlying Source is a Buffer, we can peek at its .size.
     * Otherwise we return 0.
     */
    @Throws(IOException::class)
    override fun available(): Int {
        return (source as? Buffer)?.size?.toInt() ?: 0
    }

}

