package org.gnit.lucenekmp.store

import okio.IOException
import org.gnit.lucenekmp.util.BitUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.getIntLE
import org.gnit.lucenekmp.util.getLongLE
import org.gnit.lucenekmp.util.getShortLE
import kotlin.jvm.JvmOverloads


/**
 * DataInput backed by a byte array. **WARNING:** This class omits all low-level checks.
 *
 * @lucene.experimental
 */
class ByteArrayDataInput : DataInput {
    private lateinit var bytes: ByteArray

    var position: Int = 0
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

    // NOTE: sets pos to 0, which is not right if you had
    // called reset w/ non-zero offset!!
    fun rewind() {
        this.position = 0
    }

    @JvmOverloads
    fun reset(bytes: ByteArray, offset: Int = 0, len: Int = bytes.size) {
        this.bytes = bytes
        this.position = offset
        limit = offset + len
    }

    fun length(): Int {
        return limit
    }

    fun eof(): Boolean {
        return this.position == limit
    }

    override fun skipBytes(count: Long) {
        this.position += count.toInt()
    }

    override fun readShort(): Short {
        /*try {
            return BitUtil.VH_LE_SHORT.get(bytes, this.position) as Short
        } finally {
            this.position += java.lang.Short.BYTES
        }*/

        val result = bytes.getShortLE(this.position)
        this.position += Short.SIZE_BYTES // Advance by the size of a short
        return result
    }

    override fun readInt(): Int {
        /*try {
            return BitUtil.VH_LE_INT.get(bytes, this.position) as Int
        } finally {
            this.position += java.lang.Integer.BYTES
        }*/

        val result = bytes.getIntLE(this.position)
        this.position += Int.SIZE_BYTES // which is 4
        return result
    }

    override fun readLong(): Long {
        /*try {
            return BitUtil.VH_LE_LONG.get(bytes, this.position) as Long
        } finally {
            this.position += java.lang.Long.BYTES
        }*/

        val result = bytes.getLongLE(this.position)
        this.position += Long.SIZE_BYTES  // Long.SIZE_BYTES equals 8
        return result
    }

    override fun readVInt(): Int {
        try {
            return super.readVInt()
        } catch (e: IOException) {
            throw AssertionError("ByteArrayDataInput#readByte should not throw IOException")
        }
    }

    override fun readVLong(): Long {
        try {
            return super.readVLong()
        } catch (e: IOException) {
            throw AssertionError("ByteArrayDataInput#readByte should not throw IOException")
        }
    }

    // NOTE: AIOOBE not EOF if you read too much
    override fun readByte(): Byte {
        return bytes[this.position++]
    }

    // NOTE: AIOOBE not EOF if you read too much
    override fun readBytes(b: ByteArray, offset: Int, len: Int) {
        /*java.lang.System.arraycopy(bytes, this.position, b, offset, len)*/
        b.copyInto(
            destination = b,
            destinationOffset = offset,
            startIndex = this.position,
            endIndex = this.position + len
        )
        this.position += len
    }
}
