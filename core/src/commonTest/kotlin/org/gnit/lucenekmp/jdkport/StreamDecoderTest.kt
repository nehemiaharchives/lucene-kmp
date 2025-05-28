package org.gnit.lucenekmp.jdkport

import io.github.oshai.kotlinlogging.KotlinLogging
import okio.Buffer
import okio.BufferedSource
import okio.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class StreamDecoderTest {
    private val logger = KotlinLogging.logger {}

    private fun byteArraySource(data: ByteArray): BufferedSource = Buffer().apply { write(data) }

    @Test
    fun testReadSingleCharacter() {
        val input = "Hello, World!".encodeToByteArray()
        logger.debug { "Input bytes: ${input.joinToString(", ") { it.toString() }}" }
        val inputStream = OkioSourceInputStream(byteArraySource(input))
        val decoder = StreamDecoder.forInputStreamReader(inputStream, this, StandardCharsets.UTF_8)

        val firstChar = decoder.read()
        logger.debug { "First char code: $firstChar, expected: ${'H'.code}" }
        assertEquals('H'.code, firstChar)
    }

    @Test
    fun testReadIntoCharArray() {
        val input = "Hello, World!".encodeToByteArray()
        logger.debug { "Input bytes: ${input.joinToString(", ") { it.toString() }}" }
        val inputStream = OkioSourceInputStream(byteArraySource(input))
        val decoder = StreamDecoder.forInputStreamReader(inputStream, this, StandardCharsets.UTF_8)

        val buffer = CharArray(5)
        val charsRead = decoder.read(buffer, 0, buffer.size)
        logger.debug { "Buffer content: ${buffer.joinToString(", ") { it.toString() }}" }
        assertEquals(5, charsRead)
        assertEquals("Hello", buffer.concatToString())
    }

    @Test
    fun testReady() {
        val input = "Hello, World!".encodeToByteArray()
        val inputStream = OkioSourceInputStream(byteArraySource(input))
        val decoder = StreamDecoder.forInputStreamReader(inputStream, this, StandardCharsets.UTF_8)

        assertTrue(decoder.ready())
    }

    @Test
    fun testClose() {
        val input = "Hello, World!".encodeToByteArray()
        val inputStream = OkioSourceInputStream(byteArraySource(input))
        val decoder = StreamDecoder.forInputStreamReader(inputStream, this, StandardCharsets.UTF_8)

        decoder.close()
        assertFailsWith<IOException> {
            decoder.ready()
        }
    }
}
