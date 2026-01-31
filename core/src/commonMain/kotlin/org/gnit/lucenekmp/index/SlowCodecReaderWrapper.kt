package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.codecs.DocValuesProducer
import org.gnit.lucenekmp.codecs.FieldsProducer
import org.gnit.lucenekmp.codecs.KnnVectorsReader
import org.gnit.lucenekmp.codecs.NormsProducer
import org.gnit.lucenekmp.codecs.PointsReader
import org.gnit.lucenekmp.codecs.StoredFieldsReader
import org.gnit.lucenekmp.codecs.TermVectorsReader
import org.gnit.lucenekmp.jdkport.UncheckedIOException
import org.gnit.lucenekmp.search.KnnCollector
import org.gnit.lucenekmp.util.Bits

/**
 * Wraps arbitrary readers for merging. Note that this can cause slow and memory-intensive merges.
 * Consider using [FilterCodecReader] instead.
 */
object SlowCodecReaderWrapper {
    /**
     * Returns a `CodecReader` view of reader.
     *
     *
     * If `reader` is already a `CodecReader`, it is returned directly. Otherwise, a
     * (slow) view is returned.
     */
    @Throws(IOException::class)
    fun wrap(reader: LeafReader): CodecReader {
        if (reader is CodecReader) {
            return reader
        } else {
            // simulate it slowly, over the leafReader api:
            reader.checkIntegrity()
            return object : CodecReader() {
                override val termVectorsReader: TermVectorsReader
                    get() {
                        reader.ensureOpen()
                        return readerToTermVectorsReader(reader)
                    }

                override val fieldsReader: StoredFieldsReader
                    get() {
                        reader.ensureOpen()
                        return readerToStoredFieldsReader(reader)
                    }

                override val normsReader: NormsProducer
                    get() {
                        reader.ensureOpen()
                        return readerToNormsProducer(reader)
                    }

                override val docValuesReader: DocValuesProducer
                    get() {
                        reader.ensureOpen()
                        return readerToDocValuesProducer(reader)
                    }

                override val vectorReader: KnnVectorsReader
                    get() {
                        reader.ensureOpen()
                        return readerToVectorReader(reader)
                    }

                override val postingsReader: FieldsProducer
                    get() {
                        reader.ensureOpen()
                        try {
                            return readerToFieldsProducer(reader)
                        } catch (bogus: IOException) {
                            throw AssertionError(bogus)
                        }
                    }

                override val fieldInfos: FieldInfos
                    get() = reader.fieldInfos

                override val pointsReader: PointsReader
                    get() = pointValuesToReader(reader)

                override val liveDocs: Bits?
                    get() = reader.liveDocs

                override fun numDocs(): Int {
                    return reader.numDocs()
                }

                override fun maxDoc(): Int {
                    return reader.maxDoc()
                }

                override val coreCacheHelper: CacheHelper?
                    get() = reader.coreCacheHelper

                override val readerCacheHelper: CacheHelper?
                    get() = reader.readerCacheHelper

                override fun toString(): String {
                    return "SlowCodecReaderWrapper($reader)"
                }

                override val metaData: LeafMetaData
                    get() = reader.metaData

                override fun terms(field: String?): Terms? {
                    TODO("Not yet implemented")
                }

                override fun searchNearestVectors(field: String, target: FloatArray, knnCollector: KnnCollector, acceptDocs: Bits?){
                    TODO("Not yet implemented")
                }

                override fun searchNearestVectors(field: String, target: ByteArray, knnCollector: KnnCollector, acceptDocs: Bits?){
                    TODO("Not yet implemented")
                }
            }
        }
    }

    private fun pointValuesToReader(reader: LeafReader): PointsReader {
        return object : PointsReader() {
            @Throws(IOException::class)
            override fun getValues(field: String): PointValues? {
                return reader.getPointValues(field)
            }

            @Throws(IOException::class)
            override fun checkIntegrity() {
                // We already checkIntegrity the entire reader up front
            }

            override fun close() {}
        }
    }

    private fun readerToVectorReader(reader: LeafReader): KnnVectorsReader {
        return object : KnnVectorsReader() {
            @Throws(IOException::class)
            override fun getFloatVectorValues(field: String): FloatVectorValues? {
                return reader.getFloatVectorValues(field)
            }

            @Throws(IOException::class)
            override fun getByteVectorValues(field: String): ByteVectorValues? {
                return reader.getByteVectorValues(field)
            }

            @Throws(IOException::class)
            override fun search(
                field: String,
                target: FloatArray,
                knnCollector: KnnCollector,
                acceptDocs: Bits?
            ) {
                reader.searchNearestVectors(field, target, knnCollector, acceptDocs)
            }

            @Throws(IOException::class)
            override fun search(
                field: String,
                target: ByteArray,
                knnCollector: KnnCollector,
                acceptDocs: Bits?
            ) {
                reader.searchNearestVectors(field, target, knnCollector, acceptDocs)
            }

            override fun checkIntegrity() {
                // We already checkIntegrity the entire reader up front
            }

            override fun close() {}
        }
    }

    private fun readerToNormsProducer(reader: LeafReader): NormsProducer {
        return object : NormsProducer() {
            @Throws(IOException::class)
            override fun getNorms(field: FieldInfo): NumericDocValues {
                return reader.getNormValues(field.name)!!
            }

            @Throws(IOException::class)
            override fun checkIntegrity() {
                // We already checkIntegrity the entire reader up front
            }

            override fun close() {}
        }
    }

    private fun readerToDocValuesProducer(reader: LeafReader): DocValuesProducer {
        return object : DocValuesProducer() {
            @Throws(IOException::class)
            override fun getNumeric(field: FieldInfo): NumericDocValues? {
                return reader.getNumericDocValues(field.name)
            }

            @Throws(IOException::class)
            override fun getBinary(field: FieldInfo): BinaryDocValues? {
                return reader.getBinaryDocValues(field.name)
            }

            @Throws(IOException::class)
            override fun getSorted(field: FieldInfo): SortedDocValues? {
                return reader.getSortedDocValues(field.name)
            }

            @Throws(IOException::class)
            override fun getSortedNumeric(field: FieldInfo): SortedNumericDocValues? {
                return reader.getSortedNumericDocValues(field.name)
            }

            @Throws(IOException::class)
            override fun getSortedSet(field: FieldInfo): SortedSetDocValues? {
                return reader.getSortedSetDocValues(field.name)
            }

            @Throws(IOException::class)
            override fun getSkipper(field: FieldInfo): DocValuesSkipper? {
                return reader.getDocValuesSkipper(field.name)
            }

            @Throws(IOException::class)
            override fun checkIntegrity() {
                // We already checkIntegrity the entire reader up front
            }

            override fun close() {}
        }
    }

    private fun readerToStoredFieldsReader(reader: LeafReader): StoredFieldsReader {
        val storedFields: StoredFields
        try {
            storedFields = reader.storedFields()
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
        return object : StoredFieldsReader() {
            @Throws(IOException::class)
            override fun prefetch(docID: Int) {
                storedFields.prefetch(docID)
            }

            @Throws(IOException::class)
            override fun document(
                docID: Int,
                visitor: StoredFieldVisitor
            ) {
                storedFields.document(docID, visitor)
            }

            override fun clone(): StoredFieldsReader {
                return readerToStoredFieldsReader(reader)
            }

            @Throws(IOException::class)
            override fun checkIntegrity() {
                // We already checkIntegrity the entire reader up front
            }

            override fun close() {}
        }
    }

    private fun readerToTermVectorsReader(reader: LeafReader): TermVectorsReader {
        val termVectors: TermVectors
        try {
            termVectors = reader.termVectors()
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
        return object : TermVectorsReader() {
            @Throws(IOException::class)
            override fun prefetch(docID: Int) {
                termVectors.prefetch(docID)
            }

            @Throws(IOException::class)
            override fun get(docID: Int): Fields? {
                return termVectors.get(docID)
            }

            override fun clone(): TermVectorsReader {
                return readerToTermVectorsReader(reader)
            }

            @Throws(IOException::class)
            override fun checkIntegrity() {
                // We already checkIntegrity the entire reader up front
            }

            override fun close() {}
        }
    }

    @Throws(IOException::class)
    private fun readerToFieldsProducer(reader: LeafReader): FieldsProducer {
        val indexedFields: ArrayList<String> = ArrayList()
        for (fieldInfo in reader.fieldInfos) {
            if (fieldInfo.indexOptions != IndexOptions.NONE) {
                indexedFields.add(fieldInfo.name)
            }
        }
        indexedFields.sort()
        return object : FieldsProducer() {
            override fun iterator(): MutableIterator<String> {
                return asUnmodifiableIterator(indexedFields.iterator())
            }

            @Throws(IOException::class)
            override fun terms(field: String?): Terms? {
                return reader.terms(field)
            }

            override fun size(): Int {
                return indexedFields.size
            }

            @Throws(IOException::class)
            override fun checkIntegrity() {
                // We already checkIntegrity the entire reader up front
            }

            override fun close() {}
        }
    }
}
