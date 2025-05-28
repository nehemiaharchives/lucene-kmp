package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.TermStates
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.util.AttributeSource
import kotlin.reflect.cast


/**
 * An abstract [Query] that matches documents containing a subset of terms provided by a
 * [FilteredTermsEnum] enumeration.
 *
 *
 * This query cannot be used directly; you must subclass it and define [ ][.getTermsEnum] to provide a [FilteredTermsEnum] that iterates
 * through the terms to be matched.
 *
 *
 * **NOTE**: if [RewriteMethod] is either [.CONSTANT_SCORE_BOOLEAN_REWRITE] or
 * [.SCORING_BOOLEAN_REWRITE], you may encounter a [IndexSearcher.TooManyClauses]
 * exception during searching, which happens when the number of terms to be searched exceeds [ ][IndexSearcher.getMaxClauseCount]. Setting [RewriteMethod] to [ ][.CONSTANT_SCORE_BLENDED_REWRITE] or [.CONSTANT_SCORE_REWRITE] prevents this.
 *
 *
 * The recommended rewrite method is [.CONSTANT_SCORE_BLENDED_REWRITE]: it doesn't spend
 * CPU computing unhelpful scores, and is the most performant rewrite method given the query. If you
 * need scoring (like [FuzzyQuery], use [TopTermsScoringBooleanQueryRewrite] which uses
 * a priority queue to only collect competitive terms and not hit this limitation.
 *
 *
 * Note that org.apache.lucene.queryparser.classic.QueryParser produces MultiTermQueries using
 * [.CONSTANT_SCORE_REWRITE] by default.
 */
abstract class MultiTermQuery(field: String, rewriteMethod: RewriteMethod) : Query() {
    /** Returns the field name for this query  */
    val field: String = requireNotNull<String>(field){"field must not be null"}

    /**
     * @return the rewrite method used to build the final query
     */
    val rewriteMethod: RewriteMethod

    /** Abstract class that defines how the query is rewritten.  */
    abstract class RewriteMethod {
        @Throws(IOException::class)
        abstract fun rewrite(indexSearcher: IndexSearcher, query: MultiTermQuery): Query

        /**
         * Returns the [MultiTermQuery]s [TermsEnum]
         *
         * @see MultiTermQuery.getTermsEnum
         */
        @Throws(IOException::class)
        protected fun getTermsEnum(query: MultiTermQuery, terms: Terms, atts: AttributeSource): TermsEnum {
            return query.getTermsEnum(
                terms, atts
            ) // allow RewriteMethod subclasses to pull a TermsEnum from the MTQ
        }
    }

    /**
     * A rewrite method that first translates each term into [BooleanClause.Occur.SHOULD] clause
     * in a BooleanQuery, and keeps the scores as computed by the query.
     *
     *
     * This rewrite method only uses the top scoring terms so it will not overflow the boolean max
     * clause count.
     */
    class TopTermsScoringBooleanQueryRewrite(size: Int) : TopTermsRewrite<BooleanQuery.Builder>(size) {
        /**
         * Create a TopTermsScoringBooleanQueryRewrite for at most `size` terms.
         *
         *
         * NOTE: if [IndexSearcher.getMaxClauseCount] is smaller than `size`, then
         * it will be used instead.
         */

        override val maxSize: Int
            get() = IndexSearcher.maxClauseCount

        override val topLevelBuilder: BooleanQuery.Builder
            get() = BooleanQuery.Builder()

        override fun build(builder: BooleanQuery.Builder): Query {
            return builder.build()
        }

        override fun addClause(
            topLevel: BooleanQuery.Builder, term: Term, docCount: Int, boost: Float, states: TermStates?
        ) {
            val tq = TermQuery(term, states!!)
            topLevel.add(BoostQuery(tq, boost), BooleanClause.Occur.SHOULD)
        }
    }

    /**
     * A rewrite method that first translates each term into [BooleanClause.Occur.SHOULD] clause
     * in a BooleanQuery, but adjusts the frequencies used for scoring to be blended across the terms,
     * otherwise the rarest term typically ranks highest (often not useful eg in the set of expanded
     * terms in a FuzzyQuery).
     *
     *
     * This rewrite method only uses the top scoring terms so it will not overflow the boolean max
     * clause count.
     */
    class TopTermsBlendedFreqScoringRewrite(size: Int) : TopTermsRewrite<BlendedTermQuery.Builder>(size) {
        /**
         * Create a TopTermsBlendedScoringBooleanQueryRewrite for at most `size` terms.
         *
         *
         * NOTE: if [IndexSearcher.getMaxClauseCount] is smaller than `size`, then
         * it will be used instead.
         */

        override val maxSize: Int
            get() = IndexSearcher.maxClauseCount

        override val topLevelBuilder: BlendedTermQuery.Builder
            get() {
                val builder: BlendedTermQuery.Builder = BlendedTermQuery.Builder()
                builder.setRewriteMethod(BlendedTermQuery.BOOLEAN_REWRITE)
                return builder
            }

        protected override fun build(builder: BlendedTermQuery.Builder): Query {
            return builder.build()
        }

        protected override fun addClause(
            topLevel: BlendedTermQuery.Builder,
            term: Term,
            docCount: Int,
            boost: Float,
            states: TermStates?
        ) {
            topLevel.add(term, boost, states)
        }
    }

    /**
     * A rewrite method that first translates each term into [BooleanClause.Occur.SHOULD] clause
     * in a BooleanQuery, but the scores are only computed as the boost.
     *
     *
     * This rewrite method only uses the top scoring terms so it will not overflow the boolean max
     * clause count.
     */
    class TopTermsBoostOnlyBooleanQueryRewrite

    /**
     * Create a TopTermsBoostOnlyBooleanQueryRewrite for at most `size` terms.
     *
     *
     * NOTE: if [IndexSearcher.getMaxClauseCount] is smaller than `size`, then
     * it will be used instead.
     */
        (size: Int) : TopTermsRewrite<BooleanQuery.Builder>(size) {
        override val maxSize: Int
            get() = IndexSearcher.maxClauseCount

        override val topLevelBuilder: BooleanQuery.Builder
            get() = BooleanQuery.Builder()

        protected override fun build(builder: BooleanQuery.Builder): Query {
            return builder.build()
        }

        protected override fun addClause(
            topLevel: BooleanQuery.Builder, term: Term, docFreq: Int, boost: Float, states: TermStates?
        ) {
            val q: Query = ConstantScoreQuery(TermQuery(term, states!!))
            topLevel.add(BoostQuery(q, boost), BooleanClause.Occur.SHOULD)
        }
    }

    /** Constructs a query matching terms that cannot be represented with a single Term.  */
    init {
        this.rewriteMethod = requireNotNull<RewriteMethod>(rewriteMethod){"rewriteMethod must not be null"}
    }

    /**
     * Construct the enumeration to be used, expanding the pattern term. This method should only be
     * called if the field exists (ie, implementations can assume the field does exist). This method
     * should not return null (should instead return [TermsEnum.EMPTY] if no terms match). The
     * TermsEnum must already be positioned to the first matching term. The given [ ] is passed by the [RewriteMethod] to share information between segments,
     * for example [TopTermsRewrite] uses it to share maximum competitive boosts
     */
    @Throws(IOException::class)
    protected abstract fun getTermsEnum(terms: Terms, atts: AttributeSource): TermsEnum

    /**
     * Constructs an enumeration that expands the pattern term. This method should only be called if
     * the field exists (ie, implementations can assume the field does exist). This method never
     * returns null. The returned TermsEnum is positioned to the first matching term.
     */
    @Throws(IOException::class)
    fun getTermsEnum(terms: Terms): TermsEnum {
        return getTermsEnum(terms, AttributeSource())
    }

    val termsCount: Long
        /**
         * Return the number of unique terms contained in this query, if known up-front. If not known, -1
         * will be returned.
         */
        get() = -1

    /**
     * To rewrite to a simpler form, instead return a simpler enum from [.getTermsEnum]. For example, to rewrite to a single term, return a [SingleTermsEnum]
     */
    override fun rewrite(indexSearcher: IndexSearcher): Query {
        return rewriteMethod.rewrite(indexSearcher, this)
    }

    override fun hashCode(): Int {
        val prime = 31
        var result = classHash()
        result = prime * result + rewriteMethod.hashCode()
        result = prime * result + field.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        return sameClassAs(other) && equalsTo(this::class.cast(other))
    }

    private fun equalsTo(other: MultiTermQuery): Boolean {
        return rewriteMethod == other.rewriteMethod && field == other.field
    }

    companion object {
        /**
         * A rewrite method where documents are assigned a constant score equal to the query's boost.
         * Maintains a boolean query-like implementation over the most costly terms while pre-processing
         * the less costly terms into a filter bitset. Enforces an upper-limit on the number of terms
         * allowed in the boolean query-like implementation.
         *
         *
         * This method aims to balance the benefits of both [.CONSTANT_SCORE_BOOLEAN_REWRITE] and
         * [.CONSTANT_SCORE_REWRITE] by enabling skipping and early termination over costly terms
         * while limiting the overhead of a BooleanQuery with many terms. It also ensures you cannot hit
         * [org.apache.lucene.search.IndexSearcher.TooManyClauses]. For some use-cases with all low
         * cost terms, [.CONSTANT_SCORE_REWRITE] may be more performant. While for some use-cases
         * with all high cost terms, [.CONSTANT_SCORE_BOOLEAN_REWRITE] may be better.
         */
        val CONSTANT_SCORE_BLENDED_REWRITE: RewriteMethod = object : RewriteMethod() {
            override fun rewrite(indexSearcher: IndexSearcher, query: MultiTermQuery): Query {
                return MultiTermQueryConstantScoreBlendedWrapper(query)
            }
        }

        /**
         * A rewrite method that first creates a private Filter, by visiting each term in sequence and
         * marking all docs for that term. Matching documents are assigned a constant score equal to the
         * query's boost.
         *
         *
         * This method is faster than the BooleanQuery rewrite methods when the number of matched terms
         * or matched documents is non-trivial. Also, it will never hit an errant [ ] exception.
         */
        val CONSTANT_SCORE_REWRITE: RewriteMethod = object : RewriteMethod() {
            override fun rewrite(indexSearcher: IndexSearcher, query: MultiTermQuery): Query {
                return MultiTermQueryConstantScoreWrapper(query)
            }
        }

        /**
         * A rewrite method that uses [org.apache.lucene.index.DocValuesType.SORTED] / [ ][org.apache.lucene.index.DocValuesType.SORTED_SET] doc values to find matching docs through a
         * post-filtering type approach. This will be very slow if used in isolation, but will likely be
         * the most performant option when combined with a sparse query clause. All matching docs are
         * assigned a constant score equal to the query's boost.
         *
         *
         * If you don't have doc values indexed, see the other rewrite methods that rely on postings
         * alone (e.g., [.CONSTANT_SCORE_BLENDED_REWRITE], [.SCORING_BOOLEAN_REWRITE], etc.
         * depending on scoring needs).
         */
        val DOC_VALUES_REWRITE: RewriteMethod = DocValuesRewriteMethod()

        /**
         * A rewrite method that first translates each term into [BooleanClause.Occur.SHOULD] clause
         * in a BooleanQuery, and keeps the scores as computed by the query. Note that typically such
         * scores are meaningless to the user, and require non-trivial CPU to compute, so it's almost
         * always better to use [.CONSTANT_SCORE_REWRITE] instead.
         *
         *
         * **NOTE**: This rewrite method will hit [IndexSearcher.TooManyClauses] if the number
         * of terms exceeds [IndexSearcher.getMaxClauseCount].
         */
        val SCORING_BOOLEAN_REWRITE: RewriteMethod = ScoringRewrite.SCORING_BOOLEAN_REWRITE

        /**
         * Like [.SCORING_BOOLEAN_REWRITE] except scores are not computed. Instead, each matching
         * document receives a constant score equal to the query's boost.
         *
         *
         * **NOTE**: This rewrite method will hit [IndexSearcher.TooManyClauses] if the number
         * of terms exceeds [IndexSearcher.getMaxClauseCount].
         */
        val CONSTANT_SCORE_BOOLEAN_REWRITE: RewriteMethod = ScoringRewrite.CONSTANT_SCORE_BOOLEAN_REWRITE
    }

}
