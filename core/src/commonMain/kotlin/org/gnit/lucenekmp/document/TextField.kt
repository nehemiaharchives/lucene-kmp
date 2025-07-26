package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.jdkport.Reader


/**
 * A field that is indexed and tokenized, without term vectors. For example this would be used on a
 * 'body' field, that contains the bulk of a document's text.
 */
class TextField : Field {
    // TODO: add sugar for term vectors...
    private var storedValue: StoredValue?

    /**
     * Creates a new un-stored TextField with Reader value.
     *
     * @param name field name
     * @param reader reader value
     * @throws IllegalArgumentException if the field name is null
     * @throws NullPointerException if the reader is null
     */
    constructor(name: String, reader: Reader) : super(name, reader, TYPE_NOT_STORED) {
        storedValue = null
    }

    /**
     * Creates a new TextField with String value.
     *
     * @param name field name
     * @param value string value
     * @param store Store.YES if the content should also be stored
     * @throws IllegalArgumentException if the field name or value is null.
     */
    constructor(name: String, value: String, store: Store) : super(
        name,
        value,
        if (store === Store.YES) TYPE_STORED else TYPE_NOT_STORED
    ) {
        storedValue = if (store === Store.YES) {
            StoredValue(value)
        } else {
            null
        }
    }

    /**
     * Creates a new un-stored TextField with TokenStream value.
     *
     * @param name field name
     * @param stream TokenStream value
     * @throws IllegalArgumentException if the field name is null.
     * @throws NullPointerException if the tokenStream is null
     */
    constructor(name: String, stream: TokenStream) : super(name, stream, TYPE_NOT_STORED) {
        storedValue = null
    }

    override fun setStringValue(value: String) {
        super.setStringValue(value)
        if (storedValue != null) {
            storedValue!!.stringValue = value
        }
    }

    override fun storedValue(): StoredValue? {
        return storedValue
    }

    companion object {
        /** Indexed, tokenized, not stored.  */
        val TYPE_NOT_STORED: FieldType = FieldType()

        /** Indexed, tokenized, stored.  */
        val TYPE_STORED: FieldType = FieldType()

        init {
            TYPE_NOT_STORED.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS)
            TYPE_NOT_STORED.setTokenized(true)
            TYPE_NOT_STORED.freeze()

            TYPE_STORED.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS)
            TYPE_STORED.setTokenized(true)
            TYPE_STORED.setStored(true)
            TYPE_STORED.freeze()
        }
    }
}
