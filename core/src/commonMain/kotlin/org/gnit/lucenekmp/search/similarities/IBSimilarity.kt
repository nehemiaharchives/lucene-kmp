package org.gnit.lucenekmp.search.similarities

import org.gnit.lucenekmp.search.Explanation

/**
 * Provides a framework for the family of information-based models, as described in Stéphane
 * Clinchant and Eric Gaussier. 2010. Information-based models for ad hoc IR. In Proceeding of the
 * 33rd international ACM SIGIR conference on Research and development in information retrieval
 * (SIGIR '10). ACM, New York, NY, USA, 234-241.
 *
 * The retrieval function is of the form _RSV(q, d) = Σ -xqw log Prob(Xw ≥ tdw | λw)_, where
 *
 * - _xqw_ is the query boost;
 * - _Xw_ is a random variable that counts the occurrences of word _w_;
 * - _tdw_ is the normalized term frequency;
 * - _λw_ is a parameter.
 *
 * The framework described in the paper has many similarities to the DFR framework (see
 * [DFRSimilarity]). It is possible that the two Similarities will be merged at one point.
 *
 * To construct an IBSimilarity, you must specify the implementations for all three components of
 * the Information-Based model.
 *
 *  1. [Distribution]: Probabilistic distribution used to model term occurrence
 *     - [DistributionLL]: Log-logistic
 *     - [DistributionLL]: Smoothed power-law
 *  2. [Lambda]: λw parameter of the probability distribution
 *     - [LambdaDF]: Nw/N or average number of documents where w occurs
 *     - [LambdaTTF]: Fw/N or average number of occurrences of w in the collection
 *  3. [Normalization]: Term frequency normalization
 *     - Any supported DFR normalization (listed in [DFRSimilarity])
 *
 * @see DFRSimilarity
 *
 * @lucene.experimental
 */
class IBSimilarity(
    /** The probabilistic distribution used to model term occurrence. */
    val distribution: Distribution,
    /** The _lambda (λw)_ parameter. */
    val lambda: Lambda,
    /** The term frequency normalization. */
    val normalization: Normalization,
    discountOverlaps: Boolean = true,
) : SimilarityBase(discountOverlaps) {

    override fun score(stats: BasicStats, freq: Double, docLen: Double): Double {
        return stats.boost * distribution.score(stats, normalization.tfn(stats, freq, docLen), lambda.lambda(stats).toDouble())
    }

    override fun explain(subs: MutableList<Explanation>, stats: BasicStats, freq: Double, docLen: Double) {
        if (stats.boost != 1.0) {
            subs.add(Explanation.match(stats.boost.toFloat(), "boost, query boost"))
        }
        val normExpl = normalization.explain(stats, freq, docLen)
        val lambdaExpl = lambda.explain(stats)
        subs.add(normExpl)
        subs.add(lambdaExpl)
        subs.add(distribution.explain(stats, normExpl.value.toDouble(), lambdaExpl.value.toDouble()))
    }

    override fun explain(stats: BasicStats, freq: Explanation, docLen: Double): Explanation {
        val subs = mutableListOf<Explanation>()
        explain(subs, stats, freq.value.toDouble(), docLen)

        return Explanation.match(
            score(stats, freq.value.toDouble(), docLen).toFloat(),
            "score(${this::class.simpleName}, freq=${freq.value}), computed as boost * distribution.score(stats, normalization.tfn(stats, freq, docLen), lambda.lambda(stats)) from:",
            subs,
        )
    }

    /**
     * The name of IB methods follow the pattern `IB <distribution> <lambda><normalization>`.
     * The name of the distribution is the same as in the original paper; for the names of lambda
     * parameters, refer to the javadoc of the [Lambda] classes.
     */
    override fun toString(): String {
        return "IB $distribution-$lambda$normalization"
    }

}
