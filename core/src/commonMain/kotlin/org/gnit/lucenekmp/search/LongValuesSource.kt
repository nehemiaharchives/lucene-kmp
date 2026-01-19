package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.DocValues
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.jdkport.Objects
import org.gnit.lucenekmp.search.comparators.LongComparator


/**
 * Base class for producing [LongValues]
 *
 *
 * To obtain a [LongValues] object for a leaf reader, clients should call [ ][.rewrite] against the top-level searcher, and then [ ][.getValues].
 *
 *
 * LongValuesSource objects for long and int-valued NumericDocValues fields can be obtained by
 * calling [.fromLongField] and [.fromIntField].
 *
 *
 * To obtain a LongValuesSource from a float or double-valued NumericDocValues field, use [ ][DoubleValuesSource.fromFloatField] or [DoubleValuesSource.fromDoubleField]
 * and then call [DoubleValuesSource.toLongValuesSource].
 */
abstract class LongValuesSource : SegmentCacheable {
    /**
     * Returns a [LongValues] instance for the passed-in LeafReaderContext and scores
     *
     *
     * If scores are not needed to calculate the values (ie [returns false][.needsScores],
     * callers may safely pass `null` for the `scores` parameter.
     */
    @Throws(IOException::class)
    abstract fun getValues(
        ctx: LeafReaderContext,
        scores: DoubleValues?
    ): LongValues

    /** Return true if document scores are needed to calculate values  */
    abstract fun needsScores(): Boolean

    abstract override fun hashCode(): Int

    abstract override fun equals(obj: Any?): Boolean

    abstract override fun toString(): String

    /**
     * Return a LongValuesSource specialised for the given IndexSearcher
     *
     *
     * Implementations should assume that this will only be called once. IndexSearcher-independent
     * implementations can just return `this`
     */
    @Throws(IOException::class)
    abstract fun rewrite(searcher: IndexSearcher): LongValuesSource

    /**
     * Create a sort field based on the value of this producer
     *
     * @param reverse true if the sort should be decreasing
     */
    fun getSortField(reverse: Boolean): SortField {
        return LongValuesSortField(this, reverse)
    }

    /** Convert to a DoubleValuesSource by casting long values to doubles  */
    fun toDoubleValuesSource(): DoubleValuesSource {
        return DoubleLongValuesSource(this)
    }

    private class DoubleLongValuesSource(private val inner: LongValuesSource) :
        DoubleValuesSource() {
        @Throws(IOException::class)
        override fun getValues(
            ctx: LeafReaderContext,
            scores: DoubleValues?
        ): DoubleValues {
            val v: LongValues = inner.getValues(ctx, scores)
            return object : DoubleValues() {
                @Throws(IOException::class)
                override fun doubleValue(): Double {
                    return v.longValue().toDouble()
                }

                @Throws(IOException::class)
                override fun advanceExact(doc: Int): Boolean {
                    return v.advanceExact(doc)
                }
            }
        }

        @Throws(IOException::class)
        override fun rewrite(searcher: IndexSearcher): DoubleValuesSource {
            return inner.rewrite(searcher).toDoubleValuesSource()
        }

        override fun isCacheable(ctx: LeafReaderContext): Boolean {
            return inner.isCacheable(ctx)
        }

        override fun toString(): String {
            return "double($inner)"
        }

        override fun needsScores(): Boolean {
            return inner.needsScores()
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) return true
            if (o == null || this::class != o::class) return false
            val that = o as DoubleLongValuesSource
            return inner == that.inner
        }

        override fun hashCode(): Int {
            return Objects.hash(inner)
        }
    }

    /**
     * A ConstantLongValuesSource that always returns a constant value
     *
     * @lucene.internal
     */
    class ConstantLongValuesSource(
        /** Get the constant value.  */
        val value: Long
    ) : LongValuesSource() {
        @Throws(IOException::class)
        override fun getValues(
            ctx: LeafReaderContext,
            scores: DoubleValues?
        ): LongValues {
            return object : LongValues() {
                @Throws(IOException::class)
                override fun longValue(): Long {
                    return value
                }

                @Throws(IOException::class)
                override fun advanceExact(doc: Int): Boolean {
                    return true
                }
            }
        }

        override fun isCacheable(ctx: LeafReaderContext): Boolean {
            return true
        }

        override fun needsScores(): Boolean {
            return false
        }

        override fun hashCode(): Int {
            return Objects.hash(value)
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) return true
            if (o == null || this::class != o::class) return false
            val that = o as ConstantLongValuesSource
            return value == that.value
        }

        override fun toString(): String {
            return "constant($value)"
        }

        @Throws(IOException::class)
        override fun rewrite(searcher: IndexSearcher): LongValuesSource {
            return this
        }
    }

    private class FieldValuesSource(val field: String) : LongValuesSource() {
        override fun equals(o: Any?): Boolean {
            if (this === o) return true
            if (o == null || this::class != o::class) return false
            val that = o as FieldValuesSource
            return field == that.field
        }

        override fun toString(): String {
            return "long($field)"
        }

        override fun hashCode(): Int {
            return Objects.hash(field)
        }

        @Throws(IOException::class)
        override fun getValues(
            ctx: LeafReaderContext,
            scores: DoubleValues?
        ): LongValues {
            val values: NumericDocValues =
                DocValues.getNumeric(ctx.reader(), field)
            return toLongValues(values)
        }

        override fun isCacheable(ctx: LeafReaderContext): Boolean {
            return DocValues.isCacheable(ctx, field)
        }

        override fun needsScores(): Boolean {
            return false
        }

        @Throws(IOException::class)
        override fun rewrite(searcher: IndexSearcher): LongValuesSource {
            return this
        }
    }

    private class LongValuesSortField(val producer: LongValuesSource, reverse: Boolean) :
        SortField(
            producer.toString(), LongValuesComparatorSource(producer), reverse
        ) {
        override var missingValue: Any? = null
            set(missingValue) {
                if (missingValue is Number) {
                    field = missingValue
                    (getComparatorSource() as LongValuesComparatorSource)
                        .setMissingValue(missingValue.toLong())
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
            if (producer === rewrittenSource) {
                return this
            }
            val rewritten = LongValuesSortField(rewrittenSource, reverse)
            if (missingValue != null) {
                rewritten.missingValue = missingValue!!
            }
            return rewritten
        }
    }

    private class LongValuesHolder {
        var values: LongValues? = null
    }

    private class LongValuesComparatorSource(private val producer: LongValuesSource) :
        FieldComparatorSource() {
        private var missingValue = 0L

        fun setMissingValue(missingValue: Long) {
            this.missingValue = missingValue
        }

        override fun newComparator(
            fieldname: String,
            numHits: Int,
            pruning: Pruning,
            reversed: Boolean
        ): FieldComparator<Long> {
            return object : LongComparator(
                numHits,
                fieldname,
                missingValue,
                reversed,
                Pruning.NONE
            ) {
                @Throws(IOException::class)
                override fun getLeafComparator(context: LeafReaderContext): LeafFieldComparator {
                    val holder = LongValuesHolder()

                    return object :
                        LongLeafComparator(
                            context
                        ) {
                        var ctx: LeafReaderContext? = null

                        override fun getNumericDocValues(
                            context: LeafReaderContext, field: String
                        ): NumericDocValues {
                            ctx = context
                            return asNumericDocValues(holder)
                        }

                        @Throws(IOException::class)
                        override fun setScorer(scorer: Scorable) {
                            holder.values = producer.getValues(
                                ctx!!,
                                DoubleValuesSource.fromScorer(scorer)
                            )
                            super.setScorer(scorer)
                        }
                    }
                }
            }
        }
    }

    companion object {
        /** Creates a LongValuesSource that wraps a long-valued field  */
        fun fromLongField(field: String): LongValuesSource {
            return FieldValuesSource(field)
        }

        /** Creates a LongValuesSource that wraps an int-valued field  */
        fun fromIntField(field: String): LongValuesSource {
            return fromLongField(field)
        }

        /** Creates a LongValuesSource that always returns a constant value  */
        fun constant(value: Long): LongValuesSource {
            return ConstantLongValuesSource(value)
        }

        private fun toLongValues(`in`: NumericDocValues): LongValues {
            return object : LongValues() {
                @Throws(IOException::class)
                override fun longValue(): Long {
                    return `in`.longValue()
                }

                @Throws(IOException::class)
                override fun advanceExact(target: Int): Boolean {
                    return `in`.advanceExact(target)
                }
            }
        }

        private fun asNumericDocValues(`in`: LongValuesHolder): NumericDocValues {
            return object : NumericDocValues() {
                @Throws(IOException::class)
                override fun longValue(): Long {
                    return `in`.values!!.longValue()
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
    }
}
