package org.gnit.lucenekmp.jdkport

import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.readByteArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KIOSinkOutputStreamTest {

    private fun createTestStream(): Pair<KIOSinkOutputStream, Buffer> {
        val buffer = Buffer()
        val outputStream = KIOSinkOutputStream(buffer)
        return Pair(outputStream, buffer)
    }

    @Test
    fun testWriteSingleByte() {
        val (outputStream, buffer) = createTestStream()

        outputStream.write(65) // Write ASCII 'A'
        outputStream.flush()

        val bytes = buffer.readByteArray(1)
        assertEquals("A", bytes.decodeToString())
    }

    @Test
    fun testWriteMultipleBytes() {
        val (outputStream, buffer) = createTestStream()

        val data = "Hello, World!".encodeToByteArray()
        for (byte in data) {
            outputStream.write(byte.toInt())
        }
        outputStream.flush()

        val bytes = buffer.readByteArray(data.size)
        assertEquals("Hello, World!", bytes.decodeToString())
    }

    @Test
    fun testWriteAfterClose() {
        val (outputStream, _) = createTestStream()

        outputStream.close()
        assertFailsWith<IOException> {
            outputStream.write(65)
        }
    }
}
