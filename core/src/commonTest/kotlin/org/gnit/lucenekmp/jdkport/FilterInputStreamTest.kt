package org.gnit.lucenekmp.jdkport

import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilterInputStreamTest {

    private fun createTestStream(data: ByteArray): FilterInputStream {
        val buffer = Buffer().apply { write(data) }
        val source = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long {
                return buffer.read(sink, byteCount)
            }

            override fun close() {}
        }
        return FilterInputStream(KIOSourceInputStream(source))
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
    fun testSkip() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val stream = createTestStream(data)
        assertEquals(2, stream.skip(2))
        assertEquals(3, stream.read())
    }

    @Test
    fun testAvailable() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val stream = createTestStream(data)
        assertEquals(5, stream.available())
        stream.read()
        assertEquals(4, stream.available())
    }

    @Test
    fun testClose() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val stream = createTestStream(data)
        stream.close()
        try {
            stream.read()
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("Stream closed"))
        }
    }

    @Test
    fun testMarkAndReset() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val stream = createTestStream(data)
        assertTrue(stream.markSupported())
        stream.mark(10)
        assertEquals(1, stream.read())
        assertEquals(2, stream.read())
        stream.reset()
        assertEquals(1, stream.read())
    }
}
