package org.gnit.lucenekmp.jdkport

import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FloatBufferTest {

    @Test
    fun testAllocate() {
        val buffer = FloatBuffer.allocate(10)
        assertEquals(10, buffer.capacity)
        assertEquals(0, buffer.position)
        assertEquals(10, buffer.limit)
    }

    @Test
    fun testWrap() {
        val array = floatArrayOf(1.0f, 2.0f, 3.0f)
        val buffer = FloatBuffer.wrap(array)
        assertEquals(3, buffer.capacity)
        assertEquals(0, buffer.position)
        assertEquals(3, buffer.limit)
        assertEquals(1.0f, buffer.get(0))
        assertEquals(2.0f, buffer.get(1))
        assertEquals(3.0f, buffer.get(2))
    }

    @Test
    fun testRemaining() {
        val buffer = FloatBuffer.allocate(10)
        assertEquals(10, buffer.remaining())
        buffer.position(5)
        assertEquals(5, buffer.remaining())
    }

    @Test
    fun testHasRemaining() {
        val buffer = FloatBuffer.allocate(10)
        assertTrue(buffer.hasRemaining())
        buffer.position(10)
        assertTrue(!buffer.hasRemaining())
    }

    @Test
    fun testClear() {
        val buffer = FloatBuffer.allocate(10)
        buffer.position(5)
        buffer.clear()
        assertEquals(0, buffer.position)
        assertEquals(10, buffer.limit)
    }

    @Test
    fun testFlip() {
        val buffer = FloatBuffer.allocate(10)
        buffer.position(5)
        buffer.flip()
        assertEquals(0, buffer.position)
        assertEquals(5, buffer.limit)
    }

    @Test
    fun testDuplicate() {
        val buffer = FloatBuffer.allocate(10)
        buffer.position(5)
        buffer.limit(7)
        val duplicate = buffer.duplicate()
        assertEquals(10, duplicate.capacity)
        assertEquals(5, duplicate.position)
        assertEquals(7, duplicate.limit)
    }

    @Test
    fun testGet() {
        val array = floatArrayOf(1.0f, 2.0f, 3.0f)
        val buffer = FloatBuffer.wrap(array)
        assertEquals(1.0f, buffer.get())
        assertEquals(2.0f, buffer.get())
        assertEquals(3.0f, buffer.get())
    }

    @Test
    fun testPut() {
        val buffer = FloatBuffer.allocate(3)
        buffer.put(1.0f)
        buffer.put(2.0f)
        buffer.put(3.0f)
        assertEquals(1.0f, buffer.get(0))
        assertEquals(2.0f, buffer.get(1))
        assertEquals(3.0f, buffer.get(2))
    }

    @Test
    fun testSlice() {
        val array = floatArrayOf(1.0f, 2.0f, 3.0f, 4.0f, 5.0f)
        val buffer = FloatBuffer.wrap(array)
        buffer.position(2)
        val slice = buffer.slice()
        assertEquals(3, slice.capacity)
        assertEquals(0, slice.position)
        assertEquals(3, slice.limit)
        assertEquals(3.0f, slice.get(0))
        assertEquals(4.0f, slice.get(1))
        assertEquals(5.0f, slice.get(2))
    }

    @Test
    fun testCompareTo() {
        val buffer1 = FloatBuffer.wrap(floatArrayOf(1.0f, 2.0f, 3.0f))
        val buffer2 = FloatBuffer.wrap(floatArrayOf(1.0f, 2.0f, 3.0f))
        val buffer3 = FloatBuffer.wrap(floatArrayOf(1.0f, 2.0f, 4.0f))
        assertEquals(0, buffer1.compareTo(buffer2))
        assertTrue(buffer1.compareTo(buffer3) < 0)
        assertTrue(buffer3.compareTo(buffer1) > 0)
    }

    @Test
    fun testEquals() {
        val buffer1 = FloatBuffer.wrap(floatArrayOf(1.0f, 2.0f, 3.0f))
        val buffer2 = FloatBuffer.wrap(floatArrayOf(1.0f, 2.0f, 3.0f))
        val buffer3 = FloatBuffer.wrap(floatArrayOf(1.0f, 2.0f, 4.0f))
        assertTrue(buffer1 == buffer2)
        assertTrue(buffer1 != buffer3)
    }

    @Test
    fun testHashCode() {
        val buffer1 = FloatBuffer.wrap(floatArrayOf(1.0f, 2.0f, 3.0f))
        val buffer2 = FloatBuffer.wrap(floatArrayOf(1.0f, 2.0f, 3.0f))
        assertEquals(buffer1.hashCode(), buffer2.hashCode())
    }
}
