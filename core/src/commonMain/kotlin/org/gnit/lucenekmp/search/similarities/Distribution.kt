package org.gnit.lucenekmp.search.similarities

import org.gnit.lucenekmp.search.Explanation

/**
 * The probabilistic distribution used to model term occurrence in information-based models.
 *
 * @see IBSimilarity
 *
 * @lucene.experimental
 */
abstract class Distribution {

    /** Computes the score. */
    abstract fun score(stats: BasicStats, tfn: Double, lambda: Double): Double

    /**
     * Explains the score. Returns the name of the model only, since both `tfn` and `
     * lambda` are explained elsewhere.
     */
    fun explain(stats: BasicStats, tfn: Double, lambda: Double): Explanation {
        return Explanation.match(score(stats, tfn, lambda).toFloat(), this::class.simpleName ?: "")
    }

    /** Subclasses must override this method to return the name of the distribution. */
    override abstract fun toString(): String
}
