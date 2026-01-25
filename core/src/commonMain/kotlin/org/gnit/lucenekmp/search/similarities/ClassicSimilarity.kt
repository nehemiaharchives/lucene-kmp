package org.gnit.lucenekmp.search.similarities

import org.gnit.lucenekmp.search.CollectionStatistics
import org.gnit.lucenekmp.search.Explanation
import org.gnit.lucenekmp.search.TermStatistics
import kotlin.math.ln
import kotlin.math.sqrt


/**
 * Expert: Historical scoring implementation. You might want to consider using [ ] instead, which is generally considered superior to TF-IDF.
 */
class ClassicSimilarity : TFIDFSimilarity {
    /** Default constructor: parameter-free  */
    constructor() : super()

    /** Primary constructor.  */
    constructor(discountOverlaps: Boolean) : super(discountOverlaps)

    /**
     * Implemented as `1/sqrt(length)`.
     *
     * @lucene.experimental
     */
    override fun lengthNorm(numTerms: Int): Float {
        return (1.0 / sqrt(numTerms.toDouble())).toFloat()
    }

    /** Implemented as `sqrt(freq)`.  */
    override fun tf(freq: Float): Float {
        return sqrt(freq.toDouble()).toFloat()
    }

    override fun idfExplain(
        collectionStats: CollectionStatistics,
        termStats: TermStatistics
    ): Explanation {
        val df: Long = termStats.docFreq
        val docCount: Long = collectionStats.docCount
        val idf = idf(df, docCount)
        return Explanation.match(
            idf,
            "idf, computed as log((docCount+1)/(docFreq+1)) + 1 from:",
            Explanation.match(
                df,
                "docFreq, number of documents containing term"
            ),
            Explanation.match(
                docCount,
                "docCount, total number of documents with field"
            )
        )
    }

    /** Implemented as `log((docCount+1)/(docFreq+1)) + 1`.  */
    override fun idf(docFreq: Long, docCount: Long): Float {
        return (ln((docCount + 1) / (docFreq + 1).toDouble()) + 1.0).toFloat()
    }

    override fun toString(): String {
        return "ClassicSimilarity"
    }
}
