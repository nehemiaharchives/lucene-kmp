package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.index.StoredFieldDataInput
import org.gnit.lucenekmp.util.BytesRef


/**
 * A field whose value is stored so that [IndexSearcher.storedFields] and [ ][IndexReader.storedFields] will return the field and its value.
 */
class StoredField : Field {
    /**
     * Expert: allows you to customize the [FieldType].
     *
     * @param name field name
     * @param type custom [FieldType] for this field
     * @throws IllegalArgumentException if the field name or type is null.
     */
    protected constructor(name: String, type: FieldType) : super(name, type)

    /**
     * Expert: allows you to customize the [FieldType].
     *
     *
     * NOTE: the provided byte[] is not copied so be sure not to change it until you're done with
     * this field.
     *
     * @param name field name
     * @param bytes byte array pointing to binary content (not copied)
     * @param type custom [FieldType] for this field
     * @throws IllegalArgumentException if the field name, value or type is null.
     */
    constructor(name: String, bytes: BytesRef, type: FieldType) : super(name, bytes, type)

    /**
     * Create a stored-only field with the given binary value.
     *
     *
     * NOTE: the provided byte[] is not copied so be sure not to change it until you're done with
     * this field.
     *
     * @param name field name
     * @param value byte array pointing to binary content (not copied)
     * @throws IllegalArgumentException if the field name or value is null.
     */
    constructor(name: String, value: ByteArray) : super(name, value, TYPE)

    /**
     * Create a stored-only field with the given binary value.
     *
     *
     * NOTE: the provided byte[] is not copied so be sure not to change it until you're done with
     * this field.
     *
     * @param name field name
     * @param value byte array pointing to binary content (not copied)
     * @param offset starting position of the byte array
     * @param length valid length of the byte array
     * @throws IllegalArgumentException if the field name or value is null.
     */
    constructor(name: String, value: ByteArray, offset: Int, length: Int) : super(
        name,
        value,
        offset,
        length,
        TYPE
    )

    /**
     * Create a stored-only field with the given binary value.
     *
     *
     * NOTE: the provided BytesRef is not copied so be sure not to change it until you're done with
     * this field.
     *
     * @param name field name
     * @param value BytesRef pointing to binary content (not copied)
     * @throws IllegalArgumentException if the field name or value is null.
     */
    constructor(name: String, value: BytesRef) : super(name, value, TYPE)

    /**
     * Create a stored-only field with the given data input value.
     *
     * @param name field name
     * @param value BytesRef pointing to binary content (not copied)
     * @throws IllegalArgumentException if the field name or value is null.
     */
    constructor(name: String, value: StoredFieldDataInput) : super(name, TYPE) {
        requireNotNull(value) { "store field data input must not be null" }
        fieldsData = value
    }

    /**
     * Create a stored-only field with the given string value.
     *
     * @param name field name
     * @param value string value
     * @throws IllegalArgumentException if the field name or value is null.
     */
    constructor(name: String, value: String) : super(name, value, TYPE)

    /**
     * Expert: allows you to customize the [FieldType].
     *
     * @param name field name
     * @param value string value
     * @param type custom [FieldType] for this field
     * @throws IllegalArgumentException if the field name, value or type is null.
     */
    constructor(name: String, value: String, type: FieldType) : super(name, value, type)

    /**
     * Expert: allows you to customize the [FieldType].
     *
     * @param name field name
     * @param value CharSequence value
     * @param type custom [FieldType] for this field
     * @throws IllegalArgumentException if the field name, value or type is null.
     */
    constructor(name: String, value: CharSequence, type: FieldType) : super(name, value, type)

    // TODO: not great but maybe not a big problem?
    /**
     * Create a stored-only field with the given integer value.
     *
     * @param name field name
     * @param value integer value
     * @throws IllegalArgumentException if the field name is null.
     */
    constructor(name: String, value: Int) : super(name, TYPE) {
        fieldsData = value
    }

    /**
     * Create a stored-only field with the given float value.
     *
     * @param name field name
     * @param value float value
     * @throws IllegalArgumentException if the field name is null.
     */
    constructor(name: String, value: Float) : super(name, TYPE) {
        fieldsData = value
    }

    /**
     * Create a stored-only field with the given long value.
     *
     * @param name field name
     * @param value long value
     * @throws IllegalArgumentException if the field name is null.
     */
    constructor(name: String, value: Long) : super(name, TYPE) {
        fieldsData = value
    }

    /**
     * Create a stored-only field with the given double value.
     *
     * @param name field name
     * @param value double value
     * @throws IllegalArgumentException if the field name is null.
     */
    constructor(name: String, value: Double) : super(name, TYPE) {
        fieldsData = value
    }

    companion object {
        /** Type for a stored-only field.  */
        val TYPE: FieldType = FieldType()

        init {
            TYPE.setStored(true)
            TYPE.freeze()
        }
    }
}
