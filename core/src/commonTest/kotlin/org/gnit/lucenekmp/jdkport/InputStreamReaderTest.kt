package org.gnit.lucenekmp.jdkport

import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InputStreamReaderTest {

    @Test
    fun testReadSingleCharacter() {
        val input = "Hello, World!".encodeToByteArray()
        val inputStream = KIOSourceInputStream(Buffer().write(input))
        val reader = InputStreamReader(inputStream, Charset.forName("UTF-8"))

        val firstChar = reader.read()
        assertEquals('H'.code, firstChar)
    }

    @Test
    fun testReadIntoCharArray() {
        val input = "Hello, World!".encodeToByteArray()
        val inputStream = KIOSourceInputStream(Buffer().write(input))
        val reader = InputStreamReader(inputStream, Charset.forName("UTF-8"))

        val buffer = CharArray(5)
        val bytesRead = reader.read(buffer, 0, buffer.size)
        assertEquals(5, bytesRead)
        assertEquals("Hello", buffer.concatToString())
    }

    @Test
    fun testReady() {
        val input = "Hello, World!".encodeToByteArray()
        val inputStream = KIOSourceInputStream(Buffer().write(input))
        val reader = InputStreamReader(inputStream, Charset.forName("UTF-8"))

        assertTrue(reader.ready())
    }

    @Test
    fun testClose() {
        val input = "Hello, World!".encodeToByteArray()
        val inputStream = KIOSourceInputStream(Buffer().write(input))
        val reader = InputStreamReader(inputStream, Charset.forName("UTF-8"))

        reader.close()
        assertFalse(reader.ready())
    }
}
