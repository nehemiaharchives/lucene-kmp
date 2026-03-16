package org.gnit.lucenekmp.queries.spans

import okio.IOException
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.TermStates
import org.gnit.lucenekmp.jdkport.Cloneable
import org.gnit.lucenekmp.search.BooleanClause
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.QueryVisitor
import org.gnit.lucenekmp.search.ScoreMode

/** Base class for filtering a SpanQuery based on the position of a match. */
abstract class SpanPositionCheckQuery(
    protected var matchInternal: SpanQuery,
) : SpanQuery(), Cloneable<SpanPositionCheckQuery> {
    init {
        this.matchInternal = requireNotNull(matchInternal)
    }

    /**
     * @return the SpanQuery whose matches are filtered.
     */
    fun getMatch(): SpanQuery {
        return matchInternal
    }

    override fun getField(): String? {
        return matchInternal.getField()
    }

    /**
     * Implementing classes are required to return whether the current position is a match for the
     * passed in "match" [SpanQuery].
     *
     * <p>This is only called if the underlying last [Spans.nextStartPosition] for the match
     * indicated a valid start position.
     *
     * @param spans The [Spans] instance, positioned at the spot to check
     * @return whether the match is accepted, rejected, or rejected and should move to the next doc.
        * @see Spans.nextDoc
     */
    @Throws(IOException::class)
    protected abstract fun acceptPosition(spans: Spans): FilterSpans.AcceptStatus

    override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): SpanWeight {
        val matchWeight = matchInternal.createWeight(searcher, scoreMode, boost)
        return SpanPositionCheckWeight(
            matchWeight,
            searcher,
            if (scoreMode.needsScores()) getTermStates(matchWeight) else null,
            boost,
        )
    }

    /**
     * Creates SpanPositionCheckQuery scorer instances
     *
     * @lucene.internal
     */
    inner class SpanPositionCheckWeight(
        val matchWeight: SpanWeight,
        searcher: IndexSearcher,
        terms: Map<Term, TermStates>?,
        boost: Float,
    ) : SpanWeight(this@SpanPositionCheckQuery, searcher, terms, boost) {
        override fun isCacheable(ctx: LeafReaderContext): Boolean {
            return matchWeight.isCacheable(ctx)
        }

        override fun extractTermStates(contexts: MutableMap<Term, TermStates>) {
            matchWeight.extractTermStates(contexts)
        }

        @Throws(IOException::class)
        override fun getSpans(context: LeafReaderContext, requiredPostings: Postings): Spans? {
            val matchSpans = matchWeight.getSpans(context, requiredPostings)
            return if (matchSpans == null) {
                null
            } else {
                object : FilterSpans(matchSpans) {
                    @Throws(IOException::class)
                    override fun accept(candidate: Spans): AcceptStatus {
                        return acceptPosition(candidate)
                    }
                }
            }
        }
    }

    override fun rewrite(indexSearcher: IndexSearcher): Query {
        val rewritten = matchInternal.rewrite(indexSearcher) as SpanQuery
        if (rewritten !== matchInternal) {
            val clone = clone()
            clone.matchInternal = rewritten
            return clone
        }
        return super.rewrite(indexSearcher)
    }

    override fun visit(visitor: QueryVisitor) {
        if (visitor.acceptField(getField())) {
            matchInternal.visit(visitor.getSubVisitor(BooleanClause.Occur.MUST, this))
        }
    }

    /** Returns true iff `other` is equal to this. */
    override fun equals(other: Any?): Boolean {
        return sameClassAs(other) && matchInternal == (other as SpanPositionCheckQuery).matchInternal
    }

    override fun hashCode(): Int {
        return classHash() xor matchInternal.hashCode()
    }
}
