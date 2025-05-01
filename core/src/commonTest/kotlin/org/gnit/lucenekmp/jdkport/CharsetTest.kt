package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class CharsetTest {

    @Test
    fun testForName() {
        val charset = Charset.forName("UTF-8")
        assertEquals("UTF-8", charset.name())
    }

    @Test
    fun testAvailableCharsets() {
        val charsets = Charset.availableCharsets()
        assertTrue(charsets.containsKey("UTF-8"))
    }

    @Test
    fun testDefaultCharset() {
        val charset = Charset.defaultCharset()
        assertEquals("UTF-8", charset.name())
    }

    @Test
    fun testName() {
        val charset = Charset.forName("UTF-8")
        assertEquals("UTF-8", charset.name())
    }

    @Test
    fun testAliases() {
        val charset = Charset.forName("UTF-8")
        assertTrue(charset.aliases().contains("UTF8"))
    }

    @Test
    fun testDisplayName() {
        val charset = Charset.forName("UTF-8")
        assertEquals("UTF-8", charset.displayName())
    }

    @Test
    fun testCanEncode() {
        val charset = Charset.forName("UTF-8")
        assertTrue(charset.canEncode())
    }

    @Test
    fun testNewDecoder() {
        val charset = Charset.forName("UTF-8")
        val decoder = charset.newDecoder()
        assertEquals(charset, decoder.charset())
    }

    @Test
    fun testNewEncoder() {
        val charset = Charset.forName("UTF-8")
        val encoder = charset.newEncoder()
        assertEquals(charset, encoder.charset())
    }

    @Test
    fun testDecode() {
        val charset = Charset.forName("UTF-8")
        val bytes = byteArrayOf(72, 101, 108, 108, 111)
        val str = charset.decode(bytes)
        assertEquals("Hello", str)
    }

    @Test
    fun testEncode() {
        val charset = Charset.forName("UTF-8")
        val str = "Hello"
        val bytes = charset.encode(str)
        assertTrue(bytes.contentEquals(byteArrayOf(72, 101, 108, 108, 111)))
    }

    @Test
    fun testCompareTo() {
        val charset1 = Charset.forName("UTF-8")
        val charset2 = Charset.forName("UTF-8")
        assertEquals(0, charset1.compareTo(charset2))
    }

    @Test
    fun testEquals() {
        val charset1 = Charset.forName("UTF-8")
        val charset2 = Charset.forName("UTF-8")
        assertTrue(charset1 == charset2)
    }

    @Test
    fun testHashCode() {
        val charset = Charset.forName("UTF-8")
        assertEquals("UTF-8".lowercase().hashCode(), charset.hashCode())
    }

    @Test
    fun testToString() {
        val charset = Charset.forName("UTF-8")
        assertEquals("UTF-8", charset.toString())
    }
}
