package org.gnit.lucenekmp.codecs.lucene101

import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals

class TestPostingsUtil : LuceneTestCase() {
    // checks for bug described in https://github.com/apache/lucene/issues/13373
    @Test
    fun testIntegerOverflow() {
        // Size that writes the first value as a regular vint
        val randomSize1 = random().nextInt(1, 3)
        // Size that writes the first value as a group vint
        val randomSize2 = random().nextInt(4, ForUtil.BLOCK_SIZE)
        doTestIntegerOverflow(randomSize1)
        doTestIntegerOverflow(randomSize2)
    }

    private fun doTestIntegerOverflow(size: Int) {
        val docDeltaBuffer = IntArray(size)
        val freqBuffer = IntArray(size)

        val delta = 1 shl 30
        docDeltaBuffer[0] = delta
        newDirectory().use { dir ->
            dir.createOutput("test", IOContext.DEFAULT).use { out ->
                // In old implementation, this would cause integer overflow exception.
                PostingsUtil.writeVIntBlock(out, docDeltaBuffer, freqBuffer, size, true)
            }
            val restoredDocs = IntArray(size)
            val restoredFreqs = IntArray(size)
            dir.openInput("test", IOContext.DEFAULT).use { input ->
                PostingsUtil.readVIntBlock(input, restoredDocs, restoredFreqs, size, true, true)
            }
            assertEquals(delta, restoredDocs[0])
        }
    }

    private fun newDirectory(): Directory {
        return ByteBuffersDirectory()
    }
}
