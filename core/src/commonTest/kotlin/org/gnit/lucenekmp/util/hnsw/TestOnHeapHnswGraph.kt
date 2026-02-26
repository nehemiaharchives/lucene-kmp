package org.gnit.lucenekmp.util.hnsw

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Test OnHeapHnswGraph's behavior specifically, for more complex test, see [HnswGraphTestCase]
 */
class TestOnHeapHnswGraph : LuceneTestCase() {

    /* assert exception will be thrown when we add out of bound node to a fixed size graph */
    @Test
    fun testNoGrowth() {
        val graph = OnHeapHnswGraph(10, 100)
        expectThrows(IllegalStateException::class) { graph.addNode(1, 100) }
    }

    /* AssertionError will be thrown if we add a node not from top most level,
    (likely NPE will be thrown in prod) */
    @Test
    fun testAddLevelOutOfOrder() {
        val graph = OnHeapHnswGraph(10, -1)
        graph.addNode(0, 0)
        expectThrows(IllegalArgumentException::class) { graph.addNode(1, 0) }
    }

    /* assert exception will be thrown when we call getNodeOnLevel for an incomplete graph */
    @Test
    fun testIncompleteGraphThrow() {
        val graph = OnHeapHnswGraph(10, -1)
        graph.addNode(1, 0)
        graph.addNode(0, 0)
        assertEquals(1, graph.getNodesOnLevel(1).size())
        graph.addNode(0, 5)
        expectThrows(IllegalStateException::class) { graph.getNodesOnLevel(0) }
    }

    @Test
    fun testGraphGrowth() {
        val graph = OnHeapHnswGraph(10, -1)
        val levelToNodes = ArrayList<MutableList<Int>>()
        val maxLevel = 5
        for (i in 0 until maxLevel) {
            levelToNodes.add(ArrayList())
        }
        for (i in 0..100) {
            val level = random().nextInt(maxLevel)
            for (l in level downTo 0) {
                graph.addNode(l, i)
                graph.trySetNewEntryNode(i, l)
                if (l > graph.numLevels() - 1) {
                    graph.tryPromoteNewEntryNode(i, l, graph.numLevels() - 1)
                }
                levelToNodes[l].add(i)
            }
        }
        assertGraphEquals(graph, levelToNodes)
    }

    @Test
    fun testGraphBuildOutOfOrder() {
        val graph = OnHeapHnswGraph(10, -1)
        val levelToNodes = ArrayList<MutableList<Int>>()
        val maxLevel = 5
        val numNodes = 100
        for (i in 0 until maxLevel) {
            levelToNodes.add(ArrayList())
        }
        val insertions = IntArray(numNodes)
        for (i in 0 until numNodes) {
            insertions[i] = i
        }
        // shuffle the insertion order
        for (i in 0 until 40) {
            val pos1 = random().nextInt(numNodes)
            val pos2 = random().nextInt(numNodes)
            val tmp = insertions[pos1]
            insertions[pos1] = insertions[pos2]
            insertions[pos2] = tmp
        }

        for (i in insertions) {
            val level = random().nextInt(maxLevel)
            for (l in level downTo 0) {
                graph.addNode(l, i)
                graph.trySetNewEntryNode(i, l)
                if (l > graph.numLevels() - 1) {
                    graph.tryPromoteNewEntryNode(i, l, graph.numLevels() - 1)
                }
                levelToNodes[l].add(i)
            }
        }

        for (i in 0 until maxLevel) {
            levelToNodes[i].sort()
        }

        assertGraphEquals(graph, levelToNodes)
    }

    companion object {
        private fun assertGraphEquals(graph: OnHeapHnswGraph, levelToNodes: List<List<Int>>) {
            for (l in 0 until graph.numLevels()) {
                val nodesIterator = graph.getNodesOnLevel(l)
                assertEquals(levelToNodes[l].size, nodesIterator.size())
                var idx = 0
                while (nodesIterator.hasNext()) {
                    assertEquals(levelToNodes[l][idx++], nodesIterator.next())
                }
            }
        }
    }
}
