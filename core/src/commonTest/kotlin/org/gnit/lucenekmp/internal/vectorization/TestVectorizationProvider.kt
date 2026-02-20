package org.gnit.lucenekmp.internal.vectorization

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test

class TestVectorizationProvider : LuceneTestCase() {
    @Test
    fun testCallerOfGetter() {
        expectThrows(UnsupportedOperationException::class) { illegalCaller() }
    }

    companion object {
        private fun illegalCaller() {
            VectorizationProvider.getInstance()
        }
    }
}
