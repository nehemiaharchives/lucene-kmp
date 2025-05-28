package org.gnit.lucenekmp.jdkport

import kotlin.test.*
import okio.IOException
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

class StringReaderTest {
    @Test
    fun testReadSingleChar() {
        val reader = StringReader("abc")
        assertEquals('a'.code, reader.read())
        assertEquals('b'.code, reader.read())
        assertEquals('c'.code, reader.read())
        assertEquals(-1, reader.read())
        logger.debug { "testReadSingleChar passed" }
    }

    @Test
    fun testReadAfterCloseThrows() {
        val reader = StringReader("abc")
        reader.close()
        assertFailsWith<IOException> { reader.read() }
        logger.debug { "testReadAfterCloseThrows passed" }
    }
}
