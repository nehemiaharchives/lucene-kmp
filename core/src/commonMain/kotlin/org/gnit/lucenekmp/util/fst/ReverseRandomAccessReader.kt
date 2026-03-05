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
        if (len == 0) {
            return
        }
        val currentPos = this.position
        val startPos = currentPos - len + 1
        `in`.readBytes(startPos, b, offset, len)
        var left = offset
        var right = offset + len - 1
        while (left < right) {
            val tmp = b[left]
            b[left] = b[right]
            b[right] = tmp
            left++
            right--
        }
        this.position = currentPos - len
    }

    override fun skipBytes(count: Long) {
        this.position -= count
    }
}
