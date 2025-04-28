package org.gnit.lucenekmp.util.hnsw

import org.gnit.lucenekmp.util.hnsw.HnswGraph.ArrayNodesIterator
import org.gnit.lucenekmp.util.hnsw.HnswGraph.NodesIterator
import org.gnit.lucenekmp.codecs.KnnVectorsReader
import org.gnit.lucenekmp.codecs.hnsw.HnswGraphProvider
import org.gnit.lucenekmp.codecs.perfield.PerFieldKnnVectorsFormat
import org.gnit.lucenekmp.index.CodecReader
import org.gnit.lucenekmp.index.FilterLeafReader
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.internal.hppc.IntHashSet
import org.gnit.lucenekmp.search.DocIdSetIterator.Companion.NO_MORE_DOCS
import org.gnit.lucenekmp.util.FixedBitSet
import kotlinx.io.IOException
import org.gnit.lucenekmp.jdkport.numberOfTrailingZeros
import org.gnit.lucenekmp.jdkport.pop
import org.gnit.lucenekmp.jdkport.push
import kotlin.jvm.JvmRecord

/** Utilities for use in tests involving HNSW graphs  */
object HnswUtil {
    /*
   For each level, check rooted components from previous level nodes, which are entry
   points with the goal that each node should be reachable from *some* entry point.  For each entry
   point, compute a spanning tree, recording the nodes in a single shared bitset.

   Also record a bitset marking nodes that are not full to be used when reconnecting in order to
   limit the search to include non-full nodes only.
  */
    /** Returns true if every node on every level is reachable from node 0.  */
    @Throws(IOException::class)
    fun isRooted(knnValues: HnswGraph): Boolean {
        for (level in 0..<knnValues.numLevels()) {
            if (components(knnValues, level, null, 0).size > 1) {
                return false
            }
        }
        return true
    }

    /**
     * Returns the sizes of the distinct graph components on level 0. If the graph is fully-rooted the
     * list will have one entry. If it is empty, the returned list will be empty.
     */
    @Throws(IOException::class)
    fun componentSizes(hnsw: HnswGraph): MutableList<Int> {
        return componentSizes(hnsw, 0)
    }

    /**
     * Returns the sizes of the distinct graph components on the given level. The forest starting at
     * the entry points (nodes in the next highest level) is considered as a single component. If the
     * entire graph is rooted in the entry points--that is, every node is reachable from at least one
     * entry point--the returned list will have a single entry. If the graph is empty, the returned
     * list will be empty.
     */
    @Throws(IOException::class)
    fun componentSizes(hnsw: HnswGraph, level: Int): MutableList<Int> {
        return components(hnsw, level, null, 0).map { it.size }.toMutableList()
    }

    // Finds orphaned components on the graph level.
    @Throws(IOException::class)
    fun components(
        hnsw: HnswGraph, level: Int, notFullyConnected: FixedBitSet?, maxConn: Int
    ): MutableList<Component> {
        val components: MutableList<Component> = ArrayList()
        val connectedNodes = FixedBitSet(hnsw.size())
        require(hnsw.size() == hnsw.getNodesOnLevel(0).size())
        var total = 0
        require(level < hnsw.numLevels()) { "Level " + level + " too large for graph with " + hnsw.numLevels() + " levels" }
        val entryPoints: NodesIterator
        // System.out.println("components level=" + level);
        if (level == hnsw.numLevels() - 1) {
            entryPoints = ArrayNodesIterator(intArrayOf(hnsw.entryNode()), 1)
        } else {
            entryPoints = hnsw.getNodesOnLevel(level + 1)
        }
        while (entryPoints.hasNext()) {
            val entryPoint: Int = entryPoints.nextInt()
            val component =
                markRooted(hnsw, level, connectedNodes, notFullyConnected, maxConn, entryPoint)
            total += component.size
        }
        val entryPoint: Int
        if (notFullyConnected != null) {
            entryPoint = notFullyConnected.nextSetBit(0)
        } else {
            entryPoint = connectedNodes.nextSetBit(0)
        }
        if (total > 0) {
            components.add(Component(entryPoint, total))
        }
        if (level == 0) {
            var nextClear = nextClearBit(connectedNodes, 0)
            while (nextClear != NO_MORE_DOCS) {
                val component =
                    markRooted(hnsw, level, connectedNodes, notFullyConnected, maxConn, nextClear)
                require(component.size > 0)
                components.add(component)
                total += component.size
                nextClear = nextClearBit(connectedNodes, component.start)
            }
        } else {
            val nodes: NodesIterator = hnsw.getNodesOnLevel(level)
            while (nodes.hasNext()) {
                val nextClear: Int = nodes.nextInt()
                if (connectedNodes.get(nextClear)) {
                    continue
                }
                val component =
                    markRooted(hnsw, level, connectedNodes, notFullyConnected, maxConn, nextClear)
                require(component.start == nextClear)
                require(component.size > 0)
                components.add(component)
                total += component.size
            }
        }
        require(
            total == hnsw.getNodesOnLevel(level).size()
        ) {
            ("total="
                    + total
                    + " level nodes on level "
                    + level
                    + " = "
                    + hnsw.getNodesOnLevel(level).size())
        }
        return components
    }

    /**
     * Count the nodes in a rooted component of the graph and set the bits of its nodes in
     * connectedNodes bitset. Rooted means nodes that can be reached from a root node.
     *
     * @param hnswGraph the graph to check
     * @param level the level of the graph to check
     * @param connectedNodes a bitset the size of the entire graph with 1's indicating nodes that have
     * been marked as connected. This method updates the bitset.
     * @param notFullyConnected a bitset the size of the entire graph. On output, we mark nodes
     * visited having fewer than maxConn connections. May be null.
     * @param maxConn the maximum number of connections for any node (aka M).
     * @param entryPoint a node id to start at
     */
    @Throws(IOException::class)
    private fun markRooted(
        hnswGraph: HnswGraph,
        level: Int,
        connectedNodes: FixedBitSet,
        notFullyConnected: FixedBitSet?,
        maxConn: Int,
        entryPoint: Int
    ): Component {
        // Start at entry point and search all nodes on this level
        // System.out.println("markRooted level=" + level + " entryPoint=" + entryPoint);
        if (connectedNodes.get(entryPoint)) {
            return Component(entryPoint, 0)
        }
        val nodesInStack = IntHashSet()
        val stack: ArrayDeque<Int> = ArrayDeque<Int>()
        stack.push(entryPoint)
        var count = 0
        while (!stack.isEmpty()) {
            val node: Int = stack.pop()
            if (connectedNodes.get(node)) {
                continue
            }
            count++
            connectedNodes.set(node)
            hnswGraph.seek(level, node)
            var friendOrd: Int
            var friendCount = 0
            while ((hnswGraph.nextNeighbor().also { friendOrd = it }) != NO_MORE_DOCS) {
                ++friendCount
                if (connectedNodes.get(friendOrd) == false && nodesInStack.contains(friendOrd) == false) {
                    stack.push(friendOrd)
                    nodesInStack.add(friendOrd)
                }
            }
            if (friendCount < maxConn && notFullyConnected != null) {
                notFullyConnected.set(node)
            }
        }
        return Component(entryPoint, count)
    }

    private fun nextClearBit(bits: FixedBitSet, index: Int): Int {
        // Does not depend on the ghost bits being clear!
        val barray: LongArray = bits.getBits()
        require(index >= 0 && index < bits.length()) { "index=" + index + ", numBits=" + bits.length() }
        var i = index shr 6
        var word = (barray[i] shr index).inv() // skip all the bits to the right of index

        var next: Int = NO_MORE_DOCS
        if (word != 0L) {
            next = index + Long.numberOfTrailingZeros(word)
        } else {
            while (++i < barray.size) {
                word = barray[i].inv()
                if (word != 0L) {
                    next = (i shl 6) + Long.numberOfTrailingZeros(word)
                    break
                }
            }
        }
        if (next >= bits.length()) {
            return NO_MORE_DOCS
        } else {
            return next
        }
    }

    /**
     * In graph theory, "connected components" are really defined only for undirected (ie
     * bidirectional) graphs. Our graphs are directed, because of pruning, but they are *mostly*
     * undirected. In this case we compute components starting from a single node so what we are
     * really measuring is whether the graph is a "rooted graph". TODO: measure whether the graph is
     * "strongly connected" ie there is a path from every node to every other node.
     */
    @Throws(IOException::class)
    fun graphIsRooted(reader: IndexReader, vectorField: String): Boolean {
        for (ctx in reader.leaves()) {
            val codecReader: CodecReader = FilterLeafReader.unwrap(ctx.reader()) as CodecReader
            val vectorsReader: KnnVectorsReader? =
                (codecReader.vectorReader as PerFieldKnnVectorsFormat.FieldsReader)
                    .getFieldReader(vectorField)
            if (vectorsReader is HnswGraphProvider) {
                val graph: HnswGraph? = (vectorsReader as HnswGraphProvider).getGraph(vectorField)
                if (!isRooted(graph!!)) {
                    return false
                }
            } else {
                throw IllegalArgumentException("not a graph: $vectorsReader")
            }
        }
        return true
    }

    /**
     * A component (also "connected component") of an undirected graph is a collection of nodes that
     * are connected by neighbor links: every node in a connected component is reachable from every
     * other node in the component. See https://en.wikipedia.org/wiki/Component_(graph_theory). Such a
     * graph is said to be "fully connected" *iff* it has a single component, or it is empty.
     *
     * @param start the lowest-numbered node in the component
     * @param size the number of nodes in the component
     */
    @JvmRecord
    data class Component(val start: Int, val size: Int)
}
