package org.gnit.lucenekmp.util.hnsw

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestFloatHeap : LuceneTestCase() {

    @Test
    fun testBasicOperations() {
        val heap = FloatHeap(3)
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
    fun testBasicOperations2() {
        val size = atLeast(10)
        val heap = FloatHeap(size)
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
    fun testClear() {
        val heap = FloatHeap(3)
        heap.offer(20f)
        heap.offer(40f)
        heap.offer(30f)
        assertEquals(3, heap.size())
        assertEquals(20f, heap.peek())

        heap.clear()
        assertEquals(0, heap.size())
        assertEquals(20f, heap.peek())

        heap.offer(15f)
        heap.offer(35f)
        assertEquals(2, heap.size())
        assertEquals(15f, heap.peek())

        assertEquals(15f, heap.poll())
        assertEquals(35f, heap.poll())
        assertEquals(0, heap.size())
    }
}

