package org.gnit.lucenekmp.util.packed

import okio.EOFException
import okio.IOException
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.util.LongsRef
import org.gnit.lucenekmp.util.packed.PackedInts.ReaderIteratorImpl
import kotlin.math.min


internal class PackedReaderIterator(
    val format: PackedInts.Format,
    val packedIntsVersion: Int,
    valueCount: Int,
    bitsPerValue: Int,
    `in`: DataInput,
    mem: Int
) : ReaderIteratorImpl(valueCount, bitsPerValue, `in`) {
    val bulkOperation: BulkOperation
    val nextBlocks: ByteArray
    val nextValues: LongsRef
    val iterations: Int
    var position: Int

    init {
        bulkOperation = BulkOperation.of(format, bitsPerValue)
        iterations = bulkOperation.computeIterations(valueCount, mem)
        require(valueCount == 0 || iterations > 0)
        nextBlocks = ByteArray(iterations * bulkOperation.byteBlockCount())
        nextValues = LongsRef(LongArray(iterations * bulkOperation.byteValueCount()), 0, 0)
        nextValues.offset = nextValues.longs.size
        position = -1
    }

    @Throws(IOException::class)
    override fun next(count: Int): LongsRef {
        var count = count
        require(nextValues.length >= 0)
        require(count > 0)
        require(nextValues.offset + nextValues.length <= nextValues.longs.size)

        nextValues.offset += nextValues.length

        val remaining: Int = valueCount - position - 1
        if (remaining <= 0) {
            throw EOFException()
        }
        count = min(remaining, count)

        if (nextValues.offset === nextValues.longs.size) {
            val remainingBlocks = format.byteCount(packedIntsVersion, remaining, bitsPerValue)
            val blocksToRead = min(remainingBlocks.toInt(), nextBlocks.size)
            `in`.readBytes(nextBlocks, 0, blocksToRead)
            if (blocksToRead < nextBlocks.size) {
                /*java.util.Arrays.fill(nextBlocks, blocksToRead, nextBlocks.size, 0.toByte())*/
                nextBlocks.fill(0, blocksToRead, nextBlocks.size)
            }

            bulkOperation.decode(nextBlocks, 0, nextValues.longs, 0, iterations)
            nextValues.offset = 0
        }

        nextValues.length = min(nextValues.longs.size - nextValues.offset, count)
        position += nextValues.length
        return nextValues
    }

    override fun ord(): Int {
        return position
    }
}
