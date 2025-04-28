package org.gnit.lucenekmp.search

import kotlinx.coroutines.runBlocking
import kotlinx.io.IOException
import kotlin.math.min
import org.gnit.lucenekmp.codecs.KnnVectorsReader
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.QueryTimeout
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.Objects
import org.gnit.lucenekmp.search.knn.KnnCollectorManager
import org.gnit.lucenekmp.search.knn.KnnSearchStrategy
import org.gnit.lucenekmp.search.knn.TopKnnCollectorManager
import org.gnit.lucenekmp.util.BitSet
import org.gnit.lucenekmp.util.BitSetIterator
import org.gnit.lucenekmp.util.Bits

/**
 * Uses [KnnVectorsReader.search] to perform nearest neighbour search.
 *
 *
 * This query also allows for performing a kNN search subject to a filter. In this case, it first
 * executes the filter for each leaf, then chooses a strategy dynamically:
 *
 *
 *  * If the filter cost is less than k, just execute an exact search
 *  * Otherwise run a kNN search subject to the filter
 *  * If the kNN search visits too many vectors without completing, stop and run an exact search
 *
 */
abstract class AbstractKnnVectorQuery(
    /**
     * @return the knn vector field where the knn vector search happens.
     */
    val field: String,
    /**
     * @return the max number of results the KnnVector search returns.
     */
    val k: Int,
    /**
     * @return the filter that is executed before the KnnVector search happens. Only the results
     * accepted by this filter are returned by the KnnVector search.
     */
    val filter: Query?, protected val searchStrategy: KnnSearchStrategy
) : Query() {

    init {
        require(k >= 1) { "k must be at least 1, got: $k" }
    }

    @Throws(IOException::class)
    override fun rewrite(indexSearcher: IndexSearcher): Query {
        val reader: IndexReader = indexSearcher.getIndexReader()

        val filterWeight: Weight?
        if (filter != null) {
            val booleanQuery: BooleanQuery =
                BooleanQuery.Builder()
                    .add(filter, BooleanClause.Occur.FILTER)
                    .add(FieldExistsQuery(field), BooleanClause.Occur.FILTER)
                    .build()
            val rewritten: Query = indexSearcher.rewrite(booleanQuery)
            filterWeight = indexSearcher.createWeight(rewritten, ScoreMode.COMPLETE_NO_SCORES, 1f)
        } else {
            filterWeight = null
        }

        val knnCollectorManager =
            TimeLimitingKnnCollectorManager(
                getKnnCollectorManager(k, indexSearcher), indexSearcher.getTimeout()
            )
        val taskExecutor: TaskExecutor = indexSearcher.getTaskExecutor()
        val leafReaderContexts: MutableList<LeafReaderContext> = reader.leaves()
        val tasks: MutableList<org.gnit.lucenekmp.jdkport.Callable<TopDocs>> = ArrayList(leafReaderContexts.size)
        for (context in leafReaderContexts) {
            tasks.add(object : org.gnit.lucenekmp.jdkport.Callable<TopDocs> {
                override fun call(): TopDocs {
                    return searchLeaf(
                        context,
                        filterWeight,
                        knnCollectorManager
                    )
                }
            })
        }
        val perLeafResults: Array<TopDocs> = runBlocking { taskExecutor.invokeAll(tasks) }.toTypedArray()

        // Merge sort the results
        val topK = mergeLeafResults(perLeafResults)
        if (topK.scoreDocs!!.isEmpty()) {
            return MatchNoDocsQuery()
        }
        return createRewrittenQuery(reader, topK)
    }

    @Throws(IOException::class)
    private fun searchLeaf(
        ctx: LeafReaderContext,
        filterWeight: Weight?,
        timeLimitingKnnCollectorManager: TimeLimitingKnnCollectorManager
    ): TopDocs {
        val results = getLeafResults(ctx, filterWeight, timeLimitingKnnCollectorManager)
        if (ctx.docBase > 0) {
            for (scoreDoc in results.scoreDocs!!) {
                scoreDoc.doc += ctx.docBase
            }
        }
        return results
    }

    @Throws(IOException::class)
    private fun getLeafResults(
        ctx: LeafReaderContext,
        filterWeight: Weight?,
        timeLimitingKnnCollectorManager: TimeLimitingKnnCollectorManager
    ): TopDocs {
        val reader: LeafReader = ctx.reader()
        val liveDocs: Bits = reader.liveDocs!!

        if (filterWeight == null) {
            return approximateSearch(ctx, liveDocs, Int.Companion.MAX_VALUE, timeLimitingKnnCollectorManager)
        }

        val scorer = filterWeight.scorer(ctx)
        if (scorer == null) {
            return NO_RESULTS
        }

        val acceptDocs: BitSet = createBitSet(scorer.iterator(), liveDocs, reader.maxDoc())
        val cost: Int = acceptDocs.cardinality()
        val queryTimeout: QueryTimeout? = timeLimitingKnnCollectorManager.getQueryTimeout()

        if (cost <= k) {
            // If there are <= k possible matches, short-circuit and perform exact search, since HNSW
            // must always visit at least k documents
            return exactSearch(ctx, BitSetIterator(acceptDocs, cost.toLong()), queryTimeout!!)
        }

        // Perform the approximate kNN search
        // We pass cost + 1 here to account for the edge case when we explore exactly cost vectors
        val results = approximateSearch(ctx, acceptDocs, cost + 1, timeLimitingKnnCollectorManager)
        return if ((results.totalHits.relation === TotalHits.Relation.EQUAL_TO // We know that there are more than `k` available docs, if we didn't even get `k`
                    // something weird
                    // happened, and we need to drop to exact search
                    && results.scoreDocs!!.size >= k) // Return partial results only when timeout is met
            || (queryTimeout != null && queryTimeout.shouldExit())
        ) {
            results
        } else {
            // We stopped the kNN search because it visited too many nodes, so fall back to exact search
            exactSearch(ctx, BitSetIterator(acceptDocs, cost.toLong()), queryTimeout!!)
        }
    }

    @Throws(IOException::class)
    private fun createBitSet(iterator: DocIdSetIterator, liveDocs: Bits?, maxDoc: Int): BitSet {
        if (liveDocs == null && iterator is BitSetIterator) {
            // If we already have a BitSet and no deletions, reuse the BitSet
            return iterator.bitSet
        } else {
            // Create a new BitSet from matching and live docs
            val filterIterator: FilteredDocIdSetIterator =
                object : FilteredDocIdSetIterator(iterator) {
                    override fun match(doc: Int): Boolean {
                        return liveDocs == null || liveDocs.get(doc)
                    }
                }
            return BitSet.of(filterIterator, maxDoc)
        }
    }

    protected fun getKnnCollectorManager(k: Int, searcher: IndexSearcher): KnnCollectorManager {
        return TopKnnCollectorManager(k, searcher)
    }

    @Throws(IOException::class)
    protected abstract fun approximateSearch(
        context: LeafReaderContext,
        acceptDocs: Bits,
        visitedLimit: Int,
        knnCollectorManager: KnnCollectorManager
    ): TopDocs

    @Throws(IOException::class)
    abstract fun createVectorScorer(context: LeafReaderContext, fi: FieldInfo): VectorScorer?

    // We allow this to be overridden so that tests can check what search strategy is used
    @Throws(IOException::class)
    protected fun exactSearch(
        context: LeafReaderContext, acceptIterator: DocIdSetIterator, queryTimeout: QueryTimeout?
    ): TopDocs {
        val fi: FieldInfo? = context.reader().fieldInfos.fieldInfo(field)
        if (fi == null || fi.vectorDimension == 0) {
            // The field does not exist or does not index vectors
            return NO_RESULTS
        }

        val vectorScorer = createVectorScorer(context, fi)
        if (vectorScorer == null) {
            return NO_RESULTS
        }
        val queueSize = min(k, Math.toIntExact(acceptIterator.cost()))
        val queue = HitQueue(queueSize, true)
        var relation: TotalHits.Relation = TotalHits.Relation.EQUAL_TO
        var topDoc: ScoreDoc = queue.top()!!
        val vectorIterator = vectorScorer.iterator()
        val conjunction: DocIdSetIterator =
            ConjunctionDISI.createConjunction(
                mutableListOf(vectorIterator, acceptIterator),
                mutableListOf()
            )
        var doc: Int
        while ((conjunction.nextDoc().also { doc = it }) != DocIdSetIterator.NO_MORE_DOCS) {
            // Mark results as partial if timeout is met
            if (queryTimeout != null && queryTimeout.shouldExit()) {
                relation = TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO
                break
            }
            require(vectorIterator.docID() == doc)
            val score = vectorScorer.score()
            if (score > topDoc.score) {
                topDoc.score = score
                topDoc.doc = doc
                topDoc = queue.updateTop()!!
            }
        }

        // Remove any remaining sentinel values
        while (queue.size() > 0 && queue.top()!!.score < 0) {
            queue.pop()
        }

        val topScoreDocs = kotlin.arrayOfNulls<ScoreDoc>(queue.size())
        for (i in topScoreDocs.indices.reversed()) {
            topScoreDocs[i] = queue.pop()
        }

        val totalHits = TotalHits(acceptIterator.cost(), relation)
        return TopDocs(totalHits, topScoreDocs as Array<ScoreDoc>)
    }

    /**
     * Merges all segment-level kNN results to get the index-level kNN results.
     *
     *
     * The default implementation delegates to [TopDocs.merge] to find the
     * overall top [.k], which requires input results to be sorted.
     *
     *
     * This method is useful for reading and / or modifying the final results as needed.
     *
     * @param perLeafResults array of segment-level kNN results.
     * @return index-level kNN results (no constraint on their ordering).
     * @lucene.experimental
     */
    protected fun mergeLeafResults(perLeafResults: Array<TopDocs>): TopDocs {
        return TopDocs.merge(k, perLeafResults)
    }

    private fun createRewrittenQuery(reader: IndexReader, topK: TopDocs): Query {
        val len: Int = topK.scoreDocs!!.size

        require(len > 0)
        val maxScore = topK.scoreDocs!![0].score

        topK.scoreDocs?.sortBy { it.doc }
        val docs = IntArray(len)
        val scores = FloatArray(len)
        for (i in 0..<len) {
            docs[i] = topK.scoreDocs!![i].doc
            scores[i] = topK.scoreDocs!![i].score
        }
        val segmentStarts = findSegmentStarts(reader.leaves(), docs)
        return DocAndScoreQuery(docs, scores, maxScore, segmentStarts, reader.context.id())
    }

    override fun visit(visitor: QueryVisitor) {
        if (visitor.acceptField(field)) {
            visitor.visitLeaf(this)
        }
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || this::class != o::class) return false
        val that = o as AbstractKnnVectorQuery
        return k == that.k && field == that.field
                && filter == that.filter
                && searchStrategy == that.searchStrategy
    }

    override fun hashCode(): Int {
        return Objects.hash(field, k, filter)
    }

    /** Caches the results of a KnnVector search: a list of docs and their scores  */
    internal class DocAndScoreQuery
    /**
     * Constructor
     *
     * @param docs the global docids of documents that match, in ascending order
     * @param scores the scores of the matching documents
     * @param segmentStarts the indexes in docs and scores corresponding to the first matching
     * document in each segment. If a segment has no matching documents, it should be assigned
     * the index of the next segment that does. There should be a final entry that is always
     * docs.length-1.
     * @param contextIdentity an object identifying the reader context that was used to build this
     * query
     */(
        private val docs: IntArray,
        private val scores: FloatArray,
        private val maxScore: Float,
        private val segmentStarts: IntArray,
        private val contextIdentity: Any
    ) : Query() {
        @Throws(IOException::class)
        override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
            check(
                searcher.getIndexReader().context.id() === contextIdentity
            ) { "This DocAndScore query was created by a different reader" }
            return object : Weight(this) {
                override fun explain(context: LeafReaderContext, doc: Int): Explanation {
                    val found = Arrays.binarySearch(docs, doc + context.docBase)
                    if (found < 0) {
                        return Explanation.noMatch("not in top " + docs.size + " docs")
                    }
                    return Explanation.match(scores[found] * boost, "within top " + docs.size + " docs")
                }

                override fun count(context: LeafReaderContext): Int {
                    return segmentStarts[context.ord + 1] - segmentStarts[context.ord]
                }

                @Throws(IOException::class)
                override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
                    if (segmentStarts[context.ord] == segmentStarts[context.ord + 1]) {
                        return null
                    }
                    val scorer: Scorer =
                        object : Scorer() {
                            val lower: Int = segmentStarts[context.ord]
                            val upper: Int = segmentStarts[context.ord + 1]
                            var upTo: Int = -1

                            override fun iterator(): DocIdSetIterator {
                                return object : DocIdSetIterator() {
                                    override fun docID(): Int {
                                        return docIdNoShadow()
                                    }

                                    override fun nextDoc(): Int {
                                        if (upTo == -1) {
                                            upTo = lower
                                        } else {
                                            ++upTo
                                        }
                                        return docIdNoShadow()
                                    }

                                    @Throws(IOException::class)
                                    override fun advance(target: Int): Int {
                                        return slowAdvance(target)
                                    }

                                    override fun cost(): Long {
                                        return (upper - lower).toLong()
                                    }
                                }
                            }

                            override fun getMaxScore(docId: Int): Float {
                                return maxScore * boost
                            }

                            override fun score(): Float {
                                return scores[upTo] * boost
                            }

                            /**
                             * move the implementation of docID() into a differently-named method so we can call
                             * it from DocIDSetIterator.docID() even though this class is anonymous
                             *
                             * @return the current docid
                             */
                            fun docIdNoShadow(): Int {
                                if (upTo == -1) {
                                    return -1
                                }
                                if (upTo >= upper) {
                                    return DocIdSetIterator.NO_MORE_DOCS
                                }
                                return docs[upTo] - context.docBase
                            }

                            override fun docID(): Int {
                                return docIdNoShadow()
                            }
                        }
                    return DefaultScorerSupplier(scorer)
                }

                override fun isCacheable(ctx: LeafReaderContext): Boolean {
                    return true
                }
            }
        }

        override fun toString(field: String?): String {
            return "DocAndScoreQuery[" + docs[0] + ",...][" + scores[0] + ",...]," + maxScore
        }

        override fun visit(visitor: QueryVisitor) {
            visitor.visitLeaf(this)
        }

        override fun equals(obj: Any?): Boolean {
            if (!sameClassAs(obj)) {
                return false
            }
            return contextIdentity === (obj as DocAndScoreQuery).contextIdentity && docs.contentEquals(obj.docs) && scores.contentEquals(
                obj.scores
            )
        }

        override fun hashCode(): Int {
            return Objects.hash(
                classHash(), contextIdentity, docs.contentHashCode(), scores.contentHashCode()
            )
        }
    }

    companion object {
        private val NO_RESULTS = TopDocsCollector.EMPTY_TOPDOCS

        fun findSegmentStarts(leaves: MutableList<LeafReaderContext>, docs: IntArray): IntArray {
            val starts = IntArray(leaves.size + 1)
            starts[starts.size - 1] = docs.size
            if (starts.size == 2) {
                return starts
            }
            var resultIndex = 0
            for (i in 1..<starts.size - 1) {
                val upper: Int = leaves[i].docBase
                resultIndex = Arrays.binarySearch(docs, resultIndex, docs.size, upper)
                if (resultIndex < 0) {
                    resultIndex = -1 - resultIndex
                }
                starts[i] = resultIndex
            }
            return starts
        }
    }
}