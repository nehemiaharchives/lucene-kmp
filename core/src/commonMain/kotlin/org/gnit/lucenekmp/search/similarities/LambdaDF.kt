package org.gnit.lucenekmp.search.similarities

import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.search.Explanation

/**
 * Computes lambda as `docFreq+1 / numberOfDocuments+1`.
 *
 * @lucene.experimental
 */
class LambdaDF : Lambda() {

    override fun lambda(stats: BasicStats): Float {
        var lambda = ((stats.docFreq + 1.0) / (stats.numberOfDocuments + 1.0)).toFloat()
        if (lambda == 1f) {
            // Distribution SPL cannot work with values of lambda that are equal to 1
            lambda = Math.nextDown(lambda)
        }
        return lambda
    }

    override fun explain(stats: BasicStats): Explanation {
        return Explanation.match(
            lambda(stats),
            "${this::class.simpleName}, computed as (n + 1) / (N + 1) from:",
            Explanation.match(stats.docFreq.toFloat(), "n, number of documents containing term"),
            Explanation.match(stats.numberOfDocuments.toFloat(), "N, total number of documents with field"),
        )
    }

    override fun toString(): String {
        return "D"
    }
}
