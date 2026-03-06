package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class RejectedExecutionExceptionTest {

    @Test
    fun testDefaultConstructor() {
        val exception = RejectedExecutionException()

        assertNull(exception.message)
        assertNull(exception.cause)
    }

    @Test
    fun testMessageAndCauseConstructors() {
        val cause = IllegalStateException("queue full")

        val messageOnly = RejectedExecutionException("rejected")
        val messageAndCause = RejectedExecutionException("rejected", cause)
        val causeOnly = RejectedExecutionException(cause)

        assertEquals("rejected", messageOnly.message)
        assertEquals("rejected", messageAndCause.message)
        assertSame(cause, messageAndCause.cause)
        assertEquals(cause.toString(), causeOnly.message)
        assertSame(cause, causeOnly.cause)
    }
}
