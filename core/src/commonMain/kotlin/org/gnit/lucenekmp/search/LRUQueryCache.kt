package org.gnit.lucenekmp.search

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.ReaderUtil
import org.gnit.lucenekmp.index.TieredMergePolicy
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.Accountables
import org.gnit.lucenekmp.util.BitDocIdSet
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.RamUsageEstimator
import org.gnit.lucenekmp.util.RamUsageEstimator.Companion.HASHTABLE_RAM_BYTES_PER_ENTRY
import org.gnit.lucenekmp.util.RamUsageEstimator.Companion.LINKED_HASHTABLE_RAM_BYTES_PER_ENTRY
import org.gnit.lucenekmp.util.RamUsageEstimator.Companion.QUERY_DEFAULT_RAM_BYTES_USED
import org.gnit.lucenekmp.util.RoaringDocIdSet
import okio.IOException
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.putIfAbsent
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

// make predicate top-level so it can be used in constructor defaults
fun minSegmentSizePredicate(minSize: Int): (LeafReaderContext) -> Boolean = { context ->
    val maxDoc = context.reader().maxDoc()
    if (maxDoc < minSize) {
        false
    } else {
        val topLevelContext = ReaderUtil.getTopLevelContext(context)
        val averageTotalDocs = topLevelContext.reader().maxDoc() / topLevelContext.leaves().size
        maxDoc * 2 > averageTotalDocs
    }
}

/**
 * A [QueryCache] that evicts queries using a LRU (least-recently-used) eviction policy in
 * order to remain under a given maximum size and number of bytes used.
 *
 *
 * This class is thread-safe.
 *
 *
 * Note that query eviction runs in linear time with the total number of segments that have cache
 * entries so this cache works best with [caching policies][QueryCachingPolicy] that only cache
 * on "large" segments, and it is advised to not share this cache across too many indices.
 *
 *
 * A default query cache and policy instance is used in IndexSearcher. If you want to replace
 * those defaults it is typically done like this:
 *
 * <pre class="prettyprint">
 * final int maxNumberOfCachedQueries = 256;
 * final long maxRamBytesUsed = 50 * 1024L * 1024L; // 50MB
 * // these cache and policy instances can be shared across several queries and readers
 * // it is fine to eg. store them into static variables
 * final QueryCache queryCache = new LRUQueryCache(maxNumberOfCachedQueries, maxRamBytesUsed);
 * final QueryCachingPolicy defaultCachingPolicy = new UsageTrackingQueryCachingPolicy();
 * indexSearcher.setQueryCache(queryCache);
 * indexSearcher.setQueryCachingPolicy(defaultCachingPolicy);
</pre> *
 *
 * This cache exposes some global statistics ([hit count][.getHitCount], [ ][.getMissCount], [number of cache entries][.getCacheSize], [ ][.getCacheCount], [ ][.getEvictionCount]). In case you would like to have more fine-grained
 * statistics, such as per-index or per-query-class statistics, it is possible to override various
 * callbacks: [.onHit], [.onMiss], [.onQueryCache], [.onQueryEviction],
 * [.onDocIdSetCache], [.onDocIdSetEviction] and [.onClear]. It is better to not
 * perform heavy computations in these methods though since they are called synchronously and under
 * a lock.
 *
 * @see QueryCachingPolicy
 *
 * @lucene.experimental
 */
@OptIn(ExperimentalAtomicApi::class)
open class LRUQueryCache(
    private val maxSize: Int,
    private val maxRamBytesUsed: Long,
    private val leavesToCache: (LeafReaderContext) -> Boolean = minSegmentSizePredicate(10000),
    skipCacheFactor: Float = 10f
) : QueryCache, Accountable {

    // maps queries that are contained in the cache to a singleton so that this
    // cache does not store several copies of the same query
    private val uniqueQueries: MutableMap<Query, Query>

    // The contract between this set and the per-leaf caches is that per-leaf caches
    // are only allowed to store sub-sets of the queries that are contained in
    // mostRecentlyUsedQueries. This is why write operations are performed under a lock
    private val mostRecentlyUsedQueries: MutableSet<Query>
    private val cache: MutableMap<IndexReader.CacheKey, LeafCache>



    //private val readLock: java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock
    //private val writeLock: java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock
    /**
     * In Lucene’s Java implementation you’ll see a ReentrantReadWriteLock because it’s a “blocking” lock on
     * JVM threads and you often want many threads to read in parallel while only writes are exclusive.
     * In Kotlin’s coroutines world you use a non‐blocking, cooperative Mutex – it never blocks an OS thread,
     * only suspends the coroutine – and it’s lightweight enough that you usually don’t need separate
     * read vs write locks. If you truly need reader/writer semantics in coroutines you’d have to build it
     * (e.g. with a Semaphore counting readers) or pull in a third‐party RWMutex, but for most use cases
     * one suspending Mutex is simple and fast enough.
    */
    private val cacheMutex: Mutex

    private val skipCacheFactor: Float
    @OptIn(ExperimentalAtomicApi::class)
    private val hitCount: AtomicLong
    @OptIn(ExperimentalAtomicApi::class)
    private val missCount: AtomicLong

    // these variables are volatile so that we do not need to sync reads
    // but increments need to be performed under the lock
    @Volatile
    private var ramBytesUsed: Long

    /**
     * Return the total number of cache entries that have been generated and put in the cache. It is
     * highly desirable to have a [hit count][.getHitCount] that is much higher than the [ ][.getCacheCount] as the opposite would indicate that the query cache makes efforts
     * in order to cache queries but then they do not get reused.
     *
     * @see .getCacheSize
     * @see .getEvictionCount
     */
    @Volatile
    var cacheCount: Long = 0
        private set

    /**
     * Return the total number of [DocIdSet]s which are currently stored in the cache.
     *
     * @see .getCacheCount
     * @see .getEvictionCount
     */
    @Volatile
    var cacheSize: Long = 0
        private set

    /**
     * Expert: Create a new instance that will cache at most `maxSize` queries with at most
     * `maxRamBytesUsed` bytes of memory, only on leaves that satisfy `leavesToCache`.
     *
     *
     * Also, clauses whose cost is `skipCacheFactor` times more than the cost of the
     * top-level query will not be cached in order to not slow down queries too much.
     */
    /**
     * Create a new instance that will cache at most `maxSize` queries with at most `
     * maxRamBytesUsed` bytes of memory. Queries will only be cached on leaves that have more
     * than 10k documents and have more than half of the average documents per leave of the index.
     * This should guarantee that all leaves from the upper [tier][TieredMergePolicy] will be
     * cached. Only clauses whose cost is at most 100x the cost of the top-level query will be cached
     * in order to not hurt latency too much because of caching.
     */
    init {
        require(skipCacheFactor >= 1 != false) { "skipCacheFactor must be no less than 1, get $skipCacheFactor" }
        this.skipCacheFactor = skipCacheFactor

        uniqueQueries = mutableMapOf()
            /*java.util.Collections.synchronizedMap<Query, Query>(
                LinkedHashMap<Query, Query>(16, 0.75f, true)
            )*/
        mostRecentlyUsedQueries = uniqueQueries.keys
        cache = mutableMapOf() /*java.util.IdentityHashMap<IndexReader.CacheKey, LeafCache>()*/

        /*val lock: java.util.concurrent.locks.ReentrantReadWriteLock =
            java.util.concurrent.locks.ReentrantReadWriteLock()
        writeLock = lock.writeLock()
        readLock = lock.readLock()*/
        cacheMutex = Mutex()

        ramBytesUsed = 0
        hitCount = AtomicLong(0)
        missCount = AtomicLong(0)
    }

    /**
     * Expert: callback when there is a cache hit on a given query. Implementing this method is
     * typically useful in order to compute more fine-grained statistics about the query cache.
     *
     * @see .onMiss
     *
     * @lucene.experimental
     */
    protected fun onHit(readerCoreKey: Any, query: Query) {
        hitCount.incrementAndFetch()
    }

    /**
     * Expert: callback when there is a cache miss on a given query.
     *
     * @see .onHit
     *
     * @lucene.experimental
     */
    protected fun onMiss(readerCoreKey: Any, query: Query) {
        //checkNotNull(query)
        missCount.incrementAndFetch()
    }

    /**
     * Expert: callback when a query is added to this cache. Implementing this method is typically
     * useful in order to compute more fine-grained statistics about the query cache.
     *
     * @see .onQueryEviction
     *
     * @lucene.experimental
     */
    protected fun onQueryCache(query: Query, ramBytesUsed: Long) {
        //assert(writeLock.isHeldByCurrentThread())
        this.ramBytesUsed += ramBytesUsed
    }

    /**
     * Expert: callback when a query is evicted from this cache.
     *
     * @see .onQueryCache
     *
     * @lucene.experimental
     */
    protected fun onQueryEviction(query: Query, ramBytesUsed: Long) {
        //assert(writeLock.isHeldByCurrentThread())
        this.ramBytesUsed -= ramBytesUsed
    }

    /**
     * Expert: callback when a [DocIdSet] is added to this cache. Implementing this method is
     * typically useful in order to compute more fine-grained statistics about the query cache.
     *
     * @see .onDocIdSetEviction
     *
     * @lucene.experimental
     */
    protected fun onDocIdSetCache(readerCoreKey: Any, ramBytesUsed: Long) {
        //assert(writeLock.isHeldByCurrentThread())
        cacheSize += 1
        cacheCount += 1
        this.ramBytesUsed += ramBytesUsed
    }

    /**
     * Expert: callback when one or more [DocIdSet]s are removed from this cache.
     *
     * @see .onDocIdSetCache
     *
     * @lucene.experimental
     */
    protected fun onDocIdSetEviction(readerCoreKey: Any, numEntries: Int, sumRamBytesUsed: Long) {
        //assert(writeLock.isHeldByCurrentThread())
        this.ramBytesUsed -= sumRamBytesUsed
        cacheSize -= numEntries.toLong()
    }

    /**
     * Expert: callback when the cache is completely cleared.
     *
     * @lucene.experimental
     */
    protected fun onClear() {
        //assert(writeLock.isHeldByCurrentThread())
        ramBytesUsed = 0
        cacheSize = 0
    }

    /** Whether evictions are required.  */
    fun requiresEviction(): Boolean {
        //assert(writeLock.isHeldByCurrentThread())
        val size = mostRecentlyUsedQueries.size
        return if (size == 0) {
            false
        } else {
            size > maxSize || ramBytesUsed() > maxRamBytesUsed
        }
    }

    fun get(
        key: Query,
        cacheHelper: IndexReader.CacheHelper
    ): CacheAndCount? {
        assert(key !is BoostQuery)
        assert(key !is ConstantScoreQuery)
        val readerKey: IndexReader.CacheKey = cacheHelper.key
        val leafCache = cache[readerKey]
        if (leafCache == null) {
            onMiss(readerKey, key)
            return null
        }
        // this get call moves the query to the most-recently-used position
        val singleton: Query? = uniqueQueries[key]
        if (singleton == null) {
            onMiss(readerKey, key)
            return null
        }
        val cached = leafCache.get(singleton)
        if (cached == null) {
            onMiss(readerKey, singleton)
        } else {
            onHit(readerKey, singleton)
        }
        return cached
    }

    private suspend fun putIfAbsent(
        query: Query,
        cached: CacheAndCount,
        cacheHelper: IndexReader.CacheHelper
    ) {
        var query: Query = query
        assert(query !is BoostQuery)
        assert(query !is ConstantScoreQuery)
        // under a lock to make sure that mostRecentlyUsedQueries and cache remain sync'ed
        //writeLock.lock()
        cacheMutex.withLock {
            val singleton: Query? = uniqueQueries.putIfAbsent(query, query)
            if (singleton == null) {
                onQueryCache(query, getRamBytesUsed(query))
            } else {
                query = singleton
            }
            val key: IndexReader.CacheKey = cacheHelper.key
            var leafCache = cache[key]
            if (leafCache == null) {
                leafCache = LeafCache(key)
                val previous = cache.put(key, leafCache)
                ramBytesUsed += HASHTABLE_RAM_BYTES_PER_ENTRY
                assert(previous == null)
                // we just created a new leaf cache, need to register a close listener
                cacheHelper.addClosedListener { coreKey: Any ->
                    runBlocking {
                        this@LRUQueryCache.clearCoreCacheKey(
                            coreKey
                        )
                    }
                }
            }
            leafCache.putIfAbsent(query, cached)
            evictIfNecessary()
        } /*finally {
            writeLock.unlock()
        }*/
    }

    private fun evictIfNecessary() {
        //assert(writeLock.isHeldByCurrentThread())
        // under a lock to make sure that mostRecentlyUsedQueries and cache keep sync'ed
        if (requiresEviction()) {
            val iterator: MutableIterator<Query> = mostRecentlyUsedQueries.iterator()
            do {
                val query: Query = iterator.next()
                val size = mostRecentlyUsedQueries.size
                iterator.remove()
                if (size == mostRecentlyUsedQueries.size) {
                    // size did not decrease, because the hash of the query changed since it has been
                    // put into the cache
                    throw ConcurrentModificationException(
                        ("Removal from the cache failed! This "
                                + "is probably due to a query which has been modified after having been put into "
                                + " the cache or a badly implemented clone(). Query class: ["
                                + query::class
                                + "], query: ["
                                + query
                                + "]")
                    )
                }
                onEviction(query)
            } while (iterator.hasNext() && requiresEviction())
        }
    }

    /** Remove all cache entries for the given core cache key.  */
    suspend fun clearCoreCacheKey(coreKey: Any) {
        //writeLock.lock()
        cacheMutex.withLock {
            val leafCache = cache.remove(coreKey)
            if (leafCache != null) {
                ramBytesUsed -= HASHTABLE_RAM_BYTES_PER_ENTRY
                val numEntries = leafCache.cache.size
                if (numEntries > 0) {
                    onDocIdSetEviction(coreKey, numEntries, leafCache.ramBytesUsed)
                } else {
                    assert(numEntries == 0)
                    assert(leafCache.ramBytesUsed == 0L)
                }
            }
        } /*finally {
            writeLock.unlock()
        }*/
    }

    /** Remove all cache entries for the given query.  */
    suspend fun clearQuery(query: Query) {
        //writeLock.lock()
        cacheMutex.withLock {
            val singleton: Query? = uniqueQueries.remove(query)
            if (singleton != null) {
                onEviction(singleton)
            }
        } /*finally {
            writeLock.unlock()
        }*/
    }

    private fun onEviction(singleton: Query) {
        //assert(writeLock.isHeldByCurrentThread())
        onQueryEviction(singleton, getRamBytesUsed(singleton))
        for (leafCache in cache.values) {
            leafCache.remove(singleton)
        }
    }

    /** Clear the content of this cache.  */
    suspend fun clear() {
        //writeLock.lock()
        cacheMutex.withLock {
            cache.clear()
            // Note that this also clears the uniqueQueries map since mostRecentlyUsedQueries is the
            // uniqueQueries.keySet view:
            mostRecentlyUsedQueries.clear()
            onClear()
        } /*finally {
            writeLock.unlock()
        }*/
    }

    // pkg-private for testing
    suspend fun assertConsistent() {
        //writeLock.lock()
        cacheMutex.withLock {
            if (requiresEviction()) {
                throw AssertionError(
                    ("requires evictions: size="
                            + mostRecentlyUsedQueries.size
                            + ", maxSize="
                            + maxSize
                            + ", ramBytesUsed="
                            + ramBytesUsed()
                            + ", maxRamBytesUsed="
                            + maxRamBytesUsed)
                )
            }
            for (leafCache in cache.values) {
                val keys: MutableSet<Query> = mutableSetOf()
                    /*java.util.Collections.newSetFromMap<Query>(java.util.IdentityHashMap<Query, Boolean>())*/
                keys.addAll(leafCache.cache.keys)
                keys.removeAll(mostRecentlyUsedQueries)
                if (!keys.isEmpty()) {
                    throw AssertionError(
                        "One leaf cache contains more keys than the top-level cache: $keys"
                    )
                }
            }
            var recomputedRamBytesUsed: Long =
                HASHTABLE_RAM_BYTES_PER_ENTRY * cache.size
            for (query in mostRecentlyUsedQueries) {
                recomputedRamBytesUsed += getRamBytesUsed(query)
            }
            for (leafCache in cache.values) {
                recomputedRamBytesUsed += HASHTABLE_RAM_BYTES_PER_ENTRY * leafCache.cache.size
                for (cached in leafCache.cache.values) {
                    recomputedRamBytesUsed += cached.ramBytesUsed()
                }
            }
            if (recomputedRamBytesUsed != ramBytesUsed) {
                throw AssertionError(
                    "ramBytesUsed mismatch : $ramBytesUsed != $recomputedRamBytesUsed"
                )
            }

            var recomputedCacheSize: Long = 0
            for (leafCache in cache.values) {
                recomputedCacheSize += leafCache.cache.size.toLong()
            }
            if (recomputedCacheSize != this.cacheSize) {
                throw AssertionError(
                    "cacheSize mismatch : " + this.cacheSize + " != " + recomputedCacheSize
                )
            }
        } /*finally {
            writeLock.unlock()
        }*/
    }

    // pkg-private for testing
    // return the list of cached queries in LRU order
    suspend fun cachedQueries(): MutableList<Query> {
        //readLock.lock()
        cacheMutex.withLock {
            return mostRecentlyUsedQueries.toMutableList()
        } /*finally {
            readLock.unlock()
        }*/
    }

    override fun doCache(
        weight: Weight,
        policy: QueryCachingPolicy
    ): Weight {
        var weight: Weight = weight
        while (weight is CachingWrapperWeight) {
            weight = weight.`in`
        }

        return CachingWrapperWeight(weight, policy)
    }

    override fun ramBytesUsed(): Long {
        return ramBytesUsed
    }

    override val childResources: MutableCollection<Accountable>
        get() {
            return runBlocking {//writeLock.lock()
                cacheMutex.withLock {
                    return@withLock Accountables.namedAccountables("segment", cache)
                } /*finally {
                writeLock.unlock()
            }*/
            }
        }

    /**
     * Default cache implementation: uses [RoaringDocIdSet] for sets that have a density &lt; 1%
     * and a [BitDocIdSet] over a [FixedBitSet] otherwise.
     */
    @Throws(IOException::class)
    protected fun cacheImpl(scorer: BulkScorer, maxDoc: Int): CacheAndCount {
        return if (scorer.cost() * 100 >= maxDoc) {
            // FixedBitSet is faster for dense sets and will enable the random-access
            // optimization in ConjunctionDISI
            cacheIntoBitSet(scorer, maxDoc)
        } else {
            cacheIntoRoaringDocIdSet(scorer, maxDoc)
        }
    }

    val totalCount: Long
        /**
         * Return the total number of times that a [Query] has been looked up in this [ ]. Note that this number is incremented once per segment so running a cached query
         * only once will increment this counter by the number of segments that are wrapped by the
         * searcher. Note that by definition, [.getTotalCount] is the sum of [ ][.getHitCount] and [.getMissCount].
         *
         * @see .getHitCount
         * @see .getMissCount
         */
        get() = getHitCount() + getMissCount()

    /**
     * Over the [total][.getTotalCount] number of times that a query has been looked up, return
     * how many times a cached [DocIdSet] has been found and returned.
     *
     * @see .getTotalCount
     * @see .getMissCount
     */
    fun getHitCount(): Long {
        return hitCount.load()
    }

    /**
     * Over the [total][.getTotalCount] number of times that a query has been looked up, return
     * how many times this query was not contained in the cache.
     *
     * @see .getTotalCount
     * @see .getHitCount
     */
    fun getMissCount(): Long {
        return missCount.load()
    }

    val evictionCount: Long
        /**
         * Return the number of cache entries that have been removed from the cache either in order to
         * stay under the maximum configured size/ram usage, or because a segment has been closed. High
         * numbers of evictions might mean that queries are not reused or that the [ ] caches too aggressively on NRT segments which get merged
         * early.
         *
         * @see .getCacheCount
         * @see .getCacheSize
         */
        get() = this.cacheCount - this.cacheSize

    // this class is not thread-safe, everything but ramBytesUsed needs to be called under a lock
    private inner class LeafCache(private val key: Any) : Accountable {
        val cache: MutableMap<Query, CacheAndCount> = mutableMapOf()
        /*java.util.IdentityHashMap<Query, CacheAndCount>()*/

        @Volatile
        var ramBytesUsed: Long = 0

        fun onDocIdSetCache(ramBytesUsed: Long) {
            //assert(writeLock.isHeldByCurrentThread())
            this.ramBytesUsed += ramBytesUsed
            this@LRUQueryCache.onDocIdSetCache(key, ramBytesUsed)
        }

        fun onDocIdSetEviction(ramBytesUsed: Long) {
            //assert(writeLock.isHeldByCurrentThread())
            this.ramBytesUsed -= ramBytesUsed
            this@LRUQueryCache.onDocIdSetEviction(key, 1, ramBytesUsed)
        }

        fun get(query: Query): CacheAndCount? {
            assert(query !is BoostQuery)
            assert(query !is ConstantScoreQuery)
            return cache[query]
        }

        fun putIfAbsent(query: Query, cached: CacheAndCount) {
            //assert(writeLock.isHeldByCurrentThread())
            assert(query !is BoostQuery)
            assert(query !is ConstantScoreQuery)
            if (cache.putIfAbsent(query, cached) == null) {
                // the set was actually put
                onDocIdSetCache(HASHTABLE_RAM_BYTES_PER_ENTRY + cached.ramBytesUsed())
            }
        }

        fun remove(query: Query) {
            //assert(writeLock.isHeldByCurrentThread())
            assert(query !is BoostQuery)
            assert(query !is ConstantScoreQuery)
            val removed = cache.remove(query)
            if (removed != null) {
                onDocIdSetEviction(HASHTABLE_RAM_BYTES_PER_ENTRY + removed.ramBytesUsed())
            }
        }

        override fun ramBytesUsed(): Long {
            return ramBytesUsed
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    private inner class CachingWrapperWeight(
        val `in`: Weight,
        private val policy: QueryCachingPolicy
    ) : ConstantScoreWeight(`in`.query, 1f) {

        // we use an AtomicBoolean because Weight.scorer may be called from multiple
        // threads when IndexSearcher is created with threads
        @OptIn(ExperimentalAtomicApi::class)
        private val used: AtomicBoolean = AtomicBoolean(false)

        @Throws(IOException::class)
        override fun matches(
            context: LeafReaderContext,
            doc: Int
        ): Matches? {
            return `in`.matches(context, doc)
        }

        fun cacheEntryHasReasonableWorstCaseSize(maxDoc: Int): Boolean {
            // The worst-case (dense) is a bit set which needs one bit per document
            val worstCaseRamUsage = (maxDoc / 8).toLong()
            val totalRamAvailable = maxRamBytesUsed
            // Imagine the worst-case that a cache entry is large than the size of
            // the cache: not only will this entry be trashed immediately but it
            // will also evict all current entries from the cache. For this reason
            // we only cache on an IndexReader if we have available room for
            // 5 different filters on this reader to avoid excessive trashing
            return worstCaseRamUsage * 5 < totalRamAvailable
        }

        /** Check whether this segment is eligible for caching, regardless of the query.  */
        @Throws(IOException::class)
        fun shouldCache(context: LeafReaderContext): Boolean {
            return cacheEntryHasReasonableWorstCaseSize(
                ReaderUtil.getTopLevelContext(context).reader().maxDoc()
            )
                    && leavesToCache(context)
        }

        @Throws(IOException::class)
        override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
            if (used.compareAndSet(expectedValue = false, newValue = true)) {
                policy.onUse(query)
            }

            if (!`in`.isCacheable(context)) {
                // this segment is not suitable for caching
                return `in`.scorerSupplier(context)
            }

            // Short-circuit: Check whether this segment is eligible for caching
            // before we take a lock because of #get
            if (!shouldCache(context)) {
                return `in`.scorerSupplier(context)
            }

            val cacheHelper: IndexReader.CacheHelper = context.reader().coreCacheHelper
            if (cacheHelper == null) {
                // this reader has no cache helper
                return `in`.scorerSupplier(context)
            }

            // If the lock is already busy, prefer using the uncached version than waiting
            if (!cacheMutex.tryLock()) {
                return `in`.scorerSupplier(context)
            }

            var cached: CacheAndCount?
            try {
                cached = get(`in`.query, cacheHelper)
            } finally {
                cacheMutex.unlock()
            }

            val maxDoc: Int = context.reader().maxDoc()
            if (cached == null) {
                if (policy.shouldCache(`in`.query)) {
                    val supplier: ScorerSupplier? = `in`.scorerSupplier(context)
                    if (supplier == null) {
                        runBlocking { putIfAbsent(`in`.query, CacheAndCount.EMPTY, cacheHelper) }
                        return null
                    }

                    val cost: Long = supplier.cost()
                    return object : ConstantScoreScorerSupplier(
                        0f,
                        ScoreMode.COMPLETE_NO_SCORES,
                        maxDoc
                    ) {
                        @Throws(IOException::class)
                        override fun iterator(leadCost: Long): DocIdSetIterator {
                            // skip cache operation which would slow query down too much
                            if (cost / skipCacheFactor > leadCost) {
                                return supplier.get(leadCost).iterator()
                            }

                            val cached = cacheImpl(supplier.bulkScorer()!!, maxDoc)
                            runBlocking { putIfAbsent(`in`.query, cached, cacheHelper) }
                            var disi: DocIdSetIterator = cached.iterator()
                            if (disi == null) {
                                // docIdSet.iterator() is allowed to return null when empty but we want a non-null
                                // iterator here
                                disi = DocIdSetIterator.empty()
                            }

                            return disi
                        }

                        override fun cost(): Long {
                            return cost
                        }
                    }
                } else {
                    return `in`.scorerSupplier(context)
                }
            }

            //checkNotNull(cached)
            if (cached === CacheAndCount.EMPTY) {
                return null
            }
            val disi: DocIdSetIterator = cached.iterator()
            if (disi == null) {
                return null
            }

            return ConstantScoreScorerSupplier.fromIterator(
                disi, 0f, ScoreMode.COMPLETE_NO_SCORES, maxDoc
            )
        }

        @Throws(IOException::class)
        override fun count(context: LeafReaderContext): Int {
            // Our cache won't have an accurate count if there are deletions
            if (context.reader().hasDeletions()) {
                return `in`.count(context)
            }

            // Otherwise check if the count is in the cache
            if (used.compareAndSet(expectedValue = false, newValue = true)) {
                policy.onUse(query)
            }

            if (!`in`.isCacheable(context)) {
                // this segment is not suitable for caching
                return `in`.count(context)
            }

            // Short-circuit: Check whether this segment is eligible for caching
            // before we take a lock because of #get
            if (!shouldCache(context)) {
                return `in`.count(context)
            }

            val cacheHelper: IndexReader.CacheHelper = context.reader().coreCacheHelper
            if (cacheHelper == null) {
                // this reader has no cacheHelper
                return `in`.count(context)
            }

            // If the lock is already busy, prefer using the uncached version than waiting
            if (!cacheMutex.tryLock()) {
                return `in`.count(context)
            }

            var cached: CacheAndCount?
            try {
                cached = get(`in`.query, cacheHelper)
            } finally {
                cacheMutex.unlock()
            }
            if (cached != null) {
                // cached
                return cached.count()
            }
            // Not cached, check if the wrapped weight can count quickly then use that
            return `in`.count(context)
        }

        override fun isCacheable(ctx: LeafReaderContext): Boolean {
            return `in`.isCacheable(ctx)
        }
    }

    /** Cache of doc ids with a count.  */
    class CacheAndCount(private val cache: DocIdSet, private val count: Int) : Accountable {

        @Throws(IOException::class)
        fun iterator(): DocIdSetIterator {
            return cache.iterator()
        }

        fun count(): Int {
            return count
        }

        override fun ramBytesUsed(): Long {
            return BASE_RAM_BYTES_USED + cache.ramBytesUsed()
        }

        companion object {
            val EMPTY: CacheAndCount = CacheAndCount(DocIdSet.EMPTY, 0)

            private val BASE_RAM_BYTES_USED: Long =
                RamUsageEstimator.shallowSizeOfInstance(CacheAndCount::class)
        }
    }

    companion object {
        private fun getRamBytesUsed(query: Query): Long {
            return (LINKED_HASHTABLE_RAM_BYTES_PER_ENTRY
                    + (if (query is Accountable)
                query.ramBytesUsed()
            else
                QUERY_DEFAULT_RAM_BYTES_USED.toLong()))
        }

        @Throws(IOException::class)
        private fun cacheIntoBitSet(scorer: BulkScorer, maxDoc: Int): CacheAndCount {
            val bitSet = FixedBitSet(maxDoc)
            val count = IntArray(1)
            scorer.score(
                object : LeafCollector {
                    @Throws(IOException::class)
                    override fun setScorer(scorer: Scorable) {
                    }

                    @Throws(IOException::class)
                    override fun collect(doc: Int) {
                        count[0]++
                        bitSet.set(doc)
                    }
                },
                null,
                0,
                DocIdSetIterator.NO_MORE_DOCS
            )
            return CacheAndCount(BitDocIdSet(bitSet, count[0].toLong()), count[0])
        }

        @Throws(IOException::class)
        private fun cacheIntoRoaringDocIdSet(scorer: BulkScorer, maxDoc: Int): CacheAndCount {
            val builder: RoaringDocIdSet.Builder =
                RoaringDocIdSet.Builder(maxDoc)
            scorer.score(
                object : LeafCollector {
                    @Throws(IOException::class)
                    override fun setScorer(scorer: Scorable) {
                    }

                    @Throws(IOException::class)
                    override fun collect(doc: Int) {
                        builder.add(doc)
                    }
                },
                null,
                0,
                DocIdSetIterator.NO_MORE_DOCS
            )
            val cache: RoaringDocIdSet = builder.build()
            return CacheAndCount(cache, cache.cardinality())
        }
    }
}
