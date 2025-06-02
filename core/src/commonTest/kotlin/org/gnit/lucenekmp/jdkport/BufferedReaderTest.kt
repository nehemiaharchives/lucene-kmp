package org.gnit.lucenekmp.jdkport

import okio.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.text.StringBuilder
import org.gnit.lucenekmp.jdkport.CharBuffer // Added import

// Helper class for testing transferTo
class StringWriter : Writer() {
    private val builder = StringBuilder()

    override fun write(cbuf: CharArray, off: Int, len: Int) {
        if (off < 0 || off > cbuf.size || len < 0 || off + len > cbuf.size || off + len < 0) {
            throw IndexOutOfBoundsException()
        }
        if (len == 0) {
            return
        }
        builder.appendRange(cbuf, off, off + len)
    }

    override fun flush() {
        // No-op for StringWriter
    }

    override fun close() {
        // No-op for StringWriter
    }

    override fun toString(): String {
        return builder.toString()
    }
}

class BufferedReaderTest {

    @Test
    fun testRead() {
        val input = "Hello, World!"
        val reader = BufferedReader(StringReader(input))
        val result = CharArray(input.length)
        reader.read(result, 0, input.length)
        assertEquals(input, result.concatToString())
    }

    @Test
    fun testSkip() {
        val input = "Hello, World!"
        val reader = BufferedReader(StringReader(input))
        reader.skip(7)
        val result = CharArray(6)
        reader.read(result, 0, 6)
        assertEquals("World!", result.concatToString())
    }

    @Test
    fun testReady() {
        val input = "Hello, World!"
        val reader = BufferedReader(StringReader(input))
        assertTrue(reader.ready())
        reader.read()
        assertTrue(reader.ready())
    }

    @Test
    fun testMarkAndReset() {
        val input = "Hello, World!"
        val reader = BufferedReader(StringReader(input))
        reader.mark(5)
        val result = CharArray(5)
        reader.read(result, 0, 5)
        assertEquals("Hello", result.concatToString())
        reader.reset()
        reader.read(result, 0, 5)
        assertEquals("Hello", result.concatToString())
    }

    @Test
    fun testMarkSupported() {
        val input = "Hello, World!"
        val reader = BufferedReader(StringReader(input))
        assertTrue(reader.markSupported())
    }

    @Test
    fun testClose() {
        val input = "Hello, World!"
        val reader = BufferedReader(StringReader(input))
        reader.close()
        assertFailsWith<IOException> { reader.read() }
    }

    @Test
    fun testReadSingleChar() {
        val input = "abc"
        val reader = BufferedReader(StringReader(input))
        assertEquals('a'.code, reader.read())
        assertEquals('b'.code, reader.read())
        assertEquals('c'.code, reader.read())
        assertEquals(-1, reader.read())
    }

    @Test
    fun testReadSingleCharEmptyStream() {
        val input = ""
        val reader = BufferedReader(StringReader(input))
        assertEquals(-1, reader.read())
    }

    @Test
    fun testReadLineBasic() {
        val input = "line1\nline2"
        val reader = BufferedReader(StringReader(input))
        assertEquals("line1", reader.readLine())
        assertEquals("line2", reader.readLine())
        assertEquals(null, reader.readLine())
    }

    @Test
    fun testReadLineDifferentEndings() {
        val input = "line1\nline2\rline3\r\nline4"
        val reader = BufferedReader(StringReader(input))
        assertEquals("line1", reader.readLine())
        assertEquals("line2", reader.readLine())
        assertEquals("line3", reader.readLine())
        assertEquals("line4", reader.readLine())
        assertEquals(null, reader.readLine())
    }

    @Test
    fun testReadLineEmptyLines() {
        val input = "\nline1\n\nline2\n"
        val reader = BufferedReader(StringReader(input))
        assertEquals("", reader.readLine())
        assertEquals("line1", reader.readLine())
        assertEquals("", reader.readLine())
        assertEquals("line2", reader.readLine())
        assertEquals(null, reader.readLine())
    }

    @Test
    fun testReadLineEmptyStream() {
        val input = ""
        val reader = BufferedReader(StringReader(input))
        assertEquals(null, reader.readLine())
    }

    @Test
    fun testReadLineNoEnding() {
        val input = "line1"
        val reader = BufferedReader(StringReader(input))
        assertEquals("line1", reader.readLine())
        assertEquals(null, reader.readLine())
    }

    @Test
    fun testReadLineIgnoreLF() {
        var input = "line1\r\nline2"
        var reader = BufferedReader(StringReader(input))
        assertEquals("line1", reader.readLine(true, null))
        assertEquals("line2", reader.readLine()) // or reader.readLine(false, null)

        input = "line1\rline2"
        reader = BufferedReader(StringReader(input))
        assertEquals("line1", reader.readLine(true, null))
        assertEquals("line2", reader.readLine())
    }

    @Test
    fun testReadLineTerminator() {
        val term = booleanArrayOf(false)

        var input = "line1\nline2"
        var reader = BufferedReader(StringReader(input))
        assertEquals("line1", reader.readLine(false, term))
        assertTrue(term[0])
        assertEquals("line2", reader.readLine(false, term))
        assertFalse(term[0])

        input = "line3"
        reader = BufferedReader(StringReader(input))
        term[0] = false // Reset for next read
        assertEquals("line3", reader.readLine(false, term))
        assertFalse(term[0])
        term[0] = false // Reset for next read (expecting null)
        assertEquals(null, reader.readLine(false, term))
        assertFalse(term[0])
    }

    @Test
    fun testReadLineVariedLengthAndContent() {
        val longLine = "a".repeat(1000)
        val specialCharsLine = "!@#\$%^&*()_+|}{[]:';\",./<>?"
        val input = "$longLine\n$specialCharsLine\r\n\nline3\rfinal line without ending"
        val reader = BufferedReader(StringReader(input))
        assertEquals(longLine, reader.readLine())
        assertEquals(specialCharsLine, reader.readLine())
        assertEquals("", reader.readLine())
        assertEquals("line3", reader.readLine())
        assertEquals("final line without ending", reader.readLine())
        assertEquals(null, reader.readLine())
    }

    @Test
    fun testTransferTo() {
        val input = "Hello, World!\nThis is a test."
        val reader = BufferedReader(StringReader(input))
        val writer = StringWriter()
        reader.transferTo(writer)
        assertEquals(input, writer.toString())
    }

    @Test
    fun testTransferToEmptySource() {
        val input = ""
        val reader = BufferedReader(StringReader(input))
        val writer = StringWriter()
        reader.transferTo(writer)
        assertEquals(input, writer.toString())
    }

    @Test
    fun testTransferToWithLargeContent() {
        val largeInput = "a".repeat(10000)
        val reader = BufferedReader(StringReader(largeInput))
        val writer = StringWriter()
        reader.transferTo(writer)
        assertEquals(largeInput, writer.toString())
    }

    @Test
    fun testReadIntoCharBufferBasic() {
        val reader = BufferedReader(StringReader("abcdefghij"))
        val cb = CharBuffer.allocate(10)
        val charsRead = reader.read(cb)
        assertEquals(10, charsRead)
        assertEquals(10, cb.position())
        cb.flip() // pos=0, limit=10
        assertEquals("abcdefghij", cb.toString())
    }

    @Test
    fun testReadIntoCharBufferSmallerThanStream() {
        val reader = BufferedReader(StringReader("abcdefghij"))
        val cb = CharBuffer.allocate(5)

        assertEquals(5, reader.read(cb))
        cb.flip()
        assertEquals("abcde", cb.toString())
        cb.clear()

        assertEquals(5, reader.read(cb))
        cb.flip()
        assertEquals("fghij", cb.toString())
        cb.clear()

        assertEquals(-1, reader.read(cb))
    }

    @Test
    fun testReadIntoCharBufferLargerThanStream() {
        val reader = BufferedReader(StringReader("abc"))
        val cb = CharBuffer.allocate(5)
        assertEquals(3, reader.read(cb))
        assertEquals(3, cb.position())
        cb.flip() // pos=0, limit=3
        assertEquals("abc", cb.toString())
    }

    @Test
    fun testReadIntoCharBufferWithPrePositionedBuffer() {
        val reader = BufferedReader(StringReader("1234567")) // Stream has 7 chars
        val cb = CharBuffer.allocate(10) // Buffer capacity 10
        // Pre-fill with some data to ensure it's handled correctly
        cb.putAbsolute(0, 'x')
        cb.putAbsolute(1, 'y')
        cb.putAbsolute(2, 'z')
        cb.position = 3 // Start writing at index 3. Remaining is 7.

        val charsRead = reader.read(cb)
        assertEquals(7, charsRead) // Reads all 7 chars from stream
        assertEquals(10, cb.position()) // Position is now 3 + 7 = 10
        cb.flip() // pos=0, limit=10
        // Content of cb should be 'x', 'y', 'z', '1', '2', '3', '4', '5', '6', '7'
        assertEquals("xyz1234567", cb.toString())
    }

    @Test
    fun testReadIntoCharBufferEmptyStream() {
        val reader = BufferedReader(StringReader(""))
        val cb = CharBuffer.allocate(5)
        assertEquals(-1, reader.read(cb))
    }

    @Test
    fun testReadIntoCharBufferReadOnly() {
        val reader = BufferedReader(StringReader("abc"))
        val cb = CharBuffer.allocate(5)
        val readOnlyCb = cb.asReadOnlyBuffer()
        assertFailsWith<Exception> { reader.read(readOnlyCb) } // As per Reader.kt, it throws generic Exception
    }

    @Test
    fun testReadIntoCharBufferZeroRemaining() {
        val reader = BufferedReader(StringReader("abc"))
        val cb = CharBuffer.allocate(5)
        cb.position = 5 // No space remaining (position == limit, as limit == capacity initially)
        assertEquals(0, reader.read(cb))
        assertEquals(5, cb.position()) // Position unchanged
    }
}
