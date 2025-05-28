package org.gnit.lucenekmp.search

import okio.IOException


/**
 * [ScorerSupplier] that matches all docs.
 *
 * @lucene.internal
 */
class MatchAllScorerSupplier
/** Sole constructor  */(private val score: Float, private val scoreMode: ScoreMode, private val maxDoc: Int) :
    ScorerSupplier() {
    @Throws(IOException::class)
    override fun get(leadCost: Long): Scorer {
        return ConstantScoreScorer(score, scoreMode, DocIdSetIterator.all(maxDoc))
    }

    @Throws(IOException::class)
    override fun bulkScorer(): BulkScorer? {
        if (maxDoc >= DenseConjunctionBulkScorer.WINDOW_SIZE / 2) {
            return DenseConjunctionBulkScorer(mutableListOf(), maxDoc, score)
        } else {
            return super.bulkScorer()
        }
    }

    override fun cost(): Long {
        return maxDoc.toLong()
    }
}
