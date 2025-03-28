package org.gnit.lucenekmp.index


/**
 * Describes the properties of a field.
 *
 * @lucene.experimental
 */
interface IndexableFieldType {
    /** True if the field's value should be stored  */
    fun stored(): Boolean

    /**
     * True if this field's value should be analyzed by the [Analyzer].
     *
     *
     * This has no effect if [.indexOptions] returns IndexOptions.NONE.
     */
    // TODO: shouldn't we remove this?  Whether/how a field is
    // tokenized is an impl detail under Field?
    fun tokenized(): Boolean

    /**
     * True if this field's indexed form should be also stored into term vectors.
     *
     *
     * This builds a miniature inverted-index for this field which can be accessed in a
     * document-oriented way from [TermVectors.get].
     *
     *
     * This option is illegal if [.indexOptions] returns IndexOptions.NONE.
     */
    fun storeTermVectors(): Boolean

    /**
     * True if this field's token character offsets should also be stored into term vectors.
     *
     *
     * This option is illegal if term vectors are not enabled for the field ([ ][.storeTermVectors] is false)
     */
    fun storeTermVectorOffsets(): Boolean

    /**
     * True if this field's token positions should also be stored into the term vectors.
     *
     *
     * This option is illegal if term vectors are not enabled for the field ([ ][.storeTermVectors] is false).
     */
    fun storeTermVectorPositions(): Boolean

    /**
     * True if this field's token payloads should also be stored into the term vectors.
     *
     *
     * This option is illegal if term vector positions are not enabled for the field ([ ][.storeTermVectors] is false).
     */
    fun storeTermVectorPayloads(): Boolean

    /**
     * True if normalization values should be omitted for the field.
     *
     *
     * This saves memory, but at the expense of scoring quality (length normalization will be
     * disabled), and if you omit norms, you cannot use index-time boosts.
     */
    fun omitNorms(): Boolean

    /** [IndexOptions], describing what should be recorded into the inverted index  */
    fun indexOptions(): IndexOptions

    /** DocValues [DocValuesType]: how the field's value will be indexed into docValues.  */
    fun docValuesType(): DocValuesType

    /** Whether a skip index for doc values should be created on this field.  */
    fun docValuesSkipIndexType(): DocValuesSkipIndexType

    /**
     * If this is positive (representing the number of point dimensions), the field is indexed as a
     * point.
     */
    fun pointDimensionCount(): Int

    /** The number of dimensions used for the index key  */
    fun pointIndexDimensionCount(): Int

    /** The number of bytes in each dimension's values.  */
    fun pointNumBytes(): Int

    /** The number of dimensions of the field's vector value  */
    fun vectorDimension(): Int

    /** The [VectorEncoding] of the field's vector value  */
    fun vectorEncoding(): VectorEncoding

    /** The [VectorSimilarityFunction] of the field's vector value  */
    fun vectorSimilarityFunction(): VectorSimilarityFunction

    /**
     * Attributes for the field type.
     *
     *
     * Attributes are not thread-safe, user must not add attributes while other threads are
     * indexing documents with this field type.
     *
     * @return Map
     */
    val attributes: MutableMap<String, String>?
}
