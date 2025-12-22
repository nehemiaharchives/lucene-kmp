package org.gnit.lucenekmp.search

import okio.IOException


/** Simplest implementation of [Scorable], implemented via simple getters and setters.  */
internal class SimpleScorable
/** Sole constructor.  */
    : Scorable() {
    var score: Float = 0f

    override var minCompetitiveScore: Float = 0f

    override fun score(): Float {
        return score
    }

    /** Get the min competitive score.  */
    fun minCompetitiveScore(): Float {
        return minCompetitiveScore
    }
}
