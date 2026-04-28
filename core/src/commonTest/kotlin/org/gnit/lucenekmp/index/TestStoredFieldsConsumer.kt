package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.jdkport.AtomicInteger
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.FlushInfo
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.StringHelper
import org.gnit.lucenekmp.util.Version
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.test.Test
import kotlin.test.assertEquals

class TestStoredFieldsConsumer : LuceneTestCase() {

    @Test
    @OptIn(ExperimentalAtomicApi::class)
    @Throws(IOException::class)
    fun testFinish() {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig()
        val si =
            SegmentInfo(
                dir,
                Version.LATEST,
                null,
                "_0",
                -1,
                isCompoundFile = false,
                hasBlocks = false,
                codec = iwc.codec,
                diagnostics = mutableMapOf(),
                id = StringHelper.randomId(),
                attributes = mutableMapOf(),
                indexSort = null
            )

        val startDocCounter: AtomicInteger = AtomicInteger(0)
        val finishDocCounter: AtomicInteger = AtomicInteger(0)
        val consumer: StoredFieldsConsumer =
            object : StoredFieldsConsumer(iwc.codec, dir, si) {
                @Throws(IOException::class)
                override fun startDocument(docID: Int) {
                    super.startDocument(docID)
                    startDocCounter.incrementAndFetch()
                }

                @Throws(IOException::class)
                override fun finishDocument() {
                    super.finishDocument()
                    finishDocCounter.incrementAndFetch()
                }
            }

        val numDocs = 3
        consumer.finish(numDocs)

        si.setMaxDoc(numDocs)
        val state =
            SegmentWriteState(
                null,
                dir,
                si,
                FieldInfos(emptyArray<FieldInfo>()),
                null,
                IOContext(FlushInfo(numDocs, 10))
            )
        consumer.flush(state, null)
        dir.close()

        assertEquals(numDocs.toLong(), startDocCounter.load().toLong())
        assertEquals(numDocs.toLong(), finishDocCounter.load().toLong())
    }
}
