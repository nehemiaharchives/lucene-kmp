package org.gnit.lucenekmp.util.fst

import org.gnit.lucenekmp.util.fst.FST.BytesReader


/** Reads in reverse from a single byte[].  */
internal class ReverseBytesReader(private val bytes: ByteArray) : BytesReader() {
    private var pos = 0

    override fun readByte(): Byte {
        return bytes[pos--]
    }

    override fun readBytes(b: ByteArray, offset: Int, len: Int) {
        for (i in 0..<len) {
            b[offset + i] = bytes[pos--]
        }
    }

    override fun skipBytes(count: Long) {
        pos -= count.toInt()
    }

    override var position: Long
        get() = pos.toLong()
        set(pos) {
            this.pos = pos.toInt()
        }
}
