package org.gnit.lucenekmp.queries.spans

import okio.IOException
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.TermStates
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.ScoreMode

/** Keep matches that are contained within another Spans. */
class SpanWithinQuery(
    big: SpanQuery,
    little: SpanQuery,
) : SpanContainQuery(big, little) {
    /**
     * Construct a SpanWithinQuery matching spans from `little` that are inside of `big`.
     * This query has the boost of `little`. `big` and `little` must be in the same field.
     */
    override fun toString(field: String?): String {
        return toString(field, "SpanWithin")
    }

    override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): SpanWeight {
        val bigWeight = bigInternal.createWeight(searcher, scoreMode, boost)
        val littleWeight = littleInternal.createWeight(searcher, scoreMode, boost)
        return SpanWithinWeight(
            searcher,
            if (scoreMode.needsScores()) getTermStates(bigWeight, littleWeight) else null,
            bigWeight,
            littleWeight,
            boost,
        )
    }

    /**
     * Creates SpanWithinQuery scorer instances
     *
     * @lucene.internal
     */
    inner class SpanWithinWeight(
        searcher: IndexSearcher,
        terms: Map<Term, TermStates>?,
        bigWeight: SpanWeight,
        littleWeight: SpanWeight,
        boost: Float,
    ) : SpanContainWeight(searcher, terms, bigWeight, littleWeight, boost) {
        /**
         * Return spans from `little` that are contained in a spans from `big`.
         * The payload is from the spans of `little`.
         */
        @Throws(IOException::class)
        override fun getSpans(context: LeafReaderContext, requiredPostings: Postings): Spans? {
            val containerContained = prepareConjunction(context, requiredPostings) ?: return null

            val big = containerContained[0]
            val little = containerContained[1]

            return object : ContainSpans(big, little, little) {
                @Throws(IOException::class)
                override fun twoPhaseCurrentDocMatches(): Boolean {
                    oneExhaustedInCurrentDoc = false
                    while (littleSpans.nextStartPosition() != NO_MORE_POSITIONS) {
                        while (bigSpans.endPosition() < littleSpans.endPosition()) {
                            if (bigSpans.nextStartPosition() == NO_MORE_POSITIONS) {
                                oneExhaustedInCurrentDoc = true
                                return false
                            }
                        }
                        if (bigSpans.startPosition() <= littleSpans.startPosition()) {
                            atFirstInCurrentDoc = true
                            return true
                        }
                    }
                    oneExhaustedInCurrentDoc = true
                    return false
                }

                @Throws(IOException::class)
                override fun nextStartPosition(): Int {
                    if (atFirstInCurrentDoc) {
                        atFirstInCurrentDoc = false
                        return littleSpans.startPosition()
                    }
                    while (littleSpans.nextStartPosition() != NO_MORE_POSITIONS) {
                        while (bigSpans.endPosition() < littleSpans.endPosition()) {
                            if (bigSpans.nextStartPosition() == NO_MORE_POSITIONS) {
                                oneExhaustedInCurrentDoc = true
                                return NO_MORE_POSITIONS
                            }
                        }
                        if (bigSpans.startPosition() <= littleSpans.startPosition()) {
                            return littleSpans.startPosition()
                        }
                    }
                    oneExhaustedInCurrentDoc = true
                    return NO_MORE_POSITIONS
                }
            }
        }

        override fun isCacheable(ctx: LeafReaderContext): Boolean {
            return littleWeight.isCacheable(ctx) && bigWeight.isCacheable(ctx)
        }
    }

    override fun clone(): SpanContainQuery {
        return SpanWithinQuery(bigInternal, littleInternal)
    }
}
