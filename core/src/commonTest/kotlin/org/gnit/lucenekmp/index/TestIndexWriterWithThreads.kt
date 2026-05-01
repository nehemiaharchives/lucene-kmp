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

import io.github.oshai.kotlinlogging.KotlinLogging
import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.jdkport.AtomicInteger
import org.gnit.lucenekmp.jdkport.BrokenBarrierException
import org.gnit.lucenekmp.jdkport.CyclicBarrier
import org.gnit.lucenekmp.jdkport.InterruptedException
import org.gnit.lucenekmp.jdkport.ReentrantLock
import org.gnit.lucenekmp.jdkport.Thread
import org.gnit.lucenekmp.jdkport.get
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.AlreadyClosedException
import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.store.ByteBuffersDirectoryPerfDebug
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.LockObtainFailedException
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.index.SuppressingConcurrentMergeScheduler
import org.gnit.lucenekmp.tests.store.BaseDirectoryWrapper
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper
import org.gnit.lucenekmp.tests.util.LineFileDocs
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion.SuppressCodecs
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.ThreadInterruptedException
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.TimeSource

/** MultiThreaded IndexWriter tests */
@OptIn(ExperimentalAtomicApi::class)
@SuppressCodecs("SimpleText")
class TestIndexWriterWithThreads : LuceneTestCase() {
    companion object {
        private val logger = KotlinLogging.logger {}

        private fun perfLog(message: String) {
            logger.debug { message }
            println(message)
        }
    }

    // Used by test cases below
    private class IndexerThread(
        var writer: IndexWriter,
        var noErrors: Boolean,
        private val syncStart: CyclicBarrier
    ) : Thread() {
        var error: Throwable? = null
        var addCount: Int = 0

        override fun run() {
            try {
                syncStart.await()
            } catch (e: BrokenBarrierException) {
                error = e
                throw kotlin.RuntimeException(e)
            } catch (e: InterruptedException) {
                error = e
                throw kotlin.RuntimeException(e)
            }

            val doc = Document()
            val customType = FieldType(TextField.TYPE_STORED)
            customType.setStoreTermVectors(true)
            customType.setStoreTermVectorPositions(true)
            customType.setStoreTermVectorOffsets(true)

            doc.add(newField("field", "aaa bbb ccc ddd eee fff ggg hhh iii jjj", customType))
            doc.add(NumericDocValuesField("dv", 5))

            var idUpto = 0
            var fullCount = 0

            do {
                try {
                    writer.updateDocument(Term("id", "${idUpto++}"), doc)
                    addCount++
                } catch (ioe: IOException) {
                    if (LuceneTestCase.VERBOSE) {
                        println("TEST: expected exc:")
                        println(ioe.stackTraceToString())
                    }
                    if (ioe.message!!.startsWith("fake disk full at") || ioe.message == "now failing on purpose") {
                        try {
                            Thread.sleep(1)
                        } catch (ie: InterruptedException) {
                            throw ThreadInterruptedException(ie)
                        }
                        if (fullCount++ >= 5) {
                            break
                        }
                    } else {
                        if (noErrors) {
                            println("${Thread.currentThread().getName()}: ERROR: unexpected IOException:")
                            println(ioe.stackTraceToString())
                            error = ioe
                        }
                        break
                    }
                } catch (_: IllegalStateException) {
                    // OK: abort closes the writer
                    break
                } catch (t: Throwable) {
                    if (noErrors) {
                        println("${Thread.currentThread().getName()}: ERROR: unexpected Throwable:")
                        println(t.stackTraceToString())
                        error = t
                    }
                    break
                }
            } while (true)
        }
    }

    // LUCENE-1130: make sure immediate disk full on creating
    // an IndexWriter (hit during DWPT#updateDocuments()), with
    // multiple threads, is OK:
    @Test
    fun testImmediateDiskFullWithThreads() {
        val NUM_THREADS = 3
        val numIterations = if (TEST_NIGHTLY) 10 else 1
        for (iter in 0 until numIterations) {
            if (VERBOSE) {
                println("\nTEST: iter=$iter")
            }
            val dir = newMockDirectory()
            val writer =
                IndexWriter(
                    dir,
                    newIndexWriterConfig(MockAnalyzer(random()))
                        .setMaxBufferedDocs(2)
                        .setMergeScheduler(ConcurrentMergeScheduler())
                        .setMergePolicy(newLogMergePolicy(4))
                        .setCommitOnClose(false)
                )
            (writer.config.mergeScheduler as ConcurrentMergeScheduler).setSuppressExceptions()
            dir.maxSizeInBytes = 4 * 1024L + 20L * iter

            val syncStart = CyclicBarrier(NUM_THREADS + 1)
            val threads = arrayOfNulls<IndexerThread>(NUM_THREADS)
            for (i in 0 until NUM_THREADS) {
                threads[i] = IndexerThread(writer, true, syncStart)
                threads[i]!!.start()
            }
            syncStart.await()

            for (i in 0 until NUM_THREADS) {
                threads[i]!!.join()
                assertTrue(threads[i]!!.error == null, "hit unexpected Throwable")
            }

            dir.maxSizeInBytes = 0
            try {
                writer.commit()
            } catch (_: AlreadyClosedException) {
                // OK: abort closes the writer
                assertTrue(writer.isDeleterClosed())
            } finally {
                writer.close()
            }
            dir.close()
        }
    }

    // LUCENE-1130: make sure we can close() even while
    // threads are trying to add documents.  Strictly
    // speaking, this isn't valid us of Lucene's APIs, but we
    // still want to be robust to this case:
    @Test
    fun testCloseWithThreads() {
        val NUM_THREADS = 3
        val numIterations = if (TEST_NIGHTLY) 7 else 3
        for (iter in 0 until numIterations) {
            if (VERBOSE) {
                println("\nTEST: iter=$iter")
            }
            val dir = newDirectory()

            val writer =
                IndexWriter(
                    dir,
                    newIndexWriterConfig(MockAnalyzer(random()))
                        .setMaxBufferedDocs(10)
                        .setMergeScheduler(ConcurrentMergeScheduler())
                        .setMergePolicy(newLogMergePolicy(4))
                        .setCommitOnClose(false)
                )
            (writer.config.mergeScheduler as ConcurrentMergeScheduler).setSuppressExceptions()

            val syncStart = CyclicBarrier(NUM_THREADS + 1)
            val threads = arrayOfNulls<IndexerThread>(NUM_THREADS)
            for (i in 0 until NUM_THREADS) {
                threads[i] = IndexerThread(writer, false, syncStart)
                threads[i]!!.start()
            }
            syncStart.await()

            var done = false
            while (!done) {
                Thread.sleep(100)
                for (i in 0 until NUM_THREADS) {
                    if (threads[i]!!.addCount > 0) {
                        done = true
                        break
                    } else if (!threads[i]!!.isAlive()) {
                        fail("thread failed before indexing a single document")
                    }
                }
            }

            if (VERBOSE) {
                println("\nTEST: now close")
            }
            try {
                writer.commit()
            } finally {
                writer.close()
            }

            for (i in 0 until NUM_THREADS) {
                threads[i]!!.join()
                if (threads[i]!!.isAlive()) {
                    fail("thread seems to be hung")
                }
            }

            val reader = DirectoryReader.open(dir)
            val tdocs = TestUtil.docs(random(), reader, "field", BytesRef("aaa"), null, 0)
            var count = 0
            while (tdocs!!.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                count++
            }
            assertTrue(count > 0)
            reader.close()

            dir.close()
        }
    }

    // Runs test, with multiple threads, using the specific
    // failure to trigger an IOException
    fun _testMultipleThreadsFailure(failure: MockDirectoryWrapper.Failure) {
        val NUM_THREADS = 3

        for (iter in 0 until 2) {
            if (VERBOSE) {
                println("TEST: iter=$iter")
            }
            val dir = newMockDirectory()

            val writer =
                IndexWriter(
                    dir,
                    newIndexWriterConfig(MockAnalyzer(random()))
                        .setMaxBufferedDocs(2)
                        .setMergeScheduler(ConcurrentMergeScheduler())
                        .setMergePolicy(newLogMergePolicy(4))
                        .setCommitOnClose(false)
                )
            (writer.config.mergeScheduler as ConcurrentMergeScheduler).setSuppressExceptions()

            val syncStart = CyclicBarrier(NUM_THREADS + 1)
            val threads = arrayOfNulls<IndexerThread>(NUM_THREADS)
            for (i in 0 until NUM_THREADS) {
                threads[i] = IndexerThread(writer, true, syncStart)
                threads[i]!!.start()
            }
            syncStart.await()

            dir.failOn(failure)
            failure.setDoFail()

            for (i in 0 until NUM_THREADS) {
                threads[i]!!.join()
                assertTrue(threads[i]!!.error == null, "hit unexpected Throwable")
            }

            var success = false
            try {
                writer.commit()
                writer.close()
                success = true
            } catch (_: AlreadyClosedException) {
                // OK: abort closes the writer
                assertTrue(writer.isDeleterClosed())
            } catch (_: IOException) {
                writer.rollback()
                failure.clearDoFail()
            }
            if (VERBOSE) {
                println("TEST: success=$success")
            }

            if (success) {
                val reader = DirectoryReader.open(dir)
                val delDocs: Bits? = MultiBits.getLiveDocs(reader)
                val storedFields = reader.storedFields()
                val termVectors = reader.termVectors()
                for (j in 0 until reader.maxDoc()) {
                    if (delDocs == null || !delDocs.get(j)) {
                        storedFields.document(j)
                        termVectors.get(j)
                    }
                }
                reader.close()
            }

            dir.close()
        }
    }

    // Runs test, with one thread, using the specific failure
    // to trigger an IOException
    fun _testSingleThreadFailure(failure: MockDirectoryWrapper.Failure) {
        val dir = newMockDirectory()

        val iwc =
            newIndexWriterConfig(MockAnalyzer(random()))
                .setMaxBufferedDocs(2)
                .setMergeScheduler(ConcurrentMergeScheduler())
                .setCommitOnClose(false)

        if (iwc.mergeScheduler is ConcurrentMergeScheduler) {
            iwc.setMergeScheduler(
                object : SuppressingConcurrentMergeScheduler() {
                    override fun isOK(t: Throwable): Boolean {
                        return t is AlreadyClosedException ||
                            (t is IllegalStateException &&
                                t.message!!.contains("this writer hit an unrecoverable error"))
                    }
                }
            )
        }

        val writer = IndexWriter(dir, iwc)
        val doc = Document()
        val customType = FieldType(TextField.TYPE_STORED)
        customType.setStoreTermVectors(true)
        customType.setStoreTermVectorPositions(true)
        customType.setStoreTermVectorOffsets(true)
        doc.add(newField("field", "aaa bbb ccc ddd eee fff ggg hhh iii jjj", customType))

        for (i in 0 until 6) {
            writer.addDocument(doc)
        }

        dir.failOn(failure)
        failure.setDoFail()
        expectThrows(IOException::class) {
            writer.addDocument(doc)
            writer.addDocument(doc)
            writer.commit()
        }

        failure.clearDoFail()
        expectThrows(AlreadyClosedException::class) {
            writer.addDocument(doc)
            writer.commit()
            writer.close()
        }

        assertTrue(writer.isDeleterClosed())
        dir.close()
    }

    // Throws IOException during FieldsWriter.flushDocument and during DocumentsWriter.abort
    private class FailOnlyOnAbortOrFlush(private val onlyOnce: Boolean) : MockDirectoryWrapper.Failure() {
        override fun eval(dir: MockDirectoryWrapper) {
            dir.setAssertNoUnrefencedFilesOnClose(false)

            if (doFail) {
                if (callStackContainsAnyOf("abort", "finishDocument") &&
                    false == callStackContainsAnyOf("merge", "close")
                ) {
                    if (onlyOnce) {
                        doFail = false
                    }
                    throw IOException("now failing on purpose")
                }
            }
        }
    }

    // LUCENE-1130: make sure initial IOException, and then 2nd
    // IOException during rollback(), is OK:
    @Test
    fun testIOExceptionDuringAbort() {
        _testSingleThreadFailure(FailOnlyOnAbortOrFlush(false))
    }

    // LUCENE-1130: make sure initial IOException, and then 2nd
    // IOException during rollback(), is OK:
    @Test
    fun testIOExceptionDuringAbortOnlyOnce() {
        _testSingleThreadFailure(FailOnlyOnAbortOrFlush(true))
    }

    // LUCENE-1130: make sure initial IOException, and then 2nd
    // IOException during rollback(), with multiple threads, is OK:
    @Test
    fun testIOExceptionDuringAbortWithThreads() {
        _testMultipleThreadsFailure(FailOnlyOnAbortOrFlush(false))
    }

    // LUCENE-1130: make sure initial IOException, and then 2nd
    // IOException during rollback(), with multiple threads, is OK:
    @Test
    fun testIOExceptionDuringAbortWithThreadsOnlyOnce() {
        _testMultipleThreadsFailure(FailOnlyOnAbortOrFlush(true))
    }

    // Throws IOException during DocumentsWriter.writeSegment
    private class FailOnlyInWriteSegment(private val onlyOnce: Boolean) : MockDirectoryWrapper.Failure() {
        override fun eval(dir: MockDirectoryWrapper) {
            if (doFail) {
                if (callStackContains(IndexingChain::class, "flush")) {
                    if (onlyOnce) {
                        doFail = false
                    }
                    throw IOException("now failing on purpose")
                }
            }
        }
    }

    // LUCENE-1130: test IOException in writeSegment
    @Test
    fun testIOExceptionDuringWriteSegment() {
        _testSingleThreadFailure(FailOnlyInWriteSegment(false))
    }

    // LUCENE-1130: test IOException in writeSegment
    @Test
    fun testIOExceptionDuringWriteSegmentOnlyOnce() {
        _testSingleThreadFailure(FailOnlyInWriteSegment(true))
    }

    // LUCENE-1130: test IOException in writeSegment, with threads
    @Test
    fun testIOExceptionDuringWriteSegmentWithThreads() {
        _testMultipleThreadsFailure(FailOnlyInWriteSegment(false))
    }

    // LUCENE-1130: test IOException in writeSegment, with threads
    @Test
    fun testIOExceptionDuringWriteSegmentWithThreadsOnlyOnce() {
        _testMultipleThreadsFailure(FailOnlyInWriteSegment(true))
    }

    //  LUCENE-3365: Test adding two documents with the same field from two different IndexWriters
    //  that we attempt to open at the same time.  As long as the first IndexWriter completes
    //  and closes before the second IndexWriter time's out trying to get the Lock,
    //  we should see both documents
    @Test
    fun testOpenTwoIndexWritersOnDifferentThreads() {
        newDirectory().use { dir ->
            val syncStart = CyclicBarrier(2)
            val thread1 = DelayedIndexAndCloseRunnable(dir, syncStart, Random(random().nextLong()))
            val thread2 = DelayedIndexAndCloseRunnable(dir, syncStart, Random(random().nextLong()))
            thread1.start()
            thread2.start()
            thread1.join()
            thread2.join()

            if (thread1.failure is LockObtainFailedException || thread2.failure is LockObtainFailedException) {
                return
            }

            assertFalse(thread1.failed, "Failed due to: ${thread1.failure}")
            assertFalse(thread2.failed, "Failed due to: ${thread2.failure}")

            val reader = DirectoryReader.open(dir)
            assertEquals(2, reader.numDocs(), "IndexReader should have one document per thread running")
            reader.close()
        }
    }

    private class DelayedIndexAndCloseRunnable(
        private val dir: Directory,
        private var syncStart: CyclicBarrier,
        private val random: Random
    ) : Thread() {
        var failed = false
        var failure: Throwable? = null

        override fun run() {
            try {
                val doc = Document()
                val field = LuceneTestCase.newTextField("field", "testData", Field.Store.YES)
                doc.add(field)

                syncStart.await()
                val writer = IndexWriter(dir, LuceneTestCase.newIndexWriterConfig(MockAnalyzer(random)))
                writer.addDocument(doc)
                writer.close()
            } catch (e: Throwable) {
                failed = true
                failure = e
            }
        }
    }

    // LUCENE-4147
    @Test
    fun testRollbackAndCommitWithThreads() {
        val d: BaseDirectoryWrapper = newDirectory()

        val threadCount = TestUtil.nextInt(random(), 2, 6)

        val writerRef = AtomicReference<IndexWriter?>(null)
        val analyzer = MockAnalyzer(random())
        analyzer.setMaxTokenLength(TestUtil.nextInt(random(), 1, IndexWriter.MAX_TERM_LENGTH))

        writerRef.store(IndexWriter(d, newIndexWriterConfig(analyzer)))
        writerRef.load()!!.commit()
        val docs = LineFileDocs(random())
        val threads = arrayOfNulls<Thread>(threadCount)
        val iters = atLeast(100)
        val failed = AtomicBoolean(false)
        val rollbackLock = ReentrantLock()
        val commitLock = ReentrantLock()
        for (threadID in 0 until threadCount) {
            threads[threadID] =
                object : Thread() {
                    override fun run() {
                        for (iter in 0 until iters) {
                            if (failed.load()) {
                                break
                            }
                            val x = random().nextInt(3)
                            try {
                                when (x) {
                                    0 -> {
                                        rollbackLock.lock()
                                        if (VERBOSE) {
                                            println("\nTEST: ${Thread.currentThread().getName()}: now rollback")
                                        }
                                        try {
                                            writerRef.load()!!.rollback()
                                            if (VERBOSE) {
                                                println("TEST: ${Thread.currentThread().getName()}: rollback done; now open new writer")
                                            }
                                            writerRef.store(IndexWriter(d, newIndexWriterConfig(MockAnalyzer(random()))))
                                        } finally {
                                            rollbackLock.unlock()
                                        }
                                    }

                                    1 -> {
                                        commitLock.lock()
                                        if (VERBOSE) {
                                            println("\nTEST: ${Thread.currentThread().getName()}: now commit")
                                        }
                                        try {
                                            if (random().nextBoolean()) {
                                                writerRef.load()!!.prepareCommit()
                                            }
                                            writerRef.load()!!.commit()
                                        } catch (_: AlreadyClosedException) {
                                            // ok
                                        } catch (_: NullPointerException) {
                                            // ok
                                        } finally {
                                            commitLock.unlock()
                                        }
                                    }

                                    2 -> {
                                        if (VERBOSE) {
                                            println("\nTEST: ${Thread.currentThread().getName()}: now add")
                                        }
                                        try {
                                            writerRef.load()!!.addDocument(docs.nextDoc())
                                        } catch (_: AlreadyClosedException) {
                                            // ok
                                        } catch (_: NullPointerException) {
                                            // ok
                                        } catch (_: AssertionError) {
                                            // ok
                                        }
                                    }
                                }
                            } catch (t: Throwable) {
                                failed.store(true)
                                throw kotlin.RuntimeException(t)
                            }
                        }
                    }
                }
            threads[threadID]!!.start()
        }

        for (threadID in 0 until threadCount) {
            threads[threadID]!!.join()
        }

        assertTrue(!failed.load())
        writerRef.load()!!.close()
        d.close()
    }

    @Test
    fun testUpdateSingleDocWithThreads() {
        stressUpdateSingleDocWithThreads(false, rarely(), null)
    }

    @Test
    fun testSoftUpdateSingleDocWithThreads() {
        stressUpdateSingleDocWithThreads(true, rarely(), null)
    }

    @Test
    fun testUpdateSingleDocWithThreadsPerfFixed() {
        stressUpdateSingleDocWithThreads(false, false, 600) { ByteBuffersDirectory() }
    }

    fun stressUpdateSingleDocWithThreads(
        useSoftDeletes: Boolean,
        forceMerge: Boolean,
        fixedItersPerThread: Int?,
        directoryFactory: () -> Directory = { newDirectory() }
    ) {
        val shouldProfile = fixedItersPerThread != null
        if (shouldProfile) {
            IndexPerfDebug.reset()
            ByteBuffersDirectoryPerfDebug.reset()
        }
        val totalStart = if (shouldProfile) TimeSource.Monotonic.markNow() else null
        directoryFactory().use { dir ->
            val setupStart = TimeSource.Monotonic.markNow()
            RandomIndexWriter(
                random(),
                dir,
                newIndexWriterConfig().setMaxBufferedDocs(-1).setRAMBufferSizeMB(0.00001),
                useSoftDeletes
            ).use { writer ->
                perfLog("phase=test_setup elapsedMs=${setupStart.elapsedNow().inWholeMilliseconds} useSoftDeletes=$useSoftDeletes forceMerge=$forceMerge")
                val numThreads = if (TEST_NIGHTLY) 3 + random().nextInt(3) else 3
                val threads = arrayOfNulls<Thread>(numThreads)
                val done = AtomicInteger(0)
                val barrier = CyclicBarrier(threads.size + 1)
                val doc = Document()
                doc.add(StringField("id", "1", Field.Store.NO))
                val initialUpdateStart = TimeSource.Monotonic.markNow()
                writer.updateDocument(Term("id", "1"), doc)
                perfLog("phase=initial_update elapsedMs=${initialUpdateStart.elapsedNow().inWholeMilliseconds}")
                val itersPerThread = fixedItersPerThread ?: (100 + random().nextInt(2000))
                perfLog("phase=thread_config numThreads=$numThreads itersPerThread=$itersPerThread")
                val threadLaunchStart = TimeSource.Monotonic.markNow()
                for (i in threads.indices) {
                    threads[i] =
                        Thread {
                            try {
                                barrier.await()
                                for (iters in 0 until itersPerThread) {
                                    val d = Document()
                                    d.add(StringField("id", "1", Field.Store.NO))
                                    writer.updateDocument(Term("id", "1"), d)
                                }
                            } catch (e: Exception) {
                                throw AssertionError(e)
                            } finally {
                                done.store(done.load() + 1)
                            }
                        }
                    threads[i]!!.start()
                }
                perfLog("phase=thread_launch elapsedMs=${threadLaunchStart.elapsedNow().inWholeMilliseconds} numThreads=${threads.size}")
                val openStart = TimeSource.Monotonic.markNow()
                var open = DirectoryReader.open(writer.w)
                perfLog("phase=open_initial_reader elapsedMs=${openStart.elapsedNow().inWholeMilliseconds}")
                assertEquals(1, open.numDocs())
                val barrierStart = TimeSource.Monotonic.markNow()
                barrier.await()
                perfLog("phase=barrier_release elapsedMs=${barrierStart.elapsedNow().inWholeMilliseconds}")
                val loopStart = TimeSource.Monotonic.markNow()
                var loopIterations = 0
                var forceMergeCount = 0
                var forceMergeElapsedMs = 0L
                var reopenCount = 0
                var reopenElapsedMs = 0L
                var openIfChangedCount = 0
                var openIfChangedElapsedMs = 0L
                var numDocsCheckCount = 0
                var numDocsCheckElapsedMs = 0L
                try {
                    do {
                        loopIterations++
                        if (forceMerge && random().nextBoolean()) {
                            val forceMergeStart = TimeSource.Monotonic.markNow()
                            writer.forceMerge(1)
                            forceMergeCount++
                            forceMergeElapsedMs += forceMergeStart.elapsedNow().inWholeMilliseconds
                        }
                        val openIfChangedStart = TimeSource.Monotonic.markNow()
                        val newReader = DirectoryReader.openIfChanged(open)
                        openIfChangedCount++
                        openIfChangedElapsedMs +=
                            openIfChangedStart.elapsedNow().inWholeMilliseconds
                        if (newReader != null) {
                            val reopenStart = TimeSource.Monotonic.markNow()
                            open.close()
                            open = newReader
                            reopenCount++
                            reopenElapsedMs += reopenStart.elapsedNow().inWholeMilliseconds
                        }
                        val numDocsCheckStart = TimeSource.Monotonic.markNow()
                        assertEquals(1, open.numDocs())
                        numDocsCheckCount++
                        numDocsCheckElapsedMs += numDocsCheckStart.elapsedNow().inWholeMilliseconds
                    } while (done.get() < threads.size)
                    perfLog("phase=main_loop elapsedMs=${loopStart.elapsedNow().inWholeMilliseconds} loopIterations=$loopIterations forceMergeCount=$forceMergeCount forceMergeElapsedMs=$forceMergeElapsedMs openIfChangedCount=$openIfChangedCount openIfChangedElapsedMs=$openIfChangedElapsedMs reopenCount=$reopenCount reopenElapsedMs=$reopenElapsedMs numDocsCheckCount=$numDocsCheckCount numDocsCheckElapsedMs=$numDocsCheckElapsedMs")
                } finally {
                    val joinStart = TimeSource.Monotonic.markNow()
                    open.close()
                    for (i in threads.indices) {
                        threads[i]!!.join()
                    }
                    perfLog("phase=cleanup elapsedMs=${joinStart.elapsedNow().inWholeMilliseconds}")
                }
            }
        }
        if (shouldProfile) {
            perfLog("phase=test_total elapsedMs=${totalStart!!.elapsedNow().inWholeMilliseconds} useSoftDeletes=$useSoftDeletes forceMerge=$forceMerge")
            perfLog(IndexPerfDebug.snapshot())
            perfLog(ByteBuffersDirectoryPerfDebug.snapshot())
        }
    }
}
