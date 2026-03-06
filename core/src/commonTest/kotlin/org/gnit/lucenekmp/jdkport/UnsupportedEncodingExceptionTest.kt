package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UnsupportedEncodingExceptionTest {

    @Test
    fun testDefaultConstructor() {
        val exception = UnsupportedEncodingException()

        assertNull(exception.message)
    }

    @Test
    fun testMessageConstructor() {
        val exception = UnsupportedEncodingException("X-FAKE-CHARSET")

        assertEquals("X-FAKE-CHARSET", exception.message)
    }
}
