package org.gnit.lucenekmp.search.similarities

/**
 * Saturated measure of distance from independence
 *
 * Described as: "for tasks that require high recall against long queries"
 *
 * @lucene.experimental
 */
class IndependenceSaturated : Independence() {

    override fun score(freq: Double, expected: Double): Double {
        return (freq - expected) / expected
    }

    override fun toString(): String {
        return "Saturated"
    }
}
