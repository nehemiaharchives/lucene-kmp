package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FileAlreadyExistsExceptionTest {

    @Test
    fun testSingleFileConstructor() {
        val exception = FileAlreadyExistsException("existing.txt")

        assertEquals("existing.txt", exception.file)
        assertNull(exception.otherFile)
        assertNull(exception.reason)
        assertEquals("existing.txt", exception.message)
    }

    @Test
    fun testFileOtherAndReasonConstructor() {
        val exception = FileAlreadyExistsException("existing.txt", "other.txt", "already exists")

        assertEquals("existing.txt", exception.file)
        assertEquals("other.txt", exception.otherFile)
        assertEquals("already exists", exception.reason)
        assertEquals("existing.txt -> other.txt: already exists", exception.message)
    }
}
