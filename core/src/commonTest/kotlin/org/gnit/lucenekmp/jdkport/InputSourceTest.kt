package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse


class InputSourceTest {

    @Test
    fun testDefaultConstructor() {
        val source = InputSource()
        assertNull(source.publicId)
        assertNull(source.systemId)
        assertNull(source.byteStream)
        assertNull(source.encoding)
        assertNull(source.getCharacterStream())
    }

    @Test
    fun testConstructorWithSystemId() {
        val source = InputSource("http://example.com/test.xml")
        assertEquals("http://example.com/test.xml", source.systemId)
        assertNull(source.publicId)
        assertNull(source.byteStream)
        assertNull(source.getCharacterStream())
    }

    @Test
    fun testConstructorWithNullSystemId() {
        val source = InputSource(null as String?)
        assertNull(source.systemId)
    }

    @Test
    fun testPublicId() {
        val source = InputSource()
        source.publicId = "-//W3C//DTD XHTML 1.0//EN"
        assertEquals("-//W3C//DTD XHTML 1.0//EN", source.publicId)
    }

    @Test
    fun testSystemId() {
        val source = InputSource()
        source.systemId = "http://example.com/doc.xml"
        assertEquals("http://example.com/doc.xml", source.systemId)
    }

    @Test
    fun testEncoding() {
        val source = InputSource()
        source.encoding = "UTF-8"
        assertEquals("UTF-8", source.encoding)
    }

    @Test
    fun testSetAndGetCharacterStream() {
        val source = InputSource()
        val reader = StringReader("hello")
        source.setCharacterStream(reader)
        assertEquals(reader, source.getCharacterStream())
    }

    @Test
    fun testConstructorWithCharacterStream() {
        val reader = StringReader("<root/>")
        val source = InputSource(reader)
        assertEquals(reader, source.getCharacterStream())
    }

    @Test
    fun testIsEmptyWhenAllNull() {
        val source = InputSource()
        assertTrue(source.isEmpty)
    }

    @Test
    fun testIsEmptyWhenSystemIdSet() {
        val source = InputSource("http://example.com")
        assertFalse(source.isEmpty)
    }

    @Test
    fun testIsEmptyWhenPublicIdSet() {
        val source = InputSource()
        source.publicId = "some-public-id"
        assertFalse(source.isEmpty)
    }
}