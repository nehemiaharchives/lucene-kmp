package org.gnit.lucenekmp.search

import kotlinx.coroutines.Runnable
import kotlinx.coroutines.runBlocking
import okio.IOException
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexReaderContext
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.QueryTimeout
import org.gnit.lucenekmp.index.ReaderUtil
import org.gnit.lucenekmp.index.StoredFields
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.jdkport.Callable
import org.gnit.lucenekmp.jdkport.Executor
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.similarities.BM25Similarity
import org.gnit.lucenekmp.search.similarities.Similarity
import org.gnit.lucenekmp.store.NIOFSDirectory
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.automaton.ByteRunAutomaton
import kotlin.concurrent.Volatile
import kotlin.math.max
import kotlin.math.min


/**
 * Implements search over a single IndexReader.
 *
 *
 * Applications usually need only call the inherited [.search] method. For
 * performance reasons, if your index is unchanging, you should share a single IndexSearcher
 * instance across multiple searches instead of creating a new one per-search. If your index has
 * changed and you wish to see the changes reflected in searching, you should use [ ][DirectoryReader.openIfChanged] to obtain a new reader and then create a new
 * IndexSearcher from that. Also, for low-latency turnaround it's best to use a near-real-time
 * reader ([DirectoryReader.open]). Once you have a new [IndexReader], it's
 * relatively cheap to create a new IndexSearcher from it.
 *
 *
 * **NOTE**: The [.search] and [.searchAfter] methods are configured to only count
 * top hits accurately up to `1,000` and may return a [lower bound][TotalHits.Relation]
 * of the hit count if the hit count is greater than or equal to `1,000`. On queries that
 * match lots of documents, counting the number of hits may take much longer than computing the top
 * hits so this trade-off allows to get some minimal information about the hit count without slowing
 * down search too much. The [TopDocs.scoreDocs] array is always accurate however. If this
 * behavior doesn't suit your needs, you should create collectorManagers manually with either [ ] or [TopFieldCollectorManager] and call [.search].
 *
 *
 * <a id="thread-safety"></a>
 *
 *
 * **NOTE**: `[ ]` instances are completely thread safe, meaning multiple threads can call any
 * of its methods, concurrently. If your application requires external synchronization, you should
 * **not** synchronize on the `IndexSearcher` instance; use your own (non-Lucene)
 * objects instead.
 */
open class IndexSearcher(
    context: IndexReaderContext,
    executor: Executor? = null
) {
    private var queryTimeout: QueryTimeout? = null

    // partialResult may be set on one of the threads of the executor. It may be correct to not make
    // this variable volatile since joining these threads should ensure a happens-before relationship
    // that guarantees that writes become visible on the main thread, but making the variable volatile
    // shouldn't hurt either.
    @Volatile
    private var partialResult = false

    val reader: IndexReader // package private for testing!

    // NOTE: these members might change in incompatible ways
    // in the next release
    protected val readerContext: IndexReaderContext
    val leafContexts: MutableList<LeafReaderContext>

    @Volatile
    private lateinit var leafSlices: Array<LeafSlice>

    // Used internally for load balancing threads executing for the query
    private val taskExecutor: TaskExecutor

    var queryCache: QueryCache? = DEFAULT_QUERY_CACHE
    var queryCachingPolicy: QueryCachingPolicy = DEFAULT_CACHING_POLICY

    /**
     * Expert: returns leaf contexts associated with this searcher. This is an internal method exposed
     * for tests only.
     *
     * @lucene.internal
     */
    /*fun getLeafContexts(): MutableList<LeafReaderContext> {
        return leafContexts
    }*/

    /** The Similarity implementation used by this searcher.  */
    var similarity: Similarity = defaultSimilarity

    /**
     * Runs searches for each segment separately, using the provided Executor. NOTE: if you are using
     * [NIOFSDirectory], do not use the shutdownNow method of ExecutorService as this uses
     * Thread.interrupt under-the-hood which can silently close file descriptors (see [LUCENE-2239](https://issues.apache.org/jira/browse/LUCENE-2239)).
     *
     * @lucene.experimental
     */
    /** Creates a searcher searching the provided index.  */
    constructor(
        r: IndexReader,
        executor: Executor? = null
    ) : this(r.context, executor)

    /**
     * Creates a searcher searching the provided top-level [IndexReaderContext].
     *
     *
     * Given a non-`null` [Executor] this method runs searches for each segment
     * separately, using the provided Executor. NOTE: if you are using [NIOFSDirectory], do not
     * use the shutdownNow method of ExecutorService as this uses Thread.interrupt under-the-hood
     * which can silently close file descriptors (see [LUCENE-2239](https://issues.apache.org/jira/browse/LUCENE-2239)).
     *
     * @see IndexReaderContext
     *
     * @see IndexReader.getContext
     * @lucene.experimental
     */
    /**
     * Creates a searcher searching the provided top-level [IndexReaderContext].
     *
     * @see IndexReaderContext
     *
     * @see IndexReader.getContext
     * @lucene.experimental
     */
    init {
        assert(
            context.isTopLevel
        ) { "IndexSearcher's ReaderContext must be topLevel for reader " + context.reader() }
        reader = context.reader()

        this.taskExecutor = if(executor == null){
            TaskExecutor(Runnable::run)
        }else{
            TaskExecutor(executor)
        }

        this.readerContext = context
        leafContexts = context.leaves()
        if (executor == null) {
            leafSlices =
                if (leafContexts.isEmpty())
                    kotlin.arrayOfNulls<LeafSlice>(0) as Array<LeafSlice>
                else
                    arrayOf<LeafSlice>(
                        LeafSlice(
                            ArrayList<LeafReaderContextPartition>(
                                leafContexts
                                    .map { ctx: LeafReaderContext ->
                                        LeafReaderContextPartition.createForEntireSegment(
                                            ctx
                                        )
                                    }
                                    .toList()))
                    )
        }
    }

    /**
     * Set the [QueryCache] to use when scores are not needed. A value of `null` indicates
     * that query matches should never be cached. This method should be called **before** starting
     * using this [IndexSearcher].
     *
     *
     * NOTE: When using a query cache, queries should not be modified after they have been passed
     * to IndexSearcher.
     *
     * @see QueryCache
     *
     * @lucene.experimental
     */
    /*fun setQueryCache(queryCache: QueryCache) {
        this.queryCache = queryCache
    }*/

    /**
     * Return the query cache of this [IndexSearcher]. This will be either the [ ][.getDefaultQueryCache] or the query cache that was last set through
     * [.setQueryCache]. A return value of `null` indicates that caching is
     * disabled.
     *
     * @lucene.experimental
     */
    /*fun getQueryCache(): QueryCache {
        return queryCache
    }*/

    /**
     * Set the [QueryCachingPolicy] to use for query caching. This method should be called
     * **before** starting using this [IndexSearcher].
     *
     * @see QueryCachingPolicy
     *
     * @lucene.experimental
     */
    /*fun setQueryCachingPolicy(queryCachingPolicy: QueryCachingPolicy) {
        this.queryCachingPolicy = queryCachingPolicy
    }*/

    /**
     * Return the query cache of this [IndexSearcher]. This will be either the [ ][.getDefaultQueryCachingPolicy] or the policy that was last set through [ ][.setQueryCachingPolicy].
     *
     * @lucene.experimental
     */
    /*fun getQueryCachingPolicy(): QueryCachingPolicy {
        return queryCachingPolicy
    }*/

    /**
     * Expert: Creates an array of leaf slices each holding a subset of the given leaves. Each [ ] is executed in a single thread. By default, segments with more than
     * MAX_DOCS_PER_SLICE will get their own thread.
     *
     *
     * It is possible to leverage intra-segment concurrency by splitting segments into multiple
     * partitions. Such behaviour is not enabled by default as there is still a performance penalty
     * for queries that require segment-level computation ahead of time, such as points/range queries.
     * This is an implementation limitation that we expect to improve in future releases, see [the corresponding github issue](https://github.com/apache/lucene/issues/13745).
     */
    protected fun slices(leaves: MutableList<LeafReaderContext>): Array<LeafSlice> {
        return slices(leaves, MAX_DOCS_PER_SLICE, MAX_SEGMENTS_PER_SLICE, false)
    }

    val indexReader: IndexReader
        /** Return the [IndexReader] this searches.  */
        get() = reader

    /**
     * Returns a [StoredFields] reader for the stored fields of this index.
     *
     *
     * Sugar for `.getIndexReader().storedFields()`
     *
     *
     * This call never returns `null`, even if no stored fields were indexed. The returned
     * instance should only be used by a single thread.
     *
     *
     * Example:
     *
     * <pre class="prettyprint">
     * TopDocs hits = searcher.search(query, 10);
     * StoredFields storedFields = searcher.storedFields();
     * for (ScoreDoc hit : hits.scoreDocs) {
     * Document doc = storedFields.document(hit.doc);
     * }
    </pre> *
     *
     * @throws IOException If there is a low-level IO error
     * @see IndexReader.storedFields
     */
    @Throws(IOException::class)
    fun storedFields(): StoredFields {
        return reader.storedFields()
    }

    /** Expert: Set the Similarity implementation used by this IndexSearcher.  */
    /*fun setSimilarity(similarity: Similarity) {
        this.similarity = similarity
    }*/

    /**
     * Expert: Get the [Similarity] to use to compute scores. This returns the [ ] that has been set through [.setSimilarity] or the default [ ] if none has been set explicitly.
     */
    /*fun getSimilarity(): Similarity {
        return similarity
    }*/

    /**
     * Count how many documents match the given query. May be faster than counting number of hits by
     * collecting all matches, as the number of hits is retrieved from the index statistics when
     * possible.
     */
    @Throws(IOException::class)
    fun count(query: Query): Int {
        // Rewrite query before optimization check
        var query: Query = query
        query = rewrite(ConstantScoreQuery(query))
        if (query is ConstantScoreQuery) {
            query = query.query
        }

        // Check if two clause disjunction optimization applies
        if (query is BooleanQuery && !this.reader.hasDeletions() && query.isTwoClausePureDisjunctionWithTerms
        ) {
            val queries: Array<Query> =
                query.rewriteTwoClauseDisjunctionWithTermsForCount(this)
            val countTerm1 = count(queries[0])
            val countTerm2 = count(queries[1])
            if (countTerm1 == 0 || countTerm2 == 0) {
                return max(countTerm1, countTerm2)
                // Only apply optimization if the intersection is significantly smaller than the union
            } else if (min(countTerm1, countTerm2).toDouble() / max(countTerm1, countTerm2)
                < 0.1
            ) {
                return countTerm1 + countTerm2 - count(queries[2])
            }
        }
        return search(
            ConstantScoreQuery(query), TotalHitCountCollectorManager(
                this.slices
            )
        )
    }

    val slices: Array<LeafSlice>
        /**
         * Returns the leaf slices used for concurrent searching. Override [.slices] to
         * customize how slices are created.
         *
         * @lucene.experimental
         */
        get() {
            var res = leafSlices
            if (res == null) {
                res = computeAndCacheSlices()
            }
            return res
        }

    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    private fun computeAndCacheSlices(): Array<LeafSlice> {
        var res = leafSlices
        if (res == null) {
            res = slices(leafContexts)
            /*
       * Enforce that there aren't multiple leaf partitions within the same leaf slice pointing to the
       * same leaf context. It is a requirement that {@link Collector#getLeafCollector(LeafReaderContext)}
       * gets called once per leaf context. Also, it does not make sense to partition a segment to then search
       * those partitions as part of the same slice, because the goal of partitioning is parallel searching
       * which happens at the slice level.
       */
            for (leafSlice in res) {
                if (leafSlice.partitions.size <= 1) {
                    continue
                }
                enforceDistinctLeaves(leafSlice)
            }
            leafSlices = res
        }
        return res
    }

    /**
     * Finds the top `n` hits for `query` where all results are after a previous
     * result (`after`).
     *
     *
     * By passing the bottom result from a previous page as `after`, this method can be
     * used for efficient 'deep-paging' across potentially large result sets.
     *
     * @throws TooManyClauses If a query would exceed [IndexSearcher.getMaxClauseCount]
     * clauses.
     */
    @Throws(IOException::class)
    fun searchAfter(
        after: ScoreDoc?,
        query: Query,
        numHits: Int
    ): TopDocs {
        val limit = max(1, reader.maxDoc())
        require(!(after != null && after.doc >= limit)) {
            ("after.doc exceeds the number of documents in the reader: after.doc="
                    + after!!.doc
                    + " limit="
                    + limit)
        }

        val cappedNumHits = min(numHits, limit)
        val manager: CollectorManager<TopScoreDocCollector, TopDocs> =
            TopScoreDocCollectorManager(cappedNumHits, after, TOTAL_HITS_THRESHOLD)

        return search(query, manager)
    }

    var timeout: QueryTimeout?
        /**
         * Get the configured [QueryTimeout] for all searches that run through this [ ], or `null` if not set.
         */
        get() = this.queryTimeout

        /** Set a [QueryTimeout] for all searches that run through this [IndexSearcher].  */
        set(queryTimeout) {
            this.queryTimeout = queryTimeout
        }

    /**
     * Finds the top `n` hits for `query`.
     *
     * @throws TooManyClauses If a query would exceed [IndexSearcher.getMaxClauseCount]
     * clauses.
     */
    @Throws(IOException::class)
    fun search(query: Query, n: Int): TopDocs {
        return searchAfter(null, query, n)
    }

    /**
     * Lower-level search API.
     *
     *
     * [LeafCollector.collect] is called for every matching document.
     *
     * @throws TooManyClauses If a query would exceed [IndexSearcher.getMaxClauseCount]
     * clauses.
     */
    @Deprecated(
        """This method is being deprecated in favor of {@link IndexSearcher#search(Query,
   *     CollectorManager)} due to its support for concurrency in IndexSearcher"""
    )
    @Throws(IOException::class)
    fun search(query: Query, collector: Collector) {
        var query: Query = query
        query = rewrite(query, collector.scoreMode().needsScores())
        val weight: Weight = createWeight(query, collector.scoreMode(), 1f)
        collector.weight = weight
        for (ctx in leafContexts) { // search each subreader
            searchLeaf(ctx, 0, DocIdSetIterator.NO_MORE_DOCS, weight, collector)
        }
    }

    /** Returns true if any search hit the [timeout][.setTimeout].  */
    fun timedOut(): Boolean {
        return partialResult
    }

    /**
     * Search implementation with arbitrary sorting, plus control over whether hit scores and max
     * score should be computed. Finds the top `n` hits for `query`, and sorting
     * the hits by the criteria in `sort`. If `doDocScores` is `true`
     * then the score of each hit will be computed and returned. If `doMaxScore` is `
     * true` then the maximum score over all collected hits will be computed.
     *
     * @throws TooManyClauses If a query would exceed [IndexSearcher.getMaxClauseCount]
     * clauses.
     */
    @Throws(IOException::class)
    fun search(
        query: Query,
        n: Int,
        sort: Sort,
        doDocScores: Boolean
    ): TopFieldDocs {
        return searchAfter(null, query, n, sort, doDocScores)
    }

    /**
     * Search implementation with arbitrary sorting.
     *
     * @param query The query to search for
     * @param n Return only the top n results
     * @param sort The [Sort] object
     * @return The top docs, sorted according to the supplied [Sort]
     * instance
     * @throws IOException if there is a low-level I/O error
     */
    @Throws(IOException::class)
    fun search(
        query: Query,
        n: Int,
        sort: Sort
    ): TopFieldDocs {
        return searchAfter(null, query, n, sort, false)
    }

    /**
     * Finds the top `n` hits for `query` where all results are after a previous
     * result (`after`).
     *
     *
     * By passing the bottom result from a previous page as `after`, this method can be
     * used for efficient 'deep-paging' across potentially large result sets.
     *
     * @throws TooManyClauses If a query would exceed [IndexSearcher.getMaxClauseCount]
     * clauses.
     */
    @Throws(IOException::class)
    fun searchAfter(
        after: ScoreDoc,
        query: Query,
        n: Int,
        sort: Sort
    ): TopDocs {
        return searchAfter(after, query, n, sort, false)
    }

    /**
     * Finds the top `n` hits for `query` where all results are after a previous
     * result (`after`), allowing control over whether hit scores and max score should be
     * computed.
     *
     *
     * By passing the bottom result from a previous page as `after`, this method can be
     * used for efficient 'deep-paging' across potentially large result sets. If `doDocScores
    ` *  is `true` then the score of each hit will be computed and returned. If
     * `doMaxScore` is `true` then the maximum score over all collected hits
     * will be computed.
     *
     * @throws TooManyClauses If a query would exceed [IndexSearcher.getMaxClauseCount]
     * clauses.
     */
    @Throws(IOException::class)
    fun searchAfter(
        after: ScoreDoc?,
        query: Query,
        numHits: Int,
        sort: Sort,
        doDocScores: Boolean
    ): TopFieldDocs {
        require(!(after != null && after !is FieldDoc)) { "after must be a FieldDoc; got $after" }
        return searchAfter(after as? FieldDoc, query, numHits, sort, doDocScores)
    }

    @Throws(IOException::class)
    private fun searchAfter(
        after: FieldDoc?,
        query: Query,
        numHits: Int,
        sort: Sort,
        doDocScores: Boolean
    ): TopFieldDocs {
        val limit = max(1, reader.maxDoc())
        require(!(after != null && after.doc >= limit)) {
            ("after.doc exceeds the number of documents in the reader: after.doc="
                    + after!!.doc
                    + " limit="
                    + limit)
        }
        val cappedNumHits = min(numHits, limit)
        val rewrittenSort: Sort = sort.rewrite(this)

        val manager: CollectorManager<TopFieldCollector, TopFieldDocs> =
            TopFieldCollectorManager(rewrittenSort, cappedNumHits, after, TOTAL_HITS_THRESHOLD)

        val topDocs: TopFieldDocs = search(query, manager)
        if (doDocScores) {
            TopFieldCollector.populateScores(topDocs.scoreDocs, this, query)
        }
        return topDocs
    }

    /**
     * Lower-level search API. Search all leaves using the given [CollectorManager]. In contrast
     * to [.search], this method will use the searcher's [Executor] in
     * order to parallelize execution of the collection on the configured [.getSlices].
     *
     * @see CollectorManager
     *
     * @lucene.experimental
     */
    @Throws(IOException::class)
    fun <C : Collector, T> search(
        query: Query,
        collectorManager: CollectorManager<C, T>
    ): T {
        var query: Query = query
        val firstCollector: C = collectorManager.newCollector()
        query = rewrite(query, firstCollector.scoreMode().needsScores())
        val weight: Weight = createWeight(query, firstCollector.scoreMode(), 1f)
        return search(weight, collectorManager, firstCollector)
    }

    @Throws(IOException::class)
    private fun <C : Collector, T> search(
        weight: Weight,
        collectorManager: CollectorManager<C, T>,
        firstCollector: C
    ): T {
        val leafSlices = this.slices
        if (leafSlices.isEmpty()) {
            // there are no segments, nothing to offload to the executor, but we do need to call reduce to
            // create some kind of empty result
            assert(leafContexts.isEmpty())
            return collectorManager.reduce(mutableListOf(firstCollector))
        } else {
            val collectors: MutableList<C> = ArrayList(leafSlices.size)
            collectors.add(firstCollector)
            val scoreMode: ScoreMode = firstCollector.scoreMode()
            for (i in 1..<leafSlices.size) {
                val collector: C = collectorManager.newCollector()
                collectors.add(collector)
                check(scoreMode == collector.scoreMode()) { "CollectorManager does not always produce collectors with the same score mode" }
            }
            val listTasks: MutableList<Callable<C>> = ArrayList(leafSlices.size)
            for (i in leafSlices.indices) {
                val leaves = leafSlices[i].partitions
                val collector = collectors[i]
                listTasks.add(
                    Callable {
                        search(leaves, weight, collector)
                        collector
                    })
            }
            val results: MutableList<C> = runBlocking { taskExecutor.invokeAll(listTasks) }
            return collectorManager.reduce(results)
        }
    }

    /**
     * Lower-level search API.
     *
     *
     * [.searchLeaf] is called for every leaf
     * partition. <br></br>
     *
     *
     * NOTE: this method executes the searches on all given leaf partitions exclusively. To search
     * across all the searchers leaves use [.leafContexts].
     *
     * @param partitions the leaf partitions to execute the searches on
     * @param weight to match documents
     * @param collector to receive hits
     * @throws TooManyClauses If a query would exceed [IndexSearcher.getMaxClauseCount]
     * clauses.
     */
    @Throws(IOException::class)
    protected fun search(
        partitions: Array<LeafReaderContextPartition>,
        weight: Weight,
        collector: Collector
    ) {
        collector.weight = weight

        for (partition in partitions) { // search each subreader partition
            searchLeaf(partition.ctx, partition.minDocId, partition.maxDocId, weight, collector)
        }
    }

    /**
     * Lower-level search API
     *
     *
     * [LeafCollector.collect] is called for every document. <br></br>
     *
     * @param ctx the leaf to execute the search against
     * @param minDocId the lower bound of the doc id range to search
     * @param maxDocId the upper bound of the doc id range to search
     * @param weight to match document
     * @param collector to receive hits
     * @throws TooManyClauses If a query would exceed [IndexSearcher.getMaxClauseCount]
     * clauses.
     */
    @Throws(IOException::class)
    protected fun searchLeaf(
        ctx: LeafReaderContext,
        minDocId: Int,
        maxDocId: Int,
        weight: Weight,
        collector: Collector
    ) {
        val leafCollector: LeafCollector
        try {
            leafCollector = collector.getLeafCollector(ctx)
        } catch (e: CollectionTerminatedException) {
            // there is no doc of interest in this reader context
            // continue with the following leaf
            return
        }
        val scorerSupplier: ScorerSupplier? = weight.scorerSupplier(ctx)
        if (scorerSupplier != null) {
            scorerSupplier.setTopLevelScoringClause()
            var scorer: BulkScorer = scorerSupplier.bulkScorer()!!
            if (queryTimeout != null) {
                scorer = TimeLimitingBulkScorer(scorer, queryTimeout!!)
            }
            try {
                // Optimize for the case when live docs are stored in a FixedBitSet.
                val acceptDocs: Bits? =
                    ScorerUtil.likelyLiveDocs(ctx.reader().liveDocs)
                scorer.score(leafCollector, acceptDocs, minDocId, maxDocId)
            } catch (e: CollectionTerminatedException) {
                // collection was terminated prematurely
                // continue with the following leaf
            } catch (e: TimeLimitingBulkScorer.TimeExceededException) {
                partialResult = true
            }
        }
        // Note: this is called if collection ran successfully, including the above special cases of
        // CollectionTerminatedException and TimeExceededException, but no other exception.
        leafCollector.finish()
    }

    /**
     * Expert: called to re-write queries into primitive queries.
     *
     * @throws TooManyClauses If a query would exceed [IndexSearcher.getMaxClauseCount]
     * clauses.
     */
    @Throws(IOException::class)
    fun rewrite(original: Query): Query {
        var query: Query = original
        var rewrittenQuery: Query = query.rewrite(this)
        while (rewrittenQuery !== query
        ) {
            query = rewrittenQuery
            rewrittenQuery = query.rewrite(this)
        }
        query.visit(numClausesCheckVisitor)
        return query
    }

    @Throws(IOException::class)
    private fun rewrite(
        original: Query,
        needsScores: Boolean
    ): Query {
        return if (needsScores) {
            rewrite(original)
        } else {
            // Take advantage of the few extra rewrite rules of ConstantScoreQuery.
            rewrite(ConstantScoreQuery(original))
        }
    }

    /**
     * Returns an Explanation that describes how `doc` scored against `query`.
     *
     *
     * This is intended to be used in developing Similarity implementations, and, for good
     * performance, should not be displayed with every hit. Computing an explanation is as expensive
     * as executing the query over the entire index.
     */
    @Throws(IOException::class)
    fun explain(query: Query, doc: Int): Explanation {
        var query: Query = query
        query = rewrite(query)
        return explain(createWeight(query, ScoreMode.COMPLETE, 1f), doc)
    }

    /**
     * Expert: low-level implementation method Returns an Explanation that describes how `doc
    ` *  scored against `weight`.
     *
     *
     * This is intended to be used in developing Similarity implementations, and, for good
     * performance, should not be displayed with every hit. Computing an explanation is as expensive
     * as executing the query over the entire index.
     *
     *
     * Applications should call [IndexSearcher.explain].
     *
     * @throws TooManyClauses If a query would exceed [IndexSearcher.getMaxClauseCount]
     * clauses.
     */
    @Throws(IOException::class)
    protected fun explain(weight: Weight, doc: Int): Explanation {
        val n: Int = ReaderUtil.subIndex(doc, leafContexts)
        val ctx: LeafReaderContext = leafContexts[n]
        val deBasedDoc: Int = doc - ctx.docBase
        val liveDocs: Bits? = ctx.reader().liveDocs
        if (liveDocs != null && !liveDocs.get(deBasedDoc)) {
            return Explanation.noMatch("Document $doc is deleted")
        }
        return weight.explain(ctx, deBasedDoc)
    }

    /**
     * Creates a [Weight] for the given query, potentially adding caching if possible and
     * configured.
     *
     * @lucene.experimental
     */
    @Throws(IOException::class)
    fun createWeight(
        query: Query,
        scoreMode: ScoreMode,
        boost: Float
    ): Weight {
        val queryCache: QueryCache? = this.queryCache
        var weight: Weight = query.createWeight(this, scoreMode, boost)
        if (!scoreMode.needsScores() && queryCache != null) {
            weight = queryCache.doCache(weight, queryCachingPolicy)
        }
        return weight
    }

    val topReaderContext: IndexReaderContext
        /**
         * Returns this searcher's top-level [IndexReaderContext].
         *
         * @see IndexReader.getContext
         */
        get() = readerContext

    /**
     * A class holding a subset of the [IndexSearcher]s leaf contexts to be executed within a
     * single thread. A leaf slice holds references to one or more [LeafReaderContextPartition]
     * instances. Each partition targets a specific doc id range of a [LeafReaderContext].
     *
     * @lucene.experimental
     */
    class LeafSlice(leafReaderContextPartitions: MutableList<LeafReaderContextPartition>) {
        /**
         * The leaves that make up this slice.
         *
         * @lucene.experimental
         */
        val partitions: Array<LeafReaderContextPartition>

        /**
         * Returns the total number of docs that a slice targets, by summing the number of docs that
         * each of its leaf context partitions targets.
         */
        val maxDocs: Int

        init {
            leafReaderContextPartitions.sortWith(
                compareBy<LeafReaderContextPartition> { it.ctx.docBase }.thenBy { it.minDocId }
            )
            this.partitions = leafReaderContextPartitions.toTypedArray()
            this.maxDocs = partitions.sumOf { it.maxDocs }
        }
    }

    /**
     * Holds information about a specific leaf context and the corresponding range of doc ids to
     * search within. Used to optionally search across partitions of the same segment concurrently.
     *
     *
     * A partition instance can be created via [.createForEntireSegment],
     * in which case it will target the entire provided [LeafReaderContext]. A true partition of
     * a segment can be created via [.createFromAndTo] providing
     * the minimum doc id (including) to search as well as the max doc id (excluding).
     *
     * @lucene.experimental
     */
    class LeafReaderContextPartition private constructor(
        leafReaderContext: LeafReaderContext,
        minDocId: Int,
        maxDocId: Int,
        maxDocs: Int
    ) {
        val minDocId: Int
        val maxDocId: Int
        val ctx: LeafReaderContext

        // we keep track of maxDocs separately because we use NO_MORE_DOCS as upper bound when targeting
        // the entire segment. We use this only in tests.
        val maxDocs: Int

        init {
            require(minDocId < maxDocId) {
                ("minDocId is greater than or equal to maxDocId: ["
                        + minDocId
                        + "] > ["
                        + maxDocId
                        + "]")
            }
            require(minDocId >= 0) { "minDocId is lower than 0: [$minDocId]" }
            require(minDocId < leafReaderContext.reader().maxDoc()) {
                ("minDocId is greater than than maxDoc: ["
                        + minDocId
                        + "] > ["
                        + leafReaderContext.reader().maxDoc()
                        + "]")
            }

            this.ctx = leafReaderContext
            this.minDocId = minDocId
            this.maxDocId = maxDocId
            this.maxDocs = maxDocs
        }

        companion object {
            /** Creates a partition of the provided leaf context that targets the entire segment  */
            fun createForEntireSegment(ctx: LeafReaderContext): LeafReaderContextPartition {
                return LeafReaderContextPartition(
                    ctx, 0, DocIdSetIterator.NO_MORE_DOCS, ctx.reader().maxDoc()
                )
            }

            /**
             * Creates a partition of the provided leaf context that targets a subset of the entire segment,
             * starting from and including the min doc id provided, until and not including the provided max
             * doc id
             */
            fun createFromAndTo(
                ctx: LeafReaderContext, minDocId: Int, maxDocId: Int
            ): LeafReaderContextPartition {
                assert(maxDocId != DocIdSetIterator.NO_MORE_DOCS)
                return LeafReaderContextPartition(ctx, minDocId, maxDocId, maxDocId - minDocId)
            }
        }
    }

    override fun toString(): String {
        return "IndexSearcher($reader; taskExecutor=$taskExecutor)"
    }

    /**
     * Returns [TermStatistics] for a term.
     *
     *
     * This can be overridden for example, to return a term's statistics across a distributed
     * collection.
     *
     * @param docFreq The document frequency of the term. It must be greater or equal to 1.
     * @param totalTermFreq The total term frequency.
     * @return A [TermStatistics] (never null).
     * @lucene.experimental
     */
    @Throws(IOException::class)
    fun termStatistics(
        term: Term,
        docFreq: Int,
        totalTermFreq: Long
    ): TermStatistics {
        // This constructor will throw an exception if docFreq <= 0.
        return TermStatistics(term.bytes(), docFreq.toLong(), totalTermFreq)
    }

    /**
     * Returns [CollectionStatistics] for a field, or `null` if the field does not exist
     * (has no indexed terms)
     *
     *
     * This can be overridden for example, to return a field's statistics across a distributed
     * collection.
     *
     * @lucene.experimental
     */
    @Throws(IOException::class)
    fun collectionStatistics(field: String): CollectionStatistics? {
        checkNotNull(field)
        var docCount: Long = 0
        var sumTotalTermFreq: Long = 0
        var sumDocFreq: Long = 0
        for (leaf in reader.leaves()) {
            val terms: Terms = Terms.getTerms(leaf.reader(), field)
            docCount += terms.docCount.toLong()
            sumTotalTermFreq += terms.sumTotalTermFreq
            sumDocFreq += terms.sumDocFreq
        }
        if (docCount == 0L) {
            return null
        }
        return CollectionStatistics(
            field,
            reader.maxDoc().toLong(),
            docCount,
            sumTotalTermFreq,
            sumDocFreq
        )
    }

    /**
     * Returns the [TaskExecutor] that this searcher relies on to execute concurrent operations
     *
     * @return the task executor
     */
    fun getTaskExecutor(): TaskExecutor {
        return taskExecutor
    }

    /**
     * Thrown when an attempt is made to add more than [.getMaxClauseCount] clauses. This
     * typically happens if a PrefixQuery, FuzzyQuery, WildcardQuery, or TermRangeQuery is expanded to
     * many terms during search.
     */
    open class TooManyClauses(msg: String = "maxClauseCount is set to " + IndexSearcher.maxClauseCount) :
        RuntimeException(msg) {
        /** The value of [IndexSearcher.getMaxClauseCount] when this Exception was created  */
        val maxClauseCount: Int = IndexSearcher.maxClauseCount
    }

    /**
     * Thrown when a client attempts to execute a Query that has more than [ ][.getMaxClauseCount] total clauses cumulatively in all of its children.
     *
     * @see .rewrite
     */
    class TooManyNestedClauses : TooManyClauses(
        "Query contains too many nested clauses; maxClauseCount is set to "
                + maxClauseCount
    )

    companion object {
        var maxClauseCount: Int = 1024
            /**
             * Return the maximum number of clauses permitted, 1024 by default. Attempts to add more than the
             * permitted number of clauses cause [TooManyClauses] to be thrown.
             *
             * @see .setMaxClauseCount
             */
            fun get(): Int {
                return maxClauseCount
            }

            /** Set the maximum number of clauses permitted per Query. Default value is 1024.  */
            fun set(value: Int) {
                require(value >= 1) { "maxClauseCount must be >= 1" }
                maxClauseCount = value
            }

        private var DEFAULT_QUERY_CACHE: QueryCache

        private var DEFAULT_CACHING_POLICY: QueryCachingPolicy =
            UsageTrackingQueryCachingPolicy()

        init {
            val maxCachedQueries = 1000
            // min of 32MB or 5% of the heap size
            val maxRamBytesUsed = 4L * 1024 * 1024 * 1024
            // TODO hard coding 4 GB placeholder for now, because most smartphones including iOS and Android have at least 4 GB of RAM we will later implement platform specific memory recognition code
            // /*min(1L shl 25, java.lang.Runtime.getRuntime().maxMemory() / 20)*/

            DEFAULT_QUERY_CACHE = LRUQueryCache(maxCachedQueries, maxRamBytesUsed)
        }

        /**
         * By default, we count hits accurately up to 1000. This makes sure that we don't spend most time
         * on computing hit counts
         */
        private const val TOTAL_HITS_THRESHOLD = 1000

        /**
         * Thresholds for index slice allocation logic. To change the default, extend ` IndexSearcher
        ` *  and use custom values
         */
        private const val MAX_DOCS_PER_SLICE = 250000

        private const val MAX_SEGMENTS_PER_SLICE = 5

        // the default Similarity
        val defaultSimilarity: Similarity = BM25Similarity()

        /**
         * Expert: returns a default Similarity instance. In general, this method is only called to
         * initialize searchers and writers. User code and query implementations should respect [ ][IndexSearcher.getSimilarity].
         *
         * @lucene.internal
         */
        /*fun getDefaultSimilarity(): Similarity {
            return defaultSimilarity
        }
*/
        val defaultQueryCache: QueryCache
            /**
             * Expert: Get the default [QueryCache] or `null` if the cache is disabled.
             *
             * @lucene.internal
             */
            get() = DEFAULT_QUERY_CACHE

        /**
         * Expert: set the default [QueryCache] instance.
         *
         * @lucene.internal
         */
        fun setDefaultQueryCache(defaultQueryCache: QueryCache) {
            DEFAULT_QUERY_CACHE = defaultQueryCache
        }

        val defaultQueryCachingPolicy: QueryCachingPolicy
            /**
             * Expert: Get the default [QueryCachingPolicy].
             *
             * @lucene.internal
             */
            get() = DEFAULT_CACHING_POLICY

        /**
         * Expert: set the default [QueryCachingPolicy] instance.
         *
         * @lucene.internal
         */
        fun setDefaultQueryCachingPolicy(defaultQueryCachingPolicy: QueryCachingPolicy) {
            DEFAULT_CACHING_POLICY = defaultQueryCachingPolicy
        }

        /**
         * Static method to segregate LeafReaderContexts amongst multiple slices. Creates slices according
         * to the provided max number of documents per slice and max number of segments per slice. Splits
         * segments into partitions when the last argument is true.
         *
         * @param leaves the leaves to slice
         * @param maxDocsPerSlice the maximum number of documents in a single slice
         * @param maxSegmentsPerSlice the maximum number of segments in a single slice
         * @param allowSegmentPartitions whether segments may be split into partitions according to the
         * provided maxDocsPerSlice argument. When `true`, if a segment holds more
         * documents than the provided max docs per slice, it is split into equal size partitions that
         * each gets its own slice assigned.
         * @return the array of slices
         */
        fun slices(
            leaves: MutableList<LeafReaderContext>,
            maxDocsPerSlice: Int,
            maxSegmentsPerSlice: Int,
            allowSegmentPartitions: Boolean
        ): Array<LeafSlice> {
            // Make a copy so we can sort:

            val sortedLeaves: MutableList<LeafReaderContext> = ArrayList(leaves)

            // Sort by maxDoc, descending:
            sortedLeaves.sortByDescending { it.reader().maxDoc() }

            if (allowSegmentPartitions) {
                val groupedLeafPartitions: MutableList<MutableList<LeafReaderContextPartition>> = mutableListOf()
                var currentSliceNumDocs = 0
                var group: MutableList<LeafReaderContextPartition>? = null
                for (ctx in sortedLeaves) {
                    if (ctx.reader().maxDoc() > maxDocsPerSlice) {
                        assert(group == null)
                        // if the segment does not fit in a single slice, we split it into maximum 5 partitions of
                        // equal size
                        val numSlices = min(5, Math.ceilDiv(ctx.reader().maxDoc(), maxDocsPerSlice))
                        val numDocs: Int = ctx.reader().maxDoc() / numSlices
                        var maxDocId = numDocs
                        var minDocId = 0
                        for (i in 0..<numSlices - 1) {
                            groupedLeafPartitions.add(
                                mutableListOf(
                                    LeafReaderContextPartition.createFromAndTo(ctx, minDocId, maxDocId)
                                )
                            )
                            minDocId = maxDocId
                            maxDocId += numDocs
                        }
                        // the last slice gets all the remaining docs
                        groupedLeafPartitions.add(
                            mutableListOf(
                                LeafReaderContextPartition.createFromAndTo(
                                    ctx, minDocId, ctx.reader().maxDoc()
                                )
                            )
                        )
                    } else {
                        if (group == null) {
                            group = mutableListOf()
                            groupedLeafPartitions.add(group)
                        }
                        group.add(LeafReaderContextPartition.createForEntireSegment(ctx))

                        currentSliceNumDocs += ctx.reader().maxDoc()
                        // We only split a segment when it does not fit entirely in a slice. We don't partition
                        // the
                        // segment that makes the current slice (which holds multiple segments) go over
                        // maxDocsPerSlice. This means that a slice either contains multiple entire segments, or a
                        // single partition of a segment.
                        if (group.size >= maxSegmentsPerSlice || currentSliceNumDocs > maxDocsPerSlice) {
                            group = null
                            currentSliceNumDocs = 0
                        }
                    }
                }

                val slices: Array<LeafSlice> = kotlin.arrayOfNulls<LeafSlice>(groupedLeafPartitions.size) as Array<LeafSlice>
                var upto = 0
                for (currentGroup in groupedLeafPartitions) {
                    slices[upto] = LeafSlice(currentGroup)
                    ++upto
                }
                return slices
            }

            val groupedLeaves: MutableList<MutableList<LeafReaderContext>> = mutableListOf()
            var docSum: Long = 0
            var group: MutableList<LeafReaderContext>? = null
            for (ctx in sortedLeaves) {
                if (ctx.reader().maxDoc() > maxDocsPerSlice) {
                    assert(group == null)
                    groupedLeaves.add(mutableListOf(ctx))
                } else {
                    if (group == null) {
                        group = mutableListOf()
                        group.add(ctx)

                        groupedLeaves.add(group)
                    } else {
                        group.add(ctx)
                    }

                    docSum += ctx.reader().maxDoc().toLong()
                    if (group.size >= maxSegmentsPerSlice || docSum > maxDocsPerSlice) {
                        group = null
                        docSum = 0
                    }
                }
            }

            val slices: Array<LeafSlice> = kotlin.arrayOfNulls<LeafSlice>(groupedLeaves.size) as Array<LeafSlice>
            var upto = 0
            for (currentLeaf in groupedLeaves) {
                slices[upto] =
                    LeafSlice(
                        ArrayList(
                            currentLeaf
                                .map { ctx: LeafReaderContext ->
                                    LeafReaderContextPartition.createForEntireSegment(
                                        ctx
                                    )
                                }
                                .toList()))
                ++upto
            }

            return slices
        }

        private fun enforceDistinctLeaves(leafSlice: LeafSlice) {
            val distinctLeaves: MutableSet<LeafReaderContext> = HashSet()
            for (leafPartition in leafSlice.partitions) {
                check(distinctLeaves.add(leafPartition.ctx)) { "The same slice targets multiple leaf partitions of the same leaf reader context. A physical segment should rather get partitioned to be searched concurrently from as many slices as the number of leaf partitions it is split into." }
            }
        }

        private val numClausesCheckVisitor: QueryVisitor
            /**
             * Returns a QueryVisitor which recursively checks the total number of clauses that a query and
             * its children cumulatively have and validates that the total number does not exceed the
             * specified limit. Throws [TooManyNestedClauses] if the limit is exceeded.
             */
            get() = object : QueryVisitor() {
                var numClauses: Int = 0

                override fun getSubVisitor(
                    occur: BooleanClause.Occur,
                    parent: Query
                ): QueryVisitor {
                    // Return this instance even for MUST_NOT and not an empty QueryVisitor
                    return this
                }

                override fun visitLeaf(query: Query?) {
                    if (numClauses > maxClauseCount) {
                        throw TooManyNestedClauses()
                    }
                    ++numClauses
                }

                override fun consumeTerms(
                    query: Query,
                    vararg terms: Term
                ) {
                    if (numClauses > maxClauseCount) {
                        throw TooManyNestedClauses()
                    }
                    ++numClauses
                }

                override fun consumeTermsMatching(
                    query: Query,
                    field: String,
                    automaton: () -> ByteRunAutomaton
                ) {
                    if (numClauses > maxClauseCount) {
                        throw TooManyNestedClauses()
                    }
                    ++numClauses
                }
            }
    }
}
