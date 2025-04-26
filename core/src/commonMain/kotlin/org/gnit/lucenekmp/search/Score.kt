package org.gnit.lucenekmp.search

/**
 * Used by [BulkScorer]s that need to pass a [Scorable] to [ ][LeafCollector.setScorer].
 */
internal class Score : Scorable() {
    var score: Float = 0f

    override fun score(): Float {
        return score
    }
}
