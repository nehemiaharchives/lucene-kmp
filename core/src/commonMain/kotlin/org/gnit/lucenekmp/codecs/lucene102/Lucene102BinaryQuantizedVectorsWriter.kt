package org.gnit.lucenekmp.codecs.lucene102

import okio.IOException
import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.codecs.KnnVectorsReader
import org.gnit.lucenekmp.codecs.hnsw.FlatFieldVectorsWriter
import org.gnit.lucenekmp.codecs.hnsw.FlatVectorsWriter
import org.gnit.lucenekmp.codecs.lucene95.OrdToDocDISIReaderConfiguration
import org.gnit.lucenekmp.codecs.perfield.PerFieldKnnVectorsFormat
import org.gnit.lucenekmp.index.DocsWithFieldSet
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.FloatVectorValues
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.KnnVectorValues
import org.gnit.lucenekmp.index.MergeState
import org.gnit.lucenekmp.index.SegmentWriteState
import org.gnit.lucenekmp.index.Sorter
import org.gnit.lucenekmp.index.VectorEncoding
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.internal.hppc.FloatArrayList
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.ByteBuffer
import org.gnit.lucenekmp.jdkport.ByteOrder
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.floatToIntBits
import org.gnit.lucenekmp.jdkport.toUnsignedInt
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.VectorScorer
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.RamUsageEstimator
import org.gnit.lucenekmp.util.VectorUtil
import org.gnit.lucenekmp.util.hnsw.CloseableRandomVectorScorerSupplier
import org.gnit.lucenekmp.util.hnsw.RandomVectorScorerSupplier
import org.gnit.lucenekmp.util.hnsw.UpdateableRandomVectorScorer
import org.gnit.lucenekmp.util.quantization.OptimizedScalarQuantizer
import kotlin.math.sqrt

/** Copied from Lucene, replace with Lucene's implementation sometime after Lucene 10  */
class Lucene102BinaryQuantizedVectorsWriter(
    private val vectorsScorer: Lucene102BinaryFlatVectorsScorer,
    rawVectorDelegate: FlatVectorsWriter,
    state: SegmentWriteState
) : FlatVectorsWriter(vectorsScorer) {
    private val segmentWriteState: SegmentWriteState = state
    private val fields: MutableList<FieldWriter> = mutableListOf()
    private val meta: IndexOutput
    private val binarizedVectorData: IndexOutput
    private val rawVectorDelegate: FlatVectorsWriter
    private var finished = false

    /**
     * Sole constructor
     *
     * @param vectorsScorer the scorer to use for scoring vectors
     */
    init {
        val metaFileName: String =
            IndexFileNames.segmentFileName(
                state.segmentInfo.name,
                state.segmentSuffix,
                Lucene102BinaryQuantizedVectorsFormat.META_EXTENSION
            )

        val binarizedVectorDataFileName: String =
            IndexFileNames.segmentFileName(
                state.segmentInfo.name,
                state.segmentSuffix,
                Lucene102BinaryQuantizedVectorsFormat.VECTOR_DATA_EXTENSION
            )
        this.rawVectorDelegate = rawVectorDelegate
        var success = false
        try {
            meta = state.directory.createOutput(metaFileName, state.context)
            binarizedVectorData =
                state.directory.createOutput(binarizedVectorDataFileName, state.context)

            CodecUtil.writeIndexHeader(
                meta,
                Lucene102BinaryQuantizedVectorsFormat.META_CODEC_NAME,
                Lucene102BinaryQuantizedVectorsFormat.VERSION_CURRENT,
                state.segmentInfo.getId(),
                state.segmentSuffix
            )
            CodecUtil.writeIndexHeader(
                binarizedVectorData,
                Lucene102BinaryQuantizedVectorsFormat.VECTOR_DATA_CODEC_NAME,
                Lucene102BinaryQuantizedVectorsFormat.VERSION_CURRENT,
                state.segmentInfo.getId(),
                state.segmentSuffix
            )
            success = true
        } finally {
            if (success == false) {
                IOUtils.closeWhileHandlingException(this)
            }
        }
    }

    @Throws(IOException::class)
    override fun addField(fieldInfo: FieldInfo): FlatFieldVectorsWriter<*> {
        val rawVectorDelegate: FlatFieldVectorsWriter<*> = this.rawVectorDelegate.addField(fieldInfo)
        if (fieldInfo.vectorEncoding == VectorEncoding.FLOAT32) {
            val fieldWriter =
                FieldWriter(fieldInfo, rawVectorDelegate as FlatFieldVectorsWriter<FloatArray>)
            fields.add(fieldWriter)
            return fieldWriter
        }
        return rawVectorDelegate
    }

    @Throws(IOException::class)
    override fun flush(maxDoc: Int, sortMap: Sorter.DocMap?) {
        rawVectorDelegate.flush(maxDoc, sortMap)
        for (field in fields) {
            // after raw vectors are written, normalize vectors for clustering and quantization
            if (VectorSimilarityFunction.COSINE === field.fieldInfo.vectorSimilarityFunction) {
                field.normalizeVectors()
            }
            val vectorCount: Int = field.flatFieldVectorsWriter.vectors.size
            val clusterCenter = FloatArray(field.dimensionSums.size)
            if (vectorCount > 0) {
                for (i in field.dimensionSums.indices) {
                    clusterCenter[i] = field.dimensionSums[i] / vectorCount
                }
                if (VectorSimilarityFunction.COSINE === field.fieldInfo.vectorSimilarityFunction) {
                    VectorUtil.l2normalize(clusterCenter)
                }
            }
            if (segmentWriteState.infoStream!!.isEnabled(Lucene102BinaryQuantizedVectorsFormat.BINARIZED_VECTOR_COMPONENT)) {
                segmentWriteState.infoStream.message(Lucene102BinaryQuantizedVectorsFormat.BINARIZED_VECTOR_COMPONENT, "Vectors' count:$vectorCount")
            }
            val quantizer =
                OptimizedScalarQuantizer(field.fieldInfo.vectorSimilarityFunction)
            if (sortMap == null) {
                writeField(field, clusterCenter, maxDoc, quantizer)
            } else {
                writeSortingField(field, clusterCenter, maxDoc, sortMap, quantizer)
            }
            field.finish()
        }
    }

    @Throws(IOException::class)
    private fun writeField(
        fieldData: FieldWriter, clusterCenter: FloatArray, maxDoc: Int, quantizer: OptimizedScalarQuantizer
    ) {
        // write vector values
        val vectorDataOffset: Long = binarizedVectorData.alignFilePointer(Float.SIZE_BYTES)
        writeBinarizedVectors(fieldData, clusterCenter, quantizer)
        val vectorDataLength: Long = binarizedVectorData.filePointer - vectorDataOffset
        val centroidDp =
            if (fieldData.vectors.isNotEmpty()) VectorUtil.dotProduct(clusterCenter, clusterCenter) else 0f

        writeMeta(
            fieldData.fieldInfo,
            maxDoc,
            vectorDataOffset,
            vectorDataLength,
            clusterCenter,
            centroidDp,
            fieldData.docsWithFieldSet
        )
    }

    @Throws(IOException::class)
    private fun writeBinarizedVectors(
        fieldData: FieldWriter, clusterCenter: FloatArray, scalarQuantizer: OptimizedScalarQuantizer
    ) {
        val discreteDims: Int = OptimizedScalarQuantizer.discretize(fieldData.fieldInfo.vectorDimension, 64)
        val quantizationScratch = ByteArray(discreteDims)
        val vector = ByteArray(discreteDims / 8)
        for (i in fieldData.vectors.indices) {
            val v = fieldData.vectors[i]
            val corrections: OptimizedScalarQuantizer.QuantizationResult =
                scalarQuantizer.scalarQuantize(v, quantizationScratch, Lucene102BinaryQuantizedVectorsFormat.INDEX_BITS, clusterCenter)
            OptimizedScalarQuantizer.packAsBinary(quantizationScratch, vector)
            binarizedVectorData.writeBytes(vector, vector.size)
            binarizedVectorData.writeInt(Float.floatToIntBits(corrections.lowerInterval))
            binarizedVectorData.writeInt(Float.floatToIntBits(corrections.upperInterval))
            binarizedVectorData.writeInt(Float.floatToIntBits(corrections.additionalCorrection))
            assert(corrections.quantizedComponentSum in 0..0xffff)
            binarizedVectorData.writeShort(corrections.quantizedComponentSum.toShort())
        }
    }

    @Throws(IOException::class)
    private fun writeSortingField(
        fieldData: FieldWriter,
        clusterCenter: FloatArray,
        maxDoc: Int,
        sortMap: Sorter.DocMap,
        scalarQuantizer: OptimizedScalarQuantizer
    ) {
        val ordMap = IntArray(fieldData.docsWithFieldSet.cardinality()) // new ord to old ord

        val newDocsWithField = DocsWithFieldSet()
        mapOldOrdToNewOrd(fieldData.docsWithFieldSet, sortMap, null, ordMap, newDocsWithField)

        // write vector values
        val vectorDataOffset: Long = binarizedVectorData.alignFilePointer(Float.SIZE_BYTES)
        writeSortedBinarizedVectors(fieldData, clusterCenter, ordMap, scalarQuantizer)
        val quantizedVectorLength: Long = binarizedVectorData.filePointer - vectorDataOffset

        val centroidDp: Float = VectorUtil.dotProduct(clusterCenter, clusterCenter)
        writeMeta(
            fieldData.fieldInfo,
            maxDoc,
            vectorDataOffset,
            quantizedVectorLength,
            clusterCenter,
            centroidDp,
            newDocsWithField
        )
    }

    @Throws(IOException::class)
    private fun writeSortedBinarizedVectors(
        fieldData: FieldWriter,
        clusterCenter: FloatArray,
        ordMap: IntArray,
        scalarQuantizer: OptimizedScalarQuantizer
    ) {
        val discreteDims: Int = OptimizedScalarQuantizer.discretize(fieldData.fieldInfo.vectorDimension, 64)
        val quantizationScratch = ByteArray(discreteDims)
        val vector = ByteArray(discreteDims / 8)
        for (ordinal in ordMap) {
            val v = fieldData.vectors[ordinal]
            val corrections: OptimizedScalarQuantizer.QuantizationResult = scalarQuantizer.scalarQuantize(v, quantizationScratch, Lucene102BinaryQuantizedVectorsFormat.INDEX_BITS, clusterCenter)
            OptimizedScalarQuantizer.packAsBinary(quantizationScratch, vector)
            binarizedVectorData.writeBytes(vector, vector.size)
            binarizedVectorData.writeInt(Float.floatToIntBits(corrections.lowerInterval))
            binarizedVectorData.writeInt(Float.floatToIntBits(corrections.upperInterval))
            binarizedVectorData.writeInt(Float.floatToIntBits(corrections.additionalCorrection))
            assert(corrections.quantizedComponentSum in 0..0xffff)
            binarizedVectorData.writeShort(corrections.quantizedComponentSum.toShort())
        }
    }

    @Throws(IOException::class)
    private fun writeMeta(
        field: FieldInfo,
        maxDoc: Int,
        vectorDataOffset: Long,
        vectorDataLength: Long,
        clusterCenter: FloatArray,
        centroidDp: Float,
        docsWithField: DocsWithFieldSet
    ) {
        meta.writeInt(field.number)
        meta.writeInt(field.vectorEncoding.ordinal)
        meta.writeInt(field.vectorSimilarityFunction.ordinal)
        meta.writeVInt(field.vectorDimension)
        meta.writeVLong(vectorDataOffset)
        meta.writeVLong(vectorDataLength)
        val count: Int = docsWithField.cardinality()
        meta.writeVInt(count)
        if (count > 0) {
            val buffer: ByteBuffer =
                ByteBuffer.allocate(field.vectorDimension * Float.SIZE_BYTES)
                    .order(ByteOrder.LITTLE_ENDIAN)
            buffer.asFloatBuffer().put(clusterCenter)
            meta.writeBytes(buffer.array(), buffer.array().size)
            meta.writeInt(Float.floatToIntBits(centroidDp))
        }
        OrdToDocDISIReaderConfiguration.writeStoredMeta(
            Lucene102BinaryQuantizedVectorsFormat.DIRECT_MONOTONIC_BLOCK_SHIFT, meta, binarizedVectorData, count, maxDoc, docsWithField
        )
    }

    @Throws(IOException::class)
    override fun finish() {
        check(!finished) { "already finished" }
        finished = true
        rawVectorDelegate.finish()
        if (meta != null) {
            // write end of fields marker
            meta.writeInt(-1)
            CodecUtil.writeFooter(meta)
        }
        if (binarizedVectorData != null) {
            CodecUtil.writeFooter(binarizedVectorData)
        }
    }

    @Throws(IOException::class)
    override fun mergeOneField(fieldInfo: FieldInfo, mergeState: MergeState) {
        if (fieldInfo.vectorEncoding == VectorEncoding.FLOAT32) {
            val centroid: FloatArray
            val mergedCentroid = FloatArray(fieldInfo.vectorDimension)
            val vectorCount = mergeAndRecalculateCentroids(mergeState, fieldInfo, mergedCentroid)
            // Don't need access to the random vectors, we can just use the merged
            rawVectorDelegate.mergeOneField(fieldInfo, mergeState)
            centroid = mergedCentroid
            if (segmentWriteState.infoStream!!.isEnabled(Lucene102BinaryQuantizedVectorsFormat.BINARIZED_VECTOR_COMPONENT)) {
                segmentWriteState.infoStream.message(
                    Lucene102BinaryQuantizedVectorsFormat.BINARIZED_VECTOR_COMPONENT, "Vectors' count:$vectorCount"
                )
            }
            var floatVectorValues: FloatVectorValues = MergedVectorValues.mergeFloatVectorValues(fieldInfo, mergeState)
            if (fieldInfo.vectorSimilarityFunction === VectorSimilarityFunction.COSINE) {
                floatVectorValues = NormalizedFloatVectorValues(floatVectorValues)
            }
            val binarizedVectorValues =
                BinarizedFloatVectorValues(
                    floatVectorValues,
                    OptimizedScalarQuantizer(fieldInfo.vectorSimilarityFunction),
                    centroid
                )
            val vectorDataOffset: Long = binarizedVectorData.alignFilePointer(Float.SIZE_BYTES)
            val docsWithField = writeBinarizedVectorData(binarizedVectorData, binarizedVectorValues)
            val vectorDataLength: Long = binarizedVectorData.filePointer - vectorDataOffset
            val centroidDp = if (docsWithField.cardinality() > 0) VectorUtil.dotProduct(centroid, centroid) else 0f
            writeMeta(
                fieldInfo,
                segmentWriteState.segmentInfo.maxDoc(),
                vectorDataOffset,
                vectorDataLength,
                centroid,
                centroidDp,
                docsWithField
            )
        } else {
            rawVectorDelegate.mergeOneField(fieldInfo, mergeState)
        }
    }

    @Throws(IOException::class)
    override fun mergeOneFieldToIndex(
        fieldInfo: FieldInfo, mergeState: MergeState
    ): CloseableRandomVectorScorerSupplier {
        if (fieldInfo.vectorEncoding == VectorEncoding.FLOAT32) {
            val centroid: FloatArray
            val cDotC: Float
            val mergedCentroid = FloatArray(fieldInfo.vectorDimension)
            val vectorCount = mergeAndRecalculateCentroids(mergeState, fieldInfo, mergedCentroid)

            // Don't need access to the random vectors, we can just use the merged
            rawVectorDelegate.mergeOneField(fieldInfo, mergeState)
            centroid = mergedCentroid
            cDotC = if (vectorCount > 0) VectorUtil.dotProduct(centroid, centroid) else 0f
            if (segmentWriteState.infoStream!!.isEnabled(Lucene102BinaryQuantizedVectorsFormat.BINARIZED_VECTOR_COMPONENT)) {
                segmentWriteState.infoStream.message(
                    Lucene102BinaryQuantizedVectorsFormat.BINARIZED_VECTOR_COMPONENT, "Vectors' count:$vectorCount"
                )
            }
            return mergeOneFieldToIndex(segmentWriteState, fieldInfo, mergeState, centroid, cDotC)
        }
        return rawVectorDelegate.mergeOneFieldToIndex(fieldInfo, mergeState)
    }

    @Throws(IOException::class)
    private fun mergeOneFieldToIndex(
        segmentWriteState: SegmentWriteState,
        fieldInfo: FieldInfo,
        mergeState: MergeState,
        centroid: FloatArray,
        cDotC: Float
    ): CloseableRandomVectorScorerSupplier {
        val vectorDataOffset: Long = binarizedVectorData.alignFilePointer(Float.SIZE_BYTES)
        val tempQuantizedVectorData: IndexOutput =
            segmentWriteState.directory.createTempOutput(
                binarizedVectorData.name!!, "temp", segmentWriteState.context
            )
        val tempScoreQuantizedVectorData: IndexOutput =
            segmentWriteState.directory.createTempOutput(
                binarizedVectorData.name!!, "score_temp", segmentWriteState.context
            )
        var binarizedDataInput: IndexInput? = null
        var binarizedScoreDataInput: IndexInput? = null
        var success = false
        val quantizer =
            OptimizedScalarQuantizer(fieldInfo.vectorSimilarityFunction)
        try {
            var floatVectorValues: FloatVectorValues = MergedVectorValues.mergeFloatVectorValues(fieldInfo, mergeState)
            if (fieldInfo.vectorSimilarityFunction === VectorSimilarityFunction.COSINE) {
                floatVectorValues = NormalizedFloatVectorValues(floatVectorValues)
            }
            val docsWithField =
                writeBinarizedVectorAndQueryData(
                    tempQuantizedVectorData,
                    tempScoreQuantizedVectorData,
                    floatVectorValues,
                    centroid,
                    quantizer
                )
            CodecUtil.writeFooter(tempQuantizedVectorData)
            IOUtils.close(tempQuantizedVectorData)
            binarizedDataInput =
                segmentWriteState.directory.openInput(
                    tempQuantizedVectorData.name!!, segmentWriteState.context
                )
            binarizedVectorData.copyBytes(
                binarizedDataInput, binarizedDataInput.length() - CodecUtil.footerLength()
            )
            val vectorDataLength: Long = binarizedVectorData.filePointer - vectorDataOffset
            CodecUtil.retrieveChecksum(binarizedDataInput)
            CodecUtil.writeFooter(tempScoreQuantizedVectorData)
            IOUtils.close(tempScoreQuantizedVectorData)
            binarizedScoreDataInput = segmentWriteState.directory.openInput(tempScoreQuantizedVectorData.name!!, segmentWriteState.context)
            writeMeta(
                fieldInfo,
                segmentWriteState.segmentInfo.maxDoc(),
                vectorDataOffset,
                vectorDataLength,
                centroid,
                cDotC,
                docsWithField
            )
            success = true
            val finalBinarizedDataInput: IndexInput = binarizedDataInput
            val finalBinarizedScoreDataInput: IndexInput = binarizedScoreDataInput
            val vectorValues: OffHeapBinarizedVectorValues =
                OffHeapBinarizedVectorValues.DenseOffHeapVectorValues(
                    fieldInfo.vectorDimension,
                    docsWithField.cardinality(),
                    centroid,
                    cDotC,
                    quantizer,
                    fieldInfo.vectorSimilarityFunction,
                    vectorsScorer,
                    finalBinarizedDataInput
                )
            val scorerSupplier: RandomVectorScorerSupplier =
                vectorsScorer.getRandomVectorScorerSupplier(
                    fieldInfo.vectorSimilarityFunction,
                    OffHeapBinarizedQueryVectorValues(
                        finalBinarizedScoreDataInput,
                        fieldInfo.vectorDimension,
                        docsWithField.cardinality()
                    ),
                    vectorValues
                )
            return BinarizedCloseableRandomVectorScorerSupplier(
                scorerSupplier,
                vectorValues,
                AutoCloseable {
                    IOUtils.close(finalBinarizedDataInput, finalBinarizedScoreDataInput)
                    IOUtils.deleteFilesIgnoringExceptions(
                        segmentWriteState.directory,
                        tempQuantizedVectorData.name!!,
                        tempScoreQuantizedVectorData.name!!
                    )
                })
        } finally {
            if (success == false) {
                IOUtils.closeWhileHandlingException(
                    tempQuantizedVectorData,
                    tempScoreQuantizedVectorData,
                    binarizedDataInput,
                    binarizedScoreDataInput
                )
                IOUtils.deleteFilesIgnoringExceptions(
                    segmentWriteState.directory,
                    tempQuantizedVectorData.name!!,
                    tempScoreQuantizedVectorData.name!!
                )
            }
        }
    }

    @Throws(IOException::class)
    override fun close() {
        IOUtils.close(meta, binarizedVectorData, rawVectorDelegate)
    }

    override fun ramBytesUsed(): Long {
        var total = SHALLOW_RAM_BYTES_USED
        for (field in fields) {
            // the field tracks the delegate field usage
            total += field.ramBytesUsed()
        }
        return total
    }

    internal class FieldWriter(val fieldInfo: FieldInfo, val flatFieldVectorsWriter: FlatFieldVectorsWriter<FloatArray>) : FlatFieldVectorsWriter<FloatArray>() {
        private var finished = false
        val dimensionSums: FloatArray = FloatArray(fieldInfo.vectorDimension)
        private val magnitudes: FloatArrayList = FloatArrayList()

        override val vectors: MutableList<FloatArray>
            get() = flatFieldVectorsWriter.vectors

        fun normalizeVectors() {
            for (i in flatFieldVectorsWriter.vectors.indices) {
                val vector: FloatArray = flatFieldVectorsWriter.vectors[i]
                val magnitude: Float = magnitudes.get(i)
                for (j in vector.indices) {
                    vector[j] /= magnitude
                }
            }
        }

        override val docsWithFieldSet: DocsWithFieldSet
            get() = flatFieldVectorsWriter.docsWithFieldSet

        @Throws(IOException::class)
        override fun finish() {
            if (finished) {
                return
            }
            assert(flatFieldVectorsWriter.isFinished)
            finished = true
        }

        override val isFinished: Boolean
            get() {
                return finished && flatFieldVectorsWriter.isFinished
            }

        @Throws(IOException::class)
        override fun addValue(docID: Int, vectorValue: FloatArray) {
            flatFieldVectorsWriter.addValue(docID, vectorValue)
            if (fieldInfo.vectorSimilarityFunction === VectorSimilarityFunction.COSINE) {
                val dp: Float = VectorUtil.dotProduct(vectorValue, vectorValue)
                val divisor = sqrt(dp.toDouble()).toFloat()
                magnitudes.add(divisor)
                for (i in vectorValue.indices) {
                    dimensionSums[i] += (vectorValue[i] / divisor)
                }
            } else {
                for (i in vectorValue.indices) {
                    dimensionSums[i] += vectorValue[i]
                }
            }
        }

        override fun copyValue(vectorValue: FloatArray): FloatArray {
            throw UnsupportedOperationException()
        }

        override fun ramBytesUsed(): Long {
            var size = SHALLOW_SIZE
            size += flatFieldVectorsWriter.ramBytesUsed()
            size += magnitudes.ramBytesUsed()
            return size
        }

        companion object {
            private val SHALLOW_SIZE: Long = RamUsageEstimator.shallowSizeOfInstance(FieldWriter::class)
        }
    }

    // When accessing vectorValue method, targerOrd here means a row ordinal.
    class OffHeapBinarizedQueryVectorValues(data: IndexInput, private val dimension: Int, private val size: Int) {
        private val slice: IndexInput = data
        protected val binaryValue: ByteArray
        protected val byteBuffer: ByteBuffer
        private val byteSize: Int
        protected val correctiveValues: FloatArray
        private var lastOrd = -1
        private var quantizedComponentSum = 0

        init {
            // 4x the quantized binary dimensions
            val binaryDimensions: Int = (OptimizedScalarQuantizer.discretize(dimension, 64) / 8) * Lucene102BinaryQuantizedVectorsFormat.QUERY_BITS
            this.byteBuffer = ByteBuffer.allocate(binaryDimensions)
            this.binaryValue = byteBuffer.array()
            // + 1 for the quantized sum
            this.correctiveValues = FloatArray(3)
            this.byteSize = binaryDimensions + Float.SIZE_BYTES * 3 + Short.SIZE_BYTES
        }

        @Throws(IOException::class)
        fun getCorrectiveTerms(targetOrd: Int): OptimizedScalarQuantizer.QuantizationResult {
            if (lastOrd == targetOrd) {
                return OptimizedScalarQuantizer.QuantizationResult(
                    correctiveValues[0], correctiveValues[1], correctiveValues[2], quantizedComponentSum
                )
            }
            vectorValue(targetOrd)
            return OptimizedScalarQuantizer.QuantizationResult(
                correctiveValues[0], correctiveValues[1], correctiveValues[2], quantizedComponentSum
            )
        }

        fun size(): Int {
            return size
        }

        fun quantizedLength(): Int {
            return binaryValue.size
        }

        fun dimension(): Int {
            return dimension
        }

        @Throws(IOException::class)
        fun copy(): OffHeapBinarizedQueryVectorValues {
            return OffHeapBinarizedQueryVectorValues(slice.clone(), dimension, size)
        }

        fun getSlice(): IndexInput {
            return slice
        }

        @Throws(IOException::class)
        fun vectorValue(targetOrd: Int): ByteArray {
            if (lastOrd == targetOrd) {
                return binaryValue
            }
            slice.seek(targetOrd.toLong() * byteSize)
            slice.readBytes(binaryValue, 0, binaryValue.size)
            slice.readFloats(correctiveValues, 0, 3)
            quantizedComponentSum = Short.toUnsignedInt(slice.readShort())
            lastOrd = targetOrd
            return binaryValue
        }
    }

    internal class BinarizedFloatVectorValues(delegate: FloatVectorValues, override val quantizer: OptimizedScalarQuantizer, override val centroid: FloatArray) : BinarizedByteVectorValues() {
        private var corrections: OptimizedScalarQuantizer.QuantizationResult? = null
        private val binarized: ByteArray = ByteArray(OptimizedScalarQuantizer.discretize(delegate.dimension(), 64) / 8)
        private val initQuantized: ByteArray = ByteArray(delegate.dimension())

        private val values: FloatVectorValues = delegate

        private var lastOrd = -1

        override fun getCorrectiveTerms(ord: Int): OptimizedScalarQuantizer.QuantizationResult? {
            check(ord == lastOrd) {
                "attempt to retrieve corrective terms for different ord $ord than the quantization was done for: $lastOrd"
            }
            return corrections
        }

        @Throws(IOException::class)
        override fun vectorValue(ord: Int): ByteArray {
            if (ord != lastOrd) {
                binarize(ord)
                lastOrd = ord
            }
            return binarized
        }

        override fun dimension(): Int {
            return values.dimension()
        }

        /*override fun getQuantizer(): OptimizedScalarQuantizer {
            throw UnsupportedOperationException()
        }*/

        override fun size(): Int {
            return values.size()
        }

        @Throws(IOException::class)
        override fun scorer(target: FloatArray): VectorScorer {
            throw UnsupportedOperationException()
        }

        @Throws(IOException::class)
        override fun copy(): BinarizedByteVectorValues {
            return BinarizedFloatVectorValues(values.copy(), quantizer, centroid)
        }

        @Throws(IOException::class)
        private fun binarize(ord: Int) {
            corrections =
                quantizer.scalarQuantize(values.vectorValue(ord), initQuantized, Lucene102BinaryQuantizedVectorsFormat.INDEX_BITS, centroid)
            OptimizedScalarQuantizer.packAsBinary(initQuantized, binarized)
        }

        override fun iterator(): DocIndexIterator {
            return values.iterator()
        }

        override fun ordToDoc(ord: Int): Int {
            return values.ordToDoc(ord)
        }
    }

    internal class BinarizedCloseableRandomVectorScorerSupplier
        (private val supplier: RandomVectorScorerSupplier, private val vectorValues: KnnVectorValues, private val onClose: AutoCloseable) : CloseableRandomVectorScorerSupplier {

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
            return vectorValues.size()
        }
    }

    internal class NormalizedFloatVectorValues(private val values: FloatVectorValues) : FloatVectorValues() {
        private val normalizedVector: FloatArray = FloatArray(values.dimension())

        override fun dimension(): Int {
            return values.dimension()
        }

        override fun size(): Int {
            return values.size()
        }

        override fun ordToDoc(ord: Int): Int {
            return values.ordToDoc(ord)
        }

        @Throws(IOException::class)
        override fun vectorValue(ord: Int): FloatArray {
            System.arraycopy(values.vectorValue(ord), 0, normalizedVector, 0, normalizedVector.size)
            VectorUtil.l2normalize(normalizedVector)
            return normalizedVector
        }

        override fun iterator(): DocIndexIterator {
            return values.iterator()
        }

        @Throws(IOException::class)
        override fun copy(): NormalizedFloatVectorValues {
            return NormalizedFloatVectorValues(values.copy())
        }
    }

    companion object {
        private val SHALLOW_RAM_BYTES_USED: Long = RamUsageEstimator.shallowSizeOfInstance(Lucene102BinaryQuantizedVectorsWriter::class)

        @Throws(IOException::class)
        fun writeBinarizedVectorAndQueryData(
            binarizedVectorData: IndexOutput,
            binarizedQueryData: IndexOutput,
            floatVectorValues: FloatVectorValues,
            centroid: FloatArray,
            binaryQuantizer: OptimizedScalarQuantizer
        ): DocsWithFieldSet {
            val discretizedDimension: Int = OptimizedScalarQuantizer.discretize(floatVectorValues.dimension(), 64)
            val docsWithField = DocsWithFieldSet()
            val quantizationScratch = Array(2) { ByteArray(floatVectorValues.dimension()) }
            val toIndex = ByteArray(discretizedDimension / 8)
            val toQuery = ByteArray((discretizedDimension / 8) * Lucene102BinaryQuantizedVectorsFormat.QUERY_BITS)
            val iterator: KnnVectorValues.DocIndexIterator = floatVectorValues.iterator()
            var docV: Int = iterator.nextDoc()
            while (docV != DocIdSetIterator.NO_MORE_DOCS) {
                // write index vector
                val r: Array<OptimizedScalarQuantizer.QuantizationResult> =
                    binaryQuantizer.multiScalarQuantize(
                        floatVectorValues.vectorValue(iterator.index()),
                        quantizationScratch,
                        byteArrayOf(Lucene102BinaryQuantizedVectorsFormat.INDEX_BITS, Lucene102BinaryQuantizedVectorsFormat.QUERY_BITS),
                        centroid
                    )
                // pack and store document bit vector
                OptimizedScalarQuantizer.packAsBinary(quantizationScratch[0], toIndex)
                binarizedVectorData.writeBytes(toIndex, toIndex.size)
                binarizedVectorData.writeInt(Float.floatToIntBits(r[0].lowerInterval))
                binarizedVectorData.writeInt(Float.floatToIntBits(r[0].upperInterval))
                binarizedVectorData.writeInt(Float.floatToIntBits(r[0].additionalCorrection))
                assert(r[0].quantizedComponentSum in 0..0xffff)
                binarizedVectorData.writeShort(r[0].quantizedComponentSum.toShort())
                docsWithField.add(docV)

                // pack and store the 4bit query vector
                OptimizedScalarQuantizer.transposeHalfByte(quantizationScratch[1], toQuery)
                binarizedQueryData.writeBytes(toQuery, toQuery.size)
                binarizedQueryData.writeInt(Float.floatToIntBits(r[1].lowerInterval))
                binarizedQueryData.writeInt(Float.floatToIntBits(r[1].upperInterval))
                binarizedQueryData.writeInt(Float.floatToIntBits(r[1].additionalCorrection))
                assert(r[1].quantizedComponentSum in 0..0xffff)
                binarizedQueryData.writeShort(r[1].quantizedComponentSum.toShort())
                docV = iterator.nextDoc()
            }
            return docsWithField
        }

        @Throws(IOException::class)
        fun writeBinarizedVectorData(
            output: IndexOutput, binarizedByteVectorValues: BinarizedByteVectorValues
        ): DocsWithFieldSet {
            val docsWithField = DocsWithFieldSet()
            val iterator: KnnVectorValues.DocIndexIterator = binarizedByteVectorValues.iterator()
            var docV: Int = iterator.nextDoc()
            while (docV != DocIdSetIterator.NO_MORE_DOCS) {
                // write vector
                val binaryValue: ByteArray = binarizedByteVectorValues.vectorValue(iterator.index())
                output.writeBytes(binaryValue, binaryValue.size)
                val corrections: OptimizedScalarQuantizer.QuantizationResult = binarizedByteVectorValues.getCorrectiveTerms(iterator.index())!!
                output.writeInt(Float.floatToIntBits(corrections.lowerInterval))
                output.writeInt(Float.floatToIntBits(corrections.upperInterval))
                output.writeInt(Float.floatToIntBits(corrections.additionalCorrection))
                assert(corrections.quantizedComponentSum in 0..0xffff)
                output.writeShort(corrections.quantizedComponentSum.toShort())
                docsWithField.add(docV)
                docV = iterator.nextDoc()
            }
            return docsWithField
        }

        fun getCentroid(vectorsReader: KnnVectorsReader, fieldName: String): FloatArray? {
            var vectorsReader: KnnVectorsReader? = vectorsReader
            if (vectorsReader is PerFieldKnnVectorsFormat.FieldsReader) {
                vectorsReader = vectorsReader.getFieldReader(fieldName)
            }
            if (vectorsReader is Lucene102BinaryQuantizedVectorsReader) {
                return vectorsReader.getCentroid(fieldName)
            }
            return null
        }

        @Throws(IOException::class)
        fun mergeAndRecalculateCentroids(
            mergeState: MergeState, fieldInfo: FieldInfo, mergedCentroid: FloatArray
        ): Int {
            var recalculate = false
            var totalVectorCount = 0
            for (i in mergeState.knnVectorsReaders.indices) {
                val knnVectorsReader: KnnVectorsReader? = mergeState.knnVectorsReaders[i]
                if (knnVectorsReader == null
                    || knnVectorsReader.getFloatVectorValues(fieldInfo.name) == null
                ) {
                    continue
                }
                val centroid = getCentroid(knnVectorsReader, fieldInfo.name)
                val vectorCount: Int = knnVectorsReader.getFloatVectorValues(fieldInfo.name)!!.size()
                if (vectorCount == 0) {
                    continue
                }
                totalVectorCount += vectorCount
                // If there aren't centroids, or previously clustered with more than one cluster
                // or if there are deleted docs, we must recalculate the centroid
                if (centroid == null || mergeState.liveDocs[i] != null) {
                    recalculate = true
                    break
                }
                for (j in centroid.indices) {
                    mergedCentroid[j] += centroid[j] * vectorCount
                }
            }
            if (recalculate) {
                return calculateCentroid(mergeState, fieldInfo, mergedCentroid)
            } else {
                for (j in mergedCentroid.indices) {
                    mergedCentroid[j] = mergedCentroid[j] / totalVectorCount
                }
                if (fieldInfo.vectorSimilarityFunction === VectorSimilarityFunction.COSINE) {
                    // Merged centroids can be all-zero (e.g., vectors cancel out); keep zero vector.
                    VectorUtil.l2normalize(mergedCentroid, false)
                }
                return totalVectorCount
            }
        }

        @Throws(IOException::class)
        fun calculateCentroid(mergeState: MergeState, fieldInfo: FieldInfo, centroid: FloatArray): Int {
            assert(fieldInfo.vectorEncoding == VectorEncoding.FLOAT32)
            // clear out the centroid
            Arrays.fill(centroid, 0f)
            var count = 0
            for (i in mergeState.knnVectorsReaders.indices) {
                val knnVectorsReader: KnnVectorsReader? = mergeState.knnVectorsReaders[i]
                if (knnVectorsReader == null) continue
                val vectorValues: FloatVectorValues? =
                    mergeState.knnVectorsReaders[i]!!.getFloatVectorValues(fieldInfo.name)
                if (vectorValues == null) {
                    continue
                }
                val iterator: KnnVectorValues.DocIndexIterator = vectorValues.iterator()
                var doc: Int = iterator.nextDoc()
                while (doc != DocIdSetIterator.NO_MORE_DOCS
                ) {
                    ++count
                    val vector: FloatArray = vectorValues.vectorValue(iterator.index())
                    for (j in vector.indices) {
                        centroid[j] += vector[j]
                    }
                    doc = iterator.nextDoc()
                }
            }
            if (count == 0) {
                return count
            }
            for (i in centroid.indices) {
                centroid[i] /= count.toFloat()
            }
            if (fieldInfo.vectorSimilarityFunction === VectorSimilarityFunction.COSINE) {
                // Centroid can be all-zero for cosine data during merges; do not throw.
                VectorUtil.l2normalize(centroid, false)
            }
            return count
        }
    }
}
