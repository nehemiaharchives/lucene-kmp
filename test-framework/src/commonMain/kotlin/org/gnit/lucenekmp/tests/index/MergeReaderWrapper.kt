package org.gnit.lucenekmp.tests.index

import okio.IOException
import org.gnit.lucenekmp.codecs.DocValuesProducer
import org.gnit.lucenekmp.codecs.FieldsProducer
import org.gnit.lucenekmp.codecs.NormsProducer
import org.gnit.lucenekmp.codecs.StoredFieldsReader
import org.gnit.lucenekmp.codecs.TermVectorsReader
import org.gnit.lucenekmp.index.BinaryDocValues
import org.gnit.lucenekmp.index.ByteVectorValues
import org.gnit.lucenekmp.index.CodecReader
import org.gnit.lucenekmp.index.DocValuesSkipper
import org.gnit.lucenekmp.index.DocValuesType
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.FieldInfos
import org.gnit.lucenekmp.index.FloatVectorValues
import org.gnit.lucenekmp.index.LeafMetaData
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.index.SortedDocValues
import org.gnit.lucenekmp.index.SortedNumericDocValues
import org.gnit.lucenekmp.index.SortedSetDocValues
import org.gnit.lucenekmp.index.StoredFields
import org.gnit.lucenekmp.index.TermVectors
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.search.KnnCollector
import org.gnit.lucenekmp.util.Bits

/**
 * This is a hack to make index sorting fast, with a [LeafReader] that always returns merge
 * instances when you ask for the codec readers.
 */
internal class MergeReaderWrapper(val `in`: CodecReader) : LeafReader() {
    val fields: FieldsProducer?
    val norms: NormsProducer?
    val docValues: DocValuesProducer?
    val store: StoredFieldsReader?
    val vectors: TermVectorsReader?

    init {

        var fields: FieldsProducer? = `in`.postingsReader
        if (fields != null) {
            fields = fields.mergeInstance
        }
        this.fields = fields

        var norms: NormsProducer? = `in`.normsReader
        if (norms != null) {
            norms = norms.mergeInstance
        }
        this.norms = norms

        var docValues: DocValuesProducer? = `in`.docValuesReader
        if (docValues != null) {
            docValues = docValues.mergeInstance
        }
        this.docValues = docValues

        var store: StoredFieldsReader? = `in`.fieldsReader
        if (store != null) {
            store = store.mergeInstance
        }
        this.store = store

        var vectors: TermVectorsReader? = `in`.termVectorsReader
        if (vectors != null) {
            vectors = vectors.mergeInstance
        }
        this.vectors = vectors
    }

    @Throws(IOException::class)
    override fun terms(field: String?): Terms? {
        ensureOpen()
        // We could check the FieldInfo IndexOptions but there's no point since
        //   PostingsReader will simply return null for fields that don't exist or that have no terms
        // index.
        if (fields == null) {
            return null
        }
        return fields.terms(field)
    }

    @Throws(IOException::class)
    override fun getNumericDocValues(field: String): NumericDocValues? {
        ensureOpen()
        val fi: FieldInfo? = this.fieldInfos.fieldInfo(field)
        if (fi == null) {
            // Field does not exist
            return null
        }
        if (fi.docValuesType != DocValuesType.NUMERIC) {
            // Field was not indexed with doc values
            return null
        }
        return docValues!!.getNumeric(fi)
    }

    @Throws(IOException::class)
    override fun getBinaryDocValues(field: String): BinaryDocValues? {
        ensureOpen()
        val fi: FieldInfo? = this.fieldInfos.fieldInfo(field)
        if (fi == null) {
            // Field does not exist
            return null
        }
        if (fi.docValuesType != DocValuesType.BINARY) {
            // Field was not indexed with doc values
            return null
        }
        return docValues!!.getBinary(fi)
    }

    @Throws(IOException::class)
    override fun getSortedDocValues(field: String): SortedDocValues? {
        ensureOpen()
        val fi: FieldInfo? = this.fieldInfos.fieldInfo(field)
        if (fi == null) {
            // Field does not exist
            return null
        }
        if (fi.docValuesType != DocValuesType.SORTED) {
            // Field was not indexed with doc values
            return null
        }
        return docValues!!.getSorted(fi)
    }

    @Throws(IOException::class)
    override fun getSortedNumericDocValues(field: String): SortedNumericDocValues? {
        ensureOpen()
        val fi: FieldInfo? = this.fieldInfos.fieldInfo(field)
        if (fi == null) {
            // Field does not exist
            return null
        }
        if (fi.docValuesType != DocValuesType.SORTED_NUMERIC) {
            // Field was not indexed with doc values
            return null
        }
        return docValues!!.getSortedNumeric(fi)
    }

    @Throws(IOException::class)
    override fun getSortedSetDocValues(field: String): SortedSetDocValues? {
        ensureOpen()
        val fi: FieldInfo? = this.fieldInfos.fieldInfo(field)
        if (fi == null) {
            // Field does not exist
            return null
        }
        if (fi.docValuesType != DocValuesType.SORTED_SET) {
            // Field was not indexed with doc values
            return null
        }
        return docValues!!.getSortedSet(fi)
    }

    @Throws(IOException::class)
    override fun getNormValues(field: String): NumericDocValues? {
        ensureOpen()
        val fi: FieldInfo? = this.fieldInfos.fieldInfo(field)
        if (fi == null || !fi.hasNorms()) {
            // Field does not exist or does not index norms
            return null
        }
        return norms!!.getNorms(fi)
    }

    @Throws(IOException::class)
    override fun getDocValuesSkipper(field: String): DocValuesSkipper? {
        ensureOpen()
        val fi: FieldInfo? = this.fieldInfos.fieldInfo(field)
        if (fi == null) {
            // Field does not exist
            return null
        }
        return docValues!!.getSkipper(fi)
    }

    override val fieldInfos: FieldInfos
        get() = `in`.fieldInfos

    override val liveDocs: Bits?
        get() = `in`.liveDocs

    @Throws(IOException::class)
    override fun checkIntegrity() {
        `in`.checkIntegrity()
    }

    @Throws(IOException::class)
    override fun termVectors(): TermVectors {
        ensureOpen()
        if (vectors == null) {
            return TermVectors.EMPTY
        } else {
            return vectors
        }
    }

    @Throws(IOException::class)
    override fun getPointValues(fieldName: String): PointValues? {
        return `in`.getPointValues(fieldName)
    }

    @Throws(IOException::class)
    override fun getFloatVectorValues(fieldName: String): FloatVectorValues? {
        return `in`.getFloatVectorValues(fieldName)
    }

    @Throws(IOException::class)
    override fun getByteVectorValues(fieldName: String): ByteVectorValues? {
        return `in`.getByteVectorValues(fieldName)
    }

    @Throws(IOException::class)
    override fun searchNearestVectors(
        field: String,
        target: FloatArray,
        knnCollector: KnnCollector,
        acceptDocs: Bits?
    ) {
        `in`.searchNearestVectors(field, target, knnCollector, acceptDocs)
    }

    @Throws(IOException::class)
    override fun searchNearestVectors(
        field: String,
        target: ByteArray,
        knnCollector: KnnCollector,
        acceptDocs: Bits?
    ) {
        `in`.searchNearestVectors(field, target, knnCollector, acceptDocs)
    }

    override fun numDocs(): Int {
        return `in`.numDocs()
    }

    override fun maxDoc(): Int {
        return `in`.maxDoc()
    }

    @Throws(IOException::class)
    override fun storedFields(): StoredFields {
        ensureOpen()
        return store!!
    }

    @Throws(IOException::class)
    override fun doClose() {
        `in`.close()
    }

    override val coreCacheHelper: CacheHelper?
        get() = `in`.coreCacheHelper

    override val readerCacheHelper: CacheHelper?
        get() = `in`.readerCacheHelper

    override fun toString(): String {
        return "MergeReaderWrapper($`in`)"
    }

    override val metaData: LeafMetaData
        get() = `in`.metaData
}
