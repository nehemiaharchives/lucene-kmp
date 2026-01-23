package org.gnit.lucenekmp.search.similarities

import org.gnit.lucenekmp.jdkport.isFinite
import org.gnit.lucenekmp.jdkport.isNaN
import org.gnit.lucenekmp.search.Explanation
import kotlin.math.max

/**
 * Axiomatic approaches for IR. From Hui Fang and Chengxiang Zhai 2005. An Exploration of Axiomatic
 * Approaches to Information Retrieval. In Proceedings of the 28th annual international ACM SIGIR
 * conference on Research and development in information retrieval (SIGIR '05). ACM, New York, NY,
 * USA, 480-487.
 *
 *
 * There are a family of models. All of them are based on BM25, Pivoted Document Length
 * Normalization and Language model with Dirichlet prior. Some components (e.g. Term Frequency,
 * Inverted Document Frequency) in the original models are modified so that they follow some
 * axiomatic constraints.
 *
 * @lucene.experimental
 */
abstract class Axiomatic(discountOverlaps: Boolean, s: Float, queryLen: Int, k: Float) : SimilarityBase(discountOverlaps) {
    /** hyperparam for the growth function  */
    protected val s: Float

    /** hyperparam for the primitive weighthing function  */
    protected val k: Float

    /** the query length  */
    protected val queryLen: Int

    /**
     * Constructor setting all Axiomatic hyperparameters and using default discountOverlaps value.
     *
     * @param s hyperparam for the growth function
     * @param queryLen the query length
     * @param k hyperparam for the primitive weighting function
     */
    /** Default constructor  */
    /**
     * Constructor setting only s, letting k and queryLen to default
     *
     * @param s hyperparam for the growth function
     */
    /**
     * Constructor setting s and queryLen, letting k to default
     *
     * @param s hyperparam for the growth function
     * @param queryLen the query length
     */
    constructor(s: Float = 0.25f, queryLen: Int = 1, k: Float = 0.35f) : this(true, s, queryLen, k)

    /**
     * Constructor setting all Axiomatic hyperparameters
     *
     * @param discountOverlaps true if overlap tokens should not impact document length for scoring.
     * @param s hyperparam for the growth function
     * @param queryLen the query length
     * @param k hyperparam for the primitive weighting function
     */
    init {
        require(!(!Float.isFinite(s) || Float.isNaN(s) || s < 0 || s > 1)) { "illegal s value: $s, must be between 0 and 1" }
        require(!(!Float.isFinite(k) || Float.isNaN(k) || k < 0 || k > 1)) { "illegal k value: $k, must be between 0 and 1" }
        require(queryLen >= 0) { "illegal query length value: $queryLen, must be larger 0" }
        this.s = s
        this.queryLen = queryLen
        this.k = k
    }

    override fun score(
        stats: BasicStats,
        freq: Double,
        docLen: Double
    ): Double {
        var score =
            ((tf(stats, freq, docLen)
                    * ln(stats, freq, docLen)
                    * tfln(stats, freq, docLen)
                    * idf(stats, freq, docLen))
                    - gamma(stats, freq, docLen))
        score *= stats.boost
        // AxiomaticF3 similarities might produce negative scores due to their gamma component
        return max(0.0, score)
    }

    override fun explain(
        stats: BasicStats,
        freq: Explanation,
        docLen: Double
    ): Explanation {
        val subs: MutableList<Explanation> = mutableListOf()
        val f = freq.value.toDouble()
        explain(subs, stats, f, docLen)

        val score =
            (tf(stats, f, docLen) * ln(stats, f, docLen) * tfln(stats, f, docLen) * idf(
                stats,
                f,
                docLen
            )
                    - gamma(stats, f, docLen))

        var explanation: Explanation =
            Explanation.match(
                score.toFloat(),
                ("score("
                        + this::class.simpleName
                        + ", freq="
                        + freq.value
                        + "), computed from:"),
                subs
            )
        if (stats.boost != 1.0) {
            explanation =
                Explanation.match(
                    (score * stats.boost).toFloat(),
                    "Boosted score, computed as (score * boost) from:",
                    explanation,
                    Explanation.match(stats.boost.toFloat(), "Query boost")
                )
        }
        if (score < 0) {
            explanation =
                Explanation.match(
                    0,
                    "max of:",
                    Explanation.match(0, "Minimum legal score"),
                    explanation
                )
        }
        return explanation
    }

    override fun explain(
        subs: MutableList<Explanation>,
        stats: BasicStats,
        freq: Double,
        docLen: Double
    ) {
        if (stats.boost != 1.0) {
            subs.add(
                Explanation.match(
                    stats.boost.toFloat(),
                    "boost, query boost"
                )
            )
        }

        subs.add(
            Explanation.match(
                this.k,
                "k, hyperparam for the primitive weighting function"
            )
        )
        subs.add(
            Explanation.match(
                this.s,
                "s, hyperparam for the growth function"
            )
        )
        subs.add(
            Explanation.match(
                this.queryLen,
                "queryLen, query length"
            )
        )
        subs.add(tfExplain(stats, freq, docLen))
        subs.add(lnExplain(stats, freq, docLen))
        subs.add(tflnExplain(stats, freq, docLen))
        subs.add(idfExplain(stats, freq, docLen))
        subs.add(
            Explanation.match(
                gamma(stats, freq, docLen).toFloat(),
                "gamma"
            )
        )
        super.explain(subs, stats, freq, docLen)
    }

    /** Name of the axiomatic method.  */
    abstract override fun toString(): String

    /** compute the term frequency component  */
    protected abstract fun tf(
        stats: BasicStats,
        freq: Double,
        docLen: Double
    ): Double

    /** compute the document length component  */
    protected abstract fun ln(
        stats: BasicStats,
        freq: Double,
        docLen: Double
    ): Double

    /** compute the mixed term frequency and document length component  */
    protected abstract fun tfln(
        stats: BasicStats,
        freq: Double,
        docLen: Double
    ): Double

    /** compute the inverted document frequency component  */
    protected abstract fun idf(
        stats: BasicStats,
        freq: Double,
        docLen: Double
    ): Double

    /** compute the gamma component (only for F3EXp and F3LOG)  */
    protected abstract fun gamma(
        stats: BasicStats,
        freq: Double,
        docLen: Double
    ): Double

    /**
     * Explain the score of the term frequency component for a single document
     *
     * @param stats the corpus level statistics
     * @param freq number of occurrences of term in the document
     * @param docLen the document length
     * @return Explanation of how the tf component was computed
     */
    protected abstract fun tfExplain(
        stats: BasicStats,
        freq: Double,
        docLen: Double
    ): Explanation

    /**
     * Explain the score of the document length component for a single document
     *
     * @param stats the corpus level statistics
     * @param freq number of occurrences of term in the document
     * @param docLen the document length
     * @return Explanation of how the ln component was computed
     */
    protected abstract fun lnExplain(
        stats: BasicStats,
        freq: Double,
        docLen: Double
    ): Explanation

    /**
     * Explain the score of the mixed term frequency and document length component for a single
     * document
     *
     * @param stats the corpus level statistics
     * @param freq number of occurrences of term in the document
     * @param docLen the document length
     * @return Explanation of how the tfln component was computed
     */
    protected abstract fun tflnExplain(
        stats: BasicStats,
        freq: Double,
        docLen: Double
    ): Explanation

    /**
     * Explain the score of the inverted document frequency component for a single document
     *
     * @param stats the corpus level statistics
     * @param freq number of occurrences of term in the document
     * @param docLen the document length
     * @return Explanation of how the idf component was computed
     */
    protected abstract fun idfExplain(
        stats: BasicStats,
        freq: Double,
        docLen: Double
    ): Explanation
}
