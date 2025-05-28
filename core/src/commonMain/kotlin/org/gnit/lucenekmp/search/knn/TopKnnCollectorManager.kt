package org.gnit.lucenekmp.search.knn

import okio.IOException
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.KnnCollector
import org.gnit.lucenekmp.search.TopKnnCollector
import org.gnit.lucenekmp.util.hnsw.BlockingFloatHeap

/**
 * TopKnnCollectorManager responsible for creating [TopKnnCollector] instances. When
 * concurrency is supported, the [BlockingFloatHeap] is used to track the global top scores
 * collected across all leaves.
 */
class TopKnnCollectorManager(k: Int, indexSearcher: IndexSearcher) : KnnCollectorManager {
    // the number of docs to collect
    private val k: Int

    // the global score queue used to track the top scores collected across all leaves
    private val globalScoreQueue: BlockingFloatHeap?

    init {
        val isMultiSegments = indexSearcher.getIndexReader().leaves().size > 1
        this.k = k
        this.globalScoreQueue = if (isMultiSegments) BlockingFloatHeap(k) else null
    }

    /**
     * Return a new [TopKnnCollector] instance.
     *
     * @param visitedLimit the maximum number of nodes that the search is allowed to visit
     * @param context the leaf reader context
     */
    @Throws(IOException::class)
    override fun newCollector(
        visitedLimit: Int, searchStrategy: KnnSearchStrategy, context: LeafReaderContext
    ): KnnCollector {
        return if (globalScoreQueue == null) {
            TopKnnCollector(k, visitedLimit, searchStrategy)
        } else {
            MultiLeafKnnCollector(
                k, globalScoreQueue, TopKnnCollector(k, visitedLimit, searchStrategy)
            )
        }
    }
}
