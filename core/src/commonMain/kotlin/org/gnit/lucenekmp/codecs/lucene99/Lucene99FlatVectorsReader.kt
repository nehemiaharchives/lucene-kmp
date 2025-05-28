package org.gnit.lucenekmp.codecs.lucene99


import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.codecs.hnsw.FlatVectorsReader
import org.gnit.lucenekmp.codecs.hnsw.FlatVectorsScorer
import org.gnit.lucenekmp.codecs.lucene95.OffHeapByteVectorValues
import org.gnit.lucenekmp.codecs.lucene95.OffHeapFloatVectorValues
import org.gnit.lucenekmp.codecs.lucene95.OrdToDocDISIReaderConfiguration
import org.gnit.lucenekmp.codecs.lucene99.Lucene99HnswVectorsReader.Companion.readSimilarityFunction
import org.gnit.lucenekmp.codecs.lucene99.Lucene99HnswVectorsReader.Companion.readVectorEncoding
import org.gnit.lucenekmp.index.ByteVectorValues
import org.gnit.lucenekmp.index.CorruptIndexException
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.FieldInfos
import org.gnit.lucenekmp.index.FloatVectorValues
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.VectorEncoding
import org.gnit.lucenekmp.index.VectorEncoding.BYTE
import org.gnit.lucenekmp.index.VectorEncoding.FLOAT32
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.internal.hppc.IntObjectHashMap
import org.gnit.lucenekmp.store.ChecksumIndexInput
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.ReadAdvice
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.RamUsageEstimator
import org.gnit.lucenekmp.util.hnsw.RandomVectorScorer
import okio.IOException
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.UncheckedIOException

/**
 * Reads vectors from the index segments.
 *
 * @lucene.experimental
 */
class Lucene99FlatVectorsReader(state: SegmentReadState, scorer: FlatVectorsScorer) : FlatVectorsReader(scorer) {
    private val fields: IntObjectHashMap<FieldEntry> = IntObjectHashMap()
    private val vectorData: IndexInput
    private val fieldInfos: FieldInfos

    init {
        val versionMeta = readMetadata(state)
        this.fieldInfos = state.fieldInfos
        var success = false
        try {
            vectorData =
                openDataInput(
                    state,
                    versionMeta,
                    Lucene99FlatVectorsFormat.VECTOR_DATA_EXTENSION,
                    Lucene99FlatVectorsFormat.VECTOR_DATA_CODEC_NAME,  // Flat formats are used to randomly access vectors from their node ID that is stored
                    // in the HNSW graph.
                    state.context.withReadAdvice(ReadAdvice.RANDOM)
                )
            success = true
        } finally {
            if (!success) {
                IOUtils.closeWhileHandlingException(this)
            }
        }
    }

    @Throws(IOException::class)
    private fun readMetadata(state: SegmentReadState): Int {
        val metaFileName: String =
            IndexFileNames.segmentFileName(
                state.segmentInfo.name, state.segmentSuffix, Lucene99FlatVectorsFormat.META_EXTENSION
            )
        var versionMeta = -1
        state.directory.openChecksumInput(metaFileName).use { meta ->
            var priorE: Throwable? = null
            try {
                versionMeta =
                    CodecUtil.checkIndexHeader(
                        meta,
                        Lucene99FlatVectorsFormat.META_CODEC_NAME,
                        Lucene99FlatVectorsFormat.VERSION_START,
                        Lucene99FlatVectorsFormat.VERSION_CURRENT,
                        state.segmentInfo.getId(),
                        state.segmentSuffix
                    )
                readFields(meta, state.fieldInfos)
            } catch (exception: Throwable) {
                priorE = exception
            } finally {
                CodecUtil.checkFooter(meta, priorE)
            }
        }
        return versionMeta
    }

    @Throws(IOException::class)
    private fun readFields(meta: ChecksumIndexInput, infos: FieldInfos) {
        var fieldNumber: Int = meta.readInt()
        while (fieldNumber != -1) {
            val info: FieldInfo? = infos.fieldInfo(fieldNumber)
            if (info == null) {
                throw CorruptIndexException("Invalid field number: $fieldNumber", meta)
            }
            val fieldEntry = FieldEntry.Companion.create(meta, info)
            fields.put(info.number, fieldEntry)
            fieldNumber = meta.readInt()
        }
    }

    override fun ramBytesUsed(): Long {
        return SHALLOW_SIZE + fields.ramBytesUsed()
    }

    @Throws(IOException::class)
    override fun checkIntegrity() {
        CodecUtil.checksumEntireFile(vectorData)
    }

    override val mergeInstance: FlatVectorsReader
        get() {
            try {
                // Update the read advice since vectors are guaranteed to be accessed sequentially for merge
                this.vectorData.updateReadAdvice(ReadAdvice.SEQUENTIAL)
                return this
            } catch (exception: IOException) {
                throw UncheckedIOException(exception)
            }
        }

    private fun getFieldEntry(field: String, expectedEncoding: VectorEncoding): FieldEntry {
        val info: FieldInfo? = fieldInfos.fieldInfo(field)
        val fieldEntry: FieldEntry? = fields[info!!.number]
        require(
            !(info == null || fieldEntry == null)
        ) { "field=\"$field\" not found" }
        require(fieldEntry.vectorEncoding === expectedEncoding) {
            ("field=\""
                    + field
                    + "\" is encoded as: "
                    + fieldEntry.vectorEncoding
                    + " expected: "
                    + expectedEncoding)
        }
        return fieldEntry
    }

    @Throws(IOException::class)
    override fun getFloatVectorValues(field: String): FloatVectorValues {
        val fieldEntry = getFieldEntry(field, FLOAT32)
        return OffHeapFloatVectorValues.load(
            fieldEntry.similarityFunction,
            vectorScorer,
            fieldEntry.ordToDoc,
            fieldEntry.vectorEncoding,
            fieldEntry.dimension,
            fieldEntry.vectorDataOffset,
            fieldEntry.vectorDataLength,
            vectorData
        )
    }

    @Throws(IOException::class)
    override fun getByteVectorValues(field: String): ByteVectorValues {
        val fieldEntry = getFieldEntry(field, BYTE)
        return OffHeapByteVectorValues.load(
            fieldEntry.similarityFunction,
            vectorScorer,
            fieldEntry.ordToDoc,
            fieldEntry.vectorEncoding,
            fieldEntry.dimension,
            fieldEntry.vectorDataOffset,
            fieldEntry.vectorDataLength,
            vectorData
        )
    }

    @Throws(IOException::class)
    override fun getRandomVectorScorer(field: String, target: FloatArray): RandomVectorScorer {
        val fieldEntry = getFieldEntry(field, FLOAT32)
        return vectorScorer.getRandomVectorScorer(
            fieldEntry.similarityFunction,
            OffHeapFloatVectorValues.load(
                fieldEntry.similarityFunction,
                vectorScorer,
                fieldEntry.ordToDoc,
                fieldEntry.vectorEncoding,
                fieldEntry.dimension,
                fieldEntry.vectorDataOffset,
                fieldEntry.vectorDataLength,
                vectorData
            ),
            target
        )
    }

    @Throws(IOException::class)
    override fun getRandomVectorScorer(field: String, target: ByteArray): RandomVectorScorer {
        val fieldEntry = getFieldEntry(field, BYTE)
        return vectorScorer.getRandomVectorScorer(
            fieldEntry.similarityFunction,
            OffHeapByteVectorValues.load(
                fieldEntry.similarityFunction,
                vectorScorer,
                fieldEntry.ordToDoc,
                fieldEntry.vectorEncoding,
                fieldEntry.dimension,
                fieldEntry.vectorDataOffset,
                fieldEntry.vectorDataLength,
                vectorData
            ),
            target
        )
    }

    @Throws(IOException::class)
    override fun finishMerge() {
        // This makes sure that the access pattern hint is reverted back since HNSW implementation
        // needs it
        this.vectorData.updateReadAdvice(ReadAdvice.RANDOM)
    }

    @Throws(IOException::class)
    override fun close() {
        IOUtils.close(vectorData)
    }

    private class FieldEntry(
        val similarityFunction: VectorSimilarityFunction,
        val vectorEncoding: VectorEncoding,
        val vectorDataOffset: Long,
        val vectorDataLength: Long,
        val dimension: Int,
        val size: Int,
        val ordToDoc: OrdToDocDISIReaderConfiguration,
        val info: FieldInfo
    ) {

        init {
            check(similarityFunction === info.vectorSimilarityFunction) {
                ("Inconsistent vector similarity function for field=\""
                        + info.name
                        + "\"; "
                        + similarityFunction
                        + " != "
                        + info.vectorSimilarityFunction)
            }
            val infoVectorDimension: Int = info.vectorDimension
            check(infoVectorDimension == dimension) {
                ("Inconsistent vector dimension for field=\""
                        + info.name
                        + "\"; "
                        + infoVectorDimension
                        + " != "
                        + dimension)
            }

            val byteSize: Int =
                when (info.vectorEncoding) {
                    BYTE -> Byte.SIZE_BYTES
                    FLOAT32 -> Float.SIZE_BYTES
                }
            val vectorBytes: Long = Math.multiplyExact(infoVectorDimension, byteSize).toLong()
            val numBytes: Long = Math.multiplyExact(vectorBytes.toInt(), size).toLong()
            check(numBytes == vectorDataLength) {
                ("Vector data length "
                        + vectorDataLength
                        + " not matching size="
                        + size
                        + " * dim="
                        + dimension
                        + " * byteSize="
                        + byteSize
                        + " = "
                        + numBytes)
            }
        }

        companion object {
            @Throws(IOException::class)
            fun create(input: IndexInput, info: FieldInfo): FieldEntry {
                val vectorEncoding: VectorEncoding = readVectorEncoding(input)
                val similarityFunction: VectorSimilarityFunction = readSimilarityFunction(input)
                val vectorDataOffset: Long /* TODO: class org.jetbrains.kotlin.nj2k.types.JKJavaNullPrimitiveType */ =
                    input.readVLong()
                val vectorDataLength: Long /* TODO: class org.jetbrains.kotlin.nj2k.types.JKJavaNullPrimitiveType */ =
                    input.readVLong()
                val dimension: Int /* TODO: class org.jetbrains.kotlin.nj2k.types.JKJavaNullPrimitiveType */ =
                    input.readVInt()
                val size: Int /* TODO: class org.jetbrains.kotlin.nj2k.types.JKJavaNullPrimitiveType */ =
                    input.readInt()
                val ordToDoc: OrdToDocDISIReaderConfiguration /* TODO: class org.jetbrains.kotlin.nj2k.types.JKJavaNullPrimitiveType */ =
                    OrdToDocDISIReaderConfiguration.fromStoredMeta(input, size)
                return FieldEntry(
                    similarityFunction,
                    vectorEncoding,
                    vectorDataOffset,
                    vectorDataLength,
                    dimension,
                    size,
                    ordToDoc,
                    info
                )
            }
        }
    }

    companion object {
        private val SHALLOW_SIZE: Long = RamUsageEstimator.shallowSizeOfInstance(Lucene99FlatVectorsFormat::class)

        @Throws(IOException::class)
        private fun openDataInput(
            state: SegmentReadState,
            versionMeta: Int,
            fileExtension: String,
            codecName: String,
            context: IOContext
        ): IndexInput {
            val fileName: String =
                IndexFileNames.segmentFileName(state.segmentInfo.name, state.segmentSuffix, fileExtension)
            val `in`: IndexInput = state.directory.openInput(fileName, context)
            var success = false
            try {
                val versionVectorData: Int =
                    CodecUtil.checkIndexHeader(
                        `in`,
                        codecName,
                        Lucene99FlatVectorsFormat.VERSION_START,
                        Lucene99FlatVectorsFormat.VERSION_CURRENT,
                        state.segmentInfo.getId(),
                        state.segmentSuffix
                    )
                if (versionMeta != versionVectorData) {
                    throw CorruptIndexException(
                        ("Format versions mismatch: meta="
                                + versionMeta
                                + ", "
                                + codecName
                                + "="
                                + versionVectorData),
                        `in`
                    )
                }
                CodecUtil.retrieveChecksum(`in`)
                success = true
                return `in`
            } finally {
                if (!success) {
                    IOUtils.closeWhileHandlingException(`in`)
                }
            }
        }
    }
}
