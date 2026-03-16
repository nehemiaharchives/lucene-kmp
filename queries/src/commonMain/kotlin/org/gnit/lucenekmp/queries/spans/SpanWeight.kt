package org.gnit.lucenekmp.queries.spans

import okio.IOException
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.TermStates
import org.gnit.lucenekmp.search.CollectionStatistics
import org.gnit.lucenekmp.search.Explanation
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Matches
import org.gnit.lucenekmp.search.MatchesIterator
import org.gnit.lucenekmp.search.MatchesUtils
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.ScorerSupplier
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.search.TermStatistics
import org.gnit.lucenekmp.search.Weight
import org.gnit.lucenekmp.search.similarities.Similarity
import org.gnit.lucenekmp.search.similarities.Similarity.SimScorer
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.IOSupplier

/** Expert-only. Public for use by other weight implementations */
abstract class SpanWeight(
    query: SpanQuery,
    searcher: IndexSearcher,
    termStates: Map<Term, TermStates>?,
    boost: Float,
) : Weight(query) {
    /**
     * Enumeration defining what postings information should be retrieved from the index for a given
     * Spans
     */
    enum class Postings {
        POSITIONS {
            override fun getRequiredPostings(): Int {
                return PostingsEnum.POSITIONS.toInt()
            }
        },
        PAYLOADS {
            override fun getRequiredPostings(): Int {
                return PostingsEnum.PAYLOADS.toInt()
            }
        },
        OFFSETS {
            override fun getRequiredPostings(): Int {
                return PostingsEnum.PAYLOADS.toInt() or PostingsEnum.OFFSETS.toInt()
            }
        };

        abstract fun getRequiredPostings(): Int

        fun atLeast(postings: Postings): Postings {
            if (postings.ordinal > ordinal) return postings
            return this
        }
    }

    protected val similarity: Similarity
    private val simScorerInternal: Similarity.SimScorer?
    protected val field: String?

    /**
     * Create a new SpanWeight
     *
     * @param query the parent query
     * @param searcher the IndexSearcher to query against
     * @param termStates a map of terms to [TermStates] for use in building the similarity. May
     *     be null if scores are not required
     * @throws IOException on error
     */
    init {
        this.field = query.getField()
        this.similarity = searcher.similarity
        this.simScorerInternal = buildSimWeight(query, searcher, termStates, boost)
    }

    @Throws(IOException::class)
    private fun buildSimWeight(
        query: SpanQuery,
        searcher: IndexSearcher,
        termStates: Map<Term, TermStates>?,
        boost: Float,
    ): SimScorer? {
        val field = query.getField() ?: return null
        if (termStates == null || termStates.isEmpty()) return null
        val termStats = arrayOfNulls<TermStatistics>(termStates.size)
        var termUpTo = 0
        for (entry in termStates.entries) {
            val ts = entry.value
            if (ts.docFreq() > 0) {
                termStats[termUpTo++] =
                    searcher.termStatistics(entry.key, ts.docFreq(), ts.totalTermFreq())
            }
        }
        val collectionStats: CollectionStatistics = searcher.collectionStatistics(field) ?: return null
        return if (termUpTo > 0) {
            similarity.scorer(
                boost,
                collectionStats,
                *ArrayUtil.copyOfSubArray(termStats, 0, termUpTo).filterNotNull().toTypedArray(),
            )
        } else {
            null // no terms at all exist, we won't use similarity
        }
    }

    /**
     * Collect all TermStates used by this Weight
     *
     * @param contexts a map to add the TermStates to
     */
    abstract fun extractTermStates(contexts: MutableMap<Term, TermStates>)

    /**
     * Expert: Return a Spans object iterating over matches from this Weight
     *
     * @param ctx a LeafReaderContext for this Spans
     * @return a Spans
     * @throws IOException on error
     */
    @Throws(IOException::class)
    abstract fun getSpans(ctx: LeafReaderContext, requiredPostings: Postings): Spans?

    @Throws(IOException::class)
    override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
        val spans = getSpans(context, Postings.POSITIONS) ?: return null
        val norms = if (field == null) null else context.reader().getNormValues(field)
        val scorer = SpanScorer(spans, simScorerInternal, norms)
        return object : ScorerSupplier() {
            @Throws(IOException::class)
            override fun get(leadCost: Long): SpanScorer {
                return scorer
            }

            override fun cost(): Long {
                return scorer.iterator().cost()
            }
        }
    }

    /** Return the SimScorer */
    fun getSimScorer(): SimScorer? {
        return simScorerInternal
    }

    @Throws(IOException::class)
    override fun explain(context: LeafReaderContext, doc: Int): Explanation {
        val scorer = scorer(context) as SpanScorer?
        if (scorer != null) {
            val newDoc = scorer.iterator().advance(doc)
            if (newDoc == doc) {
                if (simScorerInternal != null) {
                    val freq = scorer.sloppyFreq()
                    val freqExplanation = Explanation.match(freq, "phraseFreq=$freq")
                    val norms: NumericDocValues? = if (field == null) null else context.reader().getNormValues(field)
                    var norm = 1L
                    if (norms != null && norms.advanceExact(doc)) {
                        norm = norms.longValue()
                    }
                    val scoreExplanation = simScorerInternal.explain(freqExplanation, norm)
                    return Explanation.match(
                        scoreExplanation.value,
                        "weight($query in $doc) [${similarity::class.simpleName}], result of:",
                        scoreExplanation,
                    )
                } else {
                    // simScorer won't be set when scoring isn't needed
                    return Explanation.match(0f, "match $query in $doc without score")
                }
            }
        }

        return Explanation.noMatch("no matching term")
    }

    private class TermMatch {
        lateinit var term: Term
        var position = 0
        var startOffset = 0
        var endOffset = 0
    }

    @Throws(IOException::class)
    override fun matches(context: LeafReaderContext, doc: Int): Matches? {
        return MatchesUtils.forField(
            field!!,
            IOSupplier {
                val spans = getSpans(context, Postings.OFFSETS)
                if (spans == null || spans.advance(doc) != doc) {
                    return@IOSupplier null
                }
                object : MatchesIterator {
                    var innerTermCount = 0
                    var innerTerms = emptyArray<TermMatch>()

                    val termCollector =
                        object : SpanCollector {
                            @Throws(IOException::class)
                            override fun collectLeaf(postings: PostingsEnum, position: Int, term: Term) {
                                innerTermCount++
                                if (innerTermCount > innerTerms.size) {
                                    val temp = arrayOfNulls<TermMatch>(innerTermCount)
                                    innerTerms.copyInto(temp, 0, 0, innerTermCount - 1)
                                    innerTerms = Array(innerTermCount) { i ->
                                        temp[i] ?: TermMatch()
                                    }
                                }
                                innerTerms[innerTermCount - 1].term = term
                                innerTerms[innerTermCount - 1].position = position
                                innerTerms[innerTermCount - 1].startOffset = postings.startOffset()
                                innerTerms[innerTermCount - 1].endOffset = postings.endOffset()
                            }

                            override fun reset() {
                                innerTermCount = 0
                            }
                        }

                    @Throws(IOException::class)
                    override fun next(): Boolean {
                        innerTermCount = 0
                        return spans.nextStartPosition() != Spans.NO_MORE_POSITIONS
                    }

                    override fun startPosition(): Int {
                        return spans.startPosition()
                    }

                    override fun endPosition(): Int {
                        return spans.endPosition() - 1
                    }

                    @Throws(IOException::class)
                    override fun startOffset(): Int {
                        if (innerTermCount == 0) {
                            collectInnerTerms()
                        }
                        return innerTerms[0].startOffset
                    }

                    @Throws(IOException::class)
                    override fun endOffset(): Int {
                        if (innerTermCount == 0) {
                            collectInnerTerms()
                        }
                        return innerTerms[innerTermCount - 1].endOffset
                    }

                    override val subMatches: MatchesIterator?
                        get() {
                            if (innerTermCount == 0) {
                                collectInnerTerms()
                            }
                            return object : MatchesIterator {
                                var upto = -1

                                @Throws(IOException::class)
                                override fun next(): Boolean {
                                    upto++
                                    return upto < innerTermCount
                                }

                                override fun startPosition(): Int {
                                    return innerTerms[upto].position
                                }

                                override fun endPosition(): Int {
                                    return innerTerms[upto].position
                                }

                                @Throws(IOException::class)
                                override fun startOffset(): Int {
                                    return innerTerms[upto].startOffset
                                }

                                @Throws(IOException::class)
                                override fun endOffset(): Int {
                                    return innerTerms[upto].endOffset
                                }

                                override val subMatches: MatchesIterator?
                                    get() = null

                                override val query: Query
                                    get() = TermQuery(innerTerms[upto].term)
                            }
                        }

                    override val query: Query
                        get() = this@SpanWeight.query

                    @Throws(IOException::class)
                    fun collectInnerTerms() {
                        termCollector.reset()
                        spans.collect(termCollector)
                        innerTerms.sortBy { it.position }
                    }
                }
            },
        )
    }
}
