package org.gnit.lucenekmp.util.fst

import org.gnit.lucenekmp.store.RandomAccessInput
import okio.IOException

/** Implements reverse read from a RandomAccessInput.  */
class ReverseRandomAccessReader(private val `in`: RandomAccessInput) : FST.BytesReader() {
    override var position: Long = 0

    @Throws(IOException::class)
    override fun readByte(): Byte {
        return `in`.readByte(this.position--)
    }

    @Throws(IOException::class)
    override fun readBytes(b: ByteArray, offset: Int, len: Int) {
        var i = offset
        val end = offset + len
        while (i < end) {
            b[i++] = `in`.readByte(this.position--)
        }
    }

    override fun skipBytes(count: Long) {
        this.position -= count
    }
}
