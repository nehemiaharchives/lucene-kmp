package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.jdkport.assert

/**
 * A [ByteBlockPool.Allocator] implementation that recycles unused byte blocks in a buffer and
 * reuses them in subsequent calls to [byteBlock].
 *
 * Note: This class is not thread-safe.
 *
 * @lucene.internal
 */
class RecyclingByteBlockAllocator @JvmOverloads constructor(
    private val maxBufferedBlocks: Int = DEFAULT_BUFFERED_BLOCKS,
    private val bytesUsed: Counter = Counter.newCounter()
) : ByteBlockPool.Allocator(ByteBlockPool.BYTE_BLOCK_SIZE) {

    private var freeByteBlocks: Array<ByteArray?> = arrayOfNulls(maxBufferedBlocks)
    private var freeBlocks: Int = 0

    override val byteBlock: ByteArray
        get() {
            if (freeBlocks == 0) {
                bytesUsed.addAndGet(blockSize.toLong())
                return ByteArray(blockSize)
            }
            val b = freeByteBlocks[--freeBlocks]!!
            freeByteBlocks[freeBlocks] = null
            return b
        }

    override fun recycleByteBlocks(blocks: Array<ByteArray?>, start: Int, end: Int) {
        val numBlocks = kotlin.math.min(maxBufferedBlocks - freeBlocks, end - start)
        val size = freeBlocks + numBlocks
        if (size >= freeByteBlocks.size) {
            val newBlocks = arrayOfNulls<ByteArray>(
                ArrayUtil.oversize(size, RamUsageEstimator.NUM_BYTES_OBJECT_REF)
            )
            freeByteBlocks.copyInto(newBlocks, 0, 0, freeBlocks)
            freeByteBlocks = newBlocks
        }
        val stop = start + numBlocks
        for (i in start until stop) {
            freeByteBlocks[freeBlocks++] = blocks[i]
            blocks[i] = null
        }
        for (i in stop until end) {
            blocks[i] = null
        }
        bytesUsed.addAndGet(-((end - stop) * blockSize).toLong())
        assert(bytesUsed.get() >= 0) { "bytesUsed negative" }
    }

    /** @return the number of currently buffered blocks */
    fun numBufferedBlocks(): Int {
        return freeBlocks
    }

    /** @return the number of bytes currently allocated by this allocator */
    fun bytesUsed(): Long {
        return bytesUsed.get()
    }

    /** @return the maximum number of buffered byte blocks */
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
        val (stop, count) = if (num > freeBlocks) {
            0 to freeBlocks
        } else {
            (freeBlocks - num) to num
        }
        while (freeBlocks > stop) {
            freeByteBlocks[--freeBlocks] = null
        }
        bytesUsed.addAndGet(-count.toLong() * blockSize)
        assert(bytesUsed.get() >= 0)
        return count
    }

    companion object {
        const val DEFAULT_BUFFERED_BLOCKS: Int = 64
    }
}

