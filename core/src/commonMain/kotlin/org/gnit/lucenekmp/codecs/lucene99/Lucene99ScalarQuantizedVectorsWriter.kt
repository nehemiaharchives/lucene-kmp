package org.gnit.lucenekmp.codecs.lucene99

import okio.IOException
import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.codecs.KnnVectorsReader
import org.gnit.lucenekmp.codecs.KnnVectorsWriter
import org.gnit.lucenekmp.codecs.KnnVectorsWriter.MergedVectorValues.hasVectorValues
import org.gnit.lucenekmp.codecs.hnsw.FlatFieldVectorsWriter
import org.gnit.lucenekmp.codecs.hnsw.FlatVectorsScorer
import org.gnit.lucenekmp.codecs.hnsw.FlatVectorsWriter
import org.gnit.lucenekmp.codecs.lucene95.OrdToDocDISIReaderConfiguration
import org.gnit.lucenekmp.codecs.perfield.PerFieldKnnVectorsFormat
import org.gnit.lucenekmp.index.DocIDMerger
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
import org.gnit.lucenekmp.internal.hppc.IntArrayList
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.InfoStream
import org.gnit.lucenekmp.util.RamUsageEstimator
import org.gnit.lucenekmp.util.VectorUtil
import org.gnit.lucenekmp.util.hnsw.CloseableRandomVectorScorerSupplier
import org.gnit.lucenekmp.util.hnsw.RandomVectorScorerSupplier
import org.gnit.lucenekmp.util.hnsw.UpdateableRandomVectorScorer
import org.gnit.lucenekmp.util.quantization.QuantizedByteVectorValues
import org.gnit.lucenekmp.util.quantization.QuantizedVectorsReader
import org.gnit.lucenekmp.util.quantization.ScalarQuantizer
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.floatToIntBits
import kotlin.math.abs

/**
 * Writes quantized vector values and metadata to index segments.
 *
 * @lucene.experimental
 */
class Lucene99ScalarQuantizedVectorsWriter private constructor(
    private val segmentWriteState: SegmentWriteState,
    private val version: Int,
    private val confidenceInterval: Float?,
    private val bits: Byte,
    private val compress: Boolean,
    private val rawVectorDelegate: FlatVectorsWriter,
    scorer: FlatVectorsScorer
) : FlatVectorsWriter(scorer) {

    private val fields: MutableList<FieldWriter> = ArrayList()
    private val meta: IndexOutput
    private val quantizedVectorData: IndexOutput
    private var finished = false

    constructor(
        state: SegmentWriteState,
        confidenceInterval: Float?,
        rawVectorDelegate: FlatVectorsWriter,
        scorer: FlatVectorsScorer
    ) : this(
        state,
        Lucene99ScalarQuantizedVectorsFormat.VERSION_START,
        confidenceInterval,
        7,
        false,
        rawVectorDelegate,
        scorer
    ) {
        if (confidenceInterval != null && confidenceInterval == 0f) {
            throw IllegalArgumentException("confidenceInterval cannot be set to zero")
        }
    }

    constructor(
        state: SegmentWriteState,
        confidenceInterval: Float?,
        bits: Byte,
        compress: Boolean,
        rawVectorDelegate: FlatVectorsWriter,
        scorer: FlatVectorsScorer
    ) : this(
        state,
        Lucene99ScalarQuantizedVectorsFormat.VERSION_ADD_BITS,
        confidenceInterval,
        bits,
        compress,
        rawVectorDelegate,
        scorer
    )

    init {
        val metaFileName = IndexFileNames.segmentFileName(
            segmentWriteState.segmentInfo.name,
            segmentWriteState.segmentSuffix,
            Lucene99ScalarQuantizedVectorsFormat.META_EXTENSION
        )
        val quantizedVectorDataFileName = IndexFileNames.segmentFileName(
            segmentWriteState.segmentInfo.name,
            segmentWriteState.segmentSuffix,
            Lucene99ScalarQuantizedVectorsFormat.VECTOR_DATA_EXTENSION
        )
        var success = false
        try {
            meta = segmentWriteState.directory.createOutput(metaFileName, segmentWriteState.context)
            quantizedVectorData =
                segmentWriteState.directory.createOutput(quantizedVectorDataFileName, segmentWriteState.context)

            CodecUtil.writeIndexHeader(
                meta,
                Lucene99ScalarQuantizedVectorsFormat.META_CODEC_NAME,
                version,
                segmentWriteState.segmentInfo.getId(),
                segmentWriteState.segmentSuffix
            )
            CodecUtil.writeIndexHeader(
                quantizedVectorData,
                Lucene99ScalarQuantizedVectorsFormat.VECTOR_DATA_CODEC_NAME,
                version,
                segmentWriteState.segmentInfo.getId(),
                segmentWriteState.segmentSuffix
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
        val rawWriter = rawVectorDelegate.addField(fieldInfo)
        if (fieldInfo.vectorEncoding == VectorEncoding.FLOAT32) {
            if (bits <= 4 && fieldInfo.vectorDimension % 2 != 0) {
                throw IllegalArgumentException(
                    "bits=$bits is not supported for odd vector dimensions; " +
                        "vector dimension=${fieldInfo.vectorDimension}"
                )
            }
            @Suppress("UNCHECKED_CAST")
            val quantizedWriter = FieldWriter(
                confidenceInterval,
                bits,
                compress,
                fieldInfo,
                segmentWriteState.infoStream ?: InfoStream.NO_OUTPUT,
                rawWriter as FlatFieldVectorsWriter<FloatArray>
            )
            fields.add(quantizedWriter)
            return quantizedWriter
        }
        return rawWriter
    }

    @Throws(IOException::class)
    override fun mergeOneField(fieldInfo: FieldInfo, mergeState: MergeState) {
        rawVectorDelegate.mergeOneField(fieldInfo, mergeState)
        if (fieldInfo.vectorEncoding == VectorEncoding.FLOAT32) {
            val mergedQuantizationState =
                mergeAndRecalculateQuantiles(mergeState, fieldInfo, confidenceInterval, bits)
            val byteVectorValues =
                MergedQuantizedVectorValues.mergeQuantizedByteVectorValues(
                    fieldInfo,
                    mergeState,
                    mergedQuantizationState
                )
            val vectorDataOffset = quantizedVectorData.alignFilePointer(Float.SIZE_BYTES)
            val docsWithField =
                writeQuantizedVectorData(quantizedVectorData, byteVectorValues, bits, compress)
            val vectorDataLength = quantizedVectorData.filePointer - vectorDataOffset
            writeMeta(
                fieldInfo,
                segmentWriteState.segmentInfo.maxDoc(),
                vectorDataOffset,
                vectorDataLength,
                confidenceInterval,
                bits,
                compress,
                mergedQuantizationState.lowerQuantile,
                mergedQuantizationState.upperQuantile,
                docsWithField
            )
        }
    }

    @Throws(IOException::class)
    override fun mergeOneFieldToIndex(
        fieldInfo: FieldInfo,
        mergeState: MergeState
    ): CloseableRandomVectorScorerSupplier {
        if (fieldInfo.vectorEncoding == VectorEncoding.FLOAT32) {
            rawVectorDelegate.mergeOneField(fieldInfo, mergeState)
            val mergedQuantizationState =
                mergeAndRecalculateQuantiles(mergeState, fieldInfo, confidenceInterval, bits)
            return mergeOneFieldToIndex(segmentWriteState, fieldInfo, mergeState, mergedQuantizationState)
        }
        return rawVectorDelegate.mergeOneFieldToIndex(fieldInfo, mergeState)
    }

    @Throws(IOException::class)
    override fun flush(maxDoc: Int, sortMap: Sorter.DocMap?) {
        rawVectorDelegate.flush(maxDoc, sortMap)
        for (field in fields) {
            val quantizer = field.createQuantizer()
            if (sortMap == null) {
                writeField(field, maxDoc, quantizer)
            } else {
                writeSortingField(field, maxDoc, sortMap, quantizer)
            }
            field.finish()
        }
    }

    @Throws(IOException::class)
    override fun finish() {
        check(!finished) { "already finished" }
        finished = true
        rawVectorDelegate.finish()
        // write end of fields marker
        meta.writeInt(-1)
        CodecUtil.writeFooter(meta)
        CodecUtil.writeFooter(quantizedVectorData)
    }

    override fun ramBytesUsed(): Long {
        var total = SHALLOW_RAM_BYTES_USED
        for (field in fields) {
            total += field.ramBytesUsed()
        }
        return total
    }

    @Throws(IOException::class)
    private fun writeField(fieldData: FieldWriter, maxDoc: Int, scalarQuantizer: ScalarQuantizer) {
        val vectorDataOffset = quantizedVectorData.alignFilePointer(Float.SIZE_BYTES)
        writeQuantizedVectors(fieldData, scalarQuantizer)
        val vectorDataLength = quantizedVectorData.filePointer - vectorDataOffset
        writeMeta(
            fieldData.fieldInfo,
            maxDoc,
            vectorDataOffset,
            vectorDataLength,
            confidenceInterval,
            bits,
            compress,
            scalarQuantizer.lowerQuantile,
            scalarQuantizer.upperQuantile,
            fieldData.docsWithFieldSet
        )
    }

    @Throws(IOException::class)
    private fun writeMeta(
        field: FieldInfo,
        maxDoc: Int,
        vectorDataOffset: Long,
        vectorDataLength: Long,
        confidenceInterval: Float?,
        bits: Byte,
        compress: Boolean,
        lowerQuantile: Float,
        upperQuantile: Float,
        docsWithField: DocsWithFieldSet
    ) {
        meta.writeInt(field.number)
        meta.writeInt(field.vectorEncoding.ordinal)
        meta.writeInt(field.vectorSimilarityFunction.ordinal)
        meta.writeVLong(vectorDataOffset)
        meta.writeVLong(vectorDataLength)
        meta.writeVInt(field.vectorDimension)
        val count = docsWithField.cardinality()
        meta.writeInt(count)
        if (count > 0) {
            if (version >= Lucene99ScalarQuantizedVectorsFormat.VERSION_ADD_BITS) {
                meta.writeInt(if (confidenceInterval == null) -1 else Float.floatToIntBits(confidenceInterval))
                meta.writeByte(bits)
                meta.writeByte(if (compress) 1 else 0)
            } else {
                meta.writeInt(
                    Float.floatToIntBits(
                        confidenceInterval
                            ?: Lucene99ScalarQuantizedVectorsFormat.calculateDefaultConfidenceInterval(
                                field.vectorDimension
                            )
                    )
                )
            }
            meta.writeInt(Float.floatToIntBits(lowerQuantile))
            meta.writeInt(Float.floatToIntBits(upperQuantile))
        }
        OrdToDocDISIReaderConfiguration.writeStoredMeta(
            Lucene99FlatVectorsFormat.DIRECT_MONOTONIC_BLOCK_SHIFT,
            meta,
            quantizedVectorData,
            count,
            maxDoc,
            docsWithField
        )
    }

    @Throws(IOException::class)
    private fun writeQuantizedVectors(fieldData: FieldWriter, scalarQuantizer: ScalarQuantizer) {
        val vector = ByteArray(fieldData.fieldInfo.vectorDimension)
        val compressedVector =
            if (fieldData.compress) {
                OffHeapQuantizedByteVectorValues.compressedArray(fieldData.fieldInfo.vectorDimension, bits)
            } else {
                null
            }
        val copy = if (fieldData.normalize) FloatArray(fieldData.fieldInfo.vectorDimension) else null
        for (v0 in fieldData.vectors) {
            var v = v0
            if (fieldData.normalize) {
                System.arraycopy(v, 0, copy!!, 0, copy.size)
                VectorUtil.l2normalize(copy)
                v = copy
            }
            val offsetCorrection =
                scalarQuantizer.quantize(v, vector, fieldData.fieldInfo.vectorSimilarityFunction)
            if (compressedVector != null) {
                OffHeapQuantizedByteVectorValues.compressBytes(vector, compressedVector)
                quantizedVectorData.writeBytes(compressedVector, compressedVector.size)
            } else {
                quantizedVectorData.writeBytes(vector, vector.size)
            }
            quantizedVectorData.writeInt(Float.floatToIntBits(offsetCorrection))
        }
    }

    @Throws(IOException::class)
    private fun writeSortingField(
        fieldData: FieldWriter,
        maxDoc: Int,
        sortMap: Sorter.DocMap,
        scalarQuantizer: ScalarQuantizer
    ) {
        val ordMap = IntArray(fieldData.docsWithFieldSet.cardinality())
        val newDocsWithField = DocsWithFieldSet()
        mapOldOrdToNewOrd(fieldData.docsWithFieldSet, sortMap, null, ordMap, newDocsWithField)
        val vectorDataOffset = quantizedVectorData.alignFilePointer(Float.SIZE_BYTES)
        writeSortedQuantizedVectors(fieldData, ordMap, scalarQuantizer)
        val vectorDataLength = quantizedVectorData.filePointer - vectorDataOffset
        writeMeta(
            fieldData.fieldInfo,
            maxDoc,
            vectorDataOffset,
            vectorDataLength,
            confidenceInterval,
            bits,
            compress,
            scalarQuantizer.lowerQuantile,
            scalarQuantizer.upperQuantile,
            newDocsWithField
        )
    }

    @Throws(IOException::class)
    private fun writeSortedQuantizedVectors(
        fieldData: FieldWriter,
        ordMap: IntArray,
        scalarQuantizer: ScalarQuantizer
    ) {
        val vector = ByteArray(fieldData.fieldInfo.vectorDimension)
        val compressedVector =
            if (fieldData.compress) {
                OffHeapQuantizedByteVectorValues.compressedArray(fieldData.fieldInfo.vectorDimension, bits)
            } else {
                null
            }
        val copy = if (fieldData.normalize) FloatArray(fieldData.fieldInfo.vectorDimension) else null
        for (ordinal in ordMap) {
            var v = fieldData.vectors[ordinal]
            if (fieldData.normalize) {
                System.arraycopy(v, 0, copy!!, 0, copy.size)
                VectorUtil.l2normalize(copy)
                v = copy
            }
            val offsetCorrection =
                scalarQuantizer.quantize(v, vector, fieldData.fieldInfo.vectorSimilarityFunction)
            if (compressedVector != null) {
                OffHeapQuantizedByteVectorValues.compressBytes(vector, compressedVector)
                quantizedVectorData.writeBytes(compressedVector, compressedVector.size)
            } else {
                quantizedVectorData.writeBytes(vector, vector.size)
            }
            quantizedVectorData.writeInt(Float.floatToIntBits(offsetCorrection))
        }
    }

    @Throws(IOException::class)
    private fun mergeOneFieldToIndex(
        segmentWriteState: SegmentWriteState,
        fieldInfo: FieldInfo,
        mergeState: MergeState,
        mergedQuantizationState: ScalarQuantizer
    ): ScalarQuantizedCloseableRandomVectorScorerSupplier {
        if ((segmentWriteState.infoStream ?: InfoStream.NO_OUTPUT).isEnabled(
                Lucene99ScalarQuantizedVectorsFormat.QUANTIZED_VECTOR_COMPONENT
            )
        ) {
            (segmentWriteState.infoStream ?: InfoStream.NO_OUTPUT).message(
                Lucene99ScalarQuantizedVectorsFormat.QUANTIZED_VECTOR_COMPONENT,
                "quantized field= confidenceInterval=$confidenceInterval minQuantile=" +
                    "${mergedQuantizationState.lowerQuantile} maxQuantile=${mergedQuantizationState.upperQuantile}"
            )
        }
        val vectorDataOffset = quantizedVectorData.alignFilePointer(Float.SIZE_BYTES)
        val tempQuantizedVectorData =
            segmentWriteState.directory.createTempOutput(
                quantizedVectorData.name,
                "temp",
                segmentWriteState.context
            )
        var quantizationDataInput: IndexInput? = null
        var success = false
        try {
            val byteVectorValues =
                MergedQuantizedVectorValues.mergeQuantizedByteVectorValues(
                    fieldInfo,
                    mergeState,
                    mergedQuantizationState
                )
            val docsWithField =
                writeQuantizedVectorData(tempQuantizedVectorData, byteVectorValues, bits, compress)
            CodecUtil.writeFooter(tempQuantizedVectorData)
            IOUtils.close(tempQuantizedVectorData)
            quantizationDataInput =
                segmentWriteState.directory.openInput(
                    tempQuantizedVectorData.name,
                    segmentWriteState.context
                )
            quantizedVectorData.copyBytes(
                quantizationDataInput,
                quantizationDataInput.length() - CodecUtil.footerLength()
            )
            val vectorDataLength = quantizedVectorData.filePointer - vectorDataOffset
            CodecUtil.retrieveChecksum(quantizationDataInput)
            writeMeta(
                fieldInfo,
                segmentWriteState.segmentInfo.maxDoc(),
                vectorDataOffset,
                vectorDataLength,
                confidenceInterval,
                bits,
                compress,
                mergedQuantizationState.lowerQuantile,
                mergedQuantizationState.upperQuantile,
                docsWithField
            )
            success = true
            val finalQuantizationDataInput = quantizationDataInput
            return ScalarQuantizedCloseableRandomVectorScorerSupplier(
                {
                    IOUtils.close(finalQuantizationDataInput)
                    segmentWriteState.directory.deleteFile(tempQuantizedVectorData.name)
                },
                docsWithField.cardinality(),
                vectorScorer.getRandomVectorScorerSupplier(
                    fieldInfo.vectorSimilarityFunction,
                    OffHeapQuantizedByteVectorValues.DenseOffHeapVectorValues(
                        fieldInfo.vectorDimension,
                        docsWithField.cardinality(),
                        mergedQuantizationState,
                        compress,
                        fieldInfo.vectorSimilarityFunction,
                        vectorScorer,
                        requireNotNull(quantizationDataInput)
                    )
                )
            )
        } finally {
            if (!success) {
                IOUtils.closeWhileHandlingException(tempQuantizedVectorData, quantizationDataInput)
                IOUtils.deleteFilesIgnoringExceptions(
                    segmentWriteState.directory,
                    tempQuantizedVectorData.name
                )
            }
        }
    }

    @Throws(IOException::class)
    override fun close() {
        IOUtils.close(meta, quantizedVectorData, rawVectorDelegate)
    }

    private class FieldWriter(
        val confidenceInterval: Float?,
        val bits: Byte,
        val compress: Boolean,
        val fieldInfo: FieldInfo,
        val infoStream: InfoStream,
        val flatFieldVectorsWriter: FlatFieldVectorsWriter<FloatArray>
    ) : FlatFieldVectorsWriter<FloatArray>() {
        val normalize: Boolean = fieldInfo.vectorSimilarityFunction == VectorSimilarityFunction.COSINE
        private var finished = false

        override val vectors: MutableList<FloatArray>
            get() = flatFieldVectorsWriter.vectors

        override val docsWithFieldSet: DocsWithFieldSet
            get() = flatFieldVectorsWriter.docsWithFieldSet

        override val isFinished: Boolean
            get() = finished && flatFieldVectorsWriter.isFinished

        @Throws(IOException::class)
        override fun finish() {
            if (finished) {
                return
            }
            finished = true
        }

        @Throws(IOException::class)
        override fun addValue(docID: Int, vectorValue: FloatArray) {
            flatFieldVectorsWriter.addValue(docID, vectorValue)
        }

        override fun copyValue(vectorValue: FloatArray): FloatArray {
            throw UnsupportedOperationException()
        }

        @Throws(IOException::class)
        fun createQuantizer(): ScalarQuantizer {
            val floatVectors = flatFieldVectorsWriter.vectors
            if (floatVectors.isEmpty()) {
                return ScalarQuantizer(0f, 0f, bits)
            }
            val quantizer = buildScalarQuantizer(
                FloatVectorWrapper(floatVectors),
                floatVectors.size,
                fieldInfo.vectorSimilarityFunction,
                confidenceInterval,
                bits
            )
            if (infoStream.isEnabled(Lucene99ScalarQuantizedVectorsFormat.QUANTIZED_VECTOR_COMPONENT)) {
                infoStream.message(
                    Lucene99ScalarQuantizedVectorsFormat.QUANTIZED_VECTOR_COMPONENT,
                    "quantized field= confidenceInterval=$confidenceInterval bits=$bits " +
                        "minQuantile=${quantizer.lowerQuantile} maxQuantile=${quantizer.upperQuantile}"
                )
            }
            return quantizer
        }

        override fun ramBytesUsed(): Long {
            return SHALLOW_SIZE + flatFieldVectorsWriter.ramBytesUsed()
        }

        companion object {
            private val SHALLOW_SIZE = RamUsageEstimator.shallowSizeOfInstance(FieldWriter::class)
        }
    }

    private class FloatVectorWrapper(private val vectorList: List<FloatArray>) : FloatVectorValues() {
        override fun dimension(): Int {
            return vectorList[0].size
        }

        override fun size(): Int {
            return vectorList.size
        }

        @Throws(IOException::class)
        override fun copy(): FloatVectorValues {
            return this
        }

        @Throws(IOException::class)
        override fun vectorValue(ord: Int): FloatArray {
            if (ord < 0 || ord >= vectorList.size) {
                throw IOException("vector ord $ord out of bounds")
            }
            return vectorList[ord]
        }

        override fun iterator(): KnnVectorValues.DocIndexIterator {
            return createDenseIterator()
        }
    }

    private class QuantizedByteVectorValueSub(
        docMap: MergeState.DocMap,
        val values: QuantizedByteVectorValues
    ) : DocIDMerger.Sub(docMap) {
        private val iterator: KnnVectorValues.DocIndexIterator = values.iterator()

        init {
            require(iterator.docID() == -1)
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            return iterator.nextDoc()
        }

        fun index(): Int {
            return iterator.index()
        }
    }

    private class MergedQuantizedVectorValues(
        private val subs: MutableList<QuantizedByteVectorValueSub>,
        mergeState: MergeState
    ) : QuantizedByteVectorValues() {
        private val docIdMerger = DocIDMerger.of(subs, mergeState.needsIndexSort)
        private val size: Int
        private var current: QuantizedByteVectorValueSub? = null

        init {
            var totalSize = 0
            for (sub in subs) {
                totalSize += sub.values.size()
            }
            size = totalSize
        }

        override fun vectorValue(ord: Int): ByteArray {
            return current!!.values.vectorValue(current!!.index())
        }

        override fun iterator(): KnnVectorValues.DocIndexIterator {
            return CompositeIterator()
        }

        override fun size(): Int {
            return size
        }

        override fun dimension(): Int {
            return subs[0].values.dimension()
        }

        @Throws(IOException::class)
        override fun getScoreCorrectionConstant(ord: Int): Float {
            return current!!.values.getScoreCorrectionConstant(current!!.index())
        }

        private inner class CompositeIterator : KnnVectorValues.DocIndexIterator() {
            private var docId = -1
            private var ord = -1

            override fun index(): Int {
                return ord
            }

            override fun docID(): Int {
                return docId
            }

            @Throws(IOException::class)
            override fun nextDoc(): Int {
                current = docIdMerger.next()
                if (current == null) {
                    docId = DocIdSetIterator.NO_MORE_DOCS
                    ord = DocIdSetIterator.NO_MORE_DOCS
                } else {
                    docId = current!!.mappedDocID
                    ord += 1
                }
                return docId
            }

            @Throws(IOException::class)
            override fun advance(target: Int): Int {
                throw UnsupportedOperationException()
            }

            override fun cost(): Long {
                return size.toLong()
            }
        }

        companion object {
            @Throws(IOException::class)
            fun mergeQuantizedByteVectorValues(
                fieldInfo: FieldInfo,
                mergeState: MergeState,
                scalarQuantizer: ScalarQuantizer
            ): MergedQuantizedVectorValues {
                require(fieldInfo.hasVectorValues())
                val subs: MutableList<QuantizedByteVectorValueSub> = ArrayList()
                for (i in mergeState.knnVectorsReaders.indices) {
                    if (hasVectorValues(mergeState.fieldInfos[i]!!, fieldInfo.name)) {
                        val reader =
                            getQuantizedKnnVectorsReader(mergeState.knnVectorsReaders[i]!!, fieldInfo.name)
                        val sub = if (reader == null ||
                            reader.getQuantizationState(fieldInfo.name) == null ||
                            scalarQuantizer.bits <= 4 ||
                            shouldRequantize(reader.getQuantizationState(fieldInfo.name)!!, scalarQuantizer)
                        ) {
                            var toQuantize: FloatVectorValues =
                                mergeState.knnVectorsReaders[i]!!.getFloatVectorValues(fieldInfo.name)!!
                            if (fieldInfo.vectorSimilarityFunction == VectorSimilarityFunction.COSINE) {
                                toQuantize = NormalizedFloatVectorValues(toQuantize)
                            }
                            QuantizedByteVectorValueSub(
                                mergeState.docMaps!![i],
                                QuantizedFloatVectorValues(
                                    toQuantize,
                                    fieldInfo.vectorSimilarityFunction,
                                    scalarQuantizer
                                )
                            )
                        } else {
                            QuantizedByteVectorValueSub(
                                mergeState.docMaps!![i],
                                OffsetCorrectedQuantizedByteVectorValues(
                                    requireNotNull(reader.getQuantizedVectorValues(fieldInfo.name)),
                                    fieldInfo.vectorSimilarityFunction,
                                    scalarQuantizer,
                                    requireNotNull(reader.getQuantizationState(fieldInfo.name))
                                )
                            )
                        }
                        subs.add(sub)
                    }
                }
                return MergedQuantizedVectorValues(subs, mergeState)
            }
        }
    }

    private class QuantizedFloatVectorValues(
        private val values: FloatVectorValues,
        private val vectorSimilarityFunction: VectorSimilarityFunction,
        private val quantizer: ScalarQuantizer
    ) : QuantizedByteVectorValues() {
        private val quantizedVector = ByteArray(values.dimension())
        private var lastOrd = -1
        private var offsetValue = 0f

        override val scalarQuantizer: ScalarQuantizer
            get() = quantizer

        override fun getScoreCorrectionConstant(ord: Int): Float {
            if (ord != lastOrd) {
                throw IllegalStateException(
                    "attempt to retrieve score correction for different ord $ord than the quantization was done for: $lastOrd"
                )
            }
            return offsetValue
        }

        override fun dimension(): Int {
            return values.dimension()
        }

        override fun size(): Int {
            return values.size()
        }

        @Throws(IOException::class)
        override fun vectorValue(ord: Int): ByteArray {
            if (ord != lastOrd) {
                offsetValue = quantize(ord)
                lastOrd = ord
            }
            return quantizedVector
        }

        @Throws(IOException::class)
        private fun quantize(ord: Int): Float {
            return quantizer.quantize(values.vectorValue(ord), quantizedVector, vectorSimilarityFunction)
        }

        override fun ordToDoc(ord: Int): Int {
            return values.ordToDoc(ord)
        }

        override fun iterator(): KnnVectorValues.DocIndexIterator {
            return values.iterator()
        }
    }

    private class ScalarQuantizedCloseableRandomVectorScorerSupplier(
        private val onClose: () -> Unit,
        private val numVectors: Int,
        private val supplier: RandomVectorScorerSupplier
    ) : CloseableRandomVectorScorerSupplier {
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
            onClose()
        }

        override fun totalVectorCount(): Int {
            return numVectors
        }
    }

    private class OffsetCorrectedQuantizedByteVectorValues(
        private val input: QuantizedByteVectorValues,
        private val vectorSimilarityFunction: VectorSimilarityFunction,
        private val scalarQuantizerValue: ScalarQuantizer,
        private val oldScalarQuantizer: ScalarQuantizer
    ) : QuantizedByteVectorValues() {

        override val scalarQuantizer: ScalarQuantizer
            get() = scalarQuantizerValue

        @Throws(IOException::class)
        override fun getScoreCorrectionConstant(ord: Int): Float {
            return scalarQuantizerValue.recalculateCorrectiveOffset(
                input.vectorValue(ord),
                oldScalarQuantizer,
                vectorSimilarityFunction
            )
        }

        override fun dimension(): Int {
            return input.dimension()
        }

        override fun size(): Int {
            return input.size()
        }

        @Throws(IOException::class)
        override fun vectorValue(ord: Int): ByteArray {
            return input.vectorValue(ord)
        }

        override fun ordToDoc(ord: Int): Int {
            return input.ordToDoc(ord)
        }

        override fun iterator(): KnnVectorValues.DocIndexIterator {
            return input.iterator()
        }
    }

    private class NormalizedFloatVectorValues(private val values: FloatVectorValues) : FloatVectorValues() {
        private val normalizedVector = FloatArray(values.dimension())

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

        override fun iterator(): KnnVectorValues.DocIndexIterator {
            return values.iterator()
        }

        @Throws(IOException::class)
        override fun copy(): NormalizedFloatVectorValues {
            return NormalizedFloatVectorValues(values.copy())
        }
    }

    companion object {
        private val SHALLOW_RAM_BYTES_USED =
            RamUsageEstimator.shallowSizeOfInstance(Lucene99ScalarQuantizedVectorsWriter::class)

        private const val QUANTILE_RECOMPUTE_LIMIT = 32f
        private const val REQUANTIZATION_LIMIT = 0.2f

        fun mergeQuantiles(
            quantizationStates: List<ScalarQuantizer?>,
            segmentSizes: IntArrayList,
            bits: Byte
        ): ScalarQuantizer? {
            if (quantizationStates.isEmpty()) {
                return null
            }
            var lowerQuantile = 0f
            var upperQuantile = 0f
            var totalCount = 0
            for (i in quantizationStates.indices) {
                val state = quantizationStates[i] ?: return null
                lowerQuantile += state.lowerQuantile * segmentSizes.get(i)
                upperQuantile += state.upperQuantile * segmentSizes.get(i)
                totalCount += segmentSizes.get(i)
                if (state.bits != bits) {
                    return null
                }
            }
            lowerQuantile /= totalCount
            upperQuantile /= totalCount
            return ScalarQuantizer(lowerQuantile, upperQuantile, bits)
        }

        fun shouldRecomputeQuantiles(
            mergedQuantizationState: ScalarQuantizer,
            quantizationStates: List<ScalarQuantizer>
        ): Boolean {
            val limit =
                (mergedQuantizationState.upperQuantile - mergedQuantizationState.lowerQuantile) /
                    QUANTILE_RECOMPUTE_LIMIT
            for (state in quantizationStates) {
                if (abs(state.upperQuantile - mergedQuantizationState.upperQuantile) > limit) {
                    return true
                }
                if (abs(state.lowerQuantile - mergedQuantizationState.lowerQuantile) > limit) {
                    return true
                }
            }
            return false
        }

        private fun getQuantizedKnnVectorsReader(
            vectorsReader: KnnVectorsReader,
            fieldName: String
        ): QuantizedVectorsReader? {
            var reader = vectorsReader
            if (reader is PerFieldKnnVectorsFormat.FieldsReader) {
                reader = reader.getFieldReader(fieldName) ?: return null
            }
            return if (reader is QuantizedVectorsReader) reader else null
        }

        private fun getQuantizedState(vectorsReader: KnnVectorsReader, fieldName: String): ScalarQuantizer? {
            val reader = getQuantizedKnnVectorsReader(vectorsReader, fieldName)
            return reader?.getQuantizationState(fieldName)
        }

        @Throws(IOException::class)
        fun mergeAndRecalculateQuantiles(
            mergeState: MergeState,
            fieldInfo: FieldInfo,
            confidenceInterval: Float?,
            bits: Byte
        ): ScalarQuantizer {
            val quantizationStates: MutableList<ScalarQuantizer?> = ArrayList(mergeState.liveDocs.size)
            val segmentSizes = IntArrayList(mergeState.liveDocs.size)
            for (i in mergeState.liveDocs.indices) {
                if (!hasVectorValues(mergeState.fieldInfos[i]!!, fieldInfo.name)) {
                    continue
                }
                val reader = mergeState.knnVectorsReaders[i] ?: continue
                val fvv = reader.getFloatVectorValues(fieldInfo.name) ?: continue
                if (fvv.size() > 0) {
                    quantizationStates.add(getQuantizedState(reader, fieldInfo.name))
                    segmentSizes.add(fvv.size())
                }
            }
            val mergedQuantiles = mergeQuantiles(quantizationStates, segmentSizes, bits)
            if (mergedQuantiles == null || bits <= 4 || shouldRecomputeQuantiles(
                    mergedQuantiles,
                    quantizationStates.filterNotNull()
                )
            ) {
                var numVectors = 0
                val iter =
                    KnnVectorsWriter.MergedVectorValues.mergeFloatVectorValues(fieldInfo, mergeState)
                        .iterator()
                var doc = iter.nextDoc()
                while (doc != DocIdSetIterator.NO_MORE_DOCS) {
                    numVectors++
                    doc = iter.nextDoc()
                }
                return buildScalarQuantizer(
                    KnnVectorsWriter.MergedVectorValues.mergeFloatVectorValues(fieldInfo, mergeState),
                    numVectors,
                    fieldInfo.vectorSimilarityFunction,
                    confidenceInterval,
                    bits
                )
            }
            return mergedQuantiles
        }

        @Throws(IOException::class)
        fun buildScalarQuantizer(
            floatVectorValues: FloatVectorValues,
            numVectors: Int,
            vectorSimilarityFunction: VectorSimilarityFunction,
            confidenceInterval: Float?,
            bits: Byte
        ): ScalarQuantizer {
            var values = floatVectorValues
            var similarity = vectorSimilarityFunction
            if (similarity == VectorSimilarityFunction.COSINE) {
                values = NormalizedFloatVectorValues(values)
                similarity = VectorSimilarityFunction.DOT_PRODUCT
            }
            if (confidenceInterval != null &&
                confidenceInterval == Lucene99ScalarQuantizedVectorsFormat.DYNAMIC_CONFIDENCE_INTERVAL
            ) {
                return ScalarQuantizer.fromVectorsAutoInterval(values, similarity, numVectors, bits)
            }
            val interval =
                confidenceInterval ?: Lucene99ScalarQuantizedVectorsFormat.calculateDefaultConfidenceInterval(
                    values.dimension()
                )
            return ScalarQuantizer.fromVectors(values, interval, numVectors, bits)
        }

        fun shouldRequantize(existingQuantiles: ScalarQuantizer, newQuantiles: ScalarQuantizer): Boolean {
            val tol =
                REQUANTIZATION_LIMIT *
                    (newQuantiles.upperQuantile - newQuantiles.lowerQuantile) /
                    128f
            if (abs(existingQuantiles.upperQuantile - newQuantiles.upperQuantile) > tol) {
                return true
            }
            return abs(existingQuantiles.lowerQuantile - newQuantiles.lowerQuantile) > tol
        }

        @Throws(IOException::class)
        fun writeQuantizedVectorData(
            output: IndexOutput,
            quantizedByteVectorValues: QuantizedByteVectorValues,
            bits: Byte,
            compress: Boolean
        ): DocsWithFieldSet {
            val docsWithField = DocsWithFieldSet()
            val compressedVector =
                if (compress) {
                    OffHeapQuantizedByteVectorValues.compressedArray(quantizedByteVectorValues.dimension(), bits)
                } else {
                    null
                }
            val iter = quantizedByteVectorValues.iterator()
            var docV = iter.nextDoc()
            while (docV != DocIdSetIterator.NO_MORE_DOCS) {
                val binaryValue = quantizedByteVectorValues.vectorValue(iter.index())
                if (compressedVector != null) {
                    OffHeapQuantizedByteVectorValues.compressBytes(binaryValue, compressedVector)
                    output.writeBytes(compressedVector, compressedVector.size)
                } else {
                    output.writeBytes(binaryValue, binaryValue.size)
                }
                output.writeInt(
                    Float.floatToIntBits(
                        quantizedByteVectorValues.getScoreCorrectionConstant(iter.index())
                    )
                )
                docsWithField.add(docV)
                docV = iter.nextDoc()
            }
            return docsWithField
        }
    }
}
