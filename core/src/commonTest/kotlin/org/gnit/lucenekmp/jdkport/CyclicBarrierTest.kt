package org.gnit.lucenekmp.jdkport

import kotlinx.coroutines.Runnable
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

@OptIn(ExperimentalAtomicApi::class)
class CyclicBarrierTest {

    @Test
    fun testAwaitReturnsDistinctArrivalIndexes() {
        val barrier = CyclicBarrier(2)
        val started = CountDownLatch(2)
        val finished = CountDownLatch(2)
        val failures = arrayOfNulls<Throwable>(2)
        val indexes = arrayOfNulls<Int>(2)

        val threads =
            arrayOf(
                Thread(
                    Runnable {
                        try {
                            started.countDown()
                            indexes[0] = barrier.await()
                        } catch (t: Throwable) {
                            failures[0] = t
                        } finally {
                            finished.countDown()
                        }
                    }
                ),
                Thread(
                    Runnable {
                        try {
                            started.countDown()
                            indexes[1] = barrier.await()
                        } catch (t: Throwable) {
                            failures[1] = t
                        } finally {
                            finished.countDown()
                        }
                    }
                )
            )

        threads[0].start()
        threads[1].start()

        waitForLatchWithin(started, 2.seconds)
        waitForLatchWithin(finished, 2.seconds)
        threads[0].join()
        threads[1].join()

        failures.forEachIndexed { idx, failure ->
            if (failure != null) {
                throw AssertionError("thread $idx failed", failure)
            }
        }
        assertEquals(setOf(0, 1), indexes.filterNotNull().toSet())
    }

    @Test
    fun testBarrierIsReusable() {
        val barrierActionCount = AtomicInteger(0)
        val barrier =
            CyclicBarrier(
                2,
                Runnable {
                    barrierActionCount.incrementAndFetch()
                }
            )
        val finished = CountDownLatch(2)
        val failures = arrayOfNulls<Throwable>(2)

        val threads =
            arrayOf(
                Thread(
                    Runnable {
                        try {
                            repeat(2) {
                                barrier.await()
                            }
                        } catch (t: Throwable) {
                            failures[0] = t
                        } finally {
                            finished.countDown()
                        }
                    }
                ),
                Thread(
                    Runnable {
                        try {
                            repeat(2) {
                                barrier.await()
                            }
                        } catch (t: Throwable) {
                            failures[1] = t
                        } finally {
                            finished.countDown()
                        }
                    }
                )
            )

        threads[0].start()
        threads[1].start()

        waitForLatchWithin(finished, 2.seconds)
        threads[0].join()
        threads[1].join()

        failures.forEachIndexed { idx, failure ->
            if (failure != null) {
                throw AssertionError("thread $idx failed", failure)
            }
        }
        assertEquals(2, barrierActionCount.get())
    }

    @Test
    fun testBarrierActionFailureBreaksBarrier() {
        val barrier =
            CyclicBarrier(
                2,
                Runnable {
                    throw RuntimeException("boom")
                }
            )
        val finished = CountDownLatch(2)
        val failures = arrayOfNulls<Throwable>(2)

        val threads =
            arrayOf(
                Thread(
                    Runnable {
                        try {
                            barrier.await()
                        } catch (t: Throwable) {
                            failures[0] = t
                        } finally {
                            finished.countDown()
                        }
                    }
                ),
                Thread(
                    Runnable {
                        try {
                            barrier.await()
                        } catch (t: Throwable) {
                            failures[1] = t
                        } finally {
                            finished.countDown()
                        }
                    }
                )
            )

        threads[0].start()
        threads[1].start()

        waitForLatchWithin(finished, 2.seconds)
        threads[0].join()
        threads[1].join()

        assertTrue(
            failures.any { it is RuntimeException && it.message == "boom" },
            "expected the barrier action failure to be observed"
        )
        assertTrue(
            failures.any { it is BrokenBarrierException },
            "expected the other waiter to see a BrokenBarrierException"
        )
        assertFailsWith<BrokenBarrierException> {
            barrier.await()
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
