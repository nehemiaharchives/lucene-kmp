package org.gnit.lucenekmp.search

import kotlinx.io.IOException


/** Simplest implementation of [Scorable], implemented via simple getters and setters.  */
internal class SimpleScorable
/** Sole constructor.  */
    : Scorable() {
    var score: Float = 0f
    var minCompetitiveScore: Float = 0f

    override fun score(): Float {
        return score
    }

    /** Set the score.  */
    fun setScore(score: Float) {
        this.score = score
    }

    /** Get the min competitive score.  */
    fun minCompetitiveScore(): Float {
        return minCompetitiveScore
    }

    @Throws(IOException::class)
    override fun setMinCompetitiveScore(minScore: Float) {
        minCompetitiveScore = minScore
    }
}
