package org.gnit.lucenekmp.util.fst


import org.gnit.lucenekmp.util.fst.FST.BytesReader
import org.gnit.lucenekmp.util.ByteBlockPool
import kotlinx.io.IOException

/** Reads in reverse from a ByteBlockPool.  */
internal class ByteBlockPoolReverseBytesReader(private val buf: ByteBlockPool) : BytesReader() {

    // the difference between the FST node address and the hash table copied node address
    private var posDelta: Long = 0
    private var pos: Long = 0

    override fun readByte(): Byte {
        return buf.readByte(pos--)
    }

    override fun readBytes(b: ByteArray, offset: Int, len: Int) {
        for (i in 0..<len) {
            b[offset + i] = buf.readByte(pos--)
        }
    }

    @Throws(IOException::class)
    override fun skipBytes(numBytes: Long) {
        pos -= numBytes
    }

    override var position: Long
        get() = pos + posDelta
        set(pos) {
            this.pos = pos - posDelta
        }

    fun setPosDelta(posDelta: Long) {
        this.posDelta = posDelta
    }
}
