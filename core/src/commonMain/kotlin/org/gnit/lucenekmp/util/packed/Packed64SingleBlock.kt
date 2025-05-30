package org.gnit.lucenekmp.util.packed

import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.util.RamUsageEstimator
import kotlin.math.min

/**
 * This class is similar to [Packed64] except that it trades space for speed by ensuring that
 * a single block needs to be read/written in order to read/write a value.
 */
internal abstract class Packed64SingleBlock(valueCount: Int, bitsPerValue: Int) :
    PackedInts.MutableImpl(valueCount, bitsPerValue) {
    val blocks: LongArray

    init {
        assert(isSupported(bitsPerValue))
        val valuesPerBlock = 64 / bitsPerValue
        blocks = LongArray(requiredCapacity(valueCount, valuesPerBlock))
    }

    override fun clear() {
        Arrays.fill(blocks, 0L)
    }

    override fun ramBytesUsed(): Long {
        return (RamUsageEstimator.alignObjectSize(
            (RamUsageEstimator.NUM_BYTES_OBJECT_HEADER
                    + 2 * Int.SIZE_BYTES // valueCount,bitsPerValue
                    + RamUsageEstimator.NUM_BYTES_OBJECT_REF).toLong()
        ) // blocks ref
                + RamUsageEstimator.sizeOf(blocks))
    }

    override fun get(index: Int, arr: LongArray, off: Int, len: Int): Int {
        var index = index
        var off = off
        var len = len
        assert(len > 0) { "len must be > 0 (got $len)" }
        assert(index >= 0 && index < valueCount)
        len = min(len, valueCount - index)
        assert(off + len <= arr.size)

        val originalIndex = index

        // go to the next block boundary
        val valuesPerBlock: Int = 64 / bitsPerValue
        val offsetInBlock = index % valuesPerBlock
        if (offsetInBlock != 0) {
            var i = offsetInBlock
            while (i < valuesPerBlock && len > 0) {
                arr[off++] = get(index++)
                --len
                ++i
            }
            if (len == 0) {
                return index - originalIndex
            }
        }

        // bulk get
        assert(index % valuesPerBlock == 0)
        @Suppress("deprecation") val decoder: PackedInts.Decoder =
            BulkOperation.of(
                PackedInts.Format.PACKED_SINGLE_BLOCK,
                bitsPerValue
            )
        assert(decoder.longBlockCount() == 1)
        assert(decoder.longValueCount() == valuesPerBlock)
        val blockIndex = index / valuesPerBlock
        val nblocks = (index + len) / valuesPerBlock - blockIndex
        decoder.decode(blocks, blockIndex, arr, off, nblocks)
        val diff = nblocks * valuesPerBlock
        index += diff
        len -= diff

        if (index > originalIndex) {
            // stay at the block boundary
            return index - originalIndex
        } else {
            // no progress so far => already at a block boundary but no full block to
            // get
            assert(index == originalIndex)
            return super.get(index, arr, off, len)
        }
    }

    override fun set(index: Int, arr: LongArray, off: Int, len: Int): Int {
        var index = index
        var off = off
        var len = len
        assert(len > 0) { "len must be > 0 (got " + len + ")" }
        assert(index >= 0 && index < valueCount)
        len = min(len, valueCount - index)
        assert(off + len <= arr.size)

        val originalIndex = index

        // go to the next block boundary
        val valuesPerBlock: Int = 64 / bitsPerValue
        val offsetInBlock = index % valuesPerBlock
        if (offsetInBlock != 0) {
            var i = offsetInBlock
            while (i < valuesPerBlock && len > 0) {
                set(index++, arr[off++])
                --len
                ++i
            }
            if (len == 0) {
                return index - originalIndex
            }
        }

        // bulk set
        assert(index % valuesPerBlock == 0)
        @Suppress("deprecation") val op: BulkOperation =
            BulkOperation.of(
                PackedInts.Format.PACKED_SINGLE_BLOCK,
                bitsPerValue
            )
        assert(op.longBlockCount() == 1)
        assert(op.longValueCount() == valuesPerBlock)
        val blockIndex = index / valuesPerBlock
        val nblocks = (index + len) / valuesPerBlock - blockIndex
        op.encode(arr, off, blocks, blockIndex, nblocks)
        val diff = nblocks * valuesPerBlock
        index += diff
        len -= diff

        if (index > originalIndex) {
            // stay at the block boundary
            return index - originalIndex
        } else {
            // no progress so far => already at a block boundary but no full block to
            // set
            assert(index == originalIndex)
            return super.set(index, arr, off, len)
        }
    }

    override fun fill(fromIndex: Int, toIndex: Int, `val`: Long) {
        var fromIndex = fromIndex
        assert(fromIndex >= 0)
        assert(fromIndex <= toIndex)
        assert(PackedInts.unsignedBitsRequired(`val`) <= bitsPerValue)

        val valuesPerBlock: Int = 64 / bitsPerValue
        if (toIndex - fromIndex <= valuesPerBlock shl 1) {
            // there needs to be at least one full block to set for the block
            // approach to be worth trying
            super.fill(fromIndex, toIndex, `val`)
            return
        }

        // set values naively until the next block start
        val fromOffsetInBlock = fromIndex % valuesPerBlock
        if (fromOffsetInBlock != 0) {
            for (i in fromOffsetInBlock..<valuesPerBlock) {
                set(fromIndex++, `val`)
            }
            assert(fromIndex % valuesPerBlock == 0)
        }

        // bulk set of the inner blocks
        val fromBlock = fromIndex / valuesPerBlock
        val toBlock = toIndex / valuesPerBlock
        assert(fromBlock * valuesPerBlock == fromIndex)

        var blockValue = 0L
        for (i in 0..<valuesPerBlock) {
            blockValue = blockValue or (`val` shl (i * bitsPerValue))
        }
        Arrays.fill(blocks, fromBlock, toBlock, blockValue)

        // fill the gap
        for (i in valuesPerBlock * toBlock..<toIndex) {
            set(i, `val`)
        }
    }

    override fun toString(): String {
        return (this::class.simpleName
                + "(bitsPerValue="
                + bitsPerValue
                + ",size="
                + size()
                + ",blocks="
                + blocks.size
                + ")")
    }

    internal class Packed64SingleBlock1(valueCount: Int) : Packed64SingleBlock(valueCount, 1) {
        override fun get(index: Int): Long {
            val o = index ushr 6
            val b = index and 63
            val shift = b shl 0
            return (blocks[o] ushr shift) and 1L
        }

        override fun set(index: Int, value: Long) {
            val o = index ushr 6
            val b = index and 63
            val shift = b shl 0
            blocks[o] = (blocks[o] and (1L shl shift).inv()) or (value shl shift)
        }
    }

    internal class Packed64SingleBlock2(valueCount: Int) : Packed64SingleBlock(valueCount, 2) {
        override fun get(index: Int): Long {
            val o = index ushr 5
            val b = index and 31
            val shift = b shl 1
            return (blocks[o] ushr shift) and 3L
        }

        override fun set(index: Int, value: Long) {
            val o = index ushr 5
            val b = index and 31
            val shift = b shl 1
            blocks[o] = (blocks[o] and (3L shl shift).inv()) or (value shl shift)
        }
    }

    internal class Packed64SingleBlock3(valueCount: Int) : Packed64SingleBlock(valueCount, 3) {
        override fun get(index: Int): Long {
            val o = index / 21
            val b = index % 21
            val shift = b * 3
            return (blocks[o] ushr shift) and 7L
        }

        override fun set(index: Int, value: Long) {
            val o = index / 21
            val b = index % 21
            val shift = b * 3
            blocks[o] = (blocks[o] and (7L shl shift).inv()) or (value shl shift)
        }
    }

    internal class Packed64SingleBlock4(valueCount: Int) : Packed64SingleBlock(valueCount, 4) {
        override fun get(index: Int): Long {
            val o = index ushr 4
            val b = index and 15
            val shift = b shl 2
            return (blocks[o] ushr shift) and 15L
        }

        override fun set(index: Int, value: Long) {
            val o = index ushr 4
            val b = index and 15
            val shift = b shl 2
            blocks[o] = (blocks[o] and (15L shl shift).inv()) or (value shl shift)
        }
    }

    internal class Packed64SingleBlock5(valueCount: Int) : Packed64SingleBlock(valueCount, 5) {
        override fun get(index: Int): Long {
            val o = index / 12
            val b = index % 12
            val shift = b * 5
            return (blocks[o] ushr shift) and 31L
        }

        override fun set(index: Int, value: Long) {
            val o = index / 12
            val b = index % 12
            val shift = b * 5
            blocks[o] = (blocks[o] and (31L shl shift).inv()) or (value shl shift)
        }
    }

    internal class Packed64SingleBlock6(valueCount: Int) : Packed64SingleBlock(valueCount, 6) {
        override fun get(index: Int): Long {
            val o = index / 10
            val b = index % 10
            val shift = b * 6
            return (blocks[o] ushr shift) and 63L
        }

        override fun set(index: Int, value: Long) {
            val o = index / 10
            val b = index % 10
            val shift = b * 6
            blocks[o] = (blocks[o] and (63L shl shift).inv()) or (value shl shift)
        }
    }

    internal class Packed64SingleBlock7(valueCount: Int) : Packed64SingleBlock(valueCount, 7) {
        override fun get(index: Int): Long {
            val o = index / 9
            val b = index % 9
            val shift = b * 7
            return (blocks[o] ushr shift) and 127L
        }

        override fun set(index: Int, value: Long) {
            val o = index / 9
            val b = index % 9
            val shift = b * 7
            blocks[o] = (blocks[o] and (127L shl shift).inv()) or (value shl shift)
        }
    }

    internal class Packed64SingleBlock8(valueCount: Int) : Packed64SingleBlock(valueCount, 8) {
        override fun get(index: Int): Long {
            val o = index ushr 3
            val b = index and 7
            val shift = b shl 3
            return (blocks[o] ushr shift) and 255L
        }

        override fun set(index: Int, value: Long) {
            val o = index ushr 3
            val b = index and 7
            val shift = b shl 3
            blocks[o] = (blocks[o] and (255L shl shift).inv()) or (value shl shift)
        }
    }

    internal class Packed64SingleBlock9(valueCount: Int) : Packed64SingleBlock(valueCount, 9) {
        override fun get(index: Int): Long {
            val o = index / 7
            val b = index % 7
            val shift = b * 9
            return (blocks[o] ushr shift) and 511L
        }

        override fun set(index: Int, value: Long) {
            val o = index / 7
            val b = index % 7
            val shift = b * 9
            blocks[o] = (blocks[o] and (511L shl shift).inv()) or (value shl shift)
        }
    }

    internal class Packed64SingleBlock10(valueCount: Int) : Packed64SingleBlock(valueCount, 10) {
        override fun get(index: Int): Long {
            val o = index / 6
            val b = index % 6
            val shift = b * 10
            return (blocks[o] ushr shift) and 1023L
        }

        override fun set(index: Int, value: Long) {
            val o = index / 6
            val b = index % 6
            val shift = b * 10
            blocks[o] = (blocks[o] and (1023L shl shift).inv()) or (value shl shift)
        }
    }

    internal class Packed64SingleBlock12(valueCount: Int) : Packed64SingleBlock(valueCount, 12) {
        override fun get(index: Int): Long {
            val o = index / 5
            val b = index % 5
            val shift = b * 12
            return (blocks[o] ushr shift) and 4095L
        }

        override fun set(index: Int, value: Long) {
            val o = index / 5
            val b = index % 5
            val shift = b * 12
            blocks[o] = (blocks[o] and (4095L shl shift).inv()) or (value shl shift)
        }
    }

    internal class Packed64SingleBlock16(valueCount: Int) : Packed64SingleBlock(valueCount, 16) {
        override fun get(index: Int): Long {
            val o = index ushr 2
            val b = index and 3
            val shift = b shl 4
            return (blocks[o] ushr shift) and 65535L
        }

        override fun set(index: Int, value: Long) {
            val o = index ushr 2
            val b = index and 3
            val shift = b shl 4
            blocks[o] = (blocks[o] and (65535L shl shift).inv()) or (value shl shift)
        }
    }

    internal class Packed64SingleBlock21(valueCount: Int) : Packed64SingleBlock(valueCount, 21) {
        override fun get(index: Int): Long {
            val o = index / 3
            val b = index % 3
            val shift = b * 21
            return (blocks[o] ushr shift) and 2097151L
        }

        override fun set(index: Int, value: Long) {
            val o = index / 3
            val b = index % 3
            val shift = b * 21
            blocks[o] = (blocks[o] and (2097151L shl shift).inv()) or (value shl shift)
        }
    }

    internal class Packed64SingleBlock32(valueCount: Int) : Packed64SingleBlock(valueCount, 32) {
        override fun get(index: Int): Long {
            val o = index ushr 1
            val b = index and 1
            val shift = b shl 5
            return (blocks[o] ushr shift) and 4294967295L
        }

        override fun set(index: Int, value: Long) {
            val o = index ushr 1
            val b = index and 1
            val shift = b shl 5
            blocks[o] = (blocks[o] and (4294967295L shl shift).inv()) or (value shl shift)
        }
    }

    companion object {
        const val MAX_SUPPORTED_BITS_PER_VALUE: Int = 32
        private val SUPPORTED_BITS_PER_VALUE = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 16, 21, 32)

        fun isSupported(bitsPerValue: Int): Boolean {
            return Arrays.binarySearch(SUPPORTED_BITS_PER_VALUE, bitsPerValue) >= 0
        }

        private fun requiredCapacity(valueCount: Int, valuesPerBlock: Int): Int {
            return valueCount / valuesPerBlock + (if (valueCount % valuesPerBlock == 0) 0 else 1)
        }

        fun create(valueCount: Int, bitsPerValue: Int): Packed64SingleBlock {
            when (bitsPerValue) {
                1 -> return Packed64SingleBlock1(valueCount)
                2 -> return Packed64SingleBlock2(valueCount)
                3 -> return Packed64SingleBlock3(valueCount)
                4 -> return Packed64SingleBlock4(valueCount)
                5 -> return Packed64SingleBlock5(valueCount)
                6 -> return Packed64SingleBlock6(valueCount)
                7 -> return Packed64SingleBlock7(valueCount)
                8 -> return Packed64SingleBlock8(valueCount)
                9 -> return Packed64SingleBlock9(valueCount)
                10 -> return Packed64SingleBlock10(valueCount)
                12 -> return Packed64SingleBlock12(valueCount)
                16 -> return Packed64SingleBlock16(valueCount)
                21 -> return Packed64SingleBlock21(valueCount)
                32 -> return Packed64SingleBlock32(valueCount)
                else -> throw IllegalArgumentException("Unsupported number of bits per value: " + 32)
            }
        }
    }
}
