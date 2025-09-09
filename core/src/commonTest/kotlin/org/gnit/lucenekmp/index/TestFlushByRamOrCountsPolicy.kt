package org.gnit.lucenekmp.index

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.jdkport.AtomicInteger
import org.gnit.lucenekmp.jdkport.decrementAndGet
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.LineFileDocs
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestFlushByRamOrCountsPolicy : LuceneTestCase() {

    private lateinit var lineDocFile: LineFileDocs

    @BeforeTest
    fun setUp() {
        lineDocFile = LineFileDocs(random())
    }

    @AfterTest
    fun tearDown() {
        lineDocFile.close()
    }

    @Test
    fun testFlushByRam() {
        val ramBuffer = (if (TEST_NIGHTLY) 1 else 10) + atLeast(2) + random().nextDouble()
        runFlushByRam(1 + random().nextInt(if (TEST_NIGHTLY) 5 else 1), ramBuffer, false)
    }

    @Test
    fun testFlushByRamLargeBuffer() {
        runFlushByRam(1 + random().nextInt(if (TEST_NIGHTLY) 5 else 1), 256.0, true)
    }

    private fun runFlushByRam(numThreads: Int, maxRamMB: Double, ensureNotStalled: Boolean) = runBlocking {
        val numDocumentsToIndex = 10 + atLeast(30)
        val numDocs = AtomicInteger(numDocumentsToIndex)
        val dir = newDirectory()
        var flushPolicy = MockDefaultFlushPolicy()
        val analyzer = MockAnalyzer(random())
        analyzer.setMaxTokenLength(TestUtil.nextInt(random(), 1, IndexWriter.MAX_TERM_LENGTH))
        val iwc = newIndexWriterConfig(analyzer).setFlushPolicy(flushPolicy)
        iwc.rAMBufferSizeMB = maxRamMB
        iwc.maxBufferedDocs = IndexWriterConfig.DISABLE_AUTO_FLUSH
        val writer = IndexWriter(dir, iwc)
        flushPolicy = writer.config.flushPolicy as MockDefaultFlushPolicy
        assertFalse(flushPolicy.flushOnDocCount())
        assertTrue(flushPolicy.flushOnRAM())
        val docsWriter = writer.getDocsWriter()
        assertNotNull(docsWriter)
        val flushControl = docsWriter.flushControl
        assertEquals(0L, writer.flushingBytes, " bytes must be 0 after init")

        val jobs = Array(numThreads) {
            val thread = IndexThread(numDocs, numThreads, writer, lineDocFile, false)
            launch { thread.run() }
        }
        jobs.forEach { it.join() }

        val maxRAMBytes = (iwc.rAMBufferSizeMB * 1024.0 * 1024.0).toLong()
        assertEquals(0L, writer.flushingBytes, " all flushes must be due numThreads=$numThreads")
        assertEquals(numDocumentsToIndex, writer.getDocStats().numDocs)
        assertEquals(numDocumentsToIndex, writer.getDocStats().maxDoc)
        assertTrue(
            flushPolicy.peakBytesWithoutFlush <= maxRAMBytes,
            "peak bytes without flush exceeded watermark"
        )
        assertActiveBytesAfter(flushControl)
        if (flushPolicy.hasMarkedPending) {
            assertTrue(maxRAMBytes < flushControl.peakActiveBytes)
        }
        if (ensureNotStalled) {
            assertFalse(docsWriter.flushControl.stallControl.wasStalled())
        }
        writer.close()
        assertEquals(0L, flushControl.activeBytes())
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
            val iwc = newIndexWriterConfig(analyzer).setFlushPolicy(flushPolicy)

            iwc.maxBufferedDocs = 2 + atLeast(10)
            iwc.rAMBufferSizeMB = IndexWriterConfig.DISABLE_AUTO_FLUSH.toDouble()
            val writer = IndexWriter(dir, iwc)
            flushPolicy = writer.config.flushPolicy as MockDefaultFlushPolicy
            assertTrue(flushPolicy.flushOnDocCount())
            assertFalse(flushPolicy.flushOnRAM())
            val docsWriter = writer.getDocsWriter()
            assertNotNull(docsWriter)
            val flushControl = docsWriter.flushControl
            assertEquals(0L, writer.flushingBytes, " bytes must be 0 after init")
            val jobs = Array(numThreads[i]) {
                val thread = IndexThread(numDocs, numThreads[i], writer, lineDocFile, true)
                launch { thread.run() }
            }
            jobs.forEach { it.join() }
            assertEquals(
                0L,
                writer.flushingBytes,
                " all flushes must be due numThreads=${numThreads[i]}"
            )
            assertEquals(numDocumentsToIndex, writer.getDocStats().numDocs)
            assertEquals(numDocumentsToIndex, writer.getDocStats().maxDoc)
            assertTrue(
                flushPolicy.peakDocCountWithoutFlush <= writer.config.maxBufferedDocs,
                "peak doc count without flush exceeded watermark"
            )
            if (iwc.maxBufferedDocs < numThreads[i]) {
                assertTrue(docsWriter.flushControl.stallControl.wasStalled())
            }
            assertActiveBytesAfter(flushControl)
            writer.close()
            dir.close()
        }
    }

    private fun assertActiveBytesAfter(flushControl: DocumentsWriterFlushControl) {
        val allActiveWriter = flushControl.allActiveWriters()
        var bytesUsed = 0L
        while (allActiveWriter.hasNext()) {
            val next = allActiveWriter.next()
            bytesUsed += next.ramBytesUsed()
        }
        assertEquals(bytesUsed, flushControl.activeBytes())
    }

    private class IndexThread(
        private val pendingDocs: AtomicInteger,
        private val numThreads: Int,
        private val writer: IndexWriter,
        private val docs: LineFileDocs,
        private val doRandomCommit: Boolean
    ) {
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
                        if (rarely()) {
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
        var peakBytesWithoutFlush: Long = Int.MIN_VALUE.toLong()
        var peakDocCountWithoutFlush: Long = Int.MIN_VALUE.toLong()
        var hasMarkedPending = false

        override fun onChange(control: DocumentsWriterFlushControl, dwpt: DocumentsWriterPerThread) {
            val pending = ArrayList<DocumentsWriterPerThread>()
            val notPending = ArrayList<DocumentsWriterPerThread>()
            findPending(control, pending, notPending)
            val flushCurrent = dwpt.isFlushPending()
            val activeBytes = control.activeBytes()
            val toFlush: DocumentsWriterPerThread? =
                if (dwpt.isFlushPending()) {
                    dwpt
                } else if (flushOnDocCount() && dwpt.numDocsInRAM >= indexWriterConfig!!.maxBufferedDocs) {
                    dwpt
                } else if (flushOnRAM() &&
                    activeBytes >= (indexWriterConfig!!.rAMBufferSizeMB * 1024.0 * 1024.0).toLong()
                ) {
                    val candidate = findLargestNonPendingWriter(control, dwpt)
                    assertFalse(candidate.isFlushPending())
                    candidate
                } else {
                    null
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
                peakDocCountWithoutFlush =
                    kotlin.math.max(dwpt.numDocsInRAM.toLong(), peakDocCountWithoutFlush)
            }
            for (perThread in notPending) {
                assertFalse(perThread.isFlushPending())
            }
        }
    }
}

private fun findPending(
    flushControl: DocumentsWriterFlushControl,
    pending: ArrayList<DocumentsWriterPerThread>,
    notPending: ArrayList<DocumentsWriterPerThread>
) {
    val allActiveThreads = flushControl.allActiveWriters()
    while (allActiveThreads.hasNext()) {
        val next = allActiveThreads.next()
        if (next.isFlushPending()) {
            pending.add(next)
        } else {
            notPending.add(next)
        }
    }
}

private fun rarely(): Boolean = TestUtil.rarely(LuceneTestCase.random())

