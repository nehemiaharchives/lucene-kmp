package org.gnit.lucenekmp.queries.spans

import okio.IOException
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.ReaderUtil
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.TermState
import org.gnit.lucenekmp.index.TermStates
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.QueryVisitor
import org.gnit.lucenekmp.search.ScoreMode
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.util.IOSupplier

/**
 * Matches spans containing a term. This should not be used for terms that are indexed at position
 * Integer.MAX_VALUE.
 */
class SpanTermQuery(
    private val term: Term,
    private val termStates: TermStates? = null,
) : SpanQuery() {
    /** Return the term whose spans are matched. */
    fun getTerm(): Term {
        return term
    }

    /**
     * Returns the [TermStates] passed to the constructor, or null if it was not passed.
     *
     * @lucene.experimental
     */
    fun getTermStates(): TermStates? {
        return termStates
    }

    override fun getField(): String {
        return term.field()
    }

    override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): SpanWeight {
        val context: TermStates
        val topContext = searcher.topReaderContext
        if (termStates == null || topContext == null || !termStates.wasBuiltFor(topContext)) {
            context = TermStates.build(searcher, term, scoreMode.needsScores())
        } else {
            context = termStates
        }
        return SpanTermWeight(
            context,
            searcher,
            if (scoreMode.needsScores()) mapOf(term to context) else null,
            boost,
        )
    }

    override fun visit(visitor: QueryVisitor) {
        if (visitor.acceptField(term.field())) {
            visitor.consumeTerms(this, term)
        }
    }

    /**
     * Creates SpanTermQuery scorer instances
     *
     * @lucene.internal
     */
    inner class SpanTermWeight(
        val termStates: TermStates,
        searcher: IndexSearcher,
        terms: Map<Term, TermStates>?,
        boost: Float,
    ) : SpanWeight(this@SpanTermQuery, searcher, terms, boost) {
        init {
            checkNotNull(termStates) { "TermStates must not be null" }
        }

        override fun isCacheable(ctx: LeafReaderContext): Boolean {
            return true
        }

        override fun extractTermStates(contexts: MutableMap<Term, TermStates>) {
            contexts[term] = termStates
        }

        @Throws(IOException::class)
        override fun getSpans(ctx: LeafReaderContext, requiredPostings: Postings): Spans? {
            check(termStates.wasBuiltFor(ReaderUtil.getTopLevelContext(ctx))) {
                "The top-reader used to create Weight is not the same as the current reader's top-reader (${ReaderUtil.getTopLevelContext(ctx)})"
            }

            val supplier: IOSupplier<TermState?> = termStates.get(ctx) ?: return null
            val state: TermState? = supplier.get()
            if (state == null) {
                return null
            }
            val terms: Terms = ctx.reader().terms(term.field()) ?: return null
            if (!terms.hasPositions()) {
                throw IllegalStateException(
                    "field \"${term.field()}\" was indexed without position data; cannot run SpanTermQuery (term=${term.text()})"
                )
            }
            val termsEnum: TermsEnum = terms.iterator()
            termsEnum.seekExact(term.bytes(), state)
            val postings = termsEnum.postings(null, requiredPostings.getRequiredPostings())!!
            val positionsCost = termPositionsCost(termsEnum) * PHRASE_TO_SPAN_TERM_POSITIONS_COST
            return TermSpans(postings, term, positionsCost)
        }
    }

    companion object {
        /**
         * A guess of the relative cost of dealing with the term positions when using a SpanNearQuery
         * instead of a PhraseQuery.
         */
        private const val PHRASE_TO_SPAN_TERM_POSITIONS_COST = 4.0f
        private const val TERM_POSNS_SEEK_OPS_PER_DOC = 128
        private const val TERM_OPS_PER_POS = 7

        /**
         * Returns an expected cost in simple operations of processing the occurrences of a term in a
         * document that contains the term.
         */
        @Throws(IOException::class)
        internal fun termPositionsCost(termsEnum: TermsEnum): Float {
            val docFreq = termsEnum.docFreq()
            assert(docFreq > 0)
            val totalTermFreq = termsEnum.totalTermFreq()
            assert(totalTermFreq > 0)
            val expOccurrencesInMatchingDoc = totalTermFreq.toFloat() / docFreq.toFloat()
            return TERM_POSNS_SEEK_OPS_PER_DOC + expOccurrencesInMatchingDoc * TERM_OPS_PER_POS
        }
    }

    override fun toString(field: String?): String {
        return if (term.field() == field) {
            term.text()
        } else {
            term.toString()
        }
    }

    override fun hashCode(): Int {
        return classHash() xor term.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return sameClassAs(other) && term == (other as SpanTermQuery).term
    }
}
