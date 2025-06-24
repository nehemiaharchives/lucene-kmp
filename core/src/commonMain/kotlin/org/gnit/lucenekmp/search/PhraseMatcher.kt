package org.gnit.lucenekmp.search

import okio.IOException

/**
 * Base class for exact and sloppy phrase matching
 *
 *
 * To find matches on a document, first advance [.approximation] to the relevant document,
 * then call [.reset]. Clients can then call [.nextMatch] to iterate over the
 * matches
 *
 * @lucene.internal
 */
abstract class PhraseMatcher internal constructor(
    /**
     * An estimate of the average cost of finding all matches on a document
     *
     * @see TwoPhaseIterator.matchCost
     */
    val matchCost: Float
) {
    /** Approximation that only matches documents that have all terms.  */
    abstract fun approximation(): DocIdSetIterator

    /** Approximation that is aware of impacts.  */
    abstract fun impactsApproximation(): ImpactsDISI

    /** An upper bound on the number of possible matches on this document  */
    @Throws(IOException::class)
    abstract fun maxFreq(): Float

    /** Called after [.approximation] has been advanced  */
    @Throws(IOException::class)
    abstract fun reset()

    /** Find the next match on the current document, returning `false` if there are none.  */
    @Throws(IOException::class)
    abstract fun nextMatch(): Boolean

    /**
     * The slop-adjusted weight of the current match
     *
     *
     * The sum of the slop-adjusted weights is used as the freq for scoring
     */
    abstract fun sloppyWeight(): Float

    /** The start position of the current match  */
    abstract fun startPosition(): Int

    /** The end position of the current match  */
    abstract fun endPosition(): Int

    /** The start offset of the current match  */
    @Throws(IOException::class)
    abstract fun startOffset(): Int

    /** The end offset of the current match  */
    @Throws(IOException::class)
    abstract fun endOffset(): Int
}
