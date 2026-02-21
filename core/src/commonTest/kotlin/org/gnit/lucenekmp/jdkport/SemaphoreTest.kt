package org.gnit.lucenekmp.jdkport

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

class SemaphoreTest {

    @Test
    fun testAcquireAfterRelease() = runBlocking {
        val semaphore = Semaphore(0)
        var acquired = false
        val job = launch(Dispatchers.Default) {
            semaphore.acquire()
            acquired = true
        }
        delay(50)
        assertTrue(!acquired)
        semaphore.release()
        job.join()
        assertTrue(acquired)
    }

    @Test
    fun testAcquireBlocksUntilPermitAvailable() = runBlocking {
        val semaphore = Semaphore(0)
        val mark = TimeSource.Monotonic.markNow()
        val releaser = launch(Dispatchers.Default) {
            delay(100)
            semaphore.release()
        }
        semaphore.acquire()
        releaser.join()
        assertTrue(mark.elapsedNow() >= 80.milliseconds)
    }

    @Test
    fun testMultiplePermits() = runBlocking {
        val semaphore = Semaphore(2)
        semaphore.acquire()
        semaphore.acquire()

        var thirdAcquired = false
        val job = launch(Dispatchers.Default) {
            semaphore.acquire()
            thirdAcquired = true
        }
        delay(50)
        assertTrue(!thirdAcquired)
        semaphore.release()
        job.join()
        assertTrue(thirdAcquired)
    }
}
