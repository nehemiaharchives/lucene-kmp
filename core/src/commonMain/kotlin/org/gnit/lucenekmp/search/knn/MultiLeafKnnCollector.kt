package org.gnit.lucenekmp.search.knn

import kotlinx.coroutines.runBlocking
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.search.AbstractKnnCollector
import org.gnit.lucenekmp.search.KnnCollector.Decorator
import org.gnit.lucenekmp.util.hnsw.BlockingFloatHeap
import org.gnit.lucenekmp.util.hnsw.FloatHeap
import kotlin.math.max
import kotlin.math.min

/**
 * MultiLeafKnnCollector is a specific KnnCollector that can exchange the top collected results
 * across segments through a shared global queue.
 *
 * @lucene.experimental
 */
class MultiLeafKnnCollector(
    k: Int,
    greediness: Float,
    interval: Int,
    globalSimilarityQueue: BlockingFloatHeap,
    subCollector: AbstractKnnCollector
) : Decorator(subCollector) {
    // the global queue of the highest similarities collected so far across all segments
    private val globalSimilarityQueue: BlockingFloatHeap

    // the local queue of the highest similarities if we are not competitive globally
    // the size of this queue is defined by greediness
    private val nonCompetitiveQueue: FloatHeap

    // the queue of the local similarities to periodically update with the global queue
    private val updatesQueue: FloatHeap
    private val updatesScratch: FloatArray

    // interval to synchronize the local and global queues, as a number of visited vectors
    private val interval: Int
    private var kResultsCollected = false
    private var cachedGlobalMinSim = Float.Companion.NEGATIVE_INFINITY
    private val subCollector: AbstractKnnCollector

    /**
     * Create a new MultiLeafKnnCollector.
     *
     * @param k the number of neighbors to collect
     * @param globalSimilarityQueue the global queue of the highest similarities collected so far
     * across all segments
     * @param subCollector the local collector
     */
    constructor(k: Int, globalSimilarityQueue: BlockingFloatHeap, subCollector: AbstractKnnCollector) : this(
        k,
        DEFAULT_GREEDINESS,
        DEFAULT_INTERVAL,
        globalSimilarityQueue,
        subCollector
    )

    /**
     * Create a new MultiLeafKnnCollector.
     *
     * @param k the number of neighbors to collect
     * @param greediness the greediness of the global search
     * @param interval (by number of collected values) the interval to synchronize the local and
     * global queues
     * @param globalSimilarityQueue the global queue of the highest similarities collected so far
     * @param subCollector the local collector
     */
    init {
        require(!(greediness < 0 || greediness > 1)) { "greediness must be in [0,1]" }
        require(interval > 0) { "interval must be positive" }
        this.interval = interval
        this.subCollector = subCollector
        this.globalSimilarityQueue = globalSimilarityQueue
        this.nonCompetitiveQueue = FloatHeap(max(1, Math.round((1 - greediness) * k)))
        this.updatesQueue = FloatHeap(k)
        this.updatesScratch = FloatArray(k)
    }

    override fun collect(docId: Int, similarity: Float): Boolean {
        val localSimUpdated: Boolean = subCollector.collect(docId, similarity)
        val firstKResultsCollected =
            (kResultsCollected == false && subCollector.numCollected() == k())
        if (firstKResultsCollected) {
            kResultsCollected = true
        }
        updatesQueue.offer(similarity)
        var globalSimUpdated: Boolean = nonCompetitiveQueue.offer(similarity)

        if (kResultsCollected) {
            // as we've collected k results, we can start do periodic updates with the global queue
            if (firstKResultsCollected || (subCollector.visitedCount() and interval.toLong()) == 0L) {
                // BlockingFloatHeap#offer requires input to be sorted in ascending order, so we can't
                // pass in the underlying updatesQueue array as-is since it is only partially ordered
                // (see GH#13462):
                val len: Int = updatesQueue.size()
                if (len > 0) {
                    for (i in 0..<len) {
                        updatesScratch[i] = updatesQueue.poll()
                    }
                    require(updatesQueue.size() == 0)
                    cachedGlobalMinSim = runBlocking {
                        globalSimilarityQueue.offer(updatesScratch, len)
                    }
                    globalSimUpdated = true
                }
            }
        }
        return localSimUpdated || globalSimUpdated
    }

    public override fun minCompetitiveSimilarity(): Float {
        if (kResultsCollected == false) {
            return Float.Companion.NEGATIVE_INFINITY
        }
        return max(
            subCollector.minCompetitiveSimilarity(),
            min(nonCompetitiveQueue.peek(), cachedGlobalMinSim)
        )
    }

    override fun toString(): String {
        return "MultiLeafKnnCollector[subCollector=$subCollector]"
    }

    companion object {
        // greediness of globally non-competitive search: (0,1]
        private const val DEFAULT_GREEDINESS = 0.9f
        private const val DEFAULT_INTERVAL = 0xff
    }
}
