package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.util.BitUtil
import org.gnit.lucenekmp.util.ByteBlockPool

/**
 * IndexInput that knows how to read the byte slices written by Posting and PostingVector. We read
 * the bytes in each slice until we hit the end of that slice at which point we read the forwarding
 * address of the next slice and then jump to it.
 */
internal class ByteSliceReader : DataInput() {
    var pool: ByteBlockPool? = null
    var bufferUpto: Int = 0
    lateinit var buffer: ByteArray
    var upto: Int = 0
    var limit: Int = 0
    var level: Int = 0
    var bufferOffset: Int = 0

    var endIndex: Int = 0

    fun init(pool: ByteBlockPool, startIndex: Int, endIndex: Int) {
        assert(endIndex - startIndex >= 0)
        assert(startIndex >= 0)
        assert(endIndex >= 0)

        this.pool = pool
        this.endIndex = endIndex

        level = 0
        bufferUpto = startIndex / ByteBlockPool.BYTE_BLOCK_SIZE
        bufferOffset = bufferUpto * ByteBlockPool.BYTE_BLOCK_SIZE
        buffer = pool.getBuffer(bufferUpto)
        upto = startIndex and ByteBlockPool.BYTE_BLOCK_MASK

        val firstSize: Int = ByteSlicePool.LEVEL_SIZE_ARRAY[0]

        if (startIndex + firstSize >= endIndex) {
            // There is only this one slice to read
            limit = endIndex and ByteBlockPool.BYTE_BLOCK_MASK
        } else limit = upto + firstSize - 4
    }

    fun eof(): Boolean {
        assert(upto + bufferOffset <= endIndex)
        return upto + bufferOffset == endIndex
    }

    override fun readByte(): Byte {
        assert(!eof())
        assert(upto <= limit)
        if (upto == limit) nextSlice()
        return buffer[upto++]
    }

    @Throws(IOException::class)
    fun writeTo(out: DataOutput): Long {
        var size: Long = 0
        while (true) {
            if (limit + bufferOffset == endIndex) {
                assert(endIndex - bufferOffset >= upto)
                out.writeBytes(buffer, upto, limit - upto)
                size += (limit - upto).toLong()
                break
            } else {
                out.writeBytes(buffer, upto, limit - upto)
                size += (limit - upto).toLong()
                nextSlice()
            }
        }

        return size
    }

    fun nextSlice() {
        // Skip to our next slice

        val nextIndex = BitUtil.VH_LE_INT.get(buffer, limit)

        level = ByteSlicePool.NEXT_LEVEL_ARRAY[level]
        val newSize: Int = ByteSlicePool.LEVEL_SIZE_ARRAY[level]

        bufferUpto = nextIndex / ByteBlockPool.BYTE_BLOCK_SIZE
        bufferOffset = bufferUpto * ByteBlockPool.BYTE_BLOCK_SIZE

        buffer = pool!!.getBuffer(bufferUpto)
        upto = nextIndex and ByteBlockPool.BYTE_BLOCK_MASK

        if (nextIndex + newSize >= endIndex) {
            // We are advancing to the final slice
            assert(endIndex - nextIndex > 0)
            limit = endIndex - bufferOffset
        } else {
            // This is not the final slice (subtract 4 for the
            // forwarding address at the end of this new slice)
            limit = upto + newSize - 4
        }
    }

    override fun readBytes(b: ByteArray, offset: Int, len: Int) {
        var offset = offset
        var len = len
        while (len > 0) {
            val numLeft = limit - upto
            if (numLeft < len) {
                // Read entire slice
                System.arraycopy(buffer, upto, b, offset, numLeft)
                offset += numLeft
                len -= numLeft
                nextSlice()
            } else {
                // This slice is the last one
                System.arraycopy(buffer, upto, b, offset, len)
                upto += len
                break
            }
        }
    }

    override fun skipBytes(numBytes: Long) {
        var numBytes = numBytes
        require(numBytes >= 0) { "numBytes must be >= 0, got $numBytes" }
        while (numBytes > 0) {
            val numLeft = limit - upto
            if (numLeft < numBytes) {
                numBytes -= numLeft.toLong()
                nextSlice()
            } else {
                upto += numBytes.toInt()
                break
            }
        }
    }
}
