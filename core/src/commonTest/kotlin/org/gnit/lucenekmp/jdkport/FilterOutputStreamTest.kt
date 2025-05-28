package org.gnit.lucenekmp.jdkport

import okio.Buffer
import okio.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

// Test-specific subclass that exposes the constructor and the out field
class TestFilterOutputStream(output: OutputStream) : FilterOutputStream(output) {
    fun getOutputStream(): OutputStream = out
}

class FilterOutputStreamTest {

    private fun createTestStream(): Pair<TestFilterOutputStream, Buffer> {
        val buffer = Buffer()
        val outputStream = TestFilterOutputStream(OkioSinkOutputStream(buffer))
        return Pair(outputStream, buffer)
    }

    @Test
    fun testWrite() {
        val (stream, buffer) = createTestStream()
        stream.write(1)
        stream.write(byteArrayOf(2, 3, 4, 5))
        stream.write(byteArrayOf(6, 7, 8, 9, 10), 2, 3)

        // Read the bytes from the buffer and verify
        val bytes = buffer.readByteArray(8)
        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5, 8, 9, 10), bytes)
    }

    @Test
    fun testFlush() {
        val (stream, buffer) = createTestStream()
        stream.write(1)
        stream.flush()

        // Read the byte from the buffer and verify
        val bytes = buffer.readByteArray(1)
        assertContentEquals(byteArrayOf(1), bytes)
    }

    @Test
    fun testClose() {
        val (stream, _) = createTestStream()
        stream.write(1)
        stream.close()
        assertFailsWith<IOException> { stream.write(2) }
    }
}
