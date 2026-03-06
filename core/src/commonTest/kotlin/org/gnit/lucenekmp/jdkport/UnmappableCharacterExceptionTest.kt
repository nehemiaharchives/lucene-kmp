package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals

class UnmappableCharacterExceptionTest {

    @Test
    fun testInputLengthAndMessage() {
        val exception = UnmappableCharacterException(9)

        assertEquals(9, exception.inputLength)
        assertEquals("Input length = 9", exception.message)
    }
}
