package org.gnit.lucenekmp.util.hnsw

import org.gnit.lucenekmp.search.DocIdSetIterator.Companion.NO_MORE_DOCS
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.jdkport.assert
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestHnswUtil : LuceneTestCase() {

    @Test
    fun testTreeWithCycle() {
        // test a graph that is a tree - this is rooted from its root node, not rooted
        // from any other node, and not strongly connected
        val nodes: Array<Array<IntArray?>> = arrayOf(
            arrayOf<IntArray?>(
                intArrayOf(1, 2), // node 0
                intArrayOf(3, 4), // node 1
                intArrayOf(5, 6), // node 2
                intArrayOf(), intArrayOf(), intArrayOf(), intArrayOf(0)
            )
        )
        val graph: HnswGraph = MockGraph(nodes)
        assertTrue(HnswUtil.isRooted(graph))
        assertEquals(listOf(7), HnswUtil.componentSizes(graph))
    }

    @Test
    fun testBackLinking() {
        // test a graph that is a tree - this is rooted from its root node, not rooted
        // from any other node, and not strongly connected
        val nodes: Array<Array<IntArray?>> = arrayOf(
            arrayOf<IntArray?>(
                intArrayOf(1, 2), // node 0
                intArrayOf(3, 4), // node 1
                intArrayOf(0), // node 2
                intArrayOf(1), intArrayOf(1), intArrayOf(1), intArrayOf(1)
            )
        )
        val graph: HnswGraph = MockGraph(nodes)
        assertFalse(HnswUtil.isRooted(graph))
        // [ {0, 1, 2, 3, 4}, {5}, {6}
        assertEquals(listOf(5, 1, 1), HnswUtil.componentSizes(graph))
    }

    @Test
    fun testChain() {
        // test a graph that is a chain - this is rooted from every node, thus strongly connected
        val nodes: Array<Array<IntArray?>> =
            arrayOf(arrayOf<IntArray?>(intArrayOf(1), intArrayOf(2), intArrayOf(3), intArrayOf(0)))
        val graph: HnswGraph = MockGraph(nodes)
        assertTrue(HnswUtil.isRooted(graph))
        assertEquals(listOf(4), HnswUtil.componentSizes(graph))
    }

    @Test
    fun testTwoChains() {
        // test a graph that is two chains
        val nodes: Array<Array<IntArray?>> =
            arrayOf(arrayOf<IntArray?>(intArrayOf(2), intArrayOf(3), intArrayOf(0), intArrayOf(1)))
        val graph: HnswGraph = MockGraph(nodes)
        assertFalse(HnswUtil.isRooted(graph))
        assertEquals(listOf(2, 2), HnswUtil.componentSizes(graph))
    }

    @Test
    fun testLevels() {
        // test a graph that has three levels
        val nodes: Array<Array<IntArray?>> = arrayOf(
            arrayOf<IntArray?>(intArrayOf(1, 2), intArrayOf(3), intArrayOf(0), intArrayOf(0)),
            arrayOf<IntArray?>(intArrayOf(2), null, intArrayOf(0), null),
            arrayOf<IntArray?>(intArrayOf(), null, null, null)
        )
        val graph: HnswGraph = MockGraph(nodes)
        assertTrue(HnswUtil.isRooted(graph))
        assertEquals(listOf(4), HnswUtil.componentSizes(graph))
    }

    @Test
    fun testLevelsNotRooted() {
        // test a graph that has two levels with an orphaned node
        val nodes: Array<Array<IntArray?>> = arrayOf(
            arrayOf<IntArray?>(intArrayOf(1), intArrayOf(0), intArrayOf(0)),
            arrayOf<IntArray?>(intArrayOf(), null, null)
        )
        val graph: HnswGraph = MockGraph(nodes)
        assertFalse(HnswUtil.isRooted(graph))
        assertEquals(listOf(2, 1), HnswUtil.componentSizes(graph))
    }

    @Test
    fun testRandom() {
        for (i in 0 until atLeast(10)) {
            // test on a random directed graph comparing against a brute force algorithm
            val numNodes = random().nextInt(1, 100)
            val numLevels = ceil(ln(numNodes.toDouble())).toInt()
            val nodes = arrayOfNulls<Array<IntArray?>>(numLevels)
            for (level in numLevels - 1 downTo 0) {
                val levelNodes = arrayOfNulls<IntArray>(numNodes)
                for (node in 0 until numNodes) {
                    if (level > 0) {
                        if ((level == numLevels - 1 && node > 0)
                            || (level < numLevels - 1 && nodes[level + 1]!![node] == null)
                        ) {
                            if (random().nextFloat() > kotlin.math.E.pow(-level.toDouble())) {
                                // skip some nodes, more on higher levels while ensuring every node present on a
                                // given level is present on all lower levels. Also ensure node 0 is always present.
                                continue
                            }
                        }
                    }
                    var numNbrs = random().nextInt((numNodes + 7) / 8)
                    if (level == 0) {
                        numNbrs *= 2
                    }
                    levelNodes[node] = IntArray(numNbrs)
                    for (nbr in 0 until numNbrs) {
                        while (true) {
                            val randomNbr = random().nextInt(numNodes)
                            if (levelNodes[randomNbr] != null) {
                                // allow self-linking; this doesn't arise in HNSW but it's valid more generally
                                levelNodes[node]!![nbr] = randomNbr
                                break
                            }
                            // nbr not on this level, try again
                        }
                    }
                }
                nodes[level] = levelNodes
            }
            val graph = MockGraph(nodes.requireNoNulls())
            assertEquals(isRooted(nodes.requireNoNulls()), HnswUtil.isRooted(graph))
        }
    }

    private fun isRooted(nodes: Array<Array<IntArray?>>): Boolean {
        for (level in nodes.size - 1 downTo 0) {
            if (isRooted(nodes, level).not()) {
                return false
            }
        }
        return true
    }

    private fun isRooted(nodes: Array<Array<IntArray?>>, level: Int): Boolean {
        // check that the graph is rooted in the union of the entry nodes' trees
        val entryPoints: Array<IntArray?> =
            if (level == nodes.size - 1) {
                // entry into the top level is from a single entry point, fixed at 0
                arrayOf(nodes[level][0])
            } else {
                nodes[level + 1]
            }
        val connected = FixedBitSet(nodes[level].size)
        var count = 0
        for (entryPoint in entryPoints.indices) {
            if (entryPoints[entryPoint] == null) {
                // use nodes present on next higher level (or this level if top level) as entry points
                continue
            }
            val stack = ArrayDeque<Int>()
            stack.addLast(entryPoint)
            while (stack.isNotEmpty()) {
                val node = stack.removeLast()
                if (connected.get(node)) {
                    continue
                }
                connected.set(node)
                count++
                for (nbr in nodes[level][node]!!) {
                    stack.addLast(nbr)
                }
            }
        }
        return count == levelSize(nodes[level])
    }

    /** Empty graph value */
    private class MockGraph(private val nodes: Array<out Array<out IntArray?>>) : HnswGraph() {

        private var currentLevel = 0
        private var currentNode = 0
        private var currentNeighbor = 0

        override fun nextNeighbor(): Int {
            return if (currentNeighbor >= nodes[currentLevel][currentNode]!!.size) {
                NO_MORE_DOCS
            } else {
                nodes[currentLevel][currentNode]!![currentNeighbor++]
            }
        }

        override fun seek(level: Int, target: Int) {
            assert(level in 0 until nodes.size)
            assert(target in 0 until nodes[level].size) {
                "target out of range: $target for level $level; should be less than ${nodes[level].size}"
            }
            assert(nodes[level][target] != null) { "target $target not on level $level" }
            currentLevel = level
            currentNode = target
            currentNeighbor = 0
        }

        override fun size(): Int {
            return nodes[0].size
        }

        override fun numLevels(): Int {
            return nodes.size
        }

        override fun entryNode(): Int {
            return 0
        }

        override fun toString(): String {
            val buf = StringBuilder()
            for (level in nodes.size - 1 downTo 0) {
                buf.append("\nLEVEL ").append(level).append("\n")
                for (node in nodes[level].indices) {
                    if (nodes[level][node] != null) {
                        buf.append("  ")
                            .append(node)
                            .append(':')
                            .append(nodes[level][node]!!.contentToString())
                            .append("\n")
                    }
                }
            }
            return buf.toString()
        }

        override fun neighborCount(): Int {
            return nodes[currentLevel][currentNode]!!.size
        }

        override fun maxConn(): Int {
            return UNKNOWN_MAX_CONN
        }

        override fun getNodesOnLevel(level: Int): NodesIterator {
            var count = 0
            for (i in nodes[level].indices) {
                if (nodes[level][i] != null) {
                    count++
                }
            }
            val finalCount = count
            return object : NodesIterator(finalCount) {
                var cur = -1
                var curCount = 0

                override fun hasNext(): Boolean {
                    return curCount < finalCount
                }

                override fun nextInt(): Int {
                    while (curCount < finalCount) {
                        if (nodes[level][++cur] != null) {
                            curCount++
                            return cur
                        }
                    }
                    throw IllegalStateException("exhausted")
                }

                override fun consume(dest: IntArray): Int {
                    throw UnsupportedOperationException()
                }

                override fun remove() {
                    throw UnsupportedOperationException()
                }
            }
        }
    }

    companion object {
        fun levelSize(nodes: Array<IntArray?>): Int {
            var count = 0
            for (node in nodes) {
                if (node != null) {
                    ++count
                }
            }
            return count
        }
    }
}
