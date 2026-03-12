/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.jdkport.AtomicInteger
import org.gnit.lucenekmp.jdkport.Thread
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper
import org.gnit.lucenekmp.tests.util.LineFileDocs
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.math.max
import kotlin.random.Random
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
    @Throws(Exception::class)
    fun beforeClass() {
        lineDocFile = LineFileDocs(random())
    }

    @AfterTest
    @Throws(Exception::class)
    fun afterClass() {
        lineDocFile.close()
    }

    @Test
    @Throws(Exception::class)
    fun testFlushByRam() {
        val ramBuffer = (if (TEST_NIGHTLY) 1 else 10) + atLeast(2) + random().nextDouble()
        runFlushByRam(1 + random().nextInt(if (TEST_NIGHTLY) 5 else 1), ramBuffer, false)
    }

    @Test
    @Throws(Exception::class)
    fun testFlushByRamLargeBuffer() {
        // with a 256 mb ram buffer we should never stall
        runFlushByRam(1 + random().nextInt(if (TEST_NIGHTLY) 5 else 1), 256.0, true)
    }

    @Throws(Exception::class)
    private fun runFlushByRam(numThreads: Int, maxRamMB: Double, ensureNotStalled: Boolean) {
        val numDocumentsToIndex = 10 + atLeast(30)
        val numDocs = AtomicInteger(numDocumentsToIndex)
        val dir = newDirectory()
        var flushPolicy = MockDefaultFlushPolicy()
        val analyzer = MockAnalyzer(random())
        analyzer.setMaxTokenLength(TestUtil.nextInt(random(), 1, IndexWriter.MAX_TERM_LENGTH))

        val iwc = newIndexWriterConfig(analyzer).setFlushPolicy(flushPolicy)
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

        val threads = Array(numThreads) {
            IndexThread(numDocs, numThreads, writer, lineDocFile, false, random())
        }
        for (thread in threads) {
            thread.start()
        }

        for (thread in threads) {
            thread.join()
        }
        val maxRAMBytes = (iwc.rAMBufferSizeMB * 1024.0 * 1024.0).toLong()
        assertEquals(0, writer.getFlushingBytes(), " all flushes must be due numThreads=$numThreads")
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
        assertEquals(0, flushControl.activeBytes())
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testFlushDocCount() {
        val numThreads = intArrayOf(2 + atLeast(1), 1)
        val analyzer = MockAnalyzer(random())
        analyzer.setMaxTokenLength(TestUtil.nextInt(random(), 1, IndexWriter.MAX_TERM_LENGTH))
        for (i in numThreads.indices) {
            val numDocumentsToIndex = 50 + atLeast(30)
            val numDocs = AtomicInteger(numDocumentsToIndex)
            val dir = newDirectory()
            var flushPolicy = MockDefaultFlushPolicy()
            val iwc = newIndexWriterConfig(analyzer).setFlushPolicy(flushPolicy)

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

            val threads = Array(numThreads[i]) {
                IndexThread(numDocs, numThreads[i], writer, lineDocFile, false, random())
            }
            for (thread in threads) {
                thread.start()
            }

            for (thread in threads) {
                thread.join()
            }

            assertEquals(
                0,
                writer.getFlushingBytes(),
                " all flushes must be due numThreads=${numThreads[i]}"
            )
            assertEquals(numDocumentsToIndex, writer.getDocStats().numDocs)
            assertEquals(numDocumentsToIndex, writer.getDocStats().maxDoc)
            assertTrue(
                flushPolicy.peakDocCountWithoutFlush <= iwc.maxBufferedDocs,
                "peak bytes without flush exceeded watermark"
            )
            assertActiveBytesAfter(flushControl)
            writer.close()
            assertEquals(0, flushControl.activeBytes())
            dir.close()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testRandom() {
        val numThreads = 1 + random().nextInt(8)
        val numDocumentsToIndex = 50 + atLeast(70)
        val numDocs = AtomicInteger(numDocumentsToIndex)
        val dir = newDirectory()
        val analyzer = MockAnalyzer(random())
        analyzer.setMaxTokenLength(TestUtil.nextInt(random(), 1, IndexWriter.MAX_TERM_LENGTH))
        val iwc = newIndexWriterConfig(analyzer)
        var flushPolicy = MockDefaultFlushPolicy()
        iwc.setFlushPolicy(flushPolicy)

        val writer = IndexWriter(dir, iwc)
        flushPolicy = writer.config.flushPolicy as MockDefaultFlushPolicy
        val docsWriter = writer.getDocsWriter()
        assertNotNull(docsWriter)
        val flushControl = docsWriter.flushControl

        assertEquals(0, writer.getFlushingBytes(), " bytes must be 0 after init")

        val threads = Array(numThreads) {
            IndexThread(numDocs, numThreads, writer, lineDocFile, true, random())
        }
        for (thread in threads) {
            thread.start()
        }

        for (thread in threads) {
            thread.join()
        }
        assertEquals(0, writer.getFlushingBytes(), " all flushes must be due")
        assertEquals(numDocumentsToIndex, writer.getDocStats().numDocs)
        assertEquals(numDocumentsToIndex, writer.getDocStats().maxDoc)
        if (flushPolicy.isFlushOnRAM() && !flushPolicy.isFlushOnDocCount()) {
            val maxRAMBytes = (iwc.rAMBufferSizeMB * 1024.0 * 1024.0).toLong()
            assertTrue(
                flushPolicy.peakBytesWithoutFlush <= maxRAMBytes,
                "peak bytes without flush exceeded watermark"
            )
            if (flushPolicy.hasMarkedPending) {
                assertTrue(
                    maxRAMBytes <= flushControl.peakActiveBytes,
                    "max: $maxRAMBytes ${flushControl.peakActiveBytes}"
                )
            }
        }
        assertActiveBytesAfter(flushControl)
        writer.commit()
        assertEquals(0, flushControl.activeBytes())
        val r = DirectoryReader.open(dir)
        assertEquals(numDocumentsToIndex, r.numDocs())
        assertEquals(numDocumentsToIndex, r.maxDoc())
        if (!flushPolicy.isFlushOnRAM()) {
            assertFalse(
                docsWriter.flushControl.stallControl.wasStalled(),
                "never stall if we don't flush on RAM"
            )
            assertFalse(
                docsWriter.flushControl.stallControl.hasBlocked(),
                "never block if we don't flush on RAM"
            )
        }
        r.close()
        writer.close()
        dir.close()
    }

    @Test
    fun testStallControl() {
        val numThreads = intArrayOf(4 + random().nextInt(8), 1)
        val numDocumentsToIndex = 50 + random().nextInt(50)
        val analyzer = MockAnalyzer(random())
        analyzer.setMaxTokenLength(TestUtil.nextInt(random(), 1, IndexWriter.MAX_TERM_LENGTH))
        for (i in numThreads.indices) {
            val numDocs = AtomicInteger(numDocumentsToIndex)
            val dir: MockDirectoryWrapper = newMockDirectory()
            dir.setThrottling(MockDirectoryWrapper.Throttling.SOMETIMES)
            val iwc = newIndexWriterConfig(analyzer)
            iwc.setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH)
            val flushPolicy: FlushPolicy = FlushByRamOrCountsPolicy()
            iwc.setFlushPolicy(flushPolicy)

            // with such a small ram buffer we should be stalled quite quickly
            iwc.setRAMBufferSizeMB(0.25)
            val writer = IndexWriter(dir, iwc)
            val threads = Array(numThreads[i]) {
                IndexThread(numDocs, numThreads[i], writer, lineDocFile, false, random())
            }
            for (thread in threads) {
                thread.start()
            }

            for (thread in threads) {
                thread.join()
            }
            writer.commit()
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
        val allActiveWriter = flushControl.allActiveWriters()
        var bytesUsed = 0L
        while (allActiveWriter.hasNext()) {
            val next = allActiveWriter.next()
            bytesUsed += next.ramBytesUsed()
        }
        assertEquals(bytesUsed, flushControl.activeBytes())
    }

    class IndexThread(
        private val pendingDocs: AtomicInteger,
        @Suppress("UNUSED_PARAMETER")
        numThreads: Int,
        private val writer: IndexWriter,
        private val docs: LineFileDocs,
        private val doRandomCommit: Boolean,
        private val random: Random,
    ) {
        private var thread: Thread? = null
        private var failure: Throwable? = null

        fun start() {
            thread =
                Thread {
                    try {
                        run()
                    } catch (t: Throwable) {
                        failure = t
                        throw t
                    }
                }.also {
                    it.start()
                }
        }

        fun join() {
            thread?.join()
            if (failure != null) {
                throw RuntimeException(failure)
            }
        }

        fun run() {
            try {
                var ramSize = 0L
                while (pendingDocs.decrementAndFetch() > -1) {
                    val doc: Document = docs.nextDoc()
                    writer.addDocument(doc)
                    val newRamSize = writer.ramBytesUsed()
                    if (newRamSize != ramSize) {
                        ramSize = newRamSize
                    }
                    if (doRandomCommit) {
                        if (rarely(random)) {
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
        var peakBytesWithoutFlush = Int.MIN_VALUE.toLong()
        var peakDocCountWithoutFlush = Int.MIN_VALUE.toLong()
        var hasMarkedPending = false

        fun isFlushOnDocCount(): Boolean = flushOnDocCount()

        fun isFlushOnRAM(): Boolean = flushOnRAM()

        override fun onChange(control: DocumentsWriterFlushControl, perThread: DocumentsWriterPerThread?) {
            val pending = ArrayList<DocumentsWriterPerThread>()
            val notPending = ArrayList<DocumentsWriterPerThread>()
            findPending(control, pending, notPending)
            val flushCurrent = perThread!!.isFlushPending()
            val activeBytes = control.activeBytes()
            val toFlush: DocumentsWriterPerThread? =
                if (perThread.isFlushPending()) {
                    perThread
                } else if (flushOnDocCount() && perThread.numDocsInRAM >= indexWriterConfig!!.maxBufferedDocs) {
                    perThread
                } else if (flushOnRAM() &&
                    activeBytes >= (indexWriterConfig!!.rAMBufferSizeMB * 1024.0 * 1024.0).toLong()
                ) {
                    findLargestNonPendingWriter(control, perThread).also {
                        assert(!it.isFlushPending())
                    }
                } else {
                    null
                }
            super.onChange(control, perThread)
            if (toFlush != null) {
                if (flushCurrent) {
                    assertTrue(pending.remove(toFlush))
                } else {
                    assertTrue(notPending.remove(toFlush))
                }
                assertTrue(toFlush.isFlushPending())
                hasMarkedPending = true
            } else {
                peakBytesWithoutFlush = max(activeBytes, peakBytesWithoutFlush)
                peakDocCountWithoutFlush = max(perThread.numDocsInRAM.toLong(), peakDocCountWithoutFlush)
            }

            for (perThread in notPending) {
                assertFalse(perThread.isFlushPending())
            }
        }
    }

    companion object {
        fun findPending(
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
    }
}
