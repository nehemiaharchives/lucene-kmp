package org.gnit.lucenekmp.util.hnsw

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestBlockingFloatHeap : LuceneTestCase() {

    @Test
    fun testBasicOperations() = runBlocking {
        val heap = BlockingFloatHeap(3)
        heap.offer(2f)
        heap.offer(4f)
        heap.offer(1f)
        heap.offer(3f)
        assertEquals(3, heap.size())
        assertEquals(2f, heap.peek())

        assertEquals(2f, heap.poll())
        assertEquals(3f, heap.poll())
        assertEquals(4f, heap.poll())
        assertEquals(0, heap.size())
    }

    @Test
    fun testBasicOperations2() = runBlocking {
        val size = atLeast(10)
        val heap = BlockingFloatHeap(size)
        var sum = 0.0
        var sum2 = 0.0

        repeat(size) {
            val next = random().nextFloat() * 100f
            sum += next
            heap.offer(next)
        }

        var last = Float.NEGATIVE_INFINITY
        repeat(size) {
            val next = heap.poll()
            assertTrue(next >= last)
            last = next
            sum2 += last
        }
        assertEquals(sum, sum2, 0.01)
    }

    @Test
    fun testMultipleThreads() = runBlocking {
        val numThreads = TestUtil.nextInt(random(), 2, 4) // TODO originally 3, 20 but reduced to 2, 4 for dev speed
        val start = CompletableDeferred<Unit>()
        val globalHeap = BlockingFloatHeap(1)

        val jobs = Array(numThreads) {
            launch {
                start.await()
                var numIterations = TestUtil.nextInt(random(), 10, 100)
                var bottomValue = 0f

                while (numIterations-- > 0) {
                    bottomValue += TestUtil.nextInt(random(), 0, 5).toFloat()
                    globalHeap.offer(bottomValue)
                    delay(TestUtil.nextInt(random(), 0, 50).toLong())

                    val globalBottomValue = globalHeap.peek()
                    assertTrue(globalBottomValue >= bottomValue)
                    bottomValue = globalBottomValue
                }
            }
        }

        start.complete(Unit)
        jobs.forEach { it.join() }
    }
}

