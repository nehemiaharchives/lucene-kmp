/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnit.lucenekmp.index

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.gnit.lucenekmp.jdkport.CountDownLatch
import org.gnit.lucenekmp.jdkport.ReentrantLock
import org.gnit.lucenekmp.jdkport.TimeUnit
import org.gnit.lucenekmp.jdkport.currentThreadId
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/** Tests for [DocumentsWriterStallControl] */
@OptIn(ExperimentalAtomicApi::class)
class TestDocumentsWriterStallControl : LuceneTestCase() {
    @Test
    fun testSimpleStall() {
        val ctrl = DocumentsWriterStallControl()

        ctrl.updateStalled(false)
        var waitThreads = waitThreads(atLeast(1), ctrl)
        start(waitThreads)
        assertFalse(ctrl.hasBlocked())
        assertFalse(ctrl.anyStalledThreads())
        join(waitThreads)

        // now stall threads and wake them up again
        ctrl.updateStalled(true)
        waitThreads = waitThreads(atLeast(1), ctrl)
        start(waitThreads)
        awaitState(ThreadState.TIMED_WAITING, *waitThreads)
        assertTrue(ctrl.hasBlocked())
        assertTrue(ctrl.anyStalledThreads())
        ctrl.updateStalled(false)
        assertFalse(ctrl.anyStalledThreads())
        join(waitThreads)
    }

    @Test
    fun testRandom() {
        val ctrl = DocumentsWriterStallControl()
        ctrl.updateStalled(false)

        val stallThreads: Array<TestThread> =
            Array(atLeast(3)) {
                val stallProbability = 1 + random().nextInt(10)
                object : TestThread("staller") {
                    override fun runBody() {
                        val iters = atLeast(100)
                        for (j in 0..<iters) {
                            ctrl.updateStalled(random().nextInt(stallProbability) == 0)
                            if (random().nextInt(5) == 0) { // thread 0 only updates
                                state = ThreadState.TIMED_WAITING
                                ctrl.waitIfStalled()
                                state = ThreadState.RUNNABLE
                            }
                        }
                    }
                }
            }
        start(stallThreads)
        /*
         * use a 100 maximum iterations check to make sure we not hang forever. join will fail in
         * that case
         */
        var iterations = 0
        while (++iterations < 100 && !terminated(stallThreads)) {
            ctrl.updateStalled(false)
            if (random().nextBoolean()) {
                runBlocking { kotlinx.coroutines.yield() }
            } else {
                runBlocking { delay(1) }
            }
        }
        join(stallThreads)
    }

    @Test
    fun testAcquireReleaseRace() {
        val ctrl = DocumentsWriterStallControl()
        ctrl.updateStalled(false)
        val stop = AtomicBoolean(false)
        val checkPoint = AtomicBoolean(true)

        val numStallers = atLeast(1)
        val numReleasers = atLeast(1)
        val numWaiters = atLeast(1)
        val sync = Synchronizer(numStallers + numReleasers, numStallers + numReleasers + numWaiters)
        val threads = mutableListOf<TestThread>()
        val exceptions = ExceptionList()
        for (i in 0..<numReleasers) {
            threads.add(Updater(stop, checkPoint, ctrl, sync, true, exceptions))
        }
        for (i in numReleasers..<numReleasers + numStallers) {
            threads.add(Updater(stop, checkPoint, ctrl, sync, false, exceptions))
        }
        for (i in numReleasers + numStallers..<numReleasers + numStallers + numWaiters) {
            threads.add(Waiter(stop, checkPoint, ctrl, sync, exceptions))
        }
        val threadArray = threads.toTypedArray()

        start(threadArray)
        val iters = if (TEST_NIGHTLY) atLeast(10000) else atLeast(1000)
        val checkPointProbability = if (TEST_NIGHTLY) 0.5f else 0.1f
        for (i in 0..<iters) {
            if (checkPoint.load()) {
                assertTrue(
                    sync.updateJoin.await(10, TimeUnit.SECONDS),
                    "timed out waiting for update threads - deadlock?",
                )
                if (exceptions.isNotEmpty()) {
                    for (throwable in exceptions.snapshot()) {
                        throwable.printStackTrace()
                    }
                    fail("got exceptions in threads")
                }

                if (ctrl.hasBlocked() && ctrl.isHealthy) {
                    assertState(numReleasers, numStallers, numWaiters, threadArray, ctrl)
                }

                checkPoint.store(false)
                sync.waiter.countDown()
                sync.leftCheckpoint.await()
            }
            assertFalse(checkPoint.load())
            assertEquals(0, sync.waiter.getCount())
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
        assertState(numReleasers, numStallers, numWaiters, threadArray, ctrl)
        checkPoint.store(false)
        stop.store(true)
        sync.waiter.countDown()
        sync.leftCheckpoint.await()

        for (i in threadArray.indices) {
            ctrl.updateStalled(false)
            threadArray[i].join(2000)
            if (threadArray[i].isAlive() && threadArray[i] is Waiter) {
                if (threadArray[i].state == ThreadState.WAITING) {
                    fail("waiter is not released - anyThreadsStalled: ${ctrl.anyStalledThreads()}")
                }
            }
        }
    }

    private fun assertState(
        numReleasers: Int,
        numStallers: Int,
        numWaiters: Int,
        threads: Array<TestThread>,
        ctrl: DocumentsWriterStallControl,
    ) {
        var millisToSleep = 100
        while (ctrl.hasBlocked() && ctrl.isHealthy) {
            for (n in numReleasers + numStallers..<numReleasers + numStallers + numWaiters) {
                if (ctrl.isThreadQueued(threads[n].threadId)) {
                    if (millisToSleep < 60000) {
                        runBlocking { delay(millisToSleep.toLong()) }
                        millisToSleep *= 2
                        break
                    } else {
                        fail("control claims no stalled threads but waiter seems to be blocked ")
                    }
                }
            }
        }
    }

    private class Waiter(
        private val stop: AtomicBoolean,
        private val checkPoint: AtomicBoolean,
        private val ctrl: DocumentsWriterStallControl,
        private val sync: Synchronizer,
        private val exceptions: ExceptionList,
    ) : TestThread("waiter") {
        override fun runBody() {
            try {
                while (!stop.load()) {
                    state = ThreadState.WAITING
                    ctrl.waitIfStalled()
                    state = ThreadState.RUNNABLE
                    if (checkPoint.load()) {
                        assertTrue(sync.await())
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                exceptions.add(e)
            }
        }
    }

    private class Updater(
        private val stop: AtomicBoolean,
        private val checkPoint: AtomicBoolean,
        private val ctrl: DocumentsWriterStallControl,
        private val sync: Synchronizer,
        private val release: Boolean,
        private val exceptions: ExceptionList,
    ) : TestThread("updater") {
        override fun runBody() {
            try {
                while (!stop.load()) {
                    val internalIters = if (release && random().nextBoolean()) atLeast(5) else 1
                    for (i in 0..<internalIters) {
                        ctrl.updateStalled(random().nextBoolean())
                    }
                    if (checkPoint.load()) {
                        sync.updateJoin.countDown()
                        assertTrue(sync.await())
                        sync.leftCheckpoint.countDown()
                    }
                    if (random().nextBoolean()) {
                        runBlocking { kotlinx.coroutines.yield() }
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
        private fun terminated(threads: Array<TestThread>): Boolean {
            for (thread in threads) {
                if (ThreadState.TERMINATED != thread.state) return false
            }
            return true
        }

        private fun start(tostart: Array<TestThread>) {
            for (thread in tostart) {
                thread.start()
            }
            runBlocking { delay(1) } // let them start
        }

        private fun join(toJoin: Array<TestThread>) {
            for (thread in toJoin) {
                thread.join()
            }
        }

        private fun waitThreads(num: Int, ctrl: DocumentsWriterStallControl): Array<TestThread> {
            return Array(num) {
                object : TestThread("waitThread") {
                    override fun runBody() {
                        state = ThreadState.TIMED_WAITING
                        ctrl.waitIfStalled()
                    }
                }
            }
        }

        /** Waits for all incoming threads to be in wait() methods. */
        private fun awaitState(state: ThreadState, vararg threads: TestThread) {
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
                    runBlocking { kotlinx.coroutines.yield() }
                } else {
                    runBlocking { delay(1) }
                }
            }
        }
    }

    private class Synchronizer(
        numUpdater: Int,
        numThreads: Int,
    ) {
        @Volatile
        var waiter: CountDownLatch

        @Volatile
        var updateJoin: CountDownLatch

        @Volatile
        var leftCheckpoint: CountDownLatch

        init {
            waiter = CountDownLatch(1)
            updateJoin = CountDownLatch(numUpdater)
            leftCheckpoint = CountDownLatch(numUpdater)
            reset(numUpdater, numThreads)
        }

        fun reset(numUpdaters: Int, numThreads: Int) {
            this.waiter = CountDownLatch(1)
            this.updateJoin = CountDownLatch(numUpdaters)
            this.leftCheckpoint = CountDownLatch(numUpdaters)
        }

        fun await(): Boolean {
            return waiter.await(10, TimeUnit.SECONDS)
        }
    }

    private enum class ThreadState {
        NEW,
        RUNNABLE,
        WAITING,
        TIMED_WAITING,
        TERMINATED,
    }

    private abstract class TestThread(
        private val name: String,
    ) {
        @Volatile
        var state: ThreadState = ThreadState.NEW

        @Volatile
        var threadId: Long = -1L

        private var failure: Throwable? = null
        private var job: Job? = null

        abstract fun runBody()

        fun start() {
            job =
                CoroutineScope(Dispatchers.Default).launch {
                    threadId = currentThreadId()
                    state = ThreadState.RUNNABLE
                    try {
                        runBody()
                    } catch (t: Throwable) {
                        failure = t
                        throw t
                    } finally {
                        state = ThreadState.TERMINATED
                    }
                }
        }

        fun join(timeoutMillis: Long? = null) {
            runBlocking {
                if (timeoutMillis == null) {
                    job?.join()
                } else {
                    withTimeoutOrNull(timeoutMillis) {
                        job?.join()
                    }
                }
            }
            if (failure != null) {
                throw RuntimeException("thread $name failed", failure)
            }
        }

        fun isAlive(): Boolean {
            return state != ThreadState.NEW && state != ThreadState.TERMINATED
        }
    }

    private class ExceptionList {
        private val lock = ReentrantLock()
        private val list = mutableListOf<Throwable>()

        fun add(throwable: Throwable) {
            lock.lock()
            try {
                list.add(throwable)
            } finally {
                lock.unlock()
            }
        }

        fun isNotEmpty(): Boolean {
            lock.lock()
            try {
                return list.isNotEmpty()
            } finally {
                lock.unlock()
            }
        }

        fun snapshot(): List<Throwable> {
            lock.lock()
            try {
                return list.toList()
            } finally {
                lock.unlock()
            }
        }
    }
}
