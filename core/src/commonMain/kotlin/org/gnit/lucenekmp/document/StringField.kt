package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.util.BytesRef

/**
 * A field that is indexed but not tokenized, indexing the entire string as a single token.
 * This is a simplified port of Lucene's StringField.
 */
class StringField : Field {
    private var binaryValue: BytesRef?
    private val storedValue: StoredValue?

    constructor(name: String, value: String, store: Store) :
        super(name, value, if (store == Store.YES) TYPE_STORED else TYPE_NOT_STORED) {
        binaryValue = BytesRef(value)
        storedValue = if (store == Store.YES) StoredValue(value) else null
    }

    constructor(name: String, value: BytesRef, store: Store) :
        super(name, value, if (store == Store.YES) TYPE_STORED else TYPE_NOT_STORED) {
        binaryValue = value
        storedValue = if (store == Store.YES) StoredValue(value) else null
    }

    override fun invertableType(): InvertableType = InvertableType.BINARY

    override fun binaryValue(): BytesRef? = binaryValue

    override fun setStringValue(value: String) {
        super.setStringValue(value)
        binaryValue = BytesRef(value)
        storedValue?.setStringValue(value)
    }

    override fun setBytesValue(value: BytesRef) {
        super.setBytesValue(value)
        binaryValue = value
        storedValue?.setBinaryValue(value)
    }

    override fun storedValue(): StoredValue? = storedValue

    companion object {
        val TYPE_NOT_STORED: FieldType = FieldType().apply {
            setOmitNorms(true)
            setIndexOptions(IndexOptions.DOCS)
            setTokenized(false)
            freeze()
        }

        val TYPE_STORED: FieldType = FieldType().apply {
            setOmitNorms(true)
            setIndexOptions(IndexOptions.DOCS)
            setStored(true)
            setTokenized(false)
            freeze()
        }
    }
}
