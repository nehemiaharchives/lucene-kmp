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
    fun testDefaultCharset() {
        assertEquals(Charset.UTF_8, Charset.defaultCharset())
    }
}