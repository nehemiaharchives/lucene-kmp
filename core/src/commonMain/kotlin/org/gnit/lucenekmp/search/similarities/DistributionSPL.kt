package org.gnit.lucenekmp.search.similarities

import org.gnit.lucenekmp.jdkport.Math
import kotlin.math.ln
import kotlin.math.pow
import org.gnit.lucenekmp.jdkport.assert

/**
 * The smoothed power-law (SPL) distribution for the information-based framework that is described
 * in the original paper.
 *
 * Unlike for DFR, the natural logarithm is used, as it is faster to compute and the original
 * paper does not express any preference to a specific base. WARNING: this model currently returns
 * infinite scores for very small tf values and negative scores for very large tf values
 *
 * @lucene.experimental
 */
class DistributionSPL : Distribution() {

    override fun score(stats: BasicStats, tfn: Double, lambda: Double): Double {
        assert(lambda != 1.0)

        // tfn/(tfn+1) -> 1 - 1/(tfn+1), guaranteed to be non decreasing when tfn increases
        var q = 1 - 1 / (tfn + 1)
        if (q == 1.0) {
            q = Math.nextDown(1.0)
        }

        var pow = lambda.pow(q)
        if (pow == lambda) {
            // this can happen because of floating-point rounding
            // but then we return infinity when taking the log, so we enforce
            // that pow is different from lambda
            if (lambda < 1) {
                // x^y > x when x < 1 and y < 1
                pow = Math.nextUp(lambda)
            } else {
                // x^y < x when x > 1 and y < 1
                pow = Math.nextDown(lambda)
            }
        }

        return -ln((pow - lambda) / (1 - lambda))
    }

    override fun toString(): String {
        return "SPL"
    }
}
