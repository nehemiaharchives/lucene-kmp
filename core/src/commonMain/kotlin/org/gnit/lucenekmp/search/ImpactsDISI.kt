package org.gnit.lucenekmp.search

import kotlinx.io.IOException


/**
 * [DocIdSetIterator] that skips non-competitive docs thanks to the indexed impacts. Call
 * [.setMinCompetitiveScore] in order to give this iterator the ability to skip
 * low-scoring documents.
 *
 * @lucene.internal
 */
class ImpactsDISI(private val `in`: DocIdSetIterator, maxScoreCache: MaxScoreCache) : DocIdSetIterator() {
    private val maxScoreCache: MaxScoreCache
    private var minCompetitiveScore = 0f
    private var upTo = NO_MORE_DOCS
    private var maxScore = Float.Companion.MAX_VALUE

    /**
     * Sole constructor.
     *
     * @param `in` the iterator, typically an ImpactsEnum
     * @param maxScoreCache the cache of maximum scores, typically computed from the same ImpactsEnum
     */
    init {
        this.maxScoreCache = maxScoreCache
    }

    /** Get the [MaxScoreCache].  */
    fun getMaxScoreCache(): MaxScoreCache {
        return maxScoreCache
    }

    /**
     * Set the minimum competitive score.
     *
     * @see Scorer.setMinCompetitiveScore
     */
    fun setMinCompetitiveScore(minCompetitiveScore: Float) {
        require(minCompetitiveScore >= this.minCompetitiveScore)
        if (minCompetitiveScore > this.minCompetitiveScore) {
            this.minCompetitiveScore = minCompetitiveScore
            // force upTo and maxScore to be recomputed so that we will skip documents
            // if the current block of documents is not competitive - only if the min
            // competitive score actually increased
            upTo = -1
        }
    }

    @Throws(IOException::class)
    private fun advanceTarget(target: Int): Int {
        var target = target
        if (target <= upTo) {
            // we are still in the current block, which is considered competitive
            // according to impacts, no skipping
            return target
        }

        upTo = maxScoreCache.advanceShallow(target)
        maxScore = maxScoreCache.maxScoreForLevelZero

        while (true) {
            require(upTo >= target)

            if (maxScore >= minCompetitiveScore) {
                return target
            }

            if (upTo == NO_MORE_DOCS) {
                return NO_MORE_DOCS
            }

            val skipUpTo: Int = maxScoreCache.getSkipUpTo(minCompetitiveScore)
            if (skipUpTo == -1) { // no further skipping
                target = upTo + 1
            } else if (skipUpTo == NO_MORE_DOCS) {
                return NO_MORE_DOCS
            } else {
                target = skipUpTo + 1
            }
            upTo = maxScoreCache.advanceShallow(target)
            maxScore = maxScoreCache.maxScoreForLevelZero
        }
    }

    @Throws(IOException::class)
    override fun advance(target: Int): Int {
        return `in`.advance(advanceTarget(target))
    }

    @Throws(IOException::class)
    override fun nextDoc(): Int {
        val `in` = this.`in`
        if (`in`.docID() < upTo) {
            return `in`.nextDoc()
        }
        return advance(`in`.docID() + 1)
    }

    override fun docID(): Int {
        return `in`.docID()
    }

    override fun cost(): Long {
        return `in`.cost()
    }
}
