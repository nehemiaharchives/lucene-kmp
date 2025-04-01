package org.gnit.lucenekmp.search.knn

import kotlinx.io.IOException
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.search.KnnCollector

/**
 * KnnCollectorManager responsible for creating [KnnCollector] instances. Useful to create
 * [KnnCollector] instances that share global state across leaves, such a global queue of
 * results collected so far.
 */
interface KnnCollectorManager {
    /**
     * Return a new [KnnCollector] instance.
     *
     * @param visitedLimit the maximum number of nodes that the search is allowed to visit
     * @param searchStrategy the optional search strategy configuration
     * @param context the leaf reader context
     */
    @Throws(IOException::class)
    fun newCollector(
        visitedLimit: Int, searchStrategy: KnnSearchStrategy, context: LeafReaderContext
    ): KnnCollector
}
