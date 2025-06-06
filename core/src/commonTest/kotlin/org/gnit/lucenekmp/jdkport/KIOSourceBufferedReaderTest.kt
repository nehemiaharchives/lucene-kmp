package org.gnit.lucenekmp.jdkport

import okio.Buffer
import okio.BufferedSource
import okio.IOException
import kotlin.test.*

class KIOSourceBufferedReaderTest {

    private fun createReader(content: String, bufferSize: Int = KIOSourceBufferedReader.DEFAULT_BUFFER_SIZE): KIOSourceBufferedReader {
        val bufferedSource: BufferedSource = Buffer().writeUtf8(content)
        return KIOSourceBufferedReader(bufferedSource, bufferSize)
    }

    @Test
    fun testReadSingleCharacter() {
        val reader = createReader("abc")
        assertEquals('a'.code, reader.read())
        assertEquals('b'.code, reader.read())
        assertEquals('c'.code, reader.read())
    }

    @Test
    fun testReadEmptySource() {
        val reader = createReader("")
        assertEquals(-1, reader.read())
    }

    @Test
    fun testReadEOF() {
        val reader = createReader("a")
        assertEquals('a'.code, reader.read())
        assertEquals(-1, reader.read())
        assertEquals(-1, reader.read()) // Subsequent calls also return -1
    }

    @Test
    fun testReadMultiByteCharacters() {
        val reader = createReader("你好世界") // Hello world in Chinese
        assertEquals('你'.code, reader.read())
        assertEquals('好'.code, reader.read())
        assertEquals('世'.code, reader.read())
        assertEquals('界'.code, reader.read())
        assertEquals(-1, reader.read())
    }

    @Test
    fun testReadWithSmallBuffer() {
        // Buffer size 1, forcing buffer fill for each char
        val reader = createReader("abc", bufferSize = 1)
        assertEquals('a'.code, reader.read())
        assertEquals('b'.code, reader.read())
        assertEquals('c'.code, reader.read())
        assertEquals(-1, reader.read())
    }

    @Test
    fun testReadFromClosedStream() {
        val reader = createReader("abc")
        reader.close()
        assertFailsWith<IOException> {
            reader.read()
        }
    }

    @Test
    fun testReadCharArray() {
        val reader = createReader("abcdefghij")
        val buffer = CharArray(5)

        // Read first 5 chars
        val charsRead1 = reader.read(buffer, 0, 5)
        assertEquals(5, charsRead1)
        assertEquals("abcde", buffer.concatToString())

        // Read next 5 chars
        val charsRead2 = reader.read(buffer, 0, 5)
        assertEquals(5, charsRead2)
        assertEquals("fghij", buffer.concatToString())

        // Try to read again, should be EOF
        val charsRead3 = reader.read(buffer, 0, 5)
        assertEquals(-1, charsRead3)
    }

    @Test
    fun testReadCharArrayWithOffset() {
        val reader = createReader("abcdef")
        val buffer = CharArray(10)
        buffer.fill('z') // Pre-fill to see offset effect

        val charsRead = reader.read(buffer, 2, 4) // Read "abcd" into buffer[2]..buffer[5]
        assertEquals(4, charsRead)
        assertEquals("zzabcdzzzz", buffer.concatToString())
    }

    @Test
    fun testReadCharArrayLenZero() {
        val reader = createReader("abc")
        val buffer = CharArray(5)
        val charsRead = reader.read(buffer, 0, 0)
        assertEquals(0, charsRead)
    }

    @Test
    fun testReadCharArrayEOF() {
        val reader = createReader("abc")
        val buffer = CharArray(5)

        val charsRead1 = reader.read(buffer, 0, 5)
        assertEquals(3, charsRead1)
        assertEquals("abc", buffer.concatToString(0, 3))

        val charsRead2 = reader.read(buffer, 0, 5)
        assertEquals(-1, charsRead2)
    }

    @Test
    fun testReadCharArrayPartialReadThenEOF() {
        val reader = createReader("ab")
        val buffer = CharArray(5)
        val charsRead = reader.read(buffer, 0, 5) // Request 5, get 2
        assertEquals(2, charsRead)
        assertEquals("ab", buffer.concatToString(0, 2))

        val nextRead = reader.read(buffer, 0, 5)
        assertEquals(-1, nextRead)
    }

    @Test
    fun testReadCharArrayLargerThanSource() {
        val reader = createReader("abc")
        val buffer = CharArray(10)
        val charsRead = reader.read(buffer, 0, 10)
        assertEquals(3, charsRead)
        assertEquals("abc", buffer.concatToString(0, 3))
    }

    @Test
    fun testReadCharArrayWithSmallInternalBuffer() {
        val reader = createReader("abcdefghijklmno", bufferSize = 3)
        val buffer = CharArray(7)

        // This will require multiple fills of the internal buffer
        val charsRead = reader.read(buffer, 0, 7)
        assertEquals(7, charsRead)
        assertEquals("abcdefg", buffer.concatToString())

        val nextBuffer = CharArray(10)
        val charsRead2 = reader.read(nextBuffer, 0, 10)
        assertEquals(8, charsRead2) // "hijklmno"
        assertEquals("hijklmno", nextBuffer.concatToString(0, 8))

        assertEquals(-1, reader.read(nextBuffer, 0, 1))
    }

    @Test
    fun testReadCharArrayInvalidParams() {
        val reader = createReader("abc")
        val buffer = CharArray(5)
        assertFailsWith<IndexOutOfBoundsException> {
            reader.read(buffer, -1, 3) // Negative offset
        }
        assertFailsWith<IndexOutOfBoundsException> {
            reader.read(buffer, 0, -1) // Negative length
        }
        assertFailsWith<IndexOutOfBoundsException> {
            reader.read(buffer, 0, 6) // Length > buffer size
        }
        assertFailsWith<IndexOutOfBoundsException> {
            reader.read(buffer, 3, 3) // Offset + length > buffer size
        }
    }

    @Test
    fun testReadCharArrayClosedStream() {
        val reader = createReader("abc")
        val buffer = CharArray(5)
        reader.close()
        assertFailsWith<IOException> {
            reader.read(buffer, 0, 3)
        }
    }

    @Test
    fun testReadLineBasic() {
        val reader = createReader("line1\nline2\nline3")
        assertEquals("line1", reader.readLine())
        assertEquals("line2", reader.readLine())
        assertEquals("line3", reader.readLine())
        assertNull(reader.readLine())
    }

    @Test
    fun testReadLineDifferentTerminators() {
        val reader = createReader("lineA\rlineB\r\nlineC\nlineD")
        assertEquals("lineA", reader.readLine())
        assertEquals("lineB", reader.readLine())
        assertEquals("lineC", reader.readLine())
        assertEquals("lineD", reader.readLine())
        assertNull(reader.readLine())
    }

    @Test
    fun testReadLineEmptyLines() {
        val reader = createReader("\nline1\n\nline2\n")
        assertEquals("", reader.readLine())
        assertEquals("line1", reader.readLine())
        assertEquals("", reader.readLine())
        assertEquals("line2", reader.readLine())
        assertNull(reader.readLine())
    }

    @Test
    fun testReadLineLastLineNoTerminator() {
        val reader = createReader("line1\nlast line")
        assertEquals("line1", reader.readLine())
        assertEquals("last line", reader.readLine())
        assertNull(reader.readLine())
    }

    @Test
    fun testReadLineEmptySourceReturnsNull() {
        val reader = createReader("")
        assertNull(reader.readLine())
    }

    @Test
    fun testReadLineOnlyTerminators() {
        val reader = createReader("\n\r\n\r")
        assertEquals("", reader.readLine())
        assertEquals("", reader.readLine())
        assertEquals("", reader.readLine())
        assertNull(reader.readLine()) // KIOSourceBufferedReader behavior might differ from JDK's last empty line
    }

    @Test
    fun testReadLineLongerThanBufferSize() {
        val longLine = "a".repeat(KIOSourceBufferedReader.DEFAULT_BUFFER_SIZE * 2)
        val reader = createReader("$longLine\nshortLine")
        assertEquals(longLine, reader.readLine())
        assertEquals("shortLine", reader.readLine())
        assertNull(reader.readLine())
    }

    @Test
    fun testReadLineWithSmallInternalBuffer() {
        val reader = createReader("line1\nline2\nline3", bufferSize = 5)
        assertEquals("line1", reader.readLine())
        assertEquals("line2", reader.readLine())
        assertEquals("line3", reader.readLine())
        assertNull(reader.readLine())
    }

    @Test
    fun testReadLineLongLineWithSmallInternalBuffer() {
        val longLine = "abcdefghijklmnopqrstuvwxyz" // 26 chars
        // Buffer size less than line length, and not aligned with line breaks
        val reader = createReader("$longLine\n123\nend", bufferSize = 10)
        assertEquals(longLine, reader.readLine())
        assertEquals("123", reader.readLine())
        assertEquals("end", reader.readLine())
        assertNull(reader.readLine())
    }

    @Test
    fun testReadLineFromClosedStream() {
        val reader = createReader("line1\nline2")
        reader.close()
        assertFailsWith<IOException> {
            reader.readLine()
        }
    }

    @Test
    fun testReadLineIntermediateEmptyLine() {
        val reader = createReader("lineA\n\nlineC")
        assertEquals("lineA", reader.readLine())
        assertEquals("", reader.readLine())
        assertEquals("lineC", reader.readLine())
        assertNull(reader.readLine())
    }

    @Test
    fun testReadLineEndsWithCR() {
        val reader = createReader("lineA\r")
        assertEquals("lineA", reader.readLine())
        assertNull(reader.readLine())
    }

    @Test
    fun testReadLineEndsWithCRLF() {
        val reader = createReader("lineA\r\n")
        assertEquals("lineA", reader.readLine())
        assertNull(reader.readLine())
    }

    @Test
    fun testReadyWhenBufferHasData() {
        val reader = createReader("abc", bufferSize = 3) // Fill buffer with "abc"
        reader.read() // Reads 'a'. Now pos=1, end=3. "bc" are in buffer.
        assertTrue(reader.ready(), "Reader should be ready as 'b' and 'c' are in buffer")
        reader.read() // Reads 'b'. Now pos=2, end=3. "c" is in buffer.
        assertTrue(reader.ready(), "Reader should be ready as 'c' is in buffer")
    }

    @Test
    fun testReadyWhenBufferEmptyButSourceHasData() {
        // Use a source that is not exhausted initially
        val source = Buffer().writeUtf8("def")
        val reader = KIOSourceBufferedReader(source, bufferSize = 2) // Internal buffer size 2
        // At this point, internal buffer `buf` is empty. pos=0, end=0.
        // `source` contains "def".
        assertTrue(reader.ready(), "Reader should be ready as underlying source has data")

        // Read "de", filling and consuming buffer
        assertEquals('d'.code, reader.read()) // Fills buffer with "de", reads 'd'. pos=1, end=2
        assertEquals('e'.code, reader.read()) // Reads 'e'. pos=2, end=2. Buffer is now 'consumed'.

        // Buffer is consumed (pos == end). Source still has "f".
        assertTrue(reader.ready(), "Reader should be ready as underlying source still has 'f'")

        assertEquals('f'.code, reader.read()) // Reads 'f'. Buffer filled with "f". pos=1, end=1. Source is now exhausted.
        // Buffer has 'f', but source is exhausted. If we read 'f', then ready() should be false.
        assertFalse(reader.ready(), "Reader should not be ready as source is exhausted after reading 'f'")
    }

    @Test
    fun testReadyAtEOF() {
        val reader = createReader("a")
        reader.read() // Read the only character 'a'
        assertFalse(reader.ready(), "Reader should not be ready at EOF")
    }

    @Test
    fun testReadyEmptySource() {
        val reader = createReader("")
        assertFalse(reader.ready(), "Reader should not be ready for an empty source")
    }

    @Test
    fun testReadyOnClosedStream() {
        val reader = createReader("abc")
        reader.close()
        assertFailsWith<IOException> {
            reader.ready()
        }
    }

    @Test
    fun testMarkSupported() {
        val reader = createReader("")
        assertTrue(reader.markSupported(), "KIOSourceBufferedReader should support mark")
    }

    @Test
    fun testMarkAndResetBasic() {
        val reader = createReader("abcdefgh")
        assertEquals('a'.code, reader.read()) // a
        reader.mark(5) // Mark after reading 'a', current pos is at 'b'
        assertEquals('b'.code, reader.read()) // b
        assertEquals('c'.code, reader.read()) // c
        reader.reset() // Reset to 'b'
        assertEquals('b'.code, reader.read()) // b again
        assertEquals('c'.code, reader.read()) // c again
        assertEquals('d'.code, reader.read()) // d
    }

    @Test
    fun testMarkAndResetAtStart() {
        val reader = createReader("abc")
        reader.mark(3)
        assertEquals('a'.code, reader.read())
        reader.reset()
        assertEquals('a'.code, reader.read())
        assertEquals('b'.code, reader.read())
    }

    @Test
    fun testMarkAndResetExceedReadAheadLimit() {
        val reader = createReader("abcdefghij", bufferSize = 5) // bufferSize < readAheadLimit for some tests
        assertEquals('a'.code, reader.read()) // Read 'a'
        reader.mark(3) // Mark here, next char is 'b'. Limit allows reading 'b', 'c', 'd'.

        assertEquals('b'.code, reader.read()) // char 1 within limit
        assertEquals('c'.code, reader.read()) // char 2 within limit
        assertEquals('d'.code, reader.read()) // char 3 within limit

        reader.reset()
        assertEquals('b'.code, reader.read(), "Reset after reading up to limit edge should work") //b
        assertEquals('c'.code, reader.read()) //c
        assertEquals('d'.code, reader.read()) //d

        reader.reset() // back to 'b'
        assertEquals('b'.code, reader.read()) //b
        assertEquals('c'.code, reader.read()) //c
        assertEquals('d'.code, reader.read()) //d
        assertEquals('e'.code, reader.read()) // Read 'e'. pos=5, end=5. (pos-markPos = 4)

        reader.reset()
        assertEquals('b'.code, reader.read(), "Reset after reading past limit but before fill should work")

        val reader2 = createReader("abcdefghij", bufferSize = 3) // Small buffer
        assertEquals('a'.code, reader2.read())
        reader2.mark(2)
        assertEquals('b'.code, reader2.read())
        assertEquals('c'.code, reader2.read())
        assertEquals('d'.code, reader2.read())
        assertFailsWith<IOException>("Reset should fail as mark was invalidated by reading 'd'") {
            reader2.reset()
        }
    }

    @Test
    fun testResetWithoutMark() {
        val reader = createReader("abc")
        assertFailsWith<IOException> {
            reader.reset()
        }
        reader.read()
        assertFailsWith<IOException> { // Still fails even after reading
            reader.reset()
        }
    }

    @Test
    fun testMarkWithLargeReadAheadLimit() {
        val reader = createReader("abcdefghijklmno", bufferSize = 5)
        assertEquals('a'.code, reader.read())
        reader.mark(10)

        assertEquals('b'.code, reader.read())
        assertEquals('c'.code, reader.read())
        assertEquals('d'.code, reader.read())
        assertEquals('e'.code, reader.read())

        assertEquals('f'.code, reader.read())
        assertEquals('g'.code, reader.read())
        assertEquals('h'.code, reader.read())
        assertEquals('i'.code, reader.read())
        assertEquals('j'.code, reader.read())
        assertEquals('k'.code, reader.read())

        reader.reset()
        assertEquals('b'.code, reader.read(), "Reset to 'b' after reading 10 chars within larger limit")
        var count = 1
        while(reader.read() != -1) count++
        assertEquals(14, count, "Should read remaining 14 chars (b..o) after reset")
    }

    @Test
    fun testMarkReadAheadLimitZero() {
        val reader = createReader("abc")
        reader.mark(0)
        reader.reset()
        assertEquals('a'.code, reader.read())

        val reader3 = createReader("abc", bufferSize = 1)
        assertEquals('a'.code, reader3.read())
        reader3.mark(0)
        assertFailsWith<IOException>("Reset should fail after reading a char with readAheadLimit=0 and fill") {
            assertEquals('b'.code, reader3.read())
            reader3.reset()
        }
    }


    @Test
    fun testMarkOnClosedStream() {
        val reader = createReader("abc")
        reader.close()
        assertFailsWith<IOException> {
            reader.mark(5)
        }
    }

    @Test
    fun testResetOnClosedStream() {
        val reader = createReader("abc")
        reader.mark(5)
        reader.close()
        assertFailsWith<IOException> {
            reader.reset()
        }
    }

    @Test
    fun testSkipBasic() {
        val reader = createReader("abcdefghij")
        var skipped = reader.skip(3) // Skip "abc"
        assertEquals(3L, skipped)
        assertEquals('d'.code, reader.read(), "Next char should be 'd' after skipping 3")

        skipped = reader.skip(2) // Skip "ef" according to JDK semantics
        assertEquals(2L, skipped)
        assertEquals('g'.code, reader.read(), "Next char should be 'g' after skipping 2 more")
    }

    @Test
    fun testSkipZero() {
        val reader = createReader("abc")
        val skipped = reader.skip(0)
        assertEquals(0L, skipped)
        assertEquals('a'.code, reader.read(), "Next char should be 'a' after skipping 0")
    }

    @Test
    fun testSkipNegative() {
        val reader = createReader("abc")
        assertFailsWith<IllegalArgumentException> {
            reader.skip(-5)
        }
    }

    @Test
    fun testSkipMoreThanAvailable() {
        val reader = createReader("abcde")
        val skipped = reader.skip(10)
        assertEquals(5L, skipped, "Should skip all 5 available chars")
        assertEquals(-1, reader.read(), "Should be EOF after skipping all chars")
    }

    @Test
    fun testSkipExactlyAvailable() {
        val reader = createReader("abc")
        val skipped = reader.skip(3)
        assertEquals(3L, skipped)
        assertEquals(-1, reader.read(), "Should be EOF")
    }

    @Test
    fun testSkipWhenAlreadyAtEOF() {
        val reader = createReader("a")
        assertEquals('a'.code, reader.read()) // Consume 'a'
        assertEquals(-1, reader.read())      // Confirm EOF

        val skipped = reader.skip(5)
        assertEquals(0L, skipped, "Skipping at EOF should return 0")
    }

    @Test
    fun testSkipWithSmallInternalBuffer() {
        val reader = createReader("abcdefghijklmnopqrstuvwxyz", bufferSize = 5)
        // Skip some chars, forcing some buffer fills
        val skipped1 = reader.skip(7) // Skips 'a' through 'g'
        assertEquals(7L, skipped1)
        assertEquals('h'.code, reader.read(), "Next char should be 'h'")

        // Skip more chars, potentially past current buffer + more fills
        val skipped2 = reader.skip(10) // Skips 'i' through 'r' (10 chars after 'h')
        assertEquals(10L, skipped2)
        assertEquals('s'.code, reader.read(), "Next char should be 's'")
    }

    @Test
    fun testSkipConsumingCurrentBufferThenMore() {
        val reader = createReader("abcdefghij", bufferSize = 4) // Initial buffer: "abcd"
        assertEquals('a'.code, reader.read()) // Reads 'a'. Buffer: "abcd", pos=1. Remaining in buffer: "bcd" (3 chars)

        // Skip 5 chars: "bcdef".
        // 3 from buffer ("bcd"), then 2 from source ("ef") which requires a fill.
        val skipped = reader.skip(5)
        assertEquals(5L, skipped)
        assertEquals('g'.code, reader.read(), "Next char should be 'g'")
    }

    @Test
    fun testSkipOnClosedStream() {
        val reader = createReader("abc")
        reader.close()
        assertFailsWith<IOException> {
            reader.skip(2)
        }
    }

    @Test
    fun testClose() {
        val source = Buffer().writeUtf8("abc")
        val reader = KIOSourceBufferedReader(source)

        reader.close()

        assertFailsWith<IOException>("read() on closed stream should fail") { reader.read() }
        assertFailsWith<IOException>("read(char[]) on closed stream should fail") { reader.read(CharArray(1), 0, 1) }
        assertFailsWith<IOException>("readLine() on closed stream should fail") { reader.readLine() }
        assertFailsWith<IOException>("ready() on closed stream should fail") { reader.ready() }
        assertFailsWith<IOException>("skip() on closed stream should fail") { reader.skip(1) }
        assertFailsWith<IOException>("mark() on closed stream should fail") { reader.mark(1) }
        assertFailsWith<IOException>("reset() on closed stream should fail") { reader.reset() }
    }

    @Test
    fun testCloseAlreadyClosed() {
        val reader = createReader("abc")
        reader.close() // First close

        try {
            reader.close() // Second close should not throw
        } catch (e: Exception) {
            fail("Closing an already closed stream should not throw an exception, but got $e")
        }

        // Verify it's still closed
        assertFailsWith<IOException>("read() on doubly closed stream should still fail") {
            reader.read()
        }
    }

    @Test
    fun testOperationsAfterCloseThrowIOException() {
        val reader = createReader("test data")
        reader.close()

        assertFailsWith<IOException> { reader.read() }
        val arr = CharArray(5)
        assertFailsWith<IOException> { reader.read(arr, 0, 1) }
        assertFailsWith<IOException> { reader.ready() }
        assertFailsWith<IOException> { reader.readLine() }
        assertFailsWith<IOException> { reader.skip(1) }
        assertFailsWith<IOException> { reader.mark(1) }
        assertFailsWith<IOException> { reader.reset() }
    }
}
