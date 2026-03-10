package org.gnit.lucenekmp.jdkport

import kotlinx.coroutines.Runnable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class ThreadTest {
    @Test
    fun testStartRunsTargetAndJoinWaits() {
        val started = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val runningThread = arrayOfNulls<Thread>(1)
        val mainThread = Thread.currentThread()
        val thread =
            Thread(
                Runnable {
                    runningThread[0] = Thread.currentThread()
                    started.countDown()
                    Thread.sleep(25)
                    finished.countDown()
                }
            )
        thread.setName("worker-thread")

        thread.start()
        started.await()
        thread.join()

        val observedThread = runningThread[0]
        assertNotNull(observedThread)
        assertSame(thread, observedThread)
        assertEquals("worker-thread", observedThread.getName())
        assertEquals(0L, finished.getCount())
        assertSame(mainThread, Thread.currentThread())
    }

    @Test
    fun testJoinWithTimeoutReturnsBeforeCompletion() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val thread =
            Thread(
                Runnable {
                    started.countDown()
                    release.await()
                    finished.countDown()
                }
            )

        thread.start()
        started.await()

        val start = TimeSource.Monotonic.markNow()
        thread.join(25)
        assertTrue(start.elapsedNow() < 1.seconds)
        assertEquals(1L, finished.getCount())

        release.countDown()
        thread.join()
        assertEquals(0L, finished.getCount())
    }

    @Test
    fun testInterruptAndInterruptedClearsStatus() {
        val started = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val isInterruptedBeforeClear = arrayOfNulls<Boolean>(1)
        val interruptedResult = arrayOfNulls<Boolean>(1)
        val isInterruptedAfterClear = arrayOfNulls<Boolean>(1)
        val thread =
            Thread(
                Runnable {
                    try {
                        started.countDown()
                        Thread.sleep(5_000)
                    } catch (_: Throwable) {
                        isInterruptedBeforeClear[0] = Thread.currentThread().isInterrupted()
                        interruptedResult[0] = Thread.interrupted()
                        isInterruptedAfterClear[0] = Thread.currentThread().isInterrupted()
                    } finally {
                        finished.countDown()
                    }
                }
            )

        thread.start()
        started.await()
        thread.interrupt()
        finished.await()
        thread.join()

        assertEquals(true, isInterruptedBeforeClear[0])
        assertEquals(true, interruptedResult[0])
        assertEquals(false, isInterruptedAfterClear[0])
    }

    @Test
    fun testSetNamePriorityAndDaemon() {
        val thread = Thread()

        thread.setName("named-thread")
        thread.setPriority(Thread.MAX_PRIORITY + 10)
        thread.setDaemon(true)
        assertEquals("named-thread", thread.getName())
        assertEquals(Thread.MAX_PRIORITY, thread.getPriority())
        assertTrue(thread.isDaemon())

        thread.setPriority(Thread.MIN_PRIORITY - 10)
        assertEquals(Thread.MIN_PRIORITY, thread.getPriority())
    }

    @Test
    fun testStartTwiceFails() {
        val thread = Thread(Runnable {})

        thread.start()
        thread.join()

        assertFailsWith<IllegalStateException> {
            thread.start()
        }
    }

    @Test
    fun testInterruptedClearsCurrentThreadFlag() {
        val current = Thread.currentThread()

        assertFalse(Thread.interrupted())
        current.interrupt()
        assertTrue(Thread.interrupted())
        assertFalse(Thread.interrupted())
    }
}
