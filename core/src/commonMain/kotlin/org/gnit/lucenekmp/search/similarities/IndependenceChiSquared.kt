package org.gnit.lucenekmp.search.similarities

/**
 * Normalized chi-squared measure of distance from independence
 *
 * Described as: "can be used for tasks that require high precision, against both short and long
 * queries."
 *
 * @lucene.experimental
 */
class IndependenceChiSquared : Independence() {

    override fun score(freq: Double, expected: Double): Double {
        return (freq - expected) * (freq - expected) / expected
    }

    override fun toString(): String {
        return "ChiSquared"
    }
}
