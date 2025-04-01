package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.index.DocValuesSkipIndexType
import org.gnit.lucenekmp.index.DocValuesType
import org.gnit.lucenekmp.search.IndexOrDocValuesQuery
import org.gnit.lucenekmp.search.Query


/**
 * Field that stores a per-document `long` values for scoring, sorting or value
 * retrieval. Here's an example usage:
 *
 * <pre class="prettyprint">
 * document.add(new SortedNumericDocValuesField(name, 5L));
 * document.add(new SortedNumericDocValuesField(name, 14L));
</pre> *
 *
 *
 * Note that if you want to encode doubles or floats with proper sort order, you will need to
 * encode them with [org.apache.lucene.util.NumericUtils]:
 *
 * <pre class="prettyprint">
 * document.add(new SortedNumericDocValuesField(name, NumericUtils.floatToSortableInt(-5.3f)));
</pre> *
 *
 *
 * If you also need to store the value, you should add a separate [StoredField] instance.
 */
class SortedNumericDocValuesField private constructor(name: String, value: Long, fieldType: FieldType) :
    Field(name, fieldType) {
    /**
     * Creates a new DocValues field with the specified 64-bit long value
     *
     * @param name field name
     * @param value 64-bit long value
     * @throws IllegalArgumentException if the field name is null
     */
    constructor(name: String, value: Long) : this(name, value, TYPE)

    init {
        fieldsData = value
    }

    companion object {
        /** Type for sorted numeric DocValues.  */
        val TYPE: FieldType = FieldType()

        private val INDEXED_TYPE: FieldType

        init {
            TYPE.setDocValuesType(DocValuesType.SORTED_NUMERIC)
            TYPE.freeze()

            INDEXED_TYPE = FieldType(TYPE)
            INDEXED_TYPE.setDocValuesSkipIndexType(DocValuesSkipIndexType.RANGE)
            INDEXED_TYPE.freeze()
        }

        /**
         * Creates a new [SortedNumericDocValuesField] with the specified 64-bit long value that
         * also creates a [skip index][FieldType.docValuesSkipIndexType].
         *
         * @param name field name
         * @param value 64-bit long value
         * @throws IllegalArgumentException if the field name is null
         */
        fun indexedField(name: String, value: Long): SortedNumericDocValuesField {
            return SortedNumericDocValuesField(name, value, INDEXED_TYPE)
        }

        /**
         * Create a range query that matches all documents whose value is between `lowerValue` and
         * `upperValue` included.
         *
         *
         * You can have half-open ranges (which are in fact &lt;/ or &gt;/ queries) by setting
         * `lowerValue = Long.MIN_VALUE` or `upperValue = Long.MAX_VALUE`.
         *
         *
         * Ranges are inclusive. For exclusive ranges, pass `Math.addExact(lowerValue, 1)` or
         * `Math.addExact(upperValue, -1)`.
         *
         *
         * This query also works with fields that have indexed [NumericDocValuesField]s.
         *
         *
         * **NOTE**: Such queries cannot efficiently advance to the next match, which makes them
         * slow if they are not ANDed with a selective query. As a consequence, they are best used wrapped
         * in an [IndexOrDocValuesQuery], alongside a range query that executes on points, such as
         * [LongPoint.newRangeQuery].
         *
         * @see IntField.newRangeQuery
         *
         * @see LongField.newRangeQuery
         *
         * @see FloatField.newRangeQuery
         *
         * @see DoubleField.newRangeQuery
         */
        fun newSlowRangeQuery(field: String, lowerValue: Long, upperValue: Long): Query {
            return SortedNumericDocValuesRangeQuery(field, lowerValue, upperValue)
        }

        /**
         * Create a query matching any of the specified values.
         *
         *
         * **NOTE**: Such queries cannot efficiently advance to the next match, which makes them
         * slow if they are not ANDed with a selective query. As a consequence, they are best used wrapped
         * in an [IndexOrDocValuesQuery], alongside a set query that executes on points, such as
         * [LongPoint.newSetQuery].
         *
         * @see IntField.newSetQuery
         *
         * @see LongField.newSetQuery
         *
         * @see FloatField.newSetQuery
         *
         * @see DoubleField.newSetQuery
         */
        fun newSlowSetQuery(field: String, vararg values: Long): Query {
            return SortedNumericDocValuesSetQuery(field, values.clone())
        }

        /**
         * Create a query for matching an exact long value.
         *
         *
         * This query also works with fields that have indexed [NumericDocValuesField]s.
         *
         *
         * **NOTE**: Such queries cannot efficiently advance to the next match, which makes them
         * slow if they are not ANDed with a selective query. As a consequence, they are best used wrapped
         * in an [IndexOrDocValuesQuery], alongside a range query that executes on points, such as
         * [LongPoint.newExactQuery].
         *
         * @see IntField.newExactQuery
         *
         * @see LongField.newExactQuery
         *
         * @see FloatField.newExactQuery
         *
         * @see DoubleField.newExactQuery
         */
        fun newSlowExactQuery(field: String, value: Long): Query {
            return newSlowRangeQuery(field, value, value)
        }
    }
}
