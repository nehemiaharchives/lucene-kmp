package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.search.knn.KnnSearchStrategy
import org.gnit.lucenekmp.util.hnsw.NeighborQueue
import kotlin.jvm.JvmOverloads


/**
 * TopKnnCollector is a specific KnnCollector. A minHeap is used to keep track of the currently
 * collected vectors allowing for efficient updates as better vectors are collected.
 *
 * @lucene.experimental
 */
open class TopKnnCollector @JvmOverloads constructor(k: Int, visitLimit: Int, searchStrategy: KnnSearchStrategy? = null) :
    AbstractKnnCollector(k, visitLimit.toLong(), searchStrategy) {
    protected val queue: NeighborQueue = NeighborQueue(k, false)

    /**
     * @param k the number of neighbors to collect
     * @param visitLimit how many vector nodes the results are allowed to visit
     * @param searchStrategy the search strategy to use
     */

    public override fun collect(docId: Int, similarity: Float): Boolean {
        return queue.insertWithOverflow(docId, similarity)
    }

    public override fun minCompetitiveSimilarity(): Float {
        return if (queue.size() >= k()) queue.topScore() else Float.Companion.NEGATIVE_INFINITY
    }

    public override fun topDocs(): TopDocs {
        require(queue.size() <= k()) { "Tried to collect more results than the maximum number allowed" }
        val scoreDocs = kotlin.arrayOfNulls<ScoreDoc>(queue.size())
        for (i in 1..scoreDocs.size) {
            scoreDocs[scoreDocs.size - i] = ScoreDoc(queue.topNode(), queue.topScore())
            queue.pop()
        }
        val relation: TotalHits.Relation? =
            if (earlyTerminated())
                TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO
            else
                TotalHits.Relation.EQUAL_TO
        return TopDocs(TotalHits(visitedCount(), relation!!), scoreDocs as Array<ScoreDoc>)
    }

    public override fun numCollected(): Int {
        return queue.size()
    }

    override fun toString(): String {
        return "TopKnnCollector[k=" + k() + ", size=" + queue.size() + "]"
    }
}
