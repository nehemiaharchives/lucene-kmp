package org.gnit.lucenekmp.store

import org.gnit.lucenekmp.jdkport.OutputStream
import okio.IOException


/** A [DataOutput] wrapping a plain [OutputStream].  */
class OutputStreamDataOutput(private val os: OutputStream) : DataOutput(), AutoCloseable {

    @Throws(IOException::class)
    override fun writeByte(b: Byte) {
        os.write(b.toInt())
    }

    @Throws(IOException::class)
    override fun writeBytes(b: ByteArray, offset: Int, length: Int) {
        os.write(b, offset, length)
    }

    override fun close() {
        os.close()
    }
}
