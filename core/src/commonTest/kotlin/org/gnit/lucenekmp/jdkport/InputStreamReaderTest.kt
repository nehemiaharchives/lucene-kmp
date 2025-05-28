package org.gnit.lucenekmp.jdkport

import io.github.oshai.kotlinlogging.KotlinLogging
import okio.Buffer
import okio.BufferedSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InputStreamReaderTest {

    private val logger = KotlinLogging.logger {}

    private fun byteArraySource(data: ByteArray): BufferedSource = Buffer().apply { write(data) }

    @Test
    fun testReadSingleCharacter() {
        val input = "Hello, World!".encodeToByteArray()
        logger.debug { "Input bytes: ${input.joinToString(", ") { it.toString() }}" }
        val inputStream = OkioSourceInputStream(byteArraySource(input))
        val reader = InputStreamReader(inputStream, StandardCharsets.UTF_8)

        val firstChar = reader.read()
        logger.debug { "First char code: $firstChar, expected: ${'H'.code}" }
        assertEquals('H'.code, firstChar)
    }

    @Test
    fun testReadIntoCharArray() {
        val input = "Hello, World!".encodeToByteArray()
        logger.debug { "Input bytes: ${input.joinToString(", ") { it.toString() }}" }
        val inputStream = OkioSourceInputStream(byteArraySource(input))
        val reader = InputStreamReader(inputStream, StandardCharsets.UTF_8)

        val buffer = CharArray(5)
        val bytesRead = reader.read(buffer, 0, buffer.size)
        logger.debug { "Buffer content: ${buffer.joinToString(", ") { it.toString() }}" }
        assertEquals(5, bytesRead)
        assertEquals("Hello", buffer.concatToString())
    }

    @Test
    fun testReady() {
        val input = "Hello, World!".encodeToByteArray()
        val inputStream = OkioSourceInputStream(byteArraySource(input))
        val reader = InputStreamReader(inputStream, StandardCharsets.UTF_8)

        assertTrue(reader.ready())
    }

    @Test
    fun testClose() {
        val input = "Hello, World!".encodeToByteArray()
        val inputStream = OkioSourceInputStream(byteArraySource(input))
        val reader = InputStreamReader(inputStream, StandardCharsets.UTF_8)

        reader.close()
        assertFalse(reader.ready())
    }
}
