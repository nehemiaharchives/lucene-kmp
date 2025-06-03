package org.gnit.lucenekmp.jdkport

import kotlin.test.*
import org.gnit.lucenekmp.jdkport.CheckedOutputStream
import org.gnit.lucenekmp.jdkport.Checksum
import org.gnit.lucenekmp.jdkport.OutputStream // Assuming this is the base OutputStream in the jdkport
import okio.IOException // Corrected IOException import
import io.github.oshai.kotlinlogging.KotlinLogging

// Mock implementations will be added here later

class MockOutputStream : OutputStream() {
    private val writtenData = StringBuilder()
    var throwOnWrite = false
    var flushCalled = false
    var closeCalled = false
    private var streamClosed: Boolean = false // Renamed to avoid conflict if base class gets 'closed'

    override fun write(b: Int) {
        if (streamClosed) { // Behavior for writing to a closed stream
            throw IOException("Stream closed")
        }
        if (throwOnWrite) {
            throw IOException("Simulated IOException")
        }
        writtenData.append(b.toChar())
    }

    override fun flush() {
        if (streamClosed) {
             // Optionally throw IOException if flush is called on a closed stream,
             // or just do nothing/log it, depending on desired mock behavior.
            logger.warn { "MockOutputStream.flush() called on a closed stream" }
            // For now, let it proceed to set flushCalled for testing purposes even if closed.
        }
        logger.info { "MockOutputStream.flush() called" }
        flushCalled = true
        // super.flush() // No super.flush() in the base OutputStream typically
    }

    override fun close() {
        logger.info { "MockOutputStream.close() called" }
        closeCalled = true
        streamClosed = true
        // super.close() // No super.close() in the base OutputStream typically that manages state for FilterOutputStream
    }

    fun getWrittenData(): String = writtenData.toString()
    fun isStreamClosed(): Boolean = streamClosed // Custom method to check closed state

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}

class MockChecksum : Checksum {
    private var checksumValue: Long = 0

    override fun update(b: Int) {
        checksumValue += b.toLong()
    }

    override fun update(b: ByteArray, off: Int, len: Int) {
        for (i in off until off + len) {
            checksumValue += b[i].toLong()
        }
    }

    override fun getValue(): Long = checksumValue

    override fun reset() {
        checksumValue = 0
    }
}

class CheckedOutputStreamTest {
    private val logger = KotlinLogging.logger {}

    private lateinit var mockOut: MockOutputStream
    private lateinit var mockChecksum: MockChecksum
    private lateinit var checkedOut: CheckedOutputStream

    @BeforeTest
    fun setUp() {
        mockOut = MockOutputStream()
        mockChecksum = MockChecksum()
        checkedOut = CheckedOutputStream(mockOut, mockChecksum)
    }

    @Test
    fun testWriteSingleByte() {
        logger.info { "Test: Writing a single byte" }
        val byteToWrite = 'A'.code
        checkedOut.write(byteToWrite)

        assertEquals("A", mockOut.getWrittenData(), "Data should be written to the underlying stream")
        assertEquals(byteToWrite.toLong(), mockChecksum.getValue(), "Checksum should be updated")
        assertEquals(byteToWrite.toLong(), checkedOut.checksum.getValue(), "CheckedOutputStream checksum should match")
    }

    @Test
    fun testWriteByteArray() {
        logger.info { "Test: Writing a byte array" }
        val data = "Hello".encodeToByteArray()
        checkedOut.write(data)

        assertEquals("Hello", mockOut.getWrittenData(), "Data should be written to the underlying stream")
        val expectedChecksum = data.sumOf { it.toLong() }
        assertEquals(expectedChecksum, mockChecksum.getValue(), "Checksum should be updated")
        assertEquals(expectedChecksum, checkedOut.checksum.getValue(), "CheckedOutputStream checksum should match")
    }

    @Test
    fun testWriteByteArrayWithOffsetAndLength() {
        logger.info { "Test: Writing a byte array with offset and length" }
        val data = "WorldData".encodeToByteArray()
        // Write "orld" from "WorldData"
        // offset = 1, len = 4
        checkedOut.write(data, 1, 4)

        assertEquals("orld", mockOut.getWrittenData(), "Correct portion of data should be written")
        val expectedChecksum = data.sliceArray(1..4).sumOf { it.toLong() }
        assertEquals(expectedChecksum, mockChecksum.getValue(), "Checksum should be updated for the written portion")
        assertEquals(expectedChecksum, checkedOut.checksum.getValue(), "CheckedOutputStream checksum should match")
    }
    
    @Test
    fun testGetChecksum() {
        logger.info { "Test: Retrieving checksum" }
        val data = "Test".encodeToByteArray()
        checkedOut.write(data)

        val expectedChecksum = data.sumOf { it.toLong() }
        assertEquals(expectedChecksum, checkedOut.checksum.getValue(), "getChecksum() should return the correct checksum value")
        assertEquals(mockChecksum.getValue(), checkedOut.checksum.getValue(), "Checksum from CheckedOutputStream and MockChecksum should be identical")
    }

    @Test
    fun testIOExceptionDuringWrite() {
        logger.info { "Test: IOException during write" }
        mockOut.throwOnWrite = true
        val byteToWrite = 'X'.code
        
        val exception = assertFailsWith<IOException>(message = "Should throw IOException when underlying stream fails") {
            checkedOut.write(byteToWrite)
        }
        assertEquals("Simulated IOException", exception.message, "Exception message should match")
        assertEquals(0L, mockChecksum.getValue(), "Checksum should not be updated if write fails")
    }

    @Test
    fun testFlush() {
        logger.info { "Test: Flushing the stream" }
        checkedOut.flush()
        assertTrue(mockOut.flushCalled, "flush() should be called on the underlying stream")
    }

    @Test
    fun testClose() {
        logger.info { "Test: Closing the stream" }
        assertFalse(mockOut.isStreamClosed(), "Underlying stream should not be closed initially")

        checkedOut.close() // This should close mockOut

        assertTrue(mockOut.flushCalled, "flush() should be called on the underlying stream when close() is called on CheckedOutputStream")
        assertTrue(mockOut.closeCalled, "close() should be called on the underlying stream")
        assertTrue(mockOut.isStreamClosed(), "Underlying stream should be marked as closed by mock")
        
        // Verify CheckedOutputStream behavior by attempting a write
        val exception = assertFailsWith<IOException>(message = "Should throw IOException when writing to a closed CheckedOutputStream") {
            checkedOut.write('Z'.code)
        }
        assertEquals("Stream closed", exception.message, "Exception message for writing to closed CheckedOutputStream should match underlying mock stream's message")
    }

    @Test
    fun testIsClosedBehavior() { // Renamed to reflect behavior testing
        logger.info { "Test: Checking stream closed state via behavior" }
        // Check initial state: should not throw
        try {
            checkedOut.write('A'.code) // A write should succeed
            mockOut.getWrittenData() // to clear it for the next check
        } catch (e: IOException) {
            fail("Writing to a newly created stream should not fail.")
        }

        checkedOut.close()
        
        // Check closed state: should throw
        val exception = assertFailsWith<IOException>(message = "Should throw IOException when writing to a closed stream after close()") {
            checkedOut.write('B'.code)
        }
        assertEquals("Stream closed", exception.message, "Exception message for writing to closed stream should match")
    }

    @Test
    fun testWriteToClosedStream() {
        logger.info { "Test: Writing to a closed stream" }
        checkedOut.close() // Close the CheckedOutputStream

        // Verify the underlying mock stream is also closed
        assertTrue(mockOut.isStreamClosed(), "Underlying mock stream should be closed")

        val exception = assertFailsWith<IOException>(message = "Should throw IOException when writing to a closed stream") {
            checkedOut.write('Y'.code)
        }
        assertEquals("Stream closed", exception.message, "Exception message for writing to closed stream should match")
    }
}
