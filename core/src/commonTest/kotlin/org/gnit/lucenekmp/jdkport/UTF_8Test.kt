package org.gnit.lucenekmp.jdkport

import org.gnit.lucenekmp.jdkport.UTF_8.Companion.JLA
import org.gnit.lucenekmp.jdkport.UTF_8.Companion.updatePositions
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

    // Tests for companion object methods
    @Test
    fun testUpdatePositions() {
        val srcArray = "abcdef".encodeToByteArray()
        val dstArray = CharArray(6)
        val src = ByteBuffer.wrap(srcArray)
        val dst = CharBuffer.wrap(dstArray)
        // Set initial positions
        src.position = 2
        dst.position = 3
        // Call updatePositions to set positions to 1 and 2
        updatePositions(src, 1, dst, 2)
        assertEquals(1, src.position)
        assertEquals(2, dst.position)
    }

    @Test
    fun testDecodeASCII_AllAscii() {
        val src = "HelloWorld".encodeToByteArray()
        val dst = CharArray(10)
        val decoded = JLA.decodeASCII(src, 0, dst, 0, src.size)
        assertEquals(10, decoded)
        assertEquals("HelloWorld", dst.concatToString())
    }

    @Test
    fun testDecodeASCII_PartialAscii() {
        val src = byteArrayOf(72, 101, 108, 108, 111, -1, 87, 111, 114, 108, 100) // "Hello" + non-ASCII + "World"
        val dst = CharArray(11)
        val decoded = JLA.decodeASCII(src, 0, dst, 0, src.size)
        assertEquals(5, decoded)
        assertEquals("Hello", dst.concatToString(0, 5))
    }

    @Test
    fun testDecodeASCII_NullDst() {
        val src = "abc".encodeToByteArray()
        val decoded = JLA.decodeASCII(src, 0, null, 0, src.size)
        assertEquals(0, decoded)
    }

    @Test
    fun testDecodeASCII_OffsetAndLength() {
        val src = "0123456789".encodeToByteArray()
        val dst = CharArray(5)
        val decoded = JLA.decodeASCII(src, 2, dst, 0, 5)
        assertEquals(5, decoded)
        assertEquals("23456", dst.concatToString())
    }
}
