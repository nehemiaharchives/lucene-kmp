package org.gnit.lucenekmp.jdkport

import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.Source
import kotlin.test.*

/**
 * tests functions of [BufferedInputStream] to see if it behaves like [java.io.BufferedInputStream](https://docs.oracle.com/en/java/javase/24/docs/api/java.base/java/io/BufferedInputStream.html)
 */
class BufferedInputStreamTest {

    private val HELLO_WORLD_BYTES = "Hello, World!".encodeToByteArray()
    private fun byteArraySource(data: ByteArray): Source = Buffer().apply { write(data) }

    @Test
    fun testReadSingleBytes() {
        val inputStream = BufferedInputStream(KIOSourceInputStream(byteArraySource(HELLO_WORLD_BYTES)))
        val actual = ByteArray(HELLO_WORLD_BYTES.size)
        for (i in actual.indices) {
            val b = inputStream.read()
            assertTrue(b >= 0, "Expected valid byte at position $i but got end-of-stream")
            actual[i] = b.toByte()
        }
        assertContentEquals(HELLO_WORLD_BYTES, actual)
        assertEquals(-1, inputStream.read(), "Expected end-of-stream after all bytes read")
    }

    @Test
    fun testReadWithOffsetAndLength() {
        val data = "Hello, World!".encodeToByteArray()
        val inputStream = BufferedInputStream(KIOSourceInputStream(byteArraySource(data)))

        // 1. Read the full content into a buffer with offset.
        val buffer = ByteArray(data.size + 4) { 0x7A.toByte() } // fill with 'z' for visibility
        val bytesRead = inputStream.read(buffer, off = 2, len = data.size)
        assertEquals(data.size, bytesRead)
        assertContentEquals(byteArrayOf(0x7A,0x7A) + data + byteArrayOf(0x7A,0x7A), buffer)

        // 2. Reading again should return -1 (EOF)
        assertEquals(-1, inputStream.read(buffer, 0, buffer.size))

        // 3. 0-length read should return 0 and not modify the buffer
        buffer.fill(0x44)
        assertEquals(0, inputStream.read(buffer, 1, 0))
        assertTrue(buffer.all { it == 0x44.toByte() })

        // 4. Negative offset should throw
        assertFailsWith<IndexOutOfBoundsException> {
            inputStream.read(buffer, -1, 3)
        }

        // 5. Negative length should throw
        assertFailsWith<IndexOutOfBoundsException> {
            inputStream.read(buffer, 0, -1)
        }

        // 6. Read past end of buffer should throw
        assertFailsWith<IndexOutOfBoundsException> {
            inputStream.read(buffer, buffer.size - 1, 2)
        }
    }

    @Test
    fun testAvailableReportsCorrectValues() {
        val data = "abcde".encodeToByteArray()
        val inputStream = BufferedInputStream(KIOSourceInputStream(byteArraySource(data)), 2)

        // Initially, all bytes should be available
        assertEquals(data.size, inputStream.available())

        // Read one byte, available should decrease by one
        inputStream.read()
        assertEquals(data.size - 1, inputStream.available())

        // Skip remaining bytes, available should be 0
        inputStream.skip((data.size - 1).toLong())
        assertEquals(0, inputStream.available())
    }

    @Test
    fun testAvailableOnClosedStreamThrows() {
        val data = "abcde".encodeToByteArray()
        val inputStream = BufferedInputStream(KIOSourceInputStream(byteArraySource(data)))
        inputStream.close()
        assertFailsWith<IOException> { inputStream.available() }
    }

    @Test
    fun testMarkAndResetRestoreReadPosition() {
        val data = "abcdefgh".encodeToByteArray()
        val inputStream = BufferedInputStream(KIOSourceInputStream(byteArraySource(data)), 4)

        inputStream.mark(100)
        val read1 = inputStream.read()
        val read2 = inputStream.read()

        inputStream.reset()
        val reread1 = inputStream.read()
        val reread2 = inputStream.read()

        assertEquals('a'.code, read1)
        assertEquals('b'.code, read2)
        assertEquals('a'.code, reread1)
        assertEquals('b'.code, reread2)
    }

    @Test
    fun testResetWithoutMarkThrows() {
        val data = "xyz".encodeToByteArray()
        val inputStream = BufferedInputStream(KIOSourceInputStream(byteArraySource(data)))

        // Don't call mark()
        assertFailsWith<IOException> {
            inputStream.reset()
        }
    }

    @Test
    fun testResetAfterMarkInvalidatedThrows() {
        val data = "ABCDEFG".encodeToByteArray()
        val inputStream = BufferedInputStream(KIOSourceInputStream(byteArraySource(data)), 2)

        inputStream.mark(1) // small marklimit
        // Read more than marklimit to invalidate the mark
        inputStream.read()
        inputStream.read()
        inputStream.read() // should invalidate mark

        assertFailsWith<IOException> {
            inputStream.reset()
        }
    }

    @Test
    fun testMultipleMarksAndResets() {
        val data = "helloworld".encodeToByteArray()
        val inputStream = BufferedInputStream(KIOSourceInputStream(byteArraySource(data)), 5)

        inputStream.mark(10)
        val first = ByteArray(5)
        inputStream.read(first)
        assertContentEquals("hello".encodeToByteArray(), first)

        inputStream.mark(10)
        val second = ByteArray(5)
        inputStream.read(second)
        assertContentEquals("world".encodeToByteArray(), second)

        inputStream.reset()
        val secondAgain = ByteArray(5)
        inputStream.read(secondAgain)
        assertContentEquals("world".encodeToByteArray(), secondAgain)
    }

    @Test
    fun testSkip() {
        val data = "abcdef".encodeToByteArray()
        val inputStream = BufferedInputStream(KIOSourceInputStream(byteArraySource(data)), 3)
        // skip 2 bytes
        val skipped = inputStream.skip(2)
        assertEquals(2, skipped)
        val expected = "cdef".encodeToByteArray()
        val remaining = ByteArray(expected.size)
        val read = inputStream.read(remaining)
        assertEquals(expected.size, read)
        assertContentEquals(expected, remaining)
    }

    @Test
    fun testSkipPastEnd() {
        val data = "abc".encodeToByteArray()
        val inputStream = BufferedInputStream(KIOSourceInputStream(byteArraySource(data)), 2)
        assertEquals(3, inputStream.skip(10)) // more than length, should only skip available
        assertEquals(-1, inputStream.read())
    }

    @Test
    fun testReadZeroBytesArray() {
        val data = "abc".encodeToByteArray()
        val inStream = BufferedInputStream(KIOSourceInputStream(byteArraySource(data)))
        val buf = ByteArray(0)
        assertEquals(0, inStream.read(buf, 0, 0))
    }

    @Test
    fun testConstructorThrowsOnInvalidSize() {
        val data = "abc".encodeToByteArray()
        assertFailsWith<IllegalArgumentException> {
            BufferedInputStream(KIOSourceInputStream(byteArraySource(data)), 0)
        }
        assertFailsWith<IllegalArgumentException> {
            BufferedInputStream(KIOSourceInputStream(byteArraySource(data)), -4)
        }
    }

    @Test
    fun testReadAfterCloseThrows() {
        val data = "test".encodeToByteArray()
        val inStream = BufferedInputStream(KIOSourceInputStream(byteArraySource(data)))
        inStream.close()
        assertFailsWith<IOException> {
            inStream.read()
        }
        assertFailsWith<IOException> {
            inStream.read(ByteArray(3), 0, 3)
        }
        assertFailsWith<IOException> {
            inStream.skip(1)
        }
    }
}
