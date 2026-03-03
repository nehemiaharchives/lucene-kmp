package org.gnit.lucenekmp.search.similarities

import org.gnit.lucenekmp.search.Explanation

/**
 * The _lambda (λw)_ parameter in information-based models.
 *
 * @see IBSimilarity
 *
 * @lucene.experimental
 */
abstract class Lambda {

    /** Computes the lambda parameter. */
    abstract fun lambda(stats: BasicStats): Float

    /** Explains the lambda parameter. */
    abstract fun explain(stats: BasicStats): Explanation

    /**
     * Subclasses must override this method to return the code of the lambda formula. Since the
     * original paper is not very clear on this matter, and also uses the DFR naming scheme
     * incorrectly, the codes here were chosen arbitrarily.
     */
    override abstract fun toString(): String
}
