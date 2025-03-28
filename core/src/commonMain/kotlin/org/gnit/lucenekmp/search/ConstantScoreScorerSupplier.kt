package org.gnit.lucenekmp.search

import kotlinx.io.IOException
import org.gnit.lucenekmp.search.Weight.DefaultBulkScorer


/**
 * Specialization of [ScorerSupplier] for queries that produce constant scores.
 *
 * @lucene.internal
 */
abstract class ConstantScoreScorerSupplier
/** Constructor, invoked by sub-classes.  */ protected constructor(
    private val score: Float,
    private val scoreMode: ScoreMode,
    private val maxDoc: Int
) : ScorerSupplier() {
    /** Return an iterator given the cost of the leading clause.  */
    @Throws(IOException::class)
    abstract fun iterator(leadCost: Long): DocIdSetIterator

    @Throws(IOException::class)
    override fun get(leadCost: Long): Scorer {
        val iterator = iterator(leadCost)
        val twoPhase = TwoPhaseIterator.unwrap(iterator)
        if (twoPhase == null) {
            return ConstantScoreScorer(score, scoreMode, iterator)
        } else {
            return ConstantScoreScorer(score, scoreMode, twoPhase)
        }
    }

    @Throws(IOException::class)
    override fun bulkScorer(): BulkScorer {
        val iterator = iterator(Long.Companion.MAX_VALUE)
        if (maxDoc >= DenseConjunctionBulkScorer.WINDOW_SIZE / 2 && iterator.cost() >= maxDoc / DenseConjunctionBulkScorer.DENSITY_THRESHOLD_INVERSE && TwoPhaseIterator.unwrap(
                iterator
            ) == null
        ) {
            return DenseConjunctionBulkScorer(mutableListOf(iterator), maxDoc, score)
        } else {
            return DefaultBulkScorer(ConstantScoreScorer(score, scoreMode, iterator))
        }
    }

    companion object {
        /** Create a [ConstantScoreScorerSupplier] for the given iterator.  */
        fun fromIterator(
            iterator: DocIdSetIterator, score: Float, scoreMode: ScoreMode, maxDoc: Int
        ): ConstantScoreScorerSupplier {
            return object : ConstantScoreScorerSupplier(score, scoreMode, maxDoc) {
                override fun cost(): Long {
                    return iterator.cost()
                }

                @Throws(IOException::class)
                override fun iterator(leadCost: Long): DocIdSetIterator {
                    return iterator
                }
            }
        }
    }
}
