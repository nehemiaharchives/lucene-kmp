package org.gnit.lucenekmp.queries.spans

import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.TermStates
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.ScoreMode

/** Base class for span-based queries. */
abstract class SpanQuery : Query() {
    /** Returns the name of the field matched by this query. */
    abstract fun getField(): String?

    abstract override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): SpanWeight

    companion object {
        /**
         * Build a map of terms to [TermStates], for use in constructing SpanWeights.
         *
         * @lucene.internal
         */
        fun getTermStates(vararg weights: SpanWeight): Map<Term, TermStates> {
            val terms = mutableMapOf<Term, TermStates>()
            for (w in weights) {
                w.extractTermStates(terms)
            }
            return terms
        }

        /**
         * Build a map of terms to [TermStates], for use in constructing SpanWeights.
         *
         * @lucene.internal
         */
        fun getTermStates(weights: Collection<SpanWeight>): Map<Term, TermStates> {
            val terms = mutableMapOf<Term, TermStates>()
            for (w in weights) {
                w.extractTermStates(terms)
            }
            return terms
        }
    }
}
