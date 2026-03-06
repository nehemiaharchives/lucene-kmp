package org.gnit.lucenekmp.jdkport

import okio.IOException
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNull

class ClosedChannelExceptionTest {

    @Test
    fun testInheritanceAndDefaultMessage() {
        val exception = ClosedChannelException()

        assertIs<IOException>(exception)
        assertNull(exception.message)
    }
}
