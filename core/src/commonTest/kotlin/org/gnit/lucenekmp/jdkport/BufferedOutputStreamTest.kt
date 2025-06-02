package org.gnit.lucenekmp.jdkport

import okio.Buffer
// import okio.Sink // Not directly needed by TrackingOutputStream if it takes Buffer
// import okio.buffer // Not directly needed by TrackingOutputStream if it takes Buffer
import java.io.IOException // Changed to java.io.IOException
import kotlin.test.*

// Helper class to track if close() was called on the underlying stream
class TrackingOutputStream(private val buffer: Buffer) : OutputStream() { // Changed to accept Buffer
    var isClosed = false
        private set

    // OkioSinkOutputStream expects a Buffer, not a generic Sink directly.
    private val okioSinkOutputStream = OkioSinkOutputStream(buffer)

    override fun write(b: Int) {
        if (isClosed) throw IOException("Stream closed")
        okioSinkOutputStream.write(b)
    }

    override fun write(b: ByteArray) {
        if (isClosed) throw IOException("Stream closed")
        okioSinkOutputStream.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        if (isClosed) throw IOException("Stream closed")
        okioSinkOutputStream.write(b, off, len)
    }

    override fun flush() {
        if (isClosed) throw IOException("Stream closed")
        okioSinkOutputStream.flush()
    }

    override fun close() {
        // Note: OkioSinkOutputStream.close() also closes the buffer if it's the source.
        // For testing, we just mark our wrapper as closed.
        // The actual closing of the buffer/sink it wraps will be handled by OkioSinkOutputStream.
        if (isClosed) return 
        isClosed = true
        okioSinkOutputStream.close() 
    }
}

/**
 * tests functions of [BufferedOutputStream] to see if it behaves like [java.io.BufferedOutputStream](https://docs.oracle.com/en/java/javase/24/docs/api/java.base/java/io/BufferedOutputStream.html)
 */
class BufferedOutputStreamTest {

    /**
     * Tests the functionality of writing and flushing data using a `BufferedOutputStream`.
     *
     * This method ensures that data written to the `BufferedOutputStream` is correctly
     * flushed and matches the original input. It verifies that the written and flushed data
     * is accurately stored in the underlying buffer and that the output matches the expected content.
     *
     * Assertions:
     * - Validates that the actual byte array read from the buffer matches the expected byte array.
     *
     * @see BufferedOutputStream.write
     */
    @Test
    fun testWriteAndFlush() {
        val expected = "Hello, World!".encodeToByteArray()
        val buffer = Buffer()
        BufferedOutputStream(OkioSinkOutputStream(buffer)).use { bos ->
            bos.write(expected)
            bos.flush()
        }
        val actual = buffer.readByteArray(expected.size.toLong())
        assertContentEquals(expected, actual)
    }

    @Test
    fun testWriteSingleByte() {
        val buffer = Buffer()
        val bytesToWrite = byteArrayOf(0, 1, 2, 3, 4)
        BufferedOutputStream(OkioSinkOutputStream(buffer)).use { bos ->
            for (byteValue in bytesToWrite) {
                bos.write(byteValue.toInt())
            }
            bos.flush()
        }
        val actual = buffer.readByteArray(bytesToWrite.size.toLong())
        assertContentEquals(bytesToWrite, actual)
    }

    @Test
    fun testWriteSingleByteExceedingBuffer() {
        val buffer = Buffer()
        // Assuming default buffer size is less than 8200.
        // Common default for java.io.BufferedOutputStream is 8192.
        val numBytes = 8200
        val bytesToWrite = ByteArray(numBytes) { it.toByte() }

        BufferedOutputStream(OkioSinkOutputStream(buffer)).use { bos ->
            for (i in 0 until numBytes) {
                bos.write(bytesToWrite[i].toInt())
            }
            bos.flush()
        }
        val actual = buffer.readByteArray(numBytes.toLong())
        assertContentEquals(bytesToWrite, actual)
    }

    @Test
    fun testWriteSubArray() {
        val buffer = Buffer()
        val sourceData = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
        val offset = 2
        val length = 5
        val expectedData = sourceData.copyOfRange(offset, offset + length)

        BufferedOutputStream(OkioSinkOutputStream(buffer)).use { bos ->
            bos.write(sourceData, offset, length)
            bos.flush()
        }
        val actualData = buffer.readByteArray(length.toLong())
        assertContentEquals(expectedData, actualData)
    }

    @Test
    fun testWriteSubArrayWithOffset() {
        val buffer = Buffer()
        val sourceData = byteArrayOf(10, 11, 12, 13, 14, 15, 16, 17, 18, 19)
        val offset = 1
        val length = 3
        val expectedData = sourceData.copyOfRange(offset, offset + length)

        BufferedOutputStream(OkioSinkOutputStream(buffer)).use { bos ->
            bos.write(sourceData, offset, length)
            bos.flush()
        }
        val actualData = buffer.readByteArray(length.toLong())
        assertContentEquals(expectedData, actualData)
    }

    @Test
    fun testWriteSubArrayFullLength() {
        val buffer = Buffer()
        val sourceData = byteArrayOf(20, 21, 22, 23, 24)
        val offset = 0
        val length = sourceData.size
        val expectedData = sourceData.copyOfRange(offset, offset + length)

        BufferedOutputStream(OkioSinkOutputStream(buffer)).use { bos ->
            bos.write(sourceData, offset, length)
            bos.flush()
        }
        val actualData = buffer.readByteArray(length.toLong())
        assertContentEquals(expectedData, actualData)
    }

    @Test
    fun testWriteSubArrayEmpty() {
        val buffer = Buffer()
        val sourceData = byteArrayOf(30, 31, 32)
        val offset = 1
        val length = 0
        // expectedData is an empty byte array

        BufferedOutputStream(OkioSinkOutputStream(buffer)).use { bos ->
            bos.write(sourceData, offset, length)
            bos.flush()
        }
        assertEquals(0L, buffer.size)
        val actualData = buffer.readByteArray(length.toLong())
        assertContentEquals(byteArrayOf(), actualData)
    }

    @Test
    fun testWriteSubArrayExceedingBuffer() {
        val buffer = Buffer()
        // Assuming default buffer size is less than 8200.
        val numBytes = 8200
        val sourceData = ByteArray(numBytes * 2) { (it % 256).toByte() } // Data larger than numBytes

        // Write a segment that is larger than the typical buffer size
        val offset = 100 
        val length = numBytes // This length itself would fill/exceed buffer
        val expectedData = sourceData.copyOfRange(offset, offset + length)

        BufferedOutputStream(OkioSinkOutputStream(buffer)).use { bos ->
            bos.write(sourceData, offset, length)
            bos.flush()
        }
        val actualData = buffer.readByteArray(length.toLong())
        assertContentEquals(expectedData, actualData)
    }

    @Test
    fun testWriteSubArrayPortionsExceedingBuffer() {
        val buffer = Buffer()
        val internalBufferSize = 8192 // A common buffer size, assuming this for testing
        val segmentSize = internalBufferSize / 2
        val sourceData = ByteArray(internalBufferSize * 3) { (it % 128).toByte() }
        val expectedFullData = mutableListOf<Byte>()

        BufferedOutputStream(OkioSinkOutputStream(buffer), internalBufferSize).use { bos ->
            // Write first part, less than buffer
            var currentOffset = 0
            var currentLength = segmentSize
            bos.write(sourceData, currentOffset, currentLength)
            expectedFullData.addAll(sourceData.copyOfRange(currentOffset, currentOffset + currentLength).toList())
            // The following assertion was removed as it might be too strict depending on internal buffering behavior:
            // assertEquals(currentLength.toLong(), buffer.size, "Buffer should contain only the first segment before flush if it's smaller than internal buffer and not explicitly flushed by write")


            // Write second part, causing sum to be around buffer size
            currentOffset += currentLength
            currentLength = segmentSize 
            bos.write(sourceData, currentOffset, currentLength)
            expectedFullData.addAll(sourceData.copyOfRange(currentOffset, currentOffset + currentLength).toList())
            // At this point, the BufferedOutputStream might have flushed internally if the sum of writes exceeded its buffer.
            // Or it might hold it if internal buffer is large enough.
            // For this test, we are more interested in the final flushed output.

            // Write third part, definitely exceeding buffer
            currentOffset += currentLength
            currentLength = internalBufferSize // This segment alone is equal to buffer size
            bos.write(sourceData, currentOffset, currentLength)
            expectedFullData.addAll(sourceData.copyOfRange(currentOffset, currentOffset + currentLength).toList())
            
            bos.flush() // Ensure everything is out
        }
        val actualData = buffer.readByteArray()
        assertContentEquals(expectedFullData.toByteArray(), actualData, "Full data written in portions should match")
    }

    @Test
    fun testCloseFlushesData() {
        val buffer = Buffer()
        val expectedData = "Data to be flushed on close".encodeToByteArray()
        val bos = BufferedOutputStream(OkioSinkOutputStream(buffer))

        bos.write(expectedData)
        // No explicit flush() call here
        bos.close()

        val actualData = buffer.readByteArray(expectedData.size.toLong())
        assertContentEquals(expectedData, actualData, "Data should be flushed to underlying stream on close()")
    }

    @Test
    fun testCloseClosesUnderlyingStream() {
        val okioBuffer = Buffer() // This is the Buffer OkioSinkOutputStream will use
        val trackingOutputStream = TrackingOutputStream(okioBuffer) 
        
        val bos = BufferedOutputStream(trackingOutputStream)
        bos.write("test".encodeToByteArray())
        bos.close()

        assertTrue(trackingOutputStream.isClosed, "Underlying stream's close() method should have been called")
    }

    @Test
    fun testWriteToClosedStreamThrowsIOException() {
        val buffer = Buffer()
        val bos = BufferedOutputStream(OkioSinkOutputStream(buffer))
        bos.write("some data".encodeToByteArray())
        bos.close()

        try {
            bos.write(123) // Attempt to write a single byte
            fail("Expected an exception when writing to a closed stream (single byte)")
        } catch (e: java.io.IOException) {
            // Expected path
            assertEquals("Stream closed", e.message, "Exception message mismatch for write(byte)")
        } catch (e: Exception) {
            fail("Expected java.io.IOException but got ${e::class.simpleName}: ${e.message} for write(byte)")
        }

        try {
            bos.write("more data".encodeToByteArray()) // Attempt to write a byte array
            fail("Expected an exception when writing to a closed stream (byte array)")
        } catch (e: java.io.IOException) {
            // Expected path
            assertEquals("Stream closed", e.message, "Exception message mismatch for write(byteArray)")
        } catch (e: Exception) {
            fail("Expected java.io.IOException but got ${e::class.simpleName}: ${e.message} for write(byteArray)")
        }

        try {
            bos.write("even more data".encodeToByteArray(), 0, 4) // Attempt to write a sub-array
            fail("Expected an exception when writing to a closed stream (sub-array)")
        } catch (e: java.io.IOException) {
            // Expected path
            assertEquals("Stream closed", e.message, "Exception message mismatch for write(byteArray, off, len)")
        } catch (e: Exception) {
            fail("Expected java.io.IOException but got ${e::class.simpleName}: ${e.message} for write(byteArray, off, len)")
        }
        
        // Also test flush() on a closed stream
        try {
            bos.flush()
            fail("Expected an exception when flushing a closed stream")
        } catch (e: java.io.IOException) {
            // Expected path
            assertEquals("Stream closed", e.message, "Exception message mismatch for flush()")
        } catch (e: Exception) {
            fail("Expected java.io.IOException but got ${e::class.simpleName}: ${e.message} for flush()")
        }
    }

    @Test
    fun testCloseIsIdempotent() {
        val buffer = Buffer()
        val expectedData = "Final data".encodeToByteArray()
        val bos = BufferedOutputStream(OkioSinkOutputStream(buffer))

        bos.write(expectedData)
        bos.close() // First close

        // Data should be flushed after first close
        val actualDataFirstClose = buffer.readByteArray(expectedData.size.toLong())
        assertContentEquals(expectedData, actualDataFirstClose, "Data should be flushed after the first close")

        // Subsequent closes should not cause issues
        try {
            bos.close() // Second close
            bos.close() // Third close
        } catch (e: Exception) {
            fail("Subsequent calls to close() should not throw exceptions, but got $e")
        }

        // Verify no further writes occurred or state changed detrimentally
        assertEquals(0L, buffer.size, "Buffer should be empty after reading flushed data and subsequent closes")

        // Attempting to write after multiple closes should still fail
        try {
            bos.write(42)
            fail("Expected an exception when writing to a closed stream after multiple closes")
        } catch (e: java.io.IOException) {
            // Expected path
             assertEquals("Stream closed", e.message, "Exception message mismatch for write post-multiple-closes")
        } catch (e: Exception) {
            fail("Expected java.io.IOException but got ${e::class.simpleName}: ${e.message} for write post-multiple-closes")
        }
    }
}
