package org.gnit.lucenekmp.util.packed

import kotlin.math.ceil
import org.gnit.lucenekmp.util.packed.PackedInts.Format.PACKED


/** Efficient sequential read/write of packed integers.  */
internal abstract class BulkOperation : PackedInts.Decoder, PackedInts.Encoder {
    protected fun writeLong(block: Long, blocks: ByteArray, blocksOffset: Int): Int {
        var blocksOffset = blocksOffset
        for (j in 1..8) {
            blocks[blocksOffset++] = (block ushr (64 - (j shl 3))).toByte()
        }
        return blocksOffset
    }

    /**
     * For every number of bits per value, there is a minimum number of blocks (b) / values (v) you
     * need to write in order to reach the next block boundary:
     *
     * <pre>
     * - 16 bits per value -&gt; b=2, v=1
     * - 24 bits per value -&gt; b=3, v=1
     * - 50 bits per value -&gt; b=25, v=4
     * - 63 bits per value -&gt; b=63, v=8
     * - ...
    </pre> *
     *
     * A bulk read consists in copying `iterations*v` values that are contained in `
     * iterations*b` blocks into a `long[]` (higher values of `iterations`
     * are likely to yield a better throughput): this requires n * (b + 8v) bytes of memory.
     *
     *
     * This method computes `iterations` as `ramBudget / (b + 8v)` (since a
     * long is 8 bytes).
     */
    fun computeIterations(valueCount: Int, ramBudget: Int): Int {
        val iterations = ramBudget / (byteBlockCount() + 8 * byteValueCount())
        if (iterations == 0) {
            // at least 1
            return 1
        } else if ((iterations - 1) * byteValueCount() >= valueCount) {
            // don't allocate for more than the size of the reader
            return ceil(valueCount.toDouble() / byteValueCount()).toInt()
        } else {
            return iterations
        }
    }

    companion object {
        private val packedBulkOps: Array<BulkOperation> = arrayOf<BulkOperation>(
            BulkOperationPacked1(),
            BulkOperationPacked2(),
            BulkOperationPacked3(),
            BulkOperationPacked4(),
            BulkOperationPacked5(),
            BulkOperationPacked6(),
            BulkOperationPacked7(),
            BulkOperationPacked8(),
            BulkOperationPacked9(),
            BulkOperationPacked10(),
            BulkOperationPacked11(),
            BulkOperationPacked12(),
            BulkOperationPacked13(),
            BulkOperationPacked14(),
            BulkOperationPacked15(),
            BulkOperationPacked16(),
            BulkOperationPacked17(),
            BulkOperationPacked18(),
            BulkOperationPacked19(),
            BulkOperationPacked20(),
            BulkOperationPacked21(),
            BulkOperationPacked22(),
            BulkOperationPacked23(),
            BulkOperationPacked24(),
            BulkOperationPacked(25),
            BulkOperationPacked(26),
            BulkOperationPacked(27),
            BulkOperationPacked(28),
            BulkOperationPacked(29),
            BulkOperationPacked(30),
            BulkOperationPacked(31),
            BulkOperationPacked(32),
            BulkOperationPacked(33),
            BulkOperationPacked(34),
            BulkOperationPacked(35),
            BulkOperationPacked(36),
            BulkOperationPacked(37),
            BulkOperationPacked(38),
            BulkOperationPacked(39),
            BulkOperationPacked(40),
            BulkOperationPacked(41),
            BulkOperationPacked(42),
            BulkOperationPacked(43),
            BulkOperationPacked(44),
            BulkOperationPacked(45),
            BulkOperationPacked(46),
            BulkOperationPacked(47),
            BulkOperationPacked(48),
            BulkOperationPacked(49),
            BulkOperationPacked(50),
            BulkOperationPacked(51),
            BulkOperationPacked(52),
            BulkOperationPacked(53),
            BulkOperationPacked(54),
            BulkOperationPacked(55),
            BulkOperationPacked(56),
            BulkOperationPacked(57),
            BulkOperationPacked(58),
            BulkOperationPacked(59),
            BulkOperationPacked(60),
            BulkOperationPacked(61),
            BulkOperationPacked(62),
            BulkOperationPacked(63),
            BulkOperationPacked(64),
        )

        // deprecated
        // NOTE: this is sparse (some entries are null):
        /*private val packedSingleBlockBulkOps = arrayOf<BulkOperation?>(
            BulkOperationPackedSingleBlock(1),
            BulkOperationPackedSingleBlock(2),
            BulkOperationPackedSingleBlock(3),
            BulkOperationPackedSingleBlock(4),
            BulkOperationPackedSingleBlock(5),
            BulkOperationPackedSingleBlock(6),
            BulkOperationPackedSingleBlock(7),
            BulkOperationPackedSingleBlock(8),
            BulkOperationPackedSingleBlock(9),
            BulkOperationPackedSingleBlock(10),
            null,
            BulkOperationPackedSingleBlock(12),
            null,
            null,
            null,
            BulkOperationPackedSingleBlock(16),
            null,
            null,
            null,
            null,
            BulkOperationPackedSingleBlock(21),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            BulkOperationPackedSingleBlock(32),
        )*/

        fun of(format: PackedInts.Format, bitsPerValue: Int): BulkOperation {
            when (format) {
                PACKED -> {
                    checkNotNull(packedBulkOps[bitsPerValue - 1])
                    return packedBulkOps[bitsPerValue - 1]
                }


                /*
                Deprecated;

                PACKED_SINGLE_BLOCK -> {
                    checkNotNull(packedSingleBlockBulkOps[bitsPerValue - 1])
                    return packedSingleBlockBulkOps[bitsPerValue - 1]
                }*/

                else -> throw AssertionError()
            }
        }
    }
}
