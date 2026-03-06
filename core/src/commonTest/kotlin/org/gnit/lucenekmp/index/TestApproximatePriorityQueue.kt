package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TestApproximatePriorityQueue : LuceneTestCase() {

    @Test
    fun testBasics() {
        val pq = ApproximatePriorityQueue<Long>()
        pq.add(8L, 8L)
        pq.add(32L, 32L)
        pq.add(0L, 0L)
        assertFalse(pq.isEmpty)
        assertEquals(32L, pq.poll { true })
        assertFalse(pq.isEmpty)
        assertEquals(8L, pq.poll { true })
        assertFalse(pq.isEmpty)
        assertEquals(0L, pq.poll { true })
        assertTrue(pq.isEmpty)
        assertNull(pq.poll { true })
    }

    @Test
    fun testPollThenAdd() {
        val pq = ApproximatePriorityQueue<Long>()
        pq.add(8L, 8L)
        assertEquals(8L, pq.poll { true })
        assertNull(pq.poll { true })
        pq.add(0L, 0L)
        assertEquals(0L, pq.poll { true })
        assertNull(pq.poll { true })
        pq.add(0L, 0L)
        assertEquals(0L, pq.poll { true })
        assertNull(pq.poll { true })
    }

    @Test
    fun testCollision() {
        val pq = ApproximatePriorityQueue<Long>()
        pq.add(2L, 2L)
        pq.add(1L, 1L)
        pq.add(0L, 0L)
        pq.add(3L, 3L) // Same nlz as 2
        assertFalse(pq.isEmpty)
        assertEquals(2L, pq.poll { true })
        assertFalse(pq.isEmpty)
        assertEquals(1L, pq.poll { true })
        assertFalse(pq.isEmpty)
        assertEquals(3L, pq.poll { true })
        assertFalse(pq.isEmpty)
        assertEquals(0L, pq.poll { true })
        assertTrue(pq.isEmpty)
        assertNull(pq.poll { true })
    }

    @Test
    fun testPollWithPredicate() {
        val pq = ApproximatePriorityQueue<Long>()
        pq.add(8L, 8L)
        pq.add(32L, 32L)
        pq.add(0L, 0L)
        assertEquals(8L, pq.poll { x -> x == 8L })
        assertNull(pq.poll { x -> x == 8L })
        assertFalse(pq.isEmpty)
    }

    @Test
    fun testCollisionPollWithPredicate() {
        val pq = ApproximatePriorityQueue<Long>()
        pq.add(2L, 2L)
        pq.add(1L, 1L)
        pq.add(0L, 0L)
        pq.add(3L, 3L) // Same nlz as 2
        assertEquals(1L, pq.poll { x -> x % 2L == 1L })
        assertEquals(3L, pq.poll { x -> x % 2L == 1L })
        assertNull(pq.poll { x -> x % 2L == 1L })
        assertFalse(pq.isEmpty)
    }

    @Test
    fun testRemove() {
        val pq = ApproximatePriorityQueue<Long>()
        pq.add(8L, 8L)
        pq.add(32L, 32L)
        pq.add(0L, 0L)

        assertFalse(pq.remove(16L))
        assertFalse(pq.remove(9L))
        assertTrue(pq.remove(8L))
        assertTrue(pq.remove(0L))
        assertFalse(pq.remove(0L))
        assertTrue(pq.remove(32L))
        assertTrue(pq.isEmpty)
    }
}
