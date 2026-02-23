package org.gnit.lucenekmp.util

import kotlin.math.min
import org.gnit.lucenekmp.jdkport.assert


/**
 * A [IntBlockPool.Allocator] implementation that recycles unused int blocks in a buffer and reuses
 * them in subsequent calls to [.intBlock].
 *
 * Note: This class is not thread-safe
 *
 * @lucene.internal
 */
class RecyclingIntBlockAllocator : IntBlockPool.Allocator {
    private var freeByteBlocks: Array<IntArray?>
    private val maxBufferedBlocks: Int
    private var freeBlocks = 0
    private val bytesUsed: Counter

    /**
     * Creates a new [RecyclingIntBlockAllocator]
     *
     * @param blockSize the block size in bytes
     * @param maxBufferedBlocks maximum number of buffered int block
     * @param bytesUsed [Counter] reference counting internally allocated bytes
     */
    constructor(blockSize: Int, maxBufferedBlocks: Int, bytesUsed: Counter) : super(blockSize) {
        freeByteBlocks = arrayOfNulls(maxBufferedBlocks)
        this.maxBufferedBlocks = maxBufferedBlocks
        this.bytesUsed = bytesUsed
    }

    /**
     * Creates a new [RecyclingIntBlockAllocator].
     *
     * @param blockSize the size of each block returned by this allocator
     * @param maxBufferedBlocks maximum number of buffered int blocks
     */
    constructor(blockSize: Int, maxBufferedBlocks: Int) : this(
        blockSize,
        maxBufferedBlocks,
        Counter.newCounter(false)
    )

    /**
     * Creates a new [RecyclingIntBlockAllocator] with a block size of [ ][IntBlockPool.INT_BLOCK_SIZE], upper buffered docs limit of [ ][DEFAULT_BUFFERED_BLOCKS] ([DEFAULT_BUFFERED_BLOCKS]).
     */
    constructor() : this(IntBlockPool.INT_BLOCK_SIZE, 64, Counter.newCounter(false))

    override val intBlock: IntArray
        get() {
            if (freeBlocks == 0) {
                bytesUsed.addAndGet(blockSize * Int.SIZE_BYTES.toLong())
                return IntArray(blockSize)
            }
            val b: IntArray = freeByteBlocks[--freeBlocks]!!
            freeByteBlocks[freeBlocks] = null
            return b
        }

    override fun recycleIntBlocks(blocks: Array<IntArray>, start: Int, end: Int) {
        val numBlocks = min(maxBufferedBlocks - freeBlocks, end - start)
        val size = freeBlocks + numBlocks
        if (size >= freeByteBlocks.size) {
            val newBlocks: Array<IntArray?> =
                arrayOfNulls(ArrayUtil.oversize(size, RamUsageEstimator.NUM_BYTES_OBJECT_REF))
            freeByteBlocks.copyInto(newBlocks, 0, 0, freeBlocks)
            freeByteBlocks = newBlocks
        }
        val stop = start + numBlocks
        for (i in start..<stop) {
            freeByteBlocks[freeBlocks++] = blocks[i]
            blocks[i] = EMPTY_INT_ARRAY
        }
        for (i in stop..<end) {
            blocks[i] = EMPTY_INT_ARRAY
        }
        bytesUsed.addAndGet(-((end - stop) * (blockSize * Int.SIZE_BYTES.toLong())))
        assert(bytesUsed.get() >= 0)
    }

    /**
     * @return the number of currently buffered blocks
     */
    fun numBufferedBlocks(): Int {
        return freeBlocks
    }

    /**
     * @return the number of bytes currently allocated by this [IntBlockPool.Allocator]
     */
    fun bytesUsed(): Long {
        return bytesUsed.get()
    }

    /**
     * @return the maximum number of buffered byte blocks
     */
    fun maxBufferedBlocks(): Int {
        return maxBufferedBlocks
    }

    /**
     * Removes the given number of int blocks from the buffer if possible.
     *
     * @param num the number of int blocks to remove
     * @return the number of actually removed buffers
     */
    fun freeBlocks(num: Int): Int {
        assert(num >= 0) { "free blocks must be >= 0 but was: $num" }
        val stop: Int
        val count: Int
        if (num > freeBlocks) {
            stop = 0
            count = freeBlocks
        } else {
            stop = freeBlocks - num
            count = num
        }
        while (freeBlocks > stop) {
            freeByteBlocks[--freeBlocks] = null
        }
        bytesUsed.addAndGet(-(count * blockSize.toLong() * Int.SIZE_BYTES.toLong()))
        assert(bytesUsed.get() >= 0)
        return count
    }

    companion object {
        const val DEFAULT_BUFFERED_BLOCKS: Int = 64
        private val EMPTY_INT_ARRAY: IntArray = IntArray(0)
    }
}
