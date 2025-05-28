package org.gnit.lucenekmp.search

import okio.IOException

/**
 * A policy defining which filters should be cached.
 *
 *
 * Implementations of this class must be thread-safe.
 *
 * @see UsageTrackingQueryCachingPolicy
 *
 * @see LRUQueryCache
 *
 * @lucene.experimental
 */
// TODO: add APIs for integration with IndexWriter.IndexReaderWarmer
interface QueryCachingPolicy {
    /**
     * Callback that is called every time that a cached filter is used. This is typically useful if
     * the policy wants to track usage statistics in order to make decisions.
     */
    fun onUse(query: Query)

    /**
     * Whether the given [Query] is worth caching. This method will be called by the [ ] to know whether to cache. It will first attempt to load a [DocIdSet] from the
     * cache. If it is not cached yet and this method returns `true` then a cache entry
     * will be generated. Otherwise an uncached scorer will be returned.
     */
    @Throws(IOException::class)
    fun shouldCache(query: Query): Boolean
}
