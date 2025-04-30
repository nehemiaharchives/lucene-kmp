package org.gnit.lucenekmp.util.packed

import org.gnit.lucenekmp.util.packed.PackedInts.MutableImpl
import org.gnit.lucenekmp.util.RamUsageEstimator


/**
 * Space optimized random access capable array of values with a fixed number of bits/value. Values
 * are packed contiguously.
 *
 *
 * The implementation strives to perform as fast as possible under the constraint of contiguous
 * bits, by avoiding expensive operations. This comes at the cost of code clarity.
 *
 *
 * Technical details: This implementation is a refinement of a non-branching version. The
 * non-branching get and set methods meant that 2 or 4 atomics in the underlying array were always
 * accessed, even for the cases where only 1 or 2 were needed. Even with caching, this had a
 * detrimental effect on performance. Related to this issue, the old implementation used lookup
 * tables for shifts and masks, which also proved to be a bit slower than calculating the shifts and
 * masks on the fly. See https://issues.apache.org/jira/browse/LUCENE-4062 for details.
 */
internal class Packed64(valueCount: Int, bitsPerValue: Int) : MutableImpl(valueCount, bitsPerValue) {
    /** Values are stores contiguously in the blocks array.  */
    private val blocks: LongArray

    /** A right-aligned mask of width BitsPerValue used by [.get].  */
    private val maskRight: Long

    /** Optimization: Saves one lookup in [.get].  */
    private val bpvMinusBlockSize: Int

    /**
     * Creates an array with the internal structures adjusted for the given limits and initialized to
     * 0.
     *
     * @param valueCount the number of elements.
     * @param bitsPerValue the number of bits available for any given value.
     */
    init {
        val format = PackedInts.Format.PACKED
        val longCount = format.longCount(PackedInts.VERSION_CURRENT, valueCount, bitsPerValue)
        this.blocks = LongArray(longCount)
        maskRight = 0L.inv() shl (BLOCK_SIZE - bitsPerValue) ushr (BLOCK_SIZE - bitsPerValue)
        bpvMinusBlockSize = bitsPerValue - BLOCK_SIZE
    }

    /**
     * @param index the position of the value.
     * @return the value at the given index.
     */
    public override fun get(index: Int): Long {
        // The abstract index in a bit stream
        val majorBitPos: Long = index.toLong() * bitsPerValue
        // The index in the backing long-array
        val elementPos = (majorBitPos ushr BLOCK_BITS).toInt()
        // The number of value-bits in the second long
        val endBits = (majorBitPos and MOD_MASK.toLong()) + bpvMinusBlockSize

        if (endBits <= 0) { // Single block
            return (blocks[elementPos] ushr -endBits.toInt()) and maskRight
        }
        // Two blocks
        return (((blocks[elementPos] shl endBits.toInt()) or (blocks[elementPos + 1] ushr (BLOCK_SIZE - endBits).toInt()))
                and maskRight)
    }

    public override fun get(index: Int, arr: LongArray, off: Int, len: Int): Int {
        var index = index
        var off = off
        var len = len
        require(len > 0) { "len must be > 0 (got $len)" }
        require(index >= 0 && index < valueCount)
        len = kotlin.math.min(len, valueCount - index)
        require(off + len <= arr.size)

        val originalIndex = index
        val decoder: PackedInts.Decoder = BulkOperation.of(PackedInts.Format.PACKED, bitsPerValue)

        // go to the next block where the value does not span across two blocks
        val offsetInBlocks = index % decoder.longValueCount()
        if (offsetInBlocks != 0) {
            var i = offsetInBlocks
            while (i < decoder.longValueCount() && len > 0) {
                arr[off++] = get(index++)
                --len
                ++i
            }
            if (len == 0) {
                return index - originalIndex
            }
        }

        // bulk get
        require(index % decoder.longValueCount() == 0)
        val blockIndex = ((index.toLong() * bitsPerValue) ushr BLOCK_BITS).toInt()
        require(((index.toLong() * bitsPerValue) and MOD_MASK.toLong()) == 0L)
        val iterations = len / decoder.longValueCount()
        decoder.decode(blocks, blockIndex, arr, off, iterations)
        val gotValues = iterations * decoder.longValueCount()
        index += gotValues
        len -= gotValues
        require(len >= 0)

        if (index > originalIndex) {
            // stay at the block boundary
            return index - originalIndex
        } else {
            // no progress so far => already at a block boundary but no full block to get
            require(index == originalIndex)
            return super.get(index, arr, off, len)
        }
    }

    public override fun set(index: Int, value: Long) {
        // The abstract index in a contiguous bit stream
        val majorBitPos: Long = index.toLong() * bitsPerValue
        // The index in the backing long-array
        val elementPos = (majorBitPos ushr BLOCK_BITS).toInt() // / BLOCK_SIZE
        // The number of value-bits in the second long
        val endBits = (majorBitPos and MOD_MASK.toLong()) + bpvMinusBlockSize

        if (endBits <= 0) { // Single block
            blocks[elementPos] =
                blocks[elementPos] and (maskRight shl -endBits.toInt()).inv() or (value shl -endBits.toInt())
            return
        }
        // Two blocks
        blocks[elementPos] =
            blocks[elementPos] and (maskRight ushr endBits.toInt()).inv() or (value ushr endBits.toInt())
        blocks[elementPos + 1] =
            blocks[elementPos + 1] and (0L.inv() ushr endBits.toInt()) or (value shl (BLOCK_SIZE - endBits).toInt())
    }

    override fun set(index: Int, arr: LongArray, off: Int, len: Int): Int {
        var index = index
        var off = off
        var len = len
        require(len > 0) { "len must be > 0 (got $len)" }
        require(index >= 0 && index < valueCount)
        len = kotlin.math.min(len, valueCount - index)
        require(off + len <= arr.size)

        val originalIndex = index
        val encoder: PackedInts.Encoder = BulkOperation.of(PackedInts.Format.PACKED, bitsPerValue)

        // go to the next block where the value does not span across two blocks
        val offsetInBlocks = index % encoder.longValueCount()
        if (offsetInBlocks != 0) {
            var i = offsetInBlocks
            while (i < encoder.longValueCount() && len > 0) {
                set(index++, arr[off++])
                --len
                ++i
            }
            if (len == 0) {
                return index - originalIndex
            }
        }

        // bulk set
        require(index % encoder.longValueCount() == 0)
        val blockIndex = ((index.toLong() * bitsPerValue) ushr BLOCK_BITS).toInt()
        require(((index.toLong() * bitsPerValue) and MOD_MASK.toLong()) == 0L)
        val iterations = len / encoder.longValueCount()
        encoder.encode(arr, off, blocks, blockIndex, iterations)
        val setValues = iterations * encoder.longValueCount()
        index += setValues
        len -= setValues
        require(len >= 0)

        if (index > originalIndex) {
            // stay at the block boundary
            return index - originalIndex
        } else {
            // no progress so far => already at a block boundary but no full block to get
            require(index == originalIndex)
            return super.set(index, arr, off, len)
        }
    }

    override fun toString(): String {
        return ("Packed64(bitsPerValue="
                + bitsPerValue
                + ",size="
                + size()
                + ",blocks="
                + blocks.size
                + ")")
    }

    override fun ramBytesUsed(): Long {
        return (RamUsageEstimator.alignObjectSize(
            (RamUsageEstimator.NUM_BYTES_OBJECT_HEADER
                    + 3 * Int.SIZE_BYTES // bpvMinusBlockSize,valueCount,bitsPerValue
                    + Long.SIZE_BYTES // maskRight
                    + RamUsageEstimator.NUM_BYTES_OBJECT_REF).toLong()
        ) // blocks ref
                + RamUsageEstimator.sizeOf(blocks))
    }

    override fun fill(fromIndex: Int, toIndex: Int, `val`: Long) {
        var fromIndex = fromIndex
        require(PackedInts.unsignedBitsRequired(`val`) <= bitsPerValue)
        require(fromIndex <= toIndex)

        // minimum number of values that use an exact number of full blocks
        val nAlignedValues = 64 / gcd(64, bitsPerValue)
        val span = toIndex - fromIndex
        if (span <= 3 * nAlignedValues) {
            // there needs be at least 2 * nAlignedValues aligned values for the
            // block approach to be worth trying
            super.fill(fromIndex, toIndex, `val`)
            return
        }

        // fill the first values naively until the next block start
        val fromIndexModNAlignedValues = fromIndex % nAlignedValues
        if (fromIndexModNAlignedValues != 0) {
            for (i in fromIndexModNAlignedValues..<nAlignedValues) {
                set(fromIndex++, `val`)
            }
        }
        require(fromIndex % nAlignedValues == 0)

        // compute the long[] blocks for nAlignedValues consecutive values and
        // use them to set as many values as possible without applying any mask
        // or shift
        val nAlignedBlocks: Int = (nAlignedValues * bitsPerValue) shr 6
        val nAlignedValuesBlocks: LongArray
        run {
            val values = Packed64(nAlignedValues, bitsPerValue)
            for (i in 0..<nAlignedValues) {
                values.set(i, `val`)
            }
            nAlignedValuesBlocks = values.blocks
            require(nAlignedBlocks <= nAlignedValuesBlocks.size)
        }
        val startBlock = ((fromIndex.toLong() * bitsPerValue) ushr 6) as Int
        val endBlock = ((toIndex.toLong() * bitsPerValue) ushr 6) as Int
        for (block in startBlock..<endBlock) {
            val blockValue = nAlignedValuesBlocks[block % nAlignedBlocks]
            blocks[block] = blockValue
        }

        // fill the gap
        for (i in ((endBlock.toLong() shl 6) / bitsPerValue) as Int..<toIndex) {
            set(i, `val`)
        }
    }

    override fun clear() {
        /*java.util.Arrays.fill(blocks, 0L)*/
        blocks.fill(0L)
    }

    companion object {
        const val BLOCK_SIZE: Int = 64 // 32 = int, 64 = long
        const val BLOCK_BITS: Int = 6 // The #bits representing BLOCK_SIZE
        const val MOD_MASK: Int = BLOCK_SIZE - 1 // x % BLOCK_SIZE

        private fun gcd(a: Int, b: Int): Int {
            if (a < b) {
                return gcd(b, a)
            } else if (b == 0) {
                return a
            } else {
                return gcd(b, a % b)
            }
        }
    }
}
