package org.gnit.lucenekmp.jdkport

import okio.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class UncheckedIOExceptionTest {

    @Test
    fun testMessageAndCauseConstructor() {
        val cause = IOException("disk error")
        val exception = UncheckedIOException("unchecked", cause)

        assertEquals("unchecked", exception.message)
        assertSame(cause, exception.cause)
    }

    @Test
    fun testCauseOnlyConstructor() {
        val cause = IOException("disk error")
        val exception = UncheckedIOException(cause)

        assertEquals(cause.toString(), exception.message)
        assertSame(cause, exception.cause)
    }
}
