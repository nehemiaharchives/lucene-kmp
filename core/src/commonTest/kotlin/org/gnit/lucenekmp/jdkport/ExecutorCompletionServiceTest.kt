package org.gnit.lucenekmp.jdkport

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.*

class ExecutorCompletionServiceTest {

    private fun concurrentExecutor(scope: CoroutineScope): Executor = Executor { r ->
        scope.launch(Dispatchers.Default) { r.run() }
    }

    @Ignore
    @Test
    fun submitCallable_completesAndTakeReturnsInCompletionOrder_repeat(){
        repeat(1000){ // to reproducing flaky test fail
            submitCallable_completesAndTakeReturnsInCompletionOrder()
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun submitCallable_completesAndTakeReturnsInCompletionOrder() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val ecs = ExecutorCompletionService<Int>(concurrentExecutor(scope))
            val completionNanosOne = AtomicLong(0L)
            val completionNanosTwo = AtomicLong(0L)

            // Submit two tasks with different delays. Actual order can vary by scheduler.
            ecs.submit {
                runBlocking { delay(200) }
                completionNanosOne.store(System.nanoTime())
                1
            }
            ecs.submit {
                runBlocking { delay(50) }
                completionNanosTwo.store(System.nanoTime())
                2
            }

            // Immediately after submission, no completed tasks yet
            assertNull(ecs.poll())

            val f1 = ecs.take()
            val v1 = f1.get()
            val f2 = ecs.take()
            val v2 = f2.get()

            assertEquals(setOf(1, 2), setOf(v1, v2))

            val t1 = completionNanosOne.load()
            val t2 = completionNanosTwo.load()
            assertTrue(t1 > 0L && t2 > 0L, "tasks must publish completion timestamps")
            val expectedFirst = if (t1 <= t2) 1 else 2
            assertEquals(
                expectedFirst,
                v1,
                "take() must return tasks in completion order. t1=$t1, t2=$t2, v1=$v1, v2=$v2"
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun submitCallable_withSameThreadExecutorCanCompleteInSubmissionOrder() = runTest {
        val ecs = ExecutorCompletionService<Int>(Executor { r -> r.run() })
        ecs.submit {
            runBlocking { delay(20) }
            1
        }
        ecs.submit { 2 }

        assertEquals(1, ecs.take().get())
        assertEquals(2, ecs.take().get())
    }

    @Test
    fun submitRunnableWithResult_enqueuesAndReturnsProvidedResult() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val ecs = ExecutorCompletionService<String>(concurrentExecutor(scope))

            val ran = CompletableDeferred<Boolean>()
            val future = ecs.submit({
                // Simulate work and set a flag
                ran.complete(true)
            }, "OK")

            // The returned future should also complete with the provided result
            assertEquals("OK", future.get())

            // And it should appear on the completion queue
            val completed = ecs.take()
            assertSame(future, completed)
            assertEquals("OK", completed.get())
            assertTrue(ran.await())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun pollWithTimeout_waitsUpToTimeoutAndReturnsWhenAvailable() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val ecs = ExecutorCompletionService<Int>(concurrentExecutor(scope))

            // Gate the task so its completion can't race the first poll
            val gate = CompletableDeferred<Unit>()
            ecs.submit {
                runBlocking {
                    gate.await()
                }
                7
            }

            // Not ready yet; small timeout should return null
            assertNull(withContext(Dispatchers.Default) { ecs.poll(20, TimeUnit.MILLISECONDS) })

            // Larger timeout should allow completion
            gate.complete(Unit)
            val f = withContext(Dispatchers.Default) { ecs.poll(200, TimeUnit.MILLISECONDS) }
            assertNotNull(f)
            assertEquals(7, f.get())
        } finally {
            scope.cancel()
        }
    }
}
