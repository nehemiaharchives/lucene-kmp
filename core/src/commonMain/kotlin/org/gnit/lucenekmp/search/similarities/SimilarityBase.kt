package org.gnit.lucenekmp.search.similarities

import org.gnit.lucenekmp.search.similarities.MultiSimilarity.MultiSimScorer
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.toUnsignedInt
import org.gnit.lucenekmp.search.CollectionStatistics
import org.gnit.lucenekmp.search.Explanation
import org.gnit.lucenekmp.search.TermStatistics
import org.gnit.lucenekmp.util.SmallFloat
import kotlin.math.ln

/**
 * A subclass of `Similarity` that provides a simplified API for its descendants. Subclasses
 * are only required to implement the [.score] and [.toString] methods. Implementing
 * [.explain] is optional, inasmuch as SimilarityBase
 * already provides a basic explanation of the score and the term frequency. However, implementers
 * of a subclass are encouraged to include as much detail about the scoring method as possible.
 *
 *
 * Note: multi-word queries such as phrase queries are scored in a different way than Lucene's
 * default ranking algorithm: whereas it "fakes" an IDF value for the phrase as a whole (since it
 * does not know it), this class instead scores phrases as a summation of the individual term
 * scores.
 *
 * @lucene.experimental
 */
abstract class SimilarityBase : Similarity {
    /** Default constructor: parameter-free  */
    constructor() : super()

    /** Primary constructor.  */
    constructor(discountOverlaps: Boolean) : super(discountOverlaps)

    override fun scorer(
        boost: Float,
        collectionStats: CollectionStatistics,
        vararg termStats: TermStatistics
    ): SimScorer {
        val weights: Array<SimScorer> = Array(termStats.size) { i ->
            val stats: BasicStats = newStats(collectionStats.field!!, boost.toDouble())
            fillBasicStats(stats, collectionStats, termStats[i])
            BasicSimScorer(stats)
        }
        return if (weights.size == 1) {
            weights[0]
        } else {
            MultiSimScorer(weights)
        }
    }

    /** Factory method to return a custom stats object  */
    protected open fun newStats(field: String, boost: Double): BasicStats {
        return BasicStats(field, boost)
    }

    /**
     * Fills all member fields defined in `BasicStats` in `stats`. Subclasses can override
     * this method to fill additional stats.
     */
    protected open fun fillBasicStats(
        stats: BasicStats,
        collectionStats: CollectionStatistics,
        termStats: TermStatistics
    ) {
        // TODO: validate this for real, somewhere else
        assert(termStats.totalTermFreq <= collectionStats.sumTotalTermFreq)
        assert(termStats.docFreq <= collectionStats.sumDocFreq)

        // TODO: add sumDocFreq for field (numberOfFieldPostings)
        stats.numberOfDocuments = collectionStats.docCount
        stats.numberOfFieldTokens = collectionStats.sumTotalTermFreq
        stats.avgFieldLength = collectionStats.sumTotalTermFreq / collectionStats.docCount.toDouble()
        stats.docFreq = termStats.docFreq
        stats.totalTermFreq = termStats.totalTermFreq
    }

    /**
     * Scores the document `doc`.
     *
     *
     * Subclasses must apply their scoring formula in this class.
     *
     * @param stats the corpus level statistics.
     * @param freq the term frequency.
     * @param docLen the document length.
     * @return the score.
     */
    protected abstract fun score(stats: BasicStats, freq: Double, docLen: Double): Double

    /**
     * Subclasses should implement this method to explain the score. `expl` already contains the
     * score, the name of the class and the doc id, as well as the term frequency and its explanation;
     * subclasses can add additional clauses to explain details of their scoring formulae.
     *
     *
     * The default implementation does nothing.
     *
     * @param subExpls the list of details of the explanation to extend
     * @param stats the corpus level statistics.
     * @param freq the term frequency.
     * @param docLen the document length.
     */
    protected open fun explain(
        subExpls: MutableList<Explanation>,
        stats: BasicStats,
        freq: Double,
        docLen: Double
    ) {
    }

    /**
     * Explains the score. The implementation here provides a basic explanation in the format
     * *score(name-of-similarity, doc=doc-id, freq=term-frequency), computed from:*, and
     * attaches the score (computed via the [.score] method) and the
     * explanation for the term frequency. Subclasses content with this format may add additional
     * details in [.explain].
     *
     * @param stats the corpus level statistics.
     * @param freq the term frequency and its explanation.
     * @param docLen the document length.
     * @return the explanation.
     */
    protected open fun explain(
        stats: BasicStats,
        freq: Explanation,
        docLen: Double
    ): Explanation {
        val subs: MutableList<Explanation> = mutableListOf()
        explain(subs, stats, freq.value.toFloat().toDouble(), docLen)

        return Explanation.match(
            score(stats, freq.value.toFloat().toDouble(), docLen).toFloat(),
            "score(" + this::class.simpleName + ", freq=" + freq.value + "), computed from:",
            subs
        )
    }

    /**
     * Subclasses must override this method to return the name of the Similarity and preferably the
     * values of parameters (if any) as well.
     */
    abstract override fun toString(): String

    // --------------------------------- Classes ---------------------------------
    /**
     * Delegates the [.score] and [.explain] methods to
     * [SimilarityBase.score] and [ ][SimilarityBase.explain], respectively.
     */
    internal inner class BasicSimScorer(val stats: BasicStats) :
        SimScorer() {

        fun getLengthValue(norm: Long): Double {
            return LENGTH_TABLE[Byte.toUnsignedInt(norm.toByte())].toDouble()
        }

        override fun score(freq: Float, norm: Long): Float {
            return this@SimilarityBase.score(stats, freq.toDouble(), getLengthValue(norm)).toFloat()
        }

        override fun explain(
            freq: Explanation,
            norm: Long
        ): Explanation {
            return this@SimilarityBase.explain(stats, freq, getLengthValue(norm))
        }
    }

    companion object {
        /** For [.log2]. Precomputed for efficiency reasons.  */
        private val LOG_2 = ln(2.0)

        // ------------------------------ Norm handling ------------------------------
        /** Cache of decoded bytes.  */
        private val LENGTH_TABLE = FloatArray(256)

        init {
            for (i in 0..255) {
                LENGTH_TABLE[i] = SmallFloat.byte4ToInt(i.toByte()).toFloat()
            }
        }

        // ----------------------------- Static methods ------------------------------
        /** Returns the base two logarithm of `x`.  */
        fun log2(x: Double): Double {
            // Put this to a 'util' class if we need more of these.
            return ln(x) / LOG_2
        }
    }
}
