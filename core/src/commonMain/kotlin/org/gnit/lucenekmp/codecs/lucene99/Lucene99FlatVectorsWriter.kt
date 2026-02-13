package org.gnit.lucenekmp.codecs.lucene99

import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.codecs.hnsw.FlatFieldVectorsWriter
import org.gnit.lucenekmp.codecs.hnsw.FlatVectorsScorer
import org.gnit.lucenekmp.codecs.hnsw.FlatVectorsWriter
import org.gnit.lucenekmp.codecs.lucene95.OrdToDocDISIReaderConfiguration
import org.gnit.lucenekmp.codecs.lucene95.OffHeapByteVectorValues.DenseOffHeapVectorValues
import org.gnit.lucenekmp.codecs.lucene95.OffHeapFloatVectorValues
import org.gnit.lucenekmp.codecs.lucene99.Lucene99FlatVectorsFormat.Companion.DIRECT_MONOTONIC_BLOCK_SHIFT
import org.gnit.lucenekmp.index.ByteVectorValues
import org.gnit.lucenekmp.index.DocsWithFieldSet
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.FloatVectorValues
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.MergeState
import org.gnit.lucenekmp.index.SegmentWriteState
import org.gnit.lucenekmp.index.Sorter
import org.gnit.lucenekmp.index.VectorEncoding.BYTE
import org.gnit.lucenekmp.index.VectorEncoding.FLOAT32
import org.gnit.lucenekmp.search.DocIdSetIterator.Companion.NO_MORE_DOCS
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.store.ReadAdvice
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.RamUsageEstimator
import org.gnit.lucenekmp.util.hnsw.CloseableRandomVectorScorerSupplier
import org.gnit.lucenekmp.util.hnsw.RandomVectorScorerSupplier
import org.gnit.lucenekmp.util.hnsw.UpdateableRandomVectorScorer
import okio.IOException
import org.gnit.lucenekmp.index.KnnVectorValues

/**
 * Writes vector values to index segments.
 *
 * @lucene.experimental
 */
class Lucene99FlatVectorsWriter(state: SegmentWriteState, scorer: FlatVectorsScorer) : FlatVectorsWriter(scorer) {
    private val segmentWriteState: SegmentWriteState = state
    private val meta: IndexOutput?
    private val vectorData: IndexOutput?

    private val fields: MutableList<FieldWriter<*>> = ArrayList<FieldWriter<*>>()
    private var finished = false

    init {
        val metaFileName: String =
            IndexFileNames.segmentFileName(
                state.segmentInfo.name, state.segmentSuffix, Lucene99FlatVectorsFormat.META_EXTENSION
            )

        val vectorDataFileName: String =
            IndexFileNames.segmentFileName(
                state.segmentInfo.name,
                state.segmentSuffix,
                Lucene99FlatVectorsFormat.VECTOR_DATA_EXTENSION
            )

        var success = false
        try {
            meta = state.directory.createOutput(metaFileName, state.context)
            vectorData = state.directory.createOutput(vectorDataFileName, state.context)

            CodecUtil.writeIndexHeader(
                meta,
                Lucene99FlatVectorsFormat.META_CODEC_NAME,
                Lucene99FlatVectorsFormat.VERSION_CURRENT,
                state.segmentInfo.getId(),
                state.segmentSuffix
            )
            CodecUtil.writeIndexHeader(
                vectorData,
                Lucene99FlatVectorsFormat.VECTOR_DATA_CODEC_NAME,
                Lucene99FlatVectorsFormat.VERSION_CURRENT,
                state.segmentInfo.getId(),
                state.segmentSuffix
            )
            success = true
        } finally {
            if (!success) {
                IOUtils.closeWhileHandlingException(this)
            }
        }
    }

    @Throws(IOException::class)
    override fun addField(fieldInfo: FieldInfo): FlatFieldVectorsWriter<*> {
        val newField = FieldWriter.create(fieldInfo)
        fields.add(newField)
        return newField
    }

    @Throws(IOException::class)
    override fun flush(maxDoc: Int, sortMap: Sorter.DocMap?) {
        for (field in fields) {
            if (sortMap == null) {
                writeField(field, maxDoc)
            } else {
                writeSortingField(field, maxDoc, sortMap)
            }
            field.finish()
        }
    }

    @Throws(IOException::class)
    override fun finish() {
        check(!finished) { "already finished" }
        finished = true
        if (meta != null) {
            // write end of fields marker
            meta.writeInt(-1)
            CodecUtil.writeFooter(meta)
        }
        if (vectorData != null) {
            CodecUtil.writeFooter(vectorData)
        }
    }

    override fun ramBytesUsed(): Long {
        var total = SHALLLOW_RAM_BYTES_USED
        for (field in fields) {
            total += field.ramBytesUsed()
        }
        return total
    }

    @Throws(IOException::class)
    private fun writeField(fieldData: FieldWriter<*>, maxDoc: Int) {
        // write vector values
        val vectorDataOffset: Long = vectorData!!.alignFilePointer(Float.SIZE_BYTES)
        when (fieldData.fieldInfo.vectorEncoding) {
            BYTE -> writeByteVectors(fieldData)
            FLOAT32 -> writeFloat32Vectors(fieldData)
        }
        val vectorDataLength: Long = vectorData.filePointer - vectorDataOffset

        writeMeta(
            fieldData.fieldInfo, maxDoc, vectorDataOffset, vectorDataLength, fieldData.docsWithField
        )
    }

    @Throws(IOException::class)
    private fun writeFloat32Vectors(fieldData: FieldWriter<*>) {
        val buffer = ByteArray(fieldData.dim * Float.SIZE_BYTES)
        for (v in fieldData.vectors) {
            writeFloatArrayLE(v as FloatArray, buffer)
            vectorData!!.writeBytes(buffer, buffer.size)
        }
    }

    @Throws(IOException::class)
    private fun writeByteVectors(fieldData: FieldWriter<*>) {
        for (v in fieldData.vectors) {
            val vector = v as ByteArray
            vectorData!!.writeBytes(vector, vector.size)
        }
    }

    @Throws(IOException::class)
    private fun writeSortingField(fieldData: FieldWriter<*>, maxDoc: Int, sortMap: Sorter.DocMap) {
        val ordMap = IntArray(fieldData.docsWithField.cardinality()) // new ord to old ord

        val newDocsWithField = DocsWithFieldSet()
        mapOldOrdToNewOrd(fieldData.docsWithField, sortMap, null, ordMap, newDocsWithField)

        // write vector values
        val vectorDataOffset =
            when (fieldData.fieldInfo.vectorEncoding) {
                BYTE -> writeSortedByteVectors(fieldData, ordMap)
                FLOAT32 -> writeSortedFloat32Vectors(fieldData, ordMap)
            }
        val vectorDataLength: Long = vectorData!!.filePointer - vectorDataOffset

        writeMeta(fieldData.fieldInfo, maxDoc, vectorDataOffset, vectorDataLength, newDocsWithField)
    }

    @Throws(IOException::class)
    private fun writeSortedFloat32Vectors(fieldData: FieldWriter<*>, ordMap: IntArray): Long {
        val vectorDataOffset: Long = vectorData!!.alignFilePointer(Float.SIZE_BYTES)
        val buffer = ByteArray(fieldData.dim * Float.SIZE_BYTES)
        for (ordinal in ordMap) {
            val vector = fieldData.vectors[ordinal] as FloatArray
            writeFloatArrayLE(vector, buffer)
            vectorData.writeBytes(buffer, buffer.size)
        }
        return vectorDataOffset
    }

    @Throws(IOException::class)
    private fun writeSortedByteVectors(fieldData: FieldWriter<*>, ordMap: IntArray): Long {
        val vectorDataOffset: Long = vectorData!!.alignFilePointer(Float.SIZE_BYTES)
        for (ordinal in ordMap) {
            val vector = fieldData.vectors[ordinal] as ByteArray
            vectorData.writeBytes(vector, vector.size)
        }
        return vectorDataOffset
    }

    @Throws(IOException::class)
    override fun mergeOneField(fieldInfo: FieldInfo, mergeState: MergeState) {
        // Since we know we will not be searching for additional indexing, we can just write the
        // the vectors directly to the new segment.
        val vectorDataOffset: Long = vectorData!!.alignFilePointer(Float.SIZE_BYTES)
        // No need to use temporary file as we don't have to re-open for reading
        val docsWithField: DocsWithFieldSet =
            when (fieldInfo.vectorEncoding) {
                BYTE -> writeByteVectorData(
                    vectorData,
                    MergedVectorValues.mergeByteVectorValues(fieldInfo, mergeState)
                )

                FLOAT32 -> writeVectorData(
                    vectorData,
                    MergedVectorValues.mergeFloatVectorValues(
                        fieldInfo, mergeState
                    )
                )
            }
        val vectorDataLength: Long = vectorData.filePointer - vectorDataOffset
        writeMeta(
            fieldInfo,
            segmentWriteState.segmentInfo.maxDoc(),
            vectorDataOffset,
            vectorDataLength,
            docsWithField
        )
    }

    @Throws(IOException::class)
    override fun mergeOneFieldToIndex(
        fieldInfo: FieldInfo, mergeState: MergeState
    ): CloseableRandomVectorScorerSupplier {
        val vectorDataOffset: Long = vectorData!!.alignFilePointer(Float.SIZE_BYTES)
        val tempVectorData: IndexOutput =
            segmentWriteState.directory.createTempOutput(
                vectorData.name!!, "temp", segmentWriteState.context
            )
        var vectorDataInput: IndexInput? = null
        var success = false
        try {
            // write the vector data to a temporary file
            val docsWithField: DocsWithFieldSet =
                when (fieldInfo.vectorEncoding) {
                    BYTE -> writeByteVectorData(
                        tempVectorData,
                        MergedVectorValues.mergeByteVectorValues(
                            fieldInfo, mergeState
                        )
                    )

                    FLOAT32 -> writeVectorData(
                        tempVectorData,
                        MergedVectorValues.mergeFloatVectorValues(
                            fieldInfo, mergeState
                        )
                    )
                }
            CodecUtil.writeFooter(tempVectorData)
            IOUtils.close(tempVectorData)

            // This temp file will be accessed in a random-access fashion to construct the HNSW graph.
            // Note: don't use the context from the state, which is a flush/merge context, not expecting
            // to perform random reads.
            vectorDataInput =
                segmentWriteState.directory.openInput(
                    tempVectorData.name!!, IOContext.DEFAULT.withReadAdvice(ReadAdvice.RANDOM)
                )
            // copy the temporary file vectors to the actual data file
            vectorData.copyBytes(vectorDataInput, vectorDataInput.length() - CodecUtil.footerLength())
            CodecUtil.retrieveChecksum(vectorDataInput)
            val vectorDataLength: Long = vectorData.filePointer - vectorDataOffset
            writeMeta(
                fieldInfo,
                segmentWriteState.segmentInfo.maxDoc(),
                vectorDataOffset,
                vectorDataLength,
                docsWithField
            )
            success = true
            val finalVectorDataInput: IndexInput = vectorDataInput
            val randomVectorScorerSupplier: RandomVectorScorerSupplier =
                when (fieldInfo.vectorEncoding) {
                    BYTE -> this.vectorScorer.getRandomVectorScorerSupplier(
                        fieldInfo.vectorSimilarityFunction,
                        DenseOffHeapVectorValues(
                            fieldInfo.vectorDimension,
                            docsWithField.cardinality(),
                            finalVectorDataInput,
                            fieldInfo.vectorDimension * Byte.SIZE_BYTES,
                            this.vectorScorer,
                            fieldInfo.vectorSimilarityFunction
                        )
                    )

                    FLOAT32 -> this.vectorScorer.getRandomVectorScorerSupplier(
                        fieldInfo.vectorSimilarityFunction,
                        OffHeapFloatVectorValues.DenseOffHeapVectorValues(
                            fieldInfo.vectorDimension,
                            docsWithField.cardinality(),
                            finalVectorDataInput,
                            fieldInfo.vectorDimension * Float.SIZE_BYTES,
                            this.vectorScorer,
                            fieldInfo.vectorSimilarityFunction
                        )
                    )
                }
            return FlatCloseableRandomVectorScorerSupplier(
                AutoCloseable {
                    IOUtils.close(finalVectorDataInput)
                    segmentWriteState.directory.deleteFile(tempVectorData.name!!)
                },
                docsWithField.cardinality(),
                randomVectorScorerSupplier
            )
        } finally {
            if (!success) {
                IOUtils.closeWhileHandlingException(vectorDataInput!!, tempVectorData)
                IOUtils.deleteFilesIgnoringExceptions(
                    segmentWriteState.directory, tempVectorData.name!!
                )
            }
        }
    }

    @Throws(IOException::class)
    private fun writeMeta(
        field: FieldInfo,
        maxDoc: Int,
        vectorDataOffset: Long,
        vectorDataLength: Long,
        docsWithField: DocsWithFieldSet
    ) {
        meta!!.writeInt(field.number)
        meta.writeInt(field.vectorEncoding.ordinal)
        meta.writeInt(field.vectorSimilarityFunction.ordinal)
        meta.writeVLong(vectorDataOffset)
        meta.writeVLong(vectorDataLength)
        meta.writeVInt(field.vectorDimension)

        // write docIDs
        val count: Int = docsWithField.cardinality()
        meta.writeInt(count)
        OrdToDocDISIReaderConfiguration.writeStoredMeta(
            DIRECT_MONOTONIC_BLOCK_SHIFT, meta, vectorData!!, count, maxDoc, docsWithField
        )
    }

    @Throws(IOException::class)
    override fun close() {
        IOUtils.close(meta!!, vectorData!!)
    }

    private abstract class FieldWriter<T>(val fieldInfo: FieldInfo) : FlatFieldVectorsWriter<T>() {
        val dim: Int = fieldInfo.vectorDimension
        val docsWithField: DocsWithFieldSet = DocsWithFieldSet()
        override val vectors: MutableList<T> = ArrayList()
        final override var isFinished: Boolean = false
            private set

        private var lastDocID = -1

        @Throws(IOException::class)
        override fun addValue(docID: Int, vectorValue: T) {
            check(!this.isFinished) { "already finished, cannot add more values" }
            require(docID != lastDocID) {
                ("VectorValuesField \""
                        + fieldInfo.name
                        + "\" appears more than once in this document (only one value is allowed per field)")
            }
            require(docID > lastDocID)
            val copy: T = copyValue(vectorValue)
            docsWithField.add(docID)
            vectors.add(copy)
            lastDocID = docID
        }

        override fun ramBytesUsed(): Long {
            val size = SHALLOW_RAM_BYTES_USED
            if (vectors.isEmpty()) return size
            return (size
                    + docsWithField.ramBytesUsed()
                    + vectors.size.toLong() * (RamUsageEstimator.NUM_BYTES_OBJECT_REF + RamUsageEstimator.NUM_BYTES_ARRAY_HEADER) + (vectors.size.toLong() * fieldInfo.vectorDimension
                    * fieldInfo.vectorEncoding.byteSize))
        }

        override val docsWithFieldSet: DocsWithFieldSet
            get() = docsWithField

        @Throws(IOException::class)
        override fun finish() {
            if (this.isFinished) {
                return
            }
            this.isFinished = true
        }

        companion object {
            private val SHALLOW_RAM_BYTES_USED: Long = RamUsageEstimator.shallowSizeOfInstance(FieldWriter::class)
            fun create(fieldInfo: FieldInfo): FieldWriter<*> {
                val dim: Int = fieldInfo.vectorDimension
                return when (fieldInfo.vectorEncoding) {
                    BYTE -> object : FieldWriter<ByteArray>(fieldInfo) {
                        override fun copyValue(value: ByteArray): ByteArray {
                            return ArrayUtil.copyOfSubArray(value, 0, dim)
                        }
                    }

                    FLOAT32 -> object : FieldWriter<FloatArray>(fieldInfo) {
                        override fun copyValue(value: FloatArray): FloatArray {
                            return ArrayUtil.copyOfSubArray(value, 0, dim)
                        }
                    }
                }
            }
        }
    }

    internal class FlatCloseableRandomVectorScorerSupplier
        (private val onClose: AutoCloseable, private val numVectors: Int, private val supplier: RandomVectorScorerSupplier) :
        CloseableRandomVectorScorerSupplier {

        @Throws(IOException::class)
        override fun scorer(): UpdateableRandomVectorScorer {
            return supplier.scorer()
        }

        @Throws(IOException::class)
        override fun copy(): RandomVectorScorerSupplier {
            return supplier.copy()
        }

        @Throws(IOException::class)
        override fun close() {
            onClose.close()
        }

        override fun totalVectorCount(): Int {
            return numVectors
        }
    }

    companion object {
        private val SHALLLOW_RAM_BYTES_USED: Long =
            RamUsageEstimator.shallowSizeOfInstance(Lucene99FlatVectorsWriter::class)

        /**
         * Writes the byte vector values to the output and returns a set of documents that contains
         * vectors.
         */
        @Throws(IOException::class)
        private fun writeByteVectorData(
            output: IndexOutput, byteVectorValues: ByteVectorValues
        ): DocsWithFieldSet {
            val docsWithField = DocsWithFieldSet()
            val iter: KnnVectorValues.DocIndexIterator = byteVectorValues.iterator()
            var docV: Int = iter.nextDoc()
            while (docV != NO_MORE_DOCS) {
                // write vector
                val binaryValue: ByteArray = byteVectorValues.vectorValue(iter.index())
                require(binaryValue.size == byteVectorValues.dimension() * BYTE.byteSize)
                output.writeBytes(binaryValue, binaryValue.size)
                docsWithField.add(docV)
                docV = iter.nextDoc()
            }
            return docsWithField
        }

        /**
         * Writes the vector values to the output and returns a set of documents that contains vectors.
         */
        @Throws(IOException::class)
        private fun writeVectorData(
            output: IndexOutput, floatVectorValues: FloatVectorValues
        ): DocsWithFieldSet {
            val docsWithField = DocsWithFieldSet()
            val buffer = ByteArray(floatVectorValues.dimension() * FLOAT32.byteSize)
            val iter: KnnVectorValues.DocIndexIterator = floatVectorValues.iterator()
            var docV: Int = iter.nextDoc()
            while (docV != NO_MORE_DOCS) {
                // write vector
                val value: FloatArray = floatVectorValues.vectorValue(iter.index())
                writeFloatArrayLE(value, buffer)
                output.writeBytes(buffer, buffer.size)
                docsWithField.add(docV)
                docV = iter.nextDoc()
            }
            return docsWithField
        }

        private fun writeFloatArrayLE(value: FloatArray, buffer: ByteArray) {
            var o = 0
            for (v in value) {
                val bits = v.toBits()
                buffer[o++] = (bits and 0xFF).toByte()
                buffer[o++] = ((bits ushr 8) and 0xFF).toByte()
                buffer[o++] = ((bits ushr 16) and 0xFF).toByte()
                buffer[o++] = ((bits ushr 24) and 0xFF).toByte()
            }
        }
    }
}
