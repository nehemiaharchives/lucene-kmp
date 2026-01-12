package org.gnit.lucenekmp.search.similarities

import org.gnit.lucenekmp.search.Explanation
import kotlin.jvm.JvmOverloads
import kotlin.math.ln


/**
 * Bayesian smoothing using Dirichlet priors as implemented in the Indri Search engine
 * (http://www.lemurproject.org/indri.php). Indri Dirichelet Smoothing!
 *
 * <pre class="prettyprint">
 * tf_E + mu*P(t|D) P(t|E)= documentLength + documentMu
 * mu*P(t|C) + tf_D where P(t|D)= doclen + mu
</pre> *
 *
 *
 * A larger value for mu, produces more smoothing. Smoothing is most important for short
 * documents where the probabilities are more granular.
 */
class IndriDirichletSimilarity : LMSimilarity {
    /** Returns the  parameter.  */
    /** The  parameter.  */
    val mu: Float

    /** Instantiates the similarity with the provided parameters.  */
    constructor(
        collectionModel: CollectionModel,
        discountOverlaps: Boolean,
        mu: Float
    ) : super(collectionModel, discountOverlaps) {
        this.mu = mu
    }

    /** Instantiates the similarity with the provided  parameter.  */
    /** Instantiates the similarity with the default  value of 2000.  */
    /** Instantiates the similarity with the default  value of 2000.  */
    @JvmOverloads
    constructor(
        collectionModel: LMSimilarity.CollectionModel = IndriCollectionModel(),
        mu: Float = 2000f
    ) : super(collectionModel) {
        this.mu = mu
    }

    /** Instantiates the similarity with the provided  parameter.  */
    constructor(mu: Float) {
        this.mu = mu
    }

    override fun score(
        stats: BasicStats,
        freq: Double,
        docLen: Double
    ): Double {
        val collectionProbability: Double =
            (stats as LMStats).collectionProbability
        val score = (freq + (mu * collectionProbability)) / (docLen + mu)
        return (ln(score))
    }

    override fun explain(
        subs: MutableList<Explanation>,
        stats: BasicStats,
        freq: Double,
        docLen: Double
    ) {
        if (stats.boost != 1.0) {
            subs.add(Explanation.match(stats.boost, "boost"))
        }

        subs.add(Explanation.match(mu, "mu"))
        val collectionProbability: Double =
            (stats as LMStats).collectionProbability
        val weightExpl: Explanation =
            Explanation.match(
                ln((freq + (mu * collectionProbability)) / (docLen + mu)).toFloat(), "term weight"
            )
        subs.add(weightExpl)
        subs.add(
            Explanation.match(
                ln(mu / (docLen + mu)).toFloat(),
                "document norm"
            )
        )
        super.explain(subs, stats, freq, docLen)
    }

    override val name: String
        get() = "IndriDirichlet(${this.mu})"

    /**
     * Models `p(w|C)` as the number of occurrences of the term in the collection, divided by
     * the total number of tokens `+ 1`.
     */
    class IndriCollectionModel
    /** Sole constructor: parameter-free  */
        : CollectionModel {
        override fun computeProbability(stats: BasicStats): Double {
            return (stats.totalTermFreq.toDouble()) / (stats.numberOfFieldTokens
                .toDouble())
        }

        override val name: String?
            get() = null
    }
}
