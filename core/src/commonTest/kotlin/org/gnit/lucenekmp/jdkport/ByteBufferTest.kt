package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ByteBufferTest {

    @Test
    fun testAllocate() {
        val buffer = ByteBuffer.allocate(10)
        assertEquals(10, buffer.capacity)
        assertEquals(0, buffer.position)
        assertEquals(10, buffer.limit)
    }

    @Test
    fun testWrap() {
        val array = byteArrayOf(1, 2, 3, 4, 5)
        val buffer = ByteBuffer.wrap(array)
        assertEquals(5, buffer.capacity)
        assertEquals(0, buffer.position)
        assertEquals(5, buffer.limit)
        assertEquals(1, buffer.get())
    }

    @Test
    fun testGetPut() {
        val buffer = ByteBuffer.allocate(10)
        buffer.put(0, 1)
        buffer.put(1, 2)
        buffer.put(2, 3)
        assertEquals(1, buffer.get(0))
        assertEquals(2, buffer.get(1))
        assertEquals(3, buffer.get(2))
    }

    @Test
    fun testFlip() {
        val buffer = ByteBuffer.allocate(10)
        buffer.put(1)
        buffer.put(2)
        buffer.put(3)
        buffer.flip()
        assertEquals(0, buffer.position)
        assertEquals(3, buffer.limit)
        assertEquals(1, buffer.get())
    }

    @Test
    fun testClear() {
        val buffer = ByteBuffer.allocate(10)
        buffer.put(1)
        buffer.put(2)
        buffer.put(3)
        buffer.clear()
        assertEquals(0, buffer.position)
        assertEquals(10, buffer.limit)
    }

    @Test
    fun testCompact() {
        val buffer = ByteBuffer.allocate(10)
        buffer.put(1)
        buffer.put(2)
        buffer.put(3)
        buffer.flip()
        buffer.get()
        buffer.compact()
        assertEquals(2, buffer.position)
        assertEquals(10, buffer.limit)
        assertEquals(2, buffer.get(0))
    }

    @Test
    fun testMarkReset() {
        val buffer = ByteBuffer.allocate(10)
        buffer.put(1)
        buffer.put(2)
        buffer.mark()
        buffer.put(3)
        buffer.reset()
        assertEquals(2, buffer.position)
    }

    @Test
    fun testPositionLimit() {
        val buffer = ByteBuffer.allocate(10)
        buffer.position(5)
        assertEquals(5, buffer.position)
        buffer.limit(7)
        assertEquals(7, buffer.limit)
    }

    @Test
    fun testRemaining() {
        val buffer = ByteBuffer.allocate(10)
        buffer.position(5)
        assertEquals(5, buffer.remaining())
    }

    @Test
    fun testHasRemaining() {
        val buffer = ByteBuffer.allocate(10)
        buffer.position(5)
        assertTrue(buffer.hasRemaining())
    }

    @Test
    fun testIsReadOnly() {
        val buffer = ByteBuffer.allocate(10)
        val readOnlyBuffer = buffer.asReadOnlyBuffer()
        assertFailsWith<ReadOnlyBufferException> {
            readOnlyBuffer.put(1)
        }
    }

    @Test
    fun testOrder() {
        val buffer = ByteBuffer.allocate(10)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(ByteOrder.LITTLE_ENDIAN, buffer.order())
    }

    @Test
    fun testSlice() {
        val buffer = ByteBuffer.allocate(10)
        buffer.position(5)
        val slice = buffer.slice()
        assertEquals(5, slice.capacity)
        assertEquals(0, slice.position)
        assertEquals(5, slice.limit)
    }

    @Test
    fun testDuplicate() {
        val buffer = ByteBuffer.allocate(10)
        buffer.put(1)
        buffer.put(2)
        val duplicate = buffer.duplicate()
        assertEquals(10, duplicate.capacity)
        assertEquals(2, duplicate.position)
        assertEquals(10, duplicate.limit)
        assertEquals(1, duplicate.get(0))
    }

    @Test
    fun testArray() {
        val array = byteArrayOf(1, 2, 3, 4, 5)
        val buffer = ByteBuffer.wrap(array)
        val arrayCopy = buffer.array()
        assertTrue(array contentEquals arrayCopy)
    }

    @Test
    fun testArrayOffset() {
        val array = byteArrayOf(1, 2, 3, 4, 5)
        val buffer = ByteBuffer.wrap(array)
        assertEquals(0, buffer.arrayOffset())
    }

    @Test
    fun testCompareTo() {
        val buffer1 = ByteBuffer.wrap(byteArrayOf(1, 2, 3))
        val buffer2 = ByteBuffer.wrap(byteArrayOf(1, 2, 4))
        assertTrue(buffer1 < buffer2)
    }

    @Test
    fun testEquals() {
        val buffer1 = ByteBuffer.wrap(byteArrayOf(1, 2, 3))
        val buffer2 = ByteBuffer.wrap(byteArrayOf(1, 2, 3))
        assertEquals(buffer1, buffer2)
    }

    @Test
    fun testHashCode() {
        val buffer = ByteBuffer.wrap(byteArrayOf(1, 2, 3))
        assertEquals(buffer.hashCode(), buffer.hashCode())
    }

    @Test
    fun testToString() {
        val buffer = ByteBuffer.wrap(byteArrayOf(1, 2, 3))
        assertEquals("ByteBuffer[pos=0 lim=3 cap=3]", buffer.toString())
    }

    @Test
    fun testBulkAbsoluteGet() {
        val data = byteArrayOf(10, 20, 30, 40, 50)
        val buffer = ByteBuffer.wrap(data)
        val dst = ByteArray(3)
        buffer.get(1, dst, 0, 3) // get 20,30,40 into dst
        assertTrue(dst contentEquals byteArrayOf(20, 30, 40))
    }

    @Test
    fun testGetIntAndGetShort() {
        // little endian: bytes 0x01,0x00 -> short=1; 0x78,0x56,0x34,0x12 -> int
        val data = byteArrayOf(0x01, 0x00, 0x78, 0x56, 0x34, 0x12)
        val buffer = ByteBuffer.wrap(data)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(1.toShort(), buffer.getShort(0))
        assertEquals(0x12345678, buffer.getInt(2))
    }

    @Test
    fun testHasArray() {
        val buffer = ByteBuffer.allocate(4)
        assertTrue(buffer.hasArray())
    }

    @Test
    fun testBulkGetOutOfBounds() {
        val buffer = ByteBuffer.allocate(5)
        val dst = ByteArray(2)
        // Should fail: index+length > limit
        assertFailsWith<IndexOutOfBoundsException> {
            buffer.get(4, dst, 0, 2)
        }
        // Should fail: offset+length > dst.size
        assertFailsWith<IndexOutOfBoundsException> {
            buffer.get(0, dst, 1, 2)
        }
    }
}
