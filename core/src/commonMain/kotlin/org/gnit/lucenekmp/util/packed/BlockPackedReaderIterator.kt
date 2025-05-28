package org.gnit.lucenekmp.util.packed


import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.util.BitUtil.zigZagDecode
import org.gnit.lucenekmp.util.LongsRef
import org.gnit.lucenekmp.util.packed.AbstractBlockPackedWriter.Companion.BPV_SHIFT
import org.gnit.lucenekmp.util.packed.AbstractBlockPackedWriter.Companion.MAX_BLOCK_SIZE
import org.gnit.lucenekmp.util.packed.AbstractBlockPackedWriter.Companion.MIN_BLOCK_SIZE
import org.gnit.lucenekmp.util.packed.AbstractBlockPackedWriter.Companion.MIN_VALUE_EQUALS_0
import org.gnit.lucenekmp.util.packed.PackedInts.checkBlockSize
import okio.EOFException
import okio.IOException
import org.gnit.lucenekmp.jdkport.Arrays
import kotlin.experimental.and
import kotlin.math.min

/**
 * Reader for sequences of longs written with [BlockPackedWriter].
 *
 * @see BlockPackedWriter
 *
 * @lucene.internal
 */
class BlockPackedReaderIterator(`in`: DataInput, packedIntsVersion: Int, blockSize: Int, valueCount: Long) {
    var `in`: DataInput? = null
    val packedIntsVersion: Int
    var valueCount: Long = 0
    val blockSize: Int
    val values: LongArray
    val valuesRef: LongsRef
    var blocks: ByteArray? = null
    var off: Int = 0
    var ord: Long = 0

    /**
     * Sole constructor.
     *
     * @param blockSize the number of values of a block, must be equal to the block size of the [     ] which has been used to write the stream
     */
    init {
        checkBlockSize(blockSize, MIN_BLOCK_SIZE, MAX_BLOCK_SIZE)
        this.packedIntsVersion = packedIntsVersion
        this.blockSize = blockSize
        this.values = LongArray(blockSize)
        this.valuesRef = LongsRef(this.values, 0, 0)
        reset(`in`, valueCount)
    }

    /**
     * Reset the current reader to wrap a stream of `valueCount` values contained in `
     * in`. The block size remains unchanged.
     */
    fun reset(`in`: DataInput, valueCount: Long) {
        this.`in` = `in`
        require(valueCount >= 0)
        this.valueCount = valueCount
        off = blockSize
        ord = 0
    }

    /** Skip exactly `count` values.  */
    @Throws(IOException::class)
    fun skip(count: Long) {
        var count = count
        require(count >= 0)
        if (ord + count > valueCount || ord + count < 0) {
            throw EOFException()
        }

        // 1. skip buffered values
        val skipBuffer = min(count, (blockSize - off).toLong()).toInt()
        off += skipBuffer
        ord += skipBuffer.toLong()
        count -= skipBuffer.toLong()
        if (count == 0L) {
            return
        }

        // 2. skip as many blocks as necessary
        require(off == blockSize)
        while (count >= blockSize) {
            val token: Int = (`in`!!.readByte() and 0xFF.toByte()).toInt()
            val bitsPerValue = token ushr BPV_SHIFT
            if (bitsPerValue > 64) {
                throw IOException("Corrupted")
            }
            if ((token and MIN_VALUE_EQUALS_0) == 0) {
                readVLong(`in`!!)
            }
            val blockBytes =
                PackedInts.Format.PACKED.byteCount(packedIntsVersion, blockSize, bitsPerValue)
            skipBytes(blockBytes)
            ord += blockSize.toLong()
            count -= blockSize.toLong()
        }
        if (count == 0L) {
            return
        }

        // 3. skip last values
        require(count < blockSize)
        refill()
        ord += count
        off += count.toInt()
    }

    @Throws(IOException::class)
    private fun skipBytes(count: Long) {
        if (`in` is IndexInput) {
            (`in` as IndexInput).seek((`in` as IndexInput).filePointer + count)
        } else {
            if (blocks == null) {
                blocks = ByteArray(blockSize)
            }
            var skipped: Long = 0
            while (skipped < count) {
                val toSkip = min(blocks!!.size.toLong(), count - skipped).toInt()
                `in`!!.readBytes(blocks!!, 0, toSkip)
                skipped += toSkip.toLong()
            }
        }
    }

    /** Read the next value.  */
    @Throws(IOException::class)
    fun next(): Long {
        if (ord == valueCount) {
            throw EOFException()
        }
        if (off == blockSize) {
            refill()
        }
        val value = values[off++]
        ++ord
        return value
    }

    /** Read between `1` and `count` values.  */
    @Throws(IOException::class)
    fun next(count: Int): LongsRef {
        var count = count
        require(count > 0)
        if (ord == valueCount) {
            throw EOFException()
        }
        if (off == blockSize) {
            refill()
        }

        count = min(count, blockSize - off)
        count = min(count.toLong(), valueCount - ord).toInt()

        valuesRef.offset = off
        valuesRef.length = count
        off += count
        ord += count.toLong()
        return valuesRef
    }

    @Throws(IOException::class)
    private fun refill() {
        val token: Int = (`in`!!.readByte() and 0xFF.toByte()).toInt()
        val minEquals0 = (token and MIN_VALUE_EQUALS_0) != 0
        val bitsPerValue = token ushr BPV_SHIFT
        if (bitsPerValue > 64) {
            throw IOException("Corrupted")
        }
        val minValue = if (minEquals0) 0L else zigZagDecode(1L + readVLong(`in`!!))
        require(minEquals0 || minValue != 0L)

        if (bitsPerValue == 0) {
            Arrays.fill(values, minValue)
        } else {
            val decoder =
                PackedInts.getDecoder(PackedInts.Format.PACKED, packedIntsVersion, bitsPerValue)
            val iterations = blockSize / decoder.byteValueCount()
            val blocksSize = iterations * decoder.byteBlockCount()
            if (blocks == null || blocks!!.size < blocksSize) {
                blocks = ByteArray(blocksSize)
            }

            val valueCount = min(this.valueCount - ord, blockSize.toLong()).toInt()
            val blocksCount =
                PackedInts.Format.PACKED.byteCount(packedIntsVersion, valueCount, bitsPerValue).toInt()
            `in`!!.readBytes(blocks!!, 0, blocksCount)

            decoder.decode(blocks!!, 0, values, 0, iterations)

            if (minValue != 0L) {
                for (i in 0..<valueCount) {
                    values[i] += minValue
                }
            }
        }
        off = 0
    }

    /** Return the offset of the next value to read.  */
    fun ord(): Long {
        return ord
    }

    companion object {
        // same as DataInput.readVLong but supports negative values
        @Throws(IOException::class)
        fun readVLong(`in`: DataInput): Long {
            var l = 0L
            var shift = 0
            while (shift < 56) {
                val b: Byte = `in`.readByte()
                l = l or ((b.toLong() and 0x7FL) shl shift)
                if (b >= 0) {
                    return l
                }
                shift += 7
            }
            return l or ((`in`.readByte() and 0xFFL.toByte()).toInt() shl 56).toLong()
        }
    }
}
