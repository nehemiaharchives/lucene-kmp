package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.search.knn.KnnSearchStrategy


/**
 * AbstractKnnCollector is the default implementation for a knn collector used for gathering kNN
 * results and providing topDocs from the gathered neighbors
 */
abstract class AbstractKnnCollector protected constructor(
    private val k: Int,
    private val visitLimit: Long,
    override val searchStrategy: KnnSearchStrategy? = null,
) : KnnCollector {
    protected var visitedCount: Long = 0

    override fun earlyTerminated(): Boolean {
        return visitedCount >= visitLimit
    }

    override fun incVisitedCount(count: Int) {
        require(count > 0)
        this.visitedCount += count.toLong()
    }

    override fun visitedCount(): Long {
        return visitedCount
    }

    override fun visitLimit(): Long {
        return visitLimit
    }

    override fun k(): Int {
        return k
    }

    abstract override fun collect(docId: Int, similarity: Float): Boolean

    abstract fun numCollected(): Int

    abstract override fun minCompetitiveSimilarity(): Float

    abstract override fun topDocs(): TopDocs

}
