package org.gnit.lucenekmp.util

import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.gnit.lucenekmp.tests.util.LuceneTestCase


/** Testcase for [RecyclingIntBlockAllocator] */
class TestRecyclingIntBlockAllocator : LuceneTestCase() {

    private fun newAllocator(): RecyclingIntBlockAllocator {
        return RecyclingIntBlockAllocator(
            1 shl (2 + random().nextInt(15)),
            random().nextInt(97),
            Counter.newCounter()
        )
    }

    @Test
    fun testAllocate() {
        val allocator = newAllocator()
        val set = hashSetOf<IntArray>()
        var block = allocator.intBlock
        set.add(block)
        assertNotNull(block)
        val size = block.size

        val num = atLeast(97)
        for (i in 0..<num) {
            block = allocator.intBlock
            assertNotNull(block)
            assertEquals(size, block.size)
            assertTrue(set.add(block), "block is returned twice")
            assertEquals((Int.SIZE_BYTES * size * (i + 2)).toLong(), allocator.bytesUsed()) // zero based + 1
            assertEquals(0, allocator.numBufferedBlocks())
        }
    }

    @Test
    fun testAllocateAndRecycle() {
        val allocator = newAllocator()
        val allocated = hashSetOf<IntArray>()

        var block = allocator.intBlock
        allocated.add(block)
        assertNotNull(block)
        val size = block.size

        val numIters = atLeast(97)
        for (i in 0..<numIters) {
            val num = 1 + random().nextInt(39)
            for (j in 0..<num) {
                block = allocator.intBlock
                assertNotNull(block)
                assertEquals(size, block.size)
                assertTrue(allocated.add(block), "block is returned twice")
                assertEquals((Int.SIZE_BYTES * size * (allocated.size + allocator.numBufferedBlocks())).toLong(), allocator.bytesUsed())
            }
            val array = allocated.toTypedArray()
            val begin = random().nextInt(array.size)
            val end = begin + random().nextInt(array.size - begin)
            val selected = mutableListOf<IntArray>()
            for (j in begin..<end) {
                selected.add(array[j])
            }
            allocator.recycleIntBlocks(array, begin, end)
            for (j in begin..<end) {
                assertEquals(0, array[j].size)
                val b = selected.removeAt(0)
                assertTrue(allocated.remove(b))
            }
        }
    }

    @Test
    fun testAllocateAndFree() {
        val allocator = newAllocator()
        val allocated = hashSetOf<IntArray>()
        var freeButAllocated = 0
        var block = allocator.intBlock
        allocated.add(block)
        assertNotNull(block)
        val size = block.size

        val numIters = atLeast(97)
        for (i in 0..<numIters) {
            val num = 1 + random().nextInt(39)
            for (j in 0..<num) {
                block = allocator.intBlock
                freeButAllocated = max(0, freeButAllocated - 1)
                assertNotNull(block)
                assertEquals(size, block.size)
                assertTrue(allocated.add(block), "block is returned twice")
                assertEquals(
                    (Int.SIZE_BYTES * size * (allocated.size + allocator.numBufferedBlocks())).toLong(),
                    allocator.bytesUsed(),
                    "${Int.SIZE_BYTES * size * (allocated.size + allocator.numBufferedBlocks()) - allocator.bytesUsed()}"
                )
            }

            val array = allocated.toTypedArray()
            val begin = random().nextInt(array.size)
            val end = begin + random().nextInt(array.size - begin)
            for (j in begin..<end) {
                val b = array[j]
                assertTrue(allocated.remove(b))
            }
            allocator.recycleIntBlocks(array, begin, end)
            for (j in begin..<end) {
                assertEquals(0, array[j].size)
            }
            // randomly free blocks
            val numFreeBlocks = allocator.numBufferedBlocks()
            val freeBlocks = allocator.freeBlocks(random().nextInt(7 + allocator.maxBufferedBlocks()))
            assertEquals(allocator.numBufferedBlocks(), numFreeBlocks - freeBlocks)
        }
    }
}
