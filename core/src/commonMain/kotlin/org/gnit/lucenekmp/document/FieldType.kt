package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.index.DocValuesSkipIndexType
import org.gnit.lucenekmp.index.DocValuesType
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.index.IndexableFieldType
import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.index.VectorEncoding
import org.gnit.lucenekmp.index.VectorSimilarityFunction


/** Describes the properties of a field.  */
class FieldType : IndexableFieldType {
    private var stored = false
    private var tokenized = true
    private var storeTermVectors = false
    private var storeTermVectorOffsets = false
    private var storeTermVectorPositions = false
    private var storeTermVectorPayloads = false
    private var omitNorms = false
    private var indexOptions: IndexOptions = IndexOptions.NONE
    private var frozen = false
    private var docValuesType: DocValuesType = DocValuesType.NONE
    private var docValuesSkipIndex: DocValuesSkipIndexType = DocValuesSkipIndexType.NONE
    private var dimensionCount = 0
    private var indexDimensionCount = 0
    private var dimensionNumBytes = 0
    private var vectorDimension = 0
    private var vectorEncoding: VectorEncoding = VectorEncoding.FLOAT32
    private var vectorSimilarityFunction: VectorSimilarityFunction = VectorSimilarityFunction.EUCLIDEAN
    override var attributes: MutableMap<String, String>? = null
        private set

    /** Create a new mutable FieldType with all of the properties from `ref`  */
    constructor(ref: IndexableFieldType) {
        this.stored = ref.stored()
        this.tokenized = ref.tokenized()
        this.storeTermVectors = ref.storeTermVectors()
        this.storeTermVectorOffsets = ref.storeTermVectorOffsets()
        this.storeTermVectorPositions = ref.storeTermVectorPositions()
        this.storeTermVectorPayloads = ref.storeTermVectorPayloads()
        this.omitNorms = ref.omitNorms()
        this.indexOptions = ref.indexOptions()
        this.docValuesType = ref.docValuesType()
        this.docValuesSkipIndex = ref.docValuesSkipIndexType()
        this.dimensionCount = ref.pointDimensionCount()
        this.indexDimensionCount = ref.pointIndexDimensionCount()
        this.dimensionNumBytes = ref.pointNumBytes()
        this.vectorDimension = ref.vectorDimension()
        this.vectorEncoding = ref.vectorEncoding()
        this.vectorSimilarityFunction = ref.vectorSimilarityFunction()
        if (ref.attributes != null) {
            this.attributes = ref.attributes
        }
        // Do not copy frozen!
    }

    /** Create a new FieldType with default properties.  */
    constructor()

    /**
     * Throws an exception if this FieldType is frozen. Subclasses should call this within setters for
     * additional state.
     */
    protected fun checkIfFrozen() {
        check(!frozen) { "this FieldType is already frozen and cannot be changed" }
    }

    /**
     * Prevents future changes. Note, it is recommended that this is called once the FieldTypes's
     * properties have been set, to prevent unintentional state changes.
     */
    fun freeze() {
        this.frozen = true
    }

    /**
     * {@inheritDoc}
     *
     *
     * The default is `false`.
     *
     * @see .setStored
     */
    public override fun stored(): Boolean {
        return this.stored
    }

    /**
     * Set to `true` to store this field.
     *
     * @param value true if this field should be stored.
     * @throws IllegalStateException if this FieldType is frozen against future modifications.
     * @see .stored
     */
    fun setStored(value: Boolean) {
        checkIfFrozen()
        this.stored = value
    }

    /**
     * {@inheritDoc}
     *
     *
     * The default is `true`.
     *
     * @see .setTokenized
     */
    public override fun tokenized(): Boolean {
        return this.tokenized
    }

    /**
     * Set to `true` to tokenize this field's contents via the configured [Analyzer].
     *
     * @param value true if this field should be tokenized.
     * @throws IllegalStateException if this FieldType is frozen against future modifications.
     * @see .tokenized
     */
    fun setTokenized(value: Boolean) {
        checkIfFrozen()
        this.tokenized = value
    }

    /**
     * {@inheritDoc}
     *
     *
     * The default is `false`.
     *
     * @see .setStoreTermVectors
     */
    public override fun storeTermVectors(): Boolean {
        return this.storeTermVectors
    }

    /**
     * Set to `true` if this field's indexed form should be also stored into term vectors.
     *
     * @param value true if this field should store term vectors.
     * @throws IllegalStateException if this FieldType is frozen against future modifications.
     * @see .storeTermVectors
     */
    fun setStoreTermVectors(value: Boolean) {
        checkIfFrozen()
        this.storeTermVectors = value
    }

    /**
     * {@inheritDoc}
     *
     *
     * The default is `false`.
     *
     * @see .setStoreTermVectorOffsets
     */
    public override fun storeTermVectorOffsets(): Boolean {
        return this.storeTermVectorOffsets
    }

    /**
     * Set to `true` to also store token character offsets into the term vector for this
     * field.
     *
     * @param value true if this field should store term vector offsets.
     * @throws IllegalStateException if this FieldType is frozen against future modifications.
     * @see .storeTermVectorOffsets
     */
    fun setStoreTermVectorOffsets(value: Boolean) {
        checkIfFrozen()
        this.storeTermVectorOffsets = value
    }

    /**
     * {@inheritDoc}
     *
     *
     * The default is `false`.
     *
     * @see .setStoreTermVectorPositions
     */
    public override fun storeTermVectorPositions(): Boolean {
        return this.storeTermVectorPositions
    }

    /**
     * Set to `true` to also store token positions into the term vector for this field.
     *
     * @param value true if this field should store term vector positions.
     * @throws IllegalStateException if this FieldType is frozen against future modifications.
     * @see .storeTermVectorPositions
     */
    fun setStoreTermVectorPositions(value: Boolean) {
        checkIfFrozen()
        this.storeTermVectorPositions = value
    }

    /**
     * {@inheritDoc}
     *
     *
     * The default is `false`.
     *
     * @see .setStoreTermVectorPayloads
     */
    public override fun storeTermVectorPayloads(): Boolean {
        return this.storeTermVectorPayloads
    }

    /**
     * Set to `true` to also store token payloads into the term vector for this field.
     *
     * @param value true if this field should store term vector payloads.
     * @throws IllegalStateException if this FieldType is frozen against future modifications.
     * @see .storeTermVectorPayloads
     */
    fun setStoreTermVectorPayloads(value: Boolean) {
        checkIfFrozen()
        this.storeTermVectorPayloads = value
    }

    /**
     * {@inheritDoc}
     *
     *
     * The default is `false`.
     *
     * @see .setOmitNorms
     */
    public override fun omitNorms(): Boolean {
        return this.omitNorms
    }

    /**
     * Set to `true` to omit normalization values for the field.
     *
     * @param value true if this field should omit norms.
     * @throws IllegalStateException if this FieldType is frozen against future modifications.
     * @see .omitNorms
     */
    fun setOmitNorms(value: Boolean) {
        checkIfFrozen()
        this.omitNorms = value
    }

    /**
     * {@inheritDoc}
     *
     *
     * The default is [IndexOptions.DOCS_AND_FREQS_AND_POSITIONS].
     *
     * @see .setIndexOptions
     */
    public override fun indexOptions(): IndexOptions {
        return this.indexOptions
    }

    /**
     * Sets the indexing options for the field:
     *
     * @param value indexing options
     * @throws IllegalStateException if this FieldType is frozen against future modifications.
     * @see .indexOptions
     */
    fun setIndexOptions(value: IndexOptions) {
        checkIfFrozen()
        if (value == null) {
            throw NullPointerException("IndexOptions must not be null")
        }
        this.indexOptions = value
    }

    /** Enables points indexing.  */
    fun setDimensions(dimensionCount: Int, dimensionNumBytes: Int) {
        this.setDimensions(dimensionCount, dimensionCount, dimensionNumBytes)
    }

    /** Enables points indexing with selectable dimension indexing.  */
    fun setDimensions(dimensionCount: Int, indexDimensionCount: Int, dimensionNumBytes: Int) {
        checkIfFrozen()
        require(dimensionCount >= 0) { "dimensionCount must be >= 0; got $dimensionCount" }
        require(!(dimensionCount > PointValues.MAX_DIMENSIONS)) { "dimensionCount must be <= " + PointValues.MAX_DIMENSIONS + "; got " + dimensionCount }
        require(indexDimensionCount >= 0) { "indexDimensionCount must be >= 0; got $indexDimensionCount" }
        require(indexDimensionCount <= dimensionCount) {
            ("indexDimensionCount must be <= dimensionCount: "
                    + dimensionCount
                    + "; got "
                    + indexDimensionCount)
        }
        require(!(indexDimensionCount > PointValues.MAX_INDEX_DIMENSIONS)) {
            ("indexDimensionCount must be <= "
                    + PointValues.MAX_INDEX_DIMENSIONS
                    + "; got "
                    + indexDimensionCount)
        }
        require(dimensionNumBytes >= 0) { "dimensionNumBytes must be >= 0; got $dimensionNumBytes" }
        require(!(dimensionNumBytes > PointValues.MAX_NUM_BYTES)) {
            ("dimensionNumBytes must be <= "
                    + PointValues.MAX_NUM_BYTES
                    + "; got "
                    + dimensionNumBytes)
        }
        if (dimensionCount == 0) {
            require(indexDimensionCount == 0) { "when dimensionCount is 0, indexDimensionCount must be 0; got $indexDimensionCount" }
            require(dimensionNumBytes == 0) { "when dimensionCount is 0, dimensionNumBytes must be 0; got $dimensionNumBytes" }
        } else require(indexDimensionCount != 0) {
            ("when dimensionCount is > 0, indexDimensionCount must be > 0; got "
                    + indexDimensionCount)
        }
        require(dimensionNumBytes != 0) { "when dimensionNumBytes is 0, dimensionCount must be 0; got $dimensionCount" }

        this.dimensionCount = dimensionCount
        this.indexDimensionCount = indexDimensionCount
        this.dimensionNumBytes = dimensionNumBytes
    }

    public override fun pointDimensionCount(): Int {
        return dimensionCount
    }

    public override fun pointIndexDimensionCount(): Int {
        return indexDimensionCount
    }

    public override fun pointNumBytes(): Int {
        return dimensionNumBytes
    }

    /** Enable vector indexing, with the specified number of dimensions and distance function.  */
    fun setVectorAttributes(
        numDimensions: Int, encoding: VectorEncoding?, similarity: VectorSimilarityFunction?
    ) {
        checkIfFrozen()
        require(numDimensions > 0) { "vector numDimensions must be > 0; got $numDimensions" }
        this.vectorDimension = numDimensions
        this.vectorSimilarityFunction = requireNotNull<VectorSimilarityFunction>(similarity)
        this.vectorEncoding = requireNotNull<VectorEncoding>(encoding)
    }

    public override fun vectorDimension(): Int {
        return vectorDimension
    }

    public override fun vectorEncoding(): VectorEncoding {
        return vectorEncoding
    }

    public override fun vectorSimilarityFunction(): VectorSimilarityFunction {
        return vectorSimilarityFunction
    }

    /**
     * Puts an attribute value.
     *
     *
     * This is a key-value mapping for the field that the codec can use to store additional
     * metadata.
     *
     *
     * If a value already exists for the field, it will be replaced with the new value. This method
     * is not thread-safe, user must not add attributes while other threads are indexing documents
     * with this field type.
     *
     * @lucene.experimental
     */
    fun putAttribute(key: String, value: String): String? {
        checkIfFrozen()
        if (attributes == null) {
            attributes = mutableMapOf<String, String>()
        }
        return attributes!!.put(key, value)
    }

    /** Prints a Field for human consumption.  */
    override fun toString(): String {
        val result = StringBuilder()
        if (stored()) {
            result.append("stored")
        }
        if (indexOptions !== IndexOptions.NONE) {
            if (result.length > 0) result.append(",")
            result.append("indexed")
            if (tokenized()) {
                result.append(",tokenized")
            }
            if (storeTermVectors()) {
                result.append(",termVector")
            }
            if (storeTermVectorOffsets()) {
                result.append(",termVectorOffsets")
            }
            if (storeTermVectorPositions()) {
                result.append(",termVectorPosition")
            }
            if (storeTermVectorPayloads()) {
                result.append(",termVectorPayloads")
            }
            if (omitNorms()) {
                result.append(",omitNorms")
            }
            if (indexOptions !== IndexOptions.DOCS_AND_FREQS_AND_POSITIONS) {
                result.append(",indexOptions=")
                result.append(indexOptions)
            }
        }
        if (dimensionCount != 0) {
            if (result.length > 0) {
                result.append(",")
            }
            result.append("pointDimensionCount=")
            result.append(dimensionCount)
            result.append(",pointIndexDimensionCount=")
            result.append(indexDimensionCount)
            result.append(",pointNumBytes=")
            result.append(dimensionNumBytes)
        }
        if (docValuesType !== DocValuesType.NONE) {
            if (result.length > 0) {
                result.append(",")
            }
            result.append("docValuesType=")
            result.append(docValuesType)
        }

        return result.toString()
    }

    /**
     * {@inheritDoc}
     *
     *
     * The default is `null` (no docValues)
     *
     * @see .setDocValuesType
     */
    public override fun docValuesType(): DocValuesType {
        return docValuesType
    }

    /**
     * Sets the field's DocValuesType
     *
     * @param type DocValues type, or null if no DocValues should be stored.
     * @throws IllegalStateException if this FieldType is frozen against future modifications.
     * @see .docValuesType
     */
    fun setDocValuesType(type: DocValuesType) {
        checkIfFrozen()
        if (type == null) {
            throw NullPointerException("DocValuesType must not be null")
        }
        docValuesType = type
    }

    public override fun docValuesSkipIndexType(): DocValuesSkipIndexType {
        return docValuesSkipIndex
    }

    /**
     * Set whether to enable a skip index for doc values on this field. This is typically useful on
     * fields that are part of the [index sort][IndexWriterConfig.setIndexSort], or that
     * correlate with fields that are part of the index sort, so that values can be expected to be
     * clustered in the doc ID space.
     */
    fun setDocValuesSkipIndexType(docValuesSkipIndex: DocValuesSkipIndexType) {
        checkIfFrozen()
        this.docValuesSkipIndex = docValuesSkipIndex
    }

    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        result = prime * result + dimensionCount
        result = prime * result + indexDimensionCount
        result = prime * result + dimensionNumBytes
        result = prime * result + (if (docValuesType == null) 0 else docValuesType.hashCode())
        result = prime * result + (if (docValuesSkipIndex == null) 0 else docValuesSkipIndex.hashCode())
        result = prime * result + indexOptions.hashCode()
        result = prime * result + (if (omitNorms) 1231 else 1237)
        result = prime * result + (if (storeTermVectorOffsets) 1231 else 1237)
        result = prime * result + (if (storeTermVectorPayloads) 1231 else 1237)
        result = prime * result + (if (storeTermVectorPositions) 1231 else 1237)
        result = prime * result + (if (storeTermVectors) 1231 else 1237)
        result = prime * result + (if (stored) 1231 else 1237)
        result = prime * result + (if (tokenized) 1231 else 1237)
        return result
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) return true
        if (obj == null) return false
        if (this::class != obj::class) return false
        val other = obj as FieldType
        if (dimensionCount != other.dimensionCount) return false
        if (indexDimensionCount != other.indexDimensionCount) return false
        if (dimensionNumBytes != other.dimensionNumBytes) return false
        if (docValuesType !== other.docValuesType) return false
        if (docValuesSkipIndex !== other.docValuesSkipIndex) return false
        if (indexOptions !== other.indexOptions) return false
        if (omitNorms != other.omitNorms) return false
        if (storeTermVectorOffsets != other.storeTermVectorOffsets) return false
        if (storeTermVectorPayloads != other.storeTermVectorPayloads) return false
        if (storeTermVectorPositions != other.storeTermVectorPositions) return false
        if (storeTermVectors != other.storeTermVectors) return false
        if (stored != other.stored) return false
        if (tokenized != other.tokenized) return false
        return true
    }
}
