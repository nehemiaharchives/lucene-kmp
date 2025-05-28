package org.gnit.lucenekmp.util.hnsw

import org.gnit.lucenekmp.search.DocIdSetIterator.Companion.NO_MORE_DOCS
import org.gnit.lucenekmp.util.BitSet
import okio.IOException

/**
 * This creates a graph builder that is initialized with the provided HnswGraph. This is useful for
 * merging HnswGraphs from multiple segments.
 *
 * @lucene.experimental
 */
class InitializedHnswGraphBuilder(
    scorerSupplier: RandomVectorScorerSupplier,
    beamWidth: Int,
    seed: Long,
    initializedGraph: OnHeapHnswGraph,
    private val initializedNodes: BitSet
) : HnswGraphBuilder(scorerSupplier, beamWidth, seed, initializedGraph) {

    @Throws(IOException::class)
    override fun addGraphNode(node: Int, scorer: UpdateableRandomVectorScorer) {
        if (initializedNodes.get(node)) {
            return
        }
        super.addGraphNode(node, scorer)
    }

    @Throws(IOException::class)
    override fun addGraphNode(node: Int) {
        if (initializedNodes.get(node)) {
            return
        }
        super.addGraphNode(node)
    }

    companion object {
        /**
         * Create a new HnswGraphBuilder that is initialized with the provided HnswGraph.
         *
         * @param scorerSupplier the scorer to use for vectors
         * @param beamWidth the number of nodes to explore in the search
         * @param seed the seed for the random number generator
         * @param initializerGraph the graph to initialize the new graph builder
         * @param newOrdMap a mapping from the old node ordinal to the new node ordinal
         * @param initializedNodes a bitset of nodes that are already initialized in the initializerGraph
         * @param totalNumberOfVectors the total number of vectors in the new graph, this should include
         * all vectors expected to be added to the graph in the future
         * @return a new HnswGraphBuilder that is initialized with the provided HnswGraph
         * @throws IOException when reading the graph fails
         */
        @Throws(IOException::class)
        fun fromGraph(
            scorerSupplier: RandomVectorScorerSupplier,
            beamWidth: Int,
            seed: Long,
            initializerGraph: HnswGraph,
            newOrdMap: IntArray,
            initializedNodes: BitSet,
            totalNumberOfVectors: Int
        ): InitializedHnswGraphBuilder {
            return InitializedHnswGraphBuilder(
                scorerSupplier,
                beamWidth,
                seed,
                initGraph(initializerGraph, newOrdMap, totalNumberOfVectors),
                initializedNodes
            )
        }

        @Throws(IOException::class)
        fun initGraph(
            initializerGraph: HnswGraph, newOrdMap: IntArray, totalNumberOfVectors: Int
        ): OnHeapHnswGraph {
            val hnsw = OnHeapHnswGraph(initializerGraph.maxConn(), totalNumberOfVectors)
            for (level in initializerGraph.numLevels() - 1 downTo 0) {
                val it: HnswGraph.NodesIterator = initializerGraph.getNodesOnLevel(level)
                while (it.hasNext()) {
                    val oldOrd: Int = it.nextInt()
                    val newOrd = newOrdMap[oldOrd]
                    hnsw.addNode(level, newOrd)
                    hnsw.trySetNewEntryNode(newOrd, level)
                    val newNeighbors = hnsw.getNeighbors(level, newOrd)
                    initializerGraph.seek(level, oldOrd)
                    var oldNeighbor = initializerGraph.nextNeighbor()
                    while (oldNeighbor != NO_MORE_DOCS
                    ) {
                        val newNeighbor = newOrdMap[oldNeighbor]
                        // we will compute these scores later when we need to pop out the non-diverse nodes
                        newNeighbors.addOutOfOrder(newNeighbor, Float.Companion.NaN)
                        oldNeighbor = initializerGraph.nextNeighbor()
                    }
                }
            }
            return hnsw
        }
    }
}
