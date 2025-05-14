package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LongBufferTest {

    @Test
    fun testAllocate() {
        val buffer = LongBuffer.allocate(10)
        assertEquals(10, buffer.capacity)
        assertEquals(0, buffer.position)
        assertEquals(10, buffer.limit)
    }

    @Test
    fun testWrap() {
        val array = longArrayOf(1L, 2L, 3L)
        val buffer = LongBuffer.wrap(array)
        assertEquals(3, buffer.capacity)
        assertEquals(0, buffer.position)
        assertEquals(3, buffer.limit)
        assertEquals(1L, buffer.get(0))
        assertEquals(2L, buffer.get(1))
        assertEquals(3L, buffer.get(2))
    }

    @Test
    fun testRemaining() {
        val buffer = LongBuffer.allocate(10)
        assertEquals(10, buffer.remaining())
        buffer.position(5)
        assertEquals(5, buffer.remaining())
    }

    @Test
    fun testHasRemaining() {
        val buffer = LongBuffer.allocate(10)
        assertTrue(buffer.hasRemaining())
        buffer.position(10)
        assertTrue(!buffer.hasRemaining())
    }

    @Test
    fun testClear() {
        val buffer = LongBuffer.allocate(10)
        buffer.position(5)
        buffer.clear()
        assertEquals(0, buffer.position)
        assertEquals(10, buffer.limit)
    }

    @Test
    fun testFlip() {
        val buffer = LongBuffer.allocate(10)
        buffer.position(5)
        buffer.flip()
        assertEquals(0, buffer.position)
        assertEquals(5, buffer.limit)
    }

    @Test
    fun testDuplicate() {
        val buffer = LongBuffer.allocate(10)
        buffer.position(5)
        buffer.limit(7)
        val duplicate = buffer.duplicate()
        assertEquals(10, duplicate.capacity)
        assertEquals(5, duplicate.position)
        assertEquals(7, duplicate.limit)
    }

    @Test
    fun testGet() {
        val array = longArrayOf(1L, 2L, 3L)
        val buffer = LongBuffer.wrap(array)
        assertEquals(1L, buffer.get())
        assertEquals(2L, buffer.get())
        assertEquals(3L, buffer.get())
    }

    @Test
    fun testPut() {
        val buffer = LongBuffer.allocate(3)
        buffer.put(1L)
        buffer.put(2L)
        buffer.put(3L)
        assertEquals(1L, buffer.get(0))
        assertEquals(2L, buffer.get(1))
        assertEquals(3L, buffer.get(2))
    }

    @Test
    fun testSlice() {
        val array = longArrayOf(1L, 2L, 3L, 4L, 5L)
        val buffer = LongBuffer.wrap(array)
        buffer.position(2)
        val slice = buffer.slice()
        assertEquals(3, slice.capacity)
        assertEquals(0, slice.position)
        assertEquals(3, slice.limit)
        assertEquals(3L, slice.get(0))
        assertEquals(4L, slice.get(1))
        assertEquals(5L, slice.get(2))
    }

    @Test
    fun testCompareTo() {
        val buffer1 = LongBuffer.wrap(longArrayOf(1L, 2L, 3L))
        val buffer2 = LongBuffer.wrap(longArrayOf(1L, 2L, 3L))
        val buffer3 = LongBuffer.wrap(longArrayOf(1L, 2L, 4L))
        assertEquals(0, buffer1.compareTo(buffer2))
        assertTrue(buffer1.compareTo(buffer3) < 0)
        assertTrue(buffer3.compareTo(buffer1) > 0)
    }

    @Test
    fun testEquals() {
        val buffer1 = LongBuffer.wrap(longArrayOf(1L, 2L, 3L))
        val buffer2 = LongBuffer.wrap(longArrayOf(1L, 2L, 3L))
        val buffer3 = LongBuffer.wrap(longArrayOf(1L, 2L, 4L))
        assertTrue(buffer1 == buffer2)
        assertTrue(buffer1 != buffer3)
    }

    @Test
    fun testHashCode() {
        val buffer1 = LongBuffer.wrap(longArrayOf(1L, 2L, 3L))
        val buffer2 = LongBuffer.wrap(longArrayOf(1L, 2L, 3L))
        assertEquals(buffer1.hashCode(), buffer2.hashCode())
    }
}
