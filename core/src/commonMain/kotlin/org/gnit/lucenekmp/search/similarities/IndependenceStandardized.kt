package org.gnit.lucenekmp.search.similarities

import kotlin.math.sqrt

/**
 * Standardized measure of distance from independence
 *
 * Described as: "good at tasks that require high recall and high precision, especially against
 * short queries composed of a few words as in the case of Internet searches"
 *
 * @lucene.experimental
 */
class IndependenceStandardized : Independence() {

    override fun score(freq: Double, expected: Double): Double {
        return (freq - expected) / sqrt(expected)
    }

    override fun toString(): String {
        return "Standardized"
    }
}
