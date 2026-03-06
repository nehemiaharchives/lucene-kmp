package org.gnit.lucenekmp.search.similarities

import org.gnit.lucenekmp.jdkport.isFinite
import org.gnit.lucenekmp.jdkport.isNaN
import org.gnit.lucenekmp.search.CollectionStatistics
import org.gnit.lucenekmp.search.Explanation
import org.gnit.lucenekmp.search.TermStatistics
import org.gnit.lucenekmp.util.SmallFloat
import kotlin.math.ln

/**
 * BM25 Similarity. Introduced in Stephen E. Robertson, Steve Walker, Susan Jones, Micheline
 * Hancock-Beaulieu, and Mike Gatford. Okapi at TREC-3. In Proceedings of the Third **T**ext
 * **RE**trieval **C**onference (TREC 1994). Gaithersburg, USA, November 1994.
 */
class BM25Similarity(k1: Float = 1.2f, b: Float = 0.75f, discountOverlaps: Boolean = true) :
    Similarity(discountOverlaps) {
    /**
     * Returns the `k1` parameter
     *
     * @see .BM25Similarity
     */
    val k1: Float

    /**
     * Returns the `b` parameter
     *
     * @see .BM25Similarity
     */
    val b: Float

    /**
     * BM25 with these default values:
     *
     *
     *  * `k1 = 1.2`
     *  * `b = 0.75`
     *
     *
     * and the supplied parameter value:
     *
     * @param discountOverlaps True if overlap tokens (tokens with a position of increment of zero)
     * are discounted from the document's length.
     */
    constructor(discountOverlaps: Boolean) : this(1.2f, 0.75f, discountOverlaps)

    /** Implemented as `log(1 + (docCount - docFreq + 0.5)/(docFreq + 0.5))`.  */
    protected fun idf(docFreq: Long, docCount: Long): Float {
        return ln(1 + (docCount - docFreq + 0.5) / (docFreq + 0.5)).toFloat()
    }

    /** The default implementation computes the average as `sumTotalTermFreq / docCount`  */
    protected fun avgFieldLength(collectionStats: CollectionStatistics): Float {
        return (collectionStats.sumTotalTermFreq / collectionStats.docCount.toDouble()).toFloat()
    }

    /**
     * BM25 with the supplied parameter values.
     *
     * @param k1 Controls non-linear term frequency normalization (saturation).
     * @param b Controls to what degree document length normalizes tf values.
     * @param discountOverlaps True if overlap tokens (tokens with a position of increment of zero)
     * are discounted from the document's length.
     * @throws IllegalArgumentException if `k1` is infinite or negative, or if `b` is not
     * within the range `[0..1]`
     */
    /**
     * BM25 with these default values:
     *
     *
     *  * `k1 = 1.2`
     *  * `b = 0.75`
     *  * `discountOverlaps = true`
     *
     */
    /**
     * BM25 with the supplied parameter values.
     *
     * @param k1 Controls non-linear term frequency normalization (saturation).
     * @param b Controls to what degree document length normalizes tf values.
     * @throws IllegalArgumentException if `k1` is infinite or negative, or if `b` is not
     * within the range `[0..1]`
     */
    init {
        require(!(Float.isFinite(k1) == false || k1 < 0)) { "illegal k1 value: $k1, must be a non-negative finite value" }
        require(!(Float.isNaN(b) || b < 0 || b > 1)) { "illegal b value: $b, must be between 0 and 1" }
        this.k1 = k1
        this.b = b
    }

    /**
     * Computes a score factor for a simple term and returns an explanation for that score factor.
     *
     *
     * The default implementation uses:
     *
     * <pre class="prettyprint">
     * idf(docFreq, docCount);
    </pre> *
     *
     * Note that [CollectionStatistics.docCount] is used instead of [ ][org.apache.lucene.index.IndexReader.numDocs] because also [ ][TermStatistics.docFreq] is used, and when the latter is inaccurate, so is [ ][CollectionStatistics.docCount], and in the same direction. In addition, [ ][CollectionStatistics.docCount] does not skew when fields are sparse.
     *
     * @param collectionStats collection-level statistics
     * @param termStats term-level statistics for the term
     * @return an Explain object that includes both an idf score factor and an explanation for the
     * term.
     */
    fun idfExplain(
        collectionStats: CollectionStatistics,
        termStats: TermStatistics
    ): Explanation {
        val df: Long = termStats.docFreq
        val docCount: Long = collectionStats.docCount
        val idf = idf(df, docCount)
        return Explanation.match(
            idf,
            "idf, computed as log(1 + (N - n + 0.5) / (n + 0.5)) from:",
            Explanation.match(df, "n, number of documents containing term"),
            Explanation.match(docCount, "N, total number of documents with field")
        )
    }

    /**
     * Computes a score factor for a phrase.
     *
     *
     * The default implementation sums the idf factor for each term in the phrase.
     *
     * @param collectionStats collection-level statistics
     * @param termStats term-level statistics for the terms in the phrase
     * @return an Explain object that includes both an idf score factor for the phrase and an
     * explanation for each term.
     */
    fun idfExplain(
        collectionStats: CollectionStatistics,
        termStats: Array<out TermStatistics>
    ): Explanation {
        var idf = 0.0 // sum into a double before casting into a float
        val details: MutableList<Explanation> =
            ArrayList()
        for (stat in termStats) {
            val idfExplain: Explanation = idfExplain(collectionStats, stat)
            details.add(idfExplain)
            idf += idfExplain.value.toFloat().toDouble()
        }
        return Explanation.match(idf.toFloat(), "idf, sum of:", details)
    }

    override fun scorer(
        boost: Float,
        collectionStats: CollectionStatistics,
        vararg termStats: TermStatistics
    ): SimScorer {
        val idf: Explanation =
            if (termStats.size == 1)
                idfExplain(collectionStats, termStats[0])
            else
                idfExplain(collectionStats, termStats)
        val avgdl = avgFieldLength(collectionStats)

        val cache = FloatArray(256)
        for (i in cache.indices) {
            cache[i] = 1f / (k1 * ((1 - b) + b * LENGTH_TABLE[i] / avgdl))
        }
        return BM25Scorer(boost, k1, b, idf, avgdl, cache)
    }

    /** Collection statistics for the BM25 model.  */
    private class BM25Scorer(
        /** query boost  */
        private val boost: Float,
        /** k1 value for scale factor  */
        private val k1: Float,
        /** b value for length normalization impact  */
        private val b: Float,
        /** BM25's idf  */
        private val idf: Explanation,
        /** The average document length.  */
        private val avgdl: Float,
        /** precomputed norm[256] with k1 * ((1 - b) + b * dl / avgdl)  */
        private val cache: FloatArray
    ) : SimScorer() {

        /** weight (idf * boost)  */
        private val weight: Float = boost * idf.value.toFloat()

        override fun score(freq: Float, norm: Long): Float {
            // In order to guarantee monotonicity with both freq and norm without
            // promoting to doubles, we rewrite freq / (freq + norm) to
            // 1 - 1 / (1 + freq * 1/norm).
            // freq * 1/norm is guaranteed to be monotonic for both freq and norm due
            // to the fact that multiplication and division round to the nearest
            // float. And then monotonicity is preserved through composition via
            // x -> 1 + x and x -> 1 - 1/x.
            // Finally we expand weight * (1 - 1 / (1 + freq * 1/norm)) to
            // weight - weight / (1 + freq * 1/norm), which runs slightly faster.
            val normInverse = cache[(norm.toByte()).toInt() and 0xFF]
            return weight - weight / (1f + freq * normInverse)
        }

        override fun explain(freq: Explanation, norm: Long): Explanation {
            val subs: MutableList<Explanation> =
                ArrayList(explainConstantFactors())
            val tfExpl: Explanation = explainTF(freq, norm)
            subs.add(tfExpl)
            val normInverse = cache[(norm.toByte()).toInt() and 0xFF]
            // not using "product of" since the rewrite that we do in score()
            // introduces a small rounding error that CheckHits complains about
            return Explanation.match(
                weight - weight / (1f + freq.value.toFloat() * normInverse),
                "score(freq=" + freq.value + "), computed as boost * idf * tf from:",
                subs
            )
        }

        fun explainTF(freq: Explanation, norm: Long): Explanation {
            val subs: MutableList<Explanation> = ArrayList()
            subs.add(freq)
            subs.add(Explanation.match(k1, "k1, term saturation parameter"))
            val doclen = LENGTH_TABLE[(norm.toByte()).toInt() and 0xff]
            subs.add(Explanation.match(b, "b, length normalization parameter"))
            if ((norm and 0xFFL) > 39) {
                subs.add(Explanation.match(doclen, "dl, length of field (approximate)"))
            } else {
                subs.add(Explanation.match(doclen, "dl, length of field"))
            }
            subs.add(Explanation.match(avgdl, "avgdl, average length of field"))
            val normInverse = 1f / (k1 * ((1 - b) + b * doclen / avgdl))
            return Explanation.match(
                1f - 1f / (1 + freq.value.toFloat() * normInverse),
                "tf, computed as freq / (freq + k1 * (1 - b + b * dl / avgdl)) from:",
                subs
            )
        }

        fun explainConstantFactors(): MutableList<Explanation> {
            val subs: MutableList<Explanation> = ArrayList()
            // query boost
            if (boost != 1.0f) {
                subs.add(Explanation.match(boost, "boost"))
            }
            // idf
            subs.add(idf)
            return subs
        }
    }

    override fun toString(): String {
        return "BM25(k1=$k1,b=$b)"
    }

    companion object {
        /** Cache of decoded bytes.  */
        private val LENGTH_TABLE = FloatArray(256)

        init {
            for (i in 0..255) {
                LENGTH_TABLE[i] = SmallFloat.byte4ToInt(i.toByte()).toFloat()
            }
        }
    }
}
