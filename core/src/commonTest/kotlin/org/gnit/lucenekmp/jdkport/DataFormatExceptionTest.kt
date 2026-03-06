package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DataFormatExceptionTest {

    @Test
    fun testDefaultConstructor() {
        val exception = DataFormatException()

        assertNull(exception.message)
    }

    @Test
    fun testMessageConstructor() {
        val exception = DataFormatException("bad zlib data")

        assertEquals("bad zlib data", exception.message)
    }
}
