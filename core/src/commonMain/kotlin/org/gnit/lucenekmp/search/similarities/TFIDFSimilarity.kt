package org.gnit.lucenekmp.search.similarities

import org.gnit.lucenekmp.search.CollectionStatistics
import org.gnit.lucenekmp.search.Explanation
import org.gnit.lucenekmp.search.TermStatistics
import org.gnit.lucenekmp.util.SmallFloat
import kotlin.math.sqrt

/**
 * Implementation of [Similarity] with the Vector Space Model.
 *
 * TFIDFSimilarity defines the components of Lucene scoring. Overriding
 * computation of these components is a convenient way to alter Lucene scoring.
 */
abstract class TFIDFSimilarity(discountOverlaps: Boolean = true) : Similarity(discountOverlaps) {

    /** Implemented as `sqrt(freq)`. */
    abstract fun tf(freq: Float): Float

    /** Implemented as `log((docCount+1)/(docFreq+1)) + 1`. */
    abstract fun idf(docFreq: Long, docCount: Long): Float

    open fun idfExplain(collectionStats: CollectionStatistics, termStats: Array<TermStatistics>): Explanation {
        var idf = 0.0
        val subs = mutableListOf<Explanation>()
        for (stat in termStats) {
            val exp = idfExplain(collectionStats, stat)
            subs.add(exp)
            idf += exp.value.toFloat()
        }
        return Explanation.match(idf.toFloat(), "idf(), sum of:", subs)
    }

    abstract fun idfExplain(collectionStats: CollectionStatistics, termStats: TermStatistics): Explanation

    /** Implemented as `1/sqrt(length)`. */
    abstract fun lengthNorm(length: Int): Float

    override fun scorer(
        boost: Float,
        collectionStats: CollectionStatistics,
        vararg termStats: TermStatistics
    ): SimScorer {
        val idf = if (termStats.size == 1) {
            idfExplain(collectionStats, termStats[0])
        } else {
            idfExplain(collectionStats, arrayOf(*termStats))
        }
        val normTable = FloatArray(256)
        for (i in 1 until 256) {
            val norm = lengthNorm(LENGTH_TABLE[i])
            normTable[i] = norm
        }
        normTable[0] = 1f / normTable[255]
        return TFIDFScorer(boost, idf, normTable)
    }

    inner class TFIDFScorer(
        private val boost: Float,
        private val idf: Explanation,
        private val normTable: FloatArray
    ) : SimScorer() {
        private val queryWeight = boost * idf.value.toFloat()

        override fun score(freq: Float, norm: Long): Float {
            val raw = tf(freq) * queryWeight
            val normValue = normTable[(norm.toInt() and 0xFF)]
            return raw * normValue
        }

        override fun explain(freq: Explanation, norm: Long): Explanation {
            val subs = mutableListOf<Explanation>()
            if (boost != 1f) {
                subs.add(Explanation.match(boost, "boost"))
            }
            subs.add(idf)
            val tfExpl = Explanation.match(
                tf(freq.value.toFloat()),
                "tf(freq=" + freq.value + "), with freq of:",
                freq
            )
            subs.add(tfExpl)
            val normValue = normTable[(norm.toInt() and 0xFF)]
            val fieldNorm = Explanation.match(normValue, "fieldNorm")
            subs.add(fieldNorm)
            return Explanation.match(
                queryWeight * tfExpl.value.toFloat() * normValue,
                "score(freq=" + freq.value + "), product of:",
                subs
            )
        }
    }

    companion object {
        private val LENGTH_TABLE = IntArray(256)
        init {
            for (i in 0 until 256) {
                LENGTH_TABLE[i] = SmallFloat.byte4ToInt(i.toByte())
            }
        }
    }
}

