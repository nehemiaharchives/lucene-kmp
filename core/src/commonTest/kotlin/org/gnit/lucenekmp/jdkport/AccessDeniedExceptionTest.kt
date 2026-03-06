package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AccessDeniedExceptionTest {

    @Test
    fun testSingleFileConstructor() {
        val exception = AccessDeniedException("denied.txt")

        assertEquals("denied.txt", exception.file)
        assertNull(exception.otherFile)
        assertNull(exception.reason)
        assertEquals("denied.txt", exception.message)
    }

    @Test
    fun testFileOtherAndReasonConstructor() {
        val exception = AccessDeniedException("source.txt", "target.txt", "permission denied")

        assertEquals("source.txt", exception.file)
        assertEquals("target.txt", exception.otherFile)
        assertEquals("permission denied", exception.reason)
        assertEquals("source.txt -> target.txt: permission denied", exception.message)
    }
}
