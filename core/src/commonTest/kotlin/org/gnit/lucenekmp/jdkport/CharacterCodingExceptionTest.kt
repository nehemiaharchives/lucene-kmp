package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertNull

class CharacterCodingExceptionTest {

    @Test
    fun testDefaultState() {
        val exception = CharacterCodingException()

        assertNull(exception.message)
        assertNull(exception.cause)
    }
}
