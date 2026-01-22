package org.gnit.lucenekmp.util.packed

import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.util.packed.PackedInts.checkBlockSize
import okio.IOException
import org.gnit.lucenekmp.jdkport.Arrays

abstract class AbstractBlockPackedWriter protected constructor(out: DataOutput, blockSize: Int) {
    protected var out: DataOutput? = null
    protected val values: LongArray
    protected var blocks: ByteArray? = null
    protected var off: Int = 0
    protected var ord: Long = 0
    protected var finished: Boolean = false

    /**
     * Sole constructor.
     *
     * @param blockSize the number of values of a single block, must be a multiple of `64`
     */
    init {
        checkBlockSize(blockSize, MIN_BLOCK_SIZE, MAX_BLOCK_SIZE)
        reset(out)
        values = LongArray(blockSize)
    }

    /** Reset this writer to wrap `out`. The block size remains unchanged.  */
    fun reset(out: DataOutput) {
        checkNotNull(out)
        this.out = out
        off = 0
        ord = 0L
        finished = false
    }

    private fun checkNotFinished() {
        check(!finished) { "Already finished" }
    }

    /** Append a new long.  */
    @Throws(IOException::class)
    open fun add(l: Long) {
        checkNotFinished()
        if (off == values.size) {
            flush()
        }
        values[off++] = l
        ++ord
    }

    // For testing only
    @Throws(IOException::class)
    fun addBlockOfZeros() {
        checkNotFinished()
        check(!(off != 0 && off != values.size)) { "" + off }
        if (off == values.size) {
            flush()
        }
        Arrays.fill(values, 0)
        off = values.size
        ord += values.size.toLong()
    }

    /**
     * Flush all buffered data to disk. This instance is not usable anymore after this method has been
     * called until [.reset] has been called.
     */
    @Throws(IOException::class)
    fun finish() {
        checkNotFinished()
        if (off > 0) {
            flush()
        }
        finished = true
    }

    /** Return the number of values which have been added.  */
    fun ord(): Long {
        return ord
    }

    @Throws(IOException::class)
    protected abstract fun flush()

    @Throws(IOException::class)
    protected fun writeValues(bitsRequired: Int) {
        val encoder =
            PackedInts.getEncoder(PackedInts.Format.PACKED, PackedInts.VERSION_CURRENT, bitsRequired)
        val iterations = values.size / encoder.byteValueCount()
        val blockSize = encoder.byteBlockCount() * iterations
        if (blocks == null || blocks!!.size < blockSize) {
            blocks = ByteArray(blockSize)
        }
        if (off < values.size) {
            Arrays.fill(values, off, values.size, 0L)
        }
        encoder.encode(values, 0, blocks!!, 0, iterations)
        val blockCount =
            PackedInts.Format.PACKED.byteCount(PackedInts.VERSION_CURRENT, off, bitsRequired).toInt()
        out!!.writeBytes(blocks!!, blockCount)
    }

    companion object {
        const val MIN_BLOCK_SIZE: Int = 64
        const val MAX_BLOCK_SIZE: Int = 1 shl (30 - 3)
        const val MIN_VALUE_EQUALS_0: Int = 1 shl 0
        const val BPV_SHIFT: Int = 1

        // same as DataOutput.writeVLong but accepts negative values
        @Throws(IOException::class)
        fun writeVLong(out: DataOutput, i: Long) {
            var i = i
            var k = 0
            while ((i and 0x7FL.inv()) != 0L && k++ < 8) {
                out.writeByte(((i and 0x7FL) or 0x80L).toByte())
                i = i ushr 7
            }
            out.writeByte(i.toByte())
        }
    }
}
