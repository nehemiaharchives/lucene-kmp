package org.gnit.lucenekmp.jdkport

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlin.test.*

class ThreadPoolExecutorTest {

    private val logger = KotlinLogging.logger {}

    private fun newExecutor(
        core: Int = 1,
        max: Int = 2,
        keepAliveMs: Long = 1000,
        queue: BlockingQueue<Runnable> = LinkedBlockingQueue()
    ): ThreadPoolExecutor = ThreadPoolExecutor(
        core,
        max,
        keepAliveMs,
        TimeUnit.MILLISECONDS,
        queue
    )

    @Test
    fun execute_runsTask() = runBlocking {
        logger.debug { "[TEST] execute_runsTask: start on dispatcher=${coroutineContext[CoroutineDispatcher]}" }
        val queue = LinkedBlockingQueue<Runnable>()
        val exec = newExecutor(core = 1, max = 1, queue = queue)
        try {
            val result = CompletableDeferred<Int>()
            logger.debug { "[TEST] execute_runsTask: submitting task; queueSize(before)=${queue.size}" }
            exec.execute {
                logger.debug { "[TEST] execute_runsTask: task running (worker started)" }
                result.complete(42)
                logger.debug { "[TEST] execute_runsTask: task completed result.isCompleted=${result.isCompleted}" }
            }
            logger.debug { "[TEST] execute_runsTask: submitted; queueSize(afterSubmit)=${queue.size}; awaiting result..." }
            val v = try {
                logger.debug { "[TEST] execute_runsTask: about to await result (withTimeout)" }
                withTimeout(5000) { result.await() }.also {
                    logger.debug { "[TEST] execute_runsTask: await returned value=$it" }
                }
            } catch (t: TimeoutCancellationException) {
                logger.debug { "[TEST] execute_runsTask: TIMEOUT waiting for result; queueSize(now)=${queue.size} resultCompleted=${result.isCompleted}" }
                throw t
            }
            logger.debug { "[TEST] execute_runsTask: got result $v (post-await log)" }
            assertEquals(42, v)
        } finally {
            logger.debug { "[TEST] execute_runsTask: shutting down (before shutdown())" }
            exec.shutdown()
            logger.debug { "[TEST] execute_runsTask: after shutdown() isShutdown=${exec.isShutdown} isTerminated=${exec.isTerminated}" }
            val terminated = exec.awaitTermination(1, TimeUnit.SECONDS)
            logger.debug { "[TEST] execute_runsTask: after awaitTermination terminated=$terminated isTerminated=${exec.isTerminated}" }
            logger.debug { "[TEST] execute_runsTask: end" }
        }
    }

    @Test
    fun submit_runnableAndCallable() = runBlocking {
        val exec = newExecutor()
        try {
            // Proactively start a core worker to reduce scheduler jitter on CI
            exec.prestartCoreThread()
            logger.debug { "[TEST] submit_runnableAndCallable: submitting tasks" }
            val f1 = exec.submit {
                logger.debug { "[TEST] submit_runnableAndCallable: runnable ran" }
            }
            val f2 = exec.submit(Callable {
                logger.debug { "[TEST] submit_runnableAndCallable: callable ran" }; 7 })
            logger.debug { "[TEST] submit_runnableAndCallable: submitted; awaiting f1" }
            withTimeout(5000) { f1.get() }
            logger.debug { "[TEST] submit_runnableAndCallable: f1 done awaiting f2" }
            val v = withTimeout(5000) { f2.get() }
            logger.debug { "[TEST] submit_runnableAndCallable: f2 done value=$v" }
            assertEquals(7, v)
        } finally {
            exec.shutdown()
            exec.awaitTermination(1, TimeUnit.SECONDS)
        }
    }

    @Test
    fun prestartCoreThread_startsIdleWorker() = runBlocking {
        val exec = newExecutor(core = 1, max = 1, queue = LinkedBlockingQueue())
        try {
            val started = exec.prestartCoreThread()
            assertTrue(started)
            assertEquals(1, exec.poolSize)
        } finally {
            exec.shutdown()
            exec.awaitTermination(1, TimeUnit.SECONDS)
        }
    }

    @Test
    fun shutdown_rejectsNewTasks() = runBlocking {
        val exec = newExecutor()
        exec.shutdown()
        val r = Runnable { }
        // Using default AbortPolicy, execute should throw
        assertFails { exec.execute(r) }
        // shutdownNow returns pending tasks list, and then executor becomes terminated
        val list = exec.shutdownNow()
        assertNotNull(list)
        assertTrue(exec.isShutdown)
    }

    @Test
    fun callerRunsPolicy_runsInCallerThreadWhenRejected() = runBlocking {

        val queue = LinkedBlockingQueue<Runnable>(1)
        val exec = newExecutor(core = 1, max = 1, keepAliveMs = 10, queue = queue)
        try {
            // Set CallerRunsPolicy
            exec.setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())

            val order = mutableListOf<String>()
            val gate = CompletableDeferred<Unit>()

            // Fill queue and worker so next execute() is rejected
            exec.execute {
                runBlocking { gate.await() }
                order.add("worker")
            }
            assertTrue(queue.offer {
                order.add("queued")
            })

            // This will be rejected and should run in caller (current coroutine) immediately
            exec.execute { order.add("caller") }

            // Unblock worker and let queued run
            gate.complete(Unit)
            withTimeout(1000) {
                while (order.size < 3) delay(10)
            }

            // caller must appear before worker/queued since it ran synchronously
            assertTrue(order.first() == "caller")
            assertTrue(order.containsAll(listOf("worker", "queued")))
        } finally {
            exec.shutdown()
            exec.awaitTermination(1, TimeUnit.SECONDS)
        }
    }
}
