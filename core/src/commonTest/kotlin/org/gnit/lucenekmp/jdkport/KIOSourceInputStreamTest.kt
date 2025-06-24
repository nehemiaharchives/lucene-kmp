package org.gnit.lucenekmp.jdkport

import io.github.oshai.kotlinlogging.KotlinLogging
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class KIOSourceInputStreamTest {

    private val logger = KotlinLogging.logger {}

    private fun createTestStream(data: ByteArray): OkioSourceInputStream {
        val source = Buffer().apply { write(data) }
        return OkioSourceInputStream(source)
    }

    @Test
    fun testRead() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val stream = createTestStream(data)
        for (byte in data) {
            assertEquals(byte.toInt(), stream.read())
        }
        assertEquals(-1, stream.read())
    }

    @Test
    fun testAvailable() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val stream = createTestStream(data)
        assertEquals(data.size, stream.available())
        stream.read()
        assertEquals(data.size - 1, stream.available())
    }

    @Test
    fun testReadByteArray() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val stream = createTestStream(data)
        val buffer = ByteArray(5)
        val bytesRead = stream.read(buffer)
        assertEquals(5, bytesRead)
        assertEquals(data.toList(), buffer.toList())
    }

    @Test
    fun testReadByteArrayWithOffsetAndLength() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val stream = createTestStream(data)
        val buffer = ByteArray(5)
        val bytesRead = stream.read(buffer, 1, 3)
        assertEquals(3, bytesRead)
        assertEquals(listOf<Byte>(0, 1, 2, 3, 0), buffer.toList())
    }

    @Test
    fun testReadUtf8String() {
        val input = "Hello, World!".encodeToByteArray()
        val stream = createTestStream(input)
        val buffer = ByteArray(input.size)
        val bytesRead = stream.read(buffer)
        assertEquals(input.size, bytesRead)
        assertEquals(input.toList(), buffer.toList())

        // Verify the content can be decoded back to the original string
        val decodedString = buffer.decodeToString()
        assertEquals("Hello, World!", decodedString)
    }

    @Test
    fun testDirectStreamReading() {
        val input = "Hello, World!".encodeToByteArray()
        val buffer = Buffer().apply { write(input) }
        val inputStream = OkioSourceInputStream(buffer)
        // Try reading bytes directly
        val bytes = ByteArray(input.size)
        val readCount = inputStream.read(bytes, 0, bytes.size)

        logger.debug { "Directly read $readCount bytes: ${bytes.joinToString(", ") { it.toString() }}" }

        // Verify bytes match input
        assertEquals(input.size, readCount)
        assertContentEquals(input, bytes)
    }


    @Test
    fun testReadReturnsZeroWhenSourceReturnsZero() {
        // 1. Create a custom Source that returns 0 once, then reads from a buffer.
        val dataBuffer = Buffer().write(byteArrayOf(10, 20, 30))
        var hasReturnedZero = false
        val customSource = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long {
                if (!hasReturnedZero) {
                    hasReturnedZero = true
                    // Simulate a source that is temporarily unavailable but not at EOF.
                    return 0L
                }
                // Subsequent reads should function normally.
                return dataBuffer.read(sink, byteCount)
            }

            override fun timeout() = Timeout.NONE
            override fun close() = dataBuffer.close()
        }

        // 2. Wrap the custom source in the InputStream.
        val inputStream = OkioSourceInputStream(customSource.buffer())
        val readBuffer = ByteArray(10)

        // 3. The first call to read() should return 0.
        // The buggy code would incorrectly return -1 here.
        val firstRead = inputStream.read(readBuffer, 0, 10)
        assertEquals(0, firstRead, "InputStream.read should return 0 when the underlying source returns 0.")

        // 4. Subsequent reads should succeed.
        val secondRead = inputStream.read(readBuffer, 0, 10)
        assertEquals(3, secondRead, "InputStream should read data on subsequent calls.")
        val expectedData = byteArrayOf(10, 20, 30)
        assertContentEquals(expectedData, readBuffer.sliceArray(0 until 3))
    }
}
