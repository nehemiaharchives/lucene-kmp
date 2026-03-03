package org.gnit.lucenekmp.search.similarities

/**
 * Computes the measure of divergence from independence for DFI scoring functions.
 *
 * See http://trec.nist.gov/pubs/trec21/papers/irra.web.nb.pdf for more information on different
 * methods.
 *
 * @lucene.experimental
 */
abstract class Independence {

    /**
     * Computes distance from independence
     *
     * @param freq actual term frequency
     * @param expected expected term frequency
     */
    abstract fun score(freq: Double, expected: Double): Double

    // subclasses must provide a name
    override abstract fun toString(): String
}
