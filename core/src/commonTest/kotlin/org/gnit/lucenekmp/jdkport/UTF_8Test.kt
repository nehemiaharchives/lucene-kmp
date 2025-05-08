package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UTF_8Test {

    private val utf8Decoder = UTF_8().newDecoder()

    @Test
    fun testDecode() {
        val input = ByteBuffer.wrap("Hello".encodeToByteArray())
        val output = utf8Decoder.decode(input)
        assertEquals("Hello", output.toString())
    }

    @Test
    fun testDecodeMultiByte(){
        val inputString = "Grüße, мир, こんにちは"
        val inputBytes = inputString.encodeToByteArray()
        val inputBuffer = ByteBuffer.wrap(inputBytes)
        val output = utf8Decoder.decode(inputBuffer)
        assertEquals(inputString, output.toString())
    }

    @Test
    fun testOnMalformedInput() {
        utf8Decoder.onMalformedInput(CodingErrorAction.REPLACE)
        // 0xC3 0x28 is an invalid 2-byte sequence in UTF-8
        val input = ByteBuffer.wrap(byteArrayOf(0xC3.toByte(), 0x28))
        val output = CharBuffer.allocate(10)
        utf8Decoder.decode(input, output, true)
        utf8Decoder.flush(output)
        output.flip()
        // 0xC3 is invalid, replaced with \uFFFD, 0x28 is '('
        assertEquals("\uFFFD(", output.toString())
    }

    @Test
    fun testReset() {
        val input = ByteBuffer.wrap("Hello".encodeToByteArray())
        utf8Decoder.decode(input)
        utf8Decoder.reset()
        // Reset should allow decoding again from the same buffer
        input.position = 0
        val output = utf8Decoder.decode(input)
        assertEquals("Hello", output.toString())
    }

    @Test
    fun testCharset() {
        assertEquals(Charset.UTF_8, utf8Decoder.charset())
    }

    @Test
    fun testAverageCharsPerByte() {
        assertEquals(1.0f, utf8Decoder.averageCharsPerByte())
    }

    @Test
    fun testMaxCharsPerByte() {
        assertEquals(1.0f, utf8Decoder.maxCharsPerByte())
    }

    @Test
    fun testIsAutoDetecting() {
        assertTrue(!utf8Decoder.isAutoDetecting)
    }

    @Test
    fun testIsCharsetDetected() {
        assertFailsWith<UnsupportedOperationException> {
            utf8Decoder.isCharsetDetected
        }
    }

    @Test
    fun testDetectedCharset() {
        assertFailsWith<UnsupportedOperationException> {
            utf8Decoder.detectedCharset()
        }
    }
}
