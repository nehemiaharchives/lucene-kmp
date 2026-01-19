package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.index.DocValuesSkipIndexType
import org.gnit.lucenekmp.index.DocValuesType
import org.gnit.lucenekmp.search.MultiTermQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.TermInSetQuery
import org.gnit.lucenekmp.util.BytesRef

/**
 * Field that stores a per-document [BytesRef] value, indexed for sorting. Here's an example
 * usage:
 *
 * <pre class="prettyprint">
 * document.add(new SortedDocValuesField(name, new BytesRef("hello")));
</pre> *
 *
 *
 * If you also need to store the value, you should add a separate [StoredField] instance.
 *
 *
 * This value can be at most 32766 bytes long.
 */
class SortedDocValuesField private constructor(
    name: String,
    bytes: BytesRef,
    fieldType: FieldType
) : Field(name, fieldType) {
    /**
     * Create a new sorted DocValues field.
     *
     * @param name field name
     * @param bytes binary content
     * @throws IllegalArgumentException if the field name is null
     */
    constructor(name: String, bytes: BytesRef) : this(name, bytes, TYPE)

    init {
        fieldsData = bytes
    }

    companion object {
        /** Type for sorted bytes DocValues  */
        val TYPE: FieldType = FieldType()

        private val INDEXED_TYPE: FieldType

        init {
            TYPE.setDocValuesType(DocValuesType.SORTED)
            TYPE.freeze()

            INDEXED_TYPE = FieldType(TYPE)
            INDEXED_TYPE.setDocValuesSkipIndexType(DocValuesSkipIndexType.RANGE)
            INDEXED_TYPE.freeze()
        }

        /**
         * Creates a new [SortedDocValuesField] with the specified 64-bit long value that also
         * creates a [skip index][FieldType.docValuesSkipIndexType].
         *
         * @param name field name
         * @param bytes binary content
         * @throws IllegalArgumentException if the field name is null
         */
        fun indexedField(
            name: String,
            bytes: BytesRef
        ): SortedDocValuesField {
            return SortedDocValuesField(name, bytes, INDEXED_TYPE)
        }

        /**
         * Create a range query that matches all documents whose value is between `lowerValue` and
         * `upperValue` included.
         *
         *
         * You can have half-open ranges by setting `lowerValue = null` or `upperValue =
         * null`.
         *
         *
         * **NOTE**: Such queries cannot efficiently advance to the next match, which makes them
         * slow if they are not ANDed with a selective query. As a consequence, they are best used wrapped
         * in an [IndexOrDocValuesQuery], alongside a range query that executes on points, such as
         * [BinaryPoint.newRangeQuery].
         */
        fun newSlowRangeQuery(
            field: String,
            lowerValue: BytesRef,
            upperValue: BytesRef,
            lowerInclusive: Boolean,
            upperInclusive: Boolean
        ): Query {
            return SortedSetDocValuesRangeQuery(
                field, lowerValue, upperValue, lowerInclusive, upperInclusive
            )
        }

        /**
         * Create a query for matching an exact [BytesRef] value.
         *
         *
         * **NOTE**: Such queries cannot efficiently advance to the next match, which makes them
         * slow if they are not ANDed with a selective query. As a consequence, they are best used wrapped
         * in an [IndexOrDocValuesQuery], alongside a range query that executes on points, such as
         * [BinaryPoint.newExactQuery].
         */
        fun newSlowExactQuery(
            field: String,
            value: BytesRef
        ): Query {
            return newSlowRangeQuery(field, value, value, true, true)
        }

        /**
         * Create a query matching any of the specified values.
         *
         *
         * **NOTE**: Such queries cannot efficiently advance to the next match, which makes them
         * slow if they are not ANDed with a selective query. As a consequence, they are best used wrapped
         * in an [IndexOrDocValuesQuery], alongside a set query that executes on postings, such as
         * [TermInSetQuery].
         */
        fun newSlowSetQuery(
            field: String,
            values: MutableCollection<BytesRef>
        ): Query {
            return TermInSetQuery(
                MultiTermQuery.DOC_VALUES_REWRITE,
                field,
                values
            )
        }
    }
}
