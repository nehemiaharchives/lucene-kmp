package org.gnit.lucenekmp.codecs.lucene99

import okio.IOException
import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.codecs.hnsw.FlatVectorsReader
import org.gnit.lucenekmp.codecs.hnsw.FlatVectorsScorer
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
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.internal.hppc.IntObjectHashMap
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.intBitsToFloat
import org.gnit.lucenekmp.search.VectorScorer
import org.gnit.lucenekmp.store.ChecksumIndexInput
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.ReadAdvice
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.RamUsageEstimator
import org.gnit.lucenekmp.util.hnsw.RandomVectorScorer
import org.gnit.lucenekmp.util.quantization.QuantizedByteVectorValues
import org.gnit.lucenekmp.util.quantization.QuantizedVectorsReader
import org.gnit.lucenekmp.util.quantization.ScalarQuantizer

/**
 * Reads Scalar Quantized vectors from the index segments along with index data structures.
 *
 * @lucene.experimental
 */
class Lucene99ScalarQuantizedVectorsReader(
    state: SegmentReadState,
    private val rawVectorsReader: FlatVectorsReader,
    scorer: FlatVectorsScorer
) : FlatVectorsReader(scorer), QuantizedVectorsReader {

    private val fields: IntObjectHashMap<FieldEntry> = IntObjectHashMap()
    private val quantizedVectorData: IndexInput
    private val fieldInfos: FieldInfos = state.fieldInfos

    init {
        var versionMeta = -1
        val metaFileName = IndexFileNames.segmentFileName(
            state.segmentInfo.name,
            state.segmentSuffix,
            Lucene99ScalarQuantizedVectorsFormat.META_EXTENSION
        )
        var success = false
        try {
            state.directory.openChecksumInput(metaFileName).use { meta ->
                var priorE: Throwable? = null
                try {
                    versionMeta =
                        CodecUtil.checkIndexHeader(
                            meta,
                            Lucene99ScalarQuantizedVectorsFormat.META_CODEC_NAME,
                            Lucene99ScalarQuantizedVectorsFormat.VERSION_START,
                            Lucene99ScalarQuantizedVectorsFormat.VERSION_CURRENT,
                            state.segmentInfo.getId(),
                            state.segmentSuffix
                        )
                    readFields(meta, versionMeta, state.fieldInfos)
                } catch (exception: Throwable) {
                    priorE = exception
                } finally {
                    CodecUtil.checkFooter(meta, priorE)
                }
            }
            quantizedVectorData =
                openDataInput(
                    state,
                    versionMeta,
                    Lucene99ScalarQuantizedVectorsFormat.VECTOR_DATA_EXTENSION,
                    Lucene99ScalarQuantizedVectorsFormat.VECTOR_DATA_CODEC_NAME,
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
    private fun readFields(meta: ChecksumIndexInput, versionMeta: Int, infos: FieldInfos) {
        var fieldNumber = meta.readInt()
        while (fieldNumber != -1) {
            val info = infos.fieldInfo(fieldNumber)
                ?: throw CorruptIndexException("Invalid field number: $fieldNumber", meta)
            val fieldEntry = readField(meta, versionMeta, info)
            validateFieldEntry(info, fieldEntry)
            fields.put(info.number, fieldEntry)
            fieldNumber = meta.readInt()
        }
    }

    @Throws(IOException::class)
    override fun checkIntegrity() {
        rawVectorsReader.checkIntegrity()
        CodecUtil.checksumEntireFile(quantizedVectorData)
    }

    private fun getFieldEntry(field: String): FieldEntry {
        val info = fieldInfos.fieldInfo(field)
        val fieldEntry = if (info == null) null else fields[info.number]
        require(!(info == null || fieldEntry == null)) { "field=\"$field\" not found" }
        require(fieldEntry.vectorEncoding == VectorEncoding.FLOAT32) {
            "field=\"$field\" is encoded as: ${fieldEntry.vectorEncoding} expected: ${VectorEncoding.FLOAT32}"
        }
        return fieldEntry
    }

    @Throws(IOException::class)
    override fun getFloatVectorValues(field: String): FloatVectorValues {
        val fieldEntry = getFieldEntry(field)
        val rawVectorValues = rawVectorsReader.getFloatVectorValues(field)
        val quantizedByteVectorValues = OffHeapQuantizedByteVectorValues.load(
            fieldEntry.ordToDoc,
            fieldEntry.dimension,
            fieldEntry.size,
            fieldEntry.scalarQuantizer,
            fieldEntry.similarityFunction,
            vectorScorer,
            fieldEntry.compress,
            fieldEntry.vectorDataOffset,
            fieldEntry.vectorDataLength,
            quantizedVectorData
        )
        return QuantizedVectorValues(rawVectorValues!!, quantizedByteVectorValues)
    }

    @Throws(IOException::class)
    override fun getByteVectorValues(field: String): ByteVectorValues? {
        return rawVectorsReader.getByteVectorValues(field)
    }

    @Throws(IOException::class)
    override fun getRandomVectorScorer(field: String, target: FloatArray): RandomVectorScorer {
        val fieldEntry = getFieldEntry(field)
        if (fieldEntry.scalarQuantizer == null) {
            return rawVectorsReader.getRandomVectorScorer(field, target)
        }
        val vectorValues = OffHeapQuantizedByteVectorValues.load(
            fieldEntry.ordToDoc,
            fieldEntry.dimension,
            fieldEntry.size,
            fieldEntry.scalarQuantizer,
            fieldEntry.similarityFunction,
            vectorScorer,
            fieldEntry.compress,
            fieldEntry.vectorDataOffset,
            fieldEntry.vectorDataLength,
            quantizedVectorData
        )
        return vectorScorer.getRandomVectorScorer(fieldEntry.similarityFunction, vectorValues, target)
    }

    @Throws(IOException::class)
    override fun getRandomVectorScorer(field: String, target: ByteArray): RandomVectorScorer {
        return rawVectorsReader.getRandomVectorScorer(field, target)
    }

    @Throws(IOException::class)
    override fun close() {
        IOUtils.close(quantizedVectorData, rawVectorsReader)
    }

    override fun ramBytesUsed(): Long {
        return SHALLOW_SIZE + fields.ramBytesUsed() + rawVectorsReader.ramBytesUsed()
    }

    private fun readField(input: IndexInput, versionMeta: Int, info: FieldInfo): FieldEntry {
        val vectorEncoding = readVectorEncoding(input)
        val similarityFunction = readSimilarityFunction(input)
        if (similarityFunction != info.vectorSimilarityFunction) {
            throw IllegalStateException(
                "Inconsistent vector similarity function for field=\"${info.name}\"; " +
                    "$similarityFunction != ${info.vectorSimilarityFunction}"
            )
        }
        return FieldEntry.create(input, versionMeta, vectorEncoding, info.vectorSimilarityFunction)
    }

    @Throws(IOException::class)
    override fun getQuantizedVectorValues(fieldName: String): QuantizedByteVectorValues {
        val fieldEntry = getFieldEntry(fieldName)
        return OffHeapQuantizedByteVectorValues.load(
            fieldEntry.ordToDoc,
            fieldEntry.dimension,
            fieldEntry.size,
            fieldEntry.scalarQuantizer,
            fieldEntry.similarityFunction,
            vectorScorer,
            fieldEntry.compress,
            fieldEntry.vectorDataOffset,
            fieldEntry.vectorDataLength,
            quantizedVectorData
        )
    }

    override fun getQuantizationState(fieldName: String): ScalarQuantizer? {
        val fieldEntry = getFieldEntry(fieldName)
        return fieldEntry.scalarQuantizer
    }

    companion object {
        private val SHALLOW_SIZE =
            RamUsageEstimator.shallowSizeOfInstance(Lucene99ScalarQuantizedVectorsReader::class)

        @Throws(IOException::class)
        private fun openDataInput(
            state: SegmentReadState,
            versionMeta: Int,
            fileExtension: String,
            codecName: String,
            context: IOContext
        ): IndexInput {
            val fileName =
                IndexFileNames.segmentFileName(state.segmentInfo.name, state.segmentSuffix, fileExtension)
            val `in` = state.directory.openInput(fileName, context)
            var success = false
            try {
                val versionVectorData =
                    CodecUtil.checkIndexHeader(
                        `in`,
                        codecName,
                        Lucene99ScalarQuantizedVectorsFormat.VERSION_START,
                        Lucene99ScalarQuantizedVectorsFormat.VERSION_CURRENT,
                        state.segmentInfo.getId(),
                        state.segmentSuffix
                    )
                if (versionMeta != versionVectorData) {
                    throw CorruptIndexException(
                        "Format versions mismatch: meta=$versionMeta, $codecName=$versionVectorData",
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

        fun validateFieldEntry(info: FieldInfo, fieldEntry: FieldEntry) {
            val dimension = info.vectorDimension
            if (dimension != fieldEntry.dimension) {
                throw IllegalStateException(
                    "Inconsistent vector dimension for field=\"${info.name}\"; " +
                        "$dimension != ${fieldEntry.dimension}"
                )
            }
            val quantizedVectorBytes = if (fieldEntry.bits <= 4 && fieldEntry.compress) {
                ((dimension + 1) shr 1) + Float.SIZE_BYTES
            } else {
                dimension + Float.SIZE_BYTES
            }
            val numQuantizedVectorBytes = Math.multiplyExact(quantizedVectorBytes, fieldEntry.size).toLong()
            if (numQuantizedVectorBytes != fieldEntry.vectorDataLength) {
                throw IllegalStateException(
                    "Quantized vector data length ${fieldEntry.vectorDataLength} not matching size=" +
                        "${fieldEntry.size} * (dim=$dimension + 4) = $numQuantizedVectorBytes"
                )
            }
        }
    }

    class FieldEntry(
        val similarityFunction: VectorSimilarityFunction,
        val vectorEncoding: VectorEncoding,
        val dimension: Int,
        val vectorDataOffset: Long,
        val vectorDataLength: Long,
        val scalarQuantizer: ScalarQuantizer?,
        val size: Int,
        val bits: Byte,
        val compress: Boolean,
        val ordToDoc: OrdToDocDISIReaderConfiguration
    ) {
        companion object {
            @Throws(IOException::class)
            fun create(
                input: IndexInput,
                versionMeta: Int,
                vectorEncoding: VectorEncoding,
                similarityFunction: VectorSimilarityFunction
            ): FieldEntry {
                val vectorDataOffset = input.readVLong()
                val vectorDataLength = input.readVLong()
                val dimension = input.readVInt()
                val size = input.readInt()
                val scalarQuantizer: ScalarQuantizer?
                val bits: Byte
                val compress: Boolean
                if (size > 0) {
                    if (versionMeta < Lucene99ScalarQuantizedVectorsFormat.VERSION_ADD_BITS) {
                        val floatBits = input.readInt()
                        if (floatBits == -1) {
                            throw CorruptIndexException(
                                "Missing confidence interval for scalar quantizer",
                                input
                            )
                        }
                        val confidenceInterval = Float.intBitsToFloat(floatBits)
                        if (confidenceInterval == Lucene99ScalarQuantizedVectorsFormat.DYNAMIC_CONFIDENCE_INTERVAL) {
                            throw CorruptIndexException(
                                "Invalid confidence interval for scalar quantizer: $confidenceInterval",
                                input
                            )
                        }
                        bits = 7
                        compress = false
                        val minQuantile = Float.intBitsToFloat(input.readInt())
                        val maxQuantile = Float.intBitsToFloat(input.readInt())
                        scalarQuantizer = ScalarQuantizer(minQuantile, maxQuantile, bits)
                    } else {
                        input.readInt() // confidenceInterval, unused
                        bits = input.readByte()
                        compress = input.readByte().toInt() == 1
                        val minQuantile = Float.intBitsToFloat(input.readInt())
                        val maxQuantile = Float.intBitsToFloat(input.readInt())
                        scalarQuantizer = ScalarQuantizer(minQuantile, maxQuantile, bits)
                    }
                } else {
                    scalarQuantizer = null
                    bits = 7
                    compress = false
                }
                val ordToDoc = OrdToDocDISIReaderConfiguration.fromStoredMeta(input, size)
                return FieldEntry(
                    similarityFunction,
                    vectorEncoding,
                    dimension,
                    vectorDataOffset,
                    vectorDataLength,
                    scalarQuantizer,
                    size,
                    bits,
                    compress,
                    ordToDoc
                )
            }
        }
    }

    private class QuantizedVectorValues(
        private val rawVectorValues: FloatVectorValues,
        private val quantizedVectorValues: QuantizedByteVectorValues
    ) : FloatVectorValues() {

        override fun dimension(): Int {
            return rawVectorValues.dimension()
        }

        override fun size(): Int {
            return rawVectorValues.size()
        }

        @Throws(IOException::class)
        override fun vectorValue(ord: Int): FloatArray {
            return rawVectorValues.vectorValue(ord)
        }

        override fun ordToDoc(ord: Int): Int {
            return rawVectorValues.ordToDoc(ord)
        }

        @Throws(IOException::class)
        override fun copy(): QuantizedVectorValues {
            return QuantizedVectorValues(rawVectorValues.copy(), quantizedVectorValues.copy())
        }

        @Throws(IOException::class)
        override fun scorer(query: FloatArray): VectorScorer? {
            return quantizedVectorValues.scorer(query)
        }

        override fun iterator(): DocIndexIterator {
            return rawVectorValues.iterator()
        }
    }
}
