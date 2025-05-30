package org.gnit.lucenekmp.search.similarities

import org.gnit.lucenekmp.index.FieldInvertState
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.search.CollectionStatistics
import org.gnit.lucenekmp.search.Explanation
import org.gnit.lucenekmp.search.TermStatistics
import org.gnit.lucenekmp.util.SmallFloat


/**
 * Similarity defines the components of Lucene scoring.
 *
 *
 * Expert: Scoring API.
 *
 *
 * This is a low-level API, you should only extend this API if you want to implement an
 * information retrieval *model*. If you are instead looking for a convenient way to alter
 * Lucene's scoring, consider just tweaking the default implementation: [BM25Similarity] or
 * extend [SimilarityBase], which makes it easy to compute a score from index statistics.
 *
 *
 * Similarity determines how Lucene weights terms, and Lucene interacts with this class at both
 * [index-time](#indextime) and [query-time](#querytime).
 *
 *
 * <a id="indextime">Indexing Time</a> At indexing time, the indexer calls [ ][.computeNorm], allowing the Similarity implementation to set a per-document
 * value for the field that will be later accessible via [ ][org.apache.lucene.index.LeafReader.getNormValues]. Lucene makes no assumption about what
 * is in this norm, but it is most useful for encoding length normalization information.
 *
 *
 * Implementations should carefully consider how the normalization is encoded: while Lucene's
 * default implementation encodes length normalization information with [SmallFloat] into a
 * single byte, this might not be suitable for all purposes.
 *
 *
 * Many formulas require the use of average document length, which can be computed via a
 * combination of [CollectionStatistics.sumTotalTermFreq] and [ ][CollectionStatistics.docCount].
 *
 *
 * Additional scoring factors can be stored in named [NumericDocValuesField]s and accessed
 * at query-time with [org.apache.lucene.index.LeafReader.getNumericDocValues].
 * However this should not be done in the [Similarity] but externally, for instance by using
 * `FunctionScoreQuery`.
 *
 *
 * Finally, using index-time boosts (either via folding into the normalization byte or via
 * DocValues), is an inefficient way to boost the scores of different fields if the boost will be
 * the same for every document, instead the Similarity can simply take a constant boost parameter
 * *C*, and [PerFieldSimilarityWrapper] can return different instances with different
 * boosts depending upon field name.
 *
 *
 * <a id="querytime">Query time</a> At query-time, Queries interact with the Similarity via these
 * steps:
 *
 *
 *  1. The [.scorer] method is called a
 * single time, allowing the implementation to compute any statistics (such as IDF, average
 * document length, etc) across *the entire collection*. The [TermStatistics] and
 * [CollectionStatistics] passed in already contain all of the raw statistics involved,
 * so a Similarity can freely use any combination of statistics without causing any additional
 * I/O. Lucene makes no assumption about what is stored in the returned [       ] object.
 *  1. Then [SimScorer.score] is called for every matching document to compute
 * its score.
 *
 *
 *
 * <a id="explaintime">Explanations</a> When [ ][IndexSearcher.explain] is called, queries consult the
 * Similarity's DocScorer for an explanation of how it computed its score. The query passes in a the
 * document id and an explanation of how the frequency was computed.
 *
 * @see org.apache.lucene.index.IndexWriterConfig.setSimilarity
 * @see IndexSearcher.setSimilarity
 * @lucene.experimental
 */
abstract class Similarity
/** Default constructor. (For invocation by subclass constructors, typically implicit.)  */ protected constructor(
    /**
     * True if overlap tokens (tokens with a position of increment of zero) are discounted from the
     * document's length.
     */
    val discountOverlaps: Boolean = true
) {
    /**
     * Returns true if overlap tokens are discounted from the document's length.
     *
     * @see .computeNorm
     */

    /**
     * Expert constructor that allows adjustment of [.getDiscountOverlaps] at index-time.
     *
     *
     * Overlap tokens are tokens such as synonyms, that have a [PositionIncrementAttribute]
     * of zero from the analysis chain.
     *
     *
     * **NOTE**: If you modify this parameter, you'll need to re-index for it to take effect.
     *
     * @param discountOverlaps true if overlap tokens should not impact document length for scoring.
     */

    /**
     * Computes the normalization value for a field at index-time.
     *
     *
     * The default implementation uses [SmallFloat.intToByte4] to encode the number of terms
     * as a single byte.
     *
     *
     * **WARNING**: The default implementation is used by Lucene's supplied Similarity classes,
     * which means you can change the Similarity at runtime without reindexing. If you override this
     * method, you'll need to re-index documents for it to take effect.
     *
     *
     * Matches in longer fields are less precise, so implementations of this method usually set
     * smaller values when `state.getLength()` is large, and larger values when `
     * state.getLength()` is small.
     *
     *
     * Note that for a given term-document frequency, greater unsigned norms must produce scores
     * that are lower or equal, ie. for two encoded norms `n1` and `n2` so that `Long.compareUnsigned(n1, n2) > 0` then `SimScorer.score(freq, n1) <=
     * SimScorer.score(freq, n2)` for any legal `freq`.
     *
     *
     * `0` is not a legal norm, so `1` is the norm that produces the highest scores.
     *
     * @lucene.experimental
     * @param state accumulated state of term processing for this field
     * @return computed norm value
     */
    fun computeNorm(state: FieldInvertState): Long {
        val numTerms: Int
        if (state.indexOptions === IndexOptions.DOCS) {
            numTerms = state.uniqueTermCount
        } else if (discountOverlaps) {
            numTerms = state.length - state.numOverlap
        } else {
            numTerms = state.length
        }
        return SmallFloat.intToByte4(numTerms).toLong()
    }

    /**
     * Compute any collection-level weight (e.g. IDF, average document length, etc) needed for scoring
     * a query.
     *
     * @param boost a multiplicative factor to apply to the produces scores
     * @param collectionStats collection-level statistics, such as the number of tokens in the
     * collection.
     * @param termStats term-level statistics, such as the document frequency of a term across the
     * collection.
     * @return SimWeight object with the information this Similarity needs to score a query.
     */
    abstract fun scorer(
        boost: Float, collectionStats: CollectionStatistics, vararg termStats: TermStatistics
    ): SimScorer

    /**
     * Stores the weight for a query across the indexed collection. This abstract implementation is
     * empty; descendants of `Similarity` should subclass `SimWeight` and define the
     * statistics they require in the subclass. Examples include idf, average field length, etc.
     */
    abstract class SimScorer
    /** Sole constructor. (For invocation by subclass constructors.)  */
    protected constructor() {
        /**
         * Score a single document. `freq` is the document-term sloppy frequency and must be
         * finite and positive. `norm` is the encoded normalization factor as computed by [ ][Similarity.computeNorm] at index time, or `1` if norms are disabled.
         * `norm` is never `0`.
         *
         *
         * Score must not decrease when `freq` increases, ie. if `freq1 > freq2`, then
         * `score(freq1, norm) >= score(freq2, norm)` for any value of `norm` that may be
         * produced by [Similarity.computeNorm].
         *
         *
         * Score must not increase when the unsigned `norm` increases, ie. if `Long.compareUnsigned(norm1, norm2) > 0` then `score(freq, norm1) <= score(freq, norm2)`
         * for any legal `freq`.
         *
         *
         * As a consequence, the maximum score that this scorer can produce is bound by `score(Float.MAX_VALUE, 1)`.
         *
         * @param freq sloppy term frequency, must be finite and positive
         * @param norm encoded normalization factor or `1` if norms are disabled
         * @return document's score
         */
        abstract fun score(freq: Float, norm: Long): Float

        /**
         * Explain the score for a single document
         *
         * @param freq Explanation of how the sloppy term frequency was computed
         * @param norm encoded normalization factor, as returned by [Similarity.computeNorm], or
         * `1` if norms are disabled
         * @return document's score
         */
        open fun explain(freq: Explanation, norm: Long): Explanation {
            return Explanation.match(
                score(freq.value.toFloat(), norm),
                "score(freq=" + freq.value + "), with freq of:",
                mutableSetOf(freq)
            )
        }
    }
}
