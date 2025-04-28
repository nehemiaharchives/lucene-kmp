package org.gnit.lucenekmp.util.hnsw


import org.gnit.lucenekmp.util.hnsw.HnswGraphBuilder.GraphBuilderKnnCollector
import org.gnit.lucenekmp.search.DocIdSetIterator.Companion.NO_MORE_DOCS
import org.gnit.lucenekmp.search.KnnCollector
import org.gnit.lucenekmp.search.TopKnnCollector
import org.gnit.lucenekmp.search.knn.KnnSearchStrategy.Hnsw
import org.gnit.lucenekmp.search.knn.KnnSearchStrategy.Seeded
import org.gnit.lucenekmp.util.BitSet
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.SparseFixedBitSet
import kotlinx.io.IOException
import org.gnit.lucenekmp.jdkport.Math
import kotlin.math.min

/**
 * Searches an HNSW graph to find nearest neighbors to a query vector. For more background on the
 * search algorithm, see [HnswGraph].
 */
open class HnswGraphSearcher(
    /**
     * Scratch data structures that are used in each [.searchLevel] call. These can be expensive
     * to allocate, so they're cleared and reused across calls.
     */
    protected val candidates: NeighborQueue, visited: BitSet
) : AbstractHnswGraphSearcher() {
    protected var visited: BitSet

    /**
     * Creates a new graph searcher.
     *
     * @param candidates max heap that will track the candidate nodes to explore
     * @param visited bit set that will track nodes that have already been visited
     */
    init {
        this.visited = visited
    }

    /**
     * Searches for the nearest neighbors of a query vector in a given level.
     *
     *
     * If the search stops early because it reaches the visited nodes limit, then the results will
     * be marked incomplete through [NeighborQueue.incomplete].
     *
     * @param scorer the scorer to compare the query with the nodes
     * @param topK the number of nearest to query results to return
     * @param level level to search
     * @param eps the entry points for search at this level expressed as level 0th ordinals
     * @param graph the graph values
     * @return a set of collected vectors holding the nearest neighbors found
     */
    @Throws(IOException::class)
    fun searchLevel( // Note: this is only public because Lucene91HnswGraphBuilder needs it
        scorer: RandomVectorScorer, topK: Int, level: Int, eps: IntArray, graph: HnswGraph
    ): GraphBuilderKnnCollector {
        val results =
            GraphBuilderKnnCollector(topK)
        searchLevel(results, scorer, level, eps, graph, null)
        return results
    }

    /**
     * Function to find the best entry point from which to search the zeroth graph layer.
     *
     * @param scorer the scorer to compare the query with the nodes
     * @param graph the HNSWGraph
     * @param collector the knn result collector
     * @return the best entry point, `-1` indicates graph entry node not set, or visitation limit
     * exceeded
     * @throws IOException When accessing the vector fails
     */
    @Throws(IOException::class)
    override fun findBestEntryPoint(
        scorer: RandomVectorScorer,
        graph: HnswGraph,
        collector: KnnCollector
    ): IntArray {
        var currentEp = graph.entryNode()
        if (currentEp == -1 || graph.numLevels() == 1) {
            return intArrayOf(currentEp)
        }
        val size = getGraphSize(graph)
        prepareScratchState(size)
        var currentScore = scorer.score(currentEp)
        collector.incVisitedCount(1)
        var foundBetter: Boolean
        for (level in graph.numLevels() - 1 downTo 1) {
            foundBetter = true
            visited.set(currentEp)
            // Keep searching the given level until we stop finding a better candidate entry point
            while (foundBetter) {
                foundBetter = false
                graphSeek(graph, level, currentEp)
                var friendOrd: Int
                while ((graphNextNeighbor(graph).also { friendOrd = it }) != NO_MORE_DOCS) {
                    require(friendOrd < size) { "friendOrd=$friendOrd; size=$size" }
                    if (visited.getAndSet(friendOrd)) {
                        continue
                    }
                    if (collector.earlyTerminated()) {
                        return intArrayOf(UNK_EP)
                    }
                    val friendSimilarity = scorer.score(friendOrd)
                    collector.incVisitedCount(1)
                    if (friendSimilarity > currentScore) {
                        currentScore = friendSimilarity
                        currentEp = friendOrd
                        foundBetter = true
                    }
                }
            }
        }
        return if (collector.earlyTerminated()) intArrayOf(UNK_EP) else intArrayOf(currentEp)
    }

    /**
     * Add the closest neighbors found to a priority queue (heap). These are returned in REVERSE
     * proximity order -- the most distant neighbor of the topK found, i.e. the one with the lowest
     * score/comparison value, will be at the top of the heap, while the closest neighbor will be the
     * last to be popped.
     */
    @Throws(IOException::class)
    override fun searchLevel(
        results: KnnCollector,
        scorer: RandomVectorScorer,
        level: Int,
        eps: IntArray,
        graph: HnswGraph,
        acceptOrds: Bits?
    ) {
        val size = getGraphSize(graph)

        prepareScratchState(size)

        for (ep in eps) {
            if (!visited.getAndSet(ep)) {
                if (results.earlyTerminated()) {
                    break
                }
                val score = scorer.score(ep)
                results.incVisitedCount(1)
                candidates.add(ep, score)
                if (acceptOrds == null || acceptOrds.get(ep)) {
                    results.collect(ep, score)
                }
            }
        }

        // A bound that holds the minimum similarity to the query vector that a candidate vector must
        // have to be considered.
        var minAcceptedSimilarity: Float = Math.nextUp(results.minCompetitiveSimilarity())
        while (candidates.size() > 0 && !results.earlyTerminated()) {
            // get the best candidate (closest or best scoring)
            val topCandidateSimilarity = candidates.topScore()
            if (topCandidateSimilarity < minAcceptedSimilarity) {
                break
            }

            val topCandidateNode = candidates.pop()
            graphSeek(graph, level, topCandidateNode)
            var friendOrd: Int
            while ((graphNextNeighbor(graph).also { friendOrd = it }) != NO_MORE_DOCS) {
                require(friendOrd < size) { "friendOrd=$friendOrd; size=$size" }
                if (visited.getAndSet(friendOrd)) {
                    continue
                }

                if (results.earlyTerminated()) {
                    break
                }
                val friendSimilarity = scorer.score(friendOrd)
                results.incVisitedCount(1)
                if (friendSimilarity >= minAcceptedSimilarity) {
                    candidates.add(friendOrd, friendSimilarity)
                    if (acceptOrds == null || acceptOrds.get(friendOrd)) {
                        if (results.collect(friendOrd, friendSimilarity)) {
                            minAcceptedSimilarity = Math.nextUp(results.minCompetitiveSimilarity())
                        }
                    }
                }
            }
        }
    }

    private fun prepareScratchState(capacity: Int) {
        candidates.clear()
        if (visited.length() < capacity) {
            visited = FixedBitSet.ensureCapacity(visited as FixedBitSet, capacity)
        }
        visited.clear()
    }

    /**
     * Seek a specific node in the given graph. The default implementation will just call [ ][HnswGraph.seek]
     *
     * @throws IOException when seeking the graph
     */
    @Throws(IOException::class)
    open fun graphSeek(graph: HnswGraph, level: Int, targetNode: Int) {
        graph.seek(level, targetNode)
    }

    /**
     * Get the next neighbor from the graph, you must call [.graphSeek]
     * before calling this method. The default implementation will just call [ ][HnswGraph.nextNeighbor]
     *
     * @return see [HnswGraph.nextNeighbor]
     * @throws IOException when advance neighbors
     */
    @Throws(IOException::class)
    open fun graphNextNeighbor(graph: HnswGraph): Int {
        return graph.nextNeighbor()
    }

    /**
     * This class allows [OnHeapHnswGraph] to be searched in a thread-safe manner by avoiding
     * the unsafe methods (seek and nextNeighbor, which maintain state in the graph object) and
     * instead maintaining the state in the searcher object.
     *
     *
     * Note the class itself is NOT thread safe, but since each search will create a new Searcher,
     * the search methods using this class are thread safe.
     */
    private class OnHeapHnswGraphSearcher(candidates: NeighborQueue, visited: BitSet) :
        HnswGraphSearcher(candidates, visited) {
        private var cur: NeighborArray? = null
        private var upto = 0

        override fun graphSeek(graph: HnswGraph, level: Int, targetNode: Int) {
            cur = (graph as OnHeapHnswGraph).getNeighbors(level, targetNode)
            upto = -1
        }

        override fun graphNextNeighbor(graph: HnswGraph): Int {
            if (++upto < cur!!.size()) {
                return cur!!.nodes()[upto]
            }
            return NO_MORE_DOCS
        }
    }

    companion object {
        /**
         * See [HnswGraphSearcher.search]
         *
         * @param scorer the scorer to compare the query with the nodes
         * @param knnCollector a hnsw knn collector of top knn results to be returned
         * @param graph the graph values. May represent the entire graph, or a level in a hierarchical
         * graph.
         * @param acceptOrds [Bits] that represents the allowed document ordinals to match, or
         * `null` if they are all allowed to match.
         */
        @Throws(IOException::class)
        fun search(
            scorer: RandomVectorScorer, knnCollector: KnnCollector, graph: HnswGraph, acceptOrds: Bits
        ) {
            var filteredDocCount = 0
            if (acceptOrds is BitSet) {
                // Use approximate cardinality as this is good enough, but ensure we don't exceed the graph
                // size as that is illogical
                filteredDocCount = min(acceptOrds.approximateCardinality(), graph.size())
            }
            search(scorer, knnCollector, graph, acceptOrds, filteredDocCount)
        }

        /**
         * Searches the HNSW graph for the nearest neighbors of a query vector. If entry points are
         * directly provided via the knnCollector, then the search will be initialized at those points.
         * Otherwise, the search will discover the best entry point per the normal HNSW search algorithm.
         *
         * @param scorer the scorer to compare the query with the nodes
         * @param knnCollector a hnsw knn collector of top knn results to be returned
         * @param graph the graph values. May represent the entire graph, or a level in a hierarchical
         * graph.
         * @param acceptOrds [Bits] that represents the allowed document ordinals to match, or
         * `null` if they are all allowed to match.
         * @param filteredDocCount the number of docs that pass the filter
         */
        @Throws(IOException::class)
        fun search(
            scorer: RandomVectorScorer,
            knnCollector: KnnCollector,
            graph: HnswGraph,
            acceptOrds: Bits?,
            filteredDocCount: Int
        ) {
            require(filteredDocCount >= 0 && filteredDocCount <= graph.size())
            val hnswStrategy: Hnsw
            val searchStrategy = knnCollector.searchStrategy
            hnswStrategy = if (searchStrategy is Hnsw) {
                searchStrategy
            } else if (searchStrategy is Seeded) {
                val originalStrategy = searchStrategy.originalStrategy()
                if (originalStrategy is Hnsw) {
                    originalStrategy
                } else {
                    Hnsw.DEFAULT
                }
            } else {
                Hnsw.DEFAULT
            }
            // First, check if we should use a filtered searcher
            val innerSearcher = if (acceptOrds != null // We can only use filtered search if we know the maxConn
                && graph.maxConn() != HnswGraph.UNKNOWN_MAX_CONN && filteredDocCount > 0 && hnswStrategy.useFilteredSearch(
                    filteredDocCount.toFloat() / graph.size()
                )
            ) {
                FilteredHnswGraphSearcher.create(knnCollector.k(), graph, filteredDocCount, acceptOrds)
            } else {
                HnswGraphSearcher(
                    NeighborQueue(knnCollector.k(), true),
                    SparseFixedBitSet(getGraphSize(graph))
                )
            }
            // Then, check if the search strategy is seeded
            val graphSearcher: AbstractHnswGraphSearcher = if (searchStrategy is Seeded && searchStrategy.numberOfEntryPoints() > 0) {
                SeededHnswGraphSearcher.fromEntryPoints(
                    innerSearcher, searchStrategy.numberOfEntryPoints(), searchStrategy.entryPoints(), graph.size()
                )
            } else {
                innerSearcher
            }
            graphSearcher.search(knnCollector, scorer, graph, acceptOrds)
        }

        /**
         * Search [OnHeapHnswGraph], this method is thread safe.
         *
         * @param scorer the scorer to compare the query with the nodes
         * @param topK the number of nodes to be returned
         * @param graph the graph values. May represent the entire graph, or a level in a hierarchical
         * graph.
         * @param acceptOrds [Bits] that represents the allowed document ordinals to match, or
         * `null` if they are all allowed to match.
         * @param visitedLimit the maximum number of nodes that the search is allowed to visit
         * @return a set of collected vectors holding the nearest neighbors found
         */
        @Throws(IOException::class)
        fun search(
            scorer: RandomVectorScorer, topK: Int, graph: OnHeapHnswGraph, acceptOrds: Bits, visitedLimit: Int
        ): KnnCollector {
            val knnCollector: KnnCollector = TopKnnCollector(topK, visitedLimit, null)
            val graphSearcher =
                OnHeapHnswGraphSearcher(
                    NeighborQueue(topK, true), SparseFixedBitSet(getGraphSize(graph))
                )
            graphSearcher.search(knnCollector, scorer, graph, acceptOrds)
            return knnCollector
        }

        fun getGraphSize(graph: HnswGraph): Int {
            return graph.maxNodeId() + 1
        }
    }
}
