package org.gnit.lucenekmp.util.hnsw


import org.gnit.lucenekmp.internal.hppc.IntArrayList
import org.gnit.lucenekmp.jdkport.AtomicInteger
import org.gnit.lucenekmp.jdkport.accumulateAndGet
import org.gnit.lucenekmp.jdkport.get
import org.gnit.lucenekmp.search.DocIdSetIterator.Companion.NO_MORE_DOCS
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.RamUsageEstimator
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
//import java.util.concurrent.atomic.AtomicInteger
//import java.util.concurrent.atomic.AtomicReference
import kotlin.jvm.JvmRecord
import kotlin.math.max

/**
 * An [HnswGraph] where all nodes and connections are held in memory. This class is used to
 * construct the HNSW graph before it's written to the index.
 */
@OptIn(ExperimentalAtomicApi::class)
class OnHeapHnswGraph internal constructor(M: Int, numNodes: Int) : HnswGraph(), Accountable {
    private val entryNode: AtomicReference<EntryNode>

    // the internal graph representation where the first dimension is node id and second dimension is
    // level
    // e.g. graph!![1][2] is all the neighbours of node 1 at level 2
    private var graph: Array<Array<NeighborArray>?>?

    // essentially another 2d map which the first dimension is level and second dimension is node id,
    // this is only
    // generated on demand when there's someone calling getNodeOnLevel on a non-zero level
    private var levelToNodes: Array<IntArrayList?>? = null
    private var lastFreezeSize = 0 // remember the size we are at last time to freeze the graph and generate

    // levelToNodes
    @OptIn(ExperimentalAtomicApi::class)
    private val size: AtomicInteger = AtomicInteger(0) // graph size, which is number of nodes in level 0

    @OptIn(ExperimentalAtomicApi::class)
    private val nonZeroLevelSize: AtomicInteger = AtomicInteger(
        0
    ) // total number of NeighborArrays created that is not on level 0, for now it

    // is only used to account memory usage
    @OptIn(ExperimentalAtomicApi::class)
    private val maxNodeId: AtomicInteger = AtomicInteger(-1)
    private val nsize: Int // neighbour array size at non-zero level
    private val nsize0: Int // neighbour array size at zero level
    private val noGrowth: Boolean
    // if an initial size is passed in, we don't expect the graph to grow itself

    // KnnGraphValues iterator members
    private var upto = 0
    private var cur: NeighborArray? = null

    /**
     * ctor
     *
     * @param numNodes number of nodes that will be added to this graph, passing in -1 means unbounded
     * while passing in a non-negative value will lock the whole graph and disable the graph from
     * growing itself (you cannot add a node with has id >= numNodes)
     */
    init {
        var numNodes = numNodes
        this.entryNode = AtomicReference<EntryNode>(EntryNode(-1, 1))
        // Neighbours' size on upper levels (nsize) and level 0 (nsize0)
        // We allocate extra space for neighbours, but then prune them to keep allowed maximum
        this.nsize = M + 1
        this.nsize0 = (M * 2 + 1)
        noGrowth = numNodes != -1
        if (!noGrowth) {
            numNodes = INIT_SIZE
        }
        this.graph = kotlin.arrayOfNulls<Array<NeighborArray>>(numNodes)
    }

    /**
     * Returns the [NeighborQueue] connected to the given node.
     *
     * @param level level of the graph
     * @param node the node whose neighbors are returned, represented as an ordinal on the level 0.
     */
    fun getNeighbors(level: Int, node: Int): NeighborArray {
        require(node < graph!!.size)
        require(
            level < graph!![node]!!.size
        ) {
            ("level="
                    + level
                    + ", node "
                    + node
                    + " has only "
                    + graph!![node]!!.size
                    + " levels for graph "
                    + this)
        }
        checkNotNull(graph!![node]!![level]) { "node=$node, level=$level" }
        return graph!![node]!![level]
    }

    override fun size(): Int {
        return size.get()
    }

    /**
     * When we initialize from another graph, the max node id is different from [.size],
     * because we will add nodes out of order, such that we need two method for each
     *
     * @return max node id (inclusive)
     */
    override fun maxNodeId(): Int {
        return if (noGrowth) {
            // we know the eventual graph size and the graph can possibly
            // being concurrently modified
            graph!!.size - 1
        } else {
            // The graph cannot be concurrently modified (and searched) if
            // we don't know the size beforehand, so it's safe to return the
            // actual maxNodeId
            maxNodeId.get()
        }
    }

    /**
     * Add node on the given level. Nodes can be inserted out of order, but it requires that the nodes
     * preceded by the node inserted out of order are eventually added.
     *
     *
     * NOTE: You must add a node starting from the node's top level
     *
     * @param level level to add a node on
     * @param node the node to add, represented as an ordinal on the level 0.
     */
    fun addNode(level: Int, node: Int) {
        if (node >= graph!!.size) {
            check(!noGrowth) { "The graph does not expect to grow when an initial size is given" }
            graph = ArrayUtil.grow(graph!!, node + 1)
        }

        require(
            graph!![node] == null || graph!![node]!!.size > level
        ) { "node must be inserted from the top level" }
        if (graph!![node] == null) {
            graph!![node] =
                kotlin.arrayOfNulls<NeighborArray?>(level + 1) as Array<NeighborArray>? // assumption: we always call this function from top level
            size.incrementAndFetch()
        }
        if (level == 0) {
            graph!![node]!![level] = NeighborArray(nsize0, true)
        } else {
            graph!![node]!![level] = NeighborArray(nsize, true)
            nonZeroLevelSize.incrementAndFetch()
        }
        maxNodeId.accumulateAndGet(
            node
        ) { a: Int, b: Int -> max(a, b) }
    }

    override fun seek(level: Int, targetNode: Int) {
        cur = getNeighbors(level, targetNode)
        upto = -1
    }

    override fun neighborCount(): Int {
        return cur!!.size()
    }

    override fun nextNeighbor(): Int {
        if (++upto < cur!!.size()) {
            return cur!!.nodes()[upto]
        }
        return NO_MORE_DOCS
    }

    /**
     * Returns the current number of levels in the graph
     *
     * @return the current number of levels in the graph
     */
    override fun numLevels(): Int {
        return entryNode.get().level + 1
    }

    override fun maxConn(): Int {
        return nsize - 1
    }

    /**
     * Returns the graph's current entry node on the top level shown as ordinals of the nodes on 0th
     * level
     *
     * @return the graph's current entry node on the top level
     */
    override fun entryNode(): Int {
        return entryNode.get().node
    }

    /**
     * Try to set the entry node if the graph does not have one
     *
     * @return True if the entry node is set to the provided node. False if the entry node already
     * exists
     */
    fun trySetNewEntryNode(node: Int, level: Int): Boolean {
        val current: EntryNode = entryNode.get()
        if (current.node == -1) {
            return entryNode.compareAndSet(current, EntryNode(node, level))
        }
        return false
    }

    /**
     * Try to promote the provided node to the entry node
     *
     * @param level should be larger than expectedOldLevel
     * @param expectOldLevel is the old entry node level the caller expect to be, the actual graph
     * level can be different due to concurrent modification
     * @return True if the entry node is set to the provided node. False if expectOldLevel is not the
     * same as the current entry node level. Even if the provided node's level is still higher
     * than the current entry node level, the new entry node will not be set and false will be
     * returned.
     */
    fun tryPromoteNewEntryNode(node: Int, level: Int, expectOldLevel: Int): Boolean {
        require(level > expectOldLevel)
        val currentEntry: EntryNode = entryNode.get()
        if (currentEntry.level == expectOldLevel) {
            return entryNode.compareAndSet(currentEntry, EntryNode(node, level))
        }
        return false
    }

    /**
     * WARN: calling this method will essentially iterate through all nodes at level 0 (even if you're
     * not getting node at level 0), we have built some caching mechanism such that if graph is not
     * changed only the first non-zero level call will pay the cost. So it is highly NOT recommended
     * to call this method while the graph is still building.
     *
     *
     * NOTE: calling this method while the graph is still building is prohibited
     */
    override fun getNodesOnLevel(level: Int): NodesIterator {
        check(size() == maxNodeId() + 1) { "graph build not complete, size=" + size() + " maxNodeId=" + maxNodeId() }
        if (level == 0) {
            return ArrayNodesIterator(size())
        } else {
            generateLevelToNodes()
            return CollectionNodesIterator(levelToNodes!![level]!!)
        }
    }

    private fun generateLevelToNodes() {
        if (lastFreezeSize == size()) {
            return
        }
        val maxLevels = numLevels()
        levelToNodes = kotlin.arrayOfNulls(maxLevels)
        for (i in 1..<maxLevels) {
            levelToNodes!![i] = IntArrayList()
        }
        var nonNullNode = 0
        for (node in graph!!.indices) {
            // when we init from another graph, we could have holes where some slot is null
            if (graph!![node] == null) {
                continue
            }
            nonNullNode++
            for (i in 1..<graph!![node]!!.size) {
                levelToNodes!![i]!!.add(node)
            }
            if (nonNullNode == size()) {
                break
            }
        }
        lastFreezeSize = size()
    }

    override fun ramBytesUsed(): Long {
        val neighborArrayBytes0: Long =
            (nsize0.toLong() * (Int.SIZE_BYTES + Float.SIZE_BYTES) + RamUsageEstimator.NUM_BYTES_ARRAY_HEADER * 2L + RamUsageEstimator.NUM_BYTES_OBJECT_REF * 2L + Int.SIZE_BYTES * 3)
        val neighborArrayBytes: Long =
            (nsize.toLong() * (Int.SIZE_BYTES + Float.SIZE_BYTES) + RamUsageEstimator.NUM_BYTES_ARRAY_HEADER * 2L + RamUsageEstimator.NUM_BYTES_OBJECT_REF * 2L + Int.SIZE_BYTES * 3)
        var total: Long = 0
        total +=
            (size() * (neighborArrayBytes0 + RamUsageEstimator.NUM_BYTES_ARRAY_HEADER)
                    + RamUsageEstimator.NUM_BYTES_ARRAY_HEADER) // for graph and level 0;
        total += nonZeroLevelSize.get() * neighborArrayBytes // for non-zero level
        total += (4 * Int.SIZE_BYTES).toLong() // all int fields
        total += 1 // field: noGrowth
        total +=
            (RamUsageEstimator.NUM_BYTES_OBJECT_REF
                    + RamUsageEstimator.NUM_BYTES_OBJECT_HEADER
                    + 2 * Int.SIZE_BYTES) // field: entryNode
        total += 3L * (Int.SIZE_BYTES + RamUsageEstimator.NUM_BYTES_OBJECT_HEADER) // 3 AtomicInteger
        total += RamUsageEstimator.NUM_BYTES_OBJECT_REF // field: cur
        total += RamUsageEstimator.NUM_BYTES_ARRAY_HEADER // field: levelToNodes
        if (levelToNodes != null) {
            total +=
                (numLevels() - 1).toLong() * RamUsageEstimator.NUM_BYTES_OBJECT_REF // no cost for level 0
            total +=
                nonZeroLevelSize.get().toLong() * (RamUsageEstimator.NUM_BYTES_OBJECT_HEADER
                        + RamUsageEstimator.NUM_BYTES_OBJECT_HEADER
                        + Int.SIZE_BYTES)
        }
        return total
    }

    override fun toString(): String {
        return ("OnHeapHnswGraph(size="
                + size()
                + ", numLevels="
                + numLevels()
                + ", entryNode="
                + entryNode()
                + ")")
    }

    private data class EntryNode(val node: Int, val level: Int)
    companion object {
        private const val INIT_SIZE = 128
    }
}

