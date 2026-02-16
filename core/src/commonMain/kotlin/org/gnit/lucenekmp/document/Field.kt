package org.gnit.lucenekmp.document

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.BytesTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.index.DocValuesType
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.index.IndexableField
import org.gnit.lucenekmp.index.IndexableFieldType
import org.gnit.lucenekmp.index.StoredFieldDataInput
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.jdkport.Reader


/**
 * Expert: directly create a field for a document. Most users should use one of the sugar
 * subclasses:
 *
 *
 *  * [TextField]: [Reader] or [String] indexed for full-text search
 *  * [StringField]: [String] indexed verbatim as a single token
 *  * [IntField]: `int` indexed for exact/range queries.
 *  * [LongField]: `long` indexed for exact/range queries.
 *  * [FloatField]: `float` indexed for exact/range queries.
 *  * [DoubleField]: `double` indexed for exact/range queries.
 *  * [SortedDocValuesField]: `byte[]` indexed column-wise for sorting/faceting
 *  * [SortedSetDocValuesField]: `SortedSet<byte[]>` indexed column-wise for
 * sorting/faceting
 *  * [NumericDocValuesField]: `long` indexed column-wise for sorting/faceting
 *  * [SortedNumericDocValuesField]: `SortedSet<long>` indexed column-wise for
 * sorting/faceting
 *  * [StoredField]: Stored-only value for retrieving in summary results
 *
 *
 *
 * A field is a section of a Document. Each field has three parts: name, type and value. Values
 * may be text (String, Reader or pre-analyzed TokenStream), binary (byte[]), or numeric (a Number).
 * Fields are optionally stored in the index, so that they may be returned with hits on the
 * document.
 *
 *
 * NOTE: the field type is an [IndexableFieldType]. Making changes to the state of the
 * IndexableFieldType will impact any Field it is used in. It is strongly recommended that no
 * changes be made after Field instantiation.
 */
open class Field : IndexableField {
    /** Field's type  */
    protected val type: IndexableFieldType

    /** Field's name  */
    protected val name: String

    /** Field's value  */
    protected lateinit var fieldsData: Any

    protected fun isFieldsDataInitialized(): Boolean {
        return this::fieldsData.isInitialized
    }

    /**
     * Expert: creates a field with no initial value. This is intended to be used by custom [ ] sub-classes with pre-configured [IndexableFieldType]s.
     *
     * @param name field name
     * @param type field type
     * @throws IllegalArgumentException if either the name or type is null.
     */
    protected constructor(name: String, type: IndexableFieldType) {
        requireNotNull(name) { "name must not be null" }
        this.name = name
        requireNotNull(type) { "type must not be null" }
        this.type = type
    }

    /**
     * Create field with Reader value.
     *
     * @param name field name
     * @param reader reader value
     * @param type field type
     * @throws IllegalArgumentException if either the name or type is null, or if the field's type is
     * stored(), or if tokenized() is false.
     */
    constructor(name: String, reader: Reader, type: IndexableFieldType) {
        requireNotNull(name) { "name must not be null" }
        requireNotNull(type) { "type must not be null" }

        require(!type.stored()) { "fields with a Reader value cannot be stored" }
        require(!(type.indexOptions() !== IndexOptions.NONE && !type.tokenized())) { "non-tokenized fields must use String values" }

        this.name = name
        this.fieldsData = reader
        this.type = type
    }

    /**
     * Create field with TokenStream value.
     *
     * @param name field name
     * @param tokenStream TokenStream value
     * @param type field type
     * @throws IllegalArgumentException if either the name or type is null, or if the field's type is
     * stored(), or if tokenized() is false, or if indexed() is false.
     */
    constructor(name: String, tokenStream: TokenStream, type: IndexableFieldType) {
        requireNotNull(name) { "name must not be null" }
        require(!(type.indexOptions() === IndexOptions.NONE || !type.tokenized())) { "TokenStream fields must be indexed and tokenized" }
        require(!type.stored()) { "TokenStream fields cannot be stored" }

        this.name = name
        this.fieldsData = tokenStream
        this.type = type
    }

    /**
     * Create field with binary value.
     *
     *
     * NOTE: the provided byte[] is not copied so be sure not to change it until you're done with
     * this field.
     *
     * @param name field name
     * @param value byte array pointing to binary content (not copied)
     * @param type field type
     * @throws IllegalArgumentException if the field name, value or type is null, or the field's type
     * is indexed().
     */
    constructor(name: String, value: ByteArray, type: IndexableFieldType) : this(name, value, 0, value.size, type)

    /**
     * Create field with binary value.
     *
     *
     * NOTE: the provided byte[] is not copied so be sure not to change it until you're done with
     * this field.
     *
     * @param name field name
     * @param value byte array pointing to binary content (not copied)
     * @param offset starting position of the byte array
     * @param length valid length of the byte array
     * @param type field type
     * @throws IllegalArgumentException if the field name, value or type is null, or the field's type
     * is indexed().
     */
    constructor(name: String, value: ByteArray, offset: Int, length: Int, type: IndexableFieldType) : this(
        name,
        BytesRef(value, offset, length) ,
        type
    )

    /**
     * Create field with binary value.
     *
     *
     * NOTE: the provided BytesRef is not copied so be sure not to change it until you're done with
     * this field.
     *
     * @param name field name
     * @param bytes BytesRef pointing to binary content (not copied)
     * @param type field type
     * @throws IllegalArgumentException if the field name, bytes or type is null, or the field's type
     * is indexed().
     */
    constructor(name: String, bytes: BytesRef, type: IndexableFieldType) {
        requireNotNull(name) { "name must not be null" }
        requireNotNull(bytes) { "bytes must not be null" }
        requireNotNull(type) { "type must not be null" }
        require(
            !(type.indexOptions().compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS) >= 0
                    || type.storeTermVectorOffsets())
        ) { "It doesn't make sense to index offsets on binary fields" }
        require(!(type.indexOptions() !== IndexOptions.NONE && type.tokenized())) { "cannot set a BytesRef value on a tokenized field" }
        require(!(type.indexOptions() === IndexOptions.NONE && type.pointDimensionCount() === 0 && type.docValuesType() === DocValuesType.NONE && type.stored() === false)) { "it doesn't make sense to have a field that is neither indexed, nor doc-valued, nor stored" }
        this.name = name
        this.fieldsData = bytes
        this.type = type
    }

    // TODO: allow direct construction of int, long, float, double value too..?
    /**
     * Create field with String value.
     *
     * @param name field name
     * @param value string value
     * @param type field type
     * @throws IllegalArgumentException if either the name, value or type is null, or if the field's
     * type is neither indexed() nor stored(), or if indexed() is false but storeTermVectors() is
     * true.
     */
    constructor(name: String, value: CharSequence, type: IndexableFieldType) {
        requireNotNull(name) { "name must not be null" }
        requireNotNull(value) { "value must not be null" }
        requireNotNull(type) { "type must not be null" }
        require(!(type.stored() === false && type.indexOptions() === IndexOptions.NONE)) { "it doesn't make sense to have a field that is neither indexed nor stored" }
        this.name = name
        this.fieldsData = value
        this.type = type
    }

    /**
     * The value of the field as a String, or null. If null, the Reader value or binary value is used.
     * Exactly one of stringValue(), readerValue(), and binaryValue() must be set.
     */
    override fun stringValue(): String? {
        if (fieldsData is CharSequence || fieldsData is Number) {
            return fieldsData.toString()
        } else {
            return null
        }
    }

    override val charSequenceValue: CharSequence?
        get() = if (fieldsData is CharSequence) fieldsData as CharSequence else stringValue()

    /**
     * The value of the field as a Reader, or null. If null, the String value or binary value is used.
     * Exactly one of stringValue(), readerValue(), and binaryValue() must be set.
     */
    override fun readerValue(): Reader? {
        return if (fieldsData is Reader) fieldsData as Reader else null
    }

    /**
     * The TokenStream for this field to be used when indexing, or null. If null, the Reader value or
     * String value is analyzed to produce the indexed tokens.
     */
    fun tokenStreamValue(): TokenStream? {
        return if (fieldsData is TokenStream) fieldsData as TokenStream else null
    }

    /**
     * Expert: change the value of this field. This can be used during indexing to re-use a single
     * Field instance to improve indexing speed by avoiding GC cost of new'ing and reclaiming Field
     * instances. Typically a single [Document] instance is re-used as well. This helps most on
     * small documents.
     *
     *
     * Each Field instance should only be used once within a single [Document] instance. See
     * [ImproveIndexingSpeed](http://wiki.apache.org/lucene-java/ImproveIndexingSpeed) for
     * details.
     */
    open fun setStringValue(value: String) {
        require(fieldsData is String) { "cannot change value type from " + fieldsData::class.simpleName + " to String" }
        requireNotNull(value) { "value must not be null" }
        fieldsData = value
    }

    /** Expert: change the value of this field. See [.setStringValue].  */
    fun setReaderValue(value: Reader) {
        require(fieldsData is Reader) { "cannot change value type from " + fieldsData::class.simpleName + " to Reader" }
        fieldsData = value
    }

    /** Expert: change the value of this field. See [.setStringValue].  */
    fun setBytesValue(value: ByteArray) {
        setBytesValue(BytesRef(value))
    }

    /**
     * Expert: change the value of this field. See [.setStringValue].
     *
     *
     * NOTE: the provided BytesRef is not copied so be sure not to change it until you're done with
     * this field.
     */
    open fun setBytesValue(value: BytesRef) {
        require(fieldsData is BytesRef) {
            ("cannot change value type from "
                    + fieldsData::class.simpleName
                    + " to BytesRef")
        }
        requireNotNull(value) { "value must not be null" }
        fieldsData = value
    }

    /** Expert: change the value of this field. See [.setStringValue].  */
    fun setByteValue(value: Byte) {
        require(fieldsData is Byte) { "cannot change value type from " + fieldsData::class.simpleName + " to Byte" }
        fieldsData = value
    }

    /** Expert: change the value of this field. See [.setStringValue].  */
    fun setShortValue(value: Short) {
        require(fieldsData is Short) { "cannot change value type from " + fieldsData::class.simpleName + " to Short" }
        fieldsData = value
    }

    /** Expert: change the value of this field. See [.setStringValue].  */
    open fun setIntValue(value: Int) {
        require(fieldsData is Int) { "cannot change value type from " + fieldsData::class.simpleName + " to Integer" }
        fieldsData = value
    }

    /** Expert: change the value of this field. See [.setStringValue].  */
    open fun setLongValue(value: Long) {
        require(fieldsData is Long) { "cannot change value type from " + fieldsData::class.simpleName + " to Long" }
        fieldsData = value
    }

    /** Expert: change the value of this field. See [.setStringValue].  */
    open fun setFloatValue(value: Float) {
        require(fieldsData is Float) { "cannot change value type from " + fieldsData::class.simpleName + " to Float" }
        fieldsData = value
    }

    /** Expert: change the value of this field. See [.setStringValue].  */
    open fun setDoubleValue(value: Double) {
        require(fieldsData is Double) { "cannot change value type from " + fieldsData::class.simpleName + " to Double" }
        fieldsData = value
    }

    /** Expert: sets the token stream to be used for indexing.  */
    fun setTokenStream(tokenStream: TokenStream) {
        require(fieldsData is TokenStream) {
            ("cannot change value type from "
                    + fieldsData::class.simpleName
                    + " to TokenStream")
        }
        this.fieldsData = tokenStream
    }

    override fun name(): String {
        return name
    }

    override fun numericValue(): Number? {
        if (fieldsData is Number) {
            return fieldsData as Number
        } else {
            return null
        }
    }

    override fun binaryValue(): BytesRef? {
        if (fieldsData is BytesRef) {
            return fieldsData as BytesRef
        } else {
            return null
        }
    }

    /** Prints a Field for human consumption.  */
    override fun toString(): String {
        val result = StringBuilder()
        result.append(type.toString())
        result.append('<')
        result.append(name)
        result.append(':')

        if (fieldsData != null) {
            result.append(fieldsData)
        }

        result.append('>')
        return result.toString()
    }

    /** Returns the [FieldType] for this field.  */
    override fun fieldType(): IndexableFieldType {
        return type
    }

    override fun invertableType(): InvertableType {
        return InvertableType.TOKEN_STREAM
    }

    override fun tokenStream(analyzer: Analyzer, reuse: TokenStream?): TokenStream? {
        var reuse: TokenStream? = reuse
        if (fieldType().indexOptions() === IndexOptions.NONE) {
            // Not indexed
            return null
        }

        if (!fieldType().tokenized()) {
            if (stringValue() != null) {
                if (reuse !is StringTokenStream) {
                    // lazy init the TokenStream as it is heavy to instantiate
                    // (attributes,...) if not needed
                    reuse = StringTokenStream()
                }
                reuse.setValue(stringValue())
                return reuse
            } else if (binaryValue() != null) {
                if (reuse !is BinaryTokenStream) {
                    // lazy init the TokenStream as it is heavy to instantiate
                    // (attributes,...) if not needed
                    reuse = BinaryTokenStream()
                }
                reuse.value = binaryValue()
                return reuse
            } else {
                throw IllegalArgumentException("Non-Tokenized Fields must have a String value")
            }
        }

        if (tokenStreamValue() != null) {
            return tokenStreamValue()
        } else if (readerValue() != null) {
            return analyzer.tokenStream(name(), readerValue()!!)
        } else if (stringValue() != null) {
            return analyzer.tokenStream(name(), stringValue()!!)
        }

        throw IllegalArgumentException(
            "Field must have either TokenStream, String, Reader or Number value; got $this"
        )
    }

    private class BinaryTokenStream
    /**
     * Creates a new TokenStream that returns a BytesRef as single token.
     *
     *
     * Warning: Does not initialize the value, you must call [.setValue]
     * afterwards!
     */
        : TokenStream() {
        private val bytesAtt: BytesTermAttribute = addAttribute(BytesTermAttribute::class)
        private var used = true
        var value: BytesRef? = null

        override fun incrementToken(): Boolean {
            if (used) {
                return false
            }
            clearAttributes()
            bytesAtt.setBytesRef(value)
            used = true
            return true
        }

        override fun reset() {
            used = false
        }

        override fun close() {
            value = null
        }
    }

    private class StringTokenStream
    /**
     * Creates a new TokenStream that returns a String as single token.
     *
     *
     * Warning: Does not initialize the value, you must call [.setValue]
     * afterwards!
     */
        : TokenStream() {
        private val termAttribute: CharTermAttribute = addAttribute(CharTermAttribute::class)
        private val offsetAttribute: OffsetAttribute = addAttribute(OffsetAttribute::class)
        private var used = true
        private var value: String? = null

        /** Sets the string value.  */
        fun setValue(value: String?) {
            this.value = value
        }

        override fun incrementToken(): Boolean {
            if (used) {
                return false
            }
            clearAttributes()
            termAttribute.append(value)
            offsetAttribute.setOffset(0, value!!.length)
            used = true
            return true
        }

        @Throws(IOException::class)
        override fun end() {
            super.end()
            val finalOffset = value!!.length
            offsetAttribute.setOffset(finalOffset, finalOffset)
        }

        override fun reset() {
            used = false
        }

        override fun close() {
            value = null
        }
    }

    /** Specifies whether and how a field should be stored.  */
    enum class Store {
        /**
         * Store the original field value in the index. This is useful for short texts like a document's
         * title which should be displayed with the results. The value is stored in its original form,
         * i.e. no analyzer is used before it is stored.
         */
        YES,

        /** Do not store the field value in the index.  */
        NO
    }

    override fun storedValue(): StoredValue? {
        if (fieldType().stored() === false) {
            return null
        } else requireNotNull(fieldsData != null) { "fieldsData is unset" }
        if (fieldsData is Int) {
            return StoredValue(fieldsData as Int)
        } else if (fieldsData is Long) {
            return StoredValue(fieldsData as Long)
        } else if (fieldsData is Float) {
            return StoredValue(fieldsData as Float)
        } else if (fieldsData is Double) {
            return StoredValue(fieldsData as Double)
        } else if (fieldsData is BytesRef) {
            return StoredValue(fieldsData as BytesRef)
        } else if (fieldsData is StoredFieldDataInput) {
            return StoredValue(fieldsData as StoredFieldDataInput)
        } else if (fieldsData is String) {
            return StoredValue(fieldsData as String)
        } else {
            throw IllegalStateException("Cannot store value of type " + fieldsData::class)
        }
    }
}
