package org.gnit.lucenekmp.jdkport

import kotlin.test.*

class ISO_8859_1Test {

    @Test
    fun testContains() {
        val iso = StandardCharsets.ISO_8859_1
        assertTrue { iso.contains(iso) }
        assertFalse { iso.contains(StandardCharsets.UTF_8) }
    }

    @Test
    fun testDecode() {
        val iso = StandardCharsets.ISO_8859_1
        val bytes = byteArrayOf(0x48, 0x65, 0x6C, 0x6C, 0x6F)
        val decoded = iso.decode(ByteBuffer.wrap(bytes)).toString()
        assertEquals("Hello", decoded)
    }

    @Test
    fun testEncode() {
        val iso = StandardCharsets.ISO_8859_1
        val encoded = iso.encode(CharBuffer.wrap("Hello")).array()
        assertContentEquals(byteArrayOf(0x48, 0x65, 0x6C, 0x6C, 0x6F), encoded)
    }
}
