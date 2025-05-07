package org.gnit.lucenekmp.store

import kotlinx.io.EOFException
import kotlinx.io.IOException
import org.gnit.lucenekmp.jdkport.InputStream


/** A [DataInput] wrapping a plain [InputStream].  */
class InputStreamDataInput(private val `is`: InputStream) : DataInput(), AutoCloseable {

    @Throws(IOException::class)
    override fun readByte(): Byte {
        val v: Int = `is`.read()
        if (v == -1) throw EOFException()
        return v.toByte()
    }

    @Throws(IOException::class)
    override fun readBytes(b: ByteArray, offset: Int, len: Int) {
        var offset = offset
        var len = len
        while (len > 0) {
            val cnt: Int = `is`.read(b, offset, len)
            if (cnt < 0) {
                // Partially read the input, but no more data available in the stream.
                throw EOFException()
            }
            len -= cnt
            offset += cnt
        }
    }

    override fun close() {
        `is`.close()
    }

    @Throws(IOException::class)
    override fun skipBytes(numBytes: Long) {
        require(numBytes >= 0) { "numBytes must be >= 0, got $numBytes" }
        val skipped: Long = `is`.skip(numBytes)
        require(skipped <= numBytes)
        if (skipped < numBytes) {
            throw EOFException()
        }
    }
}
