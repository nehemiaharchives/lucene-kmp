package org.gnit.lucenekmp.util.hnsw


import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.DocIdSetIterator.Companion.NO_MORE_DOCS
import org.gnit.lucenekmp.search.KnnCollector
import org.gnit.lucenekmp.util.Bits
import kotlinx.io.IOException

/**
 * A [HnswGraphSearcher] that uses a set of seed ordinals to initiate the search.
 *
 * @lucene.experimental
 */
internal class SeededHnswGraphSearcher(
    private val delegate: AbstractHnswGraphSearcher,
    private val seedOrds: IntArray
) : AbstractHnswGraphSearcher() {
    @Throws(IOException::class)
    override fun searchLevel(
        results: KnnCollector,
        scorer: RandomVectorScorer,
        level: Int,
        eps: IntArray,
        graph: HnswGraph,
        acceptOrds: Bits?
    ) {
        delegate.searchLevel(results, scorer, level, eps, graph, acceptOrds)
    }

    override fun findBestEntryPoint(
        scorer: RandomVectorScorer,
        graph: HnswGraph,
        collector: KnnCollector
    ): IntArray {
        return seedOrds
    }

    companion object {
        @Throws(IOException::class)
        fun fromEntryPoints(
            delegate: AbstractHnswGraphSearcher, numEps: Int, eps: DocIdSetIterator, graphSize: Int
        ): SeededHnswGraphSearcher {
            require(numEps > 0) { "The number of entry points must be > 0" }
            val entryPoints = IntArray(numEps)
            var idx = 0
            while (idx < entryPoints.size) {
                val entryPointOrdInt: Int = eps.nextDoc()
                require(entryPointOrdInt != NO_MORE_DOCS) { "The number of entry points provided is less than the number of entry points requested" }
                require(entryPointOrdInt < graphSize)
                entryPoints[idx++] = entryPointOrdInt
            }
            return SeededHnswGraphSearcher(delegate, entryPoints)
        }
    }
}
