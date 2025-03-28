package org.gnit.lucenekmp.search

/**
 * A cache for queries.
 *
 * @see LRUQueryCache
 *
 * @lucene.experimental
 */
interface QueryCache {
    /**
     * Return a wrapper around the provided `weight` that will cache matching docs
     * per-segment accordingly to the given `policy`. NOTE: The returned weight will only
     * be equivalent if scores are not needed.
     *
     * @see Collector.scoreMode
     */
    fun doCache(weight: Weight, policy: QueryCachingPolicy): Weight
}
