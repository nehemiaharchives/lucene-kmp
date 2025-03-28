package org.gnit.lucenekmp.index

/**
 * Access to the Field Info file that describes document fields and whether or not they are indexed.
 * Each segment has a separate Field Info file. Objects of this class are thread-safe for multiple
 * readers, but only one thread can be adding documents at a time, with no other reader or writer
 * threads accessing this object.
 */
class FieldInfo(
    name: String,
    number: Int,
    storeTermVector: Boolean,
    omitNorms: Boolean,
    storePayloads: Boolean,
    indexOptions: IndexOptions,
    docValues: DocValuesType,
    docValuesSkipIndex: DocValuesSkipIndexType,
    dvGen: Long,
    attributes: MutableMap<String, String>,
    pointDimensionCount: Int,
    pointIndexDimensionCount: Int,
    pointNumBytes: Int,
    vectorDimension: Int,
    vectorEncoding: VectorEncoding,
    vectorSimilarityFunction: VectorSimilarityFunction,
    softDeletesField: Boolean,
    isParentField: Boolean
) {
    /**
     * Returns name of this field
     *
     * @return name
     */
    /** Field's name  */
    val name: String

    /**
     * Returns the field number
     *
     * @return field number
     */
    /** Internal field number  */
    val number: Int

    private var docValuesType = DocValuesType.NONE

    private val docValuesSkipIndex: DocValuesSkipIndexType

    // True if any document indexed term vectors
    private var storeTermVector = false

    private var omitNorms = false // omit norms associated with indexed fields

    /** Returns IndexOptions for the field, or IndexOptions.NONE if the field is not indexed  */
    val indexOptions: IndexOptions
    private var storePayloads = false // whether this field stores payloads together with term positions

    private var attributes: MutableMap<String, String>

    private var dvGen: Long

    /** Return point data dimension count  */
    /**
     * If both of these are positive it means this field indexed points (see [ ]).
     */
    var pointDimensionCount: Int
        private set

    /** Return point data dimension count  */
    var pointIndexDimensionCount: Int
        private set

    /** Return number of bytes per dimension  */
    var pointNumBytes: Int
        private set

    /** Returns the number of dimensions of the vector value  */
    // if it is a positive value, it means this field indexes vectors
    val vectorDimension: Int

    /** Returns the number of dimensions of the vector value  */
    val vectorEncoding: VectorEncoding

    /** Returns [VectorSimilarityFunction] for the field  */
    val vectorSimilarityFunction: VectorSimilarityFunction

    /**
     * Returns true if this field is configured and used as the soft-deletes field. See [ ][IndexWriterConfig.softDeletesField]
     */
    // whether this field is used as the soft-deletes field
    val isSoftDeletesField: Boolean

    /**
     * Returns true if this field is configured and used as the parent document field field. See
     * [IndexWriterConfig.setParentField]
     */
    val isParentField: Boolean

    /**
     * Sole constructor.
     *
     * @lucene.experimental
     */
    init {
        this.name = requireNotNull<String>(name)
        this.number = number
        this.docValuesType =
            requireNotNull<DocValuesType>(
                docValues
            ){ "DocValuesType must not be null (field: \"$name\")" }
        this.docValuesSkipIndex = docValuesSkipIndex
        this.indexOptions =
            requireNotNull<IndexOptions>(
                indexOptions
            ){ "IndexOptions must not be null (field: \"$name\")" }
        if (indexOptions !== IndexOptions.NONE) {
            this.storeTermVector = storeTermVector
            this.storePayloads = storePayloads
            this.omitNorms = omitNorms
        } else { // for non-indexed fields, leave defaults
            this.storeTermVector = false
            this.storePayloads = false
            this.omitNorms = false
        }
        this.dvGen = dvGen
        this.attributes = requireNotNull<MutableMap<String, String>>(attributes)
        this.pointDimensionCount = pointDimensionCount
        this.pointIndexDimensionCount = pointIndexDimensionCount
        this.pointNumBytes = pointNumBytes
        this.vectorDimension = vectorDimension
        this.vectorEncoding = vectorEncoding
        this.vectorSimilarityFunction = vectorSimilarityFunction
        this.isSoftDeletesField = softDeletesField
        this.isParentField = isParentField
        this.checkConsistency()
    }

    /**
     * Check correctness of the FieldInfo options
     *
     * @throws IllegalArgumentException if some options are incorrect
     */
    fun checkConsistency() {
        requireNotNull(indexOptions) { "IndexOptions must not be null (field: '$name')" }
        if (indexOptions !== IndexOptions.NONE) {
            // Cannot store payloads unless positions are indexed:
            require(!(indexOptions.compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS) < 0 && storePayloads)) { "indexed field '$name' cannot have payloads without positions" }
        } else {
            require(!storeTermVector) { "non-indexed field '$name' cannot store term vectors" }
            require(!storePayloads) { "non-indexed field '$name' cannot store payloads" }
            require(!omitNorms) { "non-indexed field '$name' cannot omit norms" }
        }

        requireNotNull(docValuesType) { "DocValuesType must not be null (field: '$name')" }
        require(docValuesSkipIndex.isCompatibleWith(docValuesType) != false) {
            ("field '"
                    + name
                    + "' cannot have docValuesSkipIndexType="
                    + docValuesSkipIndex
                    + " with doc values type "
                    + docValuesType)
        }
        require(!(dvGen != -1L && docValuesType === DocValuesType.NONE)) {
            ("field '"
                    + name
                    + "' cannot have a docvalues update generation without having docvalues")
        }

        require(pointDimensionCount >= 0) {
            ("pointDimensionCount must be >= 0; got "
                    + pointDimensionCount
                    + " (field: '"
                    + name
                    + "')")
        }
        require(pointIndexDimensionCount >= 0) {
            ("pointIndexDimensionCount must be >= 0; got "
                    + pointIndexDimensionCount
                    + " (field: '"
                    + name
                    + "')")
        }
        require(pointNumBytes >= 0) { "pointNumBytes must be >= 0; got $pointNumBytes (field: '$name')" }

        require(!(pointDimensionCount != 0 && pointNumBytes == 0)) {
            ("pointNumBytes must be > 0 when pointDimensionCount="
                    + pointDimensionCount
                    + " (field: '"
                    + name
                    + "')")
        }
        require(!(pointIndexDimensionCount != 0 && pointDimensionCount == 0)) {
            ("pointIndexDimensionCount must be 0 when pointDimensionCount=0"
                    + " (field: '"
                    + name
                    + "')")
        }
        require(!(pointNumBytes != 0 && pointDimensionCount == 0)) {
            ("pointDimensionCount must be > 0 when pointNumBytes="
                    + pointNumBytes
                    + " (field: '"
                    + name
                    + "')")
        }

        requireNotNull(vectorSimilarityFunction) { "Vector similarity function must not be null (field: '$name')" }
        require(vectorDimension >= 0) { "vectorDimension must be >=0; got $vectorDimension (field: '$name')" }

        require(!(this.isSoftDeletesField && isParentField)) {
            ("field can't be used as soft-deletes field and parent document field (field: '"
                    + name
                    + "')")
        }
    }

    /**
     * Verify that the provided FieldInfo has the same schema as this FieldInfo
     *
     * @param o – other FieldInfo whose schema is verified against this FieldInfo's schema
     * @throws IllegalArgumentException if the field schemas are not the same
     */
    fun verifySameSchema(o: FieldInfo) {
        val fieldName: String? = this.name
        verifySameIndexOptions(fieldName, this.indexOptions, o.indexOptions)
        if (this.indexOptions !== IndexOptions.NONE) {
            verifySameOmitNorms(fieldName, this.omitNorms, o.omitNorms)
            verifySameStoreTermVectors(fieldName, this.storeTermVector, o.storeTermVector)
        }
        verifySameDocValuesType(fieldName, this.docValuesType, o.docValuesType)
        verifySameDocValuesSkipIndex(fieldName, this.docValuesSkipIndex, o.docValuesSkipIndex)
        verifySamePointsOptions(
            fieldName,
            this.pointDimensionCount,
            this.pointIndexDimensionCount,
            this.pointNumBytes,
            o.pointDimensionCount,
            o.pointIndexDimensionCount,
            o.pointNumBytes
        )
        verifySameVectorOptions(
            fieldName,
            this.vectorDimension,
            this.vectorEncoding,
            this.vectorSimilarityFunction,
            o.vectorDimension,
            o.vectorEncoding,
            o.vectorSimilarityFunction
        )
    }

    /**
     * Record that this field is indexed with points, with the specified number of dimensions and
     * bytes per dimension.
     */
    fun setPointDimensions(dimensionCount: Int, indexDimensionCount: Int, numBytes: Int) {
        require(dimensionCount > 0) {
            ("point dimension count must be >= 0; got "
                    + dimensionCount
                    + " for field=\""
                    + name
                    + "\"")
        }
        require(!(indexDimensionCount > PointValues.MAX_INDEX_DIMENSIONS)) {
            ("point index dimension count must be < PointValues.MAX_INDEX_DIMENSIONS (= "
                    + PointValues.MAX_INDEX_DIMENSIONS
                    + "); got "
                    + indexDimensionCount
                    + " for field=\""
                    + name
                    + "\"")
        }
        require(indexDimensionCount <= dimensionCount) {
            ("point index dimension count must be <= point dimension count (= "
                    + dimensionCount
                    + "); got "
                    + indexDimensionCount
                    + " for field=\""
                    + name
                    + "\"")
        }
        require(numBytes > 0) { "point numBytes must be >= 0; got $numBytes for field=\"$name\"" }
        require(!(numBytes > PointValues.MAX_NUM_BYTES)) {
            ("point numBytes must be <= PointValues.MAX_NUM_BYTES (= "
                    + PointValues.MAX_NUM_BYTES
                    + "); got "
                    + numBytes
                    + " for field=\""
                    + name
                    + "\"")
        }
        require(!(pointDimensionCount != 0 && pointDimensionCount != dimensionCount)) {
            ("cannot change point dimension count from "
                    + pointDimensionCount
                    + " to "
                    + dimensionCount
                    + " for field=\""
                    + name
                    + "\"")
        }
        require(!(pointIndexDimensionCount != 0 && pointIndexDimensionCount != indexDimensionCount)) {
            ("cannot change point index dimension count from "
                    + pointIndexDimensionCount
                    + " to "
                    + indexDimensionCount
                    + " for field=\""
                    + name
                    + "\"")
        }
        require(!(pointNumBytes != 0 && pointNumBytes != numBytes)) {
            ("cannot change point numBytes from "
                    + pointNumBytes
                    + " to "
                    + numBytes
                    + " for field=\""
                    + name
                    + "\"")
        }

        pointDimensionCount = dimensionCount
        pointIndexDimensionCount = indexDimensionCount
        pointNumBytes = numBytes

        this.checkConsistency()
    }

    /** Record that this field is indexed with docvalues, with the specified type  */
    fun setDocValuesType(type: DocValuesType) {
        if (type == null) {
            throw NullPointerException("DocValuesType must not be null (field: \"$name\")")
        }
        require(!(docValuesType !== DocValuesType.NONE && type !== DocValuesType.NONE && docValuesType !== type)) {
            ("cannot change DocValues type from "
                    + docValuesType
                    + " to "
                    + type
                    + " for field \""
                    + name
                    + "\"")
        }
        docValuesType = type
        this.checkConsistency()
    }

    /**
     * Returns [DocValuesType] of the docValues; this is `DocValuesType.NONE` if the field
     * has no docvalues.
     */
    fun getDocValuesType(): DocValuesType {
        return docValuesType
    }

    /** Returns true if, and only if, this field has a skip index.  */
    fun docValuesSkipIndexType(): DocValuesSkipIndexType {
        return docValuesSkipIndex
    }

    var docValuesGen: Long
        /** Returns the docValues generation of this field, or -1 if no docValues updates exist for it.  */
        get() = dvGen
        /** Sets the docValues generation of this field.  */
        set(dvGen) {
            this.dvGen = dvGen
            this.checkConsistency()
        }

    fun setStoreTermVectors() {
        storeTermVector = true
        this.checkConsistency()
    }

    fun setStorePayloads() {
        if (indexOptions.compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS) >= 0) {
            storePayloads = true
        }
        this.checkConsistency()
    }

    /** Returns true if norms are explicitly omitted for this field  */
    fun omitsNorms(): Boolean {
        return omitNorms
    }

    /** Omit norms for this field.  */
    fun setOmitsNorms() {
        check(indexOptions !== IndexOptions.NONE) { "cannot omit norms: this field is not indexed" }
        omitNorms = true
        this.checkConsistency()
    }

    /** Returns true if this field actually has any norms.  */
    fun hasNorms(): Boolean {
        return indexOptions !== IndexOptions.NONE && omitNorms == false
    }

    /** Returns true if any payloads exist for this field.  */
    fun hasPayloads(): Boolean {
        return storePayloads
    }

    /** Returns true if any term vectors exist for this field.  */
    fun hasTermVectors(): Boolean {
        return storeTermVector
    }

    /** Returns whether any (numeric) vector values exist for this field  */
    fun hasVectorValues(): Boolean {
        return vectorDimension > 0
    }

    /** Get a codec attribute value, or null if it does not exist  */
    fun getAttribute(key: String?): String? {
        return attributes.get(key)
    }

    /**
     * Puts a codec attribute value.
     *
     *
     * This is a key-value mapping for the field that the codec can use to store additional
     * metadata, and will be available to the codec when reading the segment via [ ][.getAttribute]
     *
     *
     * If a value already exists for the key in the field, it will be replaced with the new value.
     * If the value of the attributes for a same field is changed between the documents, the behaviour
     * after merge is undefined.
     */
    fun putAttribute(key: String, value: String): String? {
        val newMap: MutableMap<String, String> = attributes
        val oldValue: String? = newMap[key]
        newMap[key] = value
        // This needs to be thread-safe as multiple threads may be updating (different) attributes
        // concurrently due to concurrent merging.
        attributes = newMap
        return oldValue
    }

    /** Returns internal codec attributes map.  */
    fun attributes(): MutableMap<String, String> {
        return attributes
    }

    companion object {
        /**
         * Verify that the provided index options are the same
         *
         * @throws IllegalArgumentException if they are not the same
         */
        fun verifySameIndexOptions(
            fieldName: String?, indexOptions1: IndexOptions?, indexOptions2: IndexOptions?
        ) {
            require(indexOptions1 === indexOptions2) {
                ("cannot change field \""
                        + fieldName
                        + "\" from index options="
                        + indexOptions1
                        + " to inconsistent index options="
                        + indexOptions2)
            }
        }

        /**
         * Verify that the provided docValues type are the same
         *
         * @throws IllegalArgumentException if they are not the same
         */
        fun verifySameDocValuesType(
            fieldName: String?, docValuesType1: DocValuesType?, docValuesType2: DocValuesType?
        ) {
            require(docValuesType1 === docValuesType2) {
                ("cannot change field \""
                        + fieldName
                        + "\" from doc values type="
                        + docValuesType1
                        + " to inconsistent doc values type="
                        + docValuesType2)
            }
        }

        /**
         * Verify that the provided docValues type are the same
         *
         * @throws IllegalArgumentException if they are not the same
         */
        fun verifySameDocValuesSkipIndex(
            fieldName: String?,
            hasDocValuesSkipIndex1: DocValuesSkipIndexType?,
            hasDocValuesSkipIndex2: DocValuesSkipIndexType?
        ) {
            require(hasDocValuesSkipIndex1 === hasDocValuesSkipIndex2) {
                ("cannot change field \""
                        + fieldName
                        + "\" from docValuesSkipIndexType="
                        + hasDocValuesSkipIndex1
                        + " to inconsistent docValuesSkipIndexType="
                        + hasDocValuesSkipIndex2)
            }
        }

        /**
         * Verify that the provided store term vectors options are the same
         *
         * @throws IllegalArgumentException if they are not the same
         */
        fun verifySameStoreTermVectors(
            fieldName: String?, storeTermVector1: Boolean, storeTermVector2: Boolean
        ) {
            require(storeTermVector1 == storeTermVector2) {
                ("cannot change field \""
                        + fieldName
                        + "\" from storeTermVector="
                        + storeTermVector1
                        + " to inconsistent storeTermVector="
                        + storeTermVector2)
            }
        }

        /**
         * Verify that the provided omitNorms are the same
         *
         * @throws IllegalArgumentException if they are not the same
         */
        fun verifySameOmitNorms(fieldName: String?, omitNorms1: Boolean, omitNorms2: Boolean) {
            require(omitNorms1 == omitNorms2) {
                ("cannot change field \""
                        + fieldName
                        + "\" from omitNorms="
                        + omitNorms1
                        + " to inconsistent omitNorms="
                        + omitNorms2)
            }
        }

        /**
         * Verify that the provided points indexing options are the same
         *
         * @throws IllegalArgumentException if they are not the same
         */
        fun verifySamePointsOptions(
            fieldName: String?,
            pointDimensionCount1: Int,
            indexDimensionCount1: Int,
            numBytes1: Int,
            pointDimensionCount2: Int,
            indexDimensionCount2: Int,
            numBytes2: Int
        ) {
            require(!(pointDimensionCount1 != pointDimensionCount2 || indexDimensionCount1 != indexDimensionCount2 || numBytes1 != numBytes2)) {
                ("cannot change field \""
                        + fieldName
                        + "\" from points dimensionCount="
                        + pointDimensionCount1
                        + ", indexDimensionCount="
                        + indexDimensionCount1
                        + ", numBytes="
                        + numBytes1
                        + " to inconsistent dimensionCount="
                        + pointDimensionCount2
                        + ", indexDimensionCount="
                        + indexDimensionCount2
                        + ", numBytes="
                        + numBytes2)
            }
        }

        /**
         * Verify that the provided vector indexing options are the same
         *
         * @throws IllegalArgumentException if they are not the same
         */
        fun verifySameVectorOptions(
            fieldName: String?,
            vd1: Int,
            ve1: VectorEncoding?,
            vsf1: VectorSimilarityFunction?,
            vd2: Int,
            ve2: VectorEncoding?,
            vsf2: VectorSimilarityFunction?
        ) {
            require(!(vd1 != vd2 || vsf1 !== vsf2 || ve1 !== ve2)) {
                ("cannot change field \""
                        + fieldName
                        + "\" from vector dimension="
                        + vd1
                        + ", vector encoding="
                        + ve1
                        + ", vector similarity function="
                        + vsf1
                        + " to inconsistent vector dimension="
                        + vd2
                        + ", vector encoding="
                        + ve2
                        + ", vector similarity function="
                        + vsf2)
            }
        }
    }
}
