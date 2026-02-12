package org.gnit.lucenekmp.codecs.lucene102

import okio.IOException
import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.codecs.hnsw.FlatVectorsReader
import org.gnit.lucenekmp.codecs.lucene95.OrdToDocDISIReaderConfiguration
import org.gnit.lucenekmp.codecs.lucene99.Lucene99HnswVectorsReader
import org.gnit.lucenekmp.index.ByteVectorValues
import org.gnit.lucenekmp.index.CorruptIndexException
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.FieldInfos
import org.gnit.lucenekmp.index.FloatVectorValues
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.VectorEncoding
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.intBitsToFloat
import org.gnit.lucenekmp.search.KnnCollector
import org.gnit.lucenekmp.search.VectorScorer
import org.gnit.lucenekmp.store.ChecksumIndexInput
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.ReadAdvice
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.RamUsageEstimator
import org.gnit.lucenekmp.util.hnsw.OrdinalTranslatedKnnCollector
import org.gnit.lucenekmp.util.hnsw.RandomVectorScorer
import org.gnit.lucenekmp.util.quantization.OptimizedScalarQuantizer


/** Reader for binary quantized vectors in the Lucene 10.2 format.  */
internal class Lucene102BinaryQuantizedVectorsReader(
    state: SegmentReadState,
    private val rawVectorsReader: FlatVectorsReader,
    vectorsScorer: Lucene102BinaryFlatVectorsScorer
) : FlatVectorsReader(vectorsScorer) {
    private val fields: MutableMap<String, FieldEntry> = mutableMapOf()
    private val quantizedVectorData: IndexInput
    override val vectorScorer: Lucene102BinaryFlatVectorsScorer = vectorsScorer

    init {
        var versionMeta = -1
        val metaFileName: String =
            IndexFileNames.segmentFileName(
                state.segmentInfo.name,
                state.segmentSuffix,
                Lucene102BinaryQuantizedVectorsFormat.META_EXTENSION
            )
        var success = false
        try {
            state.directory.openChecksumInput(metaFileName).use { meta ->
                var priorE: Throwable? = null
                try {
                    versionMeta =
                        CodecUtil.checkIndexHeader(
                            meta,
                            Lucene102BinaryQuantizedVectorsFormat.META_CODEC_NAME,
                            Lucene102BinaryQuantizedVectorsFormat.VERSION_START,
                            Lucene102BinaryQuantizedVectorsFormat.VERSION_CURRENT,
                            state.segmentInfo.getId(),
                            state.segmentSuffix
                        )
                    readFields(meta, state.fieldInfos)
                } catch (exception: Throwable) {
                    priorE = exception
                } finally {
                    CodecUtil.checkFooter(meta, priorE)
                }
                quantizedVectorData =
                    openDataInput(
                        state,
                        versionMeta,
                        Lucene102BinaryQuantizedVectorsFormat.VECTOR_DATA_EXTENSION,
                        Lucene102BinaryQuantizedVectorsFormat.VECTOR_DATA_CODEC_NAME,  // Quantized vectors are accessed randomly from their node ID stored in the HNSW
                        // graph.
                        state.context.withReadAdvice(ReadAdvice.RANDOM)
                    )
                success = true
            }
        } finally {
            if (success == false) {
                IOUtils.closeWhileHandlingException(this)
            }
        }
    }

    @Throws(IOException::class)
    private fun readFields(meta: ChecksumIndexInput, infos: FieldInfos) {
        var fieldNumber: Int = meta.readInt()
        while (fieldNumber != -1) {
            val info: FieldInfo? = infos.fieldInfo(fieldNumber)
            if (info == null) {
                throw CorruptIndexException("Invalid field number: $fieldNumber", meta)
            }
            val fieldEntry = readField(meta, info)
            validateFieldEntry(info, fieldEntry)
            fields[info.name] = fieldEntry
            fieldNumber = meta.readInt()
        }
    }

    @Throws(IOException::class)
    override fun getRandomVectorScorer(field: String, target: FloatArray): RandomVectorScorer {
        val fi = fields[field]
        if (fi == null) {
            /*return null*/
            throw Exception("Lucene102BinaryQuantizedVectorsReader.getRandomVectorScorer() fields[field] is null")
        }
        return vectorScorer.getRandomVectorScorer(
            fi.similarityFunction,
            OffHeapBinarizedVectorValues.load(
                fi.ordToDocDISIReaderConfiguration,
                fi.dimension,
                fi.size,
                OptimizedScalarQuantizer(fi.similarityFunction),
                fi.similarityFunction,
                vectorScorer,
                fi.centroid,
                fi.centroidDP,
                fi.vectorDataOffset,
                fi.vectorDataLength,
                quantizedVectorData
            ),
            target
        )
    }

    @Throws(IOException::class)
    override fun getRandomVectorScorer(field: String, target: ByteArray): RandomVectorScorer {
        return rawVectorsReader.getRandomVectorScorer(field, target)
    }

    @Throws(IOException::class)
    override fun checkIntegrity() {
        rawVectorsReader.checkIntegrity()
        CodecUtil.checksumEntireFile(quantizedVectorData)
    }

    @Throws(IOException::class)
    override fun getFloatVectorValues(field: String): FloatVectorValues? {
        val fi = fields[field]
        if (fi == null) {
            return null
        }
        require(fi.vectorEncoding == VectorEncoding.FLOAT32) {
            ("field=\""
                    + field
                    + "\" is encoded as: "
                    + fi.vectorEncoding
                    + " expected: "
                    + VectorEncoding.FLOAT32)
        }
        val bvv: OffHeapBinarizedVectorValues =
            OffHeapBinarizedVectorValues.load(
                fi.ordToDocDISIReaderConfiguration,
                fi.dimension,
                fi.size,
                OptimizedScalarQuantizer(fi.similarityFunction),
                fi.similarityFunction,
                vectorScorer,
                fi.centroid,
                fi.centroidDP,
                fi.vectorDataOffset,
                fi.vectorDataLength,
                quantizedVectorData
            )
        return BinarizedVectorValues(rawVectorsReader.getFloatVectorValues(field), bvv)
    }

    @Throws(IOException::class)
    override fun getByteVectorValues(field: String): ByteVectorValues? {
        return rawVectorsReader.getByteVectorValues(field)
    }

    @Throws(IOException::class)
    override fun search(field: String, target: ByteArray, knnCollector: KnnCollector, acceptDocs: Bits?) {
        rawVectorsReader.search(field, target, knnCollector, acceptDocs)
    }

    @Throws(IOException::class)
    override fun search(field: String, target: FloatArray, knnCollector: KnnCollector, acceptDocs: Bits?) {
        if (knnCollector.k() == 0) return
        val scorer: RandomVectorScorer? = getRandomVectorScorer(field, target)
        if (scorer == null) return
        val collector = OrdinalTranslatedKnnCollector(knnCollector) { ord: Int -> scorer.ordToDoc(ord) }
        val acceptedOrds: Bits? = scorer.getAcceptOrds(acceptDocs)
        for (i in 0..<scorer.maxOrd()) {
            if (acceptedOrds == null || acceptedOrds.get(i)) {
                collector.collect(i, scorer.score(i))
                collector.incVisitedCount(1)
            }
        }
    }

    @Throws(IOException::class)
    override fun close() {
        IOUtils.close(quantizedVectorData, rawVectorsReader)
    }

    override fun ramBytesUsed(): Long {
        var size = SHALLOW_SIZE
        size += RamUsageEstimator.sizeOfMap(fields, RamUsageEstimator.shallowSizeOfInstance(FieldEntry::class))
        size += rawVectorsReader.ramBytesUsed()
        return size
    }

    fun getCentroid(field: String): FloatArray? {
        val fieldEntry = fields[field]
        if (fieldEntry != null) {
            return fieldEntry.centroid
        }
        return null
    }

    @Throws(IOException::class)
    private fun readField(input: IndexInput, info: FieldInfo): FieldEntry {
        val vectorEncoding: VectorEncoding = Lucene99HnswVectorsReader.readVectorEncoding(input)
        val similarityFunction: VectorSimilarityFunction = Lucene99HnswVectorsReader.readSimilarityFunction(input)
        check(similarityFunction === info.vectorSimilarityFunction) {
            ("Inconsistent vector similarity function for field=\""
                    + info.name
                    + "\"; "
                    + similarityFunction
                    + " != "
                    + info.vectorSimilarityFunction)
        }
        return FieldEntry.create(input, vectorEncoding, info.vectorSimilarityFunction)
    }

    class FieldEntry(
        val similarityFunction: VectorSimilarityFunction,
        val vectorEncoding: VectorEncoding,
        val dimension: Int,
        val descritizedDimension: Int,
        val vectorDataOffset: Long,
        val vectorDataLength: Long,
        val size: Int,
        centroid: FloatArray?,
        centroidDP: Float,
        ordToDocDISIReaderConfiguration: OrdToDocDISIReaderConfiguration
    ) {
        val centroid: FloatArray?
        val centroidDP: Float
        val ordToDocDISIReaderConfiguration: OrdToDocDISIReaderConfiguration

        init {
            this.centroid = centroid
            this.centroidDP = centroidDP
            this.ordToDocDISIReaderConfiguration = ordToDocDISIReaderConfiguration
        }

        companion object {
            @Throws(IOException::class)
            fun create(
                input: IndexInput,
                vectorEncoding: VectorEncoding,
                similarityFunction: VectorSimilarityFunction
            ): FieldEntry {
                val dimension: Int = input.readVInt()
                val vectorDataOffset: Long = input.readVLong()
                val vectorDataLength: Long = input.readVLong()
                val size: Int = input.readVInt()
                val centroid: FloatArray?
                var centroidDP = 0f
                if (size > 0) {
                    centroid = FloatArray(dimension)
                    input.readFloats(centroid, 0, dimension)
                    centroidDP = Float.intBitsToFloat(input.readInt())
                } else {
                    centroid = null
                }
                val conf: OrdToDocDISIReaderConfiguration =
                    OrdToDocDISIReaderConfiguration.fromStoredMeta(input, size)
                return FieldEntry(
                    similarityFunction,
                    vectorEncoding,
                    dimension,
                    OptimizedScalarQuantizer.discretize(dimension, 64),
                    vectorDataOffset,
                    vectorDataLength,
                    size,
                    centroid,
                    centroidDP,
                    conf
                )
            }
        }
    }

    /** Binarized vector values holding row and quantized vector values  */
    class BinarizedVectorValues(private val rawVectorValues: FloatVectorValues?, val quantizedVectorValues: BinarizedByteVectorValues) : FloatVectorValues() {

        override fun dimension(): Int {
            return rawVectorValues!!.dimension()
        }

        override fun size(): Int {
            return rawVectorValues!!.size()
        }

        @Throws(IOException::class)
        override fun vectorValue(ord: Int): FloatArray {
            return rawVectorValues!!.vectorValue(ord)
        }

        @Throws(IOException::class)
        override fun copy(): BinarizedVectorValues {
            return BinarizedVectorValues(rawVectorValues!!.copy(), quantizedVectorValues.copy())
        }

        override fun getAcceptOrds(acceptDocs: Bits?): Bits? {
            return rawVectorValues!!.getAcceptOrds(acceptDocs)
        }

        override fun ordToDoc(ord: Int): Int {
            return rawVectorValues!!.ordToDoc(ord)
        }

        override fun iterator(): DocIndexIterator {
            return rawVectorValues!!.iterator()
        }

        @Throws(IOException::class)
        override fun scorer(query: FloatArray): VectorScorer? {
            return quantizedVectorValues.scorer(query)
        }

        /*@Throws(IOException::class)
        fun getQuantizedVectorValues(): BinarizedByteVectorValues {
            return quantizedVectorValues
        }*/
    }

    companion object {
        private val SHALLOW_SIZE: Long = RamUsageEstimator.shallowSizeOfInstance(Lucene102BinaryQuantizedVectorsReader::class)

        fun validateFieldEntry(info: FieldInfo, fieldEntry: FieldEntry) {
            val dimension: Int = info.vectorDimension
            check(dimension == fieldEntry.dimension) {
                ("Inconsistent vector dimension for field=\""
                        + info.name
                        + "\"; "
                        + dimension
                        + " != "
                        + fieldEntry.dimension)
            }

            val binaryDims: Int = OptimizedScalarQuantizer.discretize(dimension, 64) / 8
            val numQuantizedVectorBytes: Long =
                Math.multiplyExact((binaryDims + (Float.SIZE_BYTES * 3) + Short.SIZE_BYTES).toLong(), fieldEntry.size.toLong())
            check(numQuantizedVectorBytes == fieldEntry.vectorDataLength) {
                ("Binarized vector data length "
                        + fieldEntry.vectorDataLength
                        + " not matching size = "
                        + fieldEntry.size
                        + " * (binaryBytes="
                        + binaryDims
                        + " + 14"
                        + ") = "
                        + numQuantizedVectorBytes)
            }
        }

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
                        Lucene102BinaryQuantizedVectorsFormat.VERSION_START,
                        Lucene102BinaryQuantizedVectorsFormat.VERSION_CURRENT,
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
                if (success == false) {
                    IOUtils.closeWhileHandlingException(`in`)
                }
            }
        }
    }
}
