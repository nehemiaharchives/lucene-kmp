package org.gnit.lucenekmp.jdkport

import okio.Buffer
import okio.BufferedSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KIOSourceBufferedReaderTest {

    private fun byteArraySource(data: ByteArray): BufferedSource = Buffer().apply { write(data) }

    @Test
    fun testRead() {
        val text = "Hello, World!"
        val source = byteArraySource(text.encodeToByteArray())
        val reader = KIOSourceBufferedReader(source)

        val result = CharArray(text.length)
        reader.read(result, 0, text.length)

        assertEquals(text, result.concatToString())
    }

    @Test
    fun testReadLine() {
        val text = "Hello, World!\nThis is a test."
        val source = byteArraySource(text.encodeToByteArray())
        val reader = KIOSourceBufferedReader(source)

        val line1 = reader.readLine()
        val line2 = reader.readLine()

        assertEquals("Hello, World!", line1)
        assertEquals("This is a test.", line2)
    }

    @Test
    fun testReady() {
        val text = "Hello, World!"
        val source = byteArraySource(text.encodeToByteArray())
        val reader = KIOSourceBufferedReader(source)

        assertTrue(reader.ready())
        reader.read()
        assertTrue(reader.ready())
    }

    @Test
    fun testMarkAndReset() {
        val text = "Hello, World!"
        val source = byteArraySource(text.encodeToByteArray())
        val reader = KIOSourceBufferedReader(source)

        reader.mark(5)
        val result1 = CharArray(5)
        reader.read(result1, 0, 5)
        assertEquals("Hello", result1.concatToString())

        reader.reset()
        val result2 = CharArray(5)
        reader.read(result2, 0, 5)
        assertEquals("Hello", result2.concatToString())
    }

    @Test
    fun testSkip() {
        val text = "Hello, World!"
        val source = byteArraySource(text.encodeToByteArray())
        val reader = KIOSourceBufferedReader(source)

        reader.skip(7)
        val result = CharArray(5)
        reader.read(result, 0, 5)
        assertEquals("World", result.concatToString())
    }

    @Test
    fun testClose() {
        val text = "Hello, World!"
        val source = byteArraySource(text.encodeToByteArray())
        val reader = KIOSourceBufferedReader(source)

        reader.close()

        // After closing, ready() should throw IOException
        var exceptionThrown = false
        try {
            reader.ready()
        } catch (e: Exception) {
            exceptionThrown = true
            assertTrue(e.message?.contains("Stream closed") == true)
        }
        assertTrue(exceptionThrown, "Expected IOException to be thrown when calling ready() on closed stream")
    }
}
