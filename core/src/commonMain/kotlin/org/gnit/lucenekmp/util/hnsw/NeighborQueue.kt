package org.gnit.lucenekmp.util.hnsw

import org.gnit.lucenekmp.util.LongHeap
import org.gnit.lucenekmp.util.NumericUtils


/**
 * NeighborQueue uses a [LongHeap] to store lists of arcs in an HNSW graph, represented as a
 * neighbor node id with an associated score packed together as a sortable long, which is sorted
 * primarily by score. The queue provides both fixed-size and unbounded operations via [ ][.insertWithOverflow] and [.add], and provides MIN and MAX heap
 * subclasses.
 */
class NeighborQueue(initialSize: Int, maxHeap: Boolean) {
    private enum class Order {
        MIN_HEAP {
            override fun apply(v: Long): Long {
                return v
            }
        },
        MAX_HEAP {
            override fun apply(v: Long): Long {
                // This cannot be just `-v` since Long.MIN_VALUE doesn't have a positive counterpart. It
                // needs a function that returns MAX_VALUE for MIN_VALUE and vice-versa.
                return -1 - v
            }
        };

        abstract fun apply(v: Long): Long
    }

    private val heap: LongHeap = LongHeap(initialSize)
    private val order: Order = if (maxHeap) Order.MAX_HEAP else Order.MIN_HEAP

    // Used to track the number of neighbors visited during a single graph traversal
    private var visitedCount = 0

    // Whether the search stopped early because it reached the visited nodes limit
    private var incomplete = false

    /**
     * @return the number of elements in the heap
     */
    fun size(): Int {
        return heap.size()
    }

    /**
     * Adds a new graph arc, extending the storage as needed.
     *
     * @param newNode the neighbor node id
     * @param newScore the score of the neighbor, relative to some other node
     */
    fun add(newNode: Int, newScore: Float) {
        heap.push(encode(newNode, newScore))
    }

    /**
     * If the heap is not full (size is less than the initialSize provided to the constructor), adds a
     * new node-and-score element. If the heap is full, compares the score against the current top
     * score, and replaces the top element if newScore is better than (greater than unless the heap is
     * reversed), the current top score.
     *
     * @param newNode the neighbor node id
     * @param newScore the score of the neighbor, relative to some other node
     */
    fun insertWithOverflow(newNode: Int, newScore: Float): Boolean {
        return heap.insertWithOverflow(encode(newNode, newScore))
    }

    /**
     * Encodes the node ID and its similarity score as long, preserving the Lucene tie-breaking rule
     * that when two scores are equal, the smaller node ID must win.
     *
     *
     * The most significant 32 bits represent the float score, encoded as a sortable int.
     *
     *
     * The least significant 32 bits represent the node ID.
     *
     *
     * The bits representing the node ID are complemented to guarantee the win for the smaller node
     * Id.
     *
     *
     * The AND with 0xFFFFFFFFL (a long with first 32 bits as 1) is necessary to obtain a long that
     * has:
     *  * The most significant 32 bits set to 0
     *  * The least significant 32 bits represent the node ID.
     *
     * @param node the node ID
     * @param score the node score
     * @return the encoded score, node ID
     */
    private fun encode(node: Int, score: Float): Long {
        return order.apply(
            ((NumericUtils.floatToSortableInt(score).toLong()) shl 32) or (0xFFFFFFFFL and node.toLong().inv())
        )
    }

    private fun decodeScore(heapValue: Long): Float {
        return NumericUtils.sortableIntToFloat((order.apply(heapValue) shr 32).toInt())
    }

    private fun decodeNodeId(heapValue: Long): Int {
        return (order.apply(heapValue)).toInt().inv()
    }

    /** Removes the top element and returns its node id.  */
    fun pop(): Int {
        return decodeNodeId(heap.pop())
    }

    fun nodes(): IntArray {
        val size = size()
        val nodes = IntArray(size)
        for (i in 0..<size) {
            nodes[i] = decodeNodeId(heap.get(i + 1))
        }
        return nodes
    }

    /** Returns the top element's node id.  */
    fun topNode(): Int {
        return decodeNodeId(heap.top())
    }

    /**
     * Returns the top element's node score. For the min heap this is the minimum score. For the max
     * heap this is the maximum score.
     */
    fun topScore(): Float {
        return decodeScore(heap.top())
    }

    fun clear() {
        heap.clear()
        visitedCount = 0
        incomplete = false
    }

    fun visitedCount(): Int {
        return visitedCount
    }

    fun setVisitedCount(visitedCount: Int) {
        this.visitedCount = visitedCount
    }

    fun incomplete(): Boolean {
        return incomplete
    }

    fun markIncomplete() {
        this.incomplete = true
    }

    override fun toString(): String {
        return "Neighbors[" + heap.size() + "]"
    }
}
