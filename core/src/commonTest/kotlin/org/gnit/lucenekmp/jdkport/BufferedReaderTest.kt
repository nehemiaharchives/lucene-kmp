package org.gnit.lucenekmp.jdkport

import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
}
