package org.gnit.lucenekmp.jdkport

import kotlinx.io.Buffer
import kotlinx.io.Sink
import kotlinx.io.buffer
import kotlinx.io.sink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KIOSinkOutputStreamTest {

    @Test
    fun testWriteSingleByte() {
        val buffer = Buffer()
        val sink: Sink = buffer.sink()
        val outputStream = KIOSinkOutputStream(sink)

        outputStream.write(65) // Write ASCII 'A'
        outputStream.flush()

        assertEquals("A", buffer.readUtf8())
    }

    @Test
    fun testWriteMultipleBytes() {
        val buffer = Buffer()
        val sink: Sink = buffer.sink()
        val outputStream = KIOSinkOutputStream(sink)

        val data = "Hello, World!".encodeToByteArray()
        for (byte in data) {
            outputStream.write(byte.toInt())
        }
        outputStream.flush()

        assertEquals("Hello, World!", buffer.readUtf8())
    }

    @Test
    fun testWriteAfterClose() {
        val buffer = Buffer()
        val sink: Sink = buffer.sink()
        val outputStream = KIOSinkOutputStream(sink)

        outputStream.close()
        assertFailsWith<IllegalStateException> {
            outputStream.write(65)
        }
    }
}
