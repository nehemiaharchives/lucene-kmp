package org.gnit.lucenekmp.internal.tests

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals

class TestTestSecrets : LuceneTestCase() {

    @Test
    fun testCallerOfGetter() {
        val expected = expectThrows(UnsupportedOperationException::class) { illegalCaller() }
        assertEquals(
            "Lucene TestSecrets can only be used by the test-framework.",
            expected.message
        )
    }

    @Test
    fun testCannotSet() {
        expectThrows(AssertionError::class) { TestSecrets.setIndexWriterAccess(null) }
        expectThrows(AssertionError::class) { TestSecrets.setConcurrentMergeSchedulerAccess(null) }
        expectThrows(AssertionError::class) { TestSecrets.setIndexPackageAccess(null) }
        expectThrows(AssertionError::class) { TestSecrets.setSegmentReaderAccess(null) }
    }

    companion object {
        private fun illegalCaller() {
            TestSecrets.getIndexWriterAccess()
        }
    }
}
