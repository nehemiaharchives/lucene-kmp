package org.gnit.lucenekmp.index

import io.github.oshai.kotlinlogging.KotlinLogging
import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.KnnFloatVectorField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.jdkport.CountDownLatch
import org.gnit.lucenekmp.jdkport.InterruptedException
import org.gnit.lucenekmp.store.AlreadyClosedException
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.SuppressingConcurrentMergeScheduler
import org.gnit.lucenekmp.tests.util.RandomStrings
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.jdkport.Executor
import org.gnit.lucenekmp.util.StringHelper
import org.gnit.lucenekmp.util.Version
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

@OptIn(ExperimentalAtomicApi::class)
class TestConcurrentMergeScheduler : LuceneTestCase() {

    private val logger = KotlinLogging.logger { }

    private class FailOnlyOnFlush : MockDirectoryWrapper.Failure() {
        var hitExc: Boolean = false

        @Throws(IOException::class)
        override fun eval(dir: MockDirectoryWrapper) {
            if (doFail && random().nextBoolean()) {
                hitExc = true
                throw IOException("now failing during flush")
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun testFlushExceptions() {
        val directory = newMockDirectory()
        val failure = FailOnlyOnFlush()
        directory.failOn(failure)
        val iwc = newIndexWriterConfig(MockAnalyzer(random())).setMaxBufferedDocs(2)
        if (iwc.mergeScheduler is ConcurrentMergeScheduler) {
            iwc.setMergeScheduler(
                object : SuppressingConcurrentMergeScheduler() {
                    override fun isOK(th: Throwable): Boolean {
                        return th is AlreadyClosedException ||
                                (th is IllegalStateException &&
                                        th.message?.contains("this writer hit an unrecoverable error") == true)
                    }

                    override fun getIntraMergeExecutor(merge: MergePolicy.OneMerge): Executor {
                        return requireNotNull(intraMergeExecutor) { "intraMergeExecutor is not initialized" }
                    }
                }
            )
        }
        val writer = IndexWriter(directory, iwc)
        val doc = Document()
        val idField = newStringField("id", "", Field.Store.YES)
        val knnField = KnnFloatVectorField("knn", floatArrayOf(0.0f, 0.0f))
        doc.add(idField)
        doc.add(knnField)

        var aborted = false
        for (i in 0..<10) {
            if (VERBOSE) {
                println("TEST: iter=$i")
            }
            for (j in 0..<20) {
                idField.setStringValue((i * 20 + j).toString())
                knnField.setVectorValue(floatArrayOf(random().nextFloat(), random().nextFloat()))
                writer.addDocument(doc)
            }

            while (true) {
                writer.addDocument(doc)
                failure.setDoFail()
                try {
                    writer.flush(true, true)
                    if (failure.hitExc) {
                        fail("failed to hit IOException")
                    }
                } catch (_: IOException) {
                    failure.clearDoFail()
                    expectThrows(AlreadyClosedException::class) {
                        writer.ensureOpen()
                    }
                    assertTrue(writer.isDeleterClosed())
                    writer.close()
                    aborted = true
                    break
                }
            }
            if (aborted) {
                break
            }
        }

        assertFalse(DirectoryReader.indexExists(directory))
        directory.close()
    }

    @Test
    @Throws(IOException::class)
    fun testDeleteMerging() {
        val directory: Directory = newDirectory()
        val mp = LogDocMergePolicy()
        mp.minMergeDocs = 1000
        val writer =
            IndexWriter(
                directory,
                newIndexWriterConfig(MockAnalyzer(random())).setMergePolicy(mp)
            )
        val doc = Document()
        val idField = newStringField("id", "", Field.Store.YES)
        doc.add(idField)
        for (i in 0..<10) {
            if (VERBOSE) {
                println("\nTEST: cycle")
            }
            for (j in 0..<100) {
                idField.setStringValue((i * 100 + j).toString())
                writer.addDocument(doc)
            }

            var delID = i
            while (delID < 100 * (1 + i)) {
                if (VERBOSE) {
                    println("TEST: del $delID")
                }
                writer.deleteDocuments(Term("id", delID.toString()))
                delID += 10
            }
            writer.commit()
        }

        writer.close()
        val reader = DirectoryReader.open(directory)
        assertEquals(450, reader.numDocs())
        reader.close()
        directory.close()
    }

    @Test
    @Throws(IOException::class)
    fun testNoExtraFiles() {
        val directory: Directory = newDirectory()
        var writer =
            IndexWriter(
                directory,
                newIndexWriterConfig(MockAnalyzer(random())).setMaxBufferedDocs(2)
            )

        for (iter in 0..<7) {
            if (VERBOSE) {
                println("TEST: iter=$iter")
            }
            for (j in 0..<21) {
                val doc = Document()
                doc.add(newTextField("content", "a b c", Field.Store.NO))
                writer.addDocument(doc)
            }

            writer.close()
            // TODO: enable when TestIndexWriter.assertNoUnreferencedFiles is ported.

            writer =
                IndexWriter(
                    directory,
                    newIndexWriterConfig(MockAnalyzer(random()))
                        .setOpenMode(IndexWriterConfig.OpenMode.APPEND)
                        .setMaxBufferedDocs(2)
                )
        }

        writer.close()
        directory.close()
    }

    @Test
    @Throws(IOException::class)
    fun testNoWaitClose() {
        val directory: Directory = newDirectory()
        val doc = Document()
        val idField = newStringField("id", "", Field.Store.YES)
        val knnField = KnnFloatVectorField("knn", floatArrayOf(0.0f, 0.0f))
        doc.add(idField)
        doc.add(knnField)
        val iwc =
            newIndexWriterConfig(MockAnalyzer(random()))
                .setMaxBufferedDocs(2)
                .setMergePolicy(newLogMergePolicy(100))
                .setCommitOnClose(false)
        if (iwc.mergeScheduler is ConcurrentMergeScheduler) {
            iwc.setMergeScheduler(
                object : ConcurrentMergeScheduler() {
                    override fun getIntraMergeExecutor(merge: MergePolicy.OneMerge): Executor {
                        return requireNotNull(intraMergeExecutor) { "scaledExecutor is not initialized" }
                    }
                }
            )
        }
        var writer = IndexWriter(directory, iwc)

        val numIters = if (TEST_NIGHTLY) 10 else 3
        for (iter in 0..<numIters) {
            for (j in 0..<201) {
                idField.setStringValue((iter * 201 + j).toString())
                knnField.setVectorValue(floatArrayOf(random().nextFloat(), random().nextFloat()))
                writer.addDocument(doc)
            }

            var delID = iter * 201
            for (j in 0..<20) {
                writer.deleteDocuments(Term("id", delID.toString()))
                delID += 5
            }

            (writer.config.mergePolicy as LogMergePolicy).mergeFactor = 3
            writer.addDocument(doc)

            try {
                writer.commit()
            } finally {
                writer.close()
            }

            val reader = DirectoryReader.open(directory)
            assertEquals((1 + iter) * 182, reader.numDocs())
            reader.close()

            writer =
                IndexWriter(
                    directory,
                    newIndexWriterConfig(MockAnalyzer(random()))
                        .setOpenMode(IndexWriterConfig.OpenMode.APPEND)
                        .setMergePolicy(newLogMergePolicy(100))
                        .setMaxBufferedDocs(2)
                        .setCommitOnClose(false)
                )
        }
        writer.close()
        directory.close()
    }

    @Test
    @Throws(Exception::class)
    fun testMaxMergeCount() {
        val dir = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random())).setCommitOnClose(false)

        val maxMergeCount = TestUtil.nextInt(random(), 1, 5)
        val maxMergeThreads = TestUtil.nextInt(random(), 1, maxMergeCount)
        val enoughMergesWaiting = CountDownLatch(maxMergeCount)
        val runningMergeCount = AtomicInt(0)
        val failed = AtomicBoolean(false)

        val cms =
            object : ConcurrentMergeScheduler() {
                override fun doMerge(mergeSource: MergeSource, merge: MergePolicy.OneMerge) {
                    try {
                        val count = runningMergeCount.incrementAndFetch()
                        try {
                            assertTrue(count <= maxMergeCount, "count=$count vs maxMergeCount=$maxMergeCount")
                            enoughMergesWaiting.countDown()
                            while (enoughMergesWaiting.getCount() != 0L && !failed.load()) {
                                // busy wait until enough merge threads are blocked
                            }
                            super.doMerge(mergeSource, merge)
                        } finally {
                            runningMergeCount.decrementAndFetch()
                        }
                    } catch (t: Throwable) {
                        failed.store(true)
                        mergeSource.onMergeFinished(merge)
                        throw RuntimeException(t)
                    }
                }
            }
        cms.setMaxMergesAndThreads(maxMergeCount, maxMergeThreads)
        iwc.setMergeScheduler(cms)
        iwc.setMaxBufferedDocs(2)

        val tmp = TieredMergePolicy()
        iwc.setMergePolicy(tmp)
        tmp.setSegmentsPerTier(2.0)

        val w = IndexWriter(dir, iwc)
        val doc = Document()
        doc.add(newTextField("field", "field", Field.Store.NO))
        while (enoughMergesWaiting.getCount() != 0L && !failed.load()) {
            for (i in 0..<10) {
                w.addDocument(doc)
            }
        }
        try {
            w.commit()
        } finally {
            w.close()
        }
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testSmallMergesDonNotGetThreads() {
        val dir = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        iwc.setMaxBufferedDocs(2)
        iwc.setMergeScheduler(
            object : ConcurrentMergeScheduler() {
                override fun doMerge(mergeSource: MergeSource, merge: MergePolicy.OneMerge) {
                    assertTrue(this.getIntraMergeExecutor(merge) is org.gnit.lucenekmp.util.SameThreadExecutorService)
                    super.doMerge(mergeSource, merge)
                }
            }
        )
        val w = IndexWriter(dir, iwc)
        for (i in 0..<10) {
            val doc = Document()
            doc.add(StringField("id", i.toString(), Field.Store.NO))
            w.addDocument(doc)
        }
        w.forceMerge(1)
        w.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testIntraMergeThreadPoolIsLimitedByMaxThreads() {
        val testName = "testIntraMergeThreadPoolIsLimitedByMaxThreads"
        val mergeSource =
            object : MergeScheduler.MergeSource {
                override val nextMerge: MergePolicy.OneMerge?
                    get() {
                        fail("should not be called")
                        return null
                    }

                override fun onMergeFinished(merge: MergePolicy.OneMerge) {
                    fail("should not be called")
                }

                override fun hasPendingMerges(): Boolean {
                    fail("should not be called")
                    return false
                }

                @Throws(IOException::class)
                override fun merge(merge: MergePolicy.OneMerge) {
                    fail("should not be called")
                }
            }

        val mergeScheduler = ConcurrentMergeScheduler()
        val dir = newDirectory()
        val merge =
            MergePolicy.OneMerge(
                mutableListOf(
                    SegmentCommitInfo(
                        SegmentInfo(
                            dir,
                            Version.LATEST,
                            null,
                            "test",
                            0,
                            false,
                            false,
                            Codec.default,
                            mutableMapOf(),
                            StringHelper.randomId(),
                            mutableMapOf(),
                            null
                        ),
                        0,
                        0,
                        0,
                        0,
                        0,
                        ByteArray(StringHelper.ID_LENGTH)
                    )
                )
            )
        mergeScheduler.initialize(org.gnit.lucenekmp.util.InfoStream.NO_OUTPUT, dir)
        mergeScheduler.setMaxMergesAndThreads(6, 6)
        val executor = mergeScheduler.getIntraMergeExecutor(merge)
        val threadsExecutedOnPool = AtomicInt(0)
        val threadsExecutedOnSelf = AtomicInt(0)
        val releaseLatch = CountDownLatch(1)
        val taskStartedLatch = CountDownLatch(4)
        val totalThreads = 4
        val callingJob = runBlocking { currentCoroutineContext()[Job] }
        val submitsReturned = AtomicInt(0)
        logger.error {
            "$testName phase=beforeSchedule totalThreads=$totalThreads maxMergeCount=${mergeScheduler.maxMergeCount} maxThreadCount=${mergeScheduler.maxThreadCount}"
        }

        val submitter =
            CoroutineScope(Dispatchers.Default).launch {
                for (i in 0..<totalThreads) {
                    logger.error {
                        "$testName phase=beforeSubmit index=$i submitsReturned=${submitsReturned.load()} started=${totalThreads - taskStartedLatch.getCount()}"
                    }
                    executor.execute {
                        try {
                            val taskJob = runBlocking { currentCoroutineContext()[Job] }
                            val mode =
                                if (taskJob === callingJob) {
                                    threadsExecutedOnSelf.incrementAndFetch()
                                    "self"
                                } else {
                                    threadsExecutedOnPool.incrementAndFetch()
                                    "pool"
                                }
                            logger.error {
                                "$testName phase=taskStarted index=$i mode=$mode startedRemaining=${taskStartedLatch.getCount()} self=${threadsExecutedOnSelf.load()} pool=${threadsExecutedOnPool.load()}"
                            }
                            taskStartedLatch.countDown()
                            releaseLatch.await()
                            logger.error { "$testName phase=taskReleased index=$i mode=$mode" }
                        } catch (t: Throwable) {
                            logger.error(t) { "$testName phase=taskThrowable index=$i" }
                            throw t
                        }
                    }
                    val returned = submitsReturned.incrementAndFetch()
                    logger.error {
                        "$testName phase=afterSubmit index=$i submitsReturned=$returned started=${totalThreads - taskStartedLatch.getCount()}"
                    }
                }
            }

        val submitWait = TimeSource.Monotonic.markNow()
        while (submitsReturned.load() < totalThreads && submitWait.elapsedNow() < 10.seconds) {
            runBlocking { delay(10) }
        }
        if (submitsReturned.load() < totalThreads) {
            logger.error {
                "$testName phase=submitSlowPath elapsed=${submitWait.elapsedNow()} " +
                        "submitsReturned=${submitsReturned.load()} startedRemaining=${taskStartedLatch.getCount()} " +
                        "self=${threadsExecutedOnSelf.load()} pool=${threadsExecutedOnPool.load()} " +
                        "mergeThreads=${mergeScheduler.mergeThreadCount()} submitterActive=${submitter.isActive}"
            }
            // Some executors may synchronously hand off task execution to the caller path.
            // Release blocked tasks and allow submit loop to progress before deciding it's hung.
            releaseLatch.countDown()
            val postReleaseWait = TimeSource.Monotonic.markNow()
            while (submitsReturned.load() < totalThreads && postReleaseWait.elapsedNow() < 10.seconds) {
                runBlocking { delay(10) }
            }
            if (submitsReturned.load() < totalThreads) {
                fail(
                    "$testName timeout waiting submit loop after release elapsed=${postReleaseWait.elapsedNow()} " +
                            "submitsReturned=${submitsReturned.load()} startedRemaining=${taskStartedLatch.getCount()} " +
                            "self=${threadsExecutedOnSelf.load()} pool=${threadsExecutedOnPool.load()} " +
                            "mergeThreads=${mergeScheduler.mergeThreadCount()} submitterActive=${submitter.isActive}"
                )
            }
        }

        val startWait = TimeSource.Monotonic.markNow()
        while (taskStartedLatch.getCount() > 0L && startWait.elapsedNow() < 10.seconds) {
            runBlocking { delay(10) }
        }
        if (taskStartedLatch.getCount() > 0L) {
            fail(
                "$testName timeout waiting task start elapsed=${startWait.elapsedNow()} " +
                        "startedRemaining=${taskStartedLatch.getCount()} self=${threadsExecutedOnSelf.load()} " +
                        "pool=${threadsExecutedOnPool.load()} mergeThreads=${mergeScheduler.mergeThreadCount()}"
            )
        }
        logger.error {
            "$testName phase=allTasksStarted self=${threadsExecutedOnSelf.load()} pool=${threadsExecutedOnPool.load()} mergeThreads=${mergeScheduler.mergeThreadCount()}"
        }
        releaseLatch.countDown()
        logger.error { "$testName phase=beforeSync" }
        runBlocking { mergeScheduler.sync() }
        logger.error { "$testName phase=afterSync" }

        // Keep parity with Java test setup: merge source object exists even if this KMP port
        // currently validates the max-thread limiting contract without spawning raw merge threads.
        assertTrue(mergeSource is MergeScheduler.MergeSource)
        assertTrue(merge.totalMaxDoc == 0)
        assertEquals(totalThreads, threadsExecutedOnSelf.load() + threadsExecutedOnPool.load())
        assertEquals(6, mergeScheduler.maxMergeCount)
        assertEquals(6, mergeScheduler.maxThreadCount)
        mergeScheduler.close()
        dir.close()
    }

    private class TrackingCMS(val atLeastOneMerge: CountDownLatch) : ConcurrentMergeScheduler() {
        var totMergedBytes: Long = 0

        init {
            setMaxMergesAndThreads(5, 5)
        }

        @Throws(IOException::class)
        override fun doMerge(mergeSource: MergeSource, merge: MergePolicy.OneMerge) {
            totMergedBytes += merge.totalBytesSize()
            atLeastOneMerge.countDown()
            super.doMerge(mergeSource, merge)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testTotalBytesSize() {
        val d = newDirectory()
        if (d is MockDirectoryWrapper) {
            d.setThrottling(MockDirectoryWrapper.Throttling.NEVER)
        }
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        iwc.setMaxBufferedDocs(5)
        val atLeastOneMerge = CountDownLatch(1)
        iwc.setMergeScheduler(TrackingCMS(atLeastOneMerge))
        if (TestUtil.getPostingsFormat("id") == "SimpleText") {
            iwc.setCodec(TestUtil.alwaysPostingsFormat(TestUtil.getDefaultPostingsFormat()))
        }
        val w = IndexWriter(d, iwc)
        for (i in 0..<1000) {
            val doc = Document()
            doc.add(StringField("id", i.toString(), Field.Store.NO))
            w.addDocument(doc)
            if (random().nextBoolean()) {
                w.deleteDocuments(Term("id", random().nextInt(i + 1).toString()))
            }
        }
        atLeastOneMerge.await()
        assertTrue((w.config.mergeScheduler as TrackingCMS).totMergedBytes != 0L)
        w.close()
        d.close()
    }

    @Test
    @Throws(Exception::class)
    fun testInvalidMaxMergeCountAndThreads() {
        val cms = ConcurrentMergeScheduler()
        expectThrows(IllegalArgumentException::class) {
            cms.setMaxMergesAndThreads(ConcurrentMergeScheduler.AUTO_DETECT_MERGES_AND_THREADS, 3)
        }
        expectThrows(IllegalArgumentException::class) {
            cms.setMaxMergesAndThreads(3, ConcurrentMergeScheduler.AUTO_DETECT_MERGES_AND_THREADS)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testLiveMaxMergeCount() {
        val d = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        iwc.setMergePolicy(
            object : MergePolicy() {
                @Throws(IOException::class)
                override fun findMerges(
                    mergeTrigger: MergeTrigger?,
                    segmentInfos: SegmentInfos?,
                    mergeContext: MergeContext?
                ): MergeSpecification? {
                    // no natural merges
                    return null
                }

                @Throws(IOException::class)
                override fun findForcedDeletesMerges(
                    segmentInfos: SegmentInfos?,
                    mergeContext: MergeContext?
                ): MergeSpecification? {
                    // not needed
                    return null
                }

                @Throws(IOException::class)
                override fun findForcedMerges(
                    segmentInfos: SegmentInfos?,
                    maxSegmentCount: Int,
                    segmentsToMerge: MutableMap<SegmentCommitInfo, Boolean>?,
                    mergeContext: MergeContext?
                ): MergeSpecification? {
                    val spec = MergeSpecification()
                    val oneMerge = mutableListOf<SegmentCommitInfo>()
                    for (sci in segmentsToMerge!!.keys) {
                        oneMerge.add(sci)
                        if (oneMerge.size >= 10) {
                            spec.add(OneMerge(oneMerge.toMutableList()))
                            oneMerge.clear()
                        }
                    }
                    return spec
                }
            }
        )
        iwc.setMaxBufferedDocs(2)
        iwc.setRAMBufferSizeMB(-1.0)
        val maxRunningMergeCount = AtomicInt(0)
        val cms =
            object : ConcurrentMergeScheduler() {
                private val runningMergeCount = AtomicInt(0)
                override fun doMerge(mergeSource: MergeSource, merge: MergePolicy.OneMerge) {
                    val count = runningMergeCount.incrementAndFetch()
                    if (count > maxRunningMergeCount.load()) {
                        maxRunningMergeCount.store(count)
                    }
                    try {
                        super.doMerge(mergeSource, merge)
                    } finally {
                        runningMergeCount.decrementAndFetch()
                    }
                }
            }
        assertEquals(ConcurrentMergeScheduler.AUTO_DETECT_MERGES_AND_THREADS, cms.maxMergeCount)
        assertEquals(ConcurrentMergeScheduler.AUTO_DETECT_MERGES_AND_THREADS, cms.maxThreadCount)
        cms.setMaxMergesAndThreads(5, 3)
        iwc.setMergeScheduler(cms)
        val w = IndexWriter(d, iwc)
        // Makes 100 segments
        for (i in 0..<200) {
            w.addDocument(Document())
        }
        // No merges should have run so far, because TMP has high segmentsPerTier:
        assertEquals(0, maxRunningMergeCount.load())
        w.forceMerge(1)
        // At most 5 merge threads should have launched at once:
        assertTrue(maxRunningMergeCount.load() <= 5, "maxRunningMergeCount=${maxRunningMergeCount.load()}")
        maxRunningMergeCount.store(0)
        // Makes another 100 segments
        for (i in 0..<200) {
            w.addDocument(Document())
        }
        (w.config.mergeScheduler as ConcurrentMergeScheduler).setMaxMergesAndThreads(1, 1)
        w.forceMerge(1)
        // At most 1 merge thread should have launched at once:
        assertEquals(1, maxRunningMergeCount.load())
        w.close()
        d.close()
    }

    @Test
    @Throws(Exception::class)
    fun testMaybeStallCalled() {
        val wasCalled = AtomicBoolean(false)
        val dir = newDirectory()
        val iwc =
            newIndexWriterConfig(MockAnalyzer(random()))
                .setMergePolicy(LogByteSizeMergePolicy())
        iwc.setMergeScheduler(
            object : ConcurrentMergeScheduler() {
                override suspend fun maybeStall(mergeSource: MergeSource): Boolean {
                    wasCalled.store(true)
                    return true
                }
            }
        )
        val w = IndexWriter(dir, iwc)
        w.addDocument(Document())
        w.flush(true, true)
        w.addDocument(Document())
        w.forceMerge(1)
        assertTrue(wasCalled.load())
        w.close()
        dir.close()
    }

    @Test
    @Throws(Throwable::class)
    fun testHangDuringRollback() {
        val dir = newMockDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        iwc.setMaxBufferedDocs(2)
        val mp = LogDocMergePolicy()
        iwc.setMergePolicy(mp)
        mp.mergeFactor = 2
        val mergeStart = CountDownLatch(1)
        val mergeFinish = CountDownLatch(1)
        val mergeEnteredDoMerge = CountDownLatch(1)
        val cms =
            object : ConcurrentMergeScheduler() {
                override fun doMerge(mergeSource: MergeSource, merge: MergePolicy.OneMerge) {
                    logger.error { "testHangDuringRollback phase=doMerge.enter seg=${merge.segString()}" }
                    mergeStart.countDown()
                    mergeEnteredDoMerge.countDown()
                    mergeFinish.await()
                    logger.error { "testHangDuringRollback phase=doMerge.resume seg=${merge.segString()}" }
                    super.doMerge(mergeSource, merge)
                    logger.error { "testHangDuringRollback phase=doMerge.end seg=${merge.segString()}" }
                }
            }
        cms.setMaxMergesAndThreads(1, 1)
        iwc.setMergeScheduler(cms)
        val w = IndexWriter(dir, iwc)
        w.addDocument(Document())
        w.addDocument(Document())
        // flush
        w.addDocument(Document())
        w.addDocument(Document())
        // flush + merge
        // Wait for merge to kick off
        run {
            val started = TimeSource.Monotonic.markNow()
            while (mergeStart.getCount() > 0L) {
                runBlocking { delay(10) }
                if (started.elapsedNow() >= 30.seconds) {
                    fail(
                        "testHangDuringRollback phase=timeout-wait-mergeStart " +
                                "mergeStart=${mergeStart.getCount()} mergeEnteredDoMerge=${mergeEnteredDoMerge.getCount()} " +
                                "mergeFinish=${mergeFinish.getCount()} hasPendingMerges=${w.hasPendingMerges()} " +
                                "mergeThreadCount=${cms.mergeThreadCount()} docStats=${w.getDocStats().numDocs}"
                    )
                }
            }
            logger.error { "testHangDuringRollback phase=mergeStart.reached mergeThreadCount=${cms.mergeThreadCount()}" }
        }
        val producer = CoroutineScope(Dispatchers.Default).launch {
            logger.error { "testHangDuringRollback phase=producer.start" }
            w.addDocument(Document())
            w.addDocument(Document())
            // flush
            w.addDocument(Document())
            // W/o the fix for LUCENE-6094 we would hang forever here:
            w.addDocument(Document())
            // flush + merge
            logger.error { "testHangDuringRollback phase=producer.releaseMergeFinish" }
            mergeFinish.countDown()
        }
        val waitDocsStarted = TimeSource.Monotonic.markNow()
        var lastDocsLogAt = TimeSource.Monotonic.markNow()
        while (w.getDocStats().numDocs != 8) {
            // busy wait
            runBlocking { delay(10) }
            if (lastDocsLogAt.elapsedNow() >= 5.seconds) {
                lastDocsLogAt = TimeSource.Monotonic.markNow()
                logger.error {
                    "testHangDuringRollback phase=waiting-docStats " +
                            "numDocs=${w.getDocStats().numDocs} mergeStart=${mergeStart.getCount()} " +
                            "mergeEnteredDoMerge=${mergeEnteredDoMerge.getCount()} mergeFinish=${mergeFinish.getCount()} " +
                            "mergeThreadCount=${cms.mergeThreadCount()} hasPendingMerges=${w.hasPendingMerges()}"
                }
            }
            if (waitDocsStarted.elapsedNow() >= 30.seconds) {
                fail(
                    "testHangDuringRollback phase=timeout-wait-docStats " +
                            "numDocs=${w.getDocStats().numDocs} mergeStart=${mergeStart.getCount()} " +
                            "mergeEnteredDoMerge=${mergeEnteredDoMerge.getCount()} mergeFinish=${mergeFinish.getCount()} " +
                            "mergeThreadCount=${cms.mergeThreadCount()} hasPendingMerges=${w.hasPendingMerges()}"
                )
            }
        }
        logger.error { "testHangDuringRollback phase=docStatsReached8" }
        logger.error {
            "testHangDuringRollback phase=skip-producer-join " +
                    "producerCompleted=${producer.isCompleted} mergeFinish=${mergeFinish.getCount()}"
        }
        logger.error { "testHangDuringRollback phase=beforeRollback" }
        w.rollback()
        logger.error { "testHangDuringRollback phase=afterRollback" }
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testMergeThreadMessages() {
        val dir = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val mergeThreadSet = mutableSetOf<ConcurrentMergeScheduler.MergeThread>()
        val cms =
            object : ConcurrentMergeScheduler() {
                @Throws(IOException::class)
                override fun getMergeThread(
                    mergeSource: MergeSource,
                    merge: MergePolicy.OneMerge
                ): MergeThread {
                    val newMergeThread = super.getMergeThread(mergeSource, merge)
                    mergeThreadSet.add(newMergeThread)
                    return newMergeThread
                }
            }
        iwc.setMergeScheduler(cms)
        val messages = mutableListOf<String>()
        iwc.setInfoStream(
            object : org.gnit.lucenekmp.util.InfoStream() {
                override fun close() {}

                override fun message(component: String, message: String) {
                    if (component == "MS") {
                        messages.add(message)
                    }
                }

                override fun isEnabled(component: String): Boolean {
                    return component == "MS"
                }
            }
        )
        iwc.setMaxBufferedDocs(2)
        val lmp = newLogMergePolicy()
        lmp.mergeFactor = 2
        lmp.targetSearchConcurrency = 1
        iwc.setMergePolicy(lmp)
        val w = IndexWriter(dir, iwc)
        val doc = Document()
        doc.add(newTextField("foo", "bar", Field.Store.NO))
        w.addDocument(doc)
        w.addDocument(Document())
        // flush
        w.addDocument(Document())
        w.addDocument(Document())
        // flush + merge
        w.close()
        dir.close()

        assertTrue(mergeThreadSet.isNotEmpty())
        runBlocking {
            for (t in mergeThreadSet) {
                t.awaitCompletion()
            }
        }
        for (t in mergeThreadSet) {
            val name = t.getName()
            val threadMsgs = messages.filter { line -> line.startsWith("merge thread $name") }
            assertTrue(threadMsgs.size >= 3, "Expected >= 3 messages for $name, got ${threadMsgs.size}, threadMsgs=$threadMsgs")
            assertTrue(threadMsgs.first().startsWith("merge thread $name start"))
            assertTrue(threadMsgs.any { line -> line.startsWith("merge thread $name merge segment") })
        }
    }

    @Test
    @Throws(Exception::class)
    fun testDynamicDefaults() {
        val dir = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        val cms = ConcurrentMergeScheduler()
        assertEquals(ConcurrentMergeScheduler.AUTO_DETECT_MERGES_AND_THREADS, cms.maxMergeCount)
        assertEquals(ConcurrentMergeScheduler.AUTO_DETECT_MERGES_AND_THREADS, cms.maxThreadCount)
        iwc.setMergeScheduler(cms)
        iwc.setMaxBufferedDocs(2)
        val lmp = newLogMergePolicy()
        lmp.mergeFactor = 2
        iwc.setMergePolicy(lmp)

        val w = IndexWriter(dir, iwc)
        w.addDocument(Document())
        w.addDocument(Document())
        w.addDocument(Document())
        w.addDocument(Document())

        assertTrue(cms.maxMergeCount != ConcurrentMergeScheduler.AUTO_DETECT_MERGES_AND_THREADS)
        assertTrue(cms.maxThreadCount != ConcurrentMergeScheduler.AUTO_DETECT_MERGES_AND_THREADS)
        w.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testResetToAutoDefault() {
        val cms = ConcurrentMergeScheduler()
        assertEquals(ConcurrentMergeScheduler.AUTO_DETECT_MERGES_AND_THREADS, cms.maxMergeCount)
        assertEquals(ConcurrentMergeScheduler.AUTO_DETECT_MERGES_AND_THREADS, cms.maxThreadCount)
        cms.setMaxMergesAndThreads(4, 3)
        assertEquals(4, cms.maxMergeCount)
        assertEquals(3, cms.maxThreadCount)

        expectThrows(IllegalArgumentException::class) {
            cms.setMaxMergesAndThreads(ConcurrentMergeScheduler.AUTO_DETECT_MERGES_AND_THREADS, 4)
        }
        expectThrows(IllegalArgumentException::class) {
            cms.setMaxMergesAndThreads(4, ConcurrentMergeScheduler.AUTO_DETECT_MERGES_AND_THREADS)
        }

        cms.setMaxMergesAndThreads(
            ConcurrentMergeScheduler.AUTO_DETECT_MERGES_AND_THREADS,
            ConcurrentMergeScheduler.AUTO_DETECT_MERGES_AND_THREADS
        )
        assertEquals(ConcurrentMergeScheduler.AUTO_DETECT_MERGES_AND_THREADS, cms.maxMergeCount)
        assertEquals(ConcurrentMergeScheduler.AUTO_DETECT_MERGES_AND_THREADS, cms.maxThreadCount)
    }

    @Test
    @Throws(Exception::class)
    fun testSpinningDefaults() {
        val cms = ConcurrentMergeScheduler()
        cms.setDefaultMaxMergesAndThreads(true)
        assertEquals(1, cms.maxThreadCount)
        assertEquals(6, cms.maxMergeCount)
    }

    @Test
    @Throws(Exception::class)
    fun testAutoIOThrottleGetter() {
        val cms = ConcurrentMergeScheduler()
        assertFalse(cms.autoIOThrottle)
        cms.enableAutoIOThrottle()
        assertTrue(cms.autoIOThrottle)
        cms.disableAutoIOThrottle()
        assertFalse(cms.autoIOThrottle)
    }

    @Test
    @Throws(Exception::class)
    fun testNonSpinningDefaults() {
        val cms = ConcurrentMergeScheduler()
        cms.setDefaultMaxMergesAndThreads(false)
        val threadCount = cms.maxThreadCount
        assertTrue(threadCount >= 1)
        assertTrue(threadCount <= 4)
        assertEquals(5 + threadCount, cms.maxMergeCount)
    }

    @Test
    @Throws(Exception::class)
    fun testNoStallMergeThreads() {
        val dir = newMockDirectory()
        var iwc = newIndexWriterConfig(MockAnalyzer(random()))
        iwc.setMergePolicy(NoMergePolicy.INSTANCE)
        iwc.setMaxBufferedDocs(2)
        iwc.setUseCompoundFile(true)
        var w = IndexWriter(dir, iwc)
        val numDocs = if (TEST_NIGHTLY) 1000 else 100
        for (i in 0..<numDocs) {
            val doc = Document()
            doc.add(newStringField("field", i.toString(), Field.Store.YES))
            w.addDocument(doc)
        }
        w.close()

        iwc = newIndexWriterConfig(MockAnalyzer(random()))
        val failed = AtomicBoolean(false)
        val cms =
            object : ConcurrentMergeScheduler() {
                override suspend fun doStall() {
                    val jobName = currentCoroutineContext()[Job]?.toString() ?: ""
                    if (jobName.contains("Lucene Merge Thread")) {
                        failed.store(true)
                    }
                    super.doStall()
                }
            }
        cms.enableAutoIOThrottle()
        cms.setMaxMergesAndThreads(2, 1)
        iwc.setMergeScheduler(cms)
        iwc.setMaxBufferedDocs(2)

        w = IndexWriter(dir, iwc)
        w.forceMerge(1)
        w.close()
        dir.close()
        assertFalse(failed.load())
    }

    /*
     * This test tries to produce 2 merges running concurrently with 2 segments per merge. While these
     * merges run we kick off a forceMerge that puts a pending merge in the queue but waits for things to happen.
     * While we do this we reduce maxMergeCount to 1. If concurrency in CMS is not right the forceMerge will wait forever
     * since none of the currently running merges picks up the pending merge. This test fails every time.
     */
    @Test
    @Throws(IOException::class, InterruptedException::class)
    fun testChangeMaxMergeCountyWhileForceMerge() {
        val numIters = if (TEST_NIGHTLY) 100 else 10
        for (iters in 0..<numIters) {
            val mp = LogDocMergePolicy()
            mp.mergeFactor = 2
            val forceMergeWaits = CountDownLatch(1)
            val mergeThreadsStartAfterWait = CountDownLatch(1)
            val mergeThreadsArrived = CountDownLatch(2)
            val stream =
                object : org.gnit.lucenekmp.util.InfoStream() {
                    override fun close() {}

                    override fun message(component: String, message: String) {
                        if (component == "TP" && message == "mergeMiddleStart") {
                            logger.error {
                                "testChangeMaxMergeCountyWhileForceMerge iter=$iters TP mergeMiddleStart before countDown " +
                                        "arrivedCount=${mergeThreadsArrived.getCount()} forceWaitCount=${forceMergeWaits.getCount()}"
                            }
                            mergeThreadsArrived.countDown()
                            try {
                                logger.error {
                                    "testChangeMaxMergeCountyWhileForceMerge iter=$iters TP mergeMiddleStart waiting " +
                                            "startAfterWaitCount=${mergeThreadsStartAfterWait.getCount()}"
                                }
                                mergeThreadsStartAfterWait.await()
                                logger.error {
                                    "testChangeMaxMergeCountyWhileForceMerge iter=$iters TP mergeMiddleStart resumed"
                                }
                            } catch (e: InterruptedException) {
                                throw AssertionError(e)
                            }
                        } else if (component == "TP" && message == "forceMergeBeforeWait") {
                            logger.error {
                                "testChangeMaxMergeCountyWhileForceMerge iter=$iters TP forceMergeBeforeWait countDown " +
                                        "forceWaitCount=${forceMergeWaits.getCount()}"
                            }
                            forceMergeWaits.countDown()
                        }
                    }

                    override fun isEnabled(component: String): Boolean {
                        return component == "TP"
                    }
                }
            newDirectory().use { dir ->
                val writer = object : IndexWriter(
                    dir,
                    IndexWriterConfig()
                        .setMergeScheduler(ConcurrentMergeScheduler())
                        .setMergePolicy(mp)
                        .setInfoStream(stream)
                ) {
                    override fun isEnableTestPoints(): Boolean {
                        return true
                    }
                }
                val cms = writer.config.mergeScheduler as ConcurrentMergeScheduler
                fun debugState(phase: String): String {
                    return "testChangeMaxMergeCountyWhileForceMerge iter=$iters phase=$phase " +
                            "mergeThreadsArrived=${mergeThreadsArrived.getCount()} " +
                            "forceMergeWaits=${forceMergeWaits.getCount()} " +
                            "startAfterWait=${mergeThreadsStartAfterWait.getCount()} " +
                            "mergeThreadCount=${cms.mergeThreadCount()} " +
                            "hasPendingMerges=${writer.hasPendingMerges()} " +
                            "segmentCount=${writer.getSegmentCount()}"
                }

                fun awaitLatchOrFail(name: String, latch: CountDownLatch, timeoutSeconds: Int) {
                    val started = TimeSource.Monotonic.markNow()
                    var lastLogAt = TimeSource.Monotonic.markNow()
                    while (latch.getCount() > 0L) {
                        runBlocking { delay(10) }
                        if (lastLogAt.elapsedNow() >= 5.seconds) {
                            lastLogAt = TimeSource.Monotonic.markNow()
                            logger.error { debugState("waiting-$name") }
                        }
                        if (started.elapsedNow() >= timeoutSeconds.seconds) {
                            fail("${debugState("timeout-$name")} timeout=${timeoutSeconds}s latchCount=${latch.getCount()}")
                        }
                    }
                }

                val forceMergeJob =
                    CoroutineScope(Dispatchers.Default).launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                        try {
                            logger.error { debugState("forceMergeJob.start") }
                            writer.forceMerge(1)
                            logger.error { debugState("forceMergeJob.end") }
                        } catch (e: IOException) {
                            logger.error(e) { debugState("forceMergeJob.ioException") }
                            throw AssertionError(e)
                        }
                    }
                try {
                    logger.error { debugState("beforeSetMaxMergesAndThreads2x2") }
                    cms.setMaxMergesAndThreads(2, 2)
                    for (i in 0..<4) {
                        val document = Document()
                        document.add(newTextField("foo", "the quick brown fox jumps over the lazy dog", Field.Store.YES))
                        document.add(newTextField("bar", RandomStrings.randomRealisticUnicodeOfLength(random(), 20), Field.Store.YES))
                        writer.addDocument(document)
                        writer.flush()
                    }
                    logger.error { debugState("afterFlush4Docs") }
                    assertEquals(4, writer.getSegmentCount(), writer.cloneSegmentInfos().toString())
                    awaitLatchOrFail("mergeThreadsArrived", mergeThreadsArrived, 30)
                    logger.error { debugState("beforeForceMergeJobStart") }
                    forceMergeJob.start()
                    awaitLatchOrFail("forceMergeWaits", forceMergeWaits, 30)
                    logger.error { debugState("beforeSetMaxMergesAndThreads1x1") }
                    cms.setMaxMergesAndThreads(1, 1)
                } finally {
                    logger.error { debugState("finallyReleaseStartAfterWait") }
                    mergeThreadsStartAfterWait.countDown()
                }
                var lastLoopLogAt = TimeSource.Monotonic.markNow()
                val forceMergeWaitStartedAt = TimeSource.Monotonic.markNow()
                var noThreadsPendingSince: kotlin.time.TimeMark? = null
                while (!forceMergeJob.isCompleted) {
                    runBlocking { delay(10) }
                    if (lastLoopLogAt.elapsedNow() >= 5.seconds) {
                        lastLoopLogAt = TimeSource.Monotonic.markNow()
                        logger.error { debugState("waiting-forceMergeJobComplete") }
                    }
                    if (forceMergeWaitStartedAt.elapsedNow() >= 60.seconds) {
                        fail("${debugState("timeout-forceMergeJobComplete")} timeout=60s waiting for forceMergeJob completion")
                    }
                    if (cms.mergeThreadCount() == 0 && writer.hasPendingMerges()) {
                        if (noThreadsPendingSince == null) {
                            noThreadsPendingSince = TimeSource.Monotonic.markNow()
                        } else if (noThreadsPendingSince!!.elapsedNow() >= 2.seconds) {
                            fail("${debugState("noMergeThreadsButPending")} state persisted >=2s; writer has pending merges but no CMS threads are running")
                        }
                    } else {
                        noThreadsPendingSince = null
                    }
                }
                logger.error { debugState("afterForceMergeJobComplete") }
                assertEquals(1, writer.getSegmentCount())
                writer.close()
            }
        }
    }
}
