package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InterruptedExceptionTest {

    @Test
    fun testDefaultConstructor() {
        val exception = InterruptedException()

        assertNull(exception.message)
    }

    @Test
    fun testMessageConstructor() {
        val exception = InterruptedException("interrupted")

        assertEquals("interrupted", exception.message)
    }
}
