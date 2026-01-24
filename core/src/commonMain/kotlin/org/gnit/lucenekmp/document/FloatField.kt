package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.index.DocValuesType
import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.search.IndexOrDocValuesQuery
import org.gnit.lucenekmp.search.PointRangeQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.SortField
import org.gnit.lucenekmp.search.SortedNumericSelector
import org.gnit.lucenekmp.search.SortedNumericSortField
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.NumericUtils

/**
 * Field that stores a per-document `float` value for scoring, sorting or value retrieval
 * and index the field for fast range filters. If you need more fine-grained control you can use
 * [FloatPoint], [FloatDocValuesField] and [StoredField].
 *
 *
 * This field defines static factory methods for creating common queries:
 *
 *
 *  * [.newExactQuery] for matching an exact 1D point.
 *  * [.newRangeQuery] for matching a 1D range.
 *  * [.newSetQuery] for matching a 1D set.
 *
 *
 * @see PointValues
 */
class FloatField(name: String, value: Float, stored: Store) :
    Field(
        name,
        if (stored == Store.YES) FIELD_TYPE_STORED else FIELD_TYPE
    ) {
    private val storedValue: StoredValue?

    /**
     * Creates a new FloatField, indexing the provided point, storing it as a DocValue, and optionally
     * storing it as a stored field.
     *
     * @param name field name
     * @param value the float value
     * @param stored whether to store the field
     * @throws IllegalArgumentException if the field name or value is null.
     */
    init {
        fieldsData = NumericUtils.floatToSortableInt(value).toLong()
        if (stored == Store.YES) {
            storedValue = StoredValue(value)
        } else {
            storedValue = null
        }
    }

    override fun binaryValue(): BytesRef {
        val encodedPoint = ByteArray(Float.SIZE_BYTES)
        val value = this.valueAsFloat
        FloatPoint.encodeDimension(value, encodedPoint, 0)
        return BytesRef(encodedPoint)
    }

    private val valueAsFloat: Float
        get() = NumericUtils.sortableIntToFloat(numericValue()!!.toInt())

    override fun storedValue(): StoredValue? {
        return storedValue
    }

    override fun toString(): String {
        return this::class.simpleName + " <" + name + ':' + this.valueAsFloat + '>'
    }

    override fun setFloatValue(value: Float) {
        super.setLongValue(NumericUtils.floatToSortableInt(value).toLong())
        if (storedValue != null) {
            storedValue.setFloatValue(value)
        }
    }

    override fun setLongValue(value: Long) {
        throw IllegalArgumentException("cannot change value type from Float to Long")
    }

    companion object {
        private val FIELD_TYPE: FieldType =
            FieldType()
        private val FIELD_TYPE_STORED: FieldType

        init {
            FIELD_TYPE.setDimensions(1, Float.SIZE_BYTES)
            FIELD_TYPE.setDocValuesType(DocValuesType.SORTED_NUMERIC)
            FIELD_TYPE.freeze()

            FIELD_TYPE_STORED = FieldType(FIELD_TYPE)
            FIELD_TYPE_STORED.setStored(true)
            FIELD_TYPE_STORED.freeze()
        }

        /**
         * Create a query for matching an exact float value.
         *
         * @param field field name. must not be `null`.
         * @param value exact value
         * @throws IllegalArgumentException if `field` is null.
         * @return a query matching documents with this exact value
         */
        fun newExactQuery(field: String, value: Float): Query {
            return newRangeQuery(field, value, value)
        }

        /**
         * Create a range query for float values.
         *
         *
         * You can have half-open ranges (which are in fact &lt;/ or &gt;/ queries) by setting
         * `lowerValue = Float.NEGATIVE_INFINITY` or `upperValue = Float.POSITIVE_INFINITY`.
         *
         *
         * Range comparisons are consistent with [Float.compareTo].
         *
         * @param field field name. must not be `null`.
         * @param lowerValue lower portion of the range (inclusive).
         * @param upperValue upper portion of the range (inclusive).
         * @throws IllegalArgumentException if `field` is null.
         * @return a query matching documents within this range.
         */
        fun newRangeQuery(
            field: String,
            lowerValue: Float,
            upperValue: Float
        ): Query {
            PointRangeQuery.checkArgs(field, lowerValue, upperValue)
            return IndexOrDocValuesQuery(
                FloatPoint.newRangeQuery(field, lowerValue, upperValue),
                SortedNumericDocValuesField.newSlowRangeQuery(
                    field,
                    NumericUtils.floatToSortableInt(lowerValue).toLong(),
                    NumericUtils.floatToSortableInt(upperValue).toLong()
                )
            )
        }

        /**
         * Create a query matching values in a supplied set
         *
         * @param field field name. must not be `null`.
         * @param values float values
         * @throws IllegalArgumentException if `field` is null.
         * @return a query matching documents within this set.
         */
        fun newSetQuery(field: String, vararg values: Float): Query {
            /*requireNotNull(field) { "field cannot be null" }*/
            val points = LongArray(values.size)
            for (i in values.indices) {
                points[i] =
                    NumericUtils.floatToSortableInt(values[i]).toLong()
            }
            return IndexOrDocValuesQuery(
                FloatPoint.newSetQuery(field, *values.copyOf()),
                SortedNumericDocValuesField.newSlowSetQuery(
                    field,
                    *points
                )
            )
        }

        /**
         * Create a new [SortField] for float values.
         *
         * @param field field name. must not be `null`.
         * @param reverse true if natural order should be reversed.
         * @param selector custom selector type for choosing the sort value from the set.
         */
        fun newSortField(
            field: String,
            reverse: Boolean,
            selector: SortedNumericSelector.Type
        ): SortField {
            return SortedNumericSortField(
                field,
                SortField.Type.FLOAT,
                reverse,
                selector
            )
        }
    }
}
