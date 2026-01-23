package org.gnit.lucenekmp.search.similarities

import org.gnit.lucenekmp.search.Explanation

/**
 * F1LOG is defined as Sum(tf(term_doc_freq)*ln(docLen)*IDF(term)) where IDF(t) = ln((N+1)/df(t))
 * N=total num of docs, df=doc freq
 *
 * @lucene.experimental
 */
class AxiomaticF1LOG : Axiomatic {
    /**
     * Constructor setting s only, letting k and queryLen to default
     *
     * @param s hyperparam for the growth function
     */
    constructor(s: Float) : super(s)

    /** Default constructor  */
    constructor() : super()

    override fun toString(): String {
        return "F1LOG"
    }

    /** compute the term frequency component  */
    override fun tf(
        stats: BasicStats,
        freq: Double,
        docLen: Double
    ): Double {
        var freq = freq
        freq += 1.0 // otherwise gives negative scores for freqs < 1
        return 1 + kotlin.math.ln(1 + kotlin.math.ln(freq))
    }

    /** compute the document length component  */
    override fun ln(
        stats: BasicStats,
        freq: Double,
        docLen: Double
    ): Double {
        return (stats.avgFieldLength + this.s) / (stats.avgFieldLength + docLen * this.s)
    }

    /** compute the mixed term frequency and document length component  */
    override fun tfln(
        stats: BasicStats,
        freq: Double,
        docLen: Double
    ): Double {
        return 1.0
    }

    /** compute the inverted document frequency component  */
    override fun idf(
        stats: BasicStats,
        freq: Double,
        docLen: Double
    ): Double {
        return kotlin.math.ln((stats.numberOfDocuments + 1.0) / stats.docFreq)
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
            "tf, term frequency computed as 1 + log(1 + log(freq)) from:",
            Explanation.match(
                freq.toFloat(),
                "freq, number of occurrences of term in the document"
            )
        )
    }

    override fun lnExplain(
        stats: BasicStats,
        freq: Double,
        docLen: Double
    ): Explanation {
        return Explanation.match(
            ln(stats, freq, docLen).toFloat(),
            "ln, document length computed as (avgdl + s) / (avgdl + dl * s) from:",
            Explanation.match(
                stats.avgFieldLength.toFloat(),
                "avgdl, average length of field across all documents"
            ),
            Explanation.match(docLen.toFloat(), "dl, length of field")
        )
    }

    override fun tflnExplain(
        stats: BasicStats,
        freq: Double,
        docLen: Double
    ): Explanation {
        return Explanation.match(
            tfln(stats, freq, docLen).toFloat(),
            "tfln, mixed term frequency and document length, equals to 1"
        )
    }

    override fun idfExplain(
        stats: BasicStats,
        freq: Double,
        docLen: Double
    ): Explanation {
        return Explanation.match(
            idf(stats, freq, docLen).toFloat(),
            "idf, inverted document frequency computed as log((N + 1) / n) from:",
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
