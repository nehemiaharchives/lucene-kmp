package org.gnit.lucenekmp.search.similarities

import org.gnit.lucenekmp.search.Explanation

/**
 * Implements the _Divergence from Independence (DFI)_ model based on Chi-square statistics
 * (i.e., standardized Chi-squared distance from independence in term frequency tf).
 *
 * DFI is both parameter-free and non-parametric:
 *
 * - parameter-free: it does not require any parameter tuning or training.
 * - non-parametric: it does not make any assumptions about word frequency distributions on
 *   document collections.
 *
 * It is highly recommended **not** to remove stopwords (very common terms: the, of, and, to,
 * a, in, for, is, on, that, etc) with this similarity.
 *
 * For more information see: [A nonparametric term weighting method for information retrieval
 * based on measuring the divergence from independence](http://dx.doi.org/10.1007/s10791-013-9225-4)
 *
 * @lucene.experimental
 * @see org.gnit.lucenekmp.search.similarities.IndependenceStandardized
 * @see org.gnit.lucenekmp.search.similarities.IndependenceSaturated
 * @see org.gnit.lucenekmp.search.similarities.IndependenceChiSquared
 */
class DFISimilarity(
    val independence: Independence,
    discountOverlaps: Boolean = true,
) : SimilarityBase(discountOverlaps) {

    override fun score(stats: BasicStats, freq: Double, docLen: Double): Double {
        val expected =
            (stats.totalTermFreq + 1) * docLen / (stats.numberOfFieldTokens + 1)

        // if the observed frequency is less than or equal to the expected value, then return zero.
        if (freq <= expected) return 0.0

        val measure = independence.score(freq, expected)

        return stats.boost * log2(measure + 1)
    }

    override fun explain(stats: BasicStats, freq: Explanation, docLen: Double): Explanation {
        val expected =
            (stats.totalTermFreq + 1) * docLen / (stats.numberOfFieldTokens + 1)
        if (freq.value.toDouble() <= expected) {
            return Explanation.match(
                0f,
                "score(${this::class.simpleName}, freq=${freq.value}), equals to 0",
            )
        }
        val explExpected =
            Explanation.match(
                expected.toFloat(),
                "expected, computed as (F + 1) * dl / (T + 1) from:",
                Explanation.match(
                    stats.totalTermFreq.toFloat(),
                    "F, total number of occurrences of term across all docs",
                ),
                Explanation.match(docLen.toFloat(), "dl, length of field"),
                Explanation.match(
                    stats.numberOfFieldTokens.toFloat(),
                    "T, total number of tokens in the field",
                ),
            )

        val measure = independence.score(freq.value.toDouble(), expected)
        val explMeasure =
            Explanation.match(
                measure.toFloat(),
                "measure, computed as independence.score(freq, expected) from:",
                freq,
                explExpected,
            )

        return Explanation.match(
            score(stats, freq.value.toDouble(), docLen).toFloat(),
            "score(${this::class.simpleName}, freq=${freq.value}), computed as boost * log2(measure + 1) from:",
            Explanation.match(stats.boost.toFloat(), "boost, query boost"),
            explMeasure,
        )
    }

    override fun toString(): String {
        return "DFI($independence)"
    }
}
