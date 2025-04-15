package org.gnit.lucenekmp.util.packed

import kotlinx.io.IOException
import org.gnit.lucenekmp.jdkport.floatToIntBits
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.util.ArrayUtil
import kotlin.math.max
import kotlin.math.min

/**
 * Write monotonically-increasing sequences of integers. This writer splits data into blocks and
 * then for each block, computes the average slope, the minimum value and only encode the delta from
 * the expected value using a [DirectWriter].
 *
 * @see DirectMonotonicReader
 *
 * @lucene.internal
 */
class DirectMonotonicWriter internal constructor(
    metaOut: IndexOutput,
    dataOut: IndexOutput,
    numValues: Long,
    blockShift: Int
) {
    val meta: IndexOutput
    val data: IndexOutput
    val numValues: Long
    val baseDataPointer: Long
    val buffer: LongArray
    var bufferSize: Int
    var count: Long = 0
    var finished: Boolean = false

    @Throws(IOException::class)
    private fun flush() {
        require(bufferSize != 0)

        val avgInc = ((buffer[bufferSize - 1] - buffer[0]).toDouble() / max(1, bufferSize - 1)).toFloat()

        var min = Long.Companion.MAX_VALUE
        for (i in 0..<bufferSize) {
            val expected = (avgInc * i.toLong()).toLong()
            buffer[i] -= expected
            min = min(buffer[i], min)
        }

        var maxDelta: Long = 0
        for (i in 0..<bufferSize) {
            buffer[i] -= min
            // use | will change nothing when it comes to computing required bits
            // but has the benefit of working fine with negative values too
            // (in case of overflow)
            maxDelta = maxDelta or buffer[i]
        }

        meta.writeLong(min)
        meta.writeInt(Float.floatToIntBits(avgInc))
        meta.writeLong(data.filePointer - baseDataPointer)
        if (maxDelta == 0L) {
            meta.writeByte(0.toByte())
        } else {
            val bitsRequired = DirectWriter.unsignedBitsRequired(maxDelta)
            val writer = DirectWriter.getInstance(data, bufferSize.toLong(), bitsRequired)
            for (i in 0..<bufferSize) {
                writer.add(buffer[i])
            }
            writer.finish()
            meta.writeByte(bitsRequired.toByte())
        }
        bufferSize = 0
    }

    var previous: Long = Long.Companion.MIN_VALUE

    init {
        require(!(blockShift < MIN_BLOCK_SHIFT || blockShift > MAX_BLOCK_SHIFT)) {
            ("blockShift must be in ["
                    + MIN_BLOCK_SHIFT
                    + "-"
                    + MAX_BLOCK_SHIFT
                    + "], got "
                    + blockShift)
        }
        require(numValues >= 0) { "numValues can't be negative, got $numValues" }
        val numBlocks = if (numValues == 0L) 0 else ((numValues - 1) ushr blockShift) + 1
        require(numBlocks <= ArrayUtil.MAX_ARRAY_LENGTH) {
            ("blockShift is too low for the provided number of values: blockShift="
                    + blockShift
                    + ", numValues="
                    + numValues
                    + ", MAX_ARRAY_LENGTH="
                    + ArrayUtil.MAX_ARRAY_LENGTH)
        }
        this.meta = metaOut
        this.data = dataOut
        this.numValues = numValues
        val blockSize = 1 shl blockShift
        this.buffer = LongArray(min(numValues, blockSize.toLong()).toInt())
        this.bufferSize = 0
        this.baseDataPointer = dataOut.filePointer
    }

    /**
     * Write a new value. Note that data might not make it to storage until [.finish] is
     * called.
     *
     * @throws IllegalArgumentException if values don't come in order
     */
    @Throws(IOException::class)
    fun add(v: Long) {
        require(v >= previous) { "Values do not come in order: $previous, $v" }
        if (bufferSize == buffer.size) {
            flush()
        }
        buffer[bufferSize++] = v
        previous = v
        count++
    }

    /** This must be called exactly once after all values have been [added][.add].  */
    @Throws(IOException::class)
    fun finish() {
        check(count == numValues) { "Wrong number of values added, expected: $numValues, got: $count" }
        check(!finished) { "#finish has been called already" }
        if (bufferSize > 0) {
            flush()
        }
        finished = true
    }

    companion object {
        const val MIN_BLOCK_SHIFT: Int = 2
        const val MAX_BLOCK_SHIFT: Int = 22

        /**
         * Returns an instance suitable for encoding `numValues` into monotonic blocks of
         * 2<sup>`blockShift`</sup> values. Metadata will be written to `metaOut` and actual
         * data to `dataOut`.
         */
        fun getInstance(
            metaOut: IndexOutput, dataOut: IndexOutput, numValues: Long, blockShift: Int
        ): DirectMonotonicWriter {
            return DirectMonotonicWriter(metaOut, dataOut, numValues, blockShift)
        }
    }
}
