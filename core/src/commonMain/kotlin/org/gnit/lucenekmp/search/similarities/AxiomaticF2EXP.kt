package org.gnit.lucenekmp.search.similarities

import org.gnit.lucenekmp.search.Explanation
import kotlin.math.pow

/**
 * F2EXP is defined as Sum(tfln(term_doc_freq, docLen)*IDF(term)) where IDF(t) = pow((N+1)/df(t), k)
 * N=total num of docs, df=doc freq
 *
 * @lucene.experimental
 */
class AxiomaticF2EXP : Axiomatic {
    /**
     * Constructor setting s and k, letting queryLen to default
     *
     * @param s hyperparam for the growth function
     * @param k hyperparam for the primitive weighting function
     */
    /**
     * Constructor setting s only, letting k and queryLen to default
     *
     * @param s hyperparam for the growth function
     */
    constructor(s: Float, k: Float = 0.35f) : super(s, 1, k)

    /** Default constructor  */
    constructor() : super()

    override fun toString(): String {
        return "F2EXP"
    }

    /** compute the term frequency component  */
    override fun tf(
        stats: BasicStats,
        freq: Double,
        docLen: Double
    ): Double {
        return 1.0
    }

    /** compute the document length component  */
    override fun ln(
        stats: BasicStats,
        freq: Double,
        docLen: Double
    ): Double {
        return 1.0
    }

    /** compute the mixed term frequency and document length component  */
    override fun tfln(
        stats: BasicStats,
        freq: Double,
        docLen: Double
    ): Double {
        return freq / (freq + this.s + this.s * docLen / stats.avgFieldLength)
    }

    /** compute the inverted document frequency component  */
    override fun idf(
        stats: BasicStats,
        freq: Double,
        docLen: Double
    ): Double {
        return ((stats.numberOfDocuments + 1.0) / stats.docFreq).pow(this.k.toDouble())
    }

    /** compute the gamma component  */
    override fun gamma(
        stats: BasicStats,
        freq: Double,
        docLen: Double
    ): Double {
        return 0.0
    }

    override fun tfExplain(
        stats: BasicStats,
        freq: Double,
        docLen: Double
    ): Explanation {
        return Explanation.match(
            tf(stats, freq, docLen).toFloat(),
            "tf, term frequency, equals to 1"
        )
    }

    override fun lnExplain(
        stats: BasicStats,
        freq: Double,
        docLen: Double
    ): Explanation {
        return Explanation.match(
            ln(stats, freq, docLen).toFloat(),
            "ln, document length, equals to 1"
        )
    }

    override fun tflnExplain(
        stats: BasicStats,
        freq: Double,
        docLen: Double
    ): Explanation {
        return Explanation.match(
            tfln(stats, freq, docLen).toFloat(),
            "tfln, mixed term frequency and document length, "
                    + "computed as freq / (freq + s + s * dl / avgdl) from:",
            Explanation.match(
                freq.toFloat(),
                "freq, number of occurrences of term in the document"
            ),
            Explanation.match(docLen.toFloat(), "dl, length of field"),
            Explanation.match(
                stats.avgFieldLength.toFloat(),
                "avgdl, average length of field across all documents"
            )
        )
    }

    override fun idfExplain(
        stats: BasicStats,
        freq: Double,
        docLen: Double
    ): Explanation {
        return Explanation.match(
            idf(stats, freq, docLen).toFloat(),
            "idf, inverted document frequency computed as " + "Math.pow((N + 1) / n, k) from:",
            Explanation.match(
                stats.numberOfDocuments.toFloat(), "N, total number of documents with field"
            ),
            Explanation.match(
                stats.docFreq.toFloat(),
                "n, number of documents containing term"
            )
        )
    }
}
