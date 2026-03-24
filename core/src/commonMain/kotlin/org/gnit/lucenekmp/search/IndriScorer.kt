package org.gnit.lucenekmp.search

/**
 * The Indri parent scorer that stores the boost so that IndriScorers can use the boost outside of
 * the term.
 */
abstract class IndriScorer(private val boost: Float) : Scorer() {
    override abstract fun iterator(): DocIdSetIterator

    override abstract fun getMaxScore(upTo: Int): Float

    override abstract fun score(): Float

    override abstract fun smoothingScore(docId: Int): Float

    override abstract fun docID(): Int

    fun getBoost(): Float {
        return this.boost
    }
}
