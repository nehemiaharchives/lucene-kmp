package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TimeoutExceptionTest {

    @Test
    fun testDefaultConstructor() {
        val exception = TimeoutException()

        assertNull(exception.message)
    }

    @Test
    fun testMessageConstructor() {
        val exception = TimeoutException("timed out")

        assertEquals("timed out", exception.message)
    }
}
