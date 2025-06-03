package org.gnit.lucenekmp.jdkport

import io.github.oshai.kotlinlogging.KotlinLogging
import okio.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import org.gnit.lucenekmp.jdkport.Charset // Explicit import for Charset

class BufferedWriterTest {

    // per issue description, though direct logging in tests might not be needed
    private val logger = KotlinLogging.logger {}

    @Test
    fun testWrite() {
        val baos = ByteArrayOutputStream()
        val osw = OutputStreamWriter(baos, Charset.UTF_8)
        val bw = BufferedWriter(osw)
        bw.write("Hello")
        bw.write(" ")
        bw.write("World")
        bw.flush()
        val expectedString = "Hello World"
        val expectedBytes = expectedString.encodeToByteArray() // Platform's default UTF-8 encoding for "Hello World"
        val actualBytes = baos.toByteArray()
        assertEquals(expectedBytes.toList(), actualBytes.toList(), "Byte array content mismatch for 'Hello World'")
        // Also check string conversion to see if it's specifically the problem
        assertEquals(expectedString, actualBytes.decodeToString(), "Simple write failed (string conversion)")
        bw.close()
    }

    @Test
    fun testWriteEmptyString() {
        val baos = ByteArrayOutputStream()
        val osw = OutputStreamWriter(baos, Charset.UTF_8)
        val bw = BufferedWriter(osw)
        bw.write("")
        bw.flush()
        assertEquals("", baos.toByteArray().decodeToString(), "Write empty string failed")
        bw.close()
    }

    @Test
    fun testWriteNewline() {
        val baos = ByteArrayOutputStream()
        val osw = OutputStreamWriter(baos, Charset.UTF_8)
        val bw = BufferedWriter(osw)
        bw.write("Hello\nWorld")
        bw.flush()
        assertEquals("Hello\nWorld", baos.toByteArray().decodeToString(), "Write with newline failed")
        bw.close()
    }

    @Test
    fun testNewLine() {
        val baos = ByteArrayOutputStream()
        val osw = OutputStreamWriter(baos, Charset.UTF_8)
        val bw = BufferedWriter(osw)
        bw.write("Hello")
        bw.newLine()
        bw.write("World")
        bw.flush()
        val expected = "Hello" + System.lineSeparator() + "World"
        assertEquals(expected, baos.toByteArray().decodeToString(), "newLine() failed")
        bw.close()
    }

    @Test
    fun testWriteLargeString() {
        val baos = ByteArrayOutputStream()
        val osw = OutputStreamWriter(baos, Charset.UTF_8)
        // Use a small buffer size to ensure buffering is tested
        val bw = BufferedWriter(osw, 10)
        val largeString = "This is a very large string that should exceed the buffer size several times over. " +
                          "It is designed to test the flushing mechanism of the BufferedWriter. " +
                          "Let's repeat it a few times to make sure. " +
                          "Repeat 1. Repeat 2. Repeat 3. End of string."
        bw.write(largeString)
        bw.flush()
        assertEquals(largeString, baos.toByteArray().decodeToString(), "Write large string failed")
        bw.close()
    }

    @Test
    fun testWriteCharArrayPortion() {
        val baos = ByteArrayOutputStream()
        val osw = OutputStreamWriter(baos, Charset.UTF_8)
        val bw = BufferedWriter(osw)
        val chars = "0123456789".toCharArray()
        // Write "12345"
        bw.write(chars, 1, 5)
        bw.flush()
        assertEquals("12345", baos.toByteArray().decodeToString(), "Write char array portion failed")
        bw.close()
    }

    @Test
    fun testWriteStringPortion() {
        val baos = ByteArrayOutputStream()
        val osw = OutputStreamWriter(baos, Charset.UTF_8)
        val bw = BufferedWriter(osw)
        val str = "0123456789"
        // Write "12345"
        bw.write(str, 1, 5)
        bw.flush()
        assertEquals("12345", baos.toByteArray().decodeToString(), "Write string portion failed")
        bw.close()
    }

    @Test
    fun testWriteSingleCharacter() {
        val baos = ByteArrayOutputStream()
        val osw = OutputStreamWriter(baos, Charset.UTF_8)
        val bw = BufferedWriter(osw)
        bw.write('A'.code)
        bw.flush()
        assertEquals("A", baos.toByteArray().decodeToString(), "Write single character failed")
        bw.close()
    }

    @Test
    fun testFlushingOnClose() {
        val baos = ByteArrayOutputStream()
        val osw = OutputStreamWriter(baos, Charset.UTF_8)
        // Default buffer size
        val bw = BufferedWriter(osw)
        val testString = "Data to be written"
        bw.write(testString)
        // Data might still be in buffer
        // Closing the writer should flush it
        bw.close()
        assertEquals(testString, baos.toByteArray().decodeToString(), "Flushing on close failed")
    }

    @Test
    fun testWritingToClosedWriter() {
        val baos = ByteArrayOutputStream()
        val osw = OutputStreamWriter(baos, Charset.UTF_8)
        val bw = BufferedWriter(osw)
        bw.close()
        try {
            bw.write("test")
            // Should throw an exception
            kotlin.test.fail("Writing to a closed writer should throw IOException")
        } catch (e: IOException) {
            // Expected
        } catch (e: Exception) {
            kotlin.test.fail("Unexpected exception type: ${e::class.simpleName}")
        }
    }

    @Test
    fun testDoubleClose() {
        val baos = ByteArrayOutputStream()
        val osw = OutputStreamWriter(baos, Charset.UTF_8)
        val bw = BufferedWriter(osw)
        bw.close()
        try {
            bw.close() // Second close should not throw an exception
        } catch (e: Exception) {
            kotlin.test.fail("Double closing writer should not throw an exception: ${e::class.simpleName}")
        }
    }

    @Test
    fun testWriteDirectWhenCharArrayLengthExceedsMaxChars() {
        val baos = ByteArrayOutputStream()
        val osw = OutputStreamWriter(baos, Charset.UTF_8)
        // Use a small buffer size (maxChars will also be 5)
        val bw = BufferedWriter(osw, 5)
        val chars = "0123456789".toCharArray() // length 10

        // Attempt to write 7 chars, which is > maxChars (5)
        // This should trigger the direct write path after an initial flush.
        bw.write(chars, 1, 7) // write "1234567"
        bw.flush() // ensure data is written out for assertion

        assertEquals("1234567", baos.toByteArray().decodeToString(), "Direct write for char array (len > maxChars) failed")
        bw.close()
    }

    @Test
    fun testWriteDirectWhenStringLengthExceedsMaxChars() {
        val baos = ByteArrayOutputStream()
        val osw = OutputStreamWriter(baos, Charset.UTF_8)
        // Use a small buffer size (maxChars will also be 5)
        val bw = BufferedWriter(osw, 5)
        val str = "0123456789" // length 10

        // Attempt to write 7 chars, which is > maxChars (5)
        // This should trigger the direct write path after an initial flush.
        bw.write(str, 1, 7) // write "1234567"
        bw.flush() // ensure data is written out for assertion

        assertEquals("1234567", baos.toByteArray().decodeToString(), "Direct write for string (len > maxChars) failed")
        bw.close()
    }

    // Tests for methods inherited from Writer

    @Test
    fun testWriteCharArray() {
        val baos = ByteArrayOutputStream()
        val osw = OutputStreamWriter(baos, Charset.UTF_8)
        val bw = BufferedWriter(osw)
        val chars = "HelloWorld".toCharArray()
        bw.write(chars)
        bw.flush()
        assertEquals("HelloWorld", baos.toByteArray().decodeToString(), "write(char[]) failed")
        bw.close()
    }

    @Test
    fun testWriteEmptyCharArray() {
        val baos = ByteArrayOutputStream()
        val osw = OutputStreamWriter(baos, Charset.UTF_8)
        val bw = BufferedWriter(osw)
        val chars = "".toCharArray()
        bw.write(chars)
        bw.flush()
        assertEquals("", baos.toByteArray().decodeToString(), "write(char[]) with empty array failed")
        bw.close()
    }

    @Test
    fun testWriteString() {
        val baos = ByteArrayOutputStream()
        val osw = OutputStreamWriter(baos, Charset.UTF_8)
        val bw = BufferedWriter(osw)
        val str = "HelloWorld"
        bw.write(str)
        bw.flush()
        assertEquals("HelloWorld", baos.toByteArray().decodeToString(), "write(String) failed")
        bw.close()
    }

    @Test
    fun testWriteEmptyStringMethod() { // Renamed to avoid conflict with existing testWriteEmptyString
        val baos = ByteArrayOutputStream()
        val osw = OutputStreamWriter(baos, Charset.UTF_8)
        val bw = BufferedWriter(osw)
        val str = ""
        bw.write(str)
        bw.flush()
        assertEquals("", baos.toByteArray().decodeToString(), "write(String) with empty string failed")
        bw.close()
    }

    @Test
    fun testAppendCharSequence() {
        val baos = ByteArrayOutputStream()
        val osw = OutputStreamWriter(baos, Charset.UTF_8)
        val bw = BufferedWriter(osw)
        val csq: CharSequence = "HelloWorld"
        bw.append(csq)
        bw.flush()
        assertEquals("HelloWorld", baos.toByteArray().decodeToString(), "append(CharSequence) failed")
        bw.close()
    }

    @Test
    fun testAppendNullCharSequence() {
        val baos = ByteArrayOutputStream()
        val osw = OutputStreamWriter(baos, Charset.UTF_8)
        val bw = BufferedWriter(osw)
        bw.append(null)
        bw.flush()
        assertEquals("null", baos.toByteArray().decodeToString(), "append(null) should write 'null'")
        bw.close()
    }

    @Test
    fun testAppendEmptyCharSequence() {
        val baos = ByteArrayOutputStream()
        val osw = OutputStreamWriter(baos, Charset.UTF_8)
        val bw = BufferedWriter(osw)
        val csq: CharSequence = ""
        bw.append(csq)
        bw.flush()
        assertEquals("", baos.toByteArray().decodeToString(), "append(CharSequence) with empty sequence failed")
        bw.close()
    }

    @Test
    fun testAppendCharSequencePortion() {
        val baos = ByteArrayOutputStream()
        val osw = OutputStreamWriter(baos, Charset.UTF_8)
        val bw = BufferedWriter(osw)
        val csq: CharSequence = "0123456789"
        bw.append(csq, 1, 6) // Should append "12345"
        bw.flush()
        assertEquals("12345", baos.toByteArray().decodeToString(), "append(CharSequence, start, end) failed")
        bw.close()
    }

    @Test
    fun testAppendNullCharSequencePortion() {
        val baos = ByteArrayOutputStream()
        val osw = OutputStreamWriter(baos, Charset.UTF_8)
        val bw = BufferedWriter(osw)
        // Writer.append(csq, start, end) converts null csq to "null"
        bw.append(null, 0, 4) // Should append "null"
        bw.flush()
        assertEquals("null", baos.toByteArray().decodeToString(), "append(null, start, end) should write 'null'")
        bw.close()
    }


    @Test
    fun testAppendEmptyCharSequencePortion() {
        val baos = ByteArrayOutputStream()
        val osw = OutputStreamWriter(baos, Charset.UTF_8)
        val bw = BufferedWriter(osw)
        val csq: CharSequence = ""
        bw.append(csq, 0, 0) // Should append ""
        bw.flush()
        assertEquals("", baos.toByteArray().decodeToString(), "append(CharSequence, start, end) with empty sequence portion failed")
        bw.close()
    }

    @Test
    fun testAppendChar() {
        val baos = ByteArrayOutputStream()
        val osw = OutputStreamWriter(baos, Charset.UTF_8)
        val bw = BufferedWriter(osw)
        bw.append('A')
        bw.append('B')
        bw.append('C')
        bw.flush()
        assertEquals("ABC", baos.toByteArray().decodeToString(), "append(char) failed")
        bw.close()
    }
}
