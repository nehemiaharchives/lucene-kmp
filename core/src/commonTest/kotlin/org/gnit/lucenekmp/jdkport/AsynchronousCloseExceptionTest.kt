package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNull

class AsynchronousCloseExceptionTest {

    @Test
    fun testInheritanceAndDefaultMessage() {
        val exception = AsynchronousCloseException()

        assertIs<ClosedChannelException>(exception)
        assertNull(exception.message)
    }
}
