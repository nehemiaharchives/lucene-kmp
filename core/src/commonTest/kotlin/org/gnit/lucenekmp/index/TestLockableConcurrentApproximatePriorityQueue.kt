package org.gnit.lucenekmp.index

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.gnit.lucenekmp.jdkport.Condition
import org.gnit.lucenekmp.jdkport.Lock
import org.gnit.lucenekmp.jdkport.ReentrantLock
import org.gnit.lucenekmp.jdkport.TimeUnit
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test
import kotlin.test.assertNotNull

class TestLockableConcurrentApproximatePriorityQueue : LuceneTestCase() {

    private class WeightedLock : Lock {
        private val lock: Lock = ReentrantLock()
        var weight: Long = 0

        override fun lock() {
            lock.lock()
        }

        override fun lockInterruptibly() {
            throw UnsupportedOperationException()
        }

        override fun tryLock(): Boolean {
            return lock.tryLock()
        }

        override fun tryLock(time: Long, unit: TimeUnit): Boolean {
            throw UnsupportedOperationException()
        }

        override fun unlock() {
            lock.unlock()
        }

        override fun newCondition(): Condition {
            throw UnsupportedOperationException()
        }
    }

    @Test
    fun testNeverReturnNullOnNonEmptyQueue() = runBlocking {
        val iters = atLeast(10)
        repeat(iters) {
            val concurrency = TestUtil.nextInt(random(), 1, 16)
            val queue = LockableConcurrentApproximatePriorityQueue<WeightedLock>(concurrency)
            val numThreads = TestUtil.nextInt(random(), 2, 16)
            val startingGun = CompletableDeferred<Unit>()
            val jobs = Array(numThreads) {
                launch {
                    startingGun.await()
                    var lock = WeightedLock()
                    lock.lock()
                    lock.weight++
                    queue.addAndUnlock(lock, lock.weight)
                    repeat(10_000) {
                        val l = queue.lockAndPoll()
                        assertNotNull(l)
                        queue.addAndUnlock(l, l.hashCode().toLong())
                    }
                }
            }
            startingGun.complete(Unit)
            jobs.forEach { it.join() }
        }
    }
}

