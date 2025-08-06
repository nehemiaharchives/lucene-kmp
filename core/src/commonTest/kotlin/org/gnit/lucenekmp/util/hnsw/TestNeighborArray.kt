package org.gnit.lucenekmp.util.hnsw

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.Bits
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class TestNeighborArray : LuceneTestCase() {

    private val dummyScorer = TestRandomVectorScorer { 0f }

    @Test
    fun testScoresDescOrder() {
        val neighbors = NeighborArray(10, true)
        neighbors.addInOrder(0, 1f)
        neighbors.addInOrder(1, 0.8f)

        val ex = expectThrows(IllegalArgumentException::class) { neighbors.addInOrder(2, 0.9f) }
        assertTrue(ex!!.message!!.startsWith("Nodes are added in the incorrect order!"), ex.message)

        neighbors.insertSorted(3, 0.9f)
        assertScoresEqual(floatArrayOf(1f, 0.9f, 0.8f), neighbors)
        assertNodesEqual(intArrayOf(0, 3, 1), neighbors)

        neighbors.insertSorted(4, 1f)
        assertScoresEqual(floatArrayOf(1f, 1f, 0.9f, 0.8f), neighbors)
        assertNodesEqual(intArrayOf(0, 4, 3, 1), neighbors)

        neighbors.insertSorted(5, 1.1f)
        assertScoresEqual(floatArrayOf(1.1f, 1f, 1f, 0.9f, 0.8f), neighbors)
        assertNodesEqual(intArrayOf(5, 0, 4, 3, 1), neighbors)

        neighbors.insertSorted(6, 0.8f)
        assertScoresEqual(floatArrayOf(1.1f, 1f, 1f, 0.9f, 0.8f, 0.8f), neighbors)
        assertNodesEqual(intArrayOf(5, 0, 4, 3, 1, 6), neighbors)

        neighbors.insertSorted(7, 0.8f)
        assertScoresEqual(floatArrayOf(1.1f, 1f, 1f, 0.9f, 0.8f, 0.8f, 0.8f), neighbors)
        assertNodesEqual(intArrayOf(5, 0, 4, 3, 1, 6, 7), neighbors)

        neighbors.removeIndex(2)
        assertScoresEqual(floatArrayOf(1.1f, 1f, 0.9f, 0.8f, 0.8f, 0.8f), neighbors)
        assertNodesEqual(intArrayOf(5, 0, 3, 1, 6, 7), neighbors)

        neighbors.removeIndex(0)
        assertScoresEqual(floatArrayOf(1f, 0.9f, 0.8f, 0.8f, 0.8f), neighbors)
        assertNodesEqual(intArrayOf(0, 3, 1, 6, 7), neighbors)

        neighbors.removeIndex(4)
        assertScoresEqual(floatArrayOf(1f, 0.9f, 0.8f, 0.8f), neighbors)
        assertNodesEqual(intArrayOf(0, 3, 1, 6), neighbors)

        neighbors.removeLast()
        assertScoresEqual(floatArrayOf(1f, 0.9f, 0.8f), neighbors)
        assertNodesEqual(intArrayOf(0, 3, 1), neighbors)

        neighbors.insertSorted(8, 0.9f)
        assertScoresEqual(floatArrayOf(1f, 0.9f, 0.9f, 0.8f), neighbors)
        assertNodesEqual(intArrayOf(0, 3, 8, 1), neighbors)
    }

    @Test
    fun testScoresAscOrder() {
        val neighbors = NeighborArray(10, false)
        neighbors.addInOrder(0, 0.1f)
        neighbors.addInOrder(1, 0.3f)

        val ex = expectThrows(IllegalArgumentException::class) { neighbors.addInOrder(2, 0.15f) }
        assertTrue(ex!!.message!!.startsWith("Nodes are added in the incorrect order!"), ex.message)

        neighbors.insertSorted(3, 0.3f)
        assertScoresEqual(floatArrayOf(0.1f, 0.3f, 0.3f), neighbors)
        assertNodesEqual(intArrayOf(0, 1, 3), neighbors)

        neighbors.insertSorted(4, 0.2f)
        assertScoresEqual(floatArrayOf(0.1f, 0.2f, 0.3f, 0.3f), neighbors)
        assertNodesEqual(intArrayOf(0, 4, 1, 3), neighbors)

        neighbors.insertSorted(5, 0.05f)
        assertScoresEqual(floatArrayOf(0.05f, 0.1f, 0.2f, 0.3f, 0.3f), neighbors)
        assertNodesEqual(intArrayOf(5, 0, 4, 1, 3), neighbors)

        neighbors.insertSorted(6, 0.2f)
        assertScoresEqual(floatArrayOf(0.05f, 0.1f, 0.2f, 0.2f, 0.3f, 0.3f), neighbors)
        assertNodesEqual(intArrayOf(5, 0, 4, 6, 1, 3), neighbors)

        neighbors.insertSorted(7, 0.2f)
        assertScoresEqual(floatArrayOf(0.05f, 0.1f, 0.2f, 0.2f, 0.2f, 0.3f, 0.3f), neighbors)
        assertNodesEqual(intArrayOf(5, 0, 4, 6, 7, 1, 3), neighbors)

        neighbors.removeIndex(2)
        assertScoresEqual(floatArrayOf(0.05f, 0.1f, 0.2f, 0.2f, 0.3f, 0.3f), neighbors)
        assertNodesEqual(intArrayOf(5, 0, 6, 7, 1, 3), neighbors)

        neighbors.removeIndex(0)
        assertScoresEqual(floatArrayOf(0.1f, 0.2f, 0.2f, 0.3f, 0.3f), neighbors)
        assertNodesEqual(intArrayOf(0, 6, 7, 1, 3), neighbors)

        neighbors.removeIndex(4)
        assertScoresEqual(floatArrayOf(0.1f, 0.2f, 0.2f, 0.3f), neighbors)
        assertNodesEqual(intArrayOf(0, 6, 7, 1), neighbors)

        neighbors.removeLast()
        assertScoresEqual(floatArrayOf(0.1f, 0.2f, 0.2f), neighbors)
        assertNodesEqual(intArrayOf(0, 6, 7), neighbors)

        neighbors.insertSorted(8, 0.01f)
        assertScoresEqual(floatArrayOf(0.01f, 0.1f, 0.2f, 0.2f), neighbors)
        assertNodesEqual(intArrayOf(8, 0, 6, 7), neighbors)
    }

    @Test
    fun testSortAsc() {
        val neighbors = NeighborArray(10, false)
        neighbors.addOutOfOrder(1, 2f)
        expectThrows(IllegalArgumentException::class) { neighbors.addInOrder(1, 2f) }
        neighbors.addOutOfOrder(2, 3f)
        neighbors.addOutOfOrder(5, 6f)
        neighbors.addOutOfOrder(3, 4f)
        neighbors.addOutOfOrder(7, 8f)
        neighbors.addOutOfOrder(6, 7f)
        neighbors.addOutOfOrder(4, 5f)
        var unchecked = neighbors.sort(dummyScorer)
        assertContentEquals(intArrayOf(0, 1, 2, 3, 4, 5, 6), unchecked)
        assertNodesEqual(intArrayOf(1, 2, 3, 4, 5, 6, 7), neighbors)
        assertScoresEqual(floatArrayOf(2f, 3f, 4f, 5f, 6f, 7f, 8f), neighbors)

        val neighbors2 = NeighborArray(10, false)
        neighbors2.addInOrder(0, 1f)
        neighbors2.addInOrder(1, 2f)
        neighbors2.addInOrder(4, 5f)
        neighbors2.addOutOfOrder(2, 3f)
        neighbors2.addOutOfOrder(6, 7f)
        neighbors2.addOutOfOrder(5, 6f)
        neighbors2.addOutOfOrder(3, 4f)
        unchecked = neighbors2.sort(dummyScorer)
        assertContentEquals(intArrayOf(2, 3, 5, 6), unchecked)
        assertNodesEqual(intArrayOf(0, 1, 2, 3, 4, 5, 6), neighbors2)
        assertScoresEqual(floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f, 7f), neighbors2)
    }

    @Test
    fun testSortDesc() {
        val neighbors = NeighborArray(10, true)
        neighbors.addOutOfOrder(1, 7f)
        expectThrows(IllegalArgumentException::class) { neighbors.addInOrder(1, 2f) }
        neighbors.addOutOfOrder(2, 6f)
        neighbors.addOutOfOrder(5, 3f)
        neighbors.addOutOfOrder(3, 5f)
        neighbors.addOutOfOrder(7, 1f)
        neighbors.addOutOfOrder(6, 2f)
        neighbors.addOutOfOrder(4, 4f)
        var unchecked = neighbors.sort(dummyScorer)
        assertContentEquals(intArrayOf(0, 1, 2, 3, 4, 5, 6), unchecked)
        assertNodesEqual(intArrayOf(1, 2, 3, 4, 5, 6, 7), neighbors)
        assertScoresEqual(floatArrayOf(7f, 6f, 5f, 4f, 3f, 2f, 1f), neighbors)

        val neighbors2 = NeighborArray(10, true)
        neighbors2.addInOrder(1, 7f)
        neighbors2.addInOrder(2, 6f)
        neighbors2.addInOrder(5, 3f)
        neighbors2.addOutOfOrder(3, 5f)
        neighbors2.addOutOfOrder(7, 1f)
        neighbors2.addOutOfOrder(6, 2f)
        neighbors2.addOutOfOrder(4, 4f)
        unchecked = neighbors2.sort(dummyScorer)
        assertContentEquals(intArrayOf(2, 3, 5, 6), unchecked)
        assertNodesEqual(intArrayOf(1, 2, 3, 4, 5, 6, 7), neighbors2)
        assertScoresEqual(floatArrayOf(7f, 6f, 5f, 4f, 3f, 2f, 1f), neighbors2)
    }

    @Test
    fun testAddwithScoringFunction() {
        val neighbors = NeighborArray(10, true)
        neighbors.addOutOfOrder(1, Float.NaN)
        expectThrows(IllegalArgumentException::class) { neighbors.addInOrder(1, 2f) }
        neighbors.addOutOfOrder(2, Float.NaN)
        neighbors.addOutOfOrder(5, Float.NaN)
        neighbors.addOutOfOrder(3, Float.NaN)
        neighbors.addOutOfOrder(7, Float.NaN)
        neighbors.addOutOfOrder(6, Float.NaN)
        neighbors.addOutOfOrder(4, Float.NaN)
        val unchecked = neighbors.sort(TestRandomVectorScorer { nodeId -> (7 - nodeId + 1).toFloat() })
        assertContentEquals(intArrayOf(0, 1, 2, 3, 4, 5, 6), unchecked)
        assertNodesEqual(intArrayOf(1, 2, 3, 4, 5, 6, 7), neighbors)
        assertScoresEqual(floatArrayOf(7f, 6f, 5f, 4f, 3f, 2f, 1f), neighbors)
    }

    @Test
    fun testAddwithScoringFunctionLargeOrd() {
        val neighbors = NeighborArray(10, true)
        neighbors.addOutOfOrder(11, Float.NaN)
        expectThrows(IllegalArgumentException::class) { neighbors.addInOrder(1, 2f) }
        neighbors.addOutOfOrder(12, Float.NaN)
        neighbors.addOutOfOrder(15, Float.NaN)
        neighbors.addOutOfOrder(13, Float.NaN)
        neighbors.addOutOfOrder(17, Float.NaN)
        neighbors.addOutOfOrder(16, Float.NaN)
        neighbors.addOutOfOrder(14, Float.NaN)
        val unchecked = neighbors.sort(TestRandomVectorScorer { nodeId -> (7 - nodeId + 11).toFloat() })
        assertContentEquals(intArrayOf(0, 1, 2, 3, 4, 5, 6), unchecked)
        assertNodesEqual(intArrayOf(11, 12, 13, 14, 15, 16, 17), neighbors)
        assertScoresEqual(floatArrayOf(7f, 6f, 5f, 4f, 3f, 2f, 1f), neighbors)
    }

    private fun assertScoresEqual(scores: FloatArray, neighbors: NeighborArray) {
        for (i in scores.indices) {
            assertEquals(scores[i], neighbors.scores()[i], 0.01f)
        }
    }

    private fun assertNodesEqual(nodes: IntArray, neighbors: NeighborArray) {
        for (i in nodes.indices) {
            assertEquals(nodes[i], neighbors.nodes()[i])
        }
    }

    fun interface TestRandomVectorScorer : RandomVectorScorer {
        override fun maxOrd(): Int {
            throw UnsupportedOperationException()
        }
        override fun ordToDoc(ord: Int): Int {
            throw UnsupportedOperationException()
        }
        override fun getAcceptOrds(acceptDocs: Bits?): Bits? {
            throw UnsupportedOperationException()
        }
    }
}

