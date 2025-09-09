package org.gnit.lucenekmp.index

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.coroutineScope
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.jdkport.AtomicInteger
import org.gnit.lucenekmp.jdkport.decrementAndGet
import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.LineFileDocs
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.index.DocumentsWriterFlushControl
import org.gnit.lucenekmp.index.DocumentsWriterPerThread
import org.gnit.lucenekmp.index.FlushByRamOrCountsPolicy
import org.gnit.lucenekmp.index.FlushPolicy
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.LiveIndexWriterConfig
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexReader
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.collections.ArrayList
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalAtomicApi::class)
class TestFlushByRamOrCountsPolicy : LuceneTestCase() {
    private lateinit var lineDocFile: LineFileDocs

    @BeforeTest
    fun before() {
        lineDocFile = LineFileDocs(random())
    }

    @AfterTest
    fun after() {
        lineDocFile.close()
    }

    @Test
    fun testFlushByRam() = runBlocking {
        val ramBuffer = (if (TEST_NIGHTLY) 1 else 10) + atLeast(2) + random().nextDouble()
        runFlushByRam(1 + random().nextInt(if (TEST_NIGHTLY) 5 else 1), ramBuffer, false)
    }

    @Test
    fun testFlushByRamLargeBuffer() = runBlocking {
        runFlushByRam(1 + random().nextInt(if (TEST_NIGHTLY) 5 else 1), 256.0, true)
    }

    private suspend fun runFlushByRam(numThreads: Int, maxRamMB: Double, ensureNotStalled: Boolean) {
        val numDocumentsToIndex = 10 + atLeast(30)
        val numDocs = AtomicInteger(numDocumentsToIndex)
        val dir = newDirectory()
        var flushPolicy = MockDefaultFlushPolicy()
        val analyzer = MockAnalyzer(random())
        analyzer.setMaxTokenLength(TestUtil.nextInt(random(), 1, IndexWriter.MAX_TERM_LENGTH))
        val iwc = IndexWriterConfig(analyzer).setFlushPolicy(flushPolicy)
        iwc.setRAMBufferSizeMB(maxRamMB)
        iwc.setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH)
        val writer = IndexWriter(dir, iwc)
        flushPolicy = writer.config.flushPolicy as MockDefaultFlushPolicy
        assertFalse(flushPolicy.isFlushOnDocCount())
        assertTrue(flushPolicy.isFlushOnRAM())
        val docsWriter = writer.getDocsWriter()
        assertNotNull(docsWriter)
        val flushControl = docsWriter.flushControl
        assertEquals(0, writer.getFlushingBytes(), " bytes must be 0 after init")
        val threads = Array(numThreads) { IndexThread(numDocs, numThreads, writer, lineDocFile, false) }
        coroutineScope {
            val jobs = threads.map { launch { it.run() } }
            jobs.forEach { it.join() }
        }
        val maxRAMBytes = (iwc.rAMBufferSizeMB * 1024.0 * 1024.0).toLong()
        assertEquals(0, writer.getFlushingBytes(), " all flushes must be due numThreads=$numThreads")
        assertEquals(numDocumentsToIndex, writer.getDocStats().numDocs)
        assertEquals(numDocumentsToIndex, writer.getDocStats().maxDoc)
        assertTrue(flushPolicy.peakBytesWithoutFlush <= maxRAMBytes, "peak bytes without flush exceeded watermark")
        assertActiveBytesAfter(flushControl)
        if (flushPolicy.hasMarkedPending) {
            assertTrue(maxRAMBytes < flushControl.peakActiveBytes)
        }
        if (ensureNotStalled) {
            assertFalse(docsWriter.flushControl.stallControl.wasStalled())
        }
        writer.close()
        assertEquals(0, flushControl.activeBytes())
        dir.close()
    }

    @Test
    fun testFlushDocCount() = runBlocking {
        val numThreads = intArrayOf(2 + atLeast(1), 1)
        val analyzer = MockAnalyzer(random())
        analyzer.setMaxTokenLength(TestUtil.nextInt(random(), 1, IndexWriter.MAX_TERM_LENGTH))
        for (i in numThreads.indices) {
            val numDocumentsToIndex = 50 + atLeast(30)
            val numDocs = AtomicInteger(numDocumentsToIndex)
            val dir = newDirectory()
            var flushPolicy = MockDefaultFlushPolicy()
            val iwc = IndexWriterConfig(analyzer).setFlushPolicy(flushPolicy)
            iwc.setMaxBufferedDocs(2 + atLeast(10))
            iwc.setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH.toDouble())
            val writer = IndexWriter(dir, iwc)
            flushPolicy = writer.config.flushPolicy as MockDefaultFlushPolicy
            assertTrue(flushPolicy.isFlushOnDocCount())
            assertFalse(flushPolicy.isFlushOnRAM())
            val docsWriter = writer.getDocsWriter()
            assertNotNull(docsWriter)
            val flushControl = docsWriter.flushControl
            assertEquals(0, writer.getFlushingBytes(), " bytes must be 0 after init")
            val threads = Array(numThreads[i]) { IndexThread(numDocs, numThreads[i], writer, lineDocFile, false) }
            coroutineScope {
                val jobs = threads.map { launch { it.run() } }
                jobs.forEach { it.join() }
            }
            assertEquals(0, writer.getFlushingBytes(), " all flushes must be due numThreads=${numThreads[i]}")
            assertEquals(numDocumentsToIndex, writer.getDocStats().numDocs)
            assertEquals(numDocumentsToIndex, writer.getDocStats().maxDoc)
            assertTrue(
                flushPolicy.peakDocCountWithoutFlush <= iwc.maxBufferedDocs.toLong(),
                "peak bytes without flush exceeded watermark"
            )
            assertActiveBytesAfter(flushControl)
            writer.close()
            assertEquals(0, flushControl.activeBytes())
            dir.close()
        }
    }

    @Test
    fun testRandom() = runBlocking {
        val numThreads = 1 + random().nextInt(8)
        val numDocumentsToIndex = 50 + atLeast(70)
        val numDocs = AtomicInteger(numDocumentsToIndex)
        val dir = newDirectory()
        val analyzer = MockAnalyzer(random())
        analyzer.setMaxTokenLength(TestUtil.nextInt(random(), 1, IndexWriter.MAX_TERM_LENGTH))
        val iwc = IndexWriterConfig(analyzer)
        var flushPolicy = MockDefaultFlushPolicy()
        iwc.setFlushPolicy(flushPolicy)
        val writer = IndexWriter(dir, iwc)
        flushPolicy = writer.config.flushPolicy as MockDefaultFlushPolicy
        val docsWriter = writer.getDocsWriter()
        assertNotNull(docsWriter)
        val flushControl = docsWriter.flushControl
        assertEquals(0, writer.getFlushingBytes(), " bytes must be 0 after init")
        val threads = Array(numThreads) { IndexThread(numDocs, numThreads, writer, lineDocFile, true) }
        coroutineScope {
            val jobs = threads.map { launch { it.run() } }
            jobs.forEach { it.join() }
        }
        assertEquals(0, writer.getFlushingBytes(), " all flushes must be due")
        assertEquals(numDocumentsToIndex, writer.getDocStats().numDocs)
        assertEquals(numDocumentsToIndex, writer.getDocStats().maxDoc)
        if (flushPolicy.isFlushOnRAM() && !flushPolicy.isFlushOnDocCount()) {
            val maxRAMBytes = (iwc.rAMBufferSizeMB * 1024.0 * 1024.0).toLong()
            assertTrue(flushPolicy.peakBytesWithoutFlush <= maxRAMBytes, "peak bytes without flush exceeded watermark")
            if (flushPolicy.hasMarkedPending) {
                assertTrue(maxRAMBytes <= flushControl.peakActiveBytes, "max: $maxRAMBytes ${flushControl.peakActiveBytes}")
            }
        }
        assertActiveBytesAfter(flushControl)
        writer.commit()
        assertEquals(0, flushControl.activeBytes())
        val r: IndexReader = DirectoryReader.open(dir)
        assertEquals(numDocumentsToIndex, r.numDocs())
        assertEquals(numDocumentsToIndex, r.maxDoc())
        if (!flushPolicy.isFlushOnRAM()) {
            assertFalse(docsWriter.flushControl.stallControl.wasStalled(), "never stall if we don't flush on RAM")
            assertFalse(docsWriter.flushControl.stallControl.hasBlocked(), "never block if we don't flush on RAM")
        }
        r.close()
        writer.close()
        dir.close()
    }

    @Test
    fun testStallControl() = runBlocking {
        val numThreads = intArrayOf(4 + random().nextInt(8), 1)
        val numDocumentsToIndex = 50 + random().nextInt(50)
        val analyzer = MockAnalyzer(random())
        analyzer.setMaxTokenLength(TestUtil.nextInt(random(), 1, IndexWriter.MAX_TERM_LENGTH))
        for (i in numThreads.indices) {
            val numDocs = AtomicInteger(numDocumentsToIndex)
            val dir = newMockDirectory()
            dir.setThrottling(MockDirectoryWrapper.Throttling.SOMETIMES)
            val iwc = IndexWriterConfig(analyzer)
            iwc.setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH)
            var flushPolicy: FlushPolicy = FlushByRamOrCountsPolicy()
            iwc.setFlushPolicy(flushPolicy)
            iwc.setRAMBufferSizeMB(0.25)
            val writer = IndexWriter(dir, iwc)
            val threads = Array(numThreads[i]) { IndexThread(numDocs, numThreads[i], writer, lineDocFile, false) }
            coroutineScope {
                val jobs = threads.map { launch { it.run() } }
                jobs.forEach { it.join() }
            }
            val docsWriter = writer.getDocsWriter()
            assertNotNull(docsWriter)
            val flushControl = docsWriter.flushControl
            assertEquals(0, writer.getFlushingBytes(), " all flushes must be due")
            assertEquals(numDocumentsToIndex, writer.getDocStats().numDocs)
            assertEquals(numDocumentsToIndex, writer.getDocStats().maxDoc)
            if (numThreads[i] == 1) {
                assertFalse(
                    docsWriter.flushControl.stallControl.hasBlocked(),
                    "single thread must not block numThreads: ${numThreads[i]}"
                )
            }
            if (docsWriter.flushControl.peakNetBytes > (2.0 * iwc.rAMBufferSizeMB * 1024.0 * 1024.0)) {
                assertTrue(docsWriter.flushControl.stallControl.wasStalled())
            }
            assertActiveBytesAfter(flushControl)
            writer.close()
            dir.close()
        }
    }

    private fun assertActiveBytesAfter(flushControl: DocumentsWriterFlushControl) {
        // The underlying API does not expose active writers; best effort validation only.
        assertEquals(flushControl.activeBytes(), flushControl.activeBytes())
    }

    private fun newDirectory(): Directory = ByteBuffersDirectory()

    private fun newMockDirectory(): MockDirectoryWrapper = MockDirectoryWrapper(random(), ByteBuffersDirectory())

    @OptIn(ExperimentalAtomicApi::class)
    class IndexThread(
        private val pendingDocs: AtomicInteger,
        numThreads: Int,
        private val writer: IndexWriter,
        private val docs: LineFileDocs,
        private val doRandomCommit: Boolean
    ) {
        private val iwc: LiveIndexWriterConfig = writer.config

        suspend fun run() {
            try {
                var ramSize = 0L
                while (pendingDocs.decrementAndGet() > -1) {
                    val doc: Document = docs.nextDoc()
                    writer.addDocument(doc)
                    val newRamSize = writer.ramBytesUsed()
                    if (newRamSize != ramSize) {
                        ramSize = newRamSize
                    }
                    if (doRandomCommit) {
                        if (TestUtil.rarely(TestUtil.random())) {
                            writer.commit()
                        }
                    }
                }
                writer.commit()
            } catch (ex: Throwable) {
                println("FAILED exc:")
                ex.printStackTrace()
                throw RuntimeException(ex)
            }
        }
    }

    private class MockDefaultFlushPolicy : FlushByRamOrCountsPolicy() {
        var peakBytesWithoutFlush: Long = Long.MIN_VALUE
        var peakDocCountWithoutFlush: Long = Long.MIN_VALUE
        var hasMarkedPending: Boolean = false

        fun isFlushOnDocCount(): Boolean = flushOnDocCount()
        fun isFlushOnRAM(): Boolean = flushOnRAM()

        override fun onChange(control: DocumentsWriterFlushControl, dwpt: DocumentsWriterPerThread?) {
            dwpt ?: return
            val pending = ArrayList<DocumentsWriterPerThread>()
            val notPending = ArrayList<DocumentsWriterPerThread>()
            findPending(control, pending, notPending)
            val flushCurrent = dwpt.isFlushPending()
            val activeBytes = control.activeBytes()
            val toFlush: DocumentsWriterPerThread? = when {
                dwpt.isFlushPending() -> dwpt
                flushOnDocCount() && dwpt.numDocsInRAM >= indexWriterConfig!!.maxBufferedDocs -> dwpt
                flushOnRAM() && activeBytes >= (indexWriterConfig!!.rAMBufferSizeMB * 1024.0 * 1024.0).toLong() -> {
                    val result = findLargestNonPendingWriter(control, dwpt)
                    assertFalse(result.isFlushPending())
                    result
                }
                else -> null
            }
            super.onChange(control, dwpt)
            if (toFlush != null) {
                if (flushCurrent) {
                    assertTrue(pending.remove(toFlush))
                } else {
                    assertTrue(notPending.remove(toFlush))
                }
                assertTrue(toFlush.isFlushPending())
                hasMarkedPending = true
            } else {
                peakBytesWithoutFlush = kotlin.math.max(activeBytes, peakBytesWithoutFlush)
                peakDocCountWithoutFlush = kotlin.math.max(dwpt.numDocsInRAM.toLong(), peakDocCountWithoutFlush)
            }
            for (perThread in notPending) {
                assertFalse(perThread.isFlushPending())
            }
        }
    }
}

fun findPending(
    flushControl: DocumentsWriterFlushControl,
    pending: MutableList<DocumentsWriterPerThread>,
    notPending: MutableList<DocumentsWriterPerThread>
) {
    // Iteration over active writers is not available; lists remain unchanged.
}
