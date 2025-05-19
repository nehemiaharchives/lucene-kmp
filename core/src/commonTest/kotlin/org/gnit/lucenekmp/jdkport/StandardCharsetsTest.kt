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
    fun testLATIN1Charset() {
        val charset = StandardCharsets.ISO_8859_1
        assertEquals("ISO-8859-1", charset.name())
    }

    @Test
    fun testDefaultCharset() {
        val charset = Charset.defaultCharset()
        assertEquals(StandardCharsets.UTF_8, charset)
    }
}
