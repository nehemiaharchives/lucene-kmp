package org.gnit.lucenekmp.search.similarities

import org.gnit.lucenekmp.search.CollectionStatistics
import org.gnit.lucenekmp.search.Explanation
import org.gnit.lucenekmp.search.TermStatistics
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Historical scoring implementation, based on TF-IDF.
 */
class ClassicSimilarity(discountOverlaps: Boolean = true) : TFIDFSimilarity(discountOverlaps) {

    override fun lengthNorm(numTerms: Int): Float {
        return (1.0 / sqrt(numTerms.toDouble())).toFloat()
    }

    override fun tf(freq: Float): Float {
        return sqrt(freq.toDouble()).toFloat()
    }

    override fun idfExplain(collectionStats: CollectionStatistics, termStats: TermStatistics): Explanation {
        val df = termStats.docFreq
        val docCount = collectionStats.docCount
        val idf = idf(df, docCount)
        return Explanation.match(
            idf,
            "idf, computed as log((docCount+1)/(docFreq+1)) + 1 from:",
            Explanation.match(df.toFloat(), "docFreq, number of documents containing term"),
            Explanation.match(docCount.toFloat(), "docCount, total number of documents with field")
        )
    }

    override fun idf(docFreq: Long, docCount: Long): Float {
        return (ln((docCount + 1.0) / (docFreq + 1.0)) + 1.0).toFloat()
    }

    override fun toString(): String {
        return "ClassicSimilarity"
    }
}

