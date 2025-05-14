package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PriorityQueueTest {

    @Test
    fun testAdd() {
        val pq = PriorityQueue<Int>()
        pq.offer(3)
        pq.offer(1)
        pq.offer(2)
        assertEquals(3, pq.size())
    }

    @Test
    fun testRemove() {
        val pq = PriorityQueue<Int>()
        pq.offer(3)
        pq.offer(1)
        pq.offer(2)
        assertEquals(1, pq.poll())
        assertEquals(2, pq.poll())
        assertEquals(3, pq.poll())
        assertNull(pq.poll())
    }

    @Test
    fun testPeek() {
        val pq = PriorityQueue<Int>()
        pq.offer(3)
        pq.offer(1)
        pq.offer(2)
        assertEquals(1, pq.peek())
        pq.poll()
        assertEquals(2, pq.peek())
    }

    @Test
    fun testPoll() {
        val pq = PriorityQueue<Int>()
        pq.offer(3)
        pq.offer(1)
        pq.offer(2)
        assertEquals(1, pq.poll())
        assertEquals(2, pq.poll())
        assertEquals(3, pq.poll())
        assertNull(pq.poll())
    }
}
