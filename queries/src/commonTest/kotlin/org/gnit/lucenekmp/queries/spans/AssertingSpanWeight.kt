package org.gnit.lucenekmp.queries.spans

import okio.IOException
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.TermStates
import org.gnit.lucenekmp.search.Explanation
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.ScorerSupplier

/** Wraps a SpanWeight with additional asserts */
class AssertingSpanWeight(searcher: IndexSearcher, val `in`: SpanWeight) :
    SpanWeight(`in`.query as SpanQuery, searcher, null, 1f) {
    override fun extractTermStates(contexts: MutableMap<Term, TermStates>) {
        `in`.extractTermStates(contexts)
    }

    @Throws(IOException::class)
    override fun getSpans(context: LeafReaderContext, requiredPostings: Postings): Spans? {
        val spans = `in`.getSpans(context, requiredPostings) ?: return null
        return AssertingSpans(spans)
    }

    @Throws(IOException::class)
    override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
        return `in`.scorerSupplier(context)
    }

    override fun isCacheable(ctx: LeafReaderContext): Boolean {
        return `in`.isCacheable(ctx)
    }

    @Throws(IOException::class)
    override fun explain(context: LeafReaderContext, doc: Int): Explanation {
        return `in`.explain(context, doc)
    }
}
