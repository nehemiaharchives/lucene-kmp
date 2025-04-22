package org.gnit.lucenekmp.util.hnsw


import org.gnit.lucenekmp.search.KnnCollector
import org.gnit.lucenekmp.util.Bits
import kotlinx.io.IOException

/**
 * AbstractHnswGraphSearcher is the base class for HnswGraphSearcher implementations.
 *
 * @lucene.experimental
 */
abstract class AbstractHnswGraphSearcher {
    /**
     * Search a given level of the graph starting at the given entry points.
     *
     * @param results the collector to collect the results
     * @param scorer the scorer to compare the query with the nodes
     * @param level the level of the graph to search
     * @param eps the entry points to start the search from
     * @param graph the HNSWGraph
     * @param acceptOrds the ordinals to accept for the results
     */
    @Throws(IOException::class)
    abstract fun searchLevel(
        results: KnnCollector,
        scorer: RandomVectorScorer,
        level: Int,
        eps: IntArray,
        graph: HnswGraph,
        acceptOrds: Bits?
    )

    /**
     * Function to find the best entry point from which to search the zeroth graph layer.
     *
     * @param scorer the scorer to compare the query with the nodes
     * @param graph the HNSWGraph
     * @param collector the knn result collector
     * @return the best entry point, `-1` indicates graph entry node not set, or visitation limit
     * exceeded
     * @throws IOException When accessing the vectors or graph fails
     */
    @Throws(IOException::class)
    abstract fun findBestEntryPoint(
        scorer: RandomVectorScorer, graph: HnswGraph, collector: KnnCollector
    ): IntArray

    /**
     * Search the graph for the given scorer. Gathering results in the provided collector that pass
     * the provided acceptOrds.
     *
     * @param results the collector to collect the results
     * @param scorer the scorer to compare the query with the nodes
     * @param graph the HNSWGraph
     * @param acceptOrds the ordinals to accept for the results
     * @throws IOException When accessing the vectors or graph fails
     */
    @Throws(IOException::class)
    fun search(
        results: KnnCollector, scorer: RandomVectorScorer, graph: HnswGraph, acceptOrds: Bits?
    ) {
        val eps = findBestEntryPoint(scorer, graph, results)
        require(eps != null && eps.size > 0)
        if (eps[0] == UNK_EP) {
            return
        }
        searchLevel(results, scorer, 0, eps, graph, acceptOrds)
    }

    companion object {
        val UNK_EP: Int = -1
    }
}
