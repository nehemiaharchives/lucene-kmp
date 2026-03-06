package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals

class AtomicMoveNotSupportedExceptionTest {

    @Test
    fun testConstructorPopulatesFileFields() {
        val exception = AtomicMoveNotSupportedException("from.txt", "to.txt", "atomic move unsupported")

        assertEquals("from.txt", exception.file)
        assertEquals("to.txt", exception.otherFile)
        assertEquals("atomic move unsupported", exception.reason)
        assertEquals("from.txt -> to.txt: atomic move unsupported", exception.message)
    }
}
