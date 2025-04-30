package org.gnit.lucenekmp.store

import org.gnit.lucenekmp.util.BitUtil
import org.gnit.lucenekmp.util.BytesRef
import kotlin.jvm.JvmOverloads

/**
 * DataOutput backed by a byte array. **WARNING:** This class omits most low-level checks, so be
 * sure to test heavily with assertions enabled.
 *
 * @lucene.experimental
 */
class ByteArrayDataOutput : DataOutput {
    private lateinit var bytes: ByteArray

    var position: Int = 0
        private set
    private var limit = 0

    constructor(bytes: ByteArray) {
        reset(bytes)
    }

    constructor(bytes: ByteArray, offset: Int, len: Int) {
        reset(bytes, offset, len)
    }

    constructor() {
        reset(BytesRef.EMPTY_BYTES)
    }

    @JvmOverloads
    fun reset(bytes: ByteArray, offset: Int = 0, len: Int = bytes.size) {
        this.bytes = bytes
        this.position = offset
        limit = offset + len
    }

    override fun writeByte(b: Byte) {
        require(this.position < limit)
        bytes[this.position++] = b
    }

    override fun writeBytes(b: ByteArray, offset: Int, length: Int) {
        require(this.position + length <= limit)
        /*java.lang.System.arraycopy(b, offset, bytes, this.position, length)*/
        b.copyInto(
            destination = bytes,
            destinationOffset = this.position,
            startIndex = offset,
            endIndex = offset + length
        )
        this.position += length
    }

    override fun writeShort(i: Short) {
        require(this.position + Short.SIZE_BYTES <= limit)
        BitUtil.VH_LE_SHORT.set(bytes, this.position, i)
        this.position += Short.SIZE_BYTES
    }

    override fun writeInt(i: Int) {
        require(this.position + Int.SIZE_BYTES <= limit)
        BitUtil.VH_LE_INT.set(bytes, this.position, i)
        this.position += Int.SIZE_BYTES
    }

    override fun writeLong(i: Long) {
        require(this.position + Long.SIZE_BYTES <= limit)
        BitUtil.VH_LE_LONG.set(bytes, this.position, i)
        this.position += Long.SIZE_BYTES
    }
}
