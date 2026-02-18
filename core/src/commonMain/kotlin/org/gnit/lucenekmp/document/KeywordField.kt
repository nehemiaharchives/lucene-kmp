package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.index.DocValuesType
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.ConstantScoreQuery
import org.gnit.lucenekmp.search.IndexOrDocValuesQuery
import org.gnit.lucenekmp.search.MultiTermQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.SortField
import org.gnit.lucenekmp.search.SortedSetSelector
import org.gnit.lucenekmp.search.SortedSetSortField
import org.gnit.lucenekmp.search.TermInSetQuery
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.util.BytesRef

/**
 * Field that indexes a per-document String or [BytesRef] into an inverted index for fast
 * filtering, stores values in a columnar fashion using [DocValuesType.SORTED_SET] doc values
 * for sorting and faceting, and optionally stores values as stored fields for top-hits retrieval.
 * This field does not support scoring: queries produce constant scores. If you need more
 * fine-grained control you can use [StringField], [SortedDocValuesField] or [ ], and [StoredField].
 *
 *
 * This field defines static factory methods for creating common query objects:
 *
 *
 *  * [.newExactQuery] for matching a value.
 *  * [.newSetQuery] for matching any of the values coming from a set.
 *  * [.newSortField] for matching a value.
 *
 */
class KeywordField : Field {
    private var binaryValue: BytesRef
    private val storedValue: StoredValue?

    /**
     * Creates a new KeywordField.
     *
     * @param name field name
     * @param value the BytesRef value
     * @param stored whether to store the field
     * @throws IllegalArgumentException if the field name or value is null.
     */
    constructor(
        name: String,
        value: BytesRef,
        stored: Store
    ) : super(
        name,
        value,
        if (stored == Store.YES) FIELD_TYPE_STORED else FIELD_TYPE
    ) {
        this.binaryValue = value
        if (stored == Store.YES) {
            storedValue = StoredValue(value)
        } else {
            storedValue = null
        }
    }

    /**
     * Creates a new KeywordField from a String value, by indexing its UTF-8 representation.
     *
     * @param name field name
     * @param value the BytesRef value
     * @param stored whether to store the field
     * @throws IllegalArgumentException if the field name or value is null.
     */
    constructor(
        name: String,
        value: String,
        stored: Store
    ) : super(
        name,
        value,
        if (stored == Store.YES) FIELD_TYPE_STORED else FIELD_TYPE
    ) {
        this.binaryValue = BytesRef(value)
        if (stored == Store.YES) {
            storedValue = StoredValue(value)
        } else {
            storedValue = null
        }
    }

    override fun binaryValue(): BytesRef {
        return binaryValue
    }

    override fun invertableType(): InvertableType {
        return InvertableType.BINARY
    }

    override fun setStringValue(value: String) {
        super.setStringValue(value)
        binaryValue = BytesRef(value)
        if (storedValue != null) {
            storedValue.stringValue = value
        }
    }

    override fun setBytesValue(value: BytesRef) {
        super.setBytesValue(value)
        binaryValue = value
        if (storedValue != null) {
            storedValue.binaryValue = value
        }
    }

    override fun storedValue(): StoredValue? {
        return storedValue
    }

    companion object {
        private val FIELD_TYPE: FieldType =
            FieldType()
        private val FIELD_TYPE_STORED: FieldType

        init {
            FIELD_TYPE.setIndexOptions(IndexOptions.DOCS)
            FIELD_TYPE.setOmitNorms(true)
            FIELD_TYPE.setTokenized(false)
            FIELD_TYPE.setDocValuesType(DocValuesType.SORTED_SET)
            FIELD_TYPE.freeze()

            FIELD_TYPE_STORED = FieldType(FIELD_TYPE)
            FIELD_TYPE_STORED.setStored(true)
            FIELD_TYPE_STORED.freeze()
        }

        /**
         * Create a query for matching an exact [BytesRef] value.
         *
         * @param field field name. must not be `null`.
         * @param value exact value
         * @throws NullPointerException if `field` is null.
         * @return a query matching documents with this exact value
         */
        fun newExactQuery(
            field: String,
            value: BytesRef
        ): Query {
            //java.util.Objects.requireNonNull<String>(field, "field must not be null")
            /*java.util.Objects.requireNonNull<BytesRef>(
                value,
                "value must not be null"
            )*/
            return ConstantScoreQuery(
                TermQuery(
                    Term(field, value)
                )
            )
        }

        /**
         * Create a query for matching an exact [String] value.
         *
         * @param field field name. must not be `null`.
         * @param value exact value
         * @throws NullPointerException if `field` is null.
         * @return a query matching documents with this exact value
         */
        fun newExactQuery(field: String, value: String): Query {
            //java.util.Objects.requireNonNull<String>(value, "value must not be null")
            return newExactQuery(field, BytesRef(value))
        }

        /**
         * Create a query for matching any of a set of provided [BytesRef] values.
         *
         * @param field field name. must not be `null`.
         * @param values the set of values to match
         * @throws NullPointerException if `field` is null.
         * @return a query matching documents with this exact value
         */
        fun newSetQuery(
            field: String,
            values: MutableCollection<BytesRef>
        ): Query {
            /*java.util.Objects.requireNonNull<String>(field, "field must not be null")
            java.util.Objects.requireNonNull<MutableCollection<BytesRef>>(
                values,
                "values must not be null"
            )*/
            val indexQuery: Query =
                TermInSetQuery(field, values)
            val dvQuery: Query = TermInSetQuery(
                MultiTermQuery.DOC_VALUES_REWRITE,
                field,
                values
            )
            return IndexOrDocValuesQuery(indexQuery, dvQuery)
        }

        /**
         * Create a new [SortField] for [BytesRef] values.
         *
         * @param field field name. must not be `null`.
         * @param reverse true if natural order should be reversed.
         * @param selector custom selector type for choosing the sort value from the set.
         */
        fun newSortField(
            field: String,
            reverse: Boolean,
            selector: SortedSetSelector.Type
        ): SortField {
            /*java.util.Objects.requireNonNull<String>(field, "field must not be null")
            java.util.Objects.requireNonNull<SortedSetSelector.Type>(
                selector,
                "selector must not be null"
            )*/
            return SortedSetSortField(field, reverse, selector)
        }
    }
}
