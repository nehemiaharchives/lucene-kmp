package org.gnit.lucenekmp.jdkport

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class KIOSourceInputStreamTest {

    private val logger = KotlinLogging.logger {}

    private fun createTestStream(data: ByteArray): KIOSourceInputStream {
        val source = Buffer().apply { write(data) }
        return KIOSourceInputStream(source)
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
        val inputStream = KIOSourceInputStream(buffer)
        // Try reading bytes directly
        val bytes = ByteArray(input.size)
        val readCount = inputStream.read(bytes, 0, bytes.size)

        logger.debug { "Directly read $readCount bytes: ${bytes.joinToString(", ") { it.toString() }}" }

        // Verify bytes match input
        assertEquals(input.size, readCount)
        assertContentEquals(input, bytes)
    }
}
