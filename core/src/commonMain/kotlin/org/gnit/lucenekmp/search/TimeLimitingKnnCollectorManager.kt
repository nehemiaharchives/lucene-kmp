package org.gnit.lucenekmp.search


import kotlinx.io.IOException
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.QueryTimeout
import org.gnit.lucenekmp.search.TotalHits.Relation
import org.gnit.lucenekmp.search.knn.KnnCollectorManager
import org.gnit.lucenekmp.search.knn.KnnSearchStrategy


/** A [KnnCollectorManager] that collects results with a timeout.  */
class TimeLimitingKnnCollectorManager(private val delegate: KnnCollectorManager, timeout: QueryTimeout) : KnnCollectorManager {
    private val queryTimeout: QueryTimeout? = timeout

    /** Get the configured [QueryTimeout] for terminating graph and exact searches.  */
    fun getQueryTimeout(): QueryTimeout? {
        return queryTimeout
    }

    @Throws(IOException::class)
    override fun newCollector(
        visitedLimit: Int, searchStrategy: KnnSearchStrategy, context: LeafReaderContext
    ): KnnCollector {
        val collector: KnnCollector = delegate.newCollector(visitedLimit, searchStrategy, context)
        if (queryTimeout == null) {
            return collector
        }
        return this.TimeLimitingKnnCollector(collector)
    }

    internal inner class TimeLimitingKnnCollector(collector: KnnCollector) : KnnCollector.Decorator(collector) {
        override fun earlyTerminated(): Boolean {
            return queryTimeout!!.shouldExit() || super.earlyTerminated()
        }

        override fun topDocs(): TopDocs {
            val docs: TopDocs = super.topDocs()

            // Mark results as partial if timeout is met
            val relation: Relation =
                if (queryTimeout!!.shouldExit()){
                    Relation.GREATER_THAN_OR_EQUAL_TO
                }else {
                    docs.totalHits.relation
                }
            return TopDocs(TotalHits(docs.totalHits.value, relation), docs.scoreDocs)
        }
    }
}
