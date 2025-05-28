package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.codecs.DocValuesProducer
import org.gnit.lucenekmp.codecs.FieldsProducer
import org.gnit.lucenekmp.codecs.KnnVectorsReader
import org.gnit.lucenekmp.codecs.NormsProducer
import org.gnit.lucenekmp.codecs.PointsReader
import org.gnit.lucenekmp.codecs.StoredFieldsReader
import org.gnit.lucenekmp.codecs.TermVectorsReader
import org.gnit.lucenekmp.jdkport.Objects
import org.gnit.lucenekmp.search.KnnCollector
import org.gnit.lucenekmp.util.Bits


/** LeafReader implemented by codec APIs.  */
abstract class CodecReader
/** Sole constructor. (For invocation by subclass constructors, typically implicit.)  */
protected constructor() : LeafReader() {
    /**
     * Expert: retrieve underlying StoredFieldsReader
     *
     * @lucene.internal
     */
    abstract val fieldsReader: StoredFieldsReader

    /**
     * Expert: retrieve underlying TermVectorsReader
     *
     * @lucene.internal
     */
    abstract val termVectorsReader: TermVectorsReader?

    /**
     * Expert: retrieve underlying NormsProducer
     *
     * @lucene.internal
     */
    abstract val normsReader: NormsProducer

    /**
     * Expert: retrieve underlying DocValuesProducer
     *
     * @lucene.internal
     */
    abstract val docValuesReader: DocValuesProducer

    /**
     * Expert: retrieve underlying FieldsProducer
     *
     * @lucene.internal
     */
    abstract val postingsReader: FieldsProducer

    /**
     * Expert: retrieve underlying PointsReader
     *
     * @lucene.internal
     */
    abstract val pointsReader: PointsReader

    /**
     * Expert: retrieve underlying VectorReader
     *
     * @lucene.internal
     */
    abstract val vectorReader: KnnVectorsReader

    @Throws(IOException::class)
    override fun storedFields(): StoredFields {
        val reader: StoredFields = this.fieldsReader
        return object : StoredFields() {
            @Throws(IOException::class)
            override fun prefetch(docID: Int) {
                // Don't trust the codec to do proper checks
                Objects.checkIndex(docID, maxDoc())
                reader.prefetch(docID)
            }

            @Throws(IOException::class)
            override fun document(docID: Int, visitor: StoredFieldVisitor) {
                // Don't trust the codec to do proper checks
                Objects.checkIndex(docID, maxDoc())
                reader.document(docID, visitor)
            }
        }
    }

    @Throws(IOException::class)
    override fun termVectors(): TermVectors {
        val reader: TermVectorsReader? = this.termVectorsReader
        return if (reader == null) {
            TermVectors.EMPTY
        } else {
            reader
        }
    }

    @Throws(IOException::class)
    override fun terms(field: String): Terms? {
        ensureOpen()
        val fi: FieldInfo? = fieldInfos.fieldInfo(field)
        if (fi == null || fi.indexOptions === IndexOptions.NONE) {
            // Field does not exist or does not index postings
            return null
        }
        return this.postingsReader.terms(field)
    }

    // returns the FieldInfo that corresponds to the given field and type, or
    // null if the field does not exist, or not indexed as the requested
    // DovDocValuesType.
    private fun getDVField(field: String, type: DocValuesType): FieldInfo? {
        val fi: FieldInfo? = fieldInfos.fieldInfo(field)
        if (fi == null) {
            // Field does not exist
            return null
        }
        if (fi.docValuesType === DocValuesType.NONE) {
            // Field was not indexed with doc values
            return null
        }
        if (fi.docValuesType !== type) {
            // Field DocValues are different than requested type
            return null
        }

        return fi
    }

    @Throws(IOException::class)
    override fun getNumericDocValues(field: String): NumericDocValues? {
        ensureOpen()
        val fi = getDVField(field, DocValuesType.NUMERIC)
        if (fi == null) {
            return null
        }
        return this.docValuesReader.getNumeric(fi)
    }

    @Throws(IOException::class)
    override fun getBinaryDocValues(field: String): BinaryDocValues? {
        ensureOpen()
        val fi = getDVField(field, DocValuesType.BINARY)
        if (fi == null) {
            return null
        }
        return this.docValuesReader.getBinary(fi)
    }

    @Throws(IOException::class)
    override fun getSortedDocValues(field: String): SortedDocValues? {
        ensureOpen()
        val fi = getDVField(field, DocValuesType.SORTED)
        if (fi == null) {
            return null
        }
        return this.docValuesReader.getSorted(fi)
    }

    @Throws(IOException::class)
    override fun getSortedNumericDocValues(field: String): SortedNumericDocValues? {
        ensureOpen()

        val fi = getDVField(field, DocValuesType.SORTED_NUMERIC)
        if (fi == null) {
            return null
        }
        return this.docValuesReader.getSortedNumeric(fi)
    }

    @Throws(IOException::class)
    override fun getSortedSetDocValues(field: String): SortedSetDocValues? {
        ensureOpen()
        val fi = getDVField(field, DocValuesType.SORTED_SET)
        if (fi == null) {
            return null
        }
        return this.docValuesReader.getSortedSet(fi)
    }

    @Throws(IOException::class)
    override fun getDocValuesSkipper(field: String): DocValuesSkipper? {
        ensureOpen()
        val fi: FieldInfo? = fieldInfos.fieldInfo(field)
        if (fi == null || fi.docValuesSkipIndexType() === DocValuesSkipIndexType.NONE) {
            return null
        }
        return this.docValuesReader.getSkipper(fi)
    }

    @Throws(IOException::class)
    override fun getNormValues(field: String): NumericDocValues? {
        ensureOpen()
        val fi: FieldInfo? = fieldInfos.fieldInfo(field)
        if (fi == null || !fi.hasNorms()) {
            // Field does not exist or does not index norms
            return null
        }

        return this.normsReader.getNorms(fi)
    }

    @Throws(IOException::class)
    override fun getPointValues(field: String): PointValues? {
        ensureOpen()
        val fi: FieldInfo? = fieldInfos.fieldInfo(field)
        if (fi == null || fi.pointDimensionCount == 0) {
            // Field does not exist or does not index points
            return null
        }

        return this.pointsReader.getValues(field)
    }

    @Throws(IOException::class)
    override fun getFloatVectorValues(field: String): FloatVectorValues? {
        ensureOpen()
        val fi: FieldInfo? = fieldInfos.fieldInfo(field)
        if (fi == null || fi.vectorDimension == 0 || fi.vectorEncoding !== VectorEncoding.FLOAT32) {
            // Field does not exist or does not index vectors
            return null
        }

        return this.vectorReader.getFloatVectorValues(field)
    }

    @Throws(IOException::class)
    override fun getByteVectorValues(field: String): ByteVectorValues? {
        ensureOpen()
        val fi: FieldInfo? = fieldInfos.fieldInfo(field)
        if (fi == null || fi.vectorDimension == 0 || fi.vectorEncoding !== VectorEncoding.BYTE) {
            // Field does not exist or does not index vectors
            return null
        }

        return this.vectorReader.getByteVectorValues(field)
    }

    @Throws(IOException::class)
    override fun searchNearestVectors(
        field: String, target: FloatArray, knnCollector: KnnCollector, acceptDocs: Bits
    ) {
        ensureOpen()
        val fi: FieldInfo? = fieldInfos.fieldInfo(field)
        if (fi == null || fi.vectorDimension == 0 || fi.vectorEncoding !== VectorEncoding.FLOAT32) {
            // Field does not exist or does not index vectors
            return
        }
        this.vectorReader.search(field, target, knnCollector, acceptDocs)
    }

    @Throws(IOException::class)
    override fun searchNearestVectors(
        field: String, target: ByteArray, knnCollector: KnnCollector, acceptDocs: Bits
    ) {
        ensureOpen()
        val fi: FieldInfo? = fieldInfos.fieldInfo(field)
        if (fi == null || fi.vectorDimension == 0 || fi.vectorEncoding !== VectorEncoding.BYTE) {
            // Field does not exist or does not index vectors
            return
        }
        this.vectorReader.search(field, target, knnCollector, acceptDocs)
    }

    @Throws(IOException::class)
    override fun doClose() {
    }

    @Throws(IOException::class)
    override fun checkIntegrity() {
        ensureOpen()

        // terms/postings
        if (this.postingsReader != null) {
            this.postingsReader.checkIntegrity()
        }

        // norms
        if (this.normsReader != null) {
            this.normsReader.checkIntegrity()
        }

        // docvalues
        if (this.docValuesReader != null) {
            this.docValuesReader.checkIntegrity()
        }

        // stored fields
        if (this.fieldsReader != null) {
            this.fieldsReader.checkIntegrity()
        }

        // term vectors
        if (this.termVectorsReader != null) {
            this.termVectorsReader?.checkIntegrity()
        }

        // points
        if (this.pointsReader != null) {
            this.pointsReader.checkIntegrity()
        }

        // vectors
        if (this.vectorReader != null) {
            this.vectorReader.checkIntegrity()
        }
    }
}
