package org.gnit.lucenekmp.util.packed

import okio.IOException
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.floatToIntBits
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.util.BitUtil
import kotlin.math.max

/**
 * A writer for large monotonically increasing sequences of positive longs.
 *
 *
 * The sequence is divided into fixed-size blocks and for each block, values are modeled after a
 * linear function f: x  A  x + B. The block encodes deltas from the expected values
 * computed from this function using as few bits as possible.
 *
 *
 * Format:
 *
 *
 *  * &lt;BLock&gt;<sup>BlockCount</sup>
 *  * BlockCount:  ValueCount / BlockSize
 *  * Block: &lt;Header, (Ints)&gt;
 *  * Header: &lt;B, A, BitsPerValue&gt;
 *  * B: the B from f: x  A  x + B using a [zig-zag][BitUtil.zigZagEncode] [vLong][DataOutput.writeVLong]
 *  * A: the A from f: x  A  x + B encoded using [Float.floatToIntBits]
 * on [4 bytes][DataOutput.writeInt]
 *  * BitsPerValue: a [variable-length int][DataOutput.writeVInt]
 *  * Ints: if BitsPerValue is `0`, then there is nothing to read and all values
 * perfectly match the result of the function. Otherwise, these are the [       packed][PackedInts] deltas from the expected value (computed from the function) using exactly
 * BitsPerValue bits per value.
 *
 *
 * @see MonotonicBlockPackedReader
 *
 * @lucene.internal
 */
class MonotonicBlockPackedWriter
    /**
     * Sole constructor.
     *
     * @param blockSize the number of values of a single block, must be a power of 2
     */
    (out: DataOutput, blockSize: Int) :
    AbstractBlockPackedWriter(out, blockSize) {
    @Throws(IOException::class)
    override fun add(l: Long) {
        assert(l >= 0)
        super.add(l)
    }

    @Throws(IOException::class)
    override fun flush() {
        assert(off > 0)

        val avg = if (off == 1) 0f else (values[off - 1] - values[0]).toFloat() / (off - 1)
        var min: Long = values[0]
        // adjust min so that all deltas will be positive
        for (i in 1..<off) {
            val actual: Long = values[i]
            val expected: Long =
                MonotonicBlockPackedReader.expected(min, avg, i)
            if (expected > actual) {
                min -= (expected - actual)
            }
        }

        var maxDelta: Long = 0
        for (i in 0..<off) {
            values[i] =
                values[i] - MonotonicBlockPackedReader.expected(
                    min,
                    avg,
                    i
                )
            maxDelta = max(maxDelta, values[i])
        }

        out!!.writeZLong(min)
        out!!.writeInt(Float.floatToIntBits(avg))
        if (maxDelta == 0L) {
            out!!.writeVInt(0)
        } else {
            val bitsRequired: Int = PackedInts.bitsRequired(maxDelta)
            out!!.writeVInt(bitsRequired)
            writeValues(bitsRequired)
        }

        off = 0
    }
}
