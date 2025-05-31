package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.assertNull
import org.gnit.lucenekmp.jdkport.Charset
import org.gnit.lucenekmp.jdkport.OutputStreamWriter
import org.gnit.lucenekmp.jdkport.ByteArrayOutputStream
import org.gnit.lucenekmp.jdkport.CharBuffer // For CharBuffer tests
import okio.IOException // For testing operations after close

// Use kotlin.NullPointerException for explicit null checks,
// IndexOutOfBoundsException for array/string bounds.

class OutputStreamWriterTest {

    @Test
    fun testBasicSetup() {
        assertEquals(1, 1)
    }

    // Constructor Tests
    @Test
    fun testConstructorWithCharset() {
        val baos = ByteArrayOutputStream()
        val charset = Charset.UTF_8
        OutputStreamWriter(baos, charset).use { writer ->
            assertEquals(charset.name(), writer.encoding)
        }
    }

    @Test
    fun testConstructorWithCharsetEncoder() {
        val baos = ByteArrayOutputStream()
        val charset = Charset.UTF_8
        val encoder = charset.newEncoder()
        OutputStreamWriter(baos, encoder).use { writer ->
            assertEquals(charset.name(), writer.encoding)
        }
    }

    @Test
    fun testConstructorWithNullCharset() {
        val baos = ByteArrayOutputStream()
        assertFailsWith<kotlin.NullPointerException> {
            @Suppress("UNCHECKED_CAST")
            fun <T> getNull(): T = null as T
            OutputStreamWriter(baos, getNull<Charset>()).use {}
        }
    }

    @Test
    fun testConstructorWithNullCharsetEncoder() {
        val baos = ByteArrayOutputStream()
        assertFailsWith<kotlin.NullPointerException> {
            @Suppress("UNCHECKED_CAST")
            fun <T> getNull(): T = null as T
            OutputStreamWriter(baos, getNull<CharsetEncoder>()).use {}
        }
    }

    // Tests for write(c: Int)
    @Test
    fun testWriteSingleCharUTF8() {
        val baos = ByteArrayOutputStream()
        OutputStreamWriter(baos, Charset.UTF_8).use { writer ->
            writer.write('A'.code)
            writer.flush()
            val expected = byteArrayOf(0x41.toByte())
            assertTrue(expected.contentEquals(baos.toByteArray()), "Byte array for 'A' UTF-8 mismatch")
        }
    }

    @Test
    fun testWriteSingleCharMultiByteUTF8() {
        val baos = ByteArrayOutputStream()
        OutputStreamWriter(baos, Charset.UTF_8).use { writer ->
            writer.write('é'.code) // U+00E9 -> UTF-8: C3 A9
            writer.flush()
            val expected = byteArrayOf(0xC3.toByte(), 0xA9.toByte())
            assertTrue(expected.contentEquals(baos.toByteArray()), "Byte array for 'é' UTF-8 mismatch")
        }
    }

    @Test
    fun testWriteSingleCharISO8859_1() {
        val baos = ByteArrayOutputStream()
        OutputStreamWriter(baos, Charset.ISO_8859_1).use { writer ->
            writer.write('é'.code) // U+00E9 -> ISO-8859-1: E9
            writer.flush()
            val expected = byteArrayOf(0xE9.toByte())
            assertTrue(expected.contentEquals(baos.toByteArray()), "Byte array for 'é' ISO-8859-1 mismatch")
        }
    }

    // Tests for write(cbuf: CharArray, off: Int, len: Int)
    @Test
    fun testWriteCharArrayFullUTF8() {
        val baos = ByteArrayOutputStream()
        val chars = charArrayOf('H', 'e', 'l', 'l', 'o')
        OutputStreamWriter(baos, Charset.UTF_8).use { writer ->
            writer.write(chars, 0, chars.size)
            writer.flush()
            val expected = "Hello".encodeToByteArray()
            assertTrue(expected.contentEquals(baos.toByteArray()), "Byte array for char[] \"Hello\" UTF-8 mismatch")
        }
    }

    @Test
    fun testWriteCharArrayPartialUTF8() {
        val baos = ByteArrayOutputStream()
        val chars = charArrayOf('H', 'e', 'l', 'l', 'o')
        OutputStreamWriter(baos, Charset.UTF_8).use { writer ->
            writer.write(chars, 1, 3) // "ell"
            writer.flush()
            val expected = "ell".encodeToByteArray()
            assertTrue(expected.contentEquals(baos.toByteArray()), "Byte array for char[] \"ell\" UTF-8 mismatch")
        }
    }

    @Test
    fun testWriteCharArrayEmpty() {
        val baos = ByteArrayOutputStream()
        val chars = charArrayOf('H', 'e', 'l', 'l', 'o')
        OutputStreamWriter(baos, Charset.UTF_8).use { writer ->
            writer.write(chars, 1, 0)
            writer.flush()
            assertTrue(baos.toByteArray().isEmpty(), "Byte array should be empty for zero length char[] write")
        }
    }

    @Test
    fun testWriteCharArrayMultiByteISO8859_1() {
        val baos = ByteArrayOutputStream()
        val chars = charArrayOf('H', 'é', 'l', 'l', 'o') // "Héllo"
        OutputStreamWriter(baos, Charset.ISO_8859_1).use { writer ->
            writer.write(chars, 0, chars.size)
            writer.flush()
            val expected = byteArrayOf(0x48.toByte(), 0xE9.toByte(), 0x6C.toByte(), 0x6C.toByte(), 0x6F.toByte())
            assertTrue(expected.contentEquals(baos.toByteArray()), "Byte array for char[] \"Héllo\" ISO-8859-1 mismatch")
        }
    }

    private val testChars = charArrayOf('a', 'b', 'c')

    @Test
    fun testWriteCharArrayOutOfBoundsNegativeOffset() {
        val baos = ByteArrayOutputStream()
        OutputStreamWriter(baos, Charset.UTF_8).use { writer ->
            assertFailsWith<IndexOutOfBoundsException> {
                writer.write(testChars, -1, 2)
            }
        }
    }

    @Test
    fun testWriteCharArrayOutOfBoundsNegativeLength() {
        val baos = ByteArrayOutputStream()
        OutputStreamWriter(baos, Charset.UTF_8).use { writer ->
            assertFailsWith<IndexOutOfBoundsException> {
                writer.write(testChars, 0, -1)
            }
        }
    }

    @Test
    fun testWriteCharArrayOutOfBoundsTooLargeOffset() {
        val baos = ByteArrayOutputStream()
        OutputStreamWriter(baos, Charset.UTF_8).use { writer ->
            assertFailsWith<IndexOutOfBoundsException> {
                writer.write(testChars, testChars.size + 1, 1)
            }
        }
    }

    @Test
    fun testWriteCharArrayOutOfBoundsTooLargeLength() {
        val baos = ByteArrayOutputStream()
        OutputStreamWriter(baos, Charset.UTF_8).use { writer ->
            assertFailsWith<IndexOutOfBoundsException> {
                writer.write(testChars, 0, testChars.size + 1)
            }
        }
    }

    @Test
    fun testWriteCharArrayOutOfBoundsOffsetPlusLengthTooLarge() {
        val baos = ByteArrayOutputStream()
        OutputStreamWriter(baos, Charset.UTF_8).use { writer ->
            assertFailsWith<IndexOutOfBoundsException> {
                writer.write(testChars, 1, testChars.size)
            }
        }
    }

    // Tests for write(str: String, off: Int, len: Int)
    @Test
    fun testWriteStringFullUTF8() {
        val baos = ByteArrayOutputStream()
        val str = "Hello World"
        OutputStreamWriter(baos, Charset.UTF_8).use { writer ->
            writer.write(str, 0, str.length)
            writer.flush()
            val expected = str.encodeToByteArray()
            assertTrue(expected.contentEquals(baos.toByteArray()), "Byte array for String \"Hello World\" UTF-8 mismatch")
        }
    }

    @Test
    fun testWriteStringPartialUTF8() {
        val baos = ByteArrayOutputStream()
        val str = "Hello World"
        OutputStreamWriter(baos, Charset.UTF_8).use { writer ->
            writer.write(str, 6, 5) // "World"
            writer.flush()
            val expected = "World".encodeToByteArray()
            assertTrue(expected.contentEquals(baos.toByteArray()), "Byte array for String \"World\" UTF-8 mismatch")
        }
    }

    @Test
    fun testWriteStringEmpty() {
        val baos = ByteArrayOutputStream()
        val str = "Hello World"
        OutputStreamWriter(baos, Charset.UTF_8).use { writer ->
            writer.write(str, 6, 0)
            writer.flush()
            assertTrue(baos.toByteArray().isEmpty(), "Byte array should be empty for zero length String write")
        }
    }

    @Test
    fun testWriteStringMultiByteISO8859_1() {
        val baos = ByteArrayOutputStream()
        val str = "Grüße" // G r ü ß e
        OutputStreamWriter(baos, Charset.ISO_8859_1).use { writer ->
            writer.write(str, 0, str.length)
            writer.flush()
            val expected = byteArrayOf(0x47.toByte(), 0x72.toByte(), 0xFC.toByte(), 0xDF.toByte(), 0x65.toByte())
            assertTrue(expected.contentEquals(baos.toByteArray()), "Byte array for String \"Grüße\" ISO-8859-1 mismatch")
        }
    }

    private val testString = "abc"

    @Test
    fun testWriteStringOutOfBoundsNegativeOffset() {
        val baos = ByteArrayOutputStream()
        OutputStreamWriter(baos, Charset.UTF_8).use { writer ->
            assertFailsWith<IndexOutOfBoundsException> {
                writer.write(testString, -1, 2)
            }
        }
    }

    @Test
    fun testWriteStringOutOfBoundsNegativeLength() {
        val baos = ByteArrayOutputStream()
        OutputStreamWriter(baos, Charset.UTF_8).use { writer ->
            assertFailsWith<IndexOutOfBoundsException> {
                writer.write(testString, 0, -1)
            }
        }
    }

    @Test
    fun testWriteStringOutOfBoundsTooLargeOffset() {
        val baos = ByteArrayOutputStream()
        OutputStreamWriter(baos, Charset.UTF_8).use { writer ->
            assertFailsWith<IndexOutOfBoundsException> {
                writer.write(testString, testString.length + 1, 1)
            }
        }
    }

    @Test
    fun testWriteStringOutOfBoundsTooLargeLength() {
        val baos = ByteArrayOutputStream()
        OutputStreamWriter(baos, Charset.UTF_8).use { writer ->
            assertFailsWith<IndexOutOfBoundsException> {
                writer.write(testString, 0, testString.length + 1)
            }
        }
    }

    @Test
    fun testWriteStringOutOfBoundsOffsetPlusLengthTooLarge() {
        val baos = ByteArrayOutputStream()
        OutputStreamWriter(baos, Charset.UTF_8).use { writer ->
            assertFailsWith<IndexOutOfBoundsException> {
                writer.write(testString, 1, testString.length)
            }
        }
    }

    // Tests for append(csq: CharSequence?) and append(csq: CharSequence?, start: Int, end: Int)
    @Test
    fun testAppendCharSequenceString() {
        val baos = ByteArrayOutputStream()
        val str: CharSequence = "Append This"
        OutputStreamWriter(baos, Charset.UTF_8).use { writer ->
            writer.append(str)
            writer.flush()
            val expected = "Append This".encodeToByteArray()
            assertTrue(expected.contentEquals(baos.toByteArray()), "Output for append(String) mismatch")
        }
    }

    @Test
    fun testAppendCharSequenceStringBuilder() {
        val baos = ByteArrayOutputStream()
        val sb: CharSequence = StringBuilder("Append SB")
        OutputStreamWriter(baos, Charset.UTF_8).use { writer ->
            writer.append(sb)
            writer.flush()
            val expected = "Append SB".encodeToByteArray()
            assertTrue(expected.contentEquals(baos.toByteArray()), "Output for append(StringBuilder) mismatch")
        }
    }

    @Test
    fun testAppendCharSequenceCharBuffer() {
        val baos = ByteArrayOutputStream()
        val cb: CharSequence = CharBuffer.wrap("Append CB")
        OutputStreamWriter(baos, Charset.UTF_8).use { writer ->
            writer.append(cb)
            writer.flush()
            val expected = "Append CB".encodeToByteArray()
            assertTrue(expected.contentEquals(baos.toByteArray()), "Output for append(CharBuffer) mismatch")
        }
    }

    @Test
    fun testAppendNullCharSequence() {
        val baos = ByteArrayOutputStream()
        OutputStreamWriter(baos, Charset.UTF_8).use { writer ->
            writer.append(null)
            writer.flush()
            val expected = "null".encodeToByteArray()
            assertTrue(expected.contentEquals(baos.toByteArray()), "Output for append(null) should be \"null\"")
        }
    }

    @Test
    fun testAppendCharSequencePortion() {
        val baos = ByteArrayOutputStream()
        val str: CharSequence = "HelloTesting"
        OutputStreamWriter(baos, Charset.UTF_8).use { writer ->
            writer.append(str, 5, 12) // Should append "Testing"
            writer.flush()
            val expected = "Testing".encodeToByteArray()
            assertTrue(expected.contentEquals(baos.toByteArray()), "Output for append(csq, start, end) mismatch")
        }
    }

    @Test
    fun testAppendNullCharSequencePortion() {
        val baos = ByteArrayOutputStream()
        OutputStreamWriter(baos, Charset.UTF_8).use { writer ->
            writer.append(null, 1, 3) // "null".subSequence(1,3) -> "ul"
            writer.flush()
            val expected = "ul".encodeToByteArray()
            assertTrue(expected.contentEquals(baos.toByteArray()), "Output for append(null, 1, 3) mismatch")
        }
    }

    private val appendTestStr: CharSequence = "TestSequence"

    @Test
    fun testAppendCharSequencePortionNegativeStart() {
        val baos = ByteArrayOutputStream()
        OutputStreamWriter(baos, Charset.UTF_8).use { writer ->
            assertFailsWith<IndexOutOfBoundsException> {
                writer.append(appendTestStr, -1, 5)
            }
        }
    }

    @Test
    fun testAppendCharSequencePortionNegativeEnd() {
        val baos = ByteArrayOutputStream()
        OutputStreamWriter(baos, Charset.UTF_8).use { writer ->
            assertFailsWith<IndexOutOfBoundsException> {
                writer.append(appendTestStr, 0, -1)
            }
        }
    }

    @Test
    fun testAppendCharSequencePortionStartGreaterThanEnd() {
        val baos = ByteArrayOutputStream()
        OutputStreamWriter(baos, Charset.UTF_8).use { writer ->
            assertFailsWith<IndexOutOfBoundsException> {
                writer.append(appendTestStr, 5, 2)
            }
        }
    }

    @Test
    fun testAppendCharSequencePortionStartOutOfBounds() {
        val baos = ByteArrayOutputStream()
        OutputStreamWriter(baos, Charset.UTF_8).use { writer ->
            assertFailsWith<IndexOutOfBoundsException> {
                writer.append(appendTestStr, appendTestStr.length + 1, appendTestStr.length + 2)
            }
        }
    }

    @Test
    fun testAppendCharSequencePortionEndOutOfBounds() {
        val baos = ByteArrayOutputStream()
        OutputStreamWriter(baos, Charset.UTF_8).use { writer ->
            assertFailsWith<IndexOutOfBoundsException> {
                writer.append(appendTestStr, 0, appendTestStr.length + 1)
            }
        }
    }

    // Tests for flush()
    @Test
    fun testFlush() {
        val baos = ByteArrayOutputStream()
        OutputStreamWriter(baos, Charset.UTF_8).use { writer ->
            writer.write("Test")
            writer.flush()
            val expectedAfterFirstFlush = "Test".encodeToByteArray()
            assertTrue(expectedAfterFirstFlush.contentEquals(baos.toByteArray()), "Output after first flush mismatch.")

            writer.write(" Me")
            writer.flush()
            val expectedAfterSecondFlush = "Test Me".encodeToByteArray()
            assertTrue(expectedAfterSecondFlush.contentEquals(baos.toByteArray()), "Output after second flush mismatch.")
        }
    }

    // Tests for close()
    @Test
    fun testCloseFlushesData() {
        val baos = ByteArrayOutputStream()
        val writer = OutputStreamWriter(baos, Charset.UTF_8)
        writer.write("FinalData")
        writer.close()
        val expected = "FinalData".encodeToByteArray()
        assertTrue(expected.contentEquals(baos.toByteArray()), "Data not flushed on close.")
    }

    @Test
    fun testOperationsAfterCloseThrowIOException() {
        val baos = ByteArrayOutputStream()
        val writer = OutputStreamWriter(baos, Charset.UTF_8)
        writer.close()

        assertFailsWith<IOException>("Write single char after close should throw IOException") {
            writer.write('a'.code)
        }
        assertFailsWith<IOException>("Write char array after close should throw IOException") {
            writer.write(charArrayOf('b'))
        }
        assertFailsWith<IOException>("Write string after close should throw IOException") {
            writer.write("c")
        }
        assertFailsWith<IOException>("Flush after close should throw IOException") {
            writer.flush()
        }
        assertFailsWith<IOException>("Append char after close should throw IOException") {
            writer.append('d')
        }
        assertFailsWith<IOException>("Append CharSequence after close should throw IOException") {
            writer.append("efg")
        }
        assertFailsWith<IOException>("Append CharSequence portion after close should throw IOException") {
            writer.append("hij", 0, 1)
        }
    }

    @Test
    fun testMultipleCloseCalls() {
        val baos = ByteArrayOutputStream()
        val writer = OutputStreamWriter(baos, Charset.UTF_8)

        writer.write("Data before first close")
        writer.close()

        val expected = "Data before first close".encodeToByteArray()
        assertTrue(expected.contentEquals(baos.toByteArray()), "Data not flushed on first close.")

        writer.close() // Second close should not throw an exception

        assertFailsWith<IOException>("Write after multiple closes should still throw IOException") {
            writer.write('x'.code)
        }
    }

    // Tests for encoding property
    @Test
    fun testEncodingPropertyUTF8() {
        val baos = ByteArrayOutputStream()
        OutputStreamWriter(baos, Charset.UTF_8).use { writer ->
            assertEquals("UTF-8", writer.encoding)
        }
    }

    @Test
    fun testEncodingPropertyISO8859_1() {
        val baos = ByteArrayOutputStream()
        // Using the direct companion object val for ISO_8859_1
        OutputStreamWriter(baos, Charset.ISO_8859_1).use { writer ->
            assertEquals("ISO-8859-1", writer.encoding)
        }
    }

    @Test
    fun testEncodingPropertyAfterClose() {
        val baos = ByteArrayOutputStream()
        val writer = OutputStreamWriter(baos, Charset.UTF_8)
        writer.close()
        assertNull(writer.encoding, "Encoding property should be null after close.")
    }
}
