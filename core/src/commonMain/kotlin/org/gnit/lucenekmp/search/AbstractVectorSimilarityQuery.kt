package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.QueryTimeout
import org.gnit.lucenekmp.jdkport.Objects
import org.gnit.lucenekmp.search.knn.KnnCollectorManager
import org.gnit.lucenekmp.search.knn.KnnSearchStrategy
import org.gnit.lucenekmp.util.BitSet
import org.gnit.lucenekmp.util.BitSetIterator
import org.gnit.lucenekmp.util.Bits

/**
 * Search for all (approximate) vectors above a similarity threshold.
 *
 * @lucene.experimental
 */
abstract class AbstractVectorSimilarityQuery(
    val field: String,
    protected val traversalSimilarity: Float,
    protected val resultSimilarity: Float,
    protected val filter: Query?
) : Query() {
    init {
        require(traversalSimilarity <= resultSimilarity) {
            "traversalSimilarity should be <= resultSimilarity"
        }
    }

    protected open fun getKnnCollectorManager(): KnnCollectorManager {
        return object : KnnCollectorManager {
            override fun newCollector(
                visitedLimit: Int,
                searchStrategy: KnnSearchStrategy,
                context: LeafReaderContext
            ): KnnCollector {
                return VectorSimilarityCollector(traversalSimilarity, resultSimilarity, visitedLimit.toLong())
            }
        }
    }

    @Throws(IOException::class)
    abstract fun createVectorScorer(context: LeafReaderContext): VectorScorer?

    @Throws(IOException::class)
    protected abstract fun approximateSearch(
        context: LeafReaderContext,
        acceptDocs: Bits?,
        visitLimit: Int,
        knnCollectorManager: KnnCollectorManager
    ): TopDocs

    @Throws(Exception::class)
    override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
        return object : Weight(this@AbstractVectorSimilarityQuery) {
            private val filterWeight: Weight? =
                if (filter == null) null
                else searcher.createWeight(searcher.rewrite(filter), ScoreMode.COMPLETE_NO_SCORES, 1f)

            private val queryTimeout: QueryTimeout? = searcher.timeout
            private val timeLimitingKnnCollectorManager =
                TimeLimitingKnnCollectorManager(getKnnCollectorManager(), queryTimeout)

            override fun explain(context: LeafReaderContext, doc: Int): Explanation {
                if (filterWeight != null) {
                    val filterScorer = filterWeight.scorer(context)
                    if (filterScorer == null || filterScorer.iterator().advance(doc) > doc) {
                        return Explanation.noMatch("Doc does not match the filter")
                    }
                }

                val scorer = createVectorScorer(context)
                    ?: return Explanation.noMatch("Not indexed as the correct vector field")
                val iterator = scorer.iterator()
                val docId = iterator.advance(doc)
                return if (docId == doc) {
                    val score = scorer.score()
                    if (score >= resultSimilarity) {
                        Explanation.match(boost * score, "Score above threshold")
                    } else {
                        Explanation.noMatch("Score below threshold")
                    }
                } else {
                    Explanation.noMatch("No vector found for doc")
                }
            }

            override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
                val leafReader: LeafReader = context.reader()
                val liveDocs: Bits? = leafReader.liveDocs

                if (filterWeight == null) {
                    val results =
                        approximateSearch(
                            context,
                            liveDocs,
                            Int.MAX_VALUE,
                            timeLimitingKnnCollectorManager
                        )
                    return VectorSimilarityScorerSupplier.fromScoreDocs(boost, results.scoreDocs)
                }

                val scorer = filterWeight.scorer(context) ?: return null
                val acceptDocs: BitSet =
                    if (liveDocs == null && scorer.iterator() is BitSetIterator) {
                        (scorer.iterator() as BitSetIterator).bitSet
                    } else {
                        val filtered =
                            object : FilteredDocIdSetIterator(scorer.iterator()) {
                                override fun match(doc: Int): Boolean {
                                    return liveDocs == null || liveDocs.get(doc)
                                }
                            }
                        BitSet.of(filtered, leafReader.maxDoc())
                    }

                val cardinality = acceptDocs.cardinality()
                if (cardinality == 0) {
                    return null
                }

                val results =
                    approximateSearch(
                        context,
                        acceptDocs,
                        cardinality,
                        timeLimitingKnnCollectorManager
                    )

                return if (results.totalHits.relation == TotalHits.Relation.EQUAL_TO ||
                    (queryTimeout != null && queryTimeout.shouldExit())) {
                    VectorSimilarityScorerSupplier.fromScoreDocs(boost, results.scoreDocs)
                } else {
                    VectorSimilarityScorerSupplier.fromAcceptDocs(
                        boost,
                        createVectorScorer(context),
                        BitSetIterator(acceptDocs, cardinality.toLong()),
                        resultSimilarity
                    )
                }
            }

            override fun isCacheable(ctx: LeafReaderContext): Boolean {
                return true
            }
        }
    }

    override fun visit(visitor: QueryVisitor) {
        if (visitor.acceptField(field)) {
            visitor.visitLeaf(this)
        }
    }

    override fun equals(other: Any?): Boolean {
        return sameClassAs(other) &&
            field == (other as AbstractVectorSimilarityQuery).field &&
            traversalSimilarity.compareTo(other.traversalSimilarity) == 0 &&
            resultSimilarity.compareTo(other.resultSimilarity) == 0 &&
            filter == other.filter
    }

    override fun hashCode(): Int {
        return Objects.hash(field, traversalSimilarity, resultSimilarity, filter)
    }

    private class VectorSimilarityScorerSupplier(
        private val iterator: DocIdSetIterator,
        private val cachedScore: FloatArray
    ) : ScorerSupplier() {
        companion object {
            fun fromScoreDocs(boost: Float, scoreDocs: Array<ScoreDoc>): VectorSimilarityScorerSupplier? {
                if (scoreDocs.isEmpty()) {
                    return null
                }
                scoreDocs.sortBy { it.doc }
                val cachedScore = floatArrayOf(0f)
                val iterator =
                    object : DocIdSetIterator() {
                        var index = -1

                        override fun docID(): Int {
                            return when {
                                index < 0 -> -1
                                index >= scoreDocs.size -> NO_MORE_DOCS
                                else -> {
                                    cachedScore[0] = boost * scoreDocs[index].score
                                    scoreDocs[index].doc
                                }
                            }
                        }

                        override fun nextDoc(): Int {
                            index++
                            return docID()
                        }

                        override fun advance(target: Int): Int {
                            var low = 0
                            var high = scoreDocs.size
                            while (low < high) {
                                val mid = (low + high) ushr 1
                                if (scoreDocs[mid].doc < target) low = mid + 1 else high = mid
                            }
                            index = low
                            return docID()
                        }

                        override fun cost(): Long {
                            return scoreDocs.size.toLong()
                        }
                    }
                return VectorSimilarityScorerSupplier(iterator, cachedScore)
            }

            fun fromAcceptDocs(
                boost: Float,
                scorer: VectorScorer?,
                acceptDocs: DocIdSetIterator,
                threshold: Float
            ): VectorSimilarityScorerSupplier? {
                if (scorer == null) {
                    return null
                }
                val cachedScore = floatArrayOf(0f)
                val vectorIterator = scorer.iterator()
                val conjunction =
                    ConjunctionDISI.createConjunction(
                        mutableListOf(vectorIterator, acceptDocs),
                        mutableListOf()
                    )
                val iterator =
                    object : FilteredDocIdSetIterator(conjunction) {
                        override fun match(doc: Int): Boolean {
                            val score = scorer.score()
                            cachedScore[0] = score * boost
                            return score >= threshold
                        }
                    }
                return VectorSimilarityScorerSupplier(iterator, cachedScore)
            }
        }

        override fun get(leadCost: Long): Scorer {
            return object : Scorer() {
                override fun docID(): Int = iterator.docID()
                override fun iterator(): DocIdSetIterator = iterator
                override fun getMaxScore(upTo: Int): Float = Float.POSITIVE_INFINITY
                override fun score(): Float = cachedScore[0]
            }
        }

        override fun cost(): Long {
            return iterator.cost()
        }
    }

    companion object {
        val DEFAULT_STRATEGY: KnnSearchStrategy.Hnsw = KnnSearchStrategy.Hnsw(0)
    }
}
