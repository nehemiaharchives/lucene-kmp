package org.gnit.lucenekmp.jdkport

import kotlinx.coroutines.*
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

class FutureTaskTest {

    @Test
    fun testCancel() {
        runBlocking {
            // Use a CompletableDeferred to simulate a long-running task
            val deferred = CompletableDeferred<String>()

            val task = FutureTask(Callable {
                runBlocking {
                    withTimeout(2000) {
                        deferred.await()
                    }
                }
            })

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
        runBlocking {
            // Use a CompletableDeferred to control completion
            val deferred = CompletableDeferred<String>()

            val task = FutureTask(Callable {
                runBlocking {
                    deferred.await()
                }
            })

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
        runBlocking {
            // Use a CompletableDeferred to simulate a long-running task
            val deferred = CompletableDeferred<String>()

            val task = FutureTask(Callable {
                runBlocking {
                    withTimeout(2000) {
                        deferred.await()
                    }
                }
            })

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
        runBlocking {
            val task = FutureTask(Callable {
                "result"
            })
            task.run()
            assertEquals("result", task.get())
        }
    }

    @Test
    fun testRun() {
        runBlocking {
            val task = FutureTask(Callable {
                "result"
            })
            task.run()
            assertEquals("result", task.get())
        }
    }

    @Test
    fun testRunAndReset() {
        runBlocking {
            // Create a custom FutureTask that exposes the protected runAndReset method
            class TestFutureTask<V>(callable: Callable<V>) : FutureTask<V>(callable) {
                fun publicRunAndReset(): Boolean = runAndReset()
            }

            val task = TestFutureTask(Callable {
                "result"
            })

            assertTrue(task.publicRunAndReset())
            assertFalse(task.isDone())
        }
    }
}
