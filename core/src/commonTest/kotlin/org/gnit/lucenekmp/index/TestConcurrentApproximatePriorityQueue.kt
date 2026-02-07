package org.gnit.lucenekmp.index

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TestConcurrentApproximatePriorityQueue : LuceneTestCase() {

    @Test
    fun testPollFromSameThread() = runTest {
        val pq = ConcurrentApproximatePriorityQueue<Int>(
            TestUtil.nextInt(
                random(),
                ConcurrentApproximatePriorityQueue.MIN_CONCURRENCY,
                ConcurrentApproximatePriorityQueue.MAX_CONCURRENCY
            )
        )
        pq.add(3, 3)
        pq.add(10, 10)
        pq.add(7, 7)
        assertEquals(10, pq.poll { true })
        assertEquals(7, pq.poll { true })
        assertEquals(3, pq.poll { true })
        assertNull(pq.poll { true })
    }

    @Test
    fun testPollFromDifferentThread() = runTest {
        val pq = ConcurrentApproximatePriorityQueue<Int>(
            TestUtil.nextInt(
                random(),
                ConcurrentApproximatePriorityQueue.MIN_CONCURRENCY,
                ConcurrentApproximatePriorityQueue.MAX_CONCURRENCY
            )
        )
        pq.add(3, 3)
        pq.add(10, 10)
        pq.add(7, 7)
        val job = launch(Dispatchers.Default) {
            assertEquals(10, pq.poll { true })
            assertEquals(7, pq.poll { true })
            assertEquals(3, pq.poll { true })
            assertNull(pq.poll { true })
        }
        job.join()
    }

    @Test
    fun testCurrentLockIsBusy() = runTest {
        val pq = ConcurrentApproximatePriorityQueue<Int>(
            TestUtil.nextInt(
                random(),
                2,
                ConcurrentApproximatePriorityQueue.MAX_CONCURRENCY
            )
        )
        pq.add(3, 3)
        val takeLock = CompletableDeferred<Unit>()
        val releaseLock = CompletableDeferred<Unit>()
        val job = launch(Dispatchers.Default) {
            var queueIndex = -1
            for (i in 0 until pq.queues.size) {
                if (!pq.queues[i].isEmpty) {
                    queueIndex = i
                    break
                }
            }
            assertTrue(pq.locks[queueIndex].tryLock())
            takeLock.complete(Unit)
            // Do not suspend while holding ReentrantLock: coroutine resume may switch worker
            // threads and violate thread ownership on unlock.
            while (!releaseLock.isCompleted) {
            }
            pq.locks[queueIndex].unlock()
        }
        takeLock.await()
        pq.add(1, 1)
        assertEquals(1, pq.poll { true })
        releaseLock.complete(Unit)
        job.join()
        assertEquals(3, pq.poll { true })
        assertNull(pq.poll { true })
    }
}
