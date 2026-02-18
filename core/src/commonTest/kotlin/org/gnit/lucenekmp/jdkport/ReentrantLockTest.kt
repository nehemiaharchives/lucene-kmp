package org.gnit.lucenekmp.jdkport

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Runnable
import org.gnit.lucenekmp.util.NamedThreadFactory
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ReentrantLockTest {
    @Test
    fun testLockUnlock() {
        val lock = ReentrantLock()
        assertFalse(lock.isHeldByCurrentThread())
        lock.lock()
        assertTrue(lock.isHeldByCurrentThread())
        assertTrue(lock.tryLock()) // reentrant
        lock.unlock()
        lock.unlock()
        assertFalse(lock.isHeldByCurrentThread())
        assertTrue(lock.tryLock())
        lock.unlock()
    }

    @Test
    fun testReentrantHoldCount() {
        val lock = ReentrantLock()
        lock.lock()
        assertTrue(lock.isHeldByCurrentThread())
        assertTrue(lock.tryLock())
        lock.unlock()
        assertTrue(lock.isHeldByCurrentThread())
        lock.unlock()
        assertFalse(lock.isHeldByCurrentThread())
    }

    @Test
    fun testUnlockWithoutOwner() {
        val lock = ReentrantLock()
        assertFailsWith<IllegalMonitorStateException> {
            lock.unlock()
        }
    }

    @Test
    fun testTryLockFailsWhileHeldByOtherThread() {
        val lock = ReentrantLock()
        val ready = CountDownLatch(1)
        val release = CountDownLatch(1)
        val failures = arrayOfNulls<Throwable>(1)
        val threadFactory = NamedThreadFactory("ReentrantLockTest")
        val worker = threadFactory.newThread(
            Runnable {
                try {
                    lock.lock()
                    ready.countDown()
                    release.await()
                } catch (t: Throwable) {
                    failures[0] = t
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock()
                    }
                }
            }
        )

        ready.await()
        assertFalse(lock.tryLock())
        release.countDown()
        runBlocking { worker.join() }
        failures[0]?.let { throw it }

        assertTrue(lock.tryLock())
        lock.unlock()
    }

    @Test
    fun testUnlockFromOtherThreadThrows() {
        val lock = ReentrantLock()
        val ready = CountDownLatch(1)
        val release = CountDownLatch(1)
        val thrown = arrayOfNulls<Throwable>(1)
        val threadFactory = NamedThreadFactory("ReentrantLockTest")
        lock.lock()
        val worker = threadFactory.newThread(
            Runnable {
                try {
                    ready.countDown()
                    lock.unlock()
                } catch (t: Throwable) {
                    thrown[0] = t
                } finally {
                    release.countDown()
                }
            }
        )

        ready.await()
        release.await()
        runBlocking { worker.join() }

        assertTrue(thrown[0] is IllegalMonitorStateException)
        lock.unlock()
    }

    @Test
    fun testConditionAwaitReleasesAndReacquiresLock() {
        runBlocking {
            val lock = ReentrantLock()
            val condition = lock.newCondition()
            val awaiting = CountDownLatch(1)
            val resumed = CountDownLatch(1)
            val failures = arrayOfNulls<Throwable>(1)
            val threadFactory = NamedThreadFactory("ReentrantLockTest")
            val worker = threadFactory.newThread(
                Runnable {
                    runBlocking {
                        try {
                            lock.lock()
                            awaiting.countDown()
                            condition.await()
                            resumed.countDown()
                        } catch (t: Throwable) {
                            failures[0] = t
                        } finally {
                            if (lock.isHeldByCurrentThread()) {
                                lock.unlock()
                            }
                        }
                    }
                }
            )

            awaiting.await()
            acquireWithin(lock, 2.seconds)
            try {
                condition.signal()
            } finally {
                lock.unlock()
            }

            waitForLatchWithin(resumed, 2.seconds)
            worker.join()
            failures[0]?.let { throw it }
        }
    }

    @Test
    fun testConditionMethodsRequireLock() {
        runBlocking {
            val lock = ReentrantLock()
            val condition = lock.newCondition()

            assertFailsWith<IllegalStateException> { condition.signal() }
            assertFailsWith<IllegalStateException> { condition.signalAll() }
            assertFailsWith<IllegalStateException> { condition.awaitNanos(1_000_000) }
        }
    }

    private fun acquireWithin(lock: ReentrantLock, timeout: kotlin.time.Duration) {
        val start = TimeSource.Monotonic.markNow()
        while (!lock.tryLock()) {
            if (start.elapsedNow() >= timeout) {
                throw AssertionError("timed out acquiring lock within $timeout")
            }
        }
    }

    private fun waitForLatchWithin(latch: CountDownLatch, timeout: kotlin.time.Duration) {
        val start = TimeSource.Monotonic.markNow()
        while (latch.getCount() > 0) {
            if (start.elapsedNow() >= timeout) {
                throw AssertionError("timed out waiting for latch within $timeout")
            }
        }
        assertEquals(0L, latch.getCount())
    }
}
