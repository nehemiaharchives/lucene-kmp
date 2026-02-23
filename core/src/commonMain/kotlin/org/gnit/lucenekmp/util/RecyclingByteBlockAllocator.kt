package org.gnit.lucenekmp.util

import kotlin.math.min
import org.gnit.lucenekmp.jdkport.assert


/**
 * A [ByteBlockPool.Allocator] implementation that recycles unused byte blocks in a buffer and
 * reuses them in subsequent calls to [.byteBlock].
 *
 * Note: This class is not thread-safe
 *
 * @lucene.internal
 */
class RecyclingByteBlockAllocator : ByteBlockPool.Allocator {
    private var freeByteBlocks: Array<ByteArray?>
    private val maxBufferedBlocks: Int
    private var freeBlocks = 0
    private val bytesUsed: Counter

    /**
     * Creates a new [RecyclingByteBlockAllocator]
     *
     * @param maxBufferedBlocks maximum number of buffered byte block
     * @param bytesUsed [Counter] reference counting internally allocated bytes
     */
    constructor(maxBufferedBlocks: Int, bytesUsed: Counter) : super(ByteBlockPool.BYTE_BLOCK_SIZE) {
        freeByteBlocks = arrayOfNulls(maxBufferedBlocks)
        this.maxBufferedBlocks = maxBufferedBlocks
        this.bytesUsed = bytesUsed
    }

    /**
     * Creates a new [RecyclingByteBlockAllocator].
     *
     * @param maxBufferedBlocks maximum number of buffered byte block
     */
    constructor(maxBufferedBlocks: Int) : this(maxBufferedBlocks, Counter.newCounter(false))

    /**
     * Creates a new [RecyclingByteBlockAllocator] with a block size of [ ][ByteBlockPool.BYTE_BLOCK_SIZE], upper buffered docs limit of [ ][DEFAULT_BUFFERED_BLOCKS]
     * ([DEFAULT_BUFFERED_BLOCKS]).
     */
    constructor() : this(DEFAULT_BUFFERED_BLOCKS, Counter.newCounter(false))

    override val byteBlock: ByteArray
        get() {
            if (freeBlocks == 0) {
                bytesUsed.addAndGet(blockSize.toLong())
                return ByteArray(blockSize)
            }
            val b: ByteArray = freeByteBlocks[--freeBlocks]!!
            freeByteBlocks[freeBlocks] = null
            return b
        }

    override fun recycleByteBlocks(blocks: Array<ByteArray?>, start: Int, end: Int) {
        val numBlocks = min(maxBufferedBlocks - freeBlocks, end - start)
        val size = freeBlocks + numBlocks
        if (size >= freeByteBlocks.size) {
            val newBlocks: Array<ByteArray?> =
                arrayOfNulls(ArrayUtil.oversize(size, RamUsageEstimator.NUM_BYTES_OBJECT_REF))
            freeByteBlocks.copyInto(newBlocks, 0, 0, freeBlocks)
            freeByteBlocks = newBlocks
        }
        val stop = start + numBlocks
        for (i in start..<stop) {
            freeByteBlocks[freeBlocks++] = blocks[i]
            blocks[i] = null
        }
        for (i in stop..<end) {
            blocks[i] = null
        }
        bytesUsed.addAndGet(-((end - stop) * blockSize).toLong())
        assert(bytesUsed.get() >= 0)
    }

    /**
     * @return the number of currently buffered blocks
     */
    fun numBufferedBlocks(): Int {
        return freeBlocks
    }

    /**
     * @return the number of bytes currently allocated by this [ByteBlockPool.Allocator]
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
     * Removes the given number of byte blocks from the buffer if possible.
     *
     * @param num the number of byte blocks to remove
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
        bytesUsed.addAndGet(-(count * blockSize).toLong())
        assert(bytesUsed.get() >= 0)
        return count
    }

    companion object {
        const val DEFAULT_BUFFERED_BLOCKS: Int = 64
    }
}
