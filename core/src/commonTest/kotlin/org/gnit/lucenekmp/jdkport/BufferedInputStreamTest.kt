package org.gnit.lucenekmp.jdkport

import okio.Buffer
import okio.BufferedSource
import okio.IOException
import kotlin.test.*

/**
 * tests functions of [BufferedInputStream] to see if it behaves like [java.io.BufferedInputStream](https://docs.oracle.com/en/java/javase/24/docs/api/java.base/java/io/BufferedInputStream.html)
 */
class BufferedInputStreamTest {

    private val HELLO_WORLD_BYTES = "Hello, World!".encodeToByteArray()
    private fun byteArraySource(data: ByteArray): BufferedSource = Buffer().apply { write(data) }

    @Test
    fun testReadSingleBytes() {
        val inputStream = BufferedInputStream(OkioSourceInputStream(byteArraySource(HELLO_WORLD_BYTES)))
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
        val inputStream = BufferedInputStream(OkioSourceInputStream(byteArraySource(data)))

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
        val inputStream = BufferedInputStream(OkioSourceInputStream(byteArraySource(data)), 2)

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
        val inputStream = BufferedInputStream(OkioSourceInputStream(byteArraySource(data)))
        inputStream.close()
        assertFailsWith<IOException> { inputStream.available() }
    }

    @Test
    fun testMarkAndResetRestoreReadPosition() {
        val data = "abcdefgh".encodeToByteArray()
        val inputStream = BufferedInputStream(OkioSourceInputStream(byteArraySource(data)), 4)

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
        val inputStream = BufferedInputStream(OkioSourceInputStream(byteArraySource(data)))

        // Don't call mark()
        assertFailsWith<IOException> {
            inputStream.reset()
        }
    }

    @Test
    fun testResetAfterMarkInvalidatedThrows() {
        val data = "ABCDEFG".encodeToByteArray()
        val inputStream = BufferedInputStream(OkioSourceInputStream(byteArraySource(data)), 2)

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
        val inputStream = BufferedInputStream(OkioSourceInputStream(byteArraySource(data)), 5)

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
        val inputStream = BufferedInputStream(OkioSourceInputStream(byteArraySource(data)), 3)
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
        val inputStream = BufferedInputStream(OkioSourceInputStream(byteArraySource(data)), 2)
        assertEquals(3, inputStream.skip(10)) // more than length, should only skip available
        assertEquals(-1, inputStream.read())
    }

    @Test
    fun testReadZeroBytesArray() {
        val data = "abc".encodeToByteArray()
        val inStream = BufferedInputStream(OkioSourceInputStream(byteArraySource(data)))
        val buf = ByteArray(0)
        assertEquals(0, inStream.read(buf, 0, 0))
    }

    @Test
    fun testConstructorThrowsOnInvalidSize() {
        val data = "abc".encodeToByteArray()
        assertFailsWith<IllegalArgumentException> {
            BufferedInputStream(OkioSourceInputStream(byteArraySource(data)), 0)
        }
        assertFailsWith<IllegalArgumentException> {
            BufferedInputStream(OkioSourceInputStream(byteArraySource(data)), -4)
        }
    }

    @Test
    fun testReadAfterCloseThrows() {
        val data = "test".encodeToByteArray()
        val inStream = BufferedInputStream(OkioSourceInputStream(byteArraySource(data)))
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

    // ===== EDGE CASE TESTS =====

    @Test
    fun testMarkWithZeroReadlimit() {
        val data = "abcdefgh".encodeToByteArray()
        val inputStream = BufferedInputStream(OkioSourceInputStream(byteArraySource(data)), 4)
        
        // Mark with readlimit = 0
        inputStream.mark(0)
        val firstByte = inputStream.read()
        assertEquals('a'.code, firstByte)
        
        // Reset should work initially
        inputStream.reset()
        assertEquals('a'.code, inputStream.read())
    }

    @Test
    fun testMarkWithNegativeReadlimit() {
        val data = "abcdefgh".encodeToByteArray()
        val inputStream = BufferedInputStream(OkioSourceInputStream(byteArraySource(data)), 4)
        
        // Mark with negative readlimit
        inputStream.mark(-10)
        val firstByte = inputStream.read()
        assertEquals('a'.code, firstByte)
        
        // Reset should still work initially
        inputStream.reset()
        assertEquals('a'.code, inputStream.read())
    }

    @Test
    fun testBufferGrowthDuringMarkOperation() {
        val data = "abcdefghijklmnopqrstuvwxyz".encodeToByteArray()
        val inputStream = BufferedInputStream(OkioSourceInputStream(byteArraySource(data)), 4) // Small buffer
        
        // Mark with large readlimit to force buffer growth
        inputStream.mark(20)
        
        // Read enough to trigger buffer growth
        val buffer = ByteArray(15)
        val bytesRead = inputStream.read(buffer)
        assertEquals(15, bytesRead)
        
        // Reset should still work after buffer growth
        inputStream.reset()
        assertEquals('a'.code, inputStream.read())
    }

    @Test
    fun testLargeReadBypassesBufferWhenNoMark() {
        val data = "abcdefghijklmnopqrstuvwxyz0123456789".encodeToByteArray()
        val inputStream = BufferedInputStream(OkioSourceInputStream(byteArraySource(data)), 8) // Small buffer
        
        // Large read should bypass internal buffer when no mark is set
        val largeBuffer = ByteArray(20)
        val bytesRead = inputStream.read(largeBuffer, 0, 20)
        assertEquals(20, bytesRead)
        assertContentEquals("abcdefghijklmnopqrst".encodeToByteArray(), largeBuffer)
    }

    @Test
    fun testLargeReadWithMarkSet() {
        val data = "abcdefghijklmnopqrstuvwxyz0123456789".encodeToByteArray()
        val inputStream = BufferedInputStream(OkioSourceInputStream(byteArraySource(data)), 8) // Small buffer
        
        // Set mark to prevent bypass
        inputStream.mark(30)
        
        // Large read should NOT bypass buffer when mark is set
        val largeBuffer = ByteArray(20)
        val bytesRead = inputStream.read(largeBuffer, 0, 20)
        assertEquals(20, bytesRead)
        assertContentEquals("abcdefghijklmnopqrst".encodeToByteArray(), largeBuffer)
        
        // Reset should work
        inputStream.reset()
        assertEquals('a'.code, inputStream.read())
    }

    @Test
    fun testAvailableOverflowPrevention() {
        // Create a mock input stream that reports very large available bytes
        val mockInputStream = object : InputStream() {
            private var pos = 0
            private val data = "test".encodeToByteArray()
            
            override fun read(): Int {
                return if (pos < data.size) data[pos++].toInt() and 0xff else -1
            }
            
            override fun available(): Int {
                return Int.MAX_VALUE - 5 // Large value that could cause overflow
            }
        }
        
        val bufferedStream = BufferedInputStream(mockInputStream, 10)
        
        // Should handle potential overflow gracefully
        val available = bufferedStream.available()
        assertEquals(Int.MAX_VALUE - 5, available) // Should be clamped to MAX_VALUE
    }

    @Test
    fun testMarkInvalidationAtExactBoundary() {
        val data = "abcdefghijklmnop".encodeToByteArray()
        val inputStream = BufferedInputStream(OkioSourceInputStream(byteArraySource(data)), 4)
        
        inputStream.mark(5) // Read limit of 5
        
        // Read exactly 5 bytes
        repeat(5) { inputStream.read() }
        
        // Read one more byte - this should invalidate the mark
        inputStream.read()
        
        // Reset should now fail
        assertFailsWith<IOException> {
            inputStream.reset()
        }
    }

    @Test
    fun testTransferToMethod() {
        val data = "Hello, World! This is a test for transferTo.".encodeToByteArray()
        val inputStream = BufferedInputStream(OkioSourceInputStream(byteArraySource(data)))
        
        val outputStream = ByteArrayOutputStream()
        val bytesTransferred = inputStream.transferTo(outputStream)
        
        assertEquals(data.size.toLong(), bytesTransferred)
        assertContentEquals(data, outputStream.toByteArray())
    }

    @Test
    fun testTransferToWithMarkedStream() {
        val data = "Hello, World!".encodeToByteArray()
        val inputStream = BufferedInputStream(OkioSourceInputStream(byteArraySource(data)))
        
        // Set mark to change transferTo behavior
        inputStream.mark(100)
        inputStream.read() // Read one byte
        
        val outputStream = ByteArrayOutputStream()
        val bytesTransferred = inputStream.transferTo(outputStream)
        
        // Should transfer remaining bytes
        assertEquals((data.size - 1).toLong(), bytesTransferred)
        assertContentEquals("ello, World!".encodeToByteArray(), outputStream.toByteArray())
    }

    @Test
    fun testMultipleConsecutiveMarks() {
        val data = "abcdefghijklmnop".encodeToByteArray()
        val inputStream = BufferedInputStream(OkioSourceInputStream(byteArraySource(data)), 8)
        
        // Set first mark
        inputStream.mark(10)
        inputStream.read() // Read 'a'
        
        // Set second mark (should overwrite the first)
        inputStream.mark(10)
        inputStream.read() // Read 'b'
        inputStream.read() // Read 'c'
        
        // Reset should go back to the second mark position (after 'a')
        inputStream.reset()
        assertEquals('b'.code, inputStream.read())
    }

    @Test
    fun testResetImmediatelyAfterMark() {
        val data = "abcdefgh".encodeToByteArray()
        val inputStream = BufferedInputStream(OkioSourceInputStream(byteArraySource(data)))
        
        inputStream.mark(10)
        
        // Reset immediately without reading anything
        inputStream.reset()
        
        // Should be at the same position
        assertEquals('a'.code, inputStream.read())
    }

    @Test
    fun testSkipWithZero() {
        val data = "abcdefgh".encodeToByteArray()
        val inputStream = BufferedInputStream(OkioSourceInputStream(byteArraySource(data)))
        
        val skipped = inputStream.skip(0)
        assertEquals(0L, skipped)
        
        // Position should be unchanged
        assertEquals('a'.code, inputStream.read())
    }

    @Test
    fun testSkipWithNegativeValue() {
        val data = "abcdefgh".encodeToByteArray()
        val inputStream = BufferedInputStream(OkioSourceInputStream(byteArraySource(data)))
        
        val skipped = inputStream.skip(-5)
        assertEquals(0L, skipped)
        
        // Position should be unchanged
        assertEquals('a'.code, inputStream.read())
    }

    @Test
    fun testVerySmallBufferSize() {
        val data = "abcdefghijklmnop".encodeToByteArray()
        val inputStream = BufferedInputStream(OkioSourceInputStream(byteArraySource(data)), 1) // Minimum buffer size
        
        // Should still work correctly
        val firstByte = inputStream.read()
        assertEquals('a'.code, firstByte)
        
        // Test buffered reading
        val buffer = ByteArray(5)
        val bytesRead = inputStream.read(buffer)
        assertEquals(5, bytesRead)
        assertContentEquals("bcdef".encodeToByteArray(), buffer)
    }

    @Test
    fun testMultipleCloses() {
        val data = "test".encodeToByteArray()
        val inputStream = BufferedInputStream(OkioSourceInputStream(byteArraySource(data)))
        
        // First close should succeed
        inputStream.close()
        
        // Multiple closes should not throw
        inputStream.close()
        inputStream.close()
    }

    @Test
    fun testOperationsAfterClose() {
        val data = "test".encodeToByteArray()
        val inputStream = BufferedInputStream(OkioSourceInputStream(byteArraySource(data)))
        inputStream.close()
        
        // All operations should throw IOException
        assertFailsWith<IOException> { inputStream.read() }
        assertFailsWith<IOException> { inputStream.read(ByteArray(5)) }
        assertFailsWith<IOException> { inputStream.read(ByteArray(5), 0, 3) }
        assertFailsWith<IOException> { inputStream.skip(1) }
        assertFailsWith<IOException> { inputStream.available() }
        assertFailsWith<IOException> { inputStream.reset() }
        
        // Mark should not throw but subsequent reset should
        inputStream.mark(10) // This should not throw
        assertFailsWith<IOException> { inputStream.reset() }
        
        // MarkSupported should still return true
        assertTrue(inputStream.markSupported())
    }

    @Test
    fun testReadZeroLengthFromEmptyBuffer() {
        val data = "test".encodeToByteArray()
        val inputStream = BufferedInputStream(OkioSourceInputStream(byteArraySource(data)))
        
        // Read all data
        inputStream.skip(data.size.toLong())
        
        // Read zero length should return 0, not -1
        val buffer = ByteArray(5)
        val bytesRead = inputStream.read(buffer, 0, 0)
        assertEquals(0, bytesRead)
    }

    @Test
    fun testMarkSupported() {
        val data = "test".encodeToByteArray()
        val inputStream = BufferedInputStream(OkioSourceInputStream(byteArraySource(data)))
        
        assertTrue(inputStream.markSupported())
        
        // Should still return true after operations
        inputStream.read()
        assertTrue(inputStream.markSupported())
        
        inputStream.mark(10)
        assertTrue(inputStream.markSupported())
        
        inputStream.reset()
        assertTrue(inputStream.markSupported())
    }

    @Test
    fun testSkipPartialFromBuffer() {
        val data = "abcdefghijklmnop".encodeToByteArray()
        val inputStream = BufferedInputStream(OkioSourceInputStream(byteArraySource(data)), 8)
        
        // Read one byte to populate buffer
        inputStream.read()
        
        // Skip 3 bytes from buffer
        val skipped = inputStream.skip(3)
        assertEquals(3L, skipped)
        
        // Next read should be 'e' (skipped 'b', 'c', 'd')
        assertEquals('e'.code, inputStream.read())
    }

    @Test
    fun testSkipAcrossBufferBoundary() {
        val data = "abcdefghijklmnop".encodeToByteArray()
        val inputStream = BufferedInputStream(OkioSourceInputStream(byteArraySource(data)), 4)
        
        // Skip more than buffer size
        val skipped = inputStream.skip(6)
        assertEquals(6L, skipped)
        
        // Next read should be 'g'
        assertEquals('g'.code, inputStream.read())
    }

    @Test
    fun testAvailableAfterPartialRead() {
        val data = "abcdefghijklmnop".encodeToByteArray()
        val inputStream = BufferedInputStream(OkioSourceInputStream(byteArraySource(data)), 8)
        
        // Read some bytes
        inputStream.read()
        inputStream.read()
        
        val available = inputStream.available()
        assertEquals(data.size - 2, available)
    }

    @Test
    fun testBufferGrowthWithLargeMarklimit() {
        val data = "a".repeat(1000).encodeToByteArray()
        val inputStream = BufferedInputStream(OkioSourceInputStream(byteArraySource(data)), 10) // Small initial buffer
        
        // Mark with very large readlimit
        inputStream.mark(500)
        
        // Read enough to force buffer growth multiple times
        val buffer = ByteArray(200)
        val bytesRead = inputStream.read(buffer)
        assertEquals(200, bytesRead)
        
        // Reset should still work
        inputStream.reset()
        assertEquals('a'.code, inputStream.read())
    }

    @Test
    fun testMarkInvalidationWhenBufferGrowthExceedsMarklimit() {
        val data = "a".repeat(100).encodeToByteArray()
        val inputStream = BufferedInputStream(OkioSourceInputStream(byteArraySource(data)), 5) // Small buffer
        
        // Mark with small readlimit
        inputStream.mark(10)
        
        // Read more than marklimit to force buffer growth and mark invalidation
        val buffer = ByteArray(50)
        inputStream.read(buffer)
        
        // Reset should fail because mark was invalidated
        assertFailsWith<IOException> {
            inputStream.reset()
        }
    }

    @Test
    fun testReadSingleByteAtEndOfStream() {
        val data = "a".encodeToByteArray()
        val inputStream = BufferedInputStream(OkioSourceInputStream(byteArraySource(data)))
        
        // Read the single byte
        assertEquals('a'.code, inputStream.read())
        
        // Next read should return -1
        assertEquals(-1, inputStream.read())
        assertEquals(-1, inputStream.read()) // Multiple calls should consistently return -1
    }

    @Test
    fun testReadArrayAtEndOfStream() {
        val data = "abc".encodeToByteArray()
        val inputStream = BufferedInputStream(OkioSourceInputStream(byteArraySource(data)))
        
        // Read all data
        val buffer = ByteArray(5)
        val bytesRead = inputStream.read(buffer)
        assertEquals(3, bytesRead)
        
        // Next read should return -1
        assertEquals(-1, inputStream.read(buffer))
        assertEquals(-1, inputStream.read(buffer)) // Multiple calls should consistently return -1
    }
}
