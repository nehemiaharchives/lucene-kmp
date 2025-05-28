package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
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

    private fun Long.toBigEndianByteArray(byteCount: Int): ByteArray {
        require(byteCount in 1..8) { "byteCount must be between 1 and 8" }
        return ByteArray(byteCount) { i ->
            val shift = (byteCount - 1 - i) * 8
            ((this shr shift) and 0xFF).toByte()
        }
    }

    private fun Long.toLittleEndianByteArray(byteCount: Int): ByteArray {
        require(byteCount in 1..8) { "byteCount must be between 1 and 8" }
        return ByteArray(byteCount) { i ->
            val shift = i * 8
            ((this shr shift) and 0xFF).toByte()
        }
    }

    @Test
    fun testGetLong() {
        val bytes = byteArrayOf(
            1, 3, 5, 7, 1, 3, 5, 7,  // Long 1
            2, 4, 6, 8, 2, 4, 6, 8, // Long 2
        )

        val long1BE = 0x0103050701030507L
        val long2BE = 0x0204060802040608L
        val long1LE = 0x0705030107050301L // Little Endian
        val long2LE = 0x0806040208060402L // Little Endian

        // test long1BE to be converted from 1,3,5,7
        assertContentEquals(
            byteArrayOf(1, 3, 5, 7, 1, 3, 5, 7),
            long1BE.toBigEndianByteArray(8),
            "Long 1 BE byte array"
        )
        assertContentEquals(
            byteArrayOf(2, 4, 6, 8, 2, 4, 6, 8),
            long2BE.toBigEndianByteArray(8),
            "Long 2 BE byte array"
        )
        assertContentEquals(
            byteArrayOf(1, 3, 5, 7, 1, 3, 5, 7),
            long1LE.toLittleEndianByteArray(8),
            "Long 1 LE byte array"
        )
        assertContentEquals(
            byteArrayOf(2, 4, 6, 8, 2, 4, 6, 8),
            long2LE.toLittleEndianByteArray(8),
            "Long 2 LE byte array"
        )

        // Test absolute get (Big Endian - default)
        val buffer = ByteBuffer.wrap(bytes)
        assertEquals(ByteOrder.BIG_ENDIAN, buffer.order(), "Default order should be BIG_ENDIAN")
        assertEquals(long1BE, buffer.getLong(0), "Absolute get Long 1 BE")
        assertEquals(long2BE, buffer.getLong(8), "Absolute get Long 2 BE")
        assertEquals(0, buffer.position, "Position should not change after absolute get")

        // Test relative get (Big Endian)
        buffer.position(0) // Reset position
        assertEquals(long1BE, buffer.getLong(), "Relative get Long 1 BE")
        assertEquals(8, buffer.position, "Position after relative get Long 1 BE")
        assertEquals(long2BE, buffer.getLong(), "Relative get Long 2 BE")
        assertEquals(16, buffer.position, "Position after relative get Long 2 BE")

        // Change order to Little Endian
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(ByteOrder.LITTLE_ENDIAN, buffer.order(), "Order should be LITTLE_ENDIAN")

        // Test absolute get (Little Endian)
        // Position should remain 16 from previous tests, absolute gets don't change it
        assertEquals(long1LE, buffer.getLong(0), "Absolute get Long 1 LE")
        assertEquals(long2LE, buffer.getLong(8), "Absolute get Long 2 LE")
        assertEquals(16, buffer.position, "Position should not change after absolute get LE")

        // Test relative get (Little Endian)
        buffer.position(0) // Reset position
        assertEquals(long1LE, buffer.getLong(), "Relative get Long 1 LE")
        assertEquals(8, buffer.position, "Position after relative get Long 1 LE")
        assertEquals(long2LE, buffer.getLong(), "Relative get Long 2 LE")
        assertEquals(16, buffer.position, "Position after relative get Long 2 LE")

        // Test bounds for absolute get
        assertFailsWith<IndexOutOfBoundsException>("Absolute get out of bounds (start)") { buffer.getLong(-1) }
        assertFailsWith<IndexOutOfBoundsException>("Absolute get out of bounds (end)") { buffer.getLong(bytes.size) }
        assertFailsWith<IndexOutOfBoundsException>("Absolute get not enough bytes") { buffer.getLong(bytes.size - 7) }
        assertEquals(long1LE, buffer.getLong(0), "Check value after failed attempts LE") // Ensure buffer state is fine

        // Test bounds for relative get
        buffer.position(bytes.size - 7) // Position for underflow
        assertFailsWith<BufferUnderflowException>("Relative get underflow") { buffer.getLong() }
    }

    @Test
    fun testGetShortCurrent() {
        // little endian: bytes 0x01,0x00 -> short=1
        val data = byteArrayOf(0x01, 0x00)
        val buffer = ByteBuffer.wrap(data)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(1.toShort(), buffer.getShort())
    }

    @Test
    fun testAsIntBuffer() {
        // Create a ByteBuffer with known int values
        // Bytes for ints 0x01234567 and 0x89ABCDEF in big endian
        val bytes = byteArrayOf(
            0x01, 0x23, 0x45, 0x67,  // First int
            0x89.toByte(), 0xAB.toByte(), 0xCD.toByte(), 0xEF.toByte()  // Second int
        )
        val buffer = ByteBuffer.wrap(bytes)

        // Test big endian (default)
        var intBuffer = buffer.asIntBuffer()
        assertEquals(2, intBuffer.capacity)
        assertEquals(0, intBuffer.position)
        assertEquals(2, intBuffer.limit)
        assertEquals(0x01234567, intBuffer.get(0))
        assertEquals(0x89ABCDEF.toInt(), intBuffer.get(1))

        // Test little endian
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        intBuffer = buffer.asIntBuffer()
        assertEquals(0x67452301, intBuffer.get(0))
        assertEquals(0xEFCDAB89.toInt(), intBuffer.get(1))
    }

    @Test
    fun testAsLongBuffer() {
        // Create a ByteBuffer with a known long value
        val bytes = byteArrayOf(
            1,2,3,4,5,6,7,8
        )
        val buffer = ByteBuffer.wrap(bytes)

        // Test big endian (default)
        var longBuffer = buffer.asLongBuffer()
        assertEquals(1, longBuffer.capacity)
        assertEquals(0, longBuffer.position)
        assertEquals(1, longBuffer.limit)
        assertEquals(0x0102030405060708L, longBuffer.get(0))

        // Test little endian
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        longBuffer = buffer.asLongBuffer()
        assertEquals(0x0807060504030201L, longBuffer.get(0))
    }

    @Test
    fun testAsFloatBuffer() {
        // Create ByteBuffer with bytes representing specific float values
        // 0x3F800000 is 1.0f in IEEE 754
        // 0x40000000 is 2.0f in IEEE 754
        val bytes = byteArrayOf(
            0x3F, 0x80.toByte(), 0x00, 0x00,  // 1.0f in big endian
            0x40, 0x00, 0x00, 0x00   // 2.0f in big endian
        )
        val buffer = ByteBuffer.wrap(bytes)

        // Test big endian (default)
        var floatBuffer = buffer.asFloatBuffer()
        assertEquals(2, floatBuffer.capacity)
        assertEquals(0, floatBuffer.position)
        assertEquals(2, floatBuffer.limit)
        assertEquals(1.0f, floatBuffer.get(0))
        assertEquals(2.0f, floatBuffer.get(1))

        // Test little endian
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        floatBuffer = buffer.asFloatBuffer()
        // In little endian, the bytes are interpreted differently
        assertNotEquals(1.0f, floatBuffer.get(0))
        assertNotEquals(2.0f, floatBuffer.get(1))
    }

    @Test
    fun testBufferPositionAndLimit() {
        // Create a buffer with 4 ints (16 bytes)
        val buffer = ByteBuffer.allocate(16)
        // Fill with values
        for (i in 0 until 16) {
            buffer.put(i.toByte())
        }
        buffer.flip()

        // Set position to 4 (start of second int)
        buffer.position(4)

        // Create view buffer - should start at current position
        val intBuffer = buffer.asIntBuffer()

        // Should have capacity for 3 ints (12 bytes from position 4 to end)
        assertEquals(3, intBuffer.capacity)
        assertEquals(0, intBuffer.position)
        assertEquals(3, intBuffer.limit)
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
