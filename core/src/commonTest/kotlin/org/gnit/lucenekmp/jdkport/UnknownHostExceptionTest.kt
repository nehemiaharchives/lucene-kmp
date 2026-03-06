package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UnknownHostExceptionTest {

    @Test
    fun testDefaultConstructor() {
        val exception = UnknownHostException()

        assertNull(exception.message)
    }

    @Test
    fun testMessageConstructor() {
        val exception = UnknownHostException("example.invalid")

        assertEquals("example.invalid", exception.message)
    }
}
