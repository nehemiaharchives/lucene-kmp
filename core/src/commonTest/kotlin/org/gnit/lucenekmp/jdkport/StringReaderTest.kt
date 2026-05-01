package org.gnit.lucenekmp.jdkport

import kotlin.test.*
import okio.IOException

class StringReaderTest {

    @Test
    fun testReadSingleChar() {
        val reader = StringReader("abc")
        assertEquals('a'.code, reader.read())
        assertEquals('b'.code, reader.read())
        assertEquals('c'.code, reader.read())
        assertEquals(-1, reader.read())
    }

    @Test
    fun testReadAfterCloseThrows() {
        val reader = StringReader("abc")
        reader.close()
        assertFailsWith<IOException> { reader.read() }
    }
}
