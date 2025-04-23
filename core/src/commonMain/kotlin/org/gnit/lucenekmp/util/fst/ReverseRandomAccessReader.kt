package org.gnit.lucenekmp.util.fst


import org.gnit.lucenekmp.util.fst.FST.BytesReader
import org.gnit.lucenekmp.store.RandomAccessInput
import kotlinx.io.IOException

/** Implements reverse read from a RandomAccessInput.  */
internal class ReverseRandomAccessReader(private val `in`: RandomAccessInput) : BytesReader() {
    var position: Long = 0

    override fun getPosition(): Long {
        return position
    }

    override fun setPosition(position: Long) {
        this.position = position
    }

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
