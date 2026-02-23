package org.gnit.lucenekmp.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.gnit.lucenekmp.tests.util.LuceneTestCase


/** Testcase for [RecyclingByteBlockAllocator] */
class TestRecyclingByteBlockAllocator : LuceneTestCase() {

    private fun newAllocator(): RecyclingByteBlockAllocator {
        return RecyclingByteBlockAllocator(random().nextInt(97), Counter.newCounter())
    }

    @Test
    fun testAllocate() {
        val allocator = newAllocator()
        val set = hashSetOf<ByteArray>()
        var block = allocator.byteBlock
        set.add(block)
        assertNotNull(block)
        val size = block.size

        val num = atLeast(97)
        for (i in 0..<num) {
            block = allocator.byteBlock
            assertNotNull(block)
            assertEquals(size, block.size)
            assertTrue(set.add(block), "block is returned twice")
            assertEquals((size * (i + 2)).toLong(), allocator.bytesUsed()) // zero based + 1
            assertEquals(0, allocator.numBufferedBlocks())
        }
    }

    @Test
    fun testAllocateAndRecycle() {
        val allocator = newAllocator()
        val allocated = hashSetOf<ByteArray>()

        var block = allocator.byteBlock
        allocated.add(block)
        assertNotNull(block)
        val size = block.size

        val numIters = atLeast(97)
        for (i in 0..<numIters) {
            val num = 1 + random().nextInt(39)
            for (j in 0..<num) {
                block = allocator.byteBlock
                assertNotNull(block)
                assertEquals(size, block.size)
                assertTrue(allocated.add(block), "block is returned twice")
                assertEquals((size * (allocated.size + allocator.numBufferedBlocks())).toLong(), allocator.bytesUsed())
            }
            val array = arrayOfNulls<ByteArray>(allocated.size)
            allocated.toTypedArray().copyInto(array)
            val begin = random().nextInt(array.size)
            val end = begin + random().nextInt(array.size - begin)
            val selected = mutableListOf<ByteArray>()
            for (j in begin..<end) {
                selected.add(array[j]!!)
            }
            allocator.recycleByteBlocks(array, begin, end)
            for (j in begin..<end) {
                assertNull(array[j])
                val b = selected.removeAt(0)
                assertTrue(allocated.remove(b))
            }
        }
    }

    @Test
    fun testAllocateAndFree() {
        val allocator = newAllocator()
        val allocated = hashSetOf<ByteArray>()
        var freeButAllocated = 0
        var block = allocator.byteBlock
        allocated.add(block)
        assertNotNull(block)
        val size = block.size

        val numIters = atLeast(97)
        for (i in 0..<numIters) {
            val num = 1 + random().nextInt(39)
            for (j in 0..<num) {
                block = allocator.byteBlock
                freeButAllocated = kotlin.math.max(0, freeButAllocated - 1)
                assertNotNull(block)
                assertEquals(size, block.size)
                assertTrue(allocated.add(block), "block is returned twice")
                assertEquals((size * (allocated.size + allocator.numBufferedBlocks())).toLong(), allocator.bytesUsed())
            }

            val array = arrayOfNulls<ByteArray>(allocated.size)
            allocated.toTypedArray().copyInto(array)
            val begin = random().nextInt(array.size)
            val end = begin + random().nextInt(array.size - begin)
            for (j in begin..<end) {
                val b = array[j]!!
                assertTrue(allocated.remove(b))
            }
            allocator.recycleByteBlocks(array, begin, end)
            for (j in begin..<end) {
                assertNull(array[j])
            }
            // randomly free blocks
            val numFreeBlocks = allocator.numBufferedBlocks()
            val freeBlocks = allocator.freeBlocks(random().nextInt(7 + allocator.maxBufferedBlocks()))
            assertEquals(allocator.numBufferedBlocks(), numFreeBlocks - freeBlocks)
        }
    }
}
