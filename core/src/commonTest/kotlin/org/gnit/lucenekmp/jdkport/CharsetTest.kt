package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class CharsetTest {

    @Test
    fun testCharsetName() {
        assertEquals("UTF-8", Charset.UTF_8.name())
        assertEquals("ISO-8859-1", Charset.ISO_8859_1.name())
    }

    @Test
    fun testCharsetToString() {
        assertEquals("UTF-8", Charset.UTF_8.toString())
        assertEquals("ISO-8859-1", Charset.ISO_8859_1.toString())
    }

    @Test
    fun testContains() {
        assertTrue(Charset.UTF_8.contains(Charset.UTF_8))
        assertTrue(Charset.ISO_8859_1.contains(Charset.ISO_8859_1))
        assertFalse(Charset.ISO_8859_1.contains(Charset.UTF_8))
    }

    @Test
    fun testUTF8EncodeDecode() {
        val original = "hello Привет 你好"
        val encoded = Charset.UTF_8.encode(original)
        val decoded = Charset.UTF_8.decode(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun testLatin1EncodeDecode() {
        val original = "hello"
        val encoded = Charset.ISO_8859_1.encode(original)
        val decoded = Charset.ISO_8859_1.decode(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun testLatin1EncodeDecodeWithExtendedAscii() {
        // chars with codes 128–255 as 1-byte Latin1
        val str = (128..255).joinToString("") { it.toChar().toString() }
        val encoded = Charset.ISO_8859_1.encode(str)
        val decoded = Charset.ISO_8859_1.decode(encoded)
        assertEquals(str, decoded)
    }

    @Test
    fun testDefaultCharset() {
        assertEquals(Charset.UTF_8, Charset.defaultCharset())
    }
}