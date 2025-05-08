package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class CharsetTest {

    @Test
    fun testCharsetName() {
        assertEquals("UTF-8", Charset.UTF_8.name())
        assertEquals("ISO-8859-1", Charset.LATIN1.name())
    }

    @Test
    fun testCharsetToString() {
        assertEquals("UTF-8", Charset.UTF_8.toString())
        assertEquals("ISO-8859-1", Charset.LATIN1.toString())
    }

    @Test
    fun testContains() {
        assertTrue(Charset.UTF_8.contains(Charset.UTF_8))
        assertTrue(Charset.LATIN1.contains(Charset.LATIN1))
        assertFalse(Charset.LATIN1.contains(Charset.UTF_8))
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
        val encoded = Charset.LATIN1.encode(original)
        val decoded = Charset.LATIN1.decode(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun testLatin1EncodeDecodeWithExtendedAscii() {
        // chars with codes 128–255 as 1-byte Latin1
        val str = (128..255).joinToString("") { it.toChar().toString() }
        val encoded = Charset.LATIN1.encode(str)
        val decoded = Charset.LATIN1.decode(encoded)
        assertEquals(str, decoded)
    }

    @Test
    fun testDefaultCharset() {
        assertEquals(Charset.UTF_8, Charset.defaultCharset())
    }
}