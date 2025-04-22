package org.gnit.lucenekmp.util.hnsw


import org.gnit.lucenekmp.util.ArrayUtil
import kotlinx.io.IOException
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.isNaN
import kotlin.math.min

/**
 * NeighborArray encodes the neighbors of a node and their mutual scores in the HNSW graph as a pair
 * of growable arrays. Nodes are arranged in the sorted order of their scores in descending order
 * (if scoresDescOrder is true), or in the ascending order of their scores (if scoresDescOrder is
 * false)
 *
 * @lucene.internal
 */
class NeighborArray(maxSize: Int, private val scoresDescOrder: Boolean) {
    private var size = 0
    private val scores: FloatArray = FloatArray(maxSize)
    private val nodes: IntArray = IntArray(maxSize)
    private var sortedNodeSize = 0

    /**
     * Add a new node to the NeighborArray. The new node must be worse than all previously stored
     * nodes. This cannot be called after [.addOutOfOrder]
     */
    fun addInOrder(newNode: Int, newScore: Float) {
        require(size == sortedNodeSize) { "cannot call addInOrder after addOutOfOrder" }
        check(size != nodes.size) { "No growth is allowed" }
        if (size > 0) {
            val previousScore = scores[size - 1]
            require(
                (scoresDescOrder && (previousScore >= newScore)) || (!scoresDescOrder && (previousScore <= newScore))
            ) {
                ("Nodes are added in the incorrect order! Comparing "
                        + newScore
                        + " to "
                        + Arrays.toString(ArrayUtil.copyOfSubArray(scores, 0, size)))
            }
        }
        nodes[size] = newNode
        scores[size] = newScore
        ++size
        ++sortedNodeSize
    }

    /** Add node and newScore but do not insert as sorted  */
    fun addOutOfOrder(newNode: Int, newScore: Float) {
        check(size != nodes.size) { "No growth is allowed" }

        scores[size] = newScore
        nodes[size] = newNode
        size++
    }

    /**
     * In addition to [.addOutOfOrder], this function will also remove the
     * least-diverse node if the node array is full after insertion
     *
     *
     * In multi-threading environment, this method need to be locked as it will be called by
     * multiple threads while other add method is only supposed to be called by one thread.
     *
     * @param nodeId node Id of the owner of this NeighbourArray
     */
    @Throws(IOException::class)
    fun addAndEnsureDiversity(
        newNode: Int, newScore: Float, nodeId: Int, scorer: UpdateableRandomVectorScorer
    ) {
        addOutOfOrder(newNode, newScore)
        if (size < nodes.size) {
            return
        }
        // we're oversize, need to do diversity check and pop out the least diverse neighbour
        scorer.setScoringOrdinal(nodeId)
        removeIndex(findWorstNonDiverse(scorer))
        require(size == nodes.size - 1)
    }

    /**
     * Sort the array according to scores, and return the sorted indexes of previous unsorted nodes
     * (unchecked nodes)
     *
     * @return indexes of newly sorted (unchecked) nodes, in ascending order, or null if the array is
     * already fully sorted
     */
    @Throws(IOException::class)
    fun sort(scorer: RandomVectorScorer): IntArray? {
        if (size == sortedNodeSize) {
            // all nodes checked and sorted
            return null
        }
        require(sortedNodeSize < size)
        val uncheckedIndexes = IntArray(size - sortedNodeSize)
        var count = 0
        while (sortedNodeSize != size) {
            // TODO: Instead of do an array copy on every insertion, I think we can do better here:
            //       Remember the insertion point of each unsorted node and insert them altogether
            //       We can save several array copy by doing that
            uncheckedIndexes[count] = insertSortedInternal(scorer) // sortedNodeSize is increased inside
            for (i in 0..<count) {
                if (uncheckedIndexes[i] >= uncheckedIndexes[count]) {
                    // the previous inserted nodes has been shifted
                    uncheckedIndexes[i]++
                }
            }
            count++
        }
        Arrays.sort(uncheckedIndexes)
        return uncheckedIndexes
    }

    /** insert the first unsorted node into its sorted position  */
    @Throws(IOException::class)
    private fun insertSortedInternal(scorer: RandomVectorScorer?): Int {
        require(sortedNodeSize < size) { "Call this method only when there's unsorted node" }
        val tmpNode = nodes[sortedNodeSize]
        var tmpScore = scores[sortedNodeSize]

        if (Float.isNaN(tmpScore)) {
            tmpScore = scorer!!.score(tmpNode)
        }

        val insertionPoint =
            if (scoresDescOrder)
                descSortFindRightMostInsertionPoint(tmpScore, sortedNodeSize)
            else
                ascSortFindRightMostInsertionPoint(tmpScore, sortedNodeSize)
        System.arraycopy(
            nodes, insertionPoint, nodes, insertionPoint + 1, sortedNodeSize - insertionPoint
        )
        System.arraycopy(
            scores, insertionPoint, scores, insertionPoint + 1, sortedNodeSize - insertionPoint
        )
        nodes[insertionPoint] = tmpNode
        scores[insertionPoint] = tmpScore
        ++sortedNodeSize
        return insertionPoint
    }

    /** This method is for test only.  */
    @Throws(IOException::class)
    fun insertSorted(newNode: Int, newScore: Float) {
        addOutOfOrder(newNode, newScore)
        insertSortedInternal(null)
    }

    fun size(): Int {
        return size
    }

    /**
     * Direct access to the internal list of node ids; provided for efficient writing of the graph
     *
     * @lucene.internal
     */
    fun nodes(): IntArray {
        return nodes
    }

    fun scores(): FloatArray {
        return scores
    }

    fun clear() {
        size = 0
        sortedNodeSize = 0
    }

    fun removeLast() {
        size--
        sortedNodeSize = min(sortedNodeSize, size)
    }

    fun removeIndex(idx: Int) {
        if (idx == size - 1) {
            removeLast()
            return
        }
        System.arraycopy(nodes, idx + 1, nodes, idx, size - idx - 1)
        System.arraycopy(scores, idx + 1, scores, idx, size - idx - 1)
        if (idx < sortedNodeSize) {
            sortedNodeSize--
        }
        size--
    }

    override fun toString(): String {
        return "NeighborArray[$size]"
    }

    private fun ascSortFindRightMostInsertionPoint(newScore: Float, bound: Int): Int {
        var insertionPoint = Arrays.binarySearch(scores, 0, bound, newScore)
        if (insertionPoint >= 0) {
            // find the right most position with the same score
            while ((insertionPoint < bound - 1)
                && (scores[insertionPoint + 1] == scores[insertionPoint])
            ) {
                insertionPoint++
            }
            insertionPoint++
        } else {
            insertionPoint = -insertionPoint - 1
        }
        return insertionPoint
    }

    private fun descSortFindRightMostInsertionPoint(newScore: Float, bound: Int): Int {
        var start = 0
        var end = bound - 1
        while (start <= end) {
            val mid = (start + end) / 2
            if (scores[mid] < newScore) end = mid - 1
            else start = mid + 1
        }
        return start
    }

    /**
     * Find first non-diverse neighbour among the list of neighbors starting from the most distant
     * neighbours
     */
    @Throws(IOException::class)
    private fun findWorstNonDiverse(scorer: UpdateableRandomVectorScorer): Int {
        val uncheckedIndexes = checkNotNull(sort(scorer)) { "We will always have something unchecked" }
        var uncheckedCursor = uncheckedIndexes.size - 1
        for (i in size - 1 downTo 1) {
            if (uncheckedCursor < 0) {
                // no unchecked node left
                break
            }
            scorer.setScoringOrdinal(nodes[i])
            if (isWorstNonDiverse(i, uncheckedIndexes, uncheckedCursor, scorer)) {
                return i
            }
            if (i == uncheckedIndexes[uncheckedCursor]) {
                uncheckedCursor--
            }
        }
        return size - 1
    }

    @Throws(IOException::class)
    private fun isWorstNonDiverse(
        candidateIndex: Int, uncheckedIndexes: IntArray, uncheckedCursor: Int, scorer: RandomVectorScorer
    ): Boolean {
        val minAcceptedSimilarity = scores[candidateIndex]
        if (candidateIndex == uncheckedIndexes[uncheckedCursor]) {
            // the candidate itself is unchecked
            for (i in candidateIndex - 1 downTo 0) {
                val neighborSimilarity = scorer.score(nodes[i])
                // candidate node is too similar to node i given its score relative to the base node
                if (neighborSimilarity >= minAcceptedSimilarity) {
                    return true
                }
            }
        } else {
            // else we just need to make sure candidate does not violate diversity with the (newly
            // inserted) unchecked nodes
            require(candidateIndex > uncheckedIndexes[uncheckedCursor])
            for (i in uncheckedCursor downTo 0) {
                val neighborSimilarity = scorer.score(nodes[uncheckedIndexes[i]])
                // candidate node is too similar to node i given its score relative to the base node
                if (neighborSimilarity >= minAcceptedSimilarity) {
                    return true
                }
            }
        }
        return false
    }
}
