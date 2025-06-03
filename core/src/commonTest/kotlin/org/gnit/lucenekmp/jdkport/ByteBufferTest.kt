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
    fun testArrayAsBackingDataRepresentation() {
        val array = byteArrayOf(1, 2, 3, 4, 5)
        val buffer = ByteBuffer.wrap(array)
        assertTrue(buffer.hasArray())
        assertEquals(array, buffer.array())
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

    @Test
    fun testPutByteBuffer() {
        val srcCapacity = 10
        val dstCapacity = 15
        val src = ByteBuffer.allocate(srcCapacity)
        val dst = ByteBuffer.allocate(dstCapacity)

        // Put data into source buffer
        for (i in 0 until srcCapacity) {
            src.put(i.toByte())
        }
        src.flip() // Prepare for reading from src

        // Transfer data from src to dst
        dst.put(src)

        // Assertions for successful transfer
        assertEquals(srcCapacity, dst.position, "Dst position should be updated to srcCapacity")
        assertEquals(srcCapacity, src.position, "Src position should be at its limit (drained)")
        assertEquals(src.limit, src.position, "Src should be fully read")

        dst.flip() // Prepare for reading from dst
        for (i in 0 until srcCapacity) {
            assertEquals(i.toByte(), dst.get(), "Data in dst should match src data at index $i")
        }

        // Test edge case: insufficient space in destination buffer
        val smallDst = ByteBuffer.allocate(5)
        src.rewind() // Reset src buffer for another put
        assertFailsWith<BufferOverflowException>("Should throw BufferOverflowException when dst has insufficient space") {
            smallDst.put(src)
        }

        // Test edge case: putting into a read-only buffer
        val readOnlyDst = ByteBuffer.allocate(10).asReadOnlyBuffer()
        src.rewind() // Reset src buffer
        assertFailsWith<ReadOnlyBufferException>("Should throw ReadOnlyBufferException when putting into a read-only buffer") {
            readOnlyDst.put(src)
        }

        // Test putting an empty buffer
        val emptySrc = ByteBuffer.allocate(0)
        val dstForEmpty = ByteBuffer.allocate(5)
        val initialDstPos = dstForEmpty.position
        dstForEmpty.put(emptySrc)
        assertEquals(initialDstPos, dstForEmpty.position, "Position should not change when putting an empty buffer")

        // Test putting when src.remaining > dst.remaining but fits
        val srcPartial = ByteBuffer.allocate(8)
        for (i in 0 until 8) srcPartial.put(i.toByte())
        srcPartial.flip()

        val dstPartial = ByteBuffer.allocate(10)
        dstPartial.position(3) // Make remaining 7, src has 8 remaining, but only 7 will be put

        // This case should throw BufferOverflowException because src.remaining() > dst.remaining()
        assertFailsWith<BufferOverflowException>("Should throw BufferOverflowException when src.remaining > dst.remaining") {
            dstPartial.put(srcPartial)
        }

        // Reset and test when src.remaining() <= dst.remaining()
        srcPartial.clear()
        for (i in 0 until 5) srcPartial.put(i.toByte()) // src has 5 elements
        srcPartial.flip()

        dstPartial.clear() // capacity 10
        dstPartial.put(srcPartial)
        assertEquals(5, dstPartial.position(), "Dst position should be 5 after putting 5 bytes")
        assertEquals(5, srcPartial.position(), "Src position should be 5 after being put")
        dstPartial.flip()
        for (i in 0 until 5) {
            assertEquals(i.toByte(), dstPartial.get(), "Data in dstPartial should match srcPartial data at index $i")
        }
    }

    @Test
    fun testPutShort() {
        val capacity = 10
        val value = 0x1234.toShort() // Example short value

        // Test with Big Endian
        var bufferBE = ByteBuffer.allocate(capacity)
        bufferBE.order(ByteOrder.BIG_ENDIAN)
        bufferBE.putShort(value)
        assertEquals(2, bufferBE.position, "Position should be 2 after putShort (BE)")
        bufferBE.flip()
        assertEquals(0x12.toByte(), bufferBE.get(), "First byte should be MSB (BE)")
        assertEquals(0x34.toByte(), bufferBE.get(), "Second byte should be LSB (BE)")

        // Test with Little Endian
        var bufferLE = ByteBuffer.allocate(capacity)
        bufferLE.order(ByteOrder.LITTLE_ENDIAN)
        bufferLE.putShort(value)
        assertEquals(2, bufferLE.position, "Position should be 2 after putShort (LE)")
        bufferLE.flip()
        assertEquals(0x34.toByte(), bufferLE.get(), "First byte should be LSB (LE)")
        assertEquals(0x12.toByte(), bufferLE.get(), "Second byte should be MSB (LE)")

        // Test edge case: insufficient space
        val smallBuffer = ByteBuffer.allocate(1) // Not enough for a Short
        assertFailsWith<BufferOverflowException>("Should throw BufferOverflowException for insufficient space") {
            smallBuffer.putShort(value)
        }

        // Test edge case: putting into a read-only buffer
        val readOnlyBuffer = ByteBuffer.allocate(capacity).asReadOnlyBuffer()
        assertFailsWith<ReadOnlyBufferException>("Should throw ReadOnlyBufferException for read-only buffer") {
            readOnlyBuffer.putShort(value)
        }

        // Test putting at the exact end of the buffer
        val exactEndBuffer = ByteBuffer.allocate(2)
        exactEndBuffer.putShort(value)
        assertEquals(2, exactEndBuffer.position())
        assertFailsWith<BufferOverflowException>("Should throw BufferOverflowException when trying to put past limit") {
            exactEndBuffer.putShort(0.toShort()) // Try to put one more
        }
    }

    @Test
    fun testPutInt() {
        val capacity = 10
        val value = 0x12345678 // Example int value

        // Test with Big Endian
        var bufferBE = ByteBuffer.allocate(capacity)
        bufferBE.order(ByteOrder.BIG_ENDIAN)
        bufferBE.putInt(value)
        assertEquals(4, bufferBE.position, "Position should be 4 after putInt (BE)")
        bufferBE.flip()
        assertEquals(0x12.toByte(), bufferBE.get(), "First byte should be MSB (BE)")
        assertEquals(0x34.toByte(), bufferBE.get(), "Second byte should be (BE)")
        assertEquals(0x56.toByte(), bufferBE.get(), "Third byte should be (BE)")
        assertEquals(0x78.toByte(), bufferBE.get(), "Fourth byte should be LSB (BE)")

        // Test with Little Endian
        var bufferLE = ByteBuffer.allocate(capacity)
        bufferLE.order(ByteOrder.LITTLE_ENDIAN)
        bufferLE.putInt(value)
        assertEquals(4, bufferLE.position, "Position should be 4 after putInt (LE)")
        bufferLE.flip()
        assertEquals(0x78.toByte(), bufferLE.get(), "First byte should be LSB (LE)")
        assertEquals(0x56.toByte(), bufferLE.get(), "Second byte should be (LE)")
        assertEquals(0x34.toByte(), bufferLE.get(), "Third byte should be (LE)")
        assertEquals(0x12.toByte(), bufferLE.get(), "Fourth byte should be MSB (LE)")

        // Test edge case: insufficient space
        val smallBuffer = ByteBuffer.allocate(3) // Not enough for an Int
        assertFailsWith<BufferOverflowException>("Should throw BufferOverflowException for insufficient space") {
            smallBuffer.putInt(value)
        }

        // Test edge case: putting into a read-only buffer
        val readOnlyBuffer = ByteBuffer.allocate(capacity).asReadOnlyBuffer()
        assertFailsWith<ReadOnlyBufferException>("Should throw ReadOnlyBufferException for read-only buffer") {
            readOnlyBuffer.putInt(value)
        }

        // Test putting at the exact end of the buffer
        val exactEndBuffer = ByteBuffer.allocate(4)
        exactEndBuffer.putInt(value)
        assertEquals(4, exactEndBuffer.position())
        assertFailsWith<BufferOverflowException>("Should throw BufferOverflowException when trying to put past limit") {
            exactEndBuffer.putInt(0) // Try to put one more
        }
    }

    @Test
    fun testRelativeBulkGet() {
        val bufferCapacity = 20
        val initialData = ByteArray(bufferCapacity) { (it * 10).toByte() } // 0, 10, 20,...
        val buffer = ByteBuffer.wrap(initialData)

        // Scenario 1: Basic get
        val dst1 = ByteArray(5)
        buffer.position(2) // Start reading from index 2 (value 20)
        val returnedBuffer1 = buffer.get(dst1, 0, 5)
        assertEquals(buffer, returnedBuffer1, "Returned buffer should be the same instance")
        assertContentEquals(ByteArray(5) { (it + 2) * 10.toByte() }, dst1, "Data in dst1 not as expected") // 20,30,40,50,60
        assertEquals(2 + 5, buffer.position, "Buffer position incorrect after basic get")

        // Scenario 2: Get with offset
        val dst2 = ByteArray(10)
        buffer.position(1) // Start reading from index 1 (value 10)
        buffer.get(dst2, 3, 4) // Read 4 bytes into dst2 starting at dst2[3]
        // Expected: dst2 = [0,0,0, 10,20,30,40, 0,0,0]
        val expectedDst2 = ByteArray(10)
        for (i in 0 until 4) {
            expectedDst2[3 + i] = ((1 + i) * 10).toByte()
        }
        assertContentEquals(expectedDst2, dst2, "Data in dst2 with offset not as expected")
        assertEquals(1 + 4, buffer.position, "Buffer position incorrect after get with offset")

        // Scenario 3: Get with default offset and length
        val dst3 = ByteArray(3)
        buffer.position(8) // Starts at 80
        buffer.get(dst3) // Reads 3 bytes (dst3.size)
        assertContentEquals(ByteArray(3) { (it + 8) * 10.toByte() }, dst3, "Data in dst3 (default args) not as expected") // 80,90,100
        assertEquals(8 + 3, buffer.position, "Buffer position incorrect after get with default args")

        // Edge Case: Reading more than remaining
        buffer.position(bufferCapacity - 3) // 3 bytes remaining
        val dstOverflow = ByteArray(4)
        assertFailsWith<BufferUnderflowException>("Should throw BufferUnderflowException when reading more than remaining") {
            buffer.get(dstOverflow)
        }
        assertEquals(bufferCapacity - 3, buffer.position, "Position should not change after failed get")


        // Edge Case: Invalid offset/length for destination array
        val dstInvalid = ByteArray(5)
        buffer.position(0)
        // Kotlin/JVM throws IndexOutOfBoundsException for array access violations.
        // The `require` in ByteBuffer.kt might intend IllegalArgumentException for parameter validation.
        // Let's test for what the current implementation likely throws or what `require` implies.
        // Given that `dst.size - offset < length` would be `5 - 3 < 3` (false, 2 < 3), this is an invalid combo.
        assertFailsWith<IndexOutOfBoundsException>("Should throw IndexOutOfBoundsException for invalid offset/length (offset + length > dst.size)") {
            buffer.get(dstInvalid, 3, 3) // offset 3, length 3 into a size 5 array
        }
        // offset < 0
        assertFailsWith<IndexOutOfBoundsException>("Should throw IndexOutOfBoundsException for negative offset") {
            buffer.get(dstInvalid, -1, 2)
        }
        // length < 0 (ByteBuffer.kt has explicit check for this, should be IllegalArgumentException)
        // However, underlying array copy may throw IOBE first if offset is also bad.
        // Let's assume a valid offset for this specific check if possible, or be aware of interaction.
        // The `require(length >= 0)` in the common code suggests IllegalArgumentException.
        // However, the actual array copy `arraycopy(hb, srcOffset, dst, offset, length)` might throw first.
        // For now, let's stick to IndexOutOfBounds as it's a common outcome for array issues.
        assertFailsWith<IllegalArgumentException>("Should throw IllegalArgumentException for negative length") {
            buffer.get(dstInvalid, 0, -1)
        }
        assertEquals(0, buffer.position, "Position should not change after failed get due to invalid args")

        // Test getting zero bytes
        val dstZero = ByteArray(5)
        val initialDstZeroContent = dstZero.copyOf()
        buffer.position(1)
        buffer.get(dstZero, 0, 0)
        assertEquals(1, buffer.position, "Position should not change when getting zero bytes")
        assertContentEquals(initialDstZeroContent, dstZero, "dstZero content should not change when getting zero bytes")
    }

    @Test
    fun testRelativeBulkPut() {
        val bufferCapacity = 20
        val buffer = ByteBuffer.allocate(bufferCapacity)

        // Scenario 1: Basic put
        val src1 = ByteArray(5) { (it + 1).toByte() } // 1, 2, 3, 4, 5
        buffer.position(2)
        val returnedBuffer1 = buffer.put(src1, 0, src1.size)
        assertEquals(buffer, returnedBuffer1, "Returned buffer should be the same instance")
        assertEquals(2 + src1.size, buffer.position, "Buffer position incorrect after basic put")
        buffer.flip() // To read back and verify
        buffer.position(2) // Move to where data was put
        val readBack1 = ByteArray(src1.size)
        buffer.get(readBack1)
        assertContentEquals(src1, readBack1, "Data in buffer not as expected after basic put")

        // Scenario 2: Put with offset and specific length
        buffer.clear()
        val src2 = ByteArray(10) { (it * 2).toByte() } // 0, 2, 4, ..., 18
        buffer.position(1)
        // Put 4 bytes from src2 (starting at src2[3] = 6) into buffer at current position
        buffer.put(src2, 3, 4) // data: 6, 8, 10, 12
        assertEquals(1 + 4, buffer.position, "Buffer position incorrect after put with offset/length")
        buffer.flip()
        buffer.position(1)
        val readBack2 = ByteArray(4)
        buffer.get(readBack2)
        val expectedSrc2Fragment = src2.sliceArray(3 until 7)
        assertContentEquals(expectedSrc2Fragment, readBack2, "Data in buffer not as expected after put with offset/length")

        // Scenario 3: Put with default offset and length
        buffer.clear()
        val src3 = ByteArray(3) { (it + 100).toByte() } // 100, 101, 102
        buffer.position(5)
        buffer.put(src3) // Puts all of src3
        assertEquals(5 + src3.size, buffer.position, "Buffer position incorrect after put with default args")
        buffer.flip()
        buffer.position(5)
        val readBack3 = ByteArray(src3.size)
        buffer.get(readBack3)
        assertContentEquals(src3, readBack3, "Data in buffer not as expected after put with default args")

        // Edge Case: Writing more data than remaining space
        buffer.clear()
        buffer.position(bufferCapacity - 3) // 3 bytes remaining
        val srcOverflow = ByteArray(4) { 1.toByte() }
        assertFailsWith<BufferOverflowException>("Should throw BufferOverflowException when writing more than remaining space") {
            buffer.put(srcOverflow)
        }
        assertEquals(bufferCapacity - 3, buffer.position, "Position should not change after failed put (overflow)")

        // Edge Case: Invalid offset/length for source array
        buffer.clear()
        val srcInvalid = ByteArray(5)
        // Similar to bulk get, expect IndexOutOfBounds or IllegalArgumentException
        assertFailsWith<IndexOutOfBoundsException>("Should throw IndexOutOfBoundsException for invalid offset/length (offset + length > src.size)") {
            buffer.put(srcInvalid, 3, 3) // offset 3, length 3 from a size 5 array
        }
        assertFailsWith<IndexOutOfBoundsException>("Should throw IndexOutOfBoundsException for negative offset") {
            buffer.put(srcInvalid, -1, 2)
        }
        assertFailsWith<IllegalArgumentException>("Should throw IllegalArgumentException for negative length") {
            buffer.put(srcInvalid, 0, -1)
        }
        assertEquals(0, buffer.position, "Position should not change after failed put due to invalid args")

        // Edge Case: Putting into a read-only buffer
        val readOnlyBuffer = ByteBuffer.allocate(10).asReadOnlyBuffer()
        val srcForReadOnly = ByteArray(5) { 1.toByte() }
        assertFailsWith<ReadOnlyBufferException>("Should throw ReadOnlyBufferException when putting into a read-only buffer") {
            readOnlyBuffer.put(srcForReadOnly)
        }

        // Test putting zero bytes
        buffer.clear()
        val initialBufferContent = ByteArray(bufferCapacity)
        buffer.get(0, initialBufferContent) // Save current state
        buffer.position(1)
        val srcZero = ByteArray(5)
        buffer.put(srcZero, 0, 0)
        assertEquals(1, buffer.position, "Position should not change when putting zero bytes")
        val afterPutZeroContent = ByteArray(bufferCapacity)
        buffer.get(0, afterPutZeroContent)
        assertContentEquals(initialBufferContent, afterPutZeroContent, "Buffer content should not change when putting zero bytes")
    }

    @Test
    fun testPutLong() {
        val capacity = 12
        val value = 0x0123456789ABCDEF_L // Example long value

        // Test with Big Endian
        var bufferBE = ByteBuffer.allocate(capacity)
        bufferBE.order(ByteOrder.BIG_ENDIAN)
        bufferBE.putLong(value)
        assertEquals(8, bufferBE.position, "Position should be 8 after putLong (BE)")
        bufferBE.flip()
        assertEquals(0x01.toByte(), bufferBE.get(), "Byte 0 (MSB) incorrect (BE)")
        assertEquals(0x23.toByte(), bufferBE.get(), "Byte 1 incorrect (BE)")
        assertEquals(0x45.toByte(), bufferBE.get(), "Byte 2 incorrect (BE)")
        assertEquals(0x67.toByte(), bufferBE.get(), "Byte 3 incorrect (BE)")
        assertEquals(0x89.toByte(), bufferBE.get(), "Byte 4 incorrect (BE)")
        assertEquals(0xAB.toByte(), bufferBE.get(), "Byte 5 incorrect (BE)")
        assertEquals(0xCD.toByte(), bufferBE.get(), "Byte 6 incorrect (BE)")
        assertEquals(0xEF.toByte(), bufferBE.get(), "Byte 7 (LSB) incorrect (BE)")

        // Test with Little Endian
        var bufferLE = ByteBuffer.allocate(capacity)
        bufferLE.order(ByteOrder.LITTLE_ENDIAN)
        bufferLE.putLong(value)
        assertEquals(8, bufferLE.position, "Position should be 8 after putLong (LE)")
        bufferLE.flip()
        assertEquals(0xEF.toByte(), bufferLE.get(), "Byte 0 (LSB) incorrect (LE)")
        assertEquals(0xCD.toByte(), bufferLE.get(), "Byte 1 incorrect (LE)")
        assertEquals(0xAB.toByte(), bufferLE.get(), "Byte 2 incorrect (LE)")
        assertEquals(0x89.toByte(), bufferLE.get(), "Byte 3 incorrect (LE)")
        assertEquals(0x67.toByte(), bufferLE.get(), "Byte 4 incorrect (LE)")
        assertEquals(0x45.toByte(), bufferLE.get(), "Byte 5 incorrect (LE)")
        assertEquals(0x23.toByte(), bufferLE.get(), "Byte 6 incorrect (LE)")
        assertEquals(0x01.toByte(), bufferLE.get(), "Byte 7 (MSB) incorrect (LE)")

        // Test edge case: insufficient space
        val smallBuffer = ByteBuffer.allocate(7) // Not enough for a Long
        assertFailsWith<BufferOverflowException>("Should throw BufferOverflowException for insufficient space") {
            smallBuffer.putLong(value)
        }

        // Test edge case: putting into a read-only buffer
        val readOnlyBuffer = ByteBuffer.allocate(capacity).asReadOnlyBuffer()
        assertFailsWith<ReadOnlyBufferException>("Should throw ReadOnlyBufferException for read-only buffer") {
            readOnlyBuffer.putLong(value)
        }

        // Test putting at the exact end of the buffer
        val exactEndBuffer = ByteBuffer.allocate(8)
        exactEndBuffer.putLong(value)
        assertEquals(8, exactEndBuffer.position())
        assertFailsWith<BufferOverflowException>("Should throw BufferOverflowException when trying to put past limit") {
            exactEndBuffer.putLong(0L) // Try to put one more
        }
    }

    @Test
    fun testRewind() {
        val capacity = 10
        val buffer = ByteBuffer.allocate(capacity)

        // Put some data and set position and limit
        buffer.put(1.toByte())
        buffer.put(2.toByte())
        buffer.put(3.toByte()) // position = 3
        val currentLimit = buffer.limit // limit = capacity = 10
        val currentPosition = buffer.position

        // Set a mark
        buffer.position(1)
        buffer.mark() // Mark at position 1

        // Call rewind
        buffer.position(currentPosition) // Restore position to where it was after puts
        val returnedBuffer = buffer.rewind()

        // Assertions
        assertEquals(buffer, returnedBuffer, "Returned buffer should be the same instance")
        assertEquals(0, buffer.position, "Position should be reset to 0 after rewind")
        assertEquals(currentLimit, buffer.limit, "Limit should remain unchanged after rewind")

        // Assert that mark is discarded (invalidated, check ByteBuffer.kt behavior, usually -1)
        // Attempting to reset to a discarded mark should throw InvalidMarkException
        assertFailsWith<InvalidMarkException>("Resetting after rewind should throw InvalidMarkException as mark is discarded") {
            buffer.reset()
        }

        // Further check: rewind on an empty buffer (already at pos 0)
        val emptyBuffer = ByteBuffer.allocate(5)
        val emptyLimit = emptyBuffer.limit
        emptyBuffer.rewind()
        assertEquals(0, emptyBuffer.position, "Position should be 0 for empty buffer rewind")
        assertEquals(emptyLimit, emptyBuffer.limit, "Limit should be unchanged for empty buffer rewind")

        // Rewind after flip
        buffer.clear()
        buffer.put(1.toByte()).put(2.toByte()).put(3.toByte()) // pos=3, lim=10
        buffer.flip() // pos=0, lim=3
        val limitAfterFlip = buffer.limit
        buffer.position(1) // pos=1
        buffer.mark() // mark at 1
        buffer.position(2) // pos=2
        buffer.rewind() // pos=0, lim=3, mark discarded
        assertEquals(0, buffer.position, "Position after flip and rewind")
        assertEquals(limitAfterFlip, buffer.limit, "Limit after flip and rewind")
        assertFailsWith<InvalidMarkException>("Resetting after flip and rewind should throw InvalidMarkException") {
            buffer.reset()
        }
    }
}
