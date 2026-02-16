package org.gnit.lucenekmp.document

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TermFrequencyAttribute
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.TermStates
import org.gnit.lucenekmp.jdkport.MIN_NORMAL
import org.gnit.lucenekmp.jdkport.Objects
import org.gnit.lucenekmp.jdkport.floatToIntBits
import org.gnit.lucenekmp.jdkport.intBitsToFloat
import org.gnit.lucenekmp.jdkport.isFinite
import org.gnit.lucenekmp.search.BooleanQuery
import org.gnit.lucenekmp.search.BoostQuery
import org.gnit.lucenekmp.search.DoubleValuesSource
import org.gnit.lucenekmp.search.Explanation
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.SortField
import org.gnit.lucenekmp.search.similarities.BM25Similarity
import org.gnit.lucenekmp.search.similarities.Similarity
import org.gnit.lucenekmp.search.similarities.Similarity.SimScorer
import kotlin.math.ln
import kotlin.math.pow

/**
 * [Field] that can be used to store static scoring factors into documents. This is mostly
 * inspired from the work from Nick Craswell, Stephen Robertson, Hugo Zaragoza and Michael Taylor.
 * Relevance weighting for query independent evidence. Proceedings of the 28th annual international
 * ACM SIGIR conference on Research and development in information retrieval. August 15-19, 2005,
 * Salvador, Brazil.
 *
 *
 * Feature values are internally encoded as term frequencies. Putting feature queries as [ ][org.apache.lucene.search.BooleanClause.Occur.SHOULD] clauses of a [BooleanQuery] allows to
 * combine query-dependent scores (eg. BM25) with query-independent scores using a linear
 * combination. The fact that feature values are stored as frequencies also allows search logic to
 * efficiently skip documents that can't be competitive when total hit counts are not requested.
 * This makes it a compelling option compared to storing such factors eg. in a doc-value field.
 *
 *
 * This field may only store factors that are positively correlated with the final score, like
 * pagerank. In case of factors that are inversely correlated with the score like url length, the
 * inverse of the scoring factor should be stored, ie. `1/urlLength`.
 *
 *
 * This field only considers the top 9 significant bits for storage efficiency which allows to
 * store them on 16 bits internally. In practice this limitation means that values are stored with a
 * relative precision of 2<sup>-8</sup> = 0.00390625.
 *
 *
 * Given a scoring factor `S > 0` and its weight `w > 0`, there are four ways that S
 * can be turned into a score:
 *
 *
 *  * [w * log(a + S)][.newLogQuery], with a  1. This function usually makes sense
 * because the distribution of scoring factors often follows a power law. This is typically
 * the case for pagerank for instance. However the paper suggested that the `satu` and
 * `sigm` functions give even better results.
 *  * [satu(S) = w * S / (S + k)][.newSaturationQuery], with k &gt; 0. This function is
 * similar to the one used by [BM25Similarity] in order to incorporate term frequency
 * into the final score and produces values between 0 and 1. A value of 0.5 is obtained when S
 * and k are equal.
 *  * [sigm(S) = w * S&lt;sup&gt;a&lt;/sup&gt; / (S&lt;sup&gt;a&lt;/sup&gt; + k&lt;sup&gt;a&lt;/sup&gt;)][.newSigmoidQuery],
 * with k &gt; 0, a &gt; 0. This function provided even better results than the two above but
 * is also harder to tune due to the fact it has 2 parameters. Like with `satu`, values
 * are in the 0..1 range and 0.5 is obtained when S and k are equal.
 *  * [w * S][.newLinearQuery]. Expert: This function doesn't apply any transformation to an
 * indexed feature value, and the indexed value itself, multiplied by weight, determines the
 * score. Thus, there is an expectation that a feature value is encoded in the index in a way
 * that makes sense for scoring.
 *
 *
 *
 * The constants in the above formulas typically need training in order to compute optimal
 * values. If you don't know where to start, the [.newSaturationQuery] method
 * uses `1f` as a weight and tries to guess a sensible value for the `pivot` parameter
 * of the saturation function based on index statistics, which shouldn't perform too bad. Here is an
 * example, assuming that documents have a [FeatureField] called 'features' with values for
 * the 'pagerank' feature.
 *
 * <pre class="prettyprint">
 * Query query = new BooleanQuery.Builder()
 * .add(new TermQuery(new Term("body", "apache")), Occur.SHOULD)
 * .add(new TermQuery(new Term("body", "lucene")), Occur.SHOULD)
 * .build();
 * Query boost = FeatureField.newSaturationQuery("features", "pagerank");
 * Query boostedQuery = new BooleanQuery.Builder()
 * .add(query, Occur.MUST)
 * .add(boost, Occur.SHOULD)
 * .build();
 * TopDocs topDocs = searcher.search(boostedQuery, 10);
</pre> *
 *
 * @lucene.experimental
 */
class FeatureField(fieldName: String, featureName: String, featureValue: Float, storeTermVectors: Boolean = false) : Field(fieldName, featureName, if (storeTermVectors) FIELD_TYPE_STORE_TERM_VECTORS else FIELD_TYPE) {
    private var featureValue = 0f

    /** Update the feature value of this field.  */
    fun setFeatureValue(featureValue: Float) {
        require(Float.isFinite(featureValue) != false) {
            ("featureValue must be finite, got: "
                    + featureValue
                    + " for feature "
                    + fieldsData
                    + " on field "
                    + name)
        }
        require(!(featureValue < Float.MIN_NORMAL)) {
            ("featureValue must be a positive normal float, got: "
                    + featureValue
                    + " for feature "
                    + fieldsData
                    + " on field "
                    + name
                    + " which is less than the minimum positive normal float: "
                    + Float.MIN_NORMAL)
        }
        this.featureValue = featureValue
    }

    override fun tokenStream(analyzer: Analyzer, reuse: TokenStream?): TokenStream? {
        val stream: FeatureTokenStream
        if (reuse is FeatureTokenStream) {
            stream = reuse
        } else {
            stream = FeatureTokenStream()
        }

        val freqBits: Int = Float.floatToIntBits(featureValue)
        stream.setValues(fieldsData as String, freqBits ushr 15)
        return stream
    }

    /**
     * This is useful if you have multiple features sharing a name and you want to take action to
     * deduplicate them.
     *
     * @return the feature value of this field.
     */
    fun getFeatureValue(): Float {
        return featureValue
    }

    private class FeatureTokenStream : TokenStream() {
        private val termAttribute: CharTermAttribute = addAttribute<CharTermAttribute>(CharTermAttribute::class)
        private val freqAttribute: TermFrequencyAttribute = addAttribute<TermFrequencyAttribute>(TermFrequencyAttribute::class)
        private var used = true
        private var value: String? = null
        private var freq = 0

        /** Sets the values  */
        fun setValues(value: String, freq: Int) {
            this.value = value
            this.freq = freq
        }

        override fun incrementToken(): Boolean {
            if (used) {
                return false
            }
            clearAttributes()
            termAttribute.append(value)
            freqAttribute.termFrequency = freq
            used = true
            return true
        }

        override fun reset() {
            used = false
        }

        override fun close() {
            value = null
        }
    }

    internal abstract class FeatureFunction {
        abstract fun scorer(w: Float): SimScorer

        abstract fun explain(field: String, feature: String, w: Float, freq: Int): Explanation

        @Throws(IOException::class)
        open fun rewrite(indexSearcher: IndexSearcher): FeatureFunction {
            return this
        }
    }

    internal class LinearFunction : FeatureFunction() {
        override fun scorer(w: Float): SimScorer {
            return object : SimScorer() {
                override fun score(freq: Float, norm: Long): Float {
                    return (w * decodeFeatureValue(freq))
                }
            }
        }

        override fun explain(field: String, feature: String, w: Float, freq: Int): Explanation {
            val featureValue = decodeFeatureValue(freq.toFloat())
            val score: Float = scorer(w).score(freq.toFloat(), 1L)
            return Explanation.match(
                score,
                "Linear function on the $field field for the $feature feature, computed as w * S from:",
                Explanation.match(w, "w, weight of this function"),
                Explanation.match(featureValue, "S, feature value")
            )
        }

        override fun toString(): String {
            return "LinearFunction"
        }

        override fun hashCode(): Int {
            return this::class.hashCode()
        }

        override fun equals(obj: Any?): Boolean {
            if (obj == null || this::class != obj::class) {
                return false
            }
            return true
        }
    }

    internal class LogFunction(private val scalingFactor: Float) : FeatureFunction() {
        override fun equals(obj: Any?): Boolean {
            if (obj == null || this::class != obj::class) {
                return false
            }
            val that = obj as LogFunction
            return scalingFactor == that.scalingFactor
        }

        override fun hashCode(): Int {
            return scalingFactor.hashCode()
        }

        override fun toString(): String {
            return "LogFunction(scalingFactor=$scalingFactor)"
        }

        override fun scorer(weight: Float): SimScorer {
            return object : SimScorer() {
                override fun score(freq: Float, norm: Long): Float {
                    return (weight * ln((scalingFactor + decodeFeatureValue(freq)).toDouble())).toFloat()
                }
            }
        }

        override fun explain(field: String, feature: String, w: Float, freq: Int): Explanation {
            val featureValue = decodeFeatureValue(freq.toFloat())
            val score: Float = scorer(w).score(freq.toFloat(), 1L)
            return Explanation.match(
                score,
                ("Log function on the "
                        + field
                        + " field for the "
                        + feature
                        + " feature, computed as w * log(a + S) from:"),
                Explanation.match(w, "w, weight of this function"),
                Explanation.match(scalingFactor, "a, scaling factor"),
                Explanation.match(featureValue, "S, feature value")
            )
        }
    }

    internal class SaturationFunction(private val field: String, private val feature: String, private val pivot: Float?) : FeatureFunction() {
        @Throws(IOException::class)
        override fun rewrite(indexSearcher: IndexSearcher): FeatureFunction {
            if (pivot != null) {
                return super.rewrite(indexSearcher)
            }
            val newPivot = computePivotFeatureValue(indexSearcher, field, feature)
            return SaturationFunction(field, feature, newPivot)
        }

        override fun equals(obj: Any?): Boolean {
            if (obj == null || this::class != obj::class) {
                return false
            }
            val that = obj as SaturationFunction
            return field == that.field
                    && feature == that.feature
                    && Objects.equals(pivot, that.pivot)
        }

        override fun hashCode(): Int {
            return Objects.hash(field, feature, pivot)
        }

        override fun toString(): String {
            return "SaturationFunction(pivot=$pivot)"
        }

        override fun scorer(weight: Float): SimScorer {
            checkNotNull(pivot) { "Rewrite first" }
            val pivot = this.pivot // unbox
            return object : SimScorer() {
                override fun score(freq: Float, norm: Long): Float {
                    val f = decodeFeatureValue(freq)
                    // should be f / (f + k) but we rewrite it to
                    // 1 - k / (f + k) to make sure it doesn't decrease
                    // with f in spite of rounding
                    return weight * (1 - pivot / (f + pivot))
                }
            }
        }

        override fun explain(field: String, feature: String, weight: Float, freq: Int): Explanation {
            val featureValue = decodeFeatureValue(freq.toFloat())
            val score: Float = scorer(weight).score(freq.toFloat(), 1L)
            return Explanation.match(
                score,
                ("Saturation function on the "
                        + field
                        + " field for the "
                        + feature
                        + " feature, computed as w * S / (S + k) from:"),
                Explanation.match(weight, "w, weight of this function"),
                Explanation.match(
                    pivot!!, "k, pivot feature value that would give a score contribution equal to w/2"
                ),
                Explanation.match(featureValue, "S, feature value")
            )
        }
    }

    internal class SigmoidFunction(private val pivot: Float, private val a: Float) : FeatureFunction() {
        private val pivotPa: Double

        init {
            this.pivotPa = pivot.toDouble().pow(a.toDouble())
        }

        override fun equals(obj: Any?): Boolean {
            if (obj == null || this::class != obj::class) {
                return false
            }
            val that = obj as SigmoidFunction
            return pivot == that.pivot && a == that.a
        }

        override fun hashCode(): Int {
            var h = pivot.hashCode()
            h = 31 * h + a.hashCode()
            return h
        }

        override fun toString(): String {
            return "SigmoidFunction(pivot=$pivot, a=$a)"
        }

        override fun scorer(weight: Float): SimScorer {
            return object : SimScorer() {
                override fun score(freq: Float, norm: Long): Float {
                    val f = decodeFeatureValue(freq)
                    // should be f^a / (f^a + k^a) but we rewrite it to
                    // 1 - k^a / (f + k^a) to make sure it doesn't decrease
                    // with f in spite of rounding
                    return (weight * (1 - pivotPa / (f.toDouble().pow(a.toDouble()) + pivotPa))).toFloat()
                }
            }
        }

        override fun explain(field: String, feature: String, weight: Float, freq: Int): Explanation {
            val featureValue = decodeFeatureValue(freq.toFloat())
            val score: Float = scorer(weight).score(freq.toFloat(), 1L)
            return Explanation.match(
                score,
                ("Sigmoid function on the "
                        + field
                        + " field for the "
                        + feature
                        + " feature, computed as w * S^a / (S^a + k^a) from:"),
                Explanation.match(weight, "w, weight of this function"),
                Explanation.match(
                    pivot, "k, pivot feature value that would give a score contribution equal to w/2"
                ),
                Explanation.match(
                    a,
                    "a, exponent, higher values make the function grow slower before k and faster after k"
                ),
                Explanation.match(featureValue, "S, feature value")
            )
        }
    }

    /**
     * Create a feature.
     *
     * @param fieldName The name of the field to store the information into. All features may be
     * stored in the same field.
     * @param featureName The name of the feature, eg. 'pagerank`. It will be indexed as a term.
     * @param featureValue The value of the feature, must be a positive, finite, normal float.
     * @param storeTermVectors Whether term vectors should be stored.
     */
    /**
     * Create a feature.
     *
     * @param fieldName The name of the field to store the information into. All features may be
     * stored in the same field.
     * @param featureName The name of the feature, eg. 'pagerank`. It will be indexed as a term.
     * @param featureValue The value of the feature, must be a positive, finite, normal float.
     */
    init {
        setFeatureValue(featureValue)
    }

    companion object {
        private val FIELD_TYPE: FieldType = FieldType()
        private val FIELD_TYPE_STORE_TERM_VECTORS: FieldType = FieldType()

        init {
            FIELD_TYPE.setTokenized(false)
            FIELD_TYPE.setOmitNorms(true)
            FIELD_TYPE.setIndexOptions(IndexOptions.DOCS_AND_FREQS)

            FIELD_TYPE_STORE_TERM_VECTORS.setTokenized(false)
            FIELD_TYPE_STORE_TERM_VECTORS.setOmitNorms(true)
            FIELD_TYPE_STORE_TERM_VECTORS.setIndexOptions(IndexOptions.DOCS_AND_FREQS)
            FIELD_TYPE_STORE_TERM_VECTORS.setStoreTermVectors(true)
        }

        val MAX_FREQ: Int = Float.floatToIntBits(Float.Companion.MAX_VALUE) ushr 15

        fun decodeFeatureValue(freq: Float): Float {
            if (freq > MAX_FREQ) {
                // This is never used in practice but callers of the SimScorer API might
                // occasionally call it on eg. Float.MAX_VALUE to compute the max score
                // so we need to be consistent.
                return Float.Companion.MAX_VALUE
            }
            val tf = freq.toInt() // lossless
            val featureBits = tf shl 15
            return Float.intBitsToFloat(featureBits)
        }

        /**
         * Given that IDFs are logs, similarities that incorporate term freq and document length in sane
         * (ie. saturated) ways should have their score bounded by a log. So we reject weights that are
         * too high as it would mean that this clause would completely dominate ranking, removing the need
         * for query-dependent scores.
         */
        private const val MAX_WEIGHT = Long.SIZE_BITS.toFloat()

        /**
         * Return a new [Query] that will score documents as `weight * S` where S is the value
         * of the static feature.
         *
         * @param fieldName field that stores features
         * @param featureName name of the feature
         * @param weight weight to give to this feature, must be in (0,64]
         * @throws IllegalArgumentException if weight is not in (0,64]
         */
        fun newLinearQuery(fieldName: String, featureName: String, weight: Float): Query {
            require(!(weight <= 0 || weight > MAX_WEIGHT)) { "weight must be in (0, " + MAX_WEIGHT + "], got: " + weight }
            var q: Query = FeatureQuery(fieldName, featureName, LinearFunction())
            if (weight != 1f) {
                q = BoostQuery(q, weight)
            }
            return q
        }

        /**
         * Return a new [Query] that will score documents as `weight * Math.log(scalingFactor + S)` where S is the value of the static feature.
         *
         * @param fieldName field that stores features
         * @param featureName name of the feature
         * @param weight weight to give to this feature, must be in (0,64]
         * @param scalingFactor scaling factor applied before taking the logarithm, must be in [1,
         * +Infinity)
         * @throws IllegalArgumentException if weight is not in (0,64] or scalingFactor is not in [1,
         * +Infinity)
         */
        fun newLogQuery(
            fieldName: String, featureName: String, weight: Float, scalingFactor: Float
        ): Query {
            require(!(weight <= 0 || weight > MAX_WEIGHT)) { "weight must be in (0, " + MAX_WEIGHT + "], got: " + weight }
            require(!(scalingFactor < 1 || Float.isFinite(scalingFactor) == false)) { "scalingFactor must be >= 1, got: " + scalingFactor }
            var q: Query = FeatureQuery(fieldName, featureName, LogFunction(scalingFactor))
            if (weight != 1f) {
                q = BoostQuery(q, weight)
            }
            return q
        }

        /**
         * Return a new [Query] that will score documents as `weight * S / (S + pivot)` where
         * S is the value of the static feature.
         *
         * @param fieldName field that stores features
         * @param featureName name of the feature
         * @param weight weight to give to this feature, must be in (0,64]
         * @param pivot feature value that would give a score contribution equal to weight/2, must be in
         * (0, +Infinity)
         * @throws IllegalArgumentException if weight is not in (0,64] or pivot is not in (0, +Infinity)
         */
        /*fun newSaturationQuery(
            fieldName: String, featureName: String, weight: Float, pivot: Float
        ): Query {
            return Companion.newSaturationQuery(fieldName, featureName, weight, pivot)
        }
        */
        /**
         * Same as [.newSaturationQuery] but `1f` is used as a
         * weight and a reasonably good default pivot value is computed based on index statistics and is
         * approximately equal to the geometric mean of all values that exist in the index.
         *
         * @param fieldName field that stores features
         * @param featureName name of the feature
         * @throws IllegalArgumentException if weight is not in (0,64] or pivot is not in (0, +Infinity)
         */
        fun newSaturationQuery(fieldName: String, featureName: String): Query {
            return newSaturationQuery(fieldName, featureName, 1f, null)
        }

        /**
         * Return a new [Query] that will score documents as `weight * S / (S + pivot)` where
         * S is the value of the static feature.
         *
         * @param fieldName field that stores features
         * @param featureName name of the feature
         * @param weight weight to give to this feature, must be in (0,64]
         * @param pivot feature value that would give a score contribution equal to weight/2, must be in
         * (0, +Infinity)
         * @throws IllegalArgumentException if weight is not in (0,64] or pivot is not in (0, +Infinity)
         */
        fun newSaturationQuery(
            fieldName: String, featureName: String, weight: Float, pivot: Float?
        ): Query {
            require(!(weight <= 0 || weight > MAX_WEIGHT)) { "weight must be in (0, $MAX_WEIGHT], got: $weight" }
            require(!(pivot != null && (pivot <= 0 || Float.isFinite(pivot) == false))) { "pivot must be > 0, got: $pivot" }
            var q: Query =
                FeatureQuery(
                    fieldName, featureName, SaturationFunction(fieldName, featureName, pivot)
                )
            if (weight != 1f) {
                q = BoostQuery(q, weight)
            }
            return q
        }

        /**
         * Return a new [Query] that will score documents as `weight * S^a / (S^a + pivot^a)`
         * where S is the value of the static feature.
         *
         * @param fieldName field that stores features
         * @param featureName name of the feature
         * @param weight weight to give to this feature, must be in (0,64]
         * @param pivot feature value that would give a score contribution equal to weight/2, must be in
         * (0, +Infinity)
         * @param exp exponent, higher values make the function grow slower before 'pivot' and faster
         * after 'pivot', must be in (0, +Infinity)
         * @throws IllegalArgumentException if w is not in (0,64] or either k or a are not in (0,
         * +Infinity)
         */
        fun newSigmoidQuery(
            fieldName: String, featureName: String, weight: Float, pivot: Float, exp: Float
        ): Query {
            require(!(weight <= 0 || weight > MAX_WEIGHT)) { "weight must be in (0, $MAX_WEIGHT], got: $weight" }
            require(!(pivot <= 0 || Float.isFinite(pivot) == false)) { "pivot must be > 0, got: $pivot" }
            require(!(exp <= 0 || Float.isFinite(exp) == false)) { "exp must be > 0, got: $exp" }
            var q: Query = FeatureQuery(fieldName, featureName, SigmoidFunction(pivot, exp))
            if (weight != 1f) {
                q = BoostQuery(q, weight)
            }
            return q
        }

        /**
         * Compute a feature value that may be used as the `pivot` parameter of the [ ][.newSaturationQuery] and [.newSigmoidQuery] factory methods. The implementation takes the average of the int bits of
         * the float representation in practice before converting it back to a float. Given that floats
         * store the exponent in the higher bits, it means that the result will be an approximation of the
         * geometric mean of all feature values.
         *
         * @param searcher the [IndexSearcher] to perform the search
         * @param featureField the field that stores features
         * @param featureName the name of the feature
         */
        @Throws(IOException::class)
        fun computePivotFeatureValue(
            searcher: IndexSearcher, featureField: String, featureName: String
        ): Float {
            val term: Term = Term(featureField, featureName)
            val states: TermStates = TermStates.build(searcher, term, true)
            if (states.docFreq() == 0) {
                // avoid division by 0
                // The return value doesn't matter much here, the term doesn't exist,
                // it will never be used for scoring. Just Make sure to return a legal
                // value.
                return 1f
            }
            val avgFreq = (states.totalTermFreq().toDouble() / states.docFreq()).toFloat()
            return decodeFeatureValue(avgFreq)
        }

        /**
         * Creates a SortField for sorting by the value of a feature.
         *
         *
         * This sort orders documents by descending value of a feature. The value returned in [ ] for the hits contains a Float instance with the feature value.
         *
         *
         * If a document is missing the field, then it is treated as having a value of `0.0f
        ` * .
         *
         * @param field field name. Must not be null.
         * @param featureName feature name. Must not be null.
         * @return SortField ordering documents by the value of the feature
         * @throws NullPointerException if `field` or `featureName` is null.
         */
        fun newFeatureSort(field: String, featureName: String): SortField {
            return FeatureSortField(field, featureName)
        }

        /**
         * Creates a [DoubleValuesSource] instance which can be used to read the values of a feature
         * from the a [FeatureField] for documents.
         *
         * @param field field name. Must not be null.
         * @param featureName feature name. Must not be null.
         * @return a [DoubleValuesSource] which can be used to access the values of the feature for
         * documents
         * @throws NullPointerException if `field` or `featureName` is null.
         */
        fun newDoubleValues(field: String, featureName: String): DoubleValuesSource {
            return FeatureDoubleValuesSource(field, featureName)
        }
    }
}
