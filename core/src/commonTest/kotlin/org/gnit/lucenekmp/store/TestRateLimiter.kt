package org.gnit.lucenekmp.store

import org.gnit.lucenekmp.util.getLogger
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.gnit.lucenekmp.jdkport.CountDownLatch
import org.gnit.lucenekmp.store.RateLimiter.SimpleRateLimiter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.ThreadInterruptedException
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Clock
import kotlin.time.DurationUnit
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertTrue

/** Simple testcase for RateLimiter.SimpleRateLimiter */
class TestRateLimiter : LuceneTestCase() {

    private val logger = getLogger()

    // LUCENE-6075
    @Test
    fun testOverflowInt() = runTest {
        expectThrows(ThreadInterruptedException::class) {
            runBlocking {
                withTimeout(10) {
                    SimpleRateLimiter(1.0).pause((1.5 * Int.MAX_VALUE * 1024 * 1024 / 1000).toLong())
                }
            }
        }
    }

    @Test
    @OptIn(ExperimentalAtomicApi::class)
    fun testThreads() = runTest {
        val targetMBPerSec = 10.0 + 20 * random().nextDouble()
        val limiter = SimpleRateLimiter(targetMBPerSec)

        val startingGun = CountDownLatch(1)

        val jobs = arrayOfNulls<Job>(TestUtil.nextInt(random(), 3, 6))
        val totBytes = AtomicLong(0L)
        for (i in jobs.indices) {
            jobs[i] = launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
                startingGun.await()
                var bytesSinceLastPause = 0L
                for (j in 0..<500) {
                    val numBytes = TestUtil.nextInt(random(), 1000, 10000).toLong()
                    totBytes.addAndFetch(numBytes)
                    bytesSinceLastPause += numBytes
                    if (bytesSinceLastPause > limiter.minPauseCheckBytes) {
                        limiter.pause(bytesSinceLastPause)
                        bytesSinceLastPause = 0
                    }
                }
            }
            jobs[i]!!.start()
        }

        val startNS: Instant = Clock.System.now()
        startingGun.countDown()
        for (job in jobs) {
            job!!.join()
        }
        val endNS: Instant = Clock.System.now()
        val actualMBPerSec =
            (totBytes.load() / 1024.0 / 1024.0) /
                (endNS.minus(startNS).toDouble(DurationUnit.SECONDS))

        // TODO: this may false trip .... could be we can only assert that it never exceeds the max, so
        // slow jenkins doesn't trip:
        val ratio = actualMBPerSec / targetMBPerSec

        // Only enforce that it wasn't too fast; if machine is bogged down (can't schedule threads /
        // sleep properly) then it may falsely be too slow. Upstream uses assumeTrue here, but in
        // this KMP test framework assumeTrue is a hard failure rather than a skip, so keep slow
        // environments from tripping the test.
        assertTrue(ratio <= 1.1, "targetMBPerSec=$targetMBPerSec actualMBPerSec=$actualMBPerSec")
    }
}
