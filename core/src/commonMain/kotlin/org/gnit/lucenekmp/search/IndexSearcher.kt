package org.gnit.lucenekmp.search

import kotlinx.io.IOException
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexReaderContext
import org.gnit.lucenekmp.index.QueryTimeout
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.search.similarities.Similarity
import kotlin.jvm.JvmOverloads

class IndexSearcher {

    //TODO all things below are fake to compile pass, just copied and commented out partially from java lucene, need to be re-written

    val reader: IndexReader? = null // package private for testing!

    // NOTE: these members might change in incompatible ways
    // in the next release
    protected val readerContext: IndexReaderContext? = null

    private var queryTimeout: QueryTimeout? = null

    // Used internally for load balancing threads executing for the query
    private val taskExecutor: TaskExecutor? = null

    private var similarity: Similarity = defaultSimilarity


    /**
     * Returns this searcher's top-level [IndexReaderContext].
     *
     * @see IndexReader.getContext
     */
    /* sugar for #getReader().getTopReaderContext() */
    fun getTopReaderContext(): IndexReaderContext {
        return readerContext!!
    }

    /** Return the [IndexReader] this searches.  */
    fun getIndexReader(): IndexReader {
        return reader!!
    }

    /**
     * Expert: called to re-write queries into primitive queries.
     *
     * @throws TooManyClauses If a query would exceed [IndexSearcher.getMaxClauseCount]
     * clauses.
     */
    @Throws(IOException::class)
    fun rewrite(original: Query): Query {
        var query = original
        var rewrittenQuery = query.rewrite(this)
        while (rewrittenQuery !== query
        ) {
            query = rewrittenQuery
            rewrittenQuery = query.rewrite(this)
        }
    //    query.visit(IndexSearcher.getNumClausesCheckVisitor()) TODO
        return query
    }

    /**
     *
     *
     * Creates a [Weight] for the given query, potentially adding caching if possible and
     * configured.
     *
     * @lucene.experimental
     */
    @Throws(IOException::class)
    fun createWeight(query: Query, scoreMode: ScoreMode, boost: Float): Weight {
        //val queryCache: QueryCache? = this.queryCache
        var weight = query.createWeight(this, scoreMode, boost)
        /*if (scoreMode.needsScores() === false && queryCache != null) {
            weight = queryCache.doCache(weight, queryCachingPolicy)
        }*/
        return weight
    }

    /**
     * Get the configured [QueryTimeout] for all searches that run through this [ ], or `null` if not set.
     */
    fun getTimeout(): QueryTimeout? {
        return this.queryTimeout
    }


    /**
     * Returns the [TaskExecutor] that this searcher relies on to execute concurrent operations
     *
     * @return the task executor
     */
    fun getTaskExecutor(): TaskExecutor {
        return taskExecutor!!
    }


    fun getSimilarity(): Similarity {
        return similarity
    }

    @Throws(IOException::class)
    fun collectionStatistics(field: String): CollectionStatistics? {
        checkNotNull(field)
        var docCount: Long = 0
        var sumTotalTermFreq: Long = 0
        var sumDocFreq: Long = 0
        for (leaf in reader!!.leaves()) {
            val terms: Terms = Terms.getTerms(leaf.reader(), field)
            docCount += terms.docCount
            sumTotalTermFreq += terms.sumTotalTermFreq
            sumDocFreq += terms.sumDocFreq
        }
        if (docCount == 0L) {
            return null
        }
        return CollectionStatistics(field, reader.maxDoc().toLong(), docCount, sumTotalTermFreq, sumDocFreq)
    }

    @Throws(IOException::class)
    fun termStatistics(term: Term, docFreq: Int, totalTermFreq: Long): TermStatistics? {
        // This constructor will throw an exception if docFreq <= 0.
        return TermStatistics(term.bytes(), docFreq.toLong(), totalTermFreq)
    }

    companion object{

        var maxClauseCount: Int = 1024

        val defaultSimilarity: Similarity = /*BM25Similarity()*/ object : Similarity(){
            override fun scorer(
                boost: Float,
                collectionStats: CollectionStatistics,
                vararg termStats: TermStatistics
            ): SimScorer {
                TODO("Not yet implemented")
            }
        }
    }

    /**
     * Thrown when an attempt is made to add more than [.getMaxClauseCount] clauses. This
     * typically happens if a PrefixQuery, FuzzyQuery, WildcardQuery, or TermRangeQuery is expanded to
     * many terms during search.
     */
    class TooManyClauses @JvmOverloads constructor(msg: String? = "maxClauseCount is set to $maxClauseCount") :
        RuntimeException(msg) {
        /** The value of [IndexSearcher.getMaxClauseCount] when this Exception was created  */
        val maxClauseCount: Int


        init {
            this.maxClauseCount = IndexSearcher.maxClauseCount
        }
    }
}