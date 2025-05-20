package org.gnit.lucenekmp.jdkport

import org.gnit.lucenekmp.jdkport.UTF_8.Companion.JLA
import org.gnit.lucenekmp.jdkport.UTF_8.Companion.updatePositions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.test.assertContentEquals

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
    fun testDecodeASCII_OffsetAndLength() {
        val src = "0123456789".encodeToByteArray()
        val dst = CharArray(5)
        val decoded = JLA.decodeASCII(src, 2, dst, 0, 5)
        assertEquals(5, decoded)
        assertEquals("23456", dst.concatToString())
    }

    // --- UTF-8 combinations decoding tests ---

    private val logger = KotlinLogging.logger {}
    private val utf8DecoderCombo = UTF_8().newDecoder()

    // Characters for each bit-width
    private val ascii = 'A' // 7 bits: U+0041
    private val elevenBit = 'é' // 11 bits: U+00E9
    private val sixteenBit = '漢' // 16 bits: U+6F22
    private val twentyOneBit = "\uD83D\uDE00" // 21 bits: U+1F600 (😀, surrogate pair)

    // Helper to get UTF-8 bytes for a string
    private fun utf8Bytes(s: String): ByteArray = s.encodeToByteArray()

    // Helper to log and assert
    private fun assertUtf8Bytes(expected: ByteArray, actual: ByteArray, label: String) {
        logger.debug { "$label expected: ${expected.joinToString { "%02X" }}"}
        logger.debug { "$label actual:   ${actual.joinToString { "%02X" }}"}
        assertContentEquals(expected, actual, "$label bytes mismatch")
    }


    @Test
    fun testAsciiOnlyDecoding() {
        val s = "$ascii"
        val bytes = utf8Bytes(s)
        assertEquals(s, utf8DecoderCombo.decode(ByteBuffer.wrap(bytes)).toString())
        assertUtf8Bytes(byteArrayOf(0x41), bytes, "ASCII")
    }

    @Test
    fun testAsciiAnd11Decoding() {
        val s = "$ascii$elevenBit"
        val bytes = utf8Bytes(s)
        assertEquals(s, utf8DecoderCombo.decode(ByteBuffer.wrap(bytes)).toString())
        assertUtf8Bytes(byteArrayOf(0x41, 0xC3.toByte(), 0xA9.toByte()), bytes, "ASCII+11")
    }

    @Test
    fun testAscii11And16Decoding() {
        val s = "$ascii$elevenBit$sixteenBit"
        val bytes = utf8Bytes(s)
        assertEquals(s, utf8DecoderCombo.decode(ByteBuffer.wrap(bytes)).toString())
        assertUtf8Bytes(
            byteArrayOf(
                0x41,
                0xC3.toByte(), 0xA9.toByte(),
                0xE6.toByte(), 0xBC.toByte(), 0xA2.toByte()
            ),
            bytes, "ASCII+11+16"
        )
    }

    @Test
    fun testAsciiAnd16Decoding() {
        val s = "$ascii$sixteenBit"
        val bytes = utf8Bytes(s)
        assertEquals(s, utf8DecoderCombo.decode(ByteBuffer.wrap(bytes)).toString())
        assertUtf8Bytes(
            byteArrayOf(
                0x41,
                0xE6.toByte(), 0xBC.toByte(), 0xA2.toByte()
            ),
            bytes, "ASCII+16"
        )
    }

    @Test
    fun testAscii11_16_21Decoding() {
        val s = "$ascii$elevenBit$sixteenBit$twentyOneBit"
        val bytes = utf8Bytes(s)
        assertEquals(s, utf8DecoderCombo.decode(ByteBuffer.wrap(bytes)).toString())
        assertUtf8Bytes(
            byteArrayOf(
                0x41,
                0xC3.toByte(), 0xA9.toByte(),
                0xE6.toByte(), 0xBC.toByte(), 0xA2.toByte(),
                0xF0.toByte(), 0x9F.toByte(), 0x98.toByte(), 0x80.toByte()
            ),
            bytes, "ASCII+11+16+21"
        )
    }

    @Test
    fun testAscii16_21Decoding() {
        val s = "$ascii$sixteenBit$twentyOneBit"
        val bytes = utf8Bytes(s)
        assertEquals(s, utf8DecoderCombo.decode(ByteBuffer.wrap(bytes)).toString())
        assertUtf8Bytes(
            byteArrayOf(
                0x41,
                0xE6.toByte(), 0xBC.toByte(), 0xA2.toByte(),
                0xF0.toByte(), 0x9F.toByte(), 0x98.toByte(), 0x80.toByte()
            ),
            bytes, "ASCII+16+21"
        )
    }

    @Test
    fun testAscii21Decoding() {
        val s = "$ascii$twentyOneBit"
        val bytes = utf8Bytes(s)
        assertEquals(s, utf8DecoderCombo.decode(ByteBuffer.wrap(bytes)).toString())
        assertUtf8Bytes(
            byteArrayOf(
                0x41,
                0xF0.toByte(), 0x9F.toByte(), 0x98.toByte(), 0x80.toByte()
            ),
            bytes, "ASCII+21"
        )
    }

    @Test
    fun test11OnlyDecoding() {
        val s = "$elevenBit"
        val bytes = utf8Bytes(s)
        assertEquals(s, utf8DecoderCombo.decode(ByteBuffer.wrap(bytes)).toString())
        assertUtf8Bytes(byteArrayOf(0xC3.toByte(), 0xA9.toByte()), bytes, "11 only")
    }

    @Test
    fun test11_16Decoding() {
        val s = "$elevenBit$sixteenBit"
        val bytes = utf8Bytes(s)
        assertEquals(s, utf8DecoderCombo.decode(ByteBuffer.wrap(bytes)).toString())
        assertUtf8Bytes(
            byteArrayOf(
                0xC3.toByte(), 0xA9.toByte(),
                0xE6.toByte(), 0xBC.toByte(), 0xA2.toByte()
            ),
            bytes, "11+16"
        )
    }

    @Test
    fun test11_21Decoding() {
        val s = "$elevenBit$twentyOneBit"
        val bytes = utf8Bytes(s)
        assertEquals(s, utf8DecoderCombo.decode(ByteBuffer.wrap(bytes)).toString())
        assertUtf8Bytes(
            byteArrayOf(
                0xC3.toByte(), 0xA9.toByte(),
                0xF0.toByte(), 0x9F.toByte(), 0x98.toByte(), 0x80.toByte()
            ),
            bytes, "11+21"
        )
    }

    @Test
    fun test11_16_21Decoding() {
        val s = "$elevenBit$sixteenBit$twentyOneBit"
        val bytes = utf8Bytes(s)
        assertEquals(s, utf8DecoderCombo.decode(ByteBuffer.wrap(bytes)).toString())
        assertUtf8Bytes(
            byteArrayOf(
                0xC3.toByte(), 0xA9.toByte(),
                0xE6.toByte(), 0xBC.toByte(), 0xA2.toByte(),
                0xF0.toByte(), 0x9F.toByte(), 0x98.toByte(), 0x80.toByte()
            ),
            bytes, "11+16+21"
        )
    }

    @Test
    fun test16OnlyDecoding() {
        val s = "$sixteenBit"
        val bytes = utf8Bytes(s)
        assertEquals(s, utf8DecoderCombo.decode(ByteBuffer.wrap(bytes)).toString())
        assertUtf8Bytes(byteArrayOf(0xE6.toByte(), 0xBC.toByte(), 0xA2.toByte()), bytes, "16 only")
    }

    @Test
    fun test16_21Decoding() {
        val s = "$sixteenBit$twentyOneBit"
        val bytes = utf8Bytes(s)
        assertEquals(s, utf8DecoderCombo.decode(ByteBuffer.wrap(bytes)).toString())
        assertUtf8Bytes(
            byteArrayOf(
                0xE6.toByte(), 0xBC.toByte(), 0xA2.toByte(),
                0xF0.toByte(), 0x9F.toByte(), 0x98.toByte(), 0x80.toByte()
            ),
            bytes, "16+21"
        )
    }

    @Test
    fun test21OnlyDecoding() {
        val s = twentyOneBit
        val bytes = utf8Bytes(s)
        assertEquals(s, utf8DecoderCombo.decode(ByteBuffer.wrap(bytes)).toString())
        assertUtf8Bytes(
            byteArrayOf(
                0xF0.toByte(), 0x9F.toByte(), 0x98.toByte(), 0x80.toByte()
            ),
            bytes, "21 only"
        )
    }

    // --- UTF-8 combinations encoding tests ---

    private val utf8EncoderCombo = UTF_8().newEncoder()

    @Test
    fun testAsciiOnlyEncoding() {
        val s = "$ascii"
        val bytes = ByteArray(10)
        val bb = ByteBuffer.wrap(bytes)
        utf8EncoderCombo.reset()
        utf8EncoderCombo.encode(CharBuffer.wrap(s), bb, true)
        bb.flip()
        val actual = ByteArray(bb.remaining())
        bb.get(actual)
        assertUtf8Bytes(byteArrayOf(0x41), actual, "ASCII encoding")
    }

    @Test
    fun testAsciiAnd11Encoding() {
        val s = "$ascii$elevenBit"
        val bytes = ByteArray(10)
        val bb = ByteBuffer.wrap(bytes)
        utf8EncoderCombo.reset()
        utf8EncoderCombo.encode(CharBuffer.wrap(s), bb, true)
        bb.flip()
        val actual = ByteArray(bb.remaining())
        bb.get(actual)
        assertUtf8Bytes(byteArrayOf(0x41, 0xC3.toByte(), 0xA9.toByte()), actual, "ASCII+11 encoding")
    }

    @Test
    fun testAscii11And16Encoding() {
        val s = "$ascii$elevenBit$sixteenBit"
        val bytes = ByteArray(20)
        val bb = ByteBuffer.wrap(bytes)
        utf8EncoderCombo.reset()
        utf8EncoderCombo.encode(CharBuffer.wrap(s), bb, true)
        bb.flip()
        val actual = ByteArray(bb.remaining())
        bb.get(actual)
        assertUtf8Bytes(
            byteArrayOf(
                0x41,
                0xC3.toByte(), 0xA9.toByte(),
                0xE6.toByte(), 0xBC.toByte(), 0xA2.toByte()
            ),
            actual, "ASCII+11+16 encoding"
        )
    }

    @Test
    fun testAsciiAnd16Encoding() {
        val s = "$ascii$sixteenBit"
        val bytes = ByteArray(10)
        val bb = ByteBuffer.wrap(bytes)
        utf8EncoderCombo.reset()
        utf8EncoderCombo.encode(CharBuffer.wrap(s), bb, true)
        bb.flip()
        val actual = ByteArray(bb.remaining())
        bb.get(actual)
        assertUtf8Bytes(
            byteArrayOf(
                0x41,
                0xE6.toByte(), 0xBC.toByte(), 0xA2.toByte()
            ),
            actual, "ASCII+16 encoding"
        )
    }

    @Test
    fun testAscii11_16_21Encoding() {
        val s = "$ascii$elevenBit$sixteenBit$twentyOneBit"
        val bytes = ByteArray(20)
        val bb = ByteBuffer.wrap(bytes)
        utf8EncoderCombo.reset()
        utf8EncoderCombo.encode(CharBuffer.wrap(s), bb, true)
        bb.flip()
        val actual = ByteArray(bb.remaining())
        bb.get(actual)
        assertUtf8Bytes(
            byteArrayOf(
                0x41,
                0xC3.toByte(), 0xA9.toByte(),
                0xE6.toByte(), 0xBC.toByte(), 0xA2.toByte(),
                0xF0.toByte(), 0x9F.toByte(), 0x98.toByte(), 0x80.toByte()
            ),
            actual, "ASCII+11+16+21 encoding"
        )
    }

    @Test
    fun testAscii16_21Encoding() {
        val s = "$ascii$sixteenBit$twentyOneBit"
        val bytes = ByteArray(20)
        val bb = ByteBuffer.wrap(bytes)
        utf8EncoderCombo.reset()
        utf8EncoderCombo.encode(CharBuffer.wrap(s), bb, true)
        bb.flip()
        val actual = ByteArray(bb.remaining())
        bb.get(actual)
        assertUtf8Bytes(
            byteArrayOf(
                0x41,
                0xE6.toByte(), 0xBC.toByte(), 0xA2.toByte(),
                0xF0.toByte(), 0x9F.toByte(), 0x98.toByte(), 0x80.toByte()
            ),
            actual, "ASCII+16+21 encoding"
        )
    }

    @Test
    fun testAscii21Encoding() {
        val s = "$ascii$twentyOneBit"
        val bytes = ByteArray(10)
        val bb = ByteBuffer.wrap(bytes)
        utf8EncoderCombo.reset()
        utf8EncoderCombo.encode(CharBuffer.wrap(s), bb, true)
        bb.flip()
        val actual = ByteArray(bb.remaining())
        bb.get(actual)
        assertUtf8Bytes(
            byteArrayOf(
                0x41,
                0xF0.toByte(), 0x9F.toByte(), 0x98.toByte(), 0x80.toByte()
            ),
            actual, "ASCII+21 encoding"
        )
    }

    @Test
    fun test11OnlyEncoding() {
        val s = "$elevenBit"
        val bytes = ByteArray(10)
        val bb = ByteBuffer.wrap(bytes)
        utf8EncoderCombo.reset()
        utf8EncoderCombo.encode(CharBuffer.wrap(s), bb, true)
        bb.flip()
        val actual = ByteArray(bb.remaining())
        bb.get(actual)
        assertUtf8Bytes(byteArrayOf(0xC3.toByte(), 0xA9.toByte()), actual, "11 only encoding")
    }

    @Test
    fun test11_16Encoding() {
        val s = "$elevenBit$sixteenBit"
        val bytes = ByteArray(10)
        val bb = ByteBuffer.wrap(bytes)
        utf8EncoderCombo.reset()
        utf8EncoderCombo.encode(CharBuffer.wrap(s), bb, true)
        bb.flip()
        val actual = ByteArray(bb.remaining())
        bb.get(actual)
        assertUtf8Bytes(
            byteArrayOf(
                0xC3.toByte(), 0xA9.toByte(),
                0xE6.toByte(), 0xBC.toByte(), 0xA2.toByte()
            ),
            actual, "11+16 encoding"
        )
    }

    @Test
    fun test11_21Encoding() {
        val s = "$elevenBit$twentyOneBit"
        val bytes = ByteArray(10)
        val bb = ByteBuffer.wrap(bytes)
        utf8EncoderCombo.reset()
        utf8EncoderCombo.encode(CharBuffer.wrap(s), bb, true)
        bb.flip()
        val actual = ByteArray(bb.remaining())
        bb.get(actual)
        assertUtf8Bytes(
            byteArrayOf(
                0xC3.toByte(), 0xA9.toByte(),
                0xF0.toByte(), 0x9F.toByte(), 0x98.toByte(), 0x80.toByte()
            ),
            actual, "11+21 encoding"
        )
    }

    @Test
    fun test11_16_21Encoding() {
        val s = "$elevenBit$sixteenBit$twentyOneBit"
        val bytes = ByteArray(20)
        val bb = ByteBuffer.wrap(bytes)
        utf8EncoderCombo.reset()
        utf8EncoderCombo.encode(CharBuffer.wrap(s), bb, true)
        bb.flip()
        val actual = ByteArray(bb.remaining())
        bb.get(actual)
        assertUtf8Bytes(
            byteArrayOf(
                0xC3.toByte(), 0xA9.toByte(),
                0xE6.toByte(), 0xBC.toByte(), 0xA2.toByte(),
                0xF0.toByte(), 0x9F.toByte(), 0x98.toByte(), 0x80.toByte()
            ),
            actual, "11+16+21 encoding"
        )
    }

    @Test
    fun test16OnlyEncoding() {
        val s = "$sixteenBit"
        val bytes = ByteArray(10)
        val bb = ByteBuffer.wrap(bytes)
        utf8EncoderCombo.reset()
        utf8EncoderCombo.encode(CharBuffer.wrap(s), bb, true)
        bb.flip()
        val actual = ByteArray(bb.remaining())
        bb.get(actual)
        assertUtf8Bytes(byteArrayOf(0xE6.toByte(), 0xBC.toByte(), 0xA2.toByte()), actual, "16 only encoding")
    }

    @Test
    fun test16_21Encoding() {
        val s = "$sixteenBit$twentyOneBit"
        val bytes = ByteArray(10)
        val bb = ByteBuffer.wrap(bytes)
        utf8EncoderCombo.reset()
        utf8EncoderCombo.encode(CharBuffer.wrap(s), bb, true)
        bb.flip()
        val actual = ByteArray(bb.remaining())
        bb.get(actual)
        assertUtf8Bytes(
            byteArrayOf(
                0xE6.toByte(), 0xBC.toByte(), 0xA2.toByte(),
                0xF0.toByte(), 0x9F.toByte(), 0x98.toByte(), 0x80.toByte()
            ),
            actual, "16+21 encoding"
        )
    }

    @Test
    fun test21OnlyEncoding() {
        val s = twentyOneBit
        val bytes = ByteArray(10)
        val bb = ByteBuffer.wrap(bytes)
        utf8EncoderCombo.reset()
        utf8EncoderCombo.encode(CharBuffer.wrap(s), bb, true)
        bb.flip()
        val actual = ByteArray(bb.remaining())
        bb.get(actual)
        assertUtf8Bytes(
            byteArrayOf(
                0xF0.toByte(), 0x9F.toByte(), 0x98.toByte(), 0x80.toByte()
            ),
            actual, "21 only encoding"
        )
    }
}
