package org.gnit.lucenekmp.search

import kotlinx.io.IOException
import org.gnit.lucenekmp.index.*
import org.gnit.lucenekmp.jdkport.floatToIntBits
import org.gnit.lucenekmp.search.BooleanClause.Occur
import org.gnit.lucenekmp.search.IndexSearcher.TooManyClauses
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.IOSupplier
import org.gnit.lucenekmp.util.InPlaceMergeSorter
import kotlin.reflect.cast


/**
 * A [Query] that blends index statistics across multiple terms. This is particularly useful
 * when several terms should produce identical scores, regardless of their index statistics.
 *
 *
 * For instance imagine that you are resolving synonyms at search time, all terms should produce
 * identical scores instead of the default behavior, which tends to give higher scores to rare
 * terms.
 *
 *
 * An other useful use-case is cross-field search: imagine that you would like to search for
 * `john` on two fields: `first_name` and `last_name`. You might not want to give
 * a higher weight to matches on the field where `john` is rarer, in which case [ ] would help as well.
 *
 * @lucene.experimental
 */
class BlendedTermQuery private constructor(
    terms: Array<Term>,
    boosts: FloatArray,
    contexts: Array<TermStates?>,
    rewriteMethod: RewriteMethod
) : Query() {
    /** A Builder for [BlendedTermQuery].  */
    class Builder
    /** Sole constructor.  */
    {
        private var numTerms = 0
        private var terms: Array<Term?> = kotlin.arrayOfNulls<Term>(0)
        private var boosts = FloatArray(0)
        private var contexts: Array<TermStates?> = kotlin.arrayOfNulls<TermStates>(0)
        private var rewriteMethod = DISJUNCTION_MAX_REWRITE

        /**
         * Set the [RewriteMethod]. Default is to use [ ][BlendedTermQuery.DISJUNCTION_MAX_REWRITE].
         *
         * @see RewriteMethod
         */
        fun setRewriteMethod(rewiteMethod: RewriteMethod): Builder {
            this.rewriteMethod = rewiteMethod
            return this
        }

        /**
         * Add a new [Term] to this builder, with a default boost of `1`.
         *
         * @see .add
         */
        fun add(term: Term): Builder {
            return add(term, 1f)
        }

        /**
         * Add a [Term] with the provided boost. The higher the boost, the more this term will
         * contribute to the overall score of the [BlendedTermQuery].
         */
        fun add(term: Term, boost: Float): Builder {
            return add(term, boost, null)
        }

        /**
         * Expert: Add a [Term] with the provided boost and context. This method is useful if you
         * already have a [TermStates] object constructed for the given term.
         */
        fun add(term: Term, boost: Float, context: TermStates?): Builder {
            if (numTerms >= IndexSearcher.getMaxClauseCount()) {
                throw TooManyClauses()
            }
            terms = ArrayUtil.grow(terms, numTerms + 1)
            boosts = ArrayUtil.grow(boosts, numTerms + 1)
            contexts = ArrayUtil.grow(contexts, numTerms + 1)
            terms[numTerms] = term
            boosts[numTerms] = boost
            contexts[numTerms] = context
            numTerms += 1
            return this
        }

        /** Build the [BlendedTermQuery].  */
        fun build(): BlendedTermQuery {
            return BlendedTermQuery(
                ArrayUtil.copyOfSubArray(terms, 0, numTerms) as Array<Term>,
                ArrayUtil.copyOfSubArray(boosts, 0, numTerms),
                ArrayUtil.copyOfSubArray(contexts, 0, numTerms),
                rewriteMethod
            )
        }
    }

    /**
     * A [RewriteMethod] defines how queries for individual terms should be merged.
     *
     * @lucene.experimental
     * @see BlendedTermQuery.BOOLEAN_REWRITE
     *
     * @see BlendedTermQuery.DisjunctionMaxRewrite
     */
    abstract class RewriteMethod
    /** Sole constructor  */
    protected constructor() {
        /** Merge the provided sub queries into a single [Query] object.  */
        abstract fun rewrite(subQueries: Array<Query>): Query
    }

    /**
     * A [RewriteMethod] that creates a [DisjunctionMaxQuery] out of the sub queries. This
     * [RewriteMethod] is useful when having a good match on a single field is considered better
     * than having average matches on several fields.
     */
    class DisjunctionMaxRewrite
    /**
     * This [RewriteMethod] will create [DisjunctionMaxQuery] instances that have the
     * provided tie breaker.
     *
     * @see DisjunctionMaxQuery
     */(private val tieBreakerMultiplier: Float) : RewriteMethod() {
        override fun rewrite(subQueries: Array<Query>): Query {
            return DisjunctionMaxQuery(subQueries.toMutableList(), tieBreakerMultiplier)
        }

        override fun equals(obj: Any?): Boolean {
            if (obj == null || this::class != obj::class) {
                return false
            }
            val that = obj as DisjunctionMaxRewrite
            return tieBreakerMultiplier == that.tieBreakerMultiplier
        }

        override fun hashCode(): Int {
            return 31 * this::class.hashCode() + Float.floatToIntBits(tieBreakerMultiplier)
        }
    }

    private val terms: Array<Term>
    private val boosts: FloatArray
    private val contexts: Array<TermStates?>
    private val rewriteMethod: RewriteMethod

    init {
        require(terms.size == boosts.size)
        require(terms.size == contexts.size)
        this.terms = terms
        this.boosts = boosts
        this.contexts = contexts
        this.rewriteMethod = rewriteMethod

        // we sort terms so that equals/hashcode does not rely on the order
        object : InPlaceMergeSorter() {
            protected override fun swap(i: Int, j: Int) {
                val tmpTerm: Term = terms[i]
                terms[i] = terms[j]
                terms[j] = tmpTerm

                val tmpContext: TermStates? = contexts[i]
                contexts[i] = contexts[j]
                contexts[j] = tmpContext

                val tmpBoost = boosts[i]
                boosts[i] = boosts[j]
                boosts[j] = tmpBoost
            }

            protected override fun compare(i: Int, j: Int): Int {
                return terms[i].compareTo(terms[j])
            }
        }.sort(0, terms.size)
    }

    override fun equals(other: Any?): Boolean {
        return sameClassAs(other) && equalsTo(this::class.cast(other))
    }

    private fun equalsTo(other: BlendedTermQuery): Boolean {
        return terms.contentEquals(other.terms) && contexts.contentEquals(other.contexts) && boosts.contentEquals(other.boosts) && rewriteMethod == other.rewriteMethod
    }

    override fun hashCode(): Int {
        var h = classHash()
        h = 31 * h + terms.contentHashCode()
        h = 31 * h + contexts.contentHashCode()
        h = 31 * h + boosts.contentHashCode()
        h = 31 * h + rewriteMethod.hashCode()
        return h
    }

    public override fun toString(field: String?): String {
        val builder = StringBuilder("Blended(")
        for (i in terms.indices) {
            if (i != 0) {
                builder.append(" ")
            }
            var termQuery: Query = TermQuery(terms[i])
            if (boosts[i] != 1f) {
                termQuery = BoostQuery(termQuery, boosts[i])
            }
            builder.append(termQuery.toString(field))
        }
        builder.append(")")
        return builder.toString()
    }

    @Throws(IOException::class)
    public override fun rewrite(indexSearcher: IndexSearcher): Query {
        val contexts: Array<TermStates?> = ArrayUtil.copyArray(this.contexts)
        for (i in contexts.indices) {
            if (contexts[i] == null
                || contexts[i]!!.wasBuiltFor(indexSearcher.getTopReaderContext()) == false
            ) {
                contexts[i] = TermStates.build(indexSearcher, terms[i], true)
            }
        }

        // Compute aggregated doc freq and total term freq
        // df will be the max of all doc freqs
        // ttf will be the sum of all total term freqs
        var df = 0
        var ttf: Long = 0
        for (ctx in contexts) {
            df = kotlin.math.max(df, ctx!!.docFreq())
            ttf += ctx.totalTermFreq()
        }

        for (i in contexts.indices) {
            contexts[i] = adjustFrequencies(indexSearcher.getTopReaderContext(), contexts[i]!!, df, ttf)
        }

        val termQueries = kotlin.arrayOfNulls<Query>(terms.size)
        for (i in terms.indices) {
            termQueries[i] = TermQuery(terms[i], contexts[i]!!)
            if (boosts[i] != 1f) {
                termQueries[i] = BoostQuery(termQueries[i]!!, boosts[i])
            }
        }
        return rewriteMethod.rewrite(termQueries as Array<Query>)
    }

    public override fun visit(visitor: QueryVisitor) {
        val termsToVisit: Array<Term> =
            terms.filter { t: Term -> visitor.acceptField(t.field()) }
                .toTypedArray()
        if (termsToVisit.size > 0) {
            val v = visitor.getSubVisitor(Occur.SHOULD, this)
            v.consumeTerms(this, *termsToVisit)
        }
    }

    companion object {
        /**
         * A [RewriteMethod] that adds all sub queries to a [BooleanQuery]. This [ ] is useful when matching on several fields is considered better than having a
         * good match on a single field.
         */
        val BOOLEAN_REWRITE: RewriteMethod = object : RewriteMethod() {
            override fun rewrite(subQueries: Array<Query>): Query {
                val merged: BooleanQuery.Builder = BooleanQuery.Builder()
                for (query in subQueries) {
                    merged.add(query!!, Occur.SHOULD)
                }
                return merged.build()
            }
        }

        /** [DisjunctionMaxRewrite] instance with a tie-breaker of `0.01`.  */
        val DISJUNCTION_MAX_REWRITE: RewriteMethod = DisjunctionMaxRewrite(0.01f)

        @Throws(IOException::class)
        private fun adjustFrequencies(
            readerContext: IndexReaderContext, ctx: TermStates, artificialDf: Int, artificialTtf: Long
        ): TermStates {
            val leaves: MutableList<LeafReaderContext> = readerContext.leaves()
            val newCtx: TermStates = TermStates(readerContext)
            for (i in leaves.indices) {
                val supplier: IOSupplier<TermState?>? = ctx.get(leaves.get(i))
                if (supplier == null) {
                    continue
                }
                val termState: TermState? = supplier.get()
                if (termState == null) {
                    continue
                }
                newCtx.register(termState, i)
            }
            newCtx.accumulateStatistics(artificialDf, artificialTtf)
            return newCtx
        }
    }
}
