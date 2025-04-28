package org.gnit.lucenekmp.search

import kotlinx.io.IOException
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.TermState
import org.gnit.lucenekmp.index.TermStates
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.RamUsageEstimator


/**
 * Contains functionality common to both [MultiTermQueryConstantScoreBlendedWrapper] and
 * [MultiTermQueryConstantScoreWrapper]. Internal implementation detail only. Not meant as an
 * extension point for users.
 *
 * @lucene.internal
 */
abstract class AbstractMultiTermQueryConstantScoreWrapper<Q : MultiTermQuery>(
    /** Returns the encapsulated query  */
    val query: Q
) : Query(), Accountable {
    override fun ramBytesUsed(): Long {
        if (query is Accountable) {
            return (RamUsageEstimator.NUM_BYTES_OBJECT_HEADER
                    + RamUsageEstimator.NUM_BYTES_OBJECT_REF
                    + (query as Accountable).ramBytesUsed())
        }
        return (RamUsageEstimator.NUM_BYTES_OBJECT_HEADER
                + RamUsageEstimator.NUM_BYTES_OBJECT_REF
                + RamUsageEstimator.QUERY_DEFAULT_RAM_BYTES_USED).toLong()
    }

    override fun toString(field: String?): String {
        // query.toString should be ok for the filter, too, if the query boost is 1.0f
        return query.toString(field)
    }

    override fun equals(other: Any?): Boolean {
        return sameClassAs(other)
                && query.equals((other as AbstractMultiTermQueryConstantScoreWrapper<*>).query)
    }

    override fun hashCode(): Int {
        return 31 * classHash() + query.hashCode()
    }

    val field: String
        /** Returns the field name for this query  */
        get() = query.field

    override fun visit(visitor: QueryVisitor) {
        if (visitor.acceptField(this.field)) {
            query.visit(visitor.getSubVisitor(BooleanClause.Occur.FILTER, this))
        }
    }

    protected class TermAndState(
        term: BytesRef,
        state: TermState,
        docFreq: Int,
        totalTermFreq: Long
    ) {
        val term: BytesRef
        val state: TermState
        val docFreq: Int
        val totalTermFreq: Long

        init {
            this.term = term
            this.state = state
            this.docFreq = docFreq
            this.totalTermFreq = totalTermFreq
        }
    }

    protected class WeightOrDocIdSetIterator {
        val weight: Weight?
        val iterator: DocIdSetIterator?

        constructor(weight: Weight) {
            this.weight = requireNotNull<Weight>(weight)
            this.iterator = null
        }

        constructor(iterator: DocIdSetIterator) {
            this.iterator = iterator
            this.weight = null
        }
    }

    protected abstract class RewritingWeight(
        q: MultiTermQuery,
        boost: Float,
        scoreMode: ScoreMode,
        searcher: IndexSearcher
    ) : ConstantScoreWeight(q, boost) {
        private val q: MultiTermQuery
        private val scoreMode: ScoreMode
        private val searcher: IndexSearcher

        init {
            this.q = q
            this.scoreMode = scoreMode
            this.searcher = searcher
        }

        /**
         * Rewrite the query as either a [Weight] or a [DocIdSetIterator] wrapped in a
         * [WeightOrDocIdSetIterator]. Before this is called, the weight will attempt to "collect"
         * found terms up to a threshold. If fewer terms than the threshold are found, the query will
         * simply be rewritten into a [BooleanQuery] and this method will not be called. This will
         * only be called if it is determined there are more found terms. At the point this method is
         * invoked, `termsEnum` will be positioned on the next "uncollected" term. The terms that
         * were already collected will be in `collectedTerms`.
         */
        @Throws(IOException::class)
        abstract fun rewriteInner(
            context: LeafReaderContext,
            fieldDocCount: Int,
            terms: Terms,
            termsEnum: TermsEnum,
            collectedTerms: MutableList<TermAndState>,
            leadCost: Long
        ): WeightOrDocIdSetIterator

        @Throws(IOException::class)
        private fun rewriteAsBooleanQuery(
            context: LeafReaderContext, collectedTerms: MutableList<TermAndState>
        ): WeightOrDocIdSetIterator {
            val bq: BooleanQuery.Builder = BooleanQuery.Builder()
            for (t in collectedTerms) {
                val termStates = TermStates(searcher.getTopReaderContext())
                termStates.register(t.state, context.ord, t.docFreq, t.totalTermFreq)
                bq.add(TermQuery(Term(q.field, t.term), termStates), BooleanClause.Occur.SHOULD)
            }
            val q: Query = ConstantScoreQuery(bq.build())
            val weight: Weight = searcher.rewrite(q).createWeight(searcher, scoreMode, score())
            return WeightOrDocIdSetIterator(weight)
        }

        @Throws(IOException::class)
        private fun collectTerms(fieldDocCount: Int, termsEnum: TermsEnum, terms: MutableList<TermAndState>): Boolean {
            val threshold: Int =
                kotlin.math.min(BOOLEAN_REWRITE_TERM_COUNT_THRESHOLD, IndexSearcher.getMaxClauseCount())
            for (i in 0..<threshold) {
                val term: BytesRef? = termsEnum.next()
                if (term == null) {
                    return true
                }
                val state: TermState = termsEnum.termState()
                val docFreq: Int = termsEnum.docFreq()
                val termAndState =
                    TermAndState(BytesRef.deepCopyOf(term), state, docFreq, termsEnum.totalTermFreq())
                if (fieldDocCount == docFreq) {
                    // If the term contains every document with a value for the field, we can ignore all
                    // other terms:
                    terms.clear()
                    terms.add(termAndState)
                    return true
                }
                terms.add(termAndState)
            }
            return termsEnum.next() == null
        }

        private fun scorerForIterator(iterator: DocIdSetIterator?): Scorer? {
            if (iterator == null) {
                return null
            }
            return ConstantScoreScorer(score(), scoreMode, iterator)
        }

        @Throws(IOException::class)
        override fun matches(context: LeafReaderContext, doc: Int): Matches? {
            val terms: Terms? = context.reader().terms(q.field)
            if (terms == null) {
                return null
            }
            return MatchesUtils.forField(
                q.field
            ) {
                DisjunctionMatchesIterator.fromTermsEnum(
                    context, doc, q, q.field, q.getTermsEnum(terms)
                )!!
            }
        }

        @Throws(IOException::class)
        override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
            val terms: Terms? = context.reader().terms(q.field)
            if (terms == null) {
                return null
            }

            checkNotNull(terms)

            val fieldDocCount: Int = terms.getDocCount()
            val termsEnum: TermsEnum = checkNotNull(q.getTermsEnum(terms))
            val collectedTerms: MutableList<TermAndState> = mutableListOf<TermAndState>()
            val collectResult = collectTerms(fieldDocCount, termsEnum, collectedTerms)

            val cost: Long
            if (collectResult) {
                // Return a null supplier if no query terms were in the segment:
                if (collectedTerms.isEmpty()) {
                    return null
                }

                // TODO: Instead of replicating the cost logic of a BooleanQuery we could consider rewriting
                // to a BQ eagerly at this point and delegating to its cost method (instead of lazily
                // rewriting on #get). Not sure what the performance hit would be of doing this though.
                var sumTermCost: Long = 0
                for (collectedTerm in collectedTerms) {
                    sumTermCost += collectedTerm.docFreq.toLong()
                }
                cost = sumTermCost
            } else {
                cost = estimateCost(terms, q.termsCount)
            }

            val weightOrIteratorSupplier: IOLongFunction<WeightOrDocIdSetIterator> =
                object : IOLongFunction<WeightOrDocIdSetIterator> {
                    @Throws(IOException::class)
                    override fun apply(leadCost: Long): WeightOrDocIdSetIterator {
                        return if (collectResult) {
                            rewriteAsBooleanQuery(context, collectedTerms)
                        } else {
                            // Too many terms to rewrite as a simple bq.
                            // Invoke rewriteInner logic to handle rewriting:
                            rewriteInner(
                                context, fieldDocCount, terms, termsEnum, collectedTerms, leadCost
                            )
                        }
                    }
                }

            return object : ScorerSupplier() {
                @Throws(IOException::class)
                override fun get(leadCost: Long): Scorer {
                    val weightOrIterator = weightOrIteratorSupplier.apply(leadCost)
                    val scorer: Scorer?
                    if (weightOrIterator == null) {
                        scorer = null
                    } else if (weightOrIterator.weight != null) {
                        scorer = weightOrIterator.weight.scorer(context)
                    } else {
                        scorer = scorerForIterator(weightOrIterator.iterator)
                    }

                    // It's against the API contract to return a null scorer from a non-null ScoreSupplier.
                    // So if our ScoreSupplier was non-null (i.e., thought there might be hits) but we now
                    // find that there are actually no hits, we need to return an empty Scorer as opposed
                    // to null:
                    return scorer ?: ConstantScoreScorer(
                        score(),
                        scoreMode,
                        DocIdSetIterator.empty()
                    )
                }

                @Throws(IOException::class)
                override fun bulkScorer(): BulkScorer {
                    val weightOrIterator =
                        weightOrIteratorSupplier.apply(Long.Companion.MAX_VALUE)
                    val bulkScorer: BulkScorer?
                    if (weightOrIterator == null) {
                        bulkScorer = null
                    } else if (weightOrIterator.weight != null) {
                        bulkScorer = weightOrIterator.weight.bulkScorer(context)
                    } else {
                        bulkScorer =
                            DefaultBulkScorer(
                                ConstantScoreScorer(score(), scoreMode, weightOrIterator.iterator!!)
                            )
                    }

                    // It's against the API contract to return a null scorer from a non-null ScoreSupplier.
                    // So if our ScoreSupplier was non-null (i.e., thought there might be hits) but we now
                    // find that there are actually no hits, we need to return an empty BulkScorer as opposed
                    // to null:
                    return bulkScorer ?: DefaultBulkScorer(
                        ConstantScoreScorer(score(), scoreMode, DocIdSetIterator.empty())
                    )
                }

                override fun cost(): Long {
                    return cost
                }
            }
        }

        private interface IOLongFunction<T> {
            @Throws(IOException::class)
            fun apply(arg: Long): T?
        }

        override fun isCacheable(ctx: LeafReaderContext): Boolean {
            return true
        }

        companion object {
            @Throws(IOException::class)
            private fun estimateCost(terms: Terms, queryTermsCount: Long): Long {
                // Estimate the cost. If the MTQ can provide its term count, we can do a better job
                // estimating.
                // Cost estimation reasoning is:
                // 1. If we don't know how many query terms there are, we assume that every term could be
                //    in the MTQ and estimate the work as the total docs across all terms.
                // 2. If we know how many query terms there are...
                //    2a. Assume every query term matches at least one document (queryTermsCount).
                //    2b. Determine the total number of docs beyond the first one for each term.
                //        That count provides a ceiling on the number of extra docs that could match beyond
                //        that first one. (We omit the first since it's already been counted in 2a).
                // See: LUCENE-10207
                val cost: Long
                if (queryTermsCount == -1L) {
                    cost = terms.getSumDocFreq()
                } else {
                    var potentialExtraCost: Long = terms.getSumDocFreq()
                    val indexedTermCount: Long = terms.size()
                    if (indexedTermCount != -1L) {
                        potentialExtraCost -= indexedTermCount
                    }
                    cost = queryTermsCount + potentialExtraCost
                }

                return cost
            }
        }
    }

    companion object {
        // mtq that matches 16 terms or less will be executed as a regular disjunction
        const val BOOLEAN_REWRITE_TERM_COUNT_THRESHOLD: Int = 16
    }
}
