package org.gnit.lucenekmp.jdkport

import okio.Buffer
import kotlin.test.*

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
}
