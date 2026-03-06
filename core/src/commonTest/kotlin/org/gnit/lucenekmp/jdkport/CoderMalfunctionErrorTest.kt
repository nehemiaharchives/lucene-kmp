package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class CoderMalfunctionErrorTest {

    @Test
    fun testCauseIsRetained() {
        val cause = IllegalStateException("boom")
        val error = CoderMalfunctionError(cause)

        assertSame(cause, error.cause)
        assertEquals(cause.toString(), error.message)
    }
}
