package org.gnit.lucenekmp.util.hnsw

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestNeighborQueue : LuceneTestCase() {

    @Test
    fun testNeighborsProduct() {
        // make sure we have the sign correct
        val nn = NeighborQueue(2, false)
        assertTrue(nn.insertWithOverflow(2, 0.5f))
        assertTrue(nn.insertWithOverflow(1, 0.2f))
        assertTrue(nn.insertWithOverflow(3, 1f))
        assertEquals(0.5f, nn.topScore(), 0f)
        nn.pop()
        assertEquals(1f, nn.topScore(), 0f)
        nn.pop()
    }

    @Test
    fun testNeighborsMaxHeap() {
        val nn = NeighborQueue(2, true)
        assertTrue(nn.insertWithOverflow(2, 2f))
        assertTrue(nn.insertWithOverflow(1, 1f))
        assertFalse(nn.insertWithOverflow(3, 3f))
        assertEquals(2f, nn.topScore(), 0f)
        nn.pop()
        assertEquals(1f, nn.topScore(), 0f)
    }

    @Test
    fun testTopMaxHeap() {
        val nn = NeighborQueue(2, true)
        nn.add(1, 2f)
        nn.add(2, 1f)
        // lower scores are better; highest score on top
        assertEquals(2f, nn.topScore(), 0f)
        assertEquals(1, nn.topNode())
    }

    @Test
    fun testTopMinHeap() {
        val nn = NeighborQueue(2, false)
        nn.add(1, 0.5f)
        nn.add(2, -0.5f)
        // higher scores are better; lowest score on top
        assertEquals(-0.5f, nn.topScore(), 0f)
        assertEquals(2, nn.topNode())
    }

    @Test
    fun testVisitedCount() {
        val nn = NeighborQueue(2, false)
        nn.setVisitedCount(100)
        assertEquals(100, nn.visitedCount())
    }

    @Test
    fun testClear() {
        val nn = NeighborQueue(2, false)
        nn.add(1, 1.1f)
        nn.add(2, -2.2f)
        nn.setVisitedCount(42)
        nn.markIncomplete()
        nn.clear()

        assertEquals(0, nn.size())
        assertEquals(0, nn.visitedCount())
        assertFalse(nn.incomplete())
    }

    @Test
    fun testMaxSizeQueue() {
        val nn = NeighborQueue(2, false)
        nn.add(1, 1f)
        nn.add(2, 2f)
        assertEquals(2, nn.size())
        assertEquals(1, nn.topNode())

        // insertWithOverflow does not extend the queue
        nn.insertWithOverflow(3, 3f)
        assertEquals(2, nn.size())
        assertEquals(2, nn.topNode())

        // add does extend the queue beyond maxSize
        nn.add(4, 1f)
        assertEquals(3, nn.size())
    }

    @Test
    fun testUnboundedQueue() {
        val nn = NeighborQueue(1, true)
        var maxScore = -2f
        var maxNode = -1
        for (i in 0 until 256) {
            // initial size is 32
            val score = random().nextFloat()
            if (score > maxScore) {
                maxScore = score
                maxNode = i
            }
            nn.add(i, score)
        }
        assertEquals(maxScore, nn.topScore(), 0f)
        assertEquals(maxNode, nn.topNode())
    }

    @Test
    fun testInvalidArguments() {
        expectThrows(IllegalArgumentException::class) { NeighborQueue(0, false) }
    }

    @Test
    fun testToString() {
        assertEquals("Neighbors[0]", NeighborQueue(2, false).toString())
    }
}
