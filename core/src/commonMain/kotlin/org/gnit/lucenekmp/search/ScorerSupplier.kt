package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.search.Weight.DefaultBulkScorer

/**
 * A supplier of [Scorer]. This allows to get an estimate of the cost before building the
 * [Scorer].
 */
abstract class ScorerSupplier {
    /**
     * Get the [Scorer]. This may not return `null` and must be called at most once.
     *
     * @param leadCost Cost of the scorer that will be used in order to lead iteration. This can be
     * interpreted as an upper bound of the number of times that [DocIdSetIterator.nextDoc],
     * [DocIdSetIterator.advance] and [TwoPhaseIterator.matches] will be called. Under
     * doubt, pass [Long.MAX_VALUE], which will produce a [Scorer] that has good
     * iteration capabilities.
     */
    @Throws(IOException::class)
    abstract fun get(leadCost: Long): Scorer

    /**
     * Optional method: Get a scorer that is optimized for bulk-scoring. The default implementation
     * iterates matches from the [Scorer] but some queries can have more efficient approaches
     * for matching all hits.
     */
    @Throws(IOException::class)
    open fun bulkScorer(): BulkScorer? {
        return DefaultBulkScorer(get(Long.Companion.MAX_VALUE))
    }

    /**
     * Get an estimate of the [Scorer] that would be returned by [.get]. This may be a
     * costly operation, so it should only be called if necessary.
     *
     * @see DocIdSetIterator.cost
     */
    abstract fun cost(): Long

    /**
     * Inform this [ScorerSupplier] that its returned scorers produce scores that get passed to
     * the collector, as opposed to partial scores that then need to get combined (e.g. summed up).
     * Note that this method also gets called if scores are not requested, e.g. because the score mode
     * is [ScoreMode.COMPLETE_NO_SCORES], so implementations should look at both the score mode
     * and this boolean to know whether to prepare for reacting to [ ][Scorer.setMinCompetitiveScore] calls.
     */
    open fun setTopLevelScoringClause() {}
}
