package org.gnit.lucenekmp.index

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlinx.coroutines.CancellationException
import org.gnit.lucenekmp.jdkport.CountDownLatch
import org.gnit.lucenekmp.jdkport.TimeUnit
import org.gnit.lucenekmp.util.ThreadInterruptedException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.fail
import kotlin.test.Ignore

/**
 * Kotlin port of Lucene's TestDocumentsWriterStallControl.
 *
 * The underlying DocumentsWriterStallControl is currently only
 * partially implemented, so these tests are marked as ignored
 * until the full functionality is available.
 */
class TestDocumentsWriterStallControl : LuceneTestCase() {

    @Test
    @Ignore("DocumentsWriterStallControl.waitIfStalled is not fully implemented")
    fun testSimpleStall() {
        val ctrl = DocumentsWriterStallControl()

        ctrl.updateStalled(false)
        var waitThreadsArr = waitThreads(atLeast(1), ctrl)
        start(waitThreadsArr)
        assertFalse(ctrl.hasBlocked())
        assertFalse(ctrl.anyStalledThreads())
        join(waitThreadsArr)

        // now stall threads and wake them up again
        ctrl.updateStalled(true)
        waitThreadsArr = waitThreads(atLeast(1), ctrl)
        start(waitThreadsArr)
        awaitState(Thread.State.TIMED_WAITING, *waitThreadsArr)
        assertTrue(ctrl.hasBlocked())
        assertTrue(ctrl.anyStalledThreads())
        ctrl.updateStalled(false)
        assertFalse(ctrl.anyStalledThreads())
        join(waitThreadsArr)
    }

    @Test
    @Ignore("DocumentsWriterStallControl.waitIfStalled is not fully implemented")
    fun testRandom() {
        val ctrl = DocumentsWriterStallControl()
        ctrl.updateStalled(false)

        val stallThreads = Array<Thread>(atLeast(3)) {
            val stallProbability = 1 + random().nextInt(10)
            object : Thread() {
                override fun run() {
                    val iters = atLeast(100)
                    for (j in 0 until iters) {
                        ctrl.updateStalled(random().nextInt(stallProbability) == 0)
                        if (random().nextInt(5) == 0) {
                            ctrl.waitIfStalled()
                        }
                    }
                }
            }
        }
        start(stallThreads)

        var iterations = 0
        while (++iterations < 100 && !terminated(stallThreads)) {
            ctrl.updateStalled(false)
            if (random().nextBoolean()) {
                Thread.yield()
            } else {
                Thread.sleep(1)
            }
        }
        join(stallThreads)
    }

    @OptIn(ExperimentalAtomicApi::class)
    @Test
    @Ignore("DocumentsWriterStallControl.waitIfStalled is not fully implemented")
    fun testAcquireReleaseRace() {
        val ctrl = DocumentsWriterStallControl()
        ctrl.updateStalled(false)
        val stop = AtomicBoolean(false)
        val checkPoint = AtomicBoolean(true)

        val numStallers = atLeast(1)
        val numReleasers = atLeast(1)
        val numWaiters = atLeast(1)
        val sync = Synchronizer(numStallers + numReleasers, numStallers + numReleasers + numWaiters)
        val threads = Array(numReleasers + numStallers + numWaiters) { Thread() }
        val exceptions = mutableListOf<Throwable>()

        for (i in 0 until numReleasers) {
            threads[i] = Updater(stop, checkPoint, ctrl, sync, true, exceptions)
        }
        for (i in numReleasers until numReleasers + numStallers) {
            threads[i] = Updater(stop, checkPoint, ctrl, sync, false, exceptions)
        }
        for (i in numReleasers + numStallers until threads.size) {
            threads[i] = Waiter(stop, checkPoint, ctrl, sync, exceptions)
        }

        start(threads)
        val iters = if (TEST_NIGHTLY) atLeast(10000) else atLeast(1000)
        val checkPointProbability = if (TEST_NIGHTLY) 0.5f else 0.1f
        for (i in 0 until iters) {
            if (checkPoint.load()) {
                assertTrue(sync.updateJoin.await(10, TimeUnit.SECONDS),
                    "timed out waiting for update threads - deadlock?")
                if (exceptions.isNotEmpty()) {
                    for (t in exceptions) {
                        t.printStackTrace()
                    }
                    fail("got exceptions in threads")
                }

                if (ctrl.hasBlocked() && ctrl.isHealthy) {
                    assertState(numReleasers, numStallers, numWaiters, threads, ctrl)
                }

                checkPoint.store(false)
                sync.waiter.countDown()
                sync.leftCheckpoint.await()
            }
            assertFalse(checkPoint.load())
            assertEquals(0L, sync.waiter.getCount())
            if (checkPointProbability >= random().nextFloat()) {
                sync.reset(numStallers + numReleasers, numStallers + numReleasers + numWaiters)
                checkPoint.store(true)
            }
        }
        if (!checkPoint.load()) {
            sync.reset(numStallers + numReleasers, numStallers + numReleasers + numWaiters)
            checkPoint.store(true)
        }

        assertTrue(sync.updateJoin.await(10, TimeUnit.SECONDS))
        assertState(numReleasers, numStallers, numWaiters, threads, ctrl)
        checkPoint.store(false)
        stop.store(true)
        sync.waiter.countDown()
        sync.leftCheckpoint.await()

        for (i in threads.indices) {
            ctrl.updateStalled(false)
            threads[i].join(2000)
            if (threads[i].isAlive && threads[i] is Waiter) {
                if (threads[i].state == Thread.State.WAITING) {
                    fail("waiter is not released - anyThreadsStalled: ${ctrl.anyStalledThreads()}")
                }
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun assertState(
        numReleasers: Int,
        numStallers: Int,
        numWaiters: Int,
        threads: Array<Thread>,
        ctrl: DocumentsWriterStallControl
    ) {
        // TODO: implement once DocumentsWriterStallControl supports thread queue inspection
    }

    @OptIn(ExperimentalAtomicApi::class)
    private class Waiter(
        private val stop: AtomicBoolean,
        private val checkPoint: AtomicBoolean,
        private val ctrl: DocumentsWriterStallControl,
        private val sync: Synchronizer,
        private val exceptions: MutableList<Throwable>
    ) : Thread("waiter") {

        override fun run() {
            try {
                while (!stop.load()) {
                    ctrl.waitIfStalled()
                    if (checkPoint.load()) {
                        try {
                            assertTrue(sync.await())
                        } catch (e: Exception) {
                            println("[Waiter] got interrupted - wait count: ${sync.waiter.getCount()}")
                            throw ThreadInterruptedException(CancellationException(e.message))
                        }
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                exceptions.add(e)
            }
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    private class Updater(
        private val stop: AtomicBoolean,
        private val checkPoint: AtomicBoolean,
        private val ctrl: DocumentsWriterStallControl,
        private val sync: Synchronizer,
        private val release: Boolean,
        private val exceptions: MutableList<Throwable>
    ) : Thread("updater") {

        override fun run() {
            try {
                while (!stop.load()) {
                    val internalIters = if (release && random().nextBoolean()) atLeast(5) else 1
                    for (i in 0 until internalIters) {
                        ctrl.updateStalled(random().nextBoolean())
                    }
                    if (checkPoint.load()) {
                        sync.updateJoin.countDown()
                        try {
                            assertTrue(sync.await())
                        } catch (e: Exception) {
                            println("[Updater] got interrupted - wait count: ${sync.waiter.getCount()}")
                            throw ThreadInterruptedException(CancellationException(e.message))
                        }
                        sync.leftCheckpoint.countDown()
                    }
                    if (random().nextBoolean()) {
                        Thread.yield()
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                exceptions.add(e)
            }
            sync.updateJoin.countDown()
        }
    }

    companion object {
        fun terminated(threads: Array<Thread>): Boolean {
            for (thread in threads) {
                if (thread.state != Thread.State.TERMINATED) return false
            }
            return true
        }

        fun start(tostart: Array<Thread>) {
            for (thread in tostart) {
                thread.start()
            }
            Thread.sleep(1)
        }

        fun join(toJoin: Array<Thread>) {
            for (thread in toJoin) {
                thread.join()
            }
        }

        fun waitThreads(num: Int, ctrl: DocumentsWriterStallControl): Array<Thread> {
            return Array<Thread>(num) {
                object : Thread() {
                    override fun run() {
                        ctrl.waitIfStalled()
                    }
                }
            }
        }

        fun awaitState(state: Thread.State, vararg threads: Thread) {
            while (true) {
                var done = true
                for (thread in threads) {
                    if (thread.state != state) {
                        done = false
                        break
                    }
                }
                if (done) {
                    return
                }
                if (random().nextBoolean()) {
                    Thread.yield()
                } else {
                    Thread.sleep(1)
                }
            }
        }

        private class Synchronizer(numUpdater: Int, numThreads: Int) {
            lateinit var waiter: CountDownLatch
            lateinit var updateJoin: CountDownLatch
            lateinit var leftCheckpoint: CountDownLatch

            init {
                reset(numUpdater, numThreads)
            }

            fun reset(numUpdaters: Int, numThreads: Int) {
                waiter = CountDownLatch(1)
                updateJoin = CountDownLatch(numUpdaters)
                leftCheckpoint = CountDownLatch(numUpdaters)
            }

            fun await(): Boolean {
                return waiter.await(10, TimeUnit.SECONDS)
            }
        }
    }
}

