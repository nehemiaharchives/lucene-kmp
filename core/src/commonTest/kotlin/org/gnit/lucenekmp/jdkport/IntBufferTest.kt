package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IntBufferTest {

    @Test
    fun testAllocate() {
        val buffer = IntBuffer.allocate(10)
        assertEquals(10, buffer.capacity)
        assertEquals(0, buffer.position)
        assertEquals(10, buffer.limit)
    }

    @Test
    fun testWrap() {
        val array = intArrayOf(1, 2, 3)
        val buffer = IntBuffer.wrap(array)
        assertEquals(3, buffer.capacity)
        assertEquals(0, buffer.position)
        assertEquals(3, buffer.limit)
        assertEquals(1, buffer.get(0))
        assertEquals(2, buffer.get(1))
        assertEquals(3, buffer.get(2))
    }

    @Test
    fun testRemaining() {
        val buffer = IntBuffer.allocate(10)
        assertEquals(10, buffer.remaining())
        buffer.position(5)
        assertEquals(5, buffer.remaining())
    }

    @Test
    fun testHasRemaining() {
        val buffer = IntBuffer.allocate(10)
        assertTrue(buffer.hasRemaining())
        buffer.position(10)
        assertTrue(!buffer.hasRemaining())
    }

    @Test
    fun testClear() {
        val buffer = IntBuffer.allocate(10)
        buffer.position(5)
        buffer.clear()
        assertEquals(0, buffer.position)
        assertEquals(10, buffer.limit)
    }

    @Test
    fun testFlip() {
        val buffer = IntBuffer.allocate(10)
        buffer.position(5)
        buffer.flip()
        assertEquals(0, buffer.position)
        assertEquals(5, buffer.limit)
    }

    @Test
    fun testDuplicate() {
        val buffer = IntBuffer.allocate(10)
        buffer.position(5)
        buffer.limit(7)
        val duplicate = buffer.duplicate()
        assertEquals(10, duplicate.capacity)
        assertEquals(5, duplicate.position)
        assertEquals(7, duplicate.limit)
    }

    @Test
    fun testGet() {
        val array = intArrayOf(1, 2, 3)
        val buffer = IntBuffer.wrap(array)
        assertEquals(1, buffer.get())
        assertEquals(2, buffer.get())
        assertEquals(3, buffer.get())
    }

    @Test
    fun testPut() {
        val buffer = IntBuffer.allocate(3)
        buffer.put(1)
        buffer.put(2)
        buffer.put(3)
        assertEquals(1, buffer.get(0))
        assertEquals(2, buffer.get(1))
        assertEquals(3, buffer.get(2))
    }

    @Test
    fun testSlice() {
        val array = intArrayOf(1, 2, 3, 4, 5)
        val buffer = IntBuffer.wrap(array)
        buffer.position(2)
        val slice = buffer.slice()
        assertEquals(3, slice.capacity)
        assertEquals(0, slice.position)
        assertEquals(3, slice.limit)
        assertEquals(3, slice.get(0))
        assertEquals(4, slice.get(1))
        assertEquals(5, slice.get(2))
    }

    @Test
    fun testCompareTo() {
        val buffer1 = IntBuffer.wrap(intArrayOf(1, 2, 3))
        val buffer2 = IntBuffer.wrap(intArrayOf(1, 2, 3))
        val buffer3 = IntBuffer.wrap(intArrayOf(1, 2, 4))
        assertEquals(0, buffer1.compareTo(buffer2))
        assertTrue(buffer1.compareTo(buffer3) < 0)
        assertTrue(buffer3.compareTo(buffer1) > 0)
    }

    @Test
    fun testEquals() {
        val buffer1 = IntBuffer.wrap(intArrayOf(1, 2, 3))
        val buffer2 = IntBuffer.wrap(intArrayOf(1, 2, 3))
        val buffer3 = IntBuffer.wrap(intArrayOf(1, 2, 4))
        assertTrue(buffer1 == buffer2)
        assertTrue(buffer1 != buffer3)
    }

    @Test
    fun testHashCode() {
        val buffer1 = IntBuffer.wrap(intArrayOf(1, 2, 3))
        val buffer2 = IntBuffer.wrap(intArrayOf(1, 2, 3))
        assertEquals(buffer1.hashCode(), buffer2.hashCode())
    }
}
