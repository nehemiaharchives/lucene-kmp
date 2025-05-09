package org.gnit.lucenekmp.jdkport

import kotlinx.io.Buffer
import kotlinx.io.Sink
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FilterOutputStreamTest {

    private fun createTestStream(): FilterOutputStream {
        val buffer = Buffer()
        val sink = object : Sink {
            override fun write(source: Buffer, byteCount: Long) {
                buffer.write(source, byteCount)
            }

            override fun flush() {}

            override fun close() {}
        }
        return FilterOutputStream(KIOSinkOutputStream(sink))
    }

    @Test
    fun testWrite() {
        val stream = createTestStream()
        stream.write(1)
        stream.write(byteArrayOf(2, 3, 4, 5))
        stream.write(byteArrayOf(6, 7, 8, 9, 10), 2, 3)
        assertEquals(1, stream.out.buffer[0].toInt())
        assertEquals(2, stream.out.buffer[1].toInt())
        assertEquals(3, stream.out.buffer[2].toInt())
        assertEquals(4, stream.out.buffer[3].toInt())
        assertEquals(5, stream.out.buffer[4].toInt())
        assertEquals(8, stream.out.buffer[5].toInt())
        assertEquals(9, stream.out.buffer[6].toInt())
        assertEquals(10, stream.out.buffer[7].toInt())
    }

    @Test
    fun testFlush() {
        val stream = createTestStream()
        stream.write(1)
        stream.flush()
        assertEquals(1, stream.out.buffer[0].toInt())
    }

    @Test
    fun testClose() {
        val stream = createTestStream()
        stream.write(1)
        stream.close()
        assertFailsWith<IOException> { stream.write(2) }
    }
}
