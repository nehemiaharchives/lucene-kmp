package org.gnit.lucenekmp.search.similarities

import kotlin.math.ln

/**
 * Log-logistic distribution.
 *
 * Unlike for DFR, the natural logarithm is used, as it is faster to compute and the original
 * paper does not express any preference to a specific base.
 *
 * @lucene.experimental
 */
class DistributionLL : Distribution() {

    override fun score(stats: BasicStats, tfn: Double, lambda: Double): Double {
        return -ln(lambda / (tfn + lambda))
    }

    override fun toString(): String {
        return "LL"
    }
}
