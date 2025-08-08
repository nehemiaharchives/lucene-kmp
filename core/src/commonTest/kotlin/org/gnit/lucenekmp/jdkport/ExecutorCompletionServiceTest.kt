package org.gnit.lucenekmp.jdkport

import kotlinx.coroutines.*
import kotlin.test.*

class ExecutorCompletionServiceTest {

    private fun concurrentExecutor(scope: CoroutineScope): Executor = Executor { r ->
        scope.launch(Dispatchers.Default) { r.run() }
    }

    @Test
    fun submitCallable_completesAndTakeReturnsInCompletionOrder() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val ecs = ExecutorCompletionService<Int>(concurrentExecutor(scope))

            // Submit two tasks that finish out of submission order
            ecs.submit {
                runBlocking { delay(200) }
                1
            }
            ecs.submit {
                runBlocking { delay(50) }
                2
            }

            // Immediately after submission, no completed tasks yet
            assertNull(ecs.poll())

            val f1 = ecs.take() // should be the faster one (2)
            val v1 = f1.get()
            val f2 = ecs.take()
            val v2 = f2.get()

            assertEquals(2, v1)
            assertEquals(1, v2)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun submitRunnableWithResult_enqueuesAndReturnsProvidedResult() = runBlocking {
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
    fun pollWithTimeout_waitsUpToTimeoutAndReturnsWhenAvailable() = runBlocking {
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
            assertNull(ecs.poll(20, TimeUnit.MILLISECONDS))

            // Larger timeout should allow completion
            gate.complete(Unit)
            val f = ecs.poll(200, TimeUnit.MILLISECONDS)
            assertNotNull(f)
            assertEquals(7, f.get())
        } finally {
            scope.cancel()
        }
    }
}
