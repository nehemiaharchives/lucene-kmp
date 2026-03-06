package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNull

class ClosedByInterruptExceptionTest {

    @Test
    fun testInheritanceAndDefaultMessage() {
        val exception = ClosedByInterruptException()

        assertIs<AsynchronousCloseException>(exception)
        assertIs<ClosedChannelException>(exception)
        assertNull(exception.message)
    }
}
