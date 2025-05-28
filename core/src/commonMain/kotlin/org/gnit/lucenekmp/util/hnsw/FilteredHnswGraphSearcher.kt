package org.gnit.lucenekmp.util.hnsw


import org.gnit.lucenekmp.search.DocIdSetIterator.Companion.NO_MORE_DOCS
import org.gnit.lucenekmp.search.KnnCollector
import org.gnit.lucenekmp.util.BitSet
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.SparseFixedBitSet
import okio.IOException
import org.gnit.lucenekmp.jdkport.Math
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Searches an HNSW graph to find nearest neighbors to a query vector. This particular
 * implementation is optimized for a filtered search, inspired by the ACORN-1 algorithm.
 * https://arxiv.org/abs/2403.04871 However, this implementation is augmented in some ways, mainly:
 *
 *
 *  * It dynamically determines when the optimized filter step should occur based on some
 * filtered lambda. This is done per small world
 *  * The graph searcher doesn't always explore all the extended neighborhood and the number of
 * additional candidates is predicated on the original candidate's filtered percentage.
 *
 */
class FilteredHnswGraphSearcher private constructor(
    candidates: NeighborQueue,
    visited: BitSet,
    filterSize: Int,
    graph: HnswGraph
) : HnswGraphSearcher(
    candidates, visited
) {
    // How many extra neighbors to explore, used as a multiple to the candidates neighbor count
    private val maxExplorationMultiplier: Int
    private val minToScore: Int

    /** Creates a new graph searcher.  */
    init {
        require(graph.maxConn() > 0) { "graph must have known max connections" }
        val filterRatio = filterSize / graph.size()
        this.maxExplorationMultiplier =
            Math.round(min(1.0F / filterRatio, graph.maxConn() / 2.0F))
        // As the filter gets exceptionally restrictive, we must spread out the exploration
        this.minToScore =
            Math.round(
                min(
                    max(0.0F, 1.0F / filterRatio.toFloat() - (2.0F * graph.maxConn().toFloat())), graph.maxConn().toFloat()
                )
            )
    }

    @Throws(IOException::class)
    override fun searchLevel(
        results: KnnCollector,
        scorer: RandomVectorScorer,
        level: Int,
        eps: IntArray,
        graph: HnswGraph,
        acceptOrds: Bits?
    ) {
        require(level == 0) { "Filtered search only works on the base level" }

        val size = getGraphSize(graph)

        prepareScratchState()

        for (ep in eps) {
            if (!visited.getAndSet(ep)) {
                if (results.earlyTerminated()) {
                    return
                }
                val score = scorer.score(ep)
                results.incVisitedCount(1)
                candidates.add(ep, score)
                if (acceptOrds!!.get(ep)) {
                    results.collect(ep, score)
                }
            }
        }
        // Collect the vectors to score and potentially add as candidates
        val toScore = IntArrayQueue(graph.maxConn() * 2 * maxExplorationMultiplier)
        val toExplore = IntArrayQueue(graph.maxConn() * 2 * maxExplorationMultiplier)
        // A bound that holds the minimum similarity to the query vector that a candidate vector must
        // have to be considered.
        var minAcceptedSimilarity: Float = Math.nextUp(results.minCompetitiveSimilarity())
        while (candidates.size() > 0 && !results.earlyTerminated()) {
            // get the best candidate (closest or best scoring)
            val topCandidateSimilarity = candidates.topScore()
            if (minAcceptedSimilarity > topCandidateSimilarity) {
                break
            }
            val topCandidateNode = candidates.pop()
            graph.seek(level, topCandidateNode)
            val neighborCount = graph.neighborCount()
            toScore.clear()
            toExplore.clear()
            var friendOrd: Int
            while ((graph.nextNeighbor().also { friendOrd = it }) != NO_MORE_DOCS && !toScore.isFull) {
                require(friendOrd < size) { "friendOrd=$friendOrd; size=$size" }
                if (visited.getAndSet(friendOrd)) {
                    continue
                }
                if (acceptOrds!!.get(friendOrd)) {
                    toScore.add(friendOrd)
                } else {
                    toExplore.add(friendOrd)
                }
            }
            // adjust to locally number of filtered candidates to explore
            val filteredAmount = toExplore.count() / neighborCount.toFloat()
            val maxToScoreCount =
                (neighborCount * min(maxExplorationMultiplier.toFloat(), 1f / (1f - filteredAmount))).toInt()
            val maxAdditionalToExploreCount = toExplore.capacity() - 1
            // There is enough filtered, or we don't have enough candidates to score and explore
            var totalExplored = toScore.count() + toExplore.count()
            if (toScore.count() < maxToScoreCount && filteredAmount > EXPANDED_EXPLORATION_LAMBDA) {
                // Now we need to explore the neighbors of the neighbors
                var exploreFriend: Int
                while ((toExplore.poll()
                        .also { exploreFriend = it }) != NO_MORE_DOCS // only explore initial additional neighborhood
                    && totalExplored < maxAdditionalToExploreCount && toScore.count() < maxToScoreCount
                ) {
                    graphSeek(graph, level, exploreFriend)
                    var friendOfAFriendOrd: Int
                    while ((graph.nextNeighbor().also { friendOfAFriendOrd = it }) != NO_MORE_DOCS
                        && toScore.count() < maxToScoreCount
                    ) {
                        if (visited.getAndSet(friendOfAFriendOrd)) {
                            continue
                        }
                        totalExplored++
                        if (acceptOrds!!.get(friendOfAFriendOrd)) {
                            toScore.add(friendOfAFriendOrd)
                            // If we have YET to find a minimum of number candidates, we will continue to explore
                            // until our max
                        } else if (totalExplored < maxAdditionalToExploreCount
                            && toScore.count() < minToScore
                        ) {
                            toExplore.add(friendOfAFriendOrd)
                        }
                    }
                }
            }
            // Score the vectors and add them to the candidate list
            var toScoreOrd: Int
            while ((toScore.poll().also { toScoreOrd = it }) != NO_MORE_DOCS) {
                val friendSimilarity = scorer.score(toScoreOrd)
                results.incVisitedCount(1)
                if (friendSimilarity > minAcceptedSimilarity) {
                    candidates.add(toScoreOrd, friendSimilarity)
                    if (results.collect(toScoreOrd, friendSimilarity)) {
                        minAcceptedSimilarity = Math.nextUp(results.minCompetitiveSimilarity())
                    }
                }
            }
        }
    }

    private fun prepareScratchState() {
        candidates.clear()
        visited.clear()
    }

    private class IntArrayQueue(capacity: Int) {
        private val nodes: IntArray = IntArray(capacity)
        private var upto = 0
        private var size = 0

        fun capacity(): Int {
            return nodes.size
        }

        fun count(): Int {
            return size - upto
        }

        fun add(node: Int) {
            if (this.isFull) {
                throw UnsupportedOperationException("Initial capacity should remain unchanged")
            }
            nodes[size++] = node
        }

        val isFull: Boolean
            get() = size == nodes.size

        fun poll(): Int {
            if (upto == size) {
                return NO_MORE_DOCS
            }
            return nodes[upto++]
        }

        fun clear() {
            upto = 0
            size = 0
        }
    }

    companion object {
        // How many filtered candidates must be found to consider N-hop neighbors
        private const val EXPANDED_EXPLORATION_LAMBDA = 0.10f

        /**
         * Creates a new filtered graph searcher.
         *
         * @param k the number of nearest neighbors to find
         * @param graph the graph to search
         * @param filterSize the number of vectors that pass the accepted ords filter
         * @param acceptOrds the accepted ords filter
         * @return a new graph searcher optimized for filtered search
         */
        fun create(
            k: Int, graph: HnswGraph, filterSize: Int, acceptOrds: Bits
        ): FilteredHnswGraphSearcher {
            requireNotNull(acceptOrds) { "acceptOrds must not be null to used filtered search" }
            require(!(filterSize <= 0 || filterSize >= getGraphSize(graph))) { "filterSize must be > 0 and < graph size" }
            return FilteredHnswGraphSearcher(
                NeighborQueue(k, true), bitSet(filterSize.toLong(), getGraphSize(graph), k), filterSize, graph
            )
        }

        private fun bitSet(filterSize: Long, graphSize: Int, topk: Int): BitSet {
            val percentFiltered = filterSize.toFloat() / graphSize
            require(percentFiltered > 0.0f && percentFiltered < 1.0f)
            val totalOps = ln(graphSize.toDouble()) * topk
            val approximateVisitation = (totalOps / percentFiltered).toInt()
            return bitSet(approximateVisitation, graphSize)
        }

        private fun bitSet(expectedBits: Int, totalBits: Int): BitSet {
            return if (expectedBits < (totalBits ushr 7)) {
                SparseFixedBitSet(totalBits)
            } else {
                FixedBitSet(totalBits)
            }
        }
    }
}
