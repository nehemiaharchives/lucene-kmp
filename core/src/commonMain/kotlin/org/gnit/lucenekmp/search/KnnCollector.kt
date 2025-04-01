package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.search.knn.KnnSearchStrategy


/**
 * KnnCollector is a knn collector used for gathering kNN results and providing topDocs from the
 * gathered neighbors
 *
 * @lucene.experimental
 */
interface KnnCollector {
    /**
     * If search visits too many documents, the results collector will terminate early. Usually, this
     * is due to some restricted filter on the document set.
     *
     *
     * When collection is earlyTerminated, the results are not a correct representation of k
     * nearest neighbors.
     *
     * @return is the current result set marked as incomplete
     */
    fun earlyTerminated(): Boolean

    /**
     * @param count increments the visited vector count, must be greater than 0.
     */
    fun incVisitedCount(count: Int)

    /**
     * @return the current visited vector count
     */
    fun visitedCount(): Long

    /**
     * @return the visited vector limit
     */
    fun visitLimit(): Long

    /**
     * @return the expected number of collected results
     */
    fun k(): Int

    /**
     * Collect the provided docId and include in the result set.
     *
     * @param docId of the vector to collect
     * @param similarity its calculated similarity
     * @return true if the vector is collected
     */
    fun collect(docId: Int, similarity: Float): Boolean

    /**
     * This method is utilized during search to ensure only competitive results are explored.
     *
     *
     * Consequently, if this results collector wants to collect `k` results, this should return
     * [Float.NEGATIVE_INFINITY] when not full.
     *
     *
     * When full, the minimum score should be returned.
     *
     * @return the current minimum competitive similarity in the collection
     */
    fun minCompetitiveSimilarity(): Float

    /**
     * This drains the collected nearest kNN results and returns them in a new [TopDocs]
     * collection, ordered by score descending. NOTE: This is generally a destructive action and the
     * collector should not be used after topDocs() is called.
     *
     * @return The collected top documents
     */
    fun topDocs(): TopDocs?

    /**
     * @return the search strategy used by this collector, can be null
     */
    val searchStrategy: KnnSearchStrategy?

    /**
     * KnnCollector.Decorator is the base class for decorators of KnnCollector objects, which extend
     * the object with new behaviors.
     *
     * @lucene.experimental
     */
    open class Decorator(protected val collector: KnnCollector) : KnnCollector {
        override fun earlyTerminated(): Boolean {
            return collector.earlyTerminated()
        }

        override fun incVisitedCount(count: Int) {
            collector.incVisitedCount(count)
        }

        override fun visitedCount(): Long {
            return collector.visitedCount()
        }

        override fun visitLimit(): Long {
            return collector.visitLimit()
        }

        override fun k(): Int {
            return collector.k()
        }

        override fun collect(docId: Int, similarity: Float): Boolean {
            return collector.collect(docId, similarity)
        }

        override fun minCompetitiveSimilarity(): Float {
            return collector.minCompetitiveSimilarity()
        }

        override fun topDocs(): TopDocs? {
            return collector.topDocs()
        }

        override val searchStrategy: KnnSearchStrategy?
            get() = collector.searchStrategy
    }
}
