package org.gnit.lucenekmp.search

import kotlinx.io.IOException
import org.gnit.lucenekmp.index.Impact
import org.gnit.lucenekmp.index.Impacts
import org.gnit.lucenekmp.index.ImpactsSource
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.search.similarities.Similarity.SimScorer
import org.gnit.lucenekmp.util.ArrayUtil


/**
 * Compute maximum scores based on [Impacts] and keep them in a cache in order not to run
 * expensive similarity score computations multiple times on the same data.
 *
 * @lucene.internal
 */
class MaxScoreCache(impactsSource: ImpactsSource, scorer: SimScorer) {
    private val impactsSource: ImpactsSource
    private val scorer: SimScorer
    private val globalMaxScore: Float
    private var maxScoreCache: FloatArray
    private var maxScoreCacheUpTo: IntArray

    /** Sole constructor.  */
    init {
        this.impactsSource = impactsSource
        this.scorer = scorer
        this.globalMaxScore = scorer.score(Float.Companion.MAX_VALUE, 1L)
        maxScoreCache = FloatArray(0)
        maxScoreCacheUpTo = IntArray(0)
    }

    /**
     * Implement the contract of [Scorer.advanceShallow] based on the wrapped [ ].
     *
     * @see Scorer.advanceShallow
     */
    @Throws(IOException::class)
    fun advanceShallow(target: Int): Int {
        impactsSource.advanceShallow(target)
        val impacts: Impacts = impactsSource.impacts
        return impacts.getDocIdUpTo(0)
    }

    private fun ensureCacheSize(size: Int) {
        if (maxScoreCache.size < size) {
            val oldLength = maxScoreCache.size
            maxScoreCache = ArrayUtil.grow(maxScoreCache, size)
            maxScoreCacheUpTo = ArrayUtil.growExact(maxScoreCacheUpTo, maxScoreCache.size)
            Arrays.fill(maxScoreCacheUpTo, oldLength, maxScoreCacheUpTo.size, -1)
        }
    }

    private fun computeMaxScore(impacts: MutableList<Impact>): Float {
        var maxScore = 0f
        val scorer: SimScorer = this.scorer
        var i = 0
        val length = impacts.size
        while (i < length) {
            val impact: Impact = impacts[i]
            maxScore = kotlin.math.max(scorer.score(impact.freq.toFloat(), impact.norm), maxScore)
            i++
        }
        return maxScore
    }

    /**
     * Return the maximum score up to upTo included.
     *
     * @see Scorer.getMaxScore
     */
    @Throws(IOException::class)
    fun getMaxScore(upTo: Int): Float {
        val level = getLevel(upTo)
        if (level == -1) {
            return globalMaxScore
        }
        return getMaxScoreForLevel(level)
    }

    /**
     * Return the first level that includes all doc IDs up to `upTo`, or -1 if there is no such
     * level.
     */
    @Throws(IOException::class)
    private fun getLevel(upTo: Int): Int {
        val impacts: Impacts = impactsSource.impacts
        var level = 0
        val numLevels: Int = impacts.numLevels()
        while (level < numLevels) {
            val impactsUpTo: Int = impacts.getDocIdUpTo(level)
            if (upTo <= impactsUpTo) {
                return level
            }
            ++level
        }
        return -1
    }

    @get:Throws(IOException::class)
    val maxScoreForLevelZero: Float
        get() = getMaxScoreForLevel(0)

    /** Return the maximum score for the given `level`.  */
    @Throws(IOException::class)
    private fun getMaxScoreForLevel(level: Int): Float {
        require(level >= 0) { "level must not be a negative integer; got $level" }
        val impacts: Impacts = impactsSource.impacts
        ensureCacheSize(level + 1)
        val levelUpTo: Int = impacts.getDocIdUpTo(level)
        if (maxScoreCacheUpTo[level] < levelUpTo) {
            maxScoreCache[level] = computeMaxScore(impacts.getImpacts(level))
            maxScoreCacheUpTo[level] = levelUpTo
        }
        return maxScoreCache[level]
    }

    /** Return the maximum level at which scores are all less than `minScore`, or -1 if none.  */
    @Throws(IOException::class)
    private fun getSkipLevel(impacts: Impacts, minScore: Float): Int {
        val numLevels: Int = impacts.numLevels()
        for (level in 0..<numLevels) {
            if (getMaxScoreForLevel(level) >= minScore) {
                return level - 1
            }
        }
        return numLevels - 1
    }

    /**
     * Return the an inclusive upper bound of documents that all have a score that is less than `minScore`, or `-1` if the current document may be competitive.
     */
    @Throws(IOException::class)
    fun getSkipUpTo(minScore: Float): Int {
        val impacts: Impacts = impactsSource.impacts
        val level = getSkipLevel(impacts, minScore)
        if (level == -1) {
            return -1
        }
        return impacts.getDocIdUpTo(level)
    }
}
