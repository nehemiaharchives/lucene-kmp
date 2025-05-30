package org.gnit.lucenekmp.util.packed

import okio.IOException
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.intBitsToFloat
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.LongValues
import org.gnit.lucenekmp.util.RamUsageEstimator
import kotlin.math.min

/**
 * Provides random access to a stream written with [MonotonicBlockPackedWriter].
 *
 * @lucene.internal
 */
class MonotonicBlockPackedReader private constructor(
    `in`: IndexInput,
    packedIntsVersion: Int,
    blockSize: Int,
    val valueCount: Long
) : LongValues(), Accountable {
    val blockShift: Int = PackedInts.checkBlockSize(
        blockSize,
        AbstractBlockPackedWriter.MIN_BLOCK_SIZE,
        AbstractBlockPackedWriter.MAX_BLOCK_SIZE
    )
    val blockMask: Int = blockSize - 1
    val minValues: LongArray
    val averages: FloatArray
    val subReaders: Array<LongValues?>
    val sumBPV: Long
    val totalByteCount: Long

    init {
        val numBlocks: Int = PackedInts.numBlocks(valueCount, blockSize)
        minValues = LongArray(numBlocks)
        averages = FloatArray(numBlocks)
        subReaders = kotlin.arrayOfNulls<LongValues>(numBlocks)
        var sumBPV: Long = 0
        var totalByteCount: Long = 0
        for (i in 0..<numBlocks) {
            minValues[i] = `in`.readZLong()
            averages[i] = Float.intBitsToFloat(`in`.readInt())
            val bitsPerValue: Int = `in`.readVInt()
            sumBPV += bitsPerValue.toLong()
            if (bitsPerValue > 64) {
                throw IOException("Corrupted")
            }
            if (bitsPerValue == 0) {
                subReaders[i] = ZEROES
            } else {
                val size = min(blockSize.toLong(), valueCount - i.toLong() * blockSize).toInt()
                val byteCount: Int =
                    Math.toIntExact(
                        PackedInts.Format.PACKED.byteCount(
                            packedIntsVersion,
                            size,
                            bitsPerValue
                        )
                    )
                totalByteCount += byteCount.toLong()
                val blocks = ByteArray(byteCount)
                `in`.readBytes(blocks, 0, byteCount)
                val maskRight = ((1L shl bitsPerValue) - 1)
                val bpvMinusBlockSize = bitsPerValue - BLOCK_SIZE
                subReaders[i] =
                    object : LongValues() {
                        override fun get(index: Long): Long {
                            // The abstract index in a bit stream
                            val majorBitPos = index * bitsPerValue
                            // The offset of the first block in the backing byte-array
                            var blockOffset = (majorBitPos ushr BLOCK_BITS).toInt()
                            // The number of value-bits after the first byte
                            var endBits = (majorBitPos and MOD_MASK.toLong()) + bpvMinusBlockSize
                            if (endBits <= 0) {
                                // Single block
                                return ((blocks[blockOffset].toLong() and 0xFFL) ushr -endBits.toInt()) and maskRight
                            }
                            // Multiple blocks
                            var value = ((blocks[blockOffset++].toLong() and 0xFFL) shl endBits.toInt()) and maskRight
                            while (endBits > BLOCK_SIZE) {
                                endBits -= BLOCK_SIZE.toLong()
                                value = value or ((blocks[blockOffset++].toLong() and 0xFFL) shl endBits.toInt())
                            }
                            return value or ((blocks[blockOffset].toLong() and 0xFFL) ushr (BLOCK_SIZE - endBits).toInt())
                        }
                    }
            }
        }
        this.sumBPV = sumBPV
        this.totalByteCount = totalByteCount
    }

    override fun get(index: Long): Long {
        assert(index >= 0 && index < valueCount)
        val block = (index ushr blockShift).toInt()
        val idx = (index and blockMask.toLong()).toInt()
        return expected(minValues[block], averages[block], idx) + subReaders[block]!!.get(idx.toLong())
    }

    /** Returns the number of values  */
    fun size(): Long {
        return valueCount
    }

    override fun ramBytesUsed(): Long {
        var sizeInBytes: Long = 0
        sizeInBytes += RamUsageEstimator.sizeOf(minValues)
        sizeInBytes += RamUsageEstimator.sizeOf(averages)
        sizeInBytes += totalByteCount
        return sizeInBytes
    }

    override fun toString(): String {
        val avgBPV = if (subReaders.isEmpty()) 0 else sumBPV / subReaders.size
        return (this::class.simpleName
                + "(blocksize="
                + (1 shl blockShift)
                + ",size="
                + valueCount
                + ",avgBPV="
                + avgBPV
                + ")")
    }

    companion object {
        fun expected(origin: Long, average: Float, index: Int): Long {
            return origin + (average * index.toLong()).toLong()
        }

        private const val BLOCK_SIZE: Int = Byte.SIZE_BITS // #bits in a block
        private const val BLOCK_BITS = 3 // The #bits representing BLOCK_SIZE
        private const val MOD_MASK = BLOCK_SIZE - 1 // x % BLOCK_SIZE

        /** Sole constructor.  */
        @Throws(IOException::class)
        fun of(
            `in`: IndexInput, packedIntsVersion: Int, blockSize: Int, valueCount: Long
        ): MonotonicBlockPackedReader {
            return MonotonicBlockPackedReader(`in`, packedIntsVersion, blockSize, valueCount)
        }
    }
}
