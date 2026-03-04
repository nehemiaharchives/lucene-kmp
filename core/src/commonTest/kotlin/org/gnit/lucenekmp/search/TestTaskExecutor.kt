package org.gnit.lucenekmp.search

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.runBlocking
import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.jdkport.Callable
import org.gnit.lucenekmp.jdkport.CountDownLatch
import org.gnit.lucenekmp.jdkport.Executor
import org.gnit.lucenekmp.jdkport.ExecutorService
import org.gnit.lucenekmp.jdkport.Executors
import org.gnit.lucenekmp.jdkport.LinkedBlockingQueue
import org.gnit.lucenekmp.jdkport.ThreadPoolExecutor
import org.gnit.lucenekmp.jdkport.TimeUnit
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.NamedThreadFactory
import org.gnit.lucenekmp.util.configureTestLogging
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.TimeSource

class TestTaskExecutor : LuceneTestCase() {

    init {
        configureTestLogging()
    }

    private val logger = KotlinLogging.logger {  }

    private fun createExecutor(): ExecutorService =
        Executors.newFixedThreadPool(
            1,
            NamedThreadFactory(TestTaskExecutor::class.simpleName!!)
        )

    @Test
    fun testUnwrapIOExceptionFromExecutionException() {
        val executorService = createExecutor()
        val taskExecutor = TaskExecutor(executorService)
        try {
            val ioException =
                expectThrows(IOException::class) {
                    runBlocking {
                        taskExecutor.invokeAll(
                            mutableListOf(
                                Callable {
                                    throw IOException("io exception")
                                }
                            )
                        )
                    }
                }
            assertEquals("io exception", ioException.message)
        } finally {
            TestUtil.shutdownExecutorService(executorService)
        }
    }

    @Test
    fun testUnwrapRuntimeExceptionFromExecutionException() {
        val executorService = createExecutor()
        val taskExecutor = TaskExecutor(executorService)
        try {
            val runtimeException =
                expectThrows(RuntimeException::class) {
                    runBlocking {
                        taskExecutor.invokeAll(
                            mutableListOf(
                                Callable {
                                    throw RuntimeException("runtime")
                                }
                            )
                        )
                    }
                }
            assertEquals("runtime", runtimeException.message)
            assertNull(runtimeException.cause)
        } finally {
            TestUtil.shutdownExecutorService(executorService)
        }
    }

    @Test
    fun testUnwrapErrorFromExecutionException() {
        val executorService = createExecutor()
        val taskExecutor = TaskExecutor(executorService)
        try {
            val outOfMemoryError =
                expectThrows(Error::class) {
                    runBlocking {
                        taskExecutor.invokeAll(
                            mutableListOf<Callable<Any?>>(
                                Callable {
                                    throw Error("oom")
                                }
                            )
                        )
                    }
                }
            assertEquals("oom", outOfMemoryError.message)
            assertNull(outOfMemoryError.cause)
        } finally {
            TestUtil.shutdownExecutorService(executorService)
        }
    }

    @Test
    fun testUnwrappedExceptions() {
        val executorService = createExecutor()
        val taskExecutor = TaskExecutor(executorService)
        try {
            val runtimeException =
                expectThrows(RuntimeException::class) {
                    runBlocking {
                        taskExecutor.invokeAll(
                            mutableListOf(
                                Callable {
                                    throw Exception("exc")
                                }
                            )
                        )
                    }
                }
            assertEquals("exc", runtimeException.cause!!.message)
        } finally {
            TestUtil.shutdownExecutorService(executorService)
        }
    }

    @Test
    fun testInvokeAllFromTaskDoesNotDeadlockSameSearcher() {
        val executorService = createExecutor()
        try {
            doTestInvokeAllFromTaskDoesNotDeadlockSameSearcher(executorService)
            doTestInvokeAllFromTaskDoesNotDeadlockSameSearcher(Runnable::run)
            runBlocking {
                executorService.submit(
                    Callable {
                        doTestInvokeAllFromTaskDoesNotDeadlockSameSearcher(executorService)
                        null
                    }
                ).get()
            }
        } finally {
            TestUtil.shutdownExecutorService(executorService)
        }
    }

    @Test
    fun testInvokeAllFromTaskDoesNotDeadlockMultipleSearchers() {
        val executorService = createExecutor()
        try {
            doTestInvokeAllFromTaskDoesNotDeadlockMultipleSearchers(executorService)
            doTestInvokeAllFromTaskDoesNotDeadlockMultipleSearchers(Runnable::run)
            runBlocking {
                executorService.submit(
                    Callable {
                        doTestInvokeAllFromTaskDoesNotDeadlockMultipleSearchers(executorService)
                        null
                    }
                ).get()
            }
        } finally {
            TestUtil.shutdownExecutorService(executorService)
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    @Test
    fun testInvokeAllDoesNotLeaveTasksBehind() {
        val tasksStarted = AtomicInt(0)
        val taskExecutor =
            TaskExecutor(
                Executor { command ->
                    tasksStarted.fetchAndIncrement()
                    command.run()
                }
            )
        val tasksExecuted = AtomicInt(0)
        val callables = mutableListOf<Callable<Any?>>()
        callables.add(
            Callable {
                tasksExecuted.fetchAndIncrement()
                throw RuntimeException()
            }
        )
        val tasksWithNormalExit = 99
        for (i in 0..<tasksWithNormalExit) {
            callables.add(
                Callable {
                    throw AssertionError(
                        "must not be called since the first task failing cancels all subsequent tasks"
                    )
                }
            )
        }
        expectThrows(RuntimeException::class) { runBlocking { taskExecutor.invokeAll(callables) } }
        assertEquals(1, tasksExecuted.load())
        // the callables are technically all run, but the cancelled ones will be no-op
        assertEquals(tasksWithNormalExit, tasksStarted.load())
    }

    /**
     * Ensures that all invokeAll catches all exceptions thrown by Callables and adds subsequent ones
     * as suppressed exceptions to the first one caught.
     */
    @Test
    fun testInvokeAllCatchesMultipleExceptions() {
        // this test requires multiple threads, while all the other tests in this class rely on a single
        // threaded executor
        val multiThreadedExecutor =
            Executors.newFixedThreadPool(
                2,
                NamedThreadFactory(TestTaskExecutor::class.simpleName!!)
            )
        try {
            val taskExecutor = TaskExecutor(multiThreadedExecutor)
            val callables = mutableListOf<Callable<Any?>>()
            // if we have multiple threads, make sure both are started before an exception is thrown,
            // otherwise there may or may not be a suppressed exception
            val latchA = CountDownLatch(1)
            val latchB = CountDownLatch(1)
            callables.add(
                Callable {
                    latchA.countDown()
                    latchB.await()
                    throw RuntimeException("exception A")
                }
            )
            callables.add(
                Callable {
                    latchB.countDown()
                    latchA.await()
                    throw IllegalStateException("exception B")
                }
            )

            val exc =
                expectThrows(RuntimeException::class) {
                    runBlocking { taskExecutor.invokeAll(callables) }
                }
            val suppressed = exc.suppressedExceptions

            assertEquals(1, suppressed.size)
            if (exc.message == "exception A") {
                assertEquals("exception B", suppressed[0].message)
            } else {
                assertEquals("exception A", suppressed[0].message)
                assertEquals("exception B", exc.message)
            }
        } finally {
            TestUtil.shutdownExecutorService(multiThreadedExecutor)
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    @Test
    fun testCancelTasksOnException() {
        val taskExecutor = TaskExecutor(Runnable::run)
        val numTasks = random().nextInt(10, 50)
        val throwingTask = random().nextInt(numTasks)
        val error = random().nextBoolean()
        val tasks = ArrayList<Callable<Any?>>(numTasks)
        val executedTasks = AtomicInt(0)
        for (i in 0..<numTasks) {
            val index = i
            tasks.add(
                Callable {
                    if (index == throwingTask) {
                        if (error) {
                            throw Error()
                        } else {
                            throw RuntimeException()
                        }
                    }
                    if (index > throwingTask) {
                        // with a single thread we are sure that the last task to run is the one that throws,
                        // following ones must not run
                        throw AssertionError("task should not have started")
                    }
                    executedTasks.fetchAndIncrement()
                    null
                }
            )
        }
        val throwable: Throwable =
            if (error) {
                expectThrows(Error::class) { runBlocking { taskExecutor.invokeAll(tasks) } }
            } else {
                expectThrows(RuntimeException::class) { runBlocking { taskExecutor.invokeAll(tasks) } }
            }
        assertEquals(0, throwable.suppressedExceptions.size)
        assertEquals(throwingTask, executedTasks.load())
    }

    @OptIn(ExperimentalAtomicApi::class)
    @Test
    fun testTaskRejectionDoesNotFailExecution() {
        ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue(1)).use { threadPoolExecutor ->
            val taskCount = 1000 // enough tasks to cause queuing and rejections on the executor
            val callables = ArrayList<Callable<Any?>>(taskCount)
            val executedTasks = AtomicInt(0)
            for (i in 0..<taskCount) {
                callables.add(
                    Callable {
                        executedTasks.fetchAndIncrement()
                        null
                    }
                )
            }
            val taskExecutor = TaskExecutor(threadPoolExecutor)
            val res = runBlocking { taskExecutor.invokeAll(callables) }
            assertEquals(taskCount, res.size)
            assertEquals(taskCount, executedTasks.load())
        }
    }

    @Test
    fun testInvokeAllSingleSliceNativePerfProbe() {
        val executorService = createExecutor()
        try {
            val taskExecutor = TaskExecutor(executorService)
            val timeSource = TimeSource.Monotonic

            val iterations = 20_000 // TODO reduced iterations = 200000 to 20000 for dev speed
            val leafSlices = intArrayOf(7) // one slice -> same shape as the single-slice search path
            val collectors = mutableListOf(11)

            var invokeAllChecksum = 0L
            val invokeAllMark = timeSource.markNow()
            for (n in 0..<iterations) {
                val listTasks: MutableList<Callable<Int>> = ArrayList(leafSlices.size)
                for (i in leafSlices.indices) {
                    val leaves = leafSlices[i]
                    val collector = collectors[i]
                    listTasks.add(
                        Callable {
                            // mimic `search(leaves, weight, collector)` with deterministic work
                            collector + leaves + (n and 1)
                        }
                    )
                }
                val results: MutableList<Int> = runBlocking { taskExecutor.invokeAll(listTasks) }
                invokeAllChecksum += results[0].toLong()
            }
            val invokeAllElapsedMs = invokeAllMark.elapsedNow().inWholeMilliseconds

            var directChecksum = 0L
            val directMark = timeSource.markNow()
            for (n in 0..<iterations) {
                val listTasks: MutableList<Callable<Int>> = ArrayList(leafSlices.size)
                for (i in leafSlices.indices) {
                    val leaves = leafSlices[i]
                    val collector = collectors[i]
                    listTasks.add(
                        Callable {
                            collector + leaves + (n and 1)
                        }
                    )
                }
                val results: MutableList<Int> = mutableListOf()
                for (task in listTasks) {
                    results.add(task.call())
                }
                directChecksum += results[0].toLong()
            }
            val directElapsedMs = directMark.elapsedNow().inWholeMilliseconds

            assertEquals(directChecksum, invokeAllChecksum)
            logger.debug {
                "PERF taskExecutor.singleSliceProbe iterations=$iterations " +
                    "invokeAllMs=$invokeAllElapsedMs directMs=$directElapsedMs " +
                    "ratio=${if (directElapsedMs == 0L) -1 else invokeAllElapsedMs.toDouble() / directElapsedMs.toDouble()}"
            }
        } finally {
            TestUtil.shutdownExecutorService(executorService)
        }
    }

    companion object {
        private fun doTestInvokeAllFromTaskDoesNotDeadlockSameSearcher(executor: Executor) {
            val dir = newDirectory()
            try {
                val iw = RandomIndexWriter(random(), dir)
                try {
                    for (i in 0..<500) {
                        iw.addDocument(Document())
                    }
                    val reader = iw.reader
                    try {
                        val searcher =
                            object : IndexSearcher(reader, executor) {
                                override fun slices(leaves: MutableList<LeafReaderContext>): Array<LeafSlice> {
                                    return slices(leaves, 1, 1, false)
                                }
                            }
                        searcher.search(
                            MatchAllDocsQuery(),
                            object : CollectorManager<Collector, Unit> {
                                override fun newCollector(): Collector {
                                    return object : Collector {
                                        override fun getLeafCollector(context: LeafReaderContext): LeafCollector {
                                            return object : LeafCollector {
                                                override var scorer: Scorable?
                                                    get() = null
                                                    set(value) {
                                                        runBlocking {
                                                            searcher.getTaskExecutor().invokeAll(
                                                                mutableListOf(
                                                                    Callable {
                                                                        // make sure that we don't miss disabling concurrency one
                                                                        // level deeper
                                                                        runBlocking {
                                                                            searcher.getTaskExecutor().invokeAll(
                                                                                mutableListOf(Callable { null })
                                                                            )
                                                                        }
                                                                        null
                                                                    }
                                                                )
                                                            )
                                                        }
                                                    }

                                                override fun collect(doc: Int) {}
                                            }
                                        }

                                        override fun scoreMode(): ScoreMode {
                                            return ScoreMode.COMPLETE
                                        }

                                        override var weight: Weight? = null
                                    }
                                }

                                override fun reduce(collectors: MutableCollection<Collector>): Unit {
                                    return Unit
                                }
                            }
                        )
                    } finally {
                        reader.close()
                    }
                } finally {
                    iw.close()
                }
            } finally {
                dir.close()
            }
        }

        private fun doTestInvokeAllFromTaskDoesNotDeadlockMultipleSearchers(executor: Executor) {
            val dir = newDirectory()
            try {
                val iw = RandomIndexWriter(random(), dir)
                try {
                    for (i in 0..<500) {
                        iw.addDocument(Document())
                    }
                    val reader = iw.reader
                    try {
                        val searcher =
                            object : IndexSearcher(reader, executor) {
                                override fun slices(leaves: MutableList<LeafReaderContext>): Array<LeafSlice> {
                                    return slices(leaves, 1, 1, false)
                                }
                            }
                        searcher.search(
                            MatchAllDocsQuery(),
                            object : CollectorManager<Collector, Unit> {
                                override fun newCollector(): Collector {
                                    return object : Collector {
                                        override fun getLeafCollector(context: LeafReaderContext): LeafCollector {
                                            return object : LeafCollector {
                                                override var scorer: Scorable?
                                                    get() = null
                                                    set(value) {
                                                        // the thread local used to prevent deadlock is static, so while each
                                                        // searcher has its own
                                                        // TaskExecutor, the safeguard is shared among all the searchers that get
                                                        // the same executor
                                                        val indexSearcher = IndexSearcher(reader, executor)
                                                        runBlocking {
                                                            indexSearcher.getTaskExecutor().invokeAll(
                                                                mutableListOf(Callable { null })
                                                            )
                                                        }
                                                    }

                                                override fun collect(doc: Int) {}
                                            }
                                        }

                                        override fun scoreMode(): ScoreMode {
                                            return ScoreMode.COMPLETE
                                        }

                                        override var weight: Weight? = null
                                    }
                                }

                                override fun reduce(collectors: MutableCollection<Collector>): Unit {
                                    return Unit
                                }
                            }
                        )
                    } finally {
                        reader.close()
                    }
                } finally {
                    iw.close()
                }
            } finally {
                dir.close()
            }
        }
    }
}
