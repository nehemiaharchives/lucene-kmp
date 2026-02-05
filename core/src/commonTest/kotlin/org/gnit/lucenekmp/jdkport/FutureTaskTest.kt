package org.gnit.lucenekmp.jdkport

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class FutureTaskTest {

    @Test
    fun testCancel() {
        runTest {
            // Use a CompletableDeferred to simulate a long-running task
            val deferred = CompletableDeferred<String>()

            val task = FutureTask {
                runBlocking {
                    withTimeout(2000) {
                        deferred.await()
                    }
                }
            }

            // Launch the task in a separate coroutine
            launch {
                task.run()
            }

            delay(100)
            assertTrue(task.cancel(true))
            assertTrue(task.isCancelled())
            assertTrue(task.isDone())

            // Complete the deferred to avoid leaking
            deferred.complete("result")
        }
    }

    @Test
    fun testIsDone() {
        runTest {
            // Use a CompletableDeferred to control completion
            val deferred = CompletableDeferred<String>()

            val task = FutureTask {
                runBlocking {
                    deferred.await()
                }
            }

            // Launch the task in a separate coroutine
            launch {
                task.run()
            }

            delay(50)
            assertFalse(task.isDone())

            // Complete the deferred to finish the task
            deferred.complete("result")
            delay(100)
            assertTrue(task.isDone())
        }
    }

    @Test
    fun testIsCancelled() {
        runTest {
            // Use a CompletableDeferred to simulate a long-running task
            val deferred = CompletableDeferred<String>()

            val task = FutureTask {
                runBlocking {
                    withTimeout(2000) {
                        deferred.await()
                    }
                }
            }

            // Launch the task in a separate coroutine
            launch {
                task.run()
            }

            delay(100)
            task.cancel(true)
            assertTrue(task.isCancelled())

            // Complete the deferred to avoid leaking
            deferred.complete("result")
        }
    }

    @Test
    fun testGet() {
        runTest {
            val task = FutureTask {
                "result"
            }
            task.run()
            assertEquals("result", task.get())
        }
    }

    @Test
    fun testRun() {
        runTest {
            val task = FutureTask {
                "result"
            }
            task.run()
            assertEquals("result", task.get())
        }
    }

    @Test
    fun testRunAndReset() {
        runTest {
            // Create a custom FutureTask that exposes the protected runAndReset method
            class TestFutureTask<V>(callable: Callable<V>) : FutureTask<V>(callable) {
                fun publicRunAndReset(): Boolean = runAndReset()
            }

            val task = TestFutureTask {
                "result"
            }

            assertTrue(task.publicRunAndReset())
            assertFalse(task.isDone())
        }
    }

    @Test
    fun testGet_multipleWaitersAllComplete() = runTest {
        val task = FutureTask {
            "result"
        }

        val waiter1 = async(Dispatchers.Default) { withTimeout(2_000) { task.get() } }
        val waiter2 = async(Dispatchers.Default) { withTimeout(2_000) { task.get() } }

        delay(50)
        launch(Dispatchers.Default) { task.run() }

        assertEquals("result", waiter1.await())
        assertEquals("result", waiter2.await())
    }
}
