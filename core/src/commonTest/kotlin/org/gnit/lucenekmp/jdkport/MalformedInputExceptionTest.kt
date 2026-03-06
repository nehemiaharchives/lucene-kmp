package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals

class MalformedInputExceptionTest {

    @Test
    fun testInputLengthAndMessage() {
        val exception = MalformedInputException(7)

        assertEquals(7, exception.inputLength)
        assertEquals("Input length = 7", exception.message)
    }
}
