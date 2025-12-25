package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StringWriterTest {

    @Test
    fun testWriteCharArray() {
        val writer = StringWriter()
        val chars = "hello".toCharArray()
        writer.write(chars, 1, 3)
        assertEquals("ell", writer.toString())
    }

    @Test
    fun testWriteStringAndAppend() {
        val writer = StringWriter()
        writer.write("abc")
        writer.append('d')
        writer.append("ef")
        writer.append("ghij", 1, 3)
        assertEquals("abcdef" + "hi", writer.toString())
    }

    @Test
    fun testWriteIndexBounds() {
        val writer = StringWriter()
        val chars = "abc".toCharArray()
        assertFailsWith<IndexOutOfBoundsException> { writer.write(chars, -1, 1) }
        assertFailsWith<IndexOutOfBoundsException> { writer.write(chars, 0, 4) }
        assertFailsWith<IndexOutOfBoundsException> { writer.write(chars, 3, 1) }
    }
}
