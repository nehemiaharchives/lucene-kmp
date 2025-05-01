package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CharsetDecoderTest {

    private val utf8Decoder = object : CharsetDecoder(Charset.UTF_8, 1.0f, 1.0f) {
        override fun decodeLoop(`in`: ByteBuffer, out: CharBuffer): CoderResult? {
            while (`in`.hasRemaining()) {
                val byte = `in`.get()
                if (byte.toInt() and 0x80 == 0) {
                    out.put(byte.toChar())
                } else {
                    return CoderResult.unmappableForLength(1)
                }
            }
            return CoderResult.UNDERFLOW
        }
    }

    @Test
    fun testDecode() {
        val input = ByteBuffer.wrap(byteArrayOf(0x48, 0x65, 0x6C, 0x6C, 0x6F))
        val output = utf8Decoder.decode(input)
        assertEquals("Hello", output.toString())
    }

    @Test
    fun testOnMalformedInput() {
        utf8Decoder.onMalformedInput(CodingErrorAction.REPLACE)
        val input = ByteBuffer.wrap(byteArrayOf(0xC3, 0x28))
        val output = CharBuffer.allocate(10)
        utf8Decoder.decode(input, output, true)
        utf8Decoder.flush(output)
        output.flip()
        assertEquals("\uFFFD(", output.toString())
    }

    @Test
    fun testOnUnmappableCharacter() {
        utf8Decoder.onUnmappableCharacter(CodingErrorAction.REPLACE)
        val input = ByteBuffer.wrap(byteArrayOf(0x80.toByte()))
        val output = CharBuffer.allocate(10)
        utf8Decoder.decode(input, output, true)
        utf8Decoder.flush(output)
        output.flip()
        assertEquals("\uFFFD", output.toString())
    }

    @Test
    fun testReplaceWith() {
        utf8Decoder.replaceWith("?")
        val input = ByteBuffer.wrap(byteArrayOf(0x80.toByte()))
        val output = CharBuffer.allocate(10)
        utf8Decoder.decode(input, output, true)
        utf8Decoder.flush(output)
        output.flip()
        assertEquals("?", output.toString())
    }

    @Test
    fun testReset() {
        val input = ByteBuffer.wrap(byteArrayOf(0x48, 0x65, 0x6C, 0x6C, 0x6F))
        utf8Decoder.decode(input)
        utf8Decoder.reset()
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
    fun testImplReplaceWith() {
        utf8Decoder.implReplaceWith("?")
        val input = ByteBuffer.wrap(byteArrayOf(0x80.toByte()))
        val output = CharBuffer.allocate(10)
        utf8Decoder.decode(input, output, true)
        utf8Decoder.flush(output)
        output.flip()
        assertEquals("?", output.toString())
    }

    @Test
    fun testImplOnMalformedInput() {
        utf8Decoder.implOnMalformedInput(CodingErrorAction.REPLACE)
        val input = ByteBuffer.wrap(byteArrayOf(0xC3, 0x28))
        val output = CharBuffer.allocate(10)
        utf8Decoder.decode(input, output, true)
        utf8Decoder.flush(output)
        output.flip()
        assertEquals("\uFFFD(", output.toString())
    }

    @Test
    fun testImplOnUnmappableCharacter() {
        utf8Decoder.implOnUnmappableCharacter(CodingErrorAction.REPLACE)
        val input = ByteBuffer.wrap(byteArrayOf(0x80.toByte()))
        val output = CharBuffer.allocate(10)
        utf8Decoder.decode(input, output, true)
        utf8Decoder.flush(output)
        output.flip()
        assertEquals("\uFFFD", output.toString())
    }

    @Test
    fun testImplReset() {
        utf8Decoder.implReset()
        val input = ByteBuffer.wrap(byteArrayOf(0x48, 0x65, 0x6C, 0x6C, 0x6F))
        val output = utf8Decoder.decode(input)
        assertEquals("Hello", output.toString())
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

    @Test
    fun testCompareTo() {
        val otherDecoder = object : CharsetDecoder(Charset.UTF_8, 1.0f, 1.0f) {
            override fun decodeLoop(`in`: ByteBuffer, out: CharBuffer): CoderResult? {
                return CoderResult.UNDERFLOW
            }
        }
        assertEquals(0, utf8Decoder.compareTo(otherDecoder))
    }

    @Test
    fun testEquals() {
        val otherDecoder = object : CharsetDecoder(Charset.UTF_8, 1.0f, 1.0f) {
            override fun decodeLoop(`in`: ByteBuffer, out: CharBuffer): CoderResult? {
                return CoderResult.UNDERFLOW
            }
        }
        assertTrue(utf8Decoder == otherDecoder)
    }

    @Test
    fun testHashCode() {
        val otherDecoder = object : CharsetDecoder(Charset.UTF_8, 1.0f, 1.0f) {
            override fun decodeLoop(`in`: ByteBuffer, out: CharBuffer): CoderResult? {
                return CoderResult.UNDERFLOW
            }
        }
        assertEquals(utf8Decoder.hashCode(), otherDecoder.hashCode())
    }

    @Test
    fun testToString() {
        assertEquals("UTF-8", utf8Decoder.toString())
    }
}
