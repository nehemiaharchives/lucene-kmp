package org.gnit.lucenekmp.queries.spans

import okio.IOException
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.TermStates
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.ScoreMode
import org.gnit.lucenekmp.search.ScorerSupplier

/** Keep matches that contain another SpanScorer. */
class SpanContainingQuery(
    big: SpanQuery,
    little: SpanQuery,
) : SpanContainQuery(big, little) {
    /**
     * Construct a SpanContainingQuery matching spans from `big` that contain at least one
     * spans from `little`. This query has the boost of `big`. `big`
     * and `little` must be in the same field.
     */
    override fun toString(field: String?): String {
        return toString(field, "SpanContaining")
    }

    override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): SpanWeight {
        val bigWeight = bigInternal.createWeight(searcher, scoreMode, boost)
        val littleWeight = littleInternal.createWeight(searcher, scoreMode, boost)
        return SpanContainingWeight(
            searcher,
            if (scoreMode.needsScores()) getTermStates(bigWeight, littleWeight) else null,
            bigWeight,
            littleWeight,
            boost,
        )
    }

    /**
     * Creates SpanContainingQuery scorer instances
     *
     * @lucene.internal
     */
    inner class SpanContainingWeight(
        searcher: IndexSearcher,
        terms: Map<Term, TermStates>?,
        bigWeight: SpanWeight,
        littleWeight: SpanWeight,
        boost: Float,
    ) : SpanContainWeight(searcher, terms, bigWeight, littleWeight, boost) {
        /**
         * Return spans from `big` that contain at least one spans from `little`.
         * The payload is from the spans of `big`.
         */
        @Throws(IOException::class)
        override fun getSpans(context: LeafReaderContext, requiredPostings: Postings): Spans? {
            val containerContained = prepareConjunction(context, requiredPostings) ?: return null

            val big = containerContained[0]
            val little = containerContained[1]

            return object : ContainSpans(big, little, big) {
                @Throws(IOException::class)
                override fun twoPhaseCurrentDocMatches(): Boolean {
                    oneExhaustedInCurrentDoc = false
                    while (bigSpans.nextStartPosition() != NO_MORE_POSITIONS) {
                        while (littleSpans.startPosition() < bigSpans.startPosition()) {
                            if (littleSpans.nextStartPosition() == NO_MORE_POSITIONS) {
                                oneExhaustedInCurrentDoc = true
                                return false
                            }
                        }
                        if (bigSpans.endPosition() >= littleSpans.endPosition()) {
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
                        return bigSpans.startPosition()
                    }
                    while (bigSpans.nextStartPosition() != NO_MORE_POSITIONS) {
                        while (littleSpans.startPosition() < bigSpans.startPosition()) {
                            if (littleSpans.nextStartPosition() == NO_MORE_POSITIONS) {
                                oneExhaustedInCurrentDoc = true
                                return NO_MORE_POSITIONS
                            }
                        }
                        if (bigSpans.endPosition() >= littleSpans.endPosition()) {
                            return bigSpans.startPosition()
                        }
                    }
                    oneExhaustedInCurrentDoc = true
                    return NO_MORE_POSITIONS
                }
            }
        }

        override fun isCacheable(ctx: LeafReaderContext): Boolean {
            return bigWeight.isCacheable(ctx) && littleWeight.isCacheable(ctx)
        }

        @Throws(IOException::class)
        override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
            val spans = getSpans(context, Postings.POSITIONS) ?: return null
            val norms = if (field == null) null else context.reader().getNormValues(field)
            val scorer = SpanScorer(spans, getSimScorer(), norms)
            return DefaultScorerSupplier(scorer)
        }
    }

    override fun clone(): SpanContainQuery {
        return SpanContainingQuery(bigInternal, littleInternal)
    }
}
