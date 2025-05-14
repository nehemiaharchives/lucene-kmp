package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals

class StandardCharsetsTest {

    @Test
    fun testUTF8Charset() {
        val charset = StandardCharsets.UTF_8
        assertEquals("UTF-8", charset.name())
        assertEquals(setOf("UTF8", "unicode-1-1-utf-8"), StandardCharsets.aliases_UTF_8())
    }

    @Test
    fun testUTF8CharsetDecode() {
        val charset = StandardCharsets.UTF_8
        val input = byteArrayOf(0x48, 0x65, 0x6C, 0x6C, 0x6F) // "Hello" in UTF-8
        val output = charset.decode(input)
        assertEquals("Hello", output)
    }

    @Test
    fun testUTF8CharsetEncode() {
        val charset = StandardCharsets.UTF_8
        val input = "Hello"
        val output = charset.encode(input)
        assertEquals(byteArrayOf(0x48, 0x65, 0x6C, 0x6C, 0x6F).toList(), output.toList())
    }

    @Test
    fun testLATIN1Charset() {
        val charset = StandardCharsets.ISO_8859_1
        assertEquals("ISO-8859-1", charset.name())
    }

    @Test
    fun testLATIN1CharsetDecode() {
        val charset = StandardCharsets.ISO_8859_1
        val input = byteArrayOf(0x48, 0x65, 0x6C, 0x6C, 0x6F) // "Hello" in ISO-8859-1
        val output = charset.decode(input)
        assertEquals("Hello", output)
    }

    @Test
    fun testLATIN1CharsetEncode() {
        val charset = StandardCharsets.ISO_8859_1
        val input = "Hello"
        val output = charset.encode(input)
        assertEquals(byteArrayOf(0x48, 0x65, 0x6C, 0x6C, 0x6F).toList(), output.toList())
    }

    @Test
    fun testDefaultCharset() {
        val charset = Charset.defaultCharset()
        assertEquals(StandardCharsets.UTF_8, charset)
    }
}
