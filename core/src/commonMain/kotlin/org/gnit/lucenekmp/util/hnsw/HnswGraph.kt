package org.gnit.lucenekmp.util.hnsw


import kotlinx.io.IOException
import org.gnit.lucenekmp.internal.hppc.IntArrayList
import org.gnit.lucenekmp.internal.hppc.IntCursor
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.PrimitiveIterator
import org.gnit.lucenekmp.search.DocIdSetIterator.Companion.NO_MORE_DOCS
import kotlin.math.min

/**
 * Hierarchical Navigable Small World graph. Provides efficient approximate nearest neighbor search
 * for high dimensional vectors. See [Efficient and robust
 * approximate nearest neighbor search using Hierarchical Navigable Small World graphs [2018]](https://arxiv.org/abs/1603.09320)
 * paper for details.
 *
 *
 * The nomenclature is a bit different here from what's used in the paper:
 *
 * <h2>Hyperparameters</h2>
 *
 *
 *  * `beamWidth` in [HnswGraphBuilder] has the same meaning as `efConst
` *  in the paper. It is the number of nearest neighbor candidates to track while
 * searching the graph for each newly inserted node.
 *  * `maxConn` has the same meaning as `M` in the paper; it controls how
 * many of the `efConst` neighbors are connected to the new node
 *
 *
 *
 * Note: The graph may be searched by multiple threads concurrently, but updates are not
 * thread-safe. The search method optionally takes a set of "accepted nodes", which can be used to
 * exclude deleted documents.
 */
abstract class HnswGraph
/** Sole constructor  */
protected constructor() {
    /**
     * Move the pointer to exactly the given `level`'s `target`. After this method
     * returns, call [.nextNeighbor] to return successive (ordered) connected node ordinals.
     *
     * @param level level of the graph
     * @param target ordinal of a node in the graph, must be  0 and &lt; [     ][FloatVectorValues.size].
     */
    @Throws(IOException::class)
    abstract fun seek(level: Int, target: Int)

    /** Returns the number of nodes in the graph  */
    abstract fun size(): Int

    /** Returns max node id, inclusive. Normally this value will be size - 1.  */
    fun maxNodeId(): Int {
        return size() - 1
    }

    /**
     * Iterates over the neighbor list. It is illegal to call this method after it returns
     * NO_MORE_DOCS without calling [.seek], which resets the iterator.
     *
     * @return a node ordinal in the graph, or NO_MORE_DOCS if the iteration is complete.
     */
    @Throws(IOException::class)
    abstract fun nextNeighbor(): Int

    /** Returns the number of levels of the graph  */
    @Throws(IOException::class)
    abstract fun numLevels(): Int

    /** returns M, the maximum number of connections for a node.  */
    abstract fun maxConn(): Int

    /** Returns graph's entry point on the top level *  */
    @Throws(IOException::class)
    abstract fun entryNode(): Int

    /**
     * Get all nodes on a given level as node 0th ordinals. The nodes are NOT guaranteed to be
     * presented in any particular order.
     *
     * @param level level for which to get all nodes
     * @return an iterator over nodes where `nextInt` returns a next node on the level
     */
    @Throws(IOException::class)
    abstract fun getNodesOnLevel(level: Int): NodesIterator

    abstract fun neighborCount(): Int

    /**
     * Iterator over the graph nodes on a certain level. Iterator also provides the size – the total
     * number of nodes to be iterated over. The nodes are NOT guaranteed to be presented in any
     * particular order.
     */
    abstract class NodesIterator
    /** Constructor for iterator based on the size  */(protected val size: Int) : PrimitiveIterator.OfInt {
        /** The number of elements in this iterator *  */
        fun size(): Int {
            return size
        }

        /**
         * Consume integers from the iterator and place them into the `dest` array.
         *
         * @param dest where to put the integers
         * @return The number of integers written to `dest`
         */
        abstract fun consume(dest: IntArray): Int

        companion object {
            fun getSortedNodes(nodesOnLevel: NodesIterator): IntArray {
                val sortedNodes = IntArray(nodesOnLevel.size())
                var n = 0
                while (nodesOnLevel.hasNext()) {
                    sortedNodes[n] = nodesOnLevel.nextInt()
                    n++
                }
                Arrays.sort(sortedNodes)
                return sortedNodes
            }
        }
    }

    /** NodesIterator that accepts nodes as an integer array.  */
    class ArrayNodesIterator : NodesIterator {
        private val nodes: IntArray?
        private var cur = 0

        /** Constructor for iterator based on integer array representing nodes  */
        constructor(nodes: IntArray, size: Int) : super(size) {
            checkNotNull(nodes)
            require(size <= nodes.size)
            this.nodes = nodes
        }

        /** Constructor for iterator based on the size  */
        constructor(size: Int) : super(size) {
            this.nodes = null
        }

        override fun consume(dest: IntArray): Int {
            if (hasNext() == false) {
                throw NoSuchElementException()
            }
            val numToCopy = min(size - cur, dest.size)
            if (nodes == null) {
                for (i in 0..<numToCopy) {
                    dest[i] = cur + i
                }
                return numToCopy
            }
            /*java.lang.System.arraycopy(nodes, cur, dest, 0, numToCopy)*/
            nodes.copyInto(
                destination = dest,
                destinationOffset = 0,
                startIndex = cur,
                endIndex = cur + numToCopy,
            )
            cur += numToCopy
            return numToCopy
        }

        override fun nextInt(): Int {
            if (hasNext() == false) {
                throw NoSuchElementException()
            }
            return if (nodes == null) {
                cur++
            } else {
                nodes[cur++]
            }
        }

        override fun hasNext(): Boolean {
            return cur < size
        }

        override fun remove() {
            // implement remove
            throw UnsupportedOperationException("remove")
        }

        companion object {
            internal val EMPTY: NodesIterator = ArrayNodesIterator(0)
        }
    }

    /** Nodes iterator based on set representation of nodes.  */
    class CollectionNodesIterator(nodes: IntArrayList) : NodesIterator(nodes.size()) {
        var nodes: MutableIterator<IntCursor> = nodes.iterator()

        override fun consume(dest: IntArray): Int {
            if (hasNext() == false) {
                throw NoSuchElementException()
            }

            var destIndex = 0
            while (hasNext() && destIndex < dest.size) {
                dest[destIndex++] = nextInt()
            }

            return destIndex
        }

        override fun nextInt(): Int {
            if (hasNext() == false) {
                throw NoSuchElementException()
            }
            return nodes.next().value
        }

        override fun hasNext(): Boolean {
            return nodes.hasNext()
        }

        override fun remove() {
            // implement remove
            nodes.remove()
        }
    }

    companion object {
        const val UNKNOWN_MAX_CONN: Int = -1

        /** Empty graph value  */
        val EMPTY: HnswGraph = object : HnswGraph() {
            override fun nextNeighbor(): Int {
                return NO_MORE_DOCS
            }

            override fun seek(level: Int, target: Int) {}

            override fun size(): Int {
                return 0
            }

            override fun numLevels(): Int {
                return 0
            }

            override fun entryNode(): Int {
                return 0
            }

            override fun neighborCount(): Int {
                return 0
            }

            override fun maxConn(): Int {
                return UNKNOWN_MAX_CONN
            }

            override fun getNodesOnLevel(level: Int): NodesIterator {
                return ArrayNodesIterator.Companion.EMPTY
            }
        }
    }
}
