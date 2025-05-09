package org.gnit.lucenekmp.jdkport

import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlin.test.Test
import kotlin.test.assertEquals

class KIOSourceInputStreamTest {

    private fun createTestStream(data: ByteArray): KIOSourceInputStream {
        val buffer = Buffer().apply { write(data) }
        val source = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long {
                return buffer.read(sink, byteCount)
            }

            override fun close() {}
        }
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
        assertEquals(listOf(0, 1, 2, 3, 0), buffer.toList())
    }
}
