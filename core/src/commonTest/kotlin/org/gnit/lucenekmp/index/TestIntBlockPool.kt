package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.IntBlockPool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** tests basic [IntBlockPool] functionality */
class TestIntBlockPool : LuceneTestCase() {
    @Test
    fun testWriteReadReset() {
        val pool = IntBlockPool(IntBlockPool.DirectAllocator())
        pool.nextBuffer()

        // Write <count> consecutive ints to the buffer, possibly allocating a new buffer
        var count = random().nextInt(2 * IntBlockPool.INT_BLOCK_SIZE)
        for (i in 0 until count) {
            if (pool.intUpto == IntBlockPool.INT_BLOCK_SIZE) {
                pool.nextBuffer()
            }
            pool.buffer[pool.intUpto++] = i
        }

        // Check that all the ints are present in th buffer pool
        for (i in 0 until count) {
            assertEquals(i, pool.buffers[i / IntBlockPool.INT_BLOCK_SIZE][i % IntBlockPool.INT_BLOCK_SIZE])
        }

        // Reset without filling with zeros and check that the first buffer still has the ints
        count = minOf(count, IntBlockPool.INT_BLOCK_SIZE)
        pool.reset(false, true)
        for (i in 0 until count) {
            assertEquals(i, pool.buffers[0][i])
        }

        // Reset and fill with zeros, then check there is no data left
        pool.intUpto = count
        pool.reset(true, true)
        for (i in 0 until count) {
            assertEquals(0, pool.buffers[0][i])
        }
    }

    @Test
    fun testTooManyAllocs() {
        // Use a mock allocator that doesn't waste memory
        val pool =
            IntBlockPool(
                object : IntBlockPool.Allocator(0) {
                    override fun recycleIntBlocks(blocks: Array<IntArray>, start: Int, end: Int) {}

                    override val intBlock: IntArray = IntArray(0)
                }
            )
        pool.nextBuffer()

        var throwsException = false
        for (i in 0 until Int.MAX_VALUE / IntBlockPool.INT_BLOCK_SIZE + 1) {
            try {
                pool.nextBuffer()
            } catch (@Suppress("UNUSED_EXPRESSION") e: ArithmeticException) {
                // The offset overflows on the last attempt to call nextBuffer()
                throwsException = true
                break
            }
        }
        assertTrue(throwsException)
        assertTrue(pool.intOffset + IntBlockPool.INT_BLOCK_SIZE < pool.intOffset)
    }
}
