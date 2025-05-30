package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertContentEquals
import okio.IOException
import kotlin.test.assertNotNull

class StreamEncoderTest {
    // Helper to create a StreamEncoder with UTF-8 and a ByteArrayOutputStream
    private fun createEncoder(baos: ByteArrayOutputStream, charset: Charset = StandardCharsets.UTF_8): StreamEncoder {
        return StreamEncoder.forOutputStreamWriter(baos, charset)
    }

    // Initial test method to verify setup (can be kept or adapted)
    @Test
    fun initialSetupTest() {
        val baos = ByteArrayOutputStream()
        val encoder = createEncoder(baos)
        assertNotNull(encoder.encoding, "Encoder encoding should not be null")
        // Check if the encoding name contains "UTF-8". It might be, e.g., "UTF-8" or "utf-8".
        // Using equalsIgnoreCase for more robust comparison of charset names.
        assertTrue(StandardCharsets.UTF_8.name().equals(encoder.encoding, ignoreCase = true),
            "Encoder should be ${StandardCharsets.UTF_8.name()}. Actual: ${encoder.encoding}")
    }

    @Test
    fun testGetEncodingWhenOpenWithCharset() {
        val baos = ByteArrayOutputStream()
        // Using the helper, which defaults to StandardCharsets.UTF_8
        val encoder = createEncoder(baos, StandardCharsets.UTF_8)
        assertNotNull(encoder.encoding, "Encoding should not be null when open")
        assertEquals(StandardCharsets.UTF_8.name(), encoder.encoding, "Encoding should match the charset name")
    }

    @Test
    fun testGetEncodingWhenOpenWithCharsetEncoder() {
        val baos = ByteArrayOutputStream()
        val utf8Encoder: CharsetEncoder = StandardCharsets.UTF_8.newEncoder()
        // Directly use the factory method that accepts a CharsetEncoder
        val encoder = StreamEncoder.forOutputStreamWriter(baos, utf8Encoder)
        assertNotNull(encoder.encoding, "Encoding should not be null when open with CharsetEncoder")
        // The encoding name should still reflect the underlying charset of the encoder
        assertEquals(StandardCharsets.UTF_8.name(), encoder.encoding,
            "Encoding should match the name of the charset from the CharsetEncoder")
    }

    @Test
    fun testGetEncodingWhenClosed() {
        val baos = ByteArrayOutputStream()
        val encoder = createEncoder(baos, StandardCharsets.UTF_8)
        assertNotNull(encoder.encoding, "Encoding should not be null before close")
        assertEquals(StandardCharsets.UTF_8.name(), encoder.encoding, "Encoding should be UTF-8 before close")

        encoder.close() // Close the encoder

        assertNull(encoder.encoding, "Encoding should be null after the StreamEncoder is closed")
    }

    // --- Tests for write(c: Int) ---

    @Test
    fun testWriteSingleAsciiChar() {
        val baos = ByteArrayOutputStream()
        val encoder = createEncoder(baos) // Uses UTF-8 by default

        encoder.write('A'.code)
        encoder.close() // Should flush before closing

        val expectedBytes = byteArrayOf(0x41.toByte()) // 'A' in UTF-8 (and ASCII)
        assertContentEquals(expectedBytes, baos.toByteArray(), "Output bytes should match ASCII for 'A'")
    }

    @Test
    fun testWriteSingleMultiByteCharUTF8() {
        val baos = ByteArrayOutputStream()
        val encoder = createEncoder(baos) // Uses UTF-8 by default

        encoder.write('€'.code) // Euro sign U+20AC
        encoder.close() // Should flush before closing

        // UTF-8 representation of Euro sign (€) is E2 82 AC
        val expectedBytes = byteArrayOf(0xE2.toByte(), 0x82.toByte(), 0xAC.toByte())
        assertContentEquals(expectedBytes, baos.toByteArray(), "Output bytes should match UTF-8 for '€'")
    }

    @Test
    fun testWriteCharToClosedStream() {
        val baos = ByteArrayOutputStream()
        val encoder = createEncoder(baos)

        encoder.close() // Close the stream first

        assertFailsWith<IOException>("Should throw IOException when writing to a closed stream") {
            encoder.write('A'.code)
        }
        // Also ensure no bytes were written after close attempt
        assertTrue(baos.toByteArray().isEmpty(), "No bytes should be written to the underlying stream after it's closed")
    }

    // --- Tests for write(cbuf: CharArray, off: Int, len: Int) ---

    @Test
    fun testWriteCharArrayFull() {
        val baos = ByteArrayOutputStream()
        val encoder = createEncoder(baos)
        val chars = charArrayOf('H', 'e', 'l', 'l', 'o')

        encoder.write(chars, 0, chars.size)
        encoder.close()

        val expectedBytes = "Hello".encodeToByteArray() // UTF-8 encoding of "Hello"
        assertContentEquals(expectedBytes, baos.toByteArray())
    }

    @Test
    fun testWriteCharArrayPartial() {
        val baos = ByteArrayOutputStream()
        val encoder = createEncoder(baos)
        val chars = charArrayOf('W', 'o', 'r', 'l', 'd')

        encoder.write(chars, 1, 3) // Should write "orl"
        encoder.close()

        val expectedBytes = "orl".encodeToByteArray()
        assertContentEquals(expectedBytes, baos.toByteArray())
    }

    @Test
    fun testWriteCharArrayEmptySegment() {
        val baos = ByteArrayOutputStream()
        val encoder = createEncoder(baos)
        val chars = charArrayOf('T', 'e', 's', 't')

        encoder.write(chars, 1, 0) // Write zero characters
        encoder.close()

        assertTrue(baos.toByteArray().isEmpty(), "ByteArrayOutputStream should be empty for zero length write")
    }

    @Test
    fun testWriteCharArrayMultiByte() {
        val baos = ByteArrayOutputStream()
        val encoder = createEncoder(baos)
        val chars = charArrayOf('A', '€', 'B') // Euro: U+20AC -> E2 82 AC in UTF-8

        encoder.write(chars, 0, chars.size)
        encoder.close()

        val expectedBytes = "A€B".encodeToByteArray()
        // Expected: 0x41 (A), 0xE2 0x82 0xAC (€), 0x42 (B)
        assertContentEquals(expectedBytes, baos.toByteArray())
    }

    @Test
    fun testWriteCharArrayOutOfBounds() {
        val baos = ByteArrayOutputStream()
        val encoder = createEncoder(baos)
        val chars = charArrayOf('F', 'a', 'i', 'l')

        assertFailsWith<IndexOutOfBoundsException> { encoder.write(chars, -1, 2) }
        assertTrue(baos.toByteArray().isEmpty(), "Buffer should be empty after IOOBE")

        assertFailsWith<IndexOutOfBoundsException> { encoder.write(chars, 0, 5) }
        assertTrue(baos.toByteArray().isEmpty(), "Buffer should be empty after IOOBE")

        assertFailsWith<IndexOutOfBoundsException> { encoder.write(chars, 2, 3) }
        assertTrue(baos.toByteArray().isEmpty(), "Buffer should be empty after IOOBE")
        
        assertFailsWith<IndexOutOfBoundsException> { encoder.write(chars, 0, -1) }
        assertTrue(baos.toByteArray().isEmpty(), "Buffer should be empty after IOOBE")
        
        encoder.close()
    }

    @Test
    fun testWriteCharArrayToClosedStream() {
        val baos = ByteArrayOutputStream()
        val encoder = createEncoder(baos)
        val chars = charArrayOf('X')

        encoder.close() // Close the stream first

        assertFailsWith<IOException>("Should throw IOException when writing char array to a closed stream") {
            encoder.write(chars, 0, 1)
        }
        assertTrue(baos.toByteArray().isEmpty(), "No bytes should be written to the underlying stream after it's closed")
    }

    // --- Tests for write(str: String, off: Int, len: Int) ---

    @Test
    fun testWriteStringFull() {
        val baos = ByteArrayOutputStream()
        val encoder = createEncoder(baos)
        val str = "Hello"

        encoder.write(str, 0, str.length)
        encoder.close()

        assertContentEquals(str.encodeToByteArray(), baos.toByteArray())
    }

    @Test
    fun testWriteStringPartial() {
        val baos = ByteArrayOutputStream()
        val encoder = createEncoder(baos)
        val str = "World"

        encoder.write(str, 1, 3) // Should write "orl"
        encoder.close()

        assertContentEquals("orl".encodeToByteArray(), baos.toByteArray())
    }

    @Test
    fun testWriteStringEmptySegment() {
        val baos = ByteArrayOutputStream()
        val encoder = createEncoder(baos)
        val str = "Test"

        encoder.write(str, 1, 0) // Write zero characters
        encoder.close()

        assertTrue(baos.toByteArray().isEmpty(), "ByteArrayOutputStream should be empty for zero length string write")
    }

    @Test
    fun testWriteStringMultiByte() {
        val baos = ByteArrayOutputStream()
        val encoder = createEncoder(baos)
        val str = "A€B" // Euro: U+20AC -> E2 82 AC in UTF-8

        encoder.write(str, 0, str.length)
        encoder.close()
        
        assertContentEquals(str.encodeToByteArray(), baos.toByteArray())
    }

    @Test
    fun testWriteStringOutOfBounds() {
        val baos = ByteArrayOutputStream()
        val encoder = createEncoder(baos)
        val str = "Fail"

        assertFailsWith<IndexOutOfBoundsException> { encoder.write(str, -1, 2) }
        assertTrue(baos.toByteArray().isEmpty(), "Buffer should be empty after IOOBE on string write")

        assertFailsWith<IndexOutOfBoundsException> { encoder.write(str, 0, 5) }
        assertTrue(baos.toByteArray().isEmpty(), "Buffer should be empty after IOOBE on string write")

        assertFailsWith<IndexOutOfBoundsException> { encoder.write(str, 2, 3) }
        assertTrue(baos.toByteArray().isEmpty(), "Buffer should be empty after IOOBE on string write")
        
        assertFailsWith<IndexOutOfBoundsException> { encoder.write(str, 0, -1) }
        assertTrue(baos.toByteArray().isEmpty(), "Buffer should be empty after IOOBE on string write")
        
        encoder.close()
    }

    @Test
    fun testWriteStringToClosedStream() {
        val baos = ByteArrayOutputStream()
        val encoder = createEncoder(baos)
        val str = "X"

        encoder.close() // Close the stream first

        assertFailsWith<IOException>("Should throw IOException when writing string to a closed stream") {
            encoder.write(str, 0, 1)
        }
        assertTrue(baos.toByteArray().isEmpty(), "No bytes should be written to the underlying stream after it's closed")
    }

    // --- Tests for write(cb: CharBuffer) ---

    @Test
    fun testWriteCharBufferFull() {
        val baos = ByteArrayOutputStream()
        val encoder = createEncoder(baos)
        val str = "Hello"
        val cb = CharBuffer.wrap(str.toCharArray())
        val originalPosition = cb.position // Should be 0 after wrap

        encoder.write(cb)
        encoder.close()

        assertContentEquals(str.encodeToByteArray(), baos.toByteArray())
        assertEquals(originalPosition, cb.position, "CharBuffer position should be restored")
    }

    @Test
    fun testWriteCharBufferPartialConsume() {
        val baos = ByteArrayOutputStream()
        val encoder = createEncoder(baos)
        val str = "World"
        val cb = CharBuffer.wrap(str.toCharArray())

        // Set to read "orl"
        cb.position = 1
        cb.limit = 4

        val originalPosition = cb.position // Should be 1

        encoder.write(cb)
        encoder.close()

        assertContentEquals("orl".encodeToByteArray(), baos.toByteArray())
        assertEquals(originalPosition, cb.position, "CharBuffer position should be restored after partial write")
    }

    @Test
    fun testWriteCharBufferEmpty() {
        val baos = ByteArrayOutputStream()
        val encoder = createEncoder(baos)
        // Option 1: Wrap an empty char array
        val cbEmptyWrap = CharBuffer.wrap(charArrayOf())
        var originalPosition = cbEmptyWrap.position
        
        encoder.write(cbEmptyWrap)
        // No close yet, to test another empty buffer type
        assertTrue(baos.toByteArray().isEmpty(), "BAOS should be empty after writing empty CharBuffer (wrap)")
        assertEquals(originalPosition, cbEmptyWrap.position, "Position of empty CharBuffer (wrap) should be restored")

        // Option 2: Allocate an empty buffer (capacity 0)
        // Note: jdkport.CharBuffer.allocate might behave differently. Assuming it creates a buffer with pos=0, lim=0, cap=0.
        // If allocate(0) is not typical or causes issues, stick to wrap(charArrayOf()).
        // For now, let's assume wrap(charArrayOf()) is the primary way to get an empty buffer for testing.
        // Let's test a buffer that has capacity but is empty by setting position == limit
        val cbEmptyByPosLimit = CharBuffer.wrap(charArrayOf('a', 'b'))

        // position == limit, so nothing to read
        cbEmptyByPosLimit.position = 1
        cbEmptyByPosLimit.limit = 1

        originalPosition = cbEmptyByPosLimit.position

        encoder.write(cbEmptyByPosLimit)
        encoder.close() // Now close

        assertTrue(baos.toByteArray().isEmpty(), "BAOS should still be empty after writing an effectively empty CharBuffer (pos=lim)")
        assertEquals(originalPosition, cbEmptyByPosLimit.position, "Position of effectively empty CharBuffer (pos=lim) should be restored")
    }
    
    @Test
    fun testWriteCharBufferMultiByte() {
        val baos = ByteArrayOutputStream()
        val encoder = createEncoder(baos)
        val str = "A€B"
        val cb = CharBuffer.wrap(str.toCharArray())
        val originalPosition = cb.position

        encoder.write(cb)
        encoder.close()

        assertContentEquals(str.encodeToByteArray(), baos.toByteArray())
        assertEquals(originalPosition, cb.position, "CharBuffer position should be restored after multi-byte write")
    }

    @Test
    fun testWriteCharBufferToClosedStream() {
        val baos = ByteArrayOutputStream()
        val encoder = createEncoder(baos)
        val cb = CharBuffer.wrap("X".toCharArray())

        encoder.close() // Close the stream first

        assertFailsWith<IOException>("Should throw IOException when writing CharBuffer to a closed stream") {
            encoder.write(cb)
        }
        assertTrue(baos.toByteArray().isEmpty(), "No bytes should be written to the underlying stream after it's closed")
        // Position check is not strictly needed here as the operation should fail before position restoration logic
        // but if it somehow proceeded, it should ideally still restore. However, primary check is the exception.
    }
}
