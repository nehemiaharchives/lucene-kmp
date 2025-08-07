package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.util.FrequencyTrackingRingBuffer
import kotlin.reflect.KClass

/**
 * A [QueryCachingPolicy] that tracks usage statistics of recently-used filters in order to
 * decide on which filters are worth caching.
 *
 * @lucene.experimental
 */
open class UsageTrackingQueryCachingPolicy(historySize: Int = 256) :
    QueryCachingPolicy {
    private val recentlyUsedFilters: FrequencyTrackingRingBuffer = FrequencyTrackingRingBuffer(historySize, SENTINEL)

    /**
     * Expert: Create a new instance with a configurable history size. Beware of passing too large
     * values for the size of the history, either [.minFrequencyToCache] returns low values and
     * this means some filters that are rarely used will be cached, which would hurt performance. Or
     * [.minFrequencyToCache] returns high values that are function of the size of the history
     * but then filters will be slow to make it to the cache.
     *
     * @param historySize the number of recently used filters to track
     */

    /**
     * For a given filter, return how many times it should appear in the history before being cached.
     * The default implementation returns 2 for filters that need to evaluate against the entire index
     * to build a [DocIdSetIterator], like [MultiTermQuery], point-based queries or [ ], and 5 for other filters.
     */
    protected fun minFrequencyToCache(query: Query): Int {
        if (isCostly(query)) {
            return 2
        } else {
            // default: cache after the filter has been seen 5 times
            var minFrequency = 5
            if (query is BooleanQuery || query is DisjunctionMaxQuery) {
                // Say you keep reusing a boolean query that looks like "A OR B" and
                // never use the A and B queries out of that context. 5 times after it
                // has been used, we would cache both A, B and A OR B, which is
                // wasteful. So instead we cache compound queries a bit earlier so that
                // we would only cache "A OR B" in that case.
                minFrequency--
            }
            return minFrequency
        }
    }

    override fun onUse(query: Query) {
        assert(query !is BoostQuery)
        assert(query !is ConstantScoreQuery)

        if (shouldNeverCache(query)) {
            return
        }

        // call hashCode outside of sync block
        // in case it's somewhat expensive:
        val hashCode = query.hashCode()

        // we only track hash codes to avoid holding references to possible
        // large queries; this may cause rare false positives, but at worse
        // this just means we cache a query that was not in fact used enough:

        // TODO synchronized is not supported in KMP, need to think what to do here
        //synchronized(this) {
            recentlyUsedFilters.add(hashCode)
        //}
    }

    fun frequency(query: Query): Int {
        assert(query !is BoostQuery)
        assert(query !is ConstantScoreQuery)

        // call hashCode outside of sync block
        // in case it's somewhat expensive:
        val hashCode = query.hashCode()

        // TODO synchronized is not supported in KMP, need to think what to do here
        //synchronized(this) {
            return recentlyUsedFilters.frequency(hashCode)
        //}
    }

    @Throws(IOException::class)
    override fun shouldCache(query: Query): Boolean {
        if (shouldNeverCache(query)) {
            return false
        }
        val frequency = frequency(query)
        val minFrequency = minFrequencyToCache(query)
        return frequency >= minFrequency
    }

    companion object {
        // the hash code that we use as a sentinel in the ring buffer.
        private const val SENTINEL = Int.MIN_VALUE

        private fun isPointQuery(query: Query): Boolean {
            // we need to check for super classes because we occasionally use anonymous
            // sub classes of eg. PointRangeQuery
            var clazz: KClass<*> = query::class
            while (clazz != Query::class) {
                val simpleName: String = clazz.simpleName!!
                if (simpleName.startsWith("Point") && simpleName.endsWith("Query")) {
                    return true
                }

                // TODO this level of reflection is not supported in KMP, so we cannot use, if this commenting out code causes problem, implement a walkaround
                //clazz = clazz.getSuperclass()
            }
            return false
        }

        fun isCostly(query: Query): Boolean {
            // This does not measure the cost of iterating over the filter (for this we
            // already have the DocIdSetIterator#cost API) but the cost to build the
            // DocIdSet in the first place
            return query is MultiTermQuery
                    || query is MultiTermQueryConstantScoreBlendedWrapper<*>
                    || query is MultiTermQueryConstantScoreWrapper<*>
                    || query is TermInSetQuery
                    || isPointQuery(query)
        }

        private fun shouldNeverCache(query: Query): Boolean {
            if (query is TermQuery) {
                // We do not bother caching term queries since they are already plenty fast.
                return true
            }

            if (query is FieldExistsQuery) {
                // We do not bother caching FieldExistsQuery queries since they are already plenty fast.
                return true
            }

            if (query is MatchAllDocsQuery) {
                // MatchAllDocsQuery has an iterator that is faster than what a bit set could do.
                return true
            }

            // For the below queries, it's cheap to notice they cannot match any docs so
            // we do not bother caching them.
            if (query is MatchNoDocsQuery) {
                return true
            }

            if (query is BooleanQuery) {
                if (query.clauses().isEmpty()) {
                    return true
                }
            }

            if (query is DisjunctionMaxQuery) {
                if (query.getDisjuncts().isEmpty()) {
                    return true
                }
            }

            return false
        }
    }
}
