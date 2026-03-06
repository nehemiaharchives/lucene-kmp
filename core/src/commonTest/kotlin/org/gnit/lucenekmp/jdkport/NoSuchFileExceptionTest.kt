package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NoSuchFileExceptionTest {

    @Test
    fun testSingleFileConstructor() {
        val exception = NoSuchFileException("missing.txt")

        assertEquals("missing.txt", exception.file)
        assertNull(exception.otherFile)
        assertNull(exception.reason)
        assertEquals("missing.txt", exception.message)
    }

    @Test
    fun testFileOtherAndReasonConstructor() {
        val exception = NoSuchFileException("missing.txt", "other.txt", "not found")

        assertEquals("missing.txt", exception.file)
        assertEquals("other.txt", exception.otherFile)
        assertEquals("not found", exception.reason)
        assertEquals("missing.txt -> other.txt: not found", exception.message)
    }
}
