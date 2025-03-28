package org.gnit.lucenekmp.util.packed

import kotlinx.io.EOFException
import kotlinx.io.IOException
import org.gnit.lucenekmp.store.DataOutput


// Packs high order byte first, to match
// IndexOutput.writeInt/Long/Short byte order
internal class PackedWriter(
    override val format: PackedInts.Format,
    out: DataOutput,
    valueCount: Int,
    bitsPerValue: Int,
    mem: Int
) : PackedInts.Writer(out, valueCount, bitsPerValue) {
    var finished: Boolean = false
    val encoder: BulkOperation
    val nextBlocks: ByteArray
    val nextValues: LongArray
    val iterations: Int
    var off: Int = 0
    var written: Int = 0

    init {
        encoder = BulkOperation.of(format, bitsPerValue)
        iterations = encoder.computeIterations(valueCount, mem)
        nextBlocks = ByteArray(iterations * encoder.byteBlockCount())
        nextValues = LongArray(iterations * encoder.byteValueCount())
    }

    @Throws(IOException::class)
    override fun add(v: Long) {
        require(PackedInts.unsignedBitsRequired(v) <= bitsPerValue)
        require(!finished)
        if (valueCount != -1 && written >= valueCount) {
            throw EOFException("Writing past end of stream")
        }
        nextValues[off++] = v
        if (off == nextValues.size) {
            flush()
        }
        ++written
    }

    @Throws(IOException::class)
    override fun finish() {
        require(!finished)
        if (valueCount != -1) {
            while (written < valueCount) {
                add(0L)
            }
        }
        flush()
        finished = true
    }

    @Throws(IOException::class)
    private fun flush() {
        encoder.encode(nextValues, 0, nextBlocks, 0, iterations)
        val blockCount = format.byteCount(PackedInts.VERSION_CURRENT, off, bitsPerValue).toInt()
        out.writeBytes(nextBlocks, blockCount)
        /*java.util.Arrays.fill(nextValues, 0L)*/
        nextValues.fill(0L)
        off = 0
    }

    override fun ord(): Int {
        return written - 1
    }
}
