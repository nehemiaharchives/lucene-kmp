package org.gnit.lucenekmp.util.packed

import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.util.BitUtil.zigZagEncode
import okio.IOException
import kotlin.math.max
import kotlin.math.min

/**
 * A writer for large sequences of longs.
 *
 *
 * The sequence is divided into fixed-size blocks and for each block, the difference between each
 * value and the minimum value of the block is encoded using as few bits as possible. Memory usage
 * of this class is proportional to the block size. Each block has an overhead between 1 and 10
 * bytes to store the minimum value and the number of bits per value of the block.
 *
 *
 * Format:
 *
 *
 *  * &lt;BLock&gt;<sup>BlockCount</sup>
 *  * BlockCount:  ValueCount / BlockSize
 *  * Block: &lt;Header, (Ints)&gt;
 *  * Header: &lt;Token, (MinValue)&gt;
 *  * Token: a [byte][DataOutput.writeByte], first 7 bits are the number of bits per
 * value (`bitsPerValue`). If the 8th bit is 1, then MinValue (see next) is `0
` * , otherwise MinValue and needs to be decoded
 *  * MinValue: a [zigzag-encoded](https://developers.google.com/protocol-buffers/docs/encoding#types)
 * [variable-length long][DataOutput.writeVLong] whose value should be added to
 * every int from the block to restore the original values
 *  * Ints: If the number of bits per value is `0`, then there is nothing to decode
 * and all ints are equal to MinValue. Otherwise: BlockSize [packed ints][PackedInts]
 * encoded on exactly `bitsPerValue` bits per value. They are the subtraction of
 * the original values and MinValue
 *
 *
 * @see BlockPackedReaderIterator
 *
 * @lucene.internal
 */
class BlockPackedWriter
/**
 * Sole constructor.
 *
 * @param blockSize the number of values of a single block, must be a power of 2
 */
    (out: DataOutput, blockSize: Int) : AbstractBlockPackedWriter(out, blockSize) {
    @Throws(IOException::class)
    override fun flush() {
        require(off > 0)
        var min = Long.Companion.MAX_VALUE
        var max = Long.Companion.MIN_VALUE
        for (i in 0..<off) {
            min = min(values[i], min)
            max = max(values[i], max)
        }

        val delta = max - min
        val bitsRequired = if (delta == 0L) 0 else PackedInts.unsignedBitsRequired(delta)
        if (bitsRequired == 64) {
            // no need to delta-encode
            min = 0L
        } else if (min > 0L) {
            // make min as small as possible so that writeVLong requires fewer bytes
            min = max(0L, max - PackedInts.maxValue(bitsRequired))
        }

        val token = (bitsRequired shl BPV_SHIFT) or (if (min == 0L) MIN_VALUE_EQUALS_0 else 0)
        out!!.writeByte(token.toByte())

        if (min != 0L) {
            writeVLong(out!!, zigZagEncode(min) - 1)
        }

        if (bitsRequired > 0) {
            if (min != 0L) {
                for (i in 0..<off) {
                    values[i] -= min
                }
            }
            writeValues(bitsRequired)
        }

        off = 0
    }
}
