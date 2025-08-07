package org.gnit.lucenekmp.search

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import okio.IOException
import org.gnit.lucenekmp.search.IndexSearcher.LeafSlice
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.jdkport.ExecutionException
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.putIfAbsent
import org.gnit.lucenekmp.util.ThreadInterruptedException

/**
 * Collector manager based on [TotalHitCountCollector] that allows users to parallelize
 * counting the number of hits, expected to be used mostly wrapped in [MultiCollectorManager].
 * For cases when this is the only collector manager used, [IndexSearcher.count] should
 * be called instead of [IndexSearcher.search] as the former is
 * faster whenever the count can be returned directly from the index statistics.
 */
class TotalHitCountCollectorManager
    (leafSlices: Array<LeafSlice>) :
    CollectorManager<TotalHitCountCollector, Int> {
    private val hasSegmentPartitions: Boolean = hasSegmentPartitions(leafSlices)

    /**
     * Internal state shared across the different collectors that this collector manager creates. This
     * is necessary to support intra-segment concurrency. We track leaves seen as an argument of
     * [Collector.getLeafCollector] calls, to ensure correctness: if the
     * first partition of a segment early terminates, count has been already retrieved for the entire
     * segment hence subsequent partitions of the same segment should also early terminate without
     * further incrementing hit count. If the first partition of a segment computes hit counts,
     * subsequent partitions of the same segment should do the same, to prevent their counts from
     * being retrieved from [LRUQueryCache] (which returns counts for the entire segment while
     * we'd need only that of the current leaf partition).
     */
    private val earlyTerminatedMap: MutableMap<Any, CompletableDeferred<Boolean>> = mutableMapOf()
        /*ConcurrentHashMap<Any, Future<Boolean>>()*/

    @Throws(IOException::class)
    override fun newCollector(): TotalHitCountCollector {
        if (hasSegmentPartitions) {
            return LeafPartitionAwareTotalHitCountCollector(earlyTerminatedMap)
        }
        return TotalHitCountCollector()
    }

    @Throws(IOException::class)
    override fun reduce(collectors: MutableCollection<TotalHitCountCollector>): Int {
        // Make the same collector manager instance reusable across multiple searches. It isn't a strict
        // requirement but it is generally supported as collector managers normally don't hold state, as
        // opposed to collectors.
        assert(hasSegmentPartitions || earlyTerminatedMap.isEmpty())
        if (hasSegmentPartitions) {
            earlyTerminatedMap.clear()
        }
        var totalHits = 0
        for (collector in collectors) {
            totalHits += collector.totalHits
        }
        return totalHits
    }

    private class LeafPartitionAwareTotalHitCountCollector(private val earlyTerminatedMap: MutableMap<Any, CompletableDeferred<Boolean>>) :
        TotalHitCountCollector() {

        @OptIn(ExperimentalCoroutinesApi::class)
        @Throws(IOException::class)
        override fun getLeafCollector(context: LeafReaderContext): LeafCollector {
            var earlyTerminated: CompletableDeferred<Boolean>? = earlyTerminatedMap[context.id()]
            if (earlyTerminated == null) {
                val firstEarlyTerminated: CompletableDeferred<Boolean> = CompletableDeferred()
                val previousEarlyTerminated: CompletableDeferred<Boolean>? =
                    earlyTerminatedMap.putIfAbsent(context.id(), firstEarlyTerminated)
                if (previousEarlyTerminated == null) {
                    // first thread for a given leaf gets to decide what the next threads targeting the same
                    // leaf do
                    try {
                        val leafCollector: LeafCollector = super.getLeafCollector(context)
                        firstEarlyTerminated.complete(false)
                        return leafCollector
                    } catch (e: CollectionTerminatedException) {
                        firstEarlyTerminated.complete(true)
                        throw e
                    }
                }
                earlyTerminated = previousEarlyTerminated
            }

            runBlocking {
                try {
                    if (earlyTerminated.getCompleted()) {
                        // first partition of the same leaf early terminated, do the same for subsequent ones
                        throw CollectionTerminatedException()
                    }
                } catch (e: CancellationException) {
                    currentCoroutineContext()[Job]!!.cancel()
                    throw ThreadInterruptedException(e)
                } catch (e: ExecutionException) {
                    throw RuntimeException(e)
                }
            }

            // first partition of the same leaf computed hit counts, do the same for subsequent ones
            return createLeafCollector()
        }
    }

    companion object {
        private fun hasSegmentPartitions(leafSlices: Array<LeafSlice>): Boolean {
            for (leafSlice in leafSlices) {
                for (leafPartition in leafSlice.partitions) {
                    if (leafPartition.minDocId > 0
                        || leafPartition.maxDocId < leafPartition.ctx.reader().maxDoc()
                    ) {
                        return true
                    }
                }
            }
            return false
        }
    }
}
