package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class ParseExceptionTest {

    @Test
    fun testMessageAndErrorOffset() {
        val exception = ParseException("bad input", 4)

        assertEquals("bad input", exception.message)
        assertEquals(4, exception.errorOffset)
        assertNull(exception.cause)
    }

    @Test
    fun testInitCauseStoresCauseAndGuardsAgainstInvalidReuse() {
        val exception = ParseException("bad input", 4)
        val cause = IllegalArgumentException("root cause")

        assertSame(exception, exception.initCause(cause))
        assertSame(cause, exception.cause)

        assertFailsWith<IllegalStateException> {
            exception.initCause(IllegalStateException("another cause"))
        }
    }

    @Test
    fun testInitCauseRejectsSelfCausation() {
        val exception = ParseException("bad input", 4)

        assertFailsWith<IllegalArgumentException> {
            exception.initCause(exception)
        }
    }
}
