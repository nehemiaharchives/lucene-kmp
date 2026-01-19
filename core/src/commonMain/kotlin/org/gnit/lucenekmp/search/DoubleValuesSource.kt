package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.DocValues
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.VectorEncoding
import org.gnit.lucenekmp.jdkport.Objects
import org.gnit.lucenekmp.jdkport.compare
import org.gnit.lucenekmp.jdkport.doubleToLongBits
import org.gnit.lucenekmp.jdkport.intBitsToFloat
import org.gnit.lucenekmp.jdkport.longBitsToDouble
import org.gnit.lucenekmp.search.comparators.DoubleComparator
import org.gnit.lucenekmp.util.NumericUtils

/**
 * Base class for producing [DoubleValues]
 *
 *
 * To obtain a [DoubleValues] object for a leaf reader, clients should call [ ][.rewrite] against the top-level searcher, and then call [ ][.getValues] on the resulting DoubleValuesSource.
 *
 *
 * DoubleValuesSource objects for NumericDocValues fields can be obtained by calling [ ][.fromDoubleField], [.fromFloatField], [.fromIntField] or
 * [.fromLongField], or from [.fromField] if
 * special long-to-double encoding is required.
 *
 *
 * Scores may be used as a source for value calculations by wrapping a [Scorer] using
 * [.fromScorer] and passing the resulting DoubleValues to [ ][.getValues]. The scores can then be accessed using the [ ][.SCORES] DoubleValuesSource.
 */
abstract class DoubleValuesSource : SegmentCacheable {
    /**
     * Returns a [DoubleValues] instance for the passed-in LeafReaderContext and scores
     *
     *
     * If scores are not needed to calculate the values (ie [returns false][.needsScores],
     * callers may safely pass `null` for the `scores` parameter.
     */
    @Throws(IOException::class)
    abstract fun getValues(
        ctx: LeafReaderContext,
        scores: DoubleValues?
    ): DoubleValues

    /** Return true if document scores are needed to calculate values  */
    abstract fun needsScores(): Boolean

    /**
     * An explanation of the value for the named document.
     *
     * @param ctx the readers context to create the [Explanation] for.
     * @param docId the document's id relative to the given context's reader
     * @return an Explanation for the value
     * @throws IOException if an [IOException] occurs
     */
    @Throws(IOException::class)
    open fun explain(
        ctx: LeafReaderContext,
        docId: Int,
        scoreExplanation: Explanation
    ): Explanation {
        val dv: DoubleValues =
            getValues(
                ctx,
                constant(scoreExplanation.value.toDouble())
                    .getValues(ctx, null)
            )
        if (dv.advanceExact(docId)) return Explanation.match(
            dv.doubleValue(),
            this.toString()
        )
        return Explanation.noMatch(this.toString())
    }

    /**
     * Return a DoubleValuesSource specialised for the given IndexSearcher
     *
     *
     * Implementations should assume that this will only be called once. IndexReader-independent
     * implementations can just return `this`
     *
     *
     * Queries that use DoubleValuesSource objects should call rewrite() during [ ][Query.createWeight] rather than during [ ][Query.rewrite] to avoid IndexReader reference leakage.
     *
     *
     * For the same reason, implementations that cache references to the IndexSearcher should
     * return a new object from this method.
     */
    @Throws(IOException::class)
    abstract fun rewrite(reader: IndexSearcher): DoubleValuesSource

    /**
     * Create a sort field based on the value of this producer
     *
     * @param reverse true if the sort should be decreasing
     */
    fun getSortField(reverse: Boolean): SortField {
        return DoubleValuesSortField(this, reverse)
    }

    abstract override fun hashCode(): Int

    abstract override fun equals(obj: Any?): Boolean

    abstract override fun toString(): String

    /** Convert to a LongValuesSource by casting the double values to longs  */
    fun toLongValuesSource(): LongValuesSource {
        return LongDoubleValuesSource(this)
    }

    /** Convert to [LongValuesSource] by calling [NumericUtils.doubleToSortableLong]  */
    fun toSortableLongDoubleValuesSource(): LongValuesSource {
        return SortableLongDoubleValuesSource(this)
    }

    private class SortableLongDoubleValuesSource(private val inner: DoubleValuesSource) : LongValuesSource() {

        @Throws(IOException::class)
        override fun getValues(
            ctx: LeafReaderContext,
            scores: DoubleValues?
        ): LongValues {
            val `in`: DoubleValues = inner.getValues(ctx, scores)

            return object : LongValues() {
                @Throws(IOException::class)
                override fun longValue(): Long {
                    return NumericUtils.doubleToSortableLong(`in`.doubleValue())
                }

                @Throws(IOException::class)
                override fun advanceExact(doc: Int): Boolean {
                    return `in`.advanceExact(doc)
                }
            }
        }

        override fun needsScores(): Boolean {
            return inner.needsScores()
        }

        override fun hashCode(): Int {
            return inner.hashCode()
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) return true
            if (o == null || this::class != o::class) return false
            val that = o as SortableLongDoubleValuesSource
            return inner == that.inner
        }

        override fun toString(): String {
            return "sortableLong($inner)"
        }

        @Throws(IOException::class)
        override fun rewrite(searcher: IndexSearcher): LongValuesSource {
            return inner.rewrite(searcher).toLongValuesSource()
        }

        override fun isCacheable(ctx: LeafReaderContext): Boolean {
            return false
        }
    }

    private class LongDoubleValuesSource(private val inner: DoubleValuesSource) :
        LongValuesSource() {
        @Throws(IOException::class)
        override fun getValues(
            ctx: LeafReaderContext,
            scores: DoubleValues?
        ): LongValues {
            val `in`: DoubleValues = inner.getValues(ctx, scores)
            return object : LongValues() {
                @Throws(IOException::class)
                override fun longValue(): Long {
                    return `in`.doubleValue().toLong()
                }

                @Throws(IOException::class)
                override fun advanceExact(doc: Int): Boolean {
                    return `in`.advanceExact(doc)
                }
            }
        }

        override fun isCacheable(ctx: LeafReaderContext): Boolean {
            return inner.isCacheable(ctx)
        }

        override fun needsScores(): Boolean {
            return inner.needsScores()
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) return true
            if (o == null || this::class != o::class) return false
            val that = o as LongDoubleValuesSource
            return inner == that.inner
        }

        override fun hashCode(): Int {
            return Objects.hash(inner)
        }

        override fun toString(): String {
            return "long($inner)"
        }

        @Throws(IOException::class)
        override fun rewrite(searcher: IndexSearcher): LongValuesSource {
            return inner.rewrite(searcher).toLongValuesSource()
        }
    }

    private class ConstantValuesSource(private val value: Double) : DoubleValuesSource() {
        private val doubleValues: DoubleValues = object : DoubleValues() {
            override fun doubleValue(): Double {
                return value
            }

            override fun advanceExact(doc: Int): Boolean {
                return true
            }
        }

        override fun rewrite(searcher: IndexSearcher): DoubleValuesSource {
            return this
        }

        @Throws(IOException::class)
        override fun getValues(
            ctx: LeafReaderContext,
            scores: DoubleValues?
        ): DoubleValues {
            return doubleValues
        }

        override fun needsScores(): Boolean {
            return false
        }

        override fun isCacheable(ctx: LeafReaderContext): Boolean {
            return true
        }

        override fun explain(
            ctx: LeafReaderContext,
            docId: Int,
            scoreExplanation: Explanation
        ): Explanation {
            return Explanation.match(value, "constant($value)")
        }

        override fun hashCode(): Int {
            return Objects.hash(value)
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) return true
            if (o == null || this::class != o::class) return false
            val that = o as ConstantValuesSource
            return Double.compare(that.value, value) == 0
        }

        override fun toString(): String {
            return "constant($value)"
        }
    }

    private class FieldValuesSource(
        val field: String,
        val decoder: (Long) -> Double
    ) : DoubleValuesSource() {

        override fun equals(o: Any?): Boolean {
            if (this === o) return true
            if (o == null || this::class != o::class) return false
            val that = o as FieldValuesSource
            return field == that.field && decoder == that.decoder
        }

        override fun toString(): String {
            return "double($field)"
        }

        override fun hashCode(): Int {
            return Objects.hash(field, decoder)
        }

        @Throws(IOException::class)
        override fun getValues(
            ctx: LeafReaderContext,
            scores: DoubleValues?
        ): DoubleValues {
            val values: NumericDocValues =
                DocValues.getNumeric(ctx.reader(), field)
            return object : DoubleValues() {
                @Throws(IOException::class)
                override fun doubleValue(): Double {
                    return decoder(values.longValue())
                }

                @Throws(IOException::class)
                override fun advanceExact(target: Int): Boolean {
                    return values.advanceExact(target)
                }
            }
        }

        override fun needsScores(): Boolean {
            return false
        }

        override fun isCacheable(ctx: LeafReaderContext): Boolean {
            return DocValues.isCacheable(ctx, field)
        }

        @Throws(IOException::class)
        override fun explain(
            ctx: LeafReaderContext,
            docId: Int,
            scoreExplanation: Explanation
        ): Explanation {
            val values: DoubleValues = getValues(ctx, null)
            if (values.advanceExact(docId)) return Explanation.match(
                values.doubleValue(),
                this.toString()
            )
            else return Explanation.noMatch(this.toString())
        }

        @Throws(IOException::class)
        override fun rewrite(searcher: IndexSearcher): DoubleValuesSource {
            return this
        }
    }

    private class DoubleValuesSortField(val producer: DoubleValuesSource, reverse: Boolean) :
        SortField(
            producer.toString(), DoubleValuesComparatorSource(producer), reverse
        ) {
        override var missingValue: Any? = null
            set(missingValue) {
                if (missingValue is Number) {
                    field = missingValue
                    (getComparatorSource() as DoubleValuesComparatorSource)
                        .setMissingValue(missingValue.toDouble())
                } else {
                    super.missingValue = missingValue
                }
            }

        override fun needsScores(): Boolean {
            return producer.needsScores()
        }

        override fun toString(): String {
            val buffer = StringBuilder("<")
            buffer.append(field).append(">")
            if (reverse) buffer.append("!")
            return buffer.toString()
        }

        @Throws(IOException::class)
        override fun rewrite(searcher: IndexSearcher): SortField {
            val rewrittenSource = producer.rewrite(searcher)
            if (rewrittenSource === producer) {
                return this
            }
            val rewritten = DoubleValuesSortField(rewrittenSource, reverse)
            if (missingValue != null) {
                rewritten.missingValue = missingValue!!
            }
            return rewritten
        }
    }

    private class DoubleValuesHolder {
        var values: DoubleValues? = null
    }

    private class DoubleValuesComparatorSource(private val producer: DoubleValuesSource) :
        FieldComparatorSource() {
        private var missingValue = 0.0

        fun setMissingValue(missingValue: Double) {
            this.missingValue = missingValue
        }

        override fun newComparator(
            fieldname: String,
            numHits: Int,
            pruning: Pruning,
            reversed: Boolean
        ): FieldComparator<Double> {
            return object : DoubleComparator(
                numHits,
                fieldname,
                missingValue,
                reversed,
                Pruning.NONE
            ) {
                @Throws(IOException::class)
                override fun getLeafComparator(context: LeafReaderContext): LeafFieldComparator {
                    val holder = DoubleValuesHolder()

                    return object :
                        DoubleLeafComparator(
                            context
                        ) {
                        var ctx: LeafReaderContext? = null

                        override fun getNumericDocValues(
                            context: LeafReaderContext, field: String
                        ): NumericDocValues {
                            ctx = context
                            return asNumericDocValues(
                                holder
                            ) { value: Double ->
                                Double.doubleToLongBits(value)
                            }
                        }

                        @Throws(IOException::class)
                        override fun setScorer(scorer: Scorable) {
                            holder.values = producer.getValues(ctx!!, fromScorer(scorer))
                            super.setScorer(scorer)
                        }
                    }
                }
            }
        }
    }

    private class QueryDoubleValuesSource(private val query: Query) :
        DoubleValuesSource() {

        override fun equals(o: Any?): Boolean {
            if (this === o) return true
            if (o == null || this::class != o::class) return false
            val that = o as QueryDoubleValuesSource
            return query == that.query
        }

        override fun hashCode(): Int {
            return Objects.hash(query)
        }

        @Throws(IOException::class)
        override fun getValues(
            ctx: LeafReaderContext,
            scores: DoubleValues?
        ): DoubleValues {
            throw UnsupportedOperationException("This DoubleValuesSource must be rewritten")
        }

        override fun needsScores(): Boolean {
            return false
        }

        @Throws(IOException::class)
        override fun rewrite(searcher: IndexSearcher): DoubleValuesSource {
            return WeightDoubleValuesSource(
                searcher.rewrite(query)
                    .createWeight(searcher, ScoreMode.COMPLETE, 1f)
            )
        }

        override fun toString(): String {
            return "score($query)"
        }

        override fun isCacheable(ctx: LeafReaderContext): Boolean {
            return false
        }
    }

    private class WeightDoubleValuesSource(private val weight: Weight) :
        DoubleValuesSource() {

        @Throws(IOException::class)
        override fun getValues(
            ctx: LeafReaderContext,
            scores: DoubleValues?
        ): DoubleValues {
            val scorer: Scorer? = weight.scorer(ctx)
            if (scorer == null) return DoubleValues.EMPTY

            return object : DoubleValues() {
                private val tpi: TwoPhaseIterator? = scorer.twoPhaseIterator()
                private val disi: DocIdSetIterator =
                    if (tpi == null) scorer.iterator() else tpi.approximation()
                private var tpiMatch: Boolean? = null // cache tpi.matches()

                @Throws(IOException::class)
                override fun doubleValue(): Double {
                    return scorer.score().toDouble()
                }

                @Throws(IOException::class)
                override fun advanceExact(doc: Int): Boolean {
                    if (disi.docID() < doc) {
                        disi.advance(doc)
                        tpiMatch = null
                    }
                    if (disi.docID() == doc) {
                        if (tpi == null) {
                            return true
                        } else if (tpiMatch == null) {
                            tpiMatch = tpi.matches()
                        }
                        return tpiMatch!!
                    }
                    return false
                }
            }
        }

        @Throws(IOException::class)
        override fun explain(
            ctx: LeafReaderContext,
            docId: Int,
            scoreExplanation: Explanation
        ): Explanation {
            return weight.explain(ctx, docId)
        }

        override fun needsScores(): Boolean {
            return false
        }

        @Throws(IOException::class)
        override fun rewrite(searcher: IndexSearcher): DoubleValuesSource {
            return this
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) return true
            if (o == null || this::class != o::class) return false
            val that = o as WeightDoubleValuesSource
            return weight == that.weight
        }

        override fun hashCode(): Int {
            return Objects.hash(weight)
        }

        override fun toString(): String {
            return "score(" + weight.query.toString() + ")"
        }

        override fun isCacheable(ctx: LeafReaderContext): Boolean {
            return false
        }
    }

    companion object {
        /**
         * Returns a DoubleValues instance for computing the vector similarity score per document against
         * the byte query vector
         *
         * @param ctx the context for which to return the DoubleValues
         * @param queryVector byte query vector
         * @param vectorField knn byte field name
         * @return DoubleValues instance
         * @throws IOException if an [IOException] occurs
         */
        @Throws(IOException::class)
        fun similarityToQueryVector(
            ctx: LeafReaderContext,
            queryVector: ByteArray,
            vectorField: String
        ): DoubleValues {
            require(
                ctx.reader().fieldInfos.fieldInfo(vectorField)!!.vectorEncoding
                        == VectorEncoding.BYTE
            ) {
                ("Field "
                        + vectorField
                        + " does not have the expected vector encoding: "
                        + VectorEncoding.BYTE)
            }
            return ByteVectorSimilarityValuesSource(queryVector, vectorField).getValues(ctx, null)
        }

        /**
         * Returns a DoubleValues instance for computing the vector similarity score per document against
         * the float query vector
         *
         * @param ctx the context for which to return the DoubleValues
         * @param queryVector float query vector
         * @param vectorField knn float field name
         * @return DoubleValues instance
         * @throws IOException if an [IOException] occurs
         */
        @Throws(IOException::class)
        fun similarityToQueryVector(
            ctx: LeafReaderContext,
            queryVector: FloatArray,
            vectorField: String
        ): DoubleValues {
            require(
                ctx.reader().fieldInfos.fieldInfo(vectorField)!!.vectorEncoding
                        == VectorEncoding.FLOAT32
            ) {
                ("Field "
                        + vectorField
                        + " does not have the expected vector encoding: "
                        + VectorEncoding.FLOAT32)
            }
            return FloatVectorSimilarityValuesSource(queryVector, vectorField).getValues(ctx, null)
        }

        /**
         * Creates a DoubleValuesSource that wraps a generic NumericDocValues field
         *
         * @param field the field to wrap, must have NumericDocValues
         * @param decoder a function to convert the long-valued doc values to doubles
         */
        fun fromField(
            field: String,
            decoder: (Long) -> Double /*java.util.function.LongToDoubleFunction*/
        ): DoubleValuesSource {
            return FieldValuesSource(field, decoder)
        }

        /** Creates a DoubleValuesSource that wraps a double-valued field  */
        fun fromDoubleField(field: String): DoubleValuesSource {
            return fromField(
                field
            ) { bits: Long ->
                Double.longBitsToDouble(bits)
            }
        }

        /** Creates a DoubleValuesSource that wraps a float-valued field  */
        fun fromFloatField(field: String): DoubleValuesSource {
            return fromField(
                field
            ) { v: Long ->
                Float.intBitsToFloat(v.toInt()).toDouble()
            }
        }

        /** Creates a DoubleValuesSource that wraps a long-valued field  */
        fun fromLongField(field: String): DoubleValuesSource {
            return fromField(
                field
            ) { v: Long -> v.toDouble() }
        }

        /** Creates a DoubleValuesSource that wraps an int-valued field  */
        fun fromIntField(field: String): DoubleValuesSource {
            return fromLongField(field)
        }

        /**
         * A DoubleValuesSource that exposes a document's score
         *
         *
         * If this source is used as part of a values calculation, then callers must not pass `null` as the [DoubleValues] parameter on [.getValues]
         */
        val SCORES: DoubleValuesSource = object : DoubleValuesSource() {
            @Throws(IOException::class)
            override fun getValues(
                ctx: LeafReaderContext,
                scores: DoubleValues?
            ): DoubleValues {
                checkNotNull(scores)
                return scores
            }

            override fun needsScores(): Boolean {
                return true
            }

            override fun isCacheable(ctx: LeafReaderContext): Boolean {
                return false
            }

            override fun explain(
                ctx: LeafReaderContext,
                docId: Int,
                scoreExplanation: Explanation
            ): Explanation {
                return scoreExplanation
            }

            override fun hashCode(): Int {
                return 0
            }

            override fun equals(obj: Any?): Boolean {
                return obj === this
            }

            override fun toString(): String {
                return "scores"
            }

            override fun rewrite(searcher: IndexSearcher): DoubleValuesSource {
                return this
            }
        }

        /** Creates a DoubleValuesSource that always returns a constant value  */
        fun constant(value: Double): DoubleValuesSource {
            return ConstantValuesSource(value)
        }

        /**
         * Returns a DoubleValues instance that wraps scores returned by a Scorer.
         *
         *
         * Note: If you intend to call [Scorable.score] on the provided `scorer`
         * separately, you may want to consider wrapping the collector with [ ][ScoreCachingWrappingScorer.wrap] to avoid computing the actual score multiple
         * times.
         */
        fun fromScorer(scorer: Scorable): DoubleValues {
            return object : DoubleValues() {
                @Throws(IOException::class)
                override fun doubleValue(): Double {
                    return scorer.score().toDouble()
                }

                @Throws(IOException::class)
                override fun advanceExact(doc: Int): Boolean {
                    return true
                }
            }
        }

        private fun asNumericDocValues(
            `in`: DoubleValuesHolder, converter: (Double) -> Long /*java.util.function.DoubleToLongFunction*/
        ): NumericDocValues {
            return object : NumericDocValues() {
                @Throws(IOException::class)
                override fun longValue(): Long {
                    return converter(`in`.values!!.doubleValue())
                }

                @Throws(IOException::class)
                override fun advanceExact(target: Int): Boolean {
                    return `in`.values!!.advanceExact(target)
                }

                override fun docID(): Int {
                    throw UnsupportedOperationException()
                }

                @Throws(IOException::class)
                override fun nextDoc(): Int {
                    throw UnsupportedOperationException()
                }

                @Throws(IOException::class)
                override fun advance(target: Int): Int {
                    throw UnsupportedOperationException()
                }

                override fun cost(): Long {
                    throw UnsupportedOperationException()
                }
            }
        }

        /** Create a DoubleValuesSource that returns the score of a particular query  */
        fun fromQuery(query: Query): DoubleValuesSource {
            return QueryDoubleValuesSource(query)
        }
    }
}
