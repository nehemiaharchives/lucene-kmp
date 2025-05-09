package org.gnit.lucenekmp.jdkport

import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.buffer
import kotlinx.io.sink
import kotlinx.io.source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KIOSourceBufferedReaderTest {

    @Test
    fun testRead() {
        val text = "Hello, World!"
        val source: Source = Buffer().writeUtf8(text)
        val reader = KIOSourceBufferedReader(source)

        val result = CharArray(text.length)
        reader.read(result, 0, text.length)

        assertEquals(text, String(result))
    }

    @Test
    fun testReadLine() {
        val text = "Hello, World!\nThis is a test."
        val source: Source = Buffer().writeUtf8(text)
        val reader = KIOSourceBufferedReader(source)

        val line1 = reader.readLine()
        val line2 = reader.readLine()

        assertEquals("Hello, World!", line1)
        assertEquals("This is a test.", line2)
    }

    @Test
    fun testReady() {
        val text = "Hello, World!"
        val source: Source = Buffer().writeUtf8(text)
        val reader = KIOSourceBufferedReader(source)

        assertTrue(reader.ready())
        reader.read()
        assertTrue(reader.ready())
    }

    @Test
    fun testMarkAndReset() {
        val text = "Hello, World!"
        val source: Source = Buffer().writeUtf8(text)
        val reader = KIOSourceBufferedReader(source)

        reader.mark(5)
        val result1 = CharArray(5)
        reader.read(result1, 0, 5)
        assertEquals("Hello", String(result1))

        reader.reset()
        val result2 = CharArray(5)
        reader.read(result2, 0, 5)
        assertEquals("Hello", String(result2))
    }

    @Test
    fun testSkip() {
        val text = "Hello, World!"
        val source: Source = Buffer().writeUtf8(text)
        val reader = KIOSourceBufferedReader(source)

        reader.skip(7)
        val result = CharArray(5)
        reader.read(result, 0, 5)
        assertEquals("World", String(result))
    }

    @Test
    fun testClose() {
        val text = "Hello, World!"
        val source: Source = Buffer().writeUtf8(text)
        val reader = KIOSourceBufferedReader(source)

        reader.close()
        assertFalse(reader.ready())
    }
}
