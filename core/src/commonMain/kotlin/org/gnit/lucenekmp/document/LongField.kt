package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.index.DocValuesType
import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.search.BooleanClause.Occur
import org.gnit.lucenekmp.search.BooleanQuery
import org.gnit.lucenekmp.search.BoostQuery
import org.gnit.lucenekmp.search.IndexOrDocValuesQuery
import org.gnit.lucenekmp.search.IndexSortSortedNumericDocValuesRangeQuery
import org.gnit.lucenekmp.search.PointRangeQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.SortField
import org.gnit.lucenekmp.search.SortedNumericSelector
import org.gnit.lucenekmp.search.SortedNumericSortField
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.NumericUtils

/**
 * Field that stores a per-document `long` value for scoring, sorting or value retrieval
 * and index the field for fast range filters. If you need more fine-grained control you can use
 * [LongPoint], [NumericDocValuesField] or [SortedNumericDocValuesField], and
 * [StoredField].
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
class LongField(name: String, value: Long, stored: Store) :
    Field(
        name,
        if (stored == Store.YES) FIELD_TYPE_STORED else FIELD_TYPE
    ) {
    private val storedValue: StoredValue?

    /**
     * Creates a new LongField, indexing the provided point, storing it as a DocValue, and optionally
     * storing it as a stored field.
     *
     * @param name field name
     * @param value the long value
     * @param stored whether to store the field
     * @throws IllegalArgumentException if the field name or value is null.
     */
    init {
        fieldsData = value
        if (stored == Store.YES) {
            storedValue = StoredValue(value)
        } else {
            storedValue = null
        }
    }

    override fun binaryValue(): BytesRef {
        val bytes = ByteArray(Long.SIZE_BYTES)
        NumericUtils.longToSortableBytes(fieldsData as Long, bytes, 0)
        return BytesRef(bytes)
    }

    override fun storedValue(): StoredValue? {
        return storedValue
    }

    override fun setLongValue(value: Long) {
        super.setLongValue(value)
        if (storedValue != null) {
            storedValue.setLongValue(value)
        }
    }

    override fun toString(): String {
        return this::class.simpleName + " <" + name + ':' + fieldsData + '>'
    }

    companion object {
        private val FIELD_TYPE: FieldType =
            FieldType()
        private val FIELD_TYPE_STORED: FieldType

        init {
            FIELD_TYPE.setDimensions(1, Long.SIZE_BYTES)
            FIELD_TYPE.setDocValuesType(DocValuesType.SORTED_NUMERIC)
            FIELD_TYPE.freeze()

            FIELD_TYPE_STORED = FieldType(FIELD_TYPE)
            FIELD_TYPE_STORED.setStored(true)
            FIELD_TYPE_STORED.freeze()
        }

        /**
         * Create a query for matching an exact long value.
         *
         * @param field field name. must not be `null`.
         * @param value exact value
         * @throws IllegalArgumentException if `field` is null.
         * @return a query matching documents with this exact value
         */
        fun newExactQuery(field: String, value: Long): Query {
            return newRangeQuery(field, value, value)
        }

        /**
         * Create a range query for long values.
         *
         *
         * You can have half-open ranges (which are in fact &lt;/ or &gt;/ queries) by setting
         * `lowerValue = Long.MIN_VALUE` or `upperValue = Long.MAX_VALUE`.
         *
         *
         * Ranges are inclusive. For exclusive ranges, pass `Math.addExact(lowerValue, 1)` or
         * `Math.addExact(upperValue, -1)`.
         *
         * @param field field name. must not be `null`.
         * @param lowerValue lower portion of the range (inclusive).
         * @param upperValue upper portion of the range (inclusive).
         * @throws IllegalArgumentException if `field` is null.
         * @return a query matching documents within this range.
         */
        fun newRangeQuery(
            field: String,
            lowerValue: Long,
            upperValue: Long
        ): Query {
            PointRangeQuery.checkArgs(field, lowerValue, upperValue)
            val fallbackQuery: Query =
                IndexOrDocValuesQuery(
                    LongPoint.newRangeQuery(
                        field,
                        lowerValue,
                        upperValue
                    ),
                    SortedNumericDocValuesField.newSlowRangeQuery(
                        field,
                        lowerValue,
                        upperValue
                    )
                )
            return IndexSortSortedNumericDocValuesRangeQuery(
                field, lowerValue, upperValue, fallbackQuery
            )
        }

        /**
         * Create a query matching values in a supplied set
         *
         * @param field field name. must not be `null`.
         * @param values long values
         * @throws IllegalArgumentException if `field` is null.
         * @return a query matching documents within this set.
         */
        fun newSetQuery(field: String, vararg values: Long): Query {
            /*requireNotNull(field) { "field cannot be null" }*/
            val points: LongArray = values.copyOf()
            return IndexOrDocValuesQuery(
                LongPoint.newSetQuery(field, *points),
                SortedNumericDocValuesField.newSlowSetQuery(
                    field,
                    *points
                )
            )
        }

        /**
         * Create a new [SortField] for long values.
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
                SortField.Type.LONG,
                reverse,
                selector
            )
        }

        /**
         * Returns a query that scores documents based on their distance to `origin`: `score =
         * weight * pivotDistance / (pivotDistance + distance)`, ie. score is in the `[0, weight]`
         * range, is equal to `weight` when the document's value is equal to `origin` and is
         * equal to `weight/2` when the document's value is distant of `pivotDistance` from
         * `origin`. In case of multi-valued fields, only the closest point to `origin` will
         * be considered. This query is typically useful to boost results based on recency by adding this
         * query to a [Occur.SHOULD] clause of a [BooleanQuery].
         */
        fun newDistanceFeatureQuery(
            field: String, weight: Float, origin: Long, pivotDistance: Long
        ): Query {
            var query: Query =
                LongDistanceFeatureQuery(field, origin, pivotDistance)
            if (weight != 1f) {
                query = BoostQuery(query, weight)
            }
            return query
        }
    }
}
